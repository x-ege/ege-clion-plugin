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
            title = "创建 EGE 项目 - 选项配置"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            
            // 创建选项面板
            val optionsPanel = JPanel()
            optionsPanel.layout = javax.swing.BoxLayout(optionsPanel, javax.swing.BoxLayout.Y_AXIS)
            optionsPanel.border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            // 添加说明标签
            val descriptionLabel = javax.swing.JLabel("<html><body style='width: 400px'>" +
                    "<b>选择项目依赖方式：</b><br><br>" +
                    "• <b>不勾选（推荐）</b>：使用预编译的 EGE 静态库<br>" +
                    "  优点：编译速度快，项目结构简单<br><br>" +
                    "• <b>勾选</b>：直接使用 EGE 源代码<br>" +
                    "  优点：可以查看和修改 EGE 内部实现，适合高级用户" +
                    "</body></html>")
            optionsPanel.add(descriptionLabel)
            optionsPanel.add(javax.swing.Box.createVerticalStrut(15))
            
            // 添加复选框
            optionsPanel.add(useSourceCheckBox)
            
            panel.add(optionsPanel, BorderLayout.CENTER)
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
                    ResourceCopyHelper.copyCMakeTemplateFiles(targetDir, useSourceCode)
                    
                    // 复制 EGE 库文件
                    indicator.fraction = 0.5
                    indicator.text = "Copying EGE library..."
                    ResourceCopyHelper.copyEgeLibrary(targetDir, useSourceCode, indicator)
                    
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
}
