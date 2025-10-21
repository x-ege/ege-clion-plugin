package org.xege.project

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import org.xege.XegeBundle
import java.awt.BorderLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 将当前项目初始化为 EGE 项目的 Action
 * 自动使用当前打开的项目目录作为 EGE 项目根目录
 */
class SetupAsEgeProjectAction : AnAction() {
    private val logger = Logger.getInstance(SetupAsEgeProjectAction::class.java)
    
    init {
        // 设置国际化的菜单文本
        templatePresentation.text = XegeBundle.message("menu.setup.project")
        templatePresentation.description = XegeBundle.message("menu.setup.project.description")
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project is currently open")
            Messages.showErrorDialog(
                XegeBundle.message("setup.error.no.project.message"),
                XegeBundle.message("setup.error.no.project.title")
            )
            return
        }
        
        logger.info("SetupAsEgeProjectAction triggered for project: ${project.name}")
        
        // 获取项目根目录
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("Project base path is null")
            Messages.showErrorDialog(
                XegeBundle.message("setup.error.no.path.message"),
                XegeBundle.message("setup.error.no.path.title")
            )
            return
        }
        
        val projectDir = File(basePath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            logger.warn("Project directory does not exist or is not a directory: $basePath")
            Messages.showErrorDialog(
                XegeBundle.message("setup.error.invalid.dir.message"),
                XegeBundle.message("setup.error.no.path.title")
            )
            return
        }
        
        logger.info("Using project directory: $basePath")
        
        // 检查目录是否已包含 EGE 相关文件
        val egeDir = File(projectDir, "ege")
        val cmakeFile = File(projectDir, "CMakeLists.txt")
        if (egeDir.exists() || cmakeFile.exists()) {
            val confirmResult = Messages.showYesNoDialog(
                project,
                XegeBundle.message("setup.confirm.overwrite.message"),
                XegeBundle.message("setup.confirm.overwrite.title"),
                Messages.getWarningIcon()
            )
            if (confirmResult != Messages.YES) {
                logger.info("User cancelled project setup due to existing files")
                return
            }
        }
        
        // 显示选项对话框
        val dialog = ProjectSetupOptionsDialog(project)
        if (!dialog.showAndGet()) {
            logger.info("User cancelled project setup")
            return
        }
        
        val useSourceCode = dialog.useSourceCode
        logger.info("Use source code option: $useSourceCode")
        
        // 初始化项目
        initializeEgeProject(project, projectDir, useSourceCode)
    }
    
    override fun update(e: AnActionEvent) {
        // 只有在有项目打开时才启用此 Action
        e.presentation.isEnabled = e.project != null
    }
    
    /**
     * 项目设置选项对话框
     */
    private class ProjectSetupOptionsDialog(project: Project?) : DialogWrapper(project) {
        private val useSourceCheckBox = JCheckBox(XegeBundle.message("options.checkbox.use.source"), false)
        
        val useSourceCode: Boolean
            get() = useSourceCheckBox.isSelected
        
        init {
            title = XegeBundle.message("setup.options.dialog.title")
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            
            // 创建选项面板
            val optionsPanel = JPanel()
            optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
            optionsPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            // 添加说明标签
            val descriptionLabel = JLabel(XegeBundle.message("options.label.title"))
            optionsPanel.add(descriptionLabel)
            optionsPanel.add(Box.createVerticalStrut(15))
            
            // 添加复选框
            optionsPanel.add(useSourceCheckBox)
            
            panel.add(optionsPanel, BorderLayout.CENTER)
            return panel
        }
    }
    
    /**
     * 初始化 EGE 项目
     */
    private fun initializeEgeProject(project: Project, projectDir: File, useSourceCode: Boolean) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            XegeBundle.message("setup.task.title"), 
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = XegeBundle.message("setup.task.initializing")
                
                try {
                    // 复制 CMake 模板文件
                    indicator.fraction = 0.2
                    indicator.text = XegeBundle.message("setup.task.cmake")
                    ResourceCopyHelper.copyCMakeTemplateFiles(projectDir, useSourceCode)
                    
                    // 复制 EGE 库文件
                    indicator.fraction = 0.5
                    indicator.text = if (useSourceCode) {
                        XegeBundle.message("setup.task.source")
                    } else {
                        XegeBundle.message("setup.task.library")
                    }
                    ResourceCopyHelper.copyEgeLibrary(projectDir, useSourceCode, indicator)
                    
                    indicator.fraction = 0.9
                    indicator.text = XegeBundle.message("setup.task.finalizing")
                    
                    // 刷新文件系统
                    val virtualFile = VfsUtil.findFileByIoFile(projectDir, true)
                    virtualFile?.refresh(false, true)
                    
                    indicator.fraction = 1.0
                    indicator.text = XegeBundle.message("setup.task.complete")
                    
                    logger.info("EGE project initialized successfully at: ${projectDir.absolutePath}")
                    
                    // 在 EDT 线程上显示成功消息
                    ApplicationManager.getApplication().invokeLater {
                        val typeText = if (useSourceCode) {
                            XegeBundle.message("setup.success.source")
                        } else {
                            XegeBundle.message("setup.success.library")
                        }
                        Messages.showInfoMessage(
                            project,
                            XegeBundle.message("setup.success.message", typeText),
                            XegeBundle.message("setup.success.title")
                        )
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to initialize EGE project", e)
                    
                    // 在 EDT 线程上显示错误消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            XegeBundle.message("setup.error.message", e.message ?: "Unknown error"),
                            XegeBundle.message("setup.error.title")
                        )
                    }
                }
            }
        })
    }
}
