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
     * @return 相对路径列表
     */
    private fun discoverResourceFiles(resourcePath: String): List<String> {
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
                        if (relativePath.isNotEmpty() && !relativePath.startsWith(".")) {
                            files.add(relativePath)
                        }
                    }
                }
                jarFile.close()
            } else {
                // 从文件系统扫描
                val dir = File(uri)
                if (dir.exists() && dir.isDirectory) {
                    collectFiles(dir, "", files)
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
     */
    private fun collectFiles(dir: File, prefix: String, files: MutableList<String>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val newPrefix = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
                collectFiles(file, newPrefix, files)
            } else if (!file.name.startsWith(".")) {
                val relativePath = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
                files.add(relativePath)
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
}
