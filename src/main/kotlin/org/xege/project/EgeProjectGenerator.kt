package org.xege.project

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import org.xege.XegeBundle
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.*

/**
 * 选择新建项目时的默认 Demo
 */
private val DEMO_OPTIONS = arrayOf("Hello World")

/**
 * 获取本地化的Demo选项标签
 */
private fun getDemoLabel(): String = XegeBundle.message("options.demo.label")

/**
 * EGE 项目设置
 * 保存项目创建选项
 */
data class EgeProjectSettings(
    val useSourceCode: Boolean = false,
    val demoOption: String = DEMO_OPTIONS.first()
)

/**
 * EGE 项目生成器的界面组件
 * 在新建项目向导中显示项目选项
 */
class EgeProjectGeneratorPeer : ProjectGeneratorPeer<EgeProjectSettings> {
    private val useSourceCodeCheckbox = JCheckBox(XegeBundle.message("options.checkbox.use.source"), false)
    private val demoOptionComboBox = JComboBox(DEMO_OPTIONS)
    private val panel: JPanel = JPanel(BorderLayout())

    init {
        // 设置默认选择为数组的第一个元素
        demoOptionComboBox.selectedItem = DEMO_OPTIONS.first()

        // 创建选项面板
        val optionsPanel = JPanel()
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
        optionsPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 添加说明标签
        val descriptionLabel = JLabel(XegeBundle.message("options.label.title"))
        optionsPanel.add(descriptionLabel)
        optionsPanel.add(Box.createVerticalStrut(10))

        // 添加复选框
        optionsPanel.add(useSourceCodeCheckbox)

        optionsPanel.add(Box.createVerticalStrut(15))

        val demoOptionPanel = JPanel()
        demoOptionPanel.layout = BoxLayout(demoOptionPanel, BoxLayout.X_AXIS)
        demoOptionPanel.alignmentX = Component.LEFT_ALIGNMENT
        demoOptionPanel.add(JLabel(getDemoLabel()))
        demoOptionPanel.add(Box.createHorizontalStrut(5))
        demoOptionComboBox.maximumSize = demoOptionComboBox.preferredSize
        demoOptionPanel.add(demoOptionComboBox)
        demoOptionPanel.add(Box.createHorizontalGlue())

        optionsPanel.add(demoOptionPanel)

        panel.add(optionsPanel, BorderLayout.NORTH)
    }

    override fun getSettings(): EgeProjectSettings {
        return EgeProjectSettings(
            useSourceCode = useSourceCodeCheckbox.isSelected,
            demoOption = demoOptionComboBox.selectedItem as String
        )
    }

    override fun getComponent(): JComponent {
        return panel
    }

    override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {
        // 不需要在这里添加组件，getComponent() 返回的组件会自动显示
        // 这个方法可以用于添加额外的设置字段到 settingsStep，但我们不需要
    }

    override fun validate(): ValidationInfo? {
        return null // 无验证错误
    }

    override fun isBackgroundJobRunning(): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun addSettingsStateListener(listener: com.intellij.platform.WebProjectGenerator.SettingsStateListener) {
        // 不需要监听器
    }
}

/**
 * EGE 项目生成器
 * 用于在 CLion 的新建项目向导中创建 EGE C++ 项目
 */
class EgeProjectGenerator : CLionProjectGenerator<EgeProjectSettings>() {
    private val logger = Logger.getInstance(EgeProjectGenerator::class.java)
    private var peer: EgeProjectGeneratorPeer? = null

    override fun getName(): String = XegeBundle.message("generator.name")

    override fun getDescription(): String = XegeBundle.message("generator.description")

    override fun getLogo(): Icon? {
        return try {
            // 加载插件图标并缩放到 16x16
            val originalIcon = IconLoader.findIcon("/META-INF/pluginIcon.svg", javaClass)
            if (originalIcon != null) {
                // 使用 IconUtil 缩放图标到 16x16
                com.intellij.util.IconUtil.scale(originalIcon, null, 16f / originalIcon.iconWidth)
            } else {
                logger.warn("Plugin icon not found at /META-INF/pluginIcon.svg")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to load or scale logo", e)
            null
        }
    }

    override fun createPeer(): ProjectGeneratorPeer<EgeProjectSettings> {
        if (peer == null) {
            logger.info("Creating EgeProjectGeneratorPeer...")
            println("Creating EgeProjectGeneratorPeer...")
            peer = EgeProjectGeneratorPeer()
        }
        return peer!!
    }

    override fun getSettingsPanel(): JPanel? {
        return createPeer().component as? JPanel
    }

    override fun validate(baseDirPath: String): ValidationResult {
        val dir = File(baseDirPath)

        // 检查目录是否存在
        if (dir.exists()) {
            // 检查目录是否为空
            val files = dir.listFiles()
            if (files != null && files.isNotEmpty()) {
                return ValidationResult(XegeBundle.message("generator.validation.not.empty"))
            }
        }

        return ValidationResult.OK
    }

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: EgeProjectSettings,
        module: Module
    ) {
        logger.info("Starting EGE project generation at: ${baseDir.path}, useSourceCode: ${settings.useSourceCode}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, XegeBundle.message("generator.task.title"), false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = XegeBundle.message("generator.task.copying.templates")

                try {
                    // 复制 cmake_template 目录中的所有文件到项目目录
                    copyTemplateFiles(baseDir, settings, indicator)

                    indicator.fraction = 1.0
                    indicator.text = XegeBundle.message("generator.task.complete")

                    logger.info("EGE project generated successfully at: ${baseDir.path}")
                } catch (e: Exception) {
                    logger.error("Failed to generate EGE project", e)
                    throw RuntimeException(XegeBundle.message("generator.error.failed", e.message ?: "Unknown error"), e)
                }
            }
        })
    }

    /**
     * 从插件资源中复制模板文件到目标目录
     */
    private fun copyTemplateFiles(targetDir: VirtualFile, settings: EgeProjectSettings, indicator: ProgressIndicator) {
        val targetPath = File(targetDir.path)

        try {
            // 第一步：复制 cmake 模板文件 (30%)
            indicator.fraction = 0.1
            indicator.text = XegeBundle.message("generator.task.copying.cmake")
            ResourceCopyHelper.copyCMakeTemplateFiles(targetPath, settings.useSourceCode)

            // 第二步：根据选项复制对应的 EGE 资源
            indicator.fraction = 0.4
            indicator.text = if (settings.useSourceCode) {
                XegeBundle.message("generator.task.copying.source")
            } else {
                XegeBundle.message("generator.task.copying.library")
            }
            ResourceCopyHelper.copyEgeLibrary(targetPath, settings.useSourceCode, indicator)

            indicator.fraction = 1.0
            indicator.text = XegeBundle.message("generator.task.file.copy.complete")

            // 刷新虚拟文件系统
            targetDir.refresh(false, true)
        } catch (e: Exception) {
            logger.error("Failed to copy template files", e)
            throw e
        }
    }
}
