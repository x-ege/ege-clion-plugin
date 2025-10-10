package org.xege.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import java.io.File
import javax.swing.*

/**
 * EGE 项目生成器
 * 用于在 IDE 的新建项目向导中创建 EGE C++ 项目
 */
class EgeProjectGenerator : DirectoryProjectGenerator<Any> {
    private val logger = Logger.getInstance(EgeProjectGenerator::class.java)
    
    override fun getName(): String = "EGE"
    
    override fun getLogo(): Icon? {
        return try {
            // 从插件资源中加载图标
            val imageUrl = javaClass.getResource("/assets/logo.png")
            if (imageUrl != null) {
                ImageIcon(imageUrl)
            } else {
                logger.warn("Logo file not found at /assets/logo.png")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to load logo", e)
            null
        }
    }
    
    override fun createPeer(): ProjectGeneratorPeer<Any> {
        return object : ProjectGeneratorPeer<Any> {
            override fun getSettings(): Any = Any()
            
            override fun getComponent(): JComponent {
                // 创建一个简单的面板显示说明信息
                return JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                    
                    add(JLabel("EGE C++ 图形库项目"))
                    add(Box.createVerticalStrut(10))
                    add(JLabel("项目将包含："))
                    add(JLabel("  • CMake 构建配置"))
                    add(JLabel("  • EGE 图形库头文件和库文件"))
                    add(JLabel("  • 示例代码 (main.cpp)"))
                }
            }
            
            override fun validate(): com.intellij.openapi.ui.ValidationInfo? = null
            
            override fun isBackgroundJobRunning(): Boolean = false
            
            override fun addSettingsListener(listener: ProjectGeneratorPeer.SettingsListener) {}
            
            override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {
                // 不需要额外的设置步骤
            }
        }
    }
    
    override fun validate(baseDirPath: String): ValidationResult {
        val dir = File(baseDirPath)
        
        // 检查目录是否存在
        if (dir.exists()) {
            // 检查目录是否为空
            val files = dir.listFiles()
            if (files != null && files.isNotEmpty()) {
                return ValidationResult("目录不为空，请选择一个空目录或不存在的目录")
            }
        }
        
        return ValidationResult.OK
    }
    
    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: Any,
        module: Module
    ) {
        logger.info("Starting EGE project generation at: ${baseDir.path}")
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 EGE 项目...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "正在复制 EGE 模板文件..."
                
                try {
                    // 复制 cmake_template 目录中的所有文件到项目目录
                    copyTemplateFiles(baseDir, indicator)
                    
                    indicator.fraction = 1.0
                    indicator.text = "EGE 项目创建完成！"
                    
                    logger.info("EGE project generated successfully at: ${baseDir.path}")
                } catch (e: Exception) {
                    logger.error("Failed to generate EGE project", e)
                    throw RuntimeException("创建 EGE 项目失败: ${e.message}", e)
                }
            }
        })
    }
    
    /**
     * 从插件资源中复制模板文件到目标目录
     */
    private fun copyTemplateFiles(targetDir: VirtualFile, indicator: ProgressIndicator) {
        val targetPath = File(targetDir.path)
        
        try {
            // 第一步：复制 cmake 模板文件 (30%)
            indicator.fraction = 0.1
            indicator.text = "正在复制 CMake 模板文件..."
            copyCMakeTemplateFiles(targetPath)
            
            // 第二步：复制 ege_bundle 目录 (70%)
            indicator.fraction = 0.4
            indicator.text = "正在复制 EGE 库文件..."
            copyEgeBundle(targetPath, indicator)
            
            indicator.fraction = 1.0
            indicator.text = "文件复制完成"
            
            // 刷新虚拟文件系统
            targetDir.refresh(false, true)
        } catch (e: Exception) {
            logger.error("Failed to copy template files", e)
            throw e
        }
    }
    
    /**
     * 复制 CMake 模板文件
     */
    private fun copyCMakeTemplateFiles(targetPath: File) {
        val templateFiles = mapOf(
            "CMakeLists_src.txt" to "CMakeLists.txt",
            "CMakeLists_lib.txt" to "ege/CMakeLists.txt",
            "main.cpp" to "main.cpp"
        )
        
        templateFiles.forEach { (sourceFile, targetFile) ->
            try {
                val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$sourceFile")
                if (resourceStream != null) {
                    val content = resourceStream.bufferedReader().use { it.readText() }
                    val file = File(targetPath, targetFile)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    logger.info("Copied $sourceFile to ${file.absolutePath}")
                } else {
                    logger.warn("Template file not found: /assets/cmake_template/$sourceFile")
                }
            } catch (e: Exception) {
                logger.error("Failed to copy $sourceFile", e)
                throw e
            }
        }
    }
    
    /**
     * 复制 ege_bundle 目录（包含头文件和库文件）
     */
    private fun copyEgeBundle(targetPath: File, indicator: ProgressIndicator) {
        val egeDir = File(targetPath, "ege")
        egeDir.mkdirs()
        
        // 递归复制资源
        copyResourceDirectory("/assets/ege_bundle", egeDir, indicator)
    }
    
    /**
     * 递归复制资源目录
     */
    private fun copyResourceDirectory(resourcePath: String, targetDir: File, indicator: ProgressIndicator) {
        try {
            // 获取资源 URL
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource directory not found: $resourcePath")
                return
            }
            
            // 使用类加载器获取资源列表
            val uri = resourceUrl.toURI()
            
            // 如果是 jar 文件，需要特殊处理
            if (uri.scheme == "jar") {
                copyFromJar(resourcePath, targetDir, indicator)
            } else {
                // 开发模式，直接从文件系统复制
                copyFromFileSystem(File(uri), targetDir)
            }
        } catch (e: Exception) {
            logger.error("Failed to copy resource directory: $resourcePath", e)
            throw e
        }
    }
    
    /**
     * 从 JAR 文件中复制资源
     */
    private fun copyFromJar(resourcePath: String, targetDir: File, indicator: ProgressIndicator) {
        val classLoader = javaClass.classLoader
        val jarFile = javaClass.protectionDomain.codeSource.location.toURI()
        
        try {
            java.util.jar.JarFile(File(jarFile)).use { jar ->
                val entries = jar.entries()
                val normalizedPath = resourcePath.removePrefix("/")
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    
                    // 只处理目标资源路径下的文件
                    if (entryName.startsWith(normalizedPath) && !entry.isDirectory) {
                        val relativePath = entryName.removePrefix(normalizedPath).removePrefix("/")
                        if (relativePath.isNotEmpty()) {
                            val targetFile = File(targetDir, relativePath)
                            targetFile.parentFile?.mkdirs()
                            
                            jar.getInputStream(entry).use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            logger.debug("Copied from JAR: $entryName -> ${targetFile.absolutePath}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to copy from JAR", e)
            // 如果从 JAR 复制失败，尝试直接从类加载器复制
            copyFromClassLoader(resourcePath, targetDir)
        }
    }
    
    /**
     * 从类加载器复制资源（fallback 方法）
     */
    private fun copyFromClassLoader(resourcePath: String, targetDir: File) {
        // 已知的文件列表
        val knownFiles = listOf(
            "include/ege.h",
            "include/ege.zh_CN.h",
            "include/graphics.h",
            "lib/mingw64/libgraphics.a",
            "lib/mingw64/libgraphics64.a"
        )
        
        knownFiles.forEach { relPath ->
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
                }
            } catch (e: Exception) {
                logger.warn("Failed to copy $relPath: ${e.message}")
            }
        }
    }
    
    /**
     * 从文件系统复制目录
     */
    private fun copyFromFileSystem(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            logger.warn("Source directory does not exist: ${sourceDir.absolutePath}")
            return
        }
        
        sourceDir.listFiles()?.forEach { file ->
            val targetFile = File(targetDir, file.name)
            if (file.isDirectory) {
                targetFile.mkdirs()
                copyFromFileSystem(file, targetFile)
            } else {
                file.copyTo(targetFile, overwrite = true)
                logger.debug("Copied: ${file.absolutePath} -> ${targetFile.absolutePath}")
            }
        }
    }
}
