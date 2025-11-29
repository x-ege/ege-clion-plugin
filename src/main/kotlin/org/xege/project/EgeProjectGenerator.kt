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
 * Demo 选项数据类
 * @param displayName 显示名称（在下拉列表中显示）
 * @param fileName 文件名（实际的 .cpp 文件名，null 表示使用默认的 Hello World）
 */
data class DemoOption(
    val displayName: String,
    val fileName: String?
) {
    override fun toString(): String = displayName
}

/**
 * Demo 选项管理器
 * 负责从资源目录动态加载 Demo 列表
 */
object DemoOptionsManager {
    private val logger = Logger.getInstance(DemoOptionsManager::class.java)
    
    /**
     * 获取所有可用的 Demo 选项
     * 包括默认的 Hello World 和 ege_demos 目录下的所有 .cpp 文件
     */
    fun getDemoOptions(): Array<DemoOption> {
        val options = mutableListOf<DemoOption>()
        
        // 添加默认的 Hello World 选项
        options.add(DemoOption("Hello World", null))
        
        // 从 ege_demos 目录动态加载 Demo 文件
        try {
            val demoFiles = discoverDemoFiles()
            demoFiles.sorted().forEach { fileName ->
                // 将文件名转换为显示名称：去掉 .cpp 后缀，将下划线替换为空格
                val displayName = fileName
                    .removeSuffix(".cpp")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercaseChar() }
                    }
                options.add(DemoOption(displayName, fileName))
            }
            logger.info("Loaded ${options.size} demo options")
        } catch (e: Exception) {
            logger.error("Failed to load demo options", e)
        }
        
        return options.toTypedArray()
    }
    
    /**
     * 扫描 ege_demos 目录，发现所有 .cpp 文件
     */
    private fun discoverDemoFiles(): List<String> {
        val files = mutableListOf<String>()
        val resourcePath = "/assets/ege_demos"
        
        try {
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource directory not found: $resourcePath")
                return emptyList()
            }
            
            val uri = resourceUrl.toURI()
            
            if (uri.scheme == "jar") {
                // 从 JAR 文件扫描
                val jarPath = uri.toString().substringAfter("jar:file:").substringBefore("!")
                val jarFile = java.util.jar.JarFile(java.io.File(java.net.URI("file:$jarPath")))
                
                val prefix = resourcePath.removePrefix("/")
                jarFile.entries().asIterator().forEach { entry ->
                    val name = entry.name
                    if (name.startsWith(prefix) && !entry.isDirectory && name.endsWith(".cpp")) {
                        val fileName = name.substringAfterLast("/")
                        files.add(fileName)
                    }
                }
                jarFile.close()
            } else {
                // 从文件系统扫描
                val dir = java.io.File(uri)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".cpp")) {
                            files.add(file.name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to discover demo files in $resourcePath", e)
        }
        
        return files
    }
}

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
    val demoOption: DemoOption = DemoOptionsManager.getDemoOptions().first()
)

/**
 * EGE 项目生成器的界面组件
 * 在新建项目向导中显示项目选项
 */
class EgeProjectGeneratorPeer : ProjectGeneratorPeer<EgeProjectSettings> {
    private val useSourceCodeCheckbox = JCheckBox(XegeBundle.message("options.checkbox.use.source"), false)
    private val demoOptions = DemoOptionsManager.getDemoOptions()
    private val demoOptionComboBox = JComboBox(demoOptions)
    private val panel: JPanel = JPanel(BorderLayout())

    init {
        // 设置默认选择为数组的第一个元素
        demoOptionComboBox.selectedItem = demoOptions.first()

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
            demoOption = demoOptionComboBox.selectedItem as DemoOption
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
            ResourceCopyHelper.copyCMakeTemplateFiles(targetPath, settings.useSourceCode, settings.demoOption.fileName)

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
