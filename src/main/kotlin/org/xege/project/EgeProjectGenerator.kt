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
import java.awt.Dimension
import java.io.File
import javax.swing.*

/**
 * Demo 信息数据类
 * @param title 中文标题
 * @param description 功能描述
 * @param category 分类
 */
data class DemoInfo(
    val title: String,
    val description: String,
    val category: DemoCategory
)

/**
 * Demo 分类枚举
 */
enum class DemoCategory(val displayName: String) {
    BASIC("基础入门"),
    GAME("游戏示例"),
    GRAPHICS("图形绘制"),
    ALGORITHM("算法可视化"),
    PHYSICS("物理模拟"),
    FRACTAL("分形与数学"),
    IMAGE("图像处理"),
    CAMERA("摄像头")
}

/**
 * Demo 元数据注册表
 * 包含所有 Demo 的中文标题和描述
 */
object DemoMetadataRegistry {
    private val metadata: Map<String, DemoInfo> = mapOf(
        // 基础入门
        "main.cpp" to DemoInfo("Hello World", "最简单的EGE程序，初始化窗口并显示", DemoCategory.BASIC),
        
        // 摄像头
        "camera_base.cpp" to DemoInfo("摄像头基础", "调用系统摄像头，支持切换设备和分辨率", DemoCategory.CAMERA),
        "camera_wave.cpp" to DemoInfo("摄像头水波特效", "实时摄像头画面配合水波网格变形效果", DemoCategory.CAMERA),
        
        // 游戏示例
        "game_gomoku.cpp" to DemoInfo("五子棋", "经典五子棋游戏，带简单AI对手和音效", DemoCategory.GAME),
        "game_snake.cpp" to DemoInfo("贪吃蛇", "约90行代码实现的精简版贪吃蛇游戏", DemoCategory.GAME),
        "game_tetris.cpp" to DemoInfo("俄罗斯方块", "完整的俄罗斯方块游戏实现", DemoCategory.GAME),
        "game_type.cpp" to DemoInfo("打字练习", "字母下落式打字练习小游戏", DemoCategory.GAME),
        
        // 图形绘制
        "graph_5star.cpp" to DemoInfo("五角星旋转", "绘制五角星并展示旋转动画效果", DemoCategory.GRAPHICS),
        "graph_alpha.cpp" to DemoInfo("Alpha透明度", "演示Alpha通道透明度与图层混合", DemoCategory.GRAPHICS),
        "graph_arrow.cpp" to DemoInfo("箭头绘制", "多种箭头绘制算法演示", DemoCategory.GRAPHICS),
        "graph_clock.cpp" to DemoInfo("模拟时钟", "绘制带时针分针秒针的模拟时钟", DemoCategory.GRAPHICS),
        "graph_getimage.cpp" to DemoInfo("图片加载", "演示如何加载显示PNG/JPG图片", DemoCategory.IMAGE),
        "graph_lines.cpp" to DemoInfo("变幻线", "多边形变幻线屏保特效", DemoCategory.GRAPHICS),
        "graph_new_drawimage.cpp" to DemoInfo("图像变换", "PIMAGE图像绘制与矩阵变换", DemoCategory.IMAGE),
        "graph_rotateimage.cpp" to DemoInfo("图片旋转", "图片旋转缩放动画演示", DemoCategory.IMAGE),
        "graph_rotatetransparent.cpp" to DemoInfo("透明旋转", "带透明背景的图片旋转", DemoCategory.IMAGE),
        "graph_star.cpp" to DemoInfo("星空屏保", "满天繁星流动的屏保效果", DemoCategory.GRAPHICS),
        "graph_triangle.cpp" to DemoInfo("渐变三角形", "彩色渐变填充三角形动画", DemoCategory.GRAPHICS),
        
        // 算法可视化
        "graph_astar_pathfinding.cpp" to DemoInfo("A*寻路算法", "A*路径搜索算法的可视化演示", DemoCategory.ALGORITHM),
        "graph_sort_visualization.cpp" to DemoInfo("排序算法可视化", "11种排序算法的动态可视化对比", DemoCategory.ALGORITHM),
        "graph_kmeans.cpp" to DemoInfo("K-Means聚类", "K-Means机器学习算法可视化", DemoCategory.ALGORITHM),
        "graph_game_of_life.cpp" to DemoInfo("生命游戏", "康威生命游戏元胞自动机模拟", DemoCategory.ALGORITHM),
        "graph_function_visualization.cpp" to DemoInfo("函数图像", "基于蒙特卡洛法的2D数学函数绘制", DemoCategory.FRACTAL),
        
        // 物理模拟
        "graph_ball.cpp" to DemoInfo("弹球碰撞", "多彩弹球物理碰撞模拟", DemoCategory.PHYSICS),
        "graph_boids.cpp" to DemoInfo("群集模拟", "Boids算法模拟鸟群/鱼群行为", DemoCategory.PHYSICS),
        "graph_mouseball.cpp" to DemoInfo("鼠标拖动弹球", "用鼠标拖动弹球的物理模拟", DemoCategory.PHYSICS),
        "graph_wave_net.cpp" to DemoInfo("碧波荡漾", "鼠标拖动弹力网格物理模拟", DemoCategory.PHYSICS),
        
        // 分形与数学
        "graph_julia.cpp" to DemoInfo("Julia集", "Julia分形集屏保动画", DemoCategory.FRACTAL),
        "graph_mandelbrot.cpp" to DemoInfo("Mandelbrot集", "鼠标缩放Mandelbrot分形集", DemoCategory.FRACTAL),
        "graph_catharine.cpp" to DemoInfo("烟花特效", "绚丽的烟花粒子效果", DemoCategory.GRAPHICS)
    )
    
    /**
     * 获取指定文件的元数据
     */
    fun getInfo(fileName: String?): DemoInfo? {
        if (fileName == null) return metadata["main.cpp"]
        return metadata[fileName]
    }
    
    /**
     * 获取默认的 Hello World 信息
     */
    fun getDefaultInfo(): DemoInfo = metadata["main.cpp"]!!
}

/**
 * Demo 选项数据类
 * @param displayName 显示名称（在下拉列表中显示）
 * @param fileName 文件名（实际的 .cpp 文件名，null 表示使用默认的 Hello World）
 * @param info Demo 的详细信息
 */
data class DemoOption(
    val displayName: String,
    val fileName: String?,
    val info: DemoInfo? = DemoMetadataRegistry.getInfo(fileName)
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
        val defaultInfo = DemoMetadataRegistry.getDefaultInfo()
        options.add(DemoOption(defaultInfo.title, null, defaultInfo))
        
        // 从 ege_demos 目录动态加载 Demo 文件
        try {
            val demoFiles = discoverDemoFiles()
            demoFiles.sorted().forEach { fileName ->
                val info = DemoMetadataRegistry.getInfo(fileName)
                val displayName = info?.title ?: generateDisplayName(fileName)
                options.add(DemoOption(displayName, fileName, info))
            }
            logger.info("Loaded ${options.size} demo options")
        } catch (e: Exception) {
            logger.error("Failed to load demo options", e)
        }
        
        return options.toTypedArray()
    }
    
    /**
     * 按分类获取 Demo 选项
     */
    fun getDemoOptionsByCategory(): Map<DemoCategory, List<DemoOption>> {
        val options = getDemoOptions().toList()
        return options.filter { it.info != null }
            .groupBy { it.info!!.category }
    }
    
    /**
     * 从文件名生成默认显示名称（当没有元数据时使用）
     */
    private fun generateDisplayName(fileName: String): String {
        return fileName
            .removeSuffix(".cpp")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }
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
 * Demo 列表单元格渲染器
 * 在列表中显示 Demo 的标题和描述
 */
class DemoListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        
        if (value is DemoOption) {
            val info = value.info
            if (info != null) {
                // 使用 HTML 格式显示标题和描述
                text = "<html><b>${info.title}</b><br><font color='gray' size='-1'>${info.description}</font></html>"
            } else {
                text = value.displayName
            }
        }
        
        return this
    }
}

/**
 * Demo ComboBox 渲染器
 * 在下拉框中显示 Demo 的标题和描述
 */
class DemoComboBoxRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        
        if (value is DemoOption) {
            val info = value.info
            if (info != null) {
                if (index == -1) {
                    // 选中状态下只显示标题
                    text = info.title
                } else {
                    // 下拉列表中显示标题和描述
                    text = "<html><b>${info.title}</b> - <font color='gray'>${info.description}</font></html>"
                }
            } else {
                text = value.displayName
            }
        }
        
        return this
    }
}

/**
 * EGE 项目生成器的界面组件
 * 在新建项目向导中显示项目选项
 */
class EgeProjectGeneratorPeer : ProjectGeneratorPeer<EgeProjectSettings> {
    private val useSourceCodeCheckbox = JCheckBox(XegeBundle.message("options.checkbox.use.source"), false)
    private val demoOptions = DemoOptionsManager.getDemoOptions()
    private val demoOptionComboBox = JComboBox(demoOptions)
    private val descriptionLabel = JLabel()
    private val panel: JPanel = JPanel(BorderLayout())

    init {
        // 设置默认选择为数组的第一个元素
        demoOptionComboBox.selectedItem = demoOptions.first()
        
        // 设置自定义渲染器
        demoOptionComboBox.renderer = DemoComboBoxRenderer()
        
        // 监听选择变化，更新描述
        demoOptionComboBox.addActionListener {
            updateDescription()
        }

        // 创建选项面板
        val optionsPanel = JPanel()
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.Y_AXIS)
        optionsPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 添加说明标签
        val titleLabel = JLabel(XegeBundle.message("options.label.title"))
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        optionsPanel.add(titleLabel)
        optionsPanel.add(Box.createVerticalStrut(10))

        // 添加复选框
        useSourceCodeCheckbox.alignmentX = Component.LEFT_ALIGNMENT
        optionsPanel.add(useSourceCodeCheckbox)

        optionsPanel.add(Box.createVerticalStrut(15))

        // Demo 选择区域
        val demoLabel = JLabel(getDemoLabel())
        demoLabel.alignmentX = Component.LEFT_ALIGNMENT
        optionsPanel.add(demoLabel)
        optionsPanel.add(Box.createVerticalStrut(5))
        
        // Demo 下拉框
        demoOptionComboBox.alignmentX = Component.LEFT_ALIGNMENT
        demoOptionComboBox.maximumSize = Dimension(Int.MAX_VALUE, demoOptionComboBox.preferredSize.height)
        optionsPanel.add(demoOptionComboBox)
        
        optionsPanel.add(Box.createVerticalStrut(10))
        
        // 描述区域
        descriptionLabel.alignmentX = Component.LEFT_ALIGNMENT
        descriptionLabel.verticalAlignment = SwingConstants.TOP
        
        // 使用带边框的面板显示描述
        val descPanel = JPanel(BorderLayout())
        descPanel.alignmentX = Component.LEFT_ALIGNMENT
        descPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("示例说明"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
        descPanel.add(descriptionLabel, BorderLayout.CENTER)
        descPanel.maximumSize = Dimension(Int.MAX_VALUE, 80)
        descPanel.preferredSize = Dimension(300, 80)
        
        optionsPanel.add(descPanel)
        
        // 初始化描述
        updateDescription()

        panel.add(optionsPanel, BorderLayout.NORTH)
    }
    
    /**
     * 更新描述标签内容
     */
    private fun updateDescription() {
        val selectedOption = demoOptionComboBox.selectedItem as? DemoOption
        if (selectedOption != null && selectedOption.info != null) {
            val info = selectedOption.info
            descriptionLabel.text = "<html><b>分类：</b>${info.category.displayName}<br><b>功能：</b>${info.description}</html>"
        } else {
            descriptionLabel.text = "<html><b>分类：</b>基础入门<br><b>功能：</b>最简单的EGE程序</html>"
        }
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
