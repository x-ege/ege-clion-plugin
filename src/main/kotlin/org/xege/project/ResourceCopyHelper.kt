package org.xege.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File

/**
 * 资源复制辅助类
 * 用于从 JAR 或文件系统复制资源文件到目标目录
 */
object ResourceCopyHelper {
    private val logger = Logger.getInstance(ResourceCopyHelper::class.java)
    
    /**
     * 递归复制资源目录
     * @param resourcePath 资源路径（如 "/assets/ege_bundle"）
     * @param targetDir 目标目录
     * @param indicator 可选的进度指示器
     */
    fun copyResourceDirectory(
        resourcePath: String, 
        targetDir: File, 
        indicator: ProgressIndicator? = null
    ) {
        try {
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource directory not found: $resourcePath")
                return
            }
            
            val uri = resourceUrl.toURI()
            
            when (uri.scheme) {
                "jar" -> copyFromJar(resourcePath, targetDir, indicator)
                else -> copyFromFileSystem(File(uri), targetDir, indicator)
            }
        } catch (e: Exception) {
            logger.error("Failed to copy resource directory: $resourcePath", e)
            throw e
        }
    }
    
    /**
     * 从 JAR 文件中复制资源
     */
    private fun copyFromJar(
        resourcePath: String, 
        targetDir: File, 
        indicator: ProgressIndicator?
    ) {
        logger.info("Copying resources from JAR: $resourcePath")
        
        // 使用 ClassLoader 扫描资源目录
        val resourceFiles = discoverResourceFiles(resourcePath)
        logger.info("Found ${resourceFiles.size} files in $resourcePath")
        
        if (resourceFiles.isEmpty()) {
            logger.warn("No files found in resource path: $resourcePath")
            return
        }
        
        resourceFiles.forEachIndexed { index, relPath ->
            // 更新进度
            indicator?.let {
                it.fraction = index.toDouble() / resourceFiles.size
                it.text2 = "Copying: $relPath"
            }
            
            try {
                val fullPath = "$resourcePath/$relPath"
                val stream = javaClass.getResourceAsStream(fullPath)
                if (stream != null) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs()
                    stream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    logger.debug("Copied: $fullPath -> ${targetFile.absolutePath}")
                } else {
                    logger.warn("Resource not found: $fullPath")
                }
            } catch (e: Exception) {
                logger.error("Failed to copy $relPath: ${e.message}", e)
            }
        }
    }
    
    /**
     * 扫描资源目录，发现所有文件
     * @param resourcePath 资源路径
     * @param includeHidden 是否包含隐藏文件（以 . 开头的文件）
     * @return 相对路径列表
     */
    private fun discoverResourceFiles(resourcePath: String, includeHidden: Boolean = false): List<String> {
        val files = mutableListOf<String>()
        
        try {
            // 获取资源 URL
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource URL not found: $resourcePath")
                return emptyList()
            }
            
            val uri = resourceUrl.toURI()
            
            if (uri.scheme == "jar") {
                // 从 JAR 文件扫描
                val jarPath = uri.toString().substringAfter("jar:file:").substringBefore("!")
                val jarFile = java.util.jar.JarFile(File(java.net.URI("file:$jarPath")))
                
                val prefix = resourcePath.removePrefix("/")
                jarFile.entries().asIterator().forEach { entry ->
                    val name = entry.name
                    if (name.startsWith(prefix) && !entry.isDirectory) {
                        val relativePath = name.removePrefix("$prefix/")
                        // 根据 includeHidden 参数决定是否包含隐藏文件
                        val shouldInclude = if (includeHidden) {
                            relativePath.isNotEmpty()
                        } else {
                            relativePath.isNotEmpty() && !relativePath.startsWith(".")
                        }
                        if (shouldInclude) {
                            files.add(relativePath)
                        }
                    }
                }
                jarFile.close()
            } else {
                // 从文件系统扫描
                val dir = File(uri)
                if (dir.exists() && dir.isDirectory) {
                    collectFiles(dir, "", files, includeHidden)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to discover resource files in $resourcePath", e)
        }
        
        return files
    }
    
    /**
     * 递归收集文件
     * @param dir 当前目录
     * @param prefix 路径前缀
     * @param files 文件列表（输出参数）
     * @param includeHidden 是否包含隐藏文件（以 . 开头的文件）
     */
    private fun collectFiles(dir: File, prefix: String, files: MutableList<String>, includeHidden: Boolean = false) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val newPrefix = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
                collectFiles(file, newPrefix, files, includeHidden)
            } else {
                // 根据 includeHidden 参数决定是否包含隐藏文件
                val shouldInclude = if (includeHidden) {
                    true
                } else {
                    !file.name.startsWith(".")
                }
                if (shouldInclude) {
                    val relativePath = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
                    files.add(relativePath)
                }
            }
        }
    }
    
    /**
     * 从文件系统复制目录
     */
    private fun copyFromFileSystem(
        sourceDir: File, 
        targetDir: File, 
        indicator: ProgressIndicator?
    ) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            logger.warn("Source directory does not exist: ${sourceDir.absolutePath}")
            return
        }
        
        val allFiles = mutableListOf<Pair<File, File>>()
        collectFilePairs(sourceDir, targetDir, allFiles)
        
        allFiles.forEachIndexed { index, (source, target) ->
            // 更新进度
            indicator?.let {
                it.fraction = index.toDouble() / allFiles.size
                it.text2 = "Copying: ${source.name}"
            }
            
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            logger.debug("Copied: ${source.absolutePath} -> ${target.absolutePath}")
        }
    }
    
    /**
     * 收集源文件和目标文件对
     */
    private fun collectFilePairs(
        sourceDir: File, 
        targetDir: File, 
        pairs: MutableList<Pair<File, File>>
    ) {
        sourceDir.listFiles()?.forEach { file ->
            val targetFile = File(targetDir, file.name)
            if (file.isDirectory) {
                collectFilePairs(file, targetFile, pairs)
            } else if (!file.name.startsWith(".")) {
                pairs.add(file to targetFile)
            }
        }
    }
    
    /**
     * 复制 CMake 模板文件
     * @param targetDir 目标目录
     * @param useSourceCode 是否使用源码版本
     */
    fun copyCMakeTemplateFiles(targetDir: File, useSourceCode: Boolean) {
        try {
            // 1. 复制 CMakeLists.txt（根据选项选择模板）
            val targetCMake = File(targetDir, "CMakeLists.txt")
            if (targetCMake.exists()) {
                logger.info("CMakeLists.txt already exists, not modifying: ${targetCMake.absolutePath}")
            } else {
                val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
                val cmakeStream = javaClass.getResourceAsStream("/assets/cmake_template/$cmakeTemplate")
                if (cmakeStream != null) {
                    val content = cmakeStream.bufferedReader().use { it.readText() }
                    targetCMake.parentFile?.mkdirs()
                    targetCMake.writeText(content)
                    logger.info("Copied $cmakeTemplate to ${targetCMake.absolutePath}")
                } else {
                    logger.error("CMake template not found: /assets/cmake_template/$cmakeTemplate")
                    throw RuntimeException("CMake 模板文件不存在")
                }
            }
            
            // 2. 复制 cmake_template 目录下的其他所有文件（除了 CMakeLists_*.txt）
            val resourceUrl = javaClass.getResource("/assets/cmake_template")
            if (resourceUrl != null) {
                val uri = resourceUrl.toURI()
                if (uri.scheme == "jar") {
                    // 从 JAR 中复制
                    copyOtherTemplateFilesFromJar(targetDir)
                } else {
                    // 从文件系统复制
                    val templateDir = File(uri)
                    templateDir.listFiles()?.forEach { file ->
                        // 复制普通文件（排除 CMakeLists_*.txt）
                        if (file.isFile && !file.name.startsWith("CMakeLists_")) {
                            val targetFile = File(targetDir, file.name)
                            file.copyTo(targetFile, overwrite = true)
                            logger.info("Copied ${file.name} to ${targetFile.absolutePath}")
                        }
                        // 复制 .vscode 目录
                        else if (file.isDirectory && file.name == ".vscode") {
                            val targetVscodeDir = File(targetDir, ".vscode")
                            targetVscodeDir.mkdirs()
                            file.listFiles()?.forEach { vscodeFile ->
                                if (vscodeFile.isFile) {
                                    val targetFile = File(targetVscodeDir, vscodeFile.name)
                                    vscodeFile.copyTo(targetFile, overwrite = true)
                                    logger.info("Copied .vscode/${vscodeFile.name} to ${targetFile.absolutePath}")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to copy CMake template files", e)
            throw e
        }
    }
    
    /**
     * 从 JAR 中复制 cmake_template 目录下的其他文件
     */
    private fun copyOtherTemplateFilesFromJar(targetDir: File) {
        // 使用 discoverResourceFiles 动态发现所有文件（包括 .vscode 目录下的文件）
        val allFiles = discoverResourceFiles("/assets/cmake_template", includeHidden = true)
        
        // 过滤掉 CMakeLists_*.txt 文件
        val filesToCopy = allFiles.filter { !it.startsWith("CMakeLists_") }
        
        logger.info("Found ${filesToCopy.size} files to copy from cmake_template")
        
        // 复制所有文件
        filesToCopy.forEach { relPath ->
            try {
                val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$relPath")
                if (resourceStream != null) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs() // 确保目录存在（包括 .vscode 等子目录）
                    resourceStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    logger.info("Copied $relPath from JAR to ${targetFile.absolutePath}")
                } else {
                    logger.warn("Template file not found: $relPath")
                }
            } catch (e: Exception) {
                logger.error("Failed to copy $relPath", e)
            }
        }
    }
    
    /**
     * 复制 EGE 库文件
     * @param targetDir 目标目录
     * @param useSourceCode 是否使用源码版本（如果是源码版本，需要复制 ege_src，否则复制 ege_bundle）
     * @param indicator 进度指示器
     */
    fun copyEgeLibrary(targetDir: File, useSourceCode: Boolean, indicator: ProgressIndicator? = null) {
        val egeDir = File(targetDir, "ege")
        
        // 如果 ege 目录已存在，先删除
        if (egeDir.exists()) {
            logger.info("EGE directory already exists, deleting: ${egeDir.absolutePath}")
            egeDir.deleteRecursively()
        }
        
        // 创建新的 ege 目录
        egeDir.mkdirs()
        
        // 根据选项决定复制哪个目录
        val bundlePath = if (useSourceCode) "/assets/ege_src" else "/assets/ege_bundle"
        
        // 使用辅助类复制资源
        copyResourceDirectory(bundlePath, egeDir, indicator)
    }
}
