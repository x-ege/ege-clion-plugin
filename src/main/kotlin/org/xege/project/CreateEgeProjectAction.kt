package org.xege.project

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
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
                    
                    // 在 EDT 线程上显示成功消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            "EGE project created successfully at:\n$projectPath\n\nYou can open it using File → Open...",
                            "Project Created"
                        )
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to create EGE project", e)
                    
                    // 在 EDT 线程上显示错误消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            "Failed to create EGE project: ${e.message}",
                            "Error"
                        )
                    }
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
        try {
            // 1. 复制 CMakeLists.txt（根据选项选择模板）
            val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
            val cmakeStream = javaClass.getResourceAsStream("/assets/cmake_template/$cmakeTemplate")
            if (cmakeStream != null) {
                val content = cmakeStream.bufferedReader().use { it.readText() }
                val file = File(targetDir, "CMakeLists.txt")
                file.writeText(content)
                logger.info("Copied $cmakeTemplate to ${file.absolutePath}")
            } else {
                logger.error("CMake template not found: /assets/cmake_template/$cmakeTemplate")
                throw RuntimeException("CMake 模板文件不存在")
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
                        if (file.isFile && !file.name.startsWith("CMakeLists_") && !file.name.startsWith(".")) {
                            val targetFile = File(targetDir, file.name)
                            file.copyTo(targetFile, overwrite = true)
                            logger.info("Copied ${file.name} to ${targetFile.absolutePath}")
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
        // 使用更可靠的方法：直接尝试复制已知的文件
        // 这比尝试遍历 JAR 更可靠
        val knownTemplateFiles = listOf(
            "main.cpp"
            // 在这里添加其他模板文件
        )
        
        knownTemplateFiles.forEach { fileName ->
            try {
                val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$fileName")
                if (resourceStream != null) {
                    val targetFile = File(targetDir, fileName)
                    resourceStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    logger.info("Copied $fileName from JAR to ${targetFile.absolutePath}")
                } else {
                    logger.warn("Template file not found: $fileName")
                }
            } catch (e: Exception) {
                logger.error("Failed to copy $fileName", e)
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
     * 使用类加载器直接访问资源，避免依赖 protectionDomain
     */
    private fun copyFromJar(resourcePath: String, targetDir: File) {
        logger.info("Copying resources from JAR: $resourcePath")
        // 直接使用类加载器方法，它更可靠
        copyFromClassLoader(resourcePath, targetDir)
    }
    
    /**
     * 从类加载器复制资源
     */
    private fun copyFromClassLoader(resourcePath: String, targetDir: File) {
        // 根据资源路径确定文件列表
        val knownFiles = when {
            resourcePath.contains("ege_bundle") -> listOf(
                // include 目录
                "include/ege.h",
                "include/ege.zh_CN.h",
                "include/ege/button.h",
                "include/ege/camera_capture.h",
                "include/ege/egecontrolbase.h",
                "include/ege/fps.h",
                "include/ege/label.h",
                "include/ege/stdint.h",
                "include/ege/sys_edit.h",
                "include/ege/types.h",
                "include/graphics.h",
                // lib 目录
                "lib/macOS/libgraphics.a",
                "lib/mingw-w64-debian/libgraphics.a",
                "lib/mingw64/MinGW-w64 GCC 8.1.0.txt",
                "lib/mingw64/mingw-w64-gcc-8.1.0-x86_64/libgraphics.a",
                "lib/vs2010/amd64/graphics.lib",
                "lib/vs2010/graphics.lib",
                "lib/vs2015/VS2015 Update3.txt",
                "lib/vs2015/amd64/graphics.lib",
                "lib/vs2015/graphics.lib",
                "lib/vs2017/VS2017 Community 15.9.63.txt",
                "lib/vs2017/x64/graphics.lib",
                "lib/vs2017/x86/graphics.lib",
                "lib/vs2019/x64/graphics.lib",
                "lib/vs2019/x86/graphics.lib",
                "lib/vs2022/x64/graphics.lib",
                "lib/vs2022/x86/graphics.lib"
            )
            resourcePath.contains("ege_src") -> {
                // TODO: 如果需要支持源码模式，在这里添加 ege_src 的文件列表
                logger.warn("ege_src file list not yet implemented")
                emptyList()
            }
            else -> emptyList()
        }
        
        logger.info("Copying ${knownFiles.size} files from $resourcePath")
        
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
                } else {
                    logger.warn("Resource not found: $fullPath")
                }
            } catch (e: Exception) {
                logger.error("Failed to copy $relPath: ${e.message}", e)
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
