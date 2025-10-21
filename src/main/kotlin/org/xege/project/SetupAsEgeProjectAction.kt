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
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project is currently open")
            Messages.showErrorDialog(
                "请先打开一个项目",
                "无法初始化 EGE 项目"
            )
            return
        }
        
        logger.info("SetupAsEgeProjectAction triggered for project: ${project.name}")
        
        // 获取项目根目录
        val basePath = project.basePath
        if (basePath == null) {
            logger.warn("Project base path is null")
            Messages.showErrorDialog(
                "无法获取项目路径",
                "错误"
            )
            return
        }
        
        val projectDir = File(basePath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            logger.warn("Project directory does not exist or is not a directory: $basePath")
            Messages.showErrorDialog(
                "项目目录不存在或不是有效的目录",
                "错误"
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
                "检测到目录中已存在 EGE 相关文件（ege 目录或 CMakeLists.txt）。\n" +
                        "继续操作将覆盖这些文件。是否继续？",
                "确认覆盖",
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
        private val useSourceCheckBox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
        
        val useSourceCode: Boolean
            get() = useSourceCheckBox.isSelected
        
        init {
            title = "初始化为 EGE 项目 - 选项配置"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            
            // 创建选项面板
            val optionsPanel = JPanel()
            optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
            optionsPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            // 添加说明标签
            val descriptionLabel = JLabel(
                "<html><body style='width: 400px'>" +
                        "<b>选择项目依赖方式：</b><br><br>" +
                        "• <b>不勾选（推荐）</b>：使用预编译的 EGE 静态库<br>" +
                        "  优点：编译速度快，项目结构简单<br><br>" +
                        "• <b>勾选</b>：直接使用 EGE 源代码<br>" +
                        "  优点：可以查看和修改 EGE 内部实现，适合高级用户" +
                        "</body></html>"
            )
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
            "正在初始化 EGE 项目...", 
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "正在初始化 EGE 项目结构..."
                
                try {
                    // 复制 CMake 模板文件
                    indicator.fraction = 0.2
                    indicator.text = "正在复制 CMake 模板文件..."
                    ResourceCopyHelper.copyCMakeTemplateFiles(projectDir, useSourceCode)
                    
                    // 复制 EGE 库文件
                    indicator.fraction = 0.5
                    indicator.text = if (useSourceCode) {
                        "正在复制 EGE 源码..."
                    } else {
                        "正在复制 EGE 库文件..."
                    }
                    ResourceCopyHelper.copyEgeLibrary(projectDir, useSourceCode, indicator)
                    
                    indicator.fraction = 0.9
                    indicator.text = "正在完成初始化..."
                    
                    // 刷新文件系统
                    val virtualFile = VfsUtil.findFileByIoFile(projectDir, true)
                    virtualFile?.refresh(false, true)
                    
                    indicator.fraction = 1.0
                    indicator.text = "项目初始化完成！"
                    
                    logger.info("EGE project initialized successfully at: ${projectDir.absolutePath}")
                    
                    // 在 EDT 线程上显示成功消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "EGE 项目初始化成功！\n\n" +
                                    "已创建的文件：\n" +
                                    "• CMakeLists.txt - CMake 构建配置\n" +
                                    "• main.cpp - 示例源文件\n" +
                                    "• ege/ - EGE ${if (useSourceCode) "源码" else "库文件"}目录\n\n" +
                                    "CLion 将自动重新加载项目配置。",
                            "初始化完成"
                        )
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to initialize EGE project", e)
                    
                    // 在 EDT 线程上显示错误消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "初始化 EGE 项目失败：${e.message}",
                            "错误"
                        )
                    }
                }
            }
        })
    }
}
