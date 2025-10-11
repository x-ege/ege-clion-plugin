package org.xege.project

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * 创建 EGE 项目的 Action
 * 用于在 CLion 中通过菜单创建 EGE 项目
 */
class CreateEgeProjectAction : AnAction() {
    private val logger = Logger.getInstance(CreateEgeProjectAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        logger.info("CreateEgeProjectAction triggered")
        
        // 显示文件选择器
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Location for EGE Project"
        descriptor.description = "Choose where to create your EGE project"
        
        val chooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            descriptor,
            e.project,
            null
        )
        
        if (chooser == null) {
            logger.info("User cancelled project creation")
            return
        }
        
        val projectPath = chooser.path
        logger.info("Selected project path: $projectPath")
        
        // 检查目录是否为空
        val dir = File(projectPath)
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            val confirmResult = Messages.showYesNoDialog(
                e.project,
                "The selected directory is not empty. Continue anyway?",
                "Directory Not Empty",
                Messages.getWarningIcon()
            )
            if (confirmResult != Messages.YES) {
                return
            }
        }
        
        // 显示选项对话框
        val dialog = ProjectOptionsDialog(e.project)
        if (!dialog.showAndGet()) {
            logger.info("User cancelled project creation")
            return
        }
        
        val useSourceCode = dialog.useSourceCode
        logger.info("Use source code option: $useSourceCode")
        
        // 创建项目
        createEgeProject(projectPath, useSourceCode)
    }
    
    /**
     * 项目选项对话框
     */
    private class ProjectOptionsDialog(project: com.intellij.openapi.project.Project?) : DialogWrapper(project) {
        private val useSourceCheckBox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
        
        val useSourceCode: Boolean
            get() = useSourceCheckBox.isSelected
        
        init {
            title = "EGE Project Options"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            panel.add(useSourceCheckBox, BorderLayout.CENTER)
            return panel
        }
    }
    
    private fun createEgeProject(projectPath: String, useSourceCode: Boolean) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Creating EGE Project...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "Creating EGE project structure..."
                
                try {
                    val targetDir = File(projectPath)
                    targetDir.mkdirs()
                    
                    // 使用 EgeProjectGenerator 中的复制逻辑
                    val generator = EgeProjectGenerator()
                    
                    // 复制 CMake 模板文件
                    indicator.fraction = 0.2
                    indicator.text = "Copying CMake templates..."
                    copyCMakeTemplateFiles(targetDir, useSourceCode)
                    
                    // 复制 EGE 库文件
                    indicator.fraction = 0.5
                    indicator.text = "Copying EGE library..."
                    copyEgeBundle(targetDir, indicator, useSourceCode)
                    
                    indicator.fraction = 0.9
                    indicator.text = "Finalizing..."
                    
                    // 刷新文件系统
                    val virtualFile = VfsUtil.findFileByIoFile(targetDir, true)
                    virtualFile?.refresh(false, true)
                    
                    indicator.fraction = 1.0
                    indicator.text = "Project created successfully!"
                    
                    logger.info("EGE project created successfully at: $projectPath")
                    
                    // 显示成功消息
                    Messages.showInfoMessage(
                        "EGE project created successfully at:\n$projectPath\n\nYou can open it using File → Open...",
                        "Project Created"
                    )
                    
                } catch (e: Exception) {
                    logger.error("Failed to create EGE project", e)
                    Messages.showErrorDialog(
                        "Failed to create EGE project: ${e.message}",
                        "Error"
                    )
                }
            }
        })
    }
    
    /**
     * 复制 CMake 模板文件
     * @param targetDir 目标目录
     * @param useSourceCode 是否使用源码版本
     */
    private fun copyCMakeTemplateFiles(targetDir: File, useSourceCode: Boolean) {
        // 根据选项决定使用哪个 CMakeLists 模板
        val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
        
        val templateFiles = mapOf(
            cmakeTemplate to "CMakeLists.txt",
            "main.cpp" to "main.cpp"
        )
        
        templateFiles.forEach { (sourceFile, targetFile) ->
            try {
                val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$sourceFile")
                if (resourceStream != null) {
                    val content = resourceStream.bufferedReader().use { it.readText() }
                    val file = File(targetDir, targetFile)
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
     * 复制 EGE 库文件
     * @param targetDir 目标目录
     * @param indicator 进度指示器
     * @param useSourceCode 是否使用源码版本(如果是源码版本，需要复制 ege_src，否则复制 ege_bundle)
     */
    private fun copyEgeBundle(targetDir: File, indicator: ProgressIndicator, useSourceCode: Boolean) {
        val egeDir = File(targetDir, "ege")
        egeDir.mkdirs()
        
        // 根据选项决定复制哪个目录
        val bundlePath = if (useSourceCode) "/assets/ege_src" else "/assets/ege_bundle"
        
        // 递归复制资源
        copyResourceDirectory(bundlePath, egeDir, indicator)
    }
    
    /**
     * 递归复制资源目录
     */
    private fun copyResourceDirectory(resourcePath: String, targetDir: File, indicator: ProgressIndicator) {
        try {
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource directory not found: $resourcePath")
                return
            }
            
            val uri = resourceUrl.toURI()
            
            if (uri.scheme == "jar") {
                copyFromJar(resourcePath, targetDir)
            } else {
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
    private fun copyFromJar(resourcePath: String, targetDir: File) {
        val jarFile = javaClass.protectionDomain.codeSource.location.toURI()
        
        try {
            java.util.jar.JarFile(File(jarFile)).use { jar ->
                val entries = jar.entries()
                val normalizedPath = resourcePath.removePrefix("/")
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    
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
            throw e
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
