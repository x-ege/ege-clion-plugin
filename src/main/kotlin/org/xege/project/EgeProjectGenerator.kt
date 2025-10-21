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
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.*

/**
 * 选择新建项目时的默认 Demo
 */
private val DEMO_OPTIONS = arrayOf("Hello World")

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
    private val useSourceCodeCheckbox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
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
        val descriptionLabel = JLabel(
            "<html><body style='width: 400px'>" +
                    "选择项目依赖方式：<br>" +
                    "• 不勾选：使用预编译的 EGE 静态库（推荐）<br>" +
                    "• 勾选：直接使用 EGE 源代码（可以查看和修改 EGE 内部实现）" +
                    "</body></html>"
        )
        optionsPanel.add(descriptionLabel)
        optionsPanel.add(Box.createVerticalStrut(10))

        // 添加复选框
        optionsPanel.add(useSourceCodeCheckbox)

        optionsPanel.add(Box.createVerticalStrut(15))

        val demoOptionPanel = JPanel()
        demoOptionPanel.layout = BoxLayout(demoOptionPanel, BoxLayout.X_AXIS)
        demoOptionPanel.alignmentX = Component.LEFT_ALIGNMENT
        demoOptionPanel.add(JLabel("Demo Template: "))
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

    override fun getName(): String = "Easy Graphics Engine"

    override fun getDescription(): String = "创建一个基于 EGE (Easy Graphics Engine) 的 C++ 图形项目"

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
                return ValidationResult("目录不为空，请选择一个空目录或不存在的目录")
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

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 EGE 项目...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "正在复制 EGE 模板文件..."

                try {
                    // 复制 cmake_template 目录中的所有文件到项目目录
                    copyTemplateFiles(baseDir, settings, indicator)

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
    private fun copyTemplateFiles(targetDir: VirtualFile, settings: EgeProjectSettings, indicator: ProgressIndicator) {
        val targetPath = File(targetDir.path)

        try {
            // 第一步：复制 cmake 模板文件 (30%)
            indicator.fraction = 0.1
            indicator.text = "正在复制 CMake 模板文件..."
            copyCMakeTemplateFiles(targetPath, settings.useSourceCode)

            // 第二步：根据选项复制对应的 EGE 资源
            indicator.fraction = 0.4
            if (settings.useSourceCode) {
                indicator.text = "正在复制 EGE 源码..."
                copyEgeSource(targetPath, indicator)
            } else {
                indicator.text = "正在复制 EGE 库文件..."
                copyEgeBundle(targetPath, indicator)
            }

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
    private fun copyCMakeTemplateFiles(targetPath: File, useSourceCode: Boolean) {

        try {
            // 1. 复制 CMakeLists.txt（根据选项选择模板）
            val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
            val cmakeStream = javaClass.getResourceAsStream("/assets/cmake_template/$cmakeTemplate")
            if (cmakeStream != null) {
                val content = cmakeStream.bufferedReader().use { it.readText() }
                val file = File(targetPath, "CMakeLists.txt")
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
                    copyOtherTemplateFilesFromJar(targetPath)
                } else {
                    // 从文件系统复制
                    val templateDir = File(uri)
                    templateDir.listFiles()?.forEach { file ->
                        if (file.isFile && !file.name.startsWith("CMakeLists_")) {
                            val targetFile = File(targetPath, file.name)
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
    private fun copyOtherTemplateFilesFromJar(targetPath: File) {
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
                    val targetFile = File(targetPath, fileName)
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
     * 复制 ege_bundle 目录（包含头文件和库文件）
     */
    private fun copyEgeBundle(targetPath: File, indicator: ProgressIndicator) {
        val egeDir = File(targetPath, "ege")
        egeDir.mkdirs()

        // 使用辅助类复制资源
        ResourceCopyHelper.copyResourceDirectory("/assets/ege_bundle", egeDir, indicator)
    }

    /**
     * 复制 ege_src 目录（EGE 源码）
     */
    private fun copyEgeSource(targetPath: File, indicator: ProgressIndicator) {
        val egeDir = File(targetPath, "ege")
        egeDir.mkdirs()

        // 使用辅助类复制资源
        ResourceCopyHelper.copyResourceDirectory("/assets/ege_src", egeDir, indicator)
    }
}
