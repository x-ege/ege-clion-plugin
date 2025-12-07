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
import org.xege.XegeBundle
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
    
    init {
        // 设置国际化的菜单文本
        templatePresentation.text = XegeBundle.message("menu.create.project")
        templatePresentation.description = XegeBundle.message("menu.create.project.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("CreateEgeProjectAction triggered")

        // 显示文件选择器
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = XegeBundle.message("create.dialog.title")
        descriptor.description = XegeBundle.message("create.dialog.description")

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
                XegeBundle.message("create.dialog.not.empty.message"),
                XegeBundle.message("create.dialog.not.empty.title"),
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

        val settings = dialog.settings
        logger.info("Project settings: useSourceCode=${settings.useSourceCode}, demo=${settings.demoOption}")

        // 创建项目
        createEgeProject(projectPath, settings)
    }

    /**
     * 项目选项对话框
     */
    private class ProjectOptionsDialog(project: com.intellij.openapi.project.Project?) : DialogWrapper(project) {
        private val peer = EgeProjectGeneratorPeer()

        val settings: EgeProjectSettings
            get() = peer.settings

        init {
            title = XegeBundle.message("create.options.dialog.title")
            init()
        }

        override fun createCenterPanel(): JComponent {
            return peer.component
        }
    }

    private fun createEgeProject(projectPath: String, settings: EgeProjectSettings) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, XegeBundle.message("create.task.title"), false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = XegeBundle.message("create.task.structure")

                try {
                    val targetDir = File(projectPath)
                    targetDir.mkdirs()

                    // 复制 CMake 模板文件
                    indicator.fraction = 0.2
                    indicator.text = XegeBundle.message("create.task.cmake")
                    ResourceCopyHelper.copyCMakeTemplateFiles(targetDir, settings.useSourceCode, settings.demoOption.fileName)

                    // 复制 EGE 库文件
                    indicator.fraction = 0.5
                    indicator.text = XegeBundle.message("create.task.library")
                    ResourceCopyHelper.copyEgeLibrary(targetDir, settings.useSourceCode, indicator)

                    indicator.fraction = 0.9
                    indicator.text = XegeBundle.message("create.task.finalizing")

                    // 刷新文件系统
                    val virtualFile = VfsUtil.findFileByIoFile(targetDir, true)
                    virtualFile?.refresh(false, true)

                    indicator.fraction = 1.0
                    indicator.text = XegeBundle.message("create.task.success")

                    logger.info("EGE project created successfully at: $projectPath")

                    // 在 EDT 线程上显示成功消息
                    ApplicationManager.getApplication().invokeLater {
                        val productName = com.intellij.openapi.application.ApplicationNamesInfo.getInstance().productName
                        // 如果是 CLion，询问是否打开项目
                        if (productName.equals("CLion", ignoreCase = true)) {
                            val result = Messages.showYesNoDialog(
                                null,
                                XegeBundle.message("create.success.open.message", projectPath),
                                XegeBundle.message("create.success.open.title"),
                                Messages.getQuestionIcon()
                            )
                            
                            if (result == Messages.YES) {
                                // 尝试打开项目
                                try {
                                    // 使用 ProjectUtil 打开项目
                                    // 注意：ProjectUtil 在不同版本中位置可能不同，这里使用反射或尝试直接调用
                                    // 为了兼容性，我们尝试使用 com.intellij.ide.impl.ProjectUtil
                                    val projectUtilClass = Class.forName("com.intellij.ide.impl.ProjectUtil")
                                    val openMethod = projectUtilClass.getMethod("openOrImport", String::class.java, com.intellij.openapi.project.Project::class.java, Boolean::class.javaPrimitiveType)
                                    openMethod.invoke(null, projectPath, null, false)
                                } catch (e: Exception) {
                                    logger.warn("Failed to open project using reflection, trying alternative method", e)
                                    // 如果反射失败，回退到只显示消息
                                    Messages.showInfoMessage(
                                        XegeBundle.message("create.success.message", projectPath),
                                        XegeBundle.message("create.success.title")
                                    )
                                }
                            }
                        } else {
                            Messages.showInfoMessage(
                                XegeBundle.message("create.success.message", projectPath),
                                XegeBundle.message("create.success.title")
                            )
                        }
                    }

                } catch (e: Exception) {
                    logger.error("Failed to create EGE project", e)

                    // 在 EDT 线程上显示错误消息
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            XegeBundle.message("create.error.message", e.message ?: "Unknown error"),
                            XegeBundle.message("create.error.title")
                        )
                    }
                }
            }
        })
    }
}
