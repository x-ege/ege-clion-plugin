package org.xege.project

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

/** Stream-only access works identically from a plugin JAR and an unpacked classpath. */
class EgeResourceCopier(private val open: (String) -> InputStream?) {
    companion object {
        private val projectLocks = ConcurrentHashMap<String, Any>()
    }
    private data class OwnedPath(val path: Path, val key: Any?, val checksum: String?)

    private fun trackTree(source: File, destination: File): List<OwnedPath> =
        source.walkTopDown().map { file ->
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            val path = destination.toPath().resolve(source.toPath().relativize(file.toPath()))
            OwnedPath(path, attributes.fileKey(), if (attributes.isRegularFile) sha256(file.readBytes()) else null)
        }.toList()

    private fun rollback(paths: List<OwnedPath>) {
        for (owned in paths.sortedByDescending { it.path.nameCount }) {
            try {
                val attributes = Files.readAttributes(owned.path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                // A renamed file keeps its identity. Never remove a user's replacement or edits.
                if (owned.key == null || attributes.fileKey() != owned.key) continue
                if (owned.checksum != null &&
                    (!attributes.isRegularFile || sha256(Files.readAllBytes(owned.path)) != owned.checksum)) continue
                // Directory deletion only succeeds when empty, preserving new user files.
                Files.deleteIfExists(owned.path)
            } catch (_: IOException) { /* Preserve anything whose ownership cannot be proved. */ }
        }
    }
    private fun bytes(path: String): ByteArray =
        open(path)?.use { it.readBytes() } ?: throw IOException("Missing EGE resource: $path")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        bytes("resource-manifest.sha256").toString(Charsets.UTF_8).lineSequence()
            .filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("  ", limit = 2)
                require(parts.size == 2 && parts[0].matches(Regex("[a-f0-9]{64}"))) { "Invalid resource manifest" }
                val path = parts[1]
                require(!path.startsWith("/") && !path.contains('\\') && !path.contains(':') &&
                    path.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "Unsafe resource path: $path" }
                require(entries.put(path, parts[0]) == null) { "Duplicate resource: $path" }
            }
        for (path in listOf("ege_src/CMakeLists.txt", "ege_src/cmake/EgeBackends.cmake",
            "ege_src/cmake/EgeSources.cmake", "ege_src/src/backend/linux/LinuxWindow.cpp",
            "ege_src/src/backend/macos/MacWindow.mm", "ege_src/include/ege/win32_compat.h",
            "ege_src/3rdparty/ccap/src/ccap_core.cpp", "ege_src/3rdparty/ccap/src/ccap_imp_apple.mm",
            "ege_src/LICENSE", "ege_src/3rdparty/ccap/LICENSE", "ege_src/source.properties",
            "cmake_template/CMakeLists_src.txt", "cmake_template/ege-project.cmake",
            "cmake_template/main.cpp", "ege_demos/camera_demo_screen.h",
            "ege_demos/getimage.png", "ege_demos/getimage.jpg")) {
            require(entries.containsKey(path)) { "Incomplete EGE bundle: $path" }
        }
        return entries
    }

    fun generate(target: File, demo: String? = null, beforePublish: (() -> Unit)? = null,
                 progress: (String) -> Unit = {}) {
        val canonical = target.canonicalFile
        synchronized(projectLocks.computeIfAbsent(canonical.path) { Any() }) {
            generateLocked(canonical, demo, beforePublish, progress)
        }
    }

    private fun generateLocked(target: File, demo: String?, beforePublish: (() -> Unit)?, progress: (String) -> Unit) {
        val index = manifest()
        val selectedDemo = if (demo == null) "cmake_template/main.cpp" else "ege_demos/$demo"
        require(index.containsKey(selectedDemo) && (demo == null ||
            (demo.endsWith(".cpp") && !demo.contains('/') && !demo.contains('\\')))) { "Unknown EGE demo: $demo" }
        require(!Files.exists(File(target, "ege").toPath(), NOFOLLOW_LINKS)) {
            "The ege directory already exists. Move it aside before installing the pinned native sources."
        }
        require(!target.exists() || target.isDirectory) { "Project path is not a directory: $target" }
        require(target.isDirectory || target.mkdirs()) { "Cannot create project directory: $target" }
        val cmake = File(target, "CMakeLists.txt")
        require(!Files.isSymbolicLink(cmake.toPath())) { "Refusing to replace a CMake symlink" }
        val originalCMake = if (cmake.exists()) cmake.readBytes() else null
        val stage = Files.createTempDirectory(target.toPath(), ".xege-stage-").toFile()
        val installed = mutableListOf<OwnedPath>()
        try {
            val selected = index.filterKeys { it.startsWith("ege_src/") ||
                it.startsWith("cmake_template/") || it.startsWith("ege_demos/") }
            for ((path, checksum) in selected) {
                progress(path)
                val data = bytes(path)
                require(sha256(data) == checksum) { "Corrupt EGE resource: $path" }
                val relative = when {
                    path.startsWith("ege_src/") -> "ege/" + path.removePrefix("ege_src/")
                    path == selectedDemo -> "main.cpp"
                    path == "cmake_template/gitignore.template" -> ".gitignore"
                    path.startsWith("cmake_template/") && path != "cmake_template/main.cpp" &&
                        !path.removePrefix("cmake_template/").startsWith("CMakeLists_") -> path.removePrefix("cmake_template/")
                    path.startsWith("ege_demos/") && !path.endsWith(".cpp") -> path.removePrefix("ege_demos/")
                    else -> continue
                }
                val out = File(stage, relative)
                out.parentFile.mkdirs()
                out.writeBytes(data)
            }
            val includeLine = "include(\"\${CMAKE_CURRENT_LIST_DIR}/ege-project.cmake\")"
            val content = if (originalCMake != null) {
                val existing = originalCMake.toString(Charsets.UTF_8)
                require(!existing.contains("ege-project.cmake")) { "Project already has EGE integration" }
                existing + "\n# EGE native source integration\n" + includeLine + "\n"
            } else bytes("cmake_template/CMakeLists_src.txt").toString(Charsets.UTF_8)
            // Check every destination before installing anything. Existing user sources are preserved.
            for (file in stage.listFiles().orEmpty()) {
                val dest = File(target, file.name)
                if (file.name in listOf("main.cpp", ".gitignore", ".vscode") && Files.exists(dest.toPath(), NOFOLLOW_LINKS)) continue
                require(!Files.exists(dest.toPath(), NOFOLLOW_LINKS)) { "Refusing to overwrite existing project file: $dest" }
            }
            for (file in stage.listFiles().orEmpty()) {
                val dest = File(target, file.name)
                if (file.name in listOf("main.cpp", ".gitignore", ".vscode") && Files.exists(dest.toPath(), NOFOLLOW_LINKS)) continue
                val owned = trackTree(file, dest)
                Files.move(file.toPath(), dest.toPath())
                installed.addAll(owned)
            }
            // Publish the CMake entrypoint only after all resources have been installed.
            beforePublish?.invoke()
            val currentCMake = if (Files.exists(cmake.toPath(), NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(cmake.toPath())) { "CMake changed during generation" }
                cmake.readBytes()
            } else null
            require(if (originalCMake == null) currentCMake == null else
                currentCMake != null && originalCMake.contentEquals(currentCMake)) { "CMake changed during generation; preserving user edits" }
            val stagedCMake = File(stage, "CMakeLists.txt")
            stagedCMake.writeText(content)
            Files.move(stagedCMake.toPath(), cmake.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            rollback(installed)
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }
}
