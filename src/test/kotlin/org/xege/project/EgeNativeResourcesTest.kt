package org.xege.project

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class EgeNativeResourcesTest {
    private fun resource(path: String) = javaClass.getResourceAsStream("/assets/$path")
    private fun copier() = EgeResourceCopier(::resource)
    private fun project(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("ege-resources-").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun `every supported platform uses pinned native sources`() {
        for (os in listOf("Windows 11", "Mac OS X", "Linux")) assertTrue(EgeBuildMode.usesSource(os))
        assertFailsWith<IllegalArgumentException> { EgeBuildMode.usesSource("DarwinUnknown") }
    }

    @Test fun `generation includes native backends camera helpers images and licenses`() = project { dir ->
        copier().generate(dir, "camera_base.cpp")
        for (name in listOf("ege/cmake/EgeSources.cmake", "ege/src/backend/macos/MacWindow.mm",
            "ege/src/backend/linux/LinuxWindow.cpp", "ege/3rdparty/ccap/src/ccap_imp_apple.mm",
            "ege/3rdparty/ccap/LICENSE", "ege/LICENSE", "camera_demo_screen.h", "getimage.png",
            "getimage.jpg", "macos-camera-info.plist", "ege-project.cmake", ".gitignore", ".vscode/settings.json")) assertTrue(File(dir, name).isFile, name)
        assertTrue(File(dir, "main.cpp").readText().contains("camera_demo_screen.h"))
        val settings = File(dir, ".vscode/settings.json").readText()
        assertFalse(settings.contains("CMAKE_SYSTEM_NAME"))
        assertFalse(settings.contains("mingw"))
        assertFalse(dir.walkTopDown().any { it.extension in listOf("a", "lib", "dll", "exe") })
    }

    @Test fun `conversion preserves existing sources and appends CMake integration`() = project { dir ->
        val cmake = "cmake_minimum_required(VERSION 3.13)\nproject(existing)\nadd_executable(app main.cpp)\n"
        File(dir, "CMakeLists.txt").writeText(cmake)
        File(dir, "main.cpp").writeText("// user source")
        File(dir, ".gitignore").writeText("user-ignore")
        File(dir, ".vscode").mkdir()
        File(dir, ".vscode/settings.json").writeText("user-settings")
        copier().generate(dir)
        assertEquals("// user source", File(dir, "main.cpp").readText())
        assertEquals("user-ignore", File(dir, ".gitignore").readText())
        assertEquals("user-settings", File(dir, ".vscode/settings.json").readText())
        assertTrue(File(dir, "CMakeLists.txt").readText().startsWith(cmake))
        assertTrue(File(dir, "CMakeLists.txt").readText().contains("include(\"\${CMAKE_CURRENT_LIST_DIR}/ege-project.cmake\")"))
    }

    @Test fun `corrupt and missing resources fail before publishing CMake`() = project { dir ->
        for (missing in listOf(true, false)) {
            val broken = EgeResourceCopier { path ->
                if (path == "ege_src/src/backend/macos/MacWindow.mm") {
                    if (missing) null else "corrupted".byteInputStream()
                } else resource(path)
            }
            assertFails { broken.generate(dir) }
            assertFalse(File(dir, "CMakeLists.txt").exists())
            assertFalse(File(dir, "ege").exists())
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun `unknown demo and existing EGE directory are not overwritten`() = project { dir ->
        assertFails { copier().generate(dir, "../escape.cpp") }
        File(dir, "ege").mkdir()
        File(dir, "ege/user.txt").writeText("keep")
        assertFails { copier().generate(dir) }
        assertEquals("keep", File(dir, "ege/user.txt").readText())
    }

    @Test fun `rollback preserves concurrent user files edits and CMake`() = project { dir ->
        File(dir, "CMakeLists.txt").writeText("# original CMake\n")
        assertFails {
            copier().generate(dir, beforePublish = {
                File(dir, "ege/user.txt").writeText("new user file")
                File(dir, "ege/LICENSE").writeText("user edit")
                File(dir, "getimage.png").delete()
                File(dir, "getimage.png").writeText("user replacement")
                File(dir, "CMakeLists.txt").writeText("# concurrent user edit\n")
            })
        }
        assertEquals("new user file", File(dir, "ege/user.txt").readText())
        assertEquals("user edit", File(dir, "ege/LICENSE").readText())
        assertEquals("user replacement", File(dir, "getimage.png").readText())
        assertEquals("# concurrent user edit\n", File(dir, "CMakeLists.txt").readText())
        assertFalse(File(dir, "ege-project.cmake").exists())
        assertFalse(dir.listFiles().orEmpty().any { it.name.startsWith(".xege-stage-") })
    }

    @Test fun `failed final publication removes only unchanged generated files`() = project { dir ->
        assertFails { copier().generate(dir, beforePublish = { error("publication failed") }) }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun `JAR resources work without directory entries`() = project { dir ->
        val jar = File(dir, "resources.jar")
        java.util.jar.JarOutputStream(jar.outputStream()).use { output ->
            val index = resource("resource-manifest.sha256")!!.use { it.readBytes() }
            val paths = index.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }
                .map { it.substringAfter("  ") }.toList() + "resource-manifest.sha256"
            for (path in paths) {
                output.putNextEntry(java.util.jar.JarEntry("assets/$path"))
                resource(path)!!.use { it.copyTo(output) }
                output.closeEntry()
            }
        }
        java.net.URLClassLoader(arrayOf(jar.toURI().toURL()), null).use { loader ->
            EgeResourceCopier { path -> loader.getResourceAsStream("assets/$path") }
                .generate(File(dir, "generated"), "graph_getimage.cpp")
        }
        assertTrue(File(dir, "generated/getimage.png").isFile)
        assertTrue(File(dir, "generated/main.cpp").readText().contains("getimage.png"))
    }

    @Test fun `generate project for native build verification`() {
        val dir = File("build/native-smoke-project")
        dir.deleteRecursively()
        copier().generate(dir)
    }
}
