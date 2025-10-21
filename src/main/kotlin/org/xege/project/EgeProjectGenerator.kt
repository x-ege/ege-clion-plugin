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
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.*

/**
 * EGE 项目设置
 * 保存项目创建选项
 */
data class EgeProjectSettings(
    val useSourceCode: Boolean = false,
    val cppStandard: String = "C++17"
)

/**
 * EGE 项目生成器的界面组件
 * 在新建项目向导中显示项目选项
 */
class EgeProjectGeneratorPeer : ProjectGeneratorPeer<EgeProjectSettings> {
    private val useSourceCodeCheckbox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
    private val cppStandardComboBox = JComboBox(arrayOf("C++11", "C++14", "C++17", "C++20", "C++23"))
    private val panel: JPanel = JPanel(BorderLayout())

    init {
        // 设置默认选择为 C++17
        cppStandardComboBox.selectedItem = "C++17"

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
        
        // 添加 C++ 标准选择
        val standardPanel = JPanel()
        standardPanel.layout = BoxLayout(standardPanel, BoxLayout.X_AXIS)
        standardPanel.alignmentX = Component.LEFT_ALIGNMENT
        standardPanel.add(JLabel("Language standard: "))
        standardPanel.add(Box.createHorizontalStrut(5))
        cppStandardComboBox.maximumSize = cppStandardComboBox.preferredSize
        standardPanel.add(cppStandardComboBox)
        standardPanel.add(Box.createHorizontalGlue())
        
        optionsPanel.add(standardPanel)

        panel.add(optionsPanel, BorderLayout.NORTH)
    }

    override fun getSettings(): EgeProjectSettings {
        return EgeProjectSettings(
            useSourceCode = useSourceCodeCheckbox.isSelected,
            cppStandard = cppStandardComboBox.selectedItem as String
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
 * 用于在 IDE 的新建项目向导中创建 EGE C++ 项目
 */
class EgeProjectGenerator : DirectoryProjectGenerator<EgeProjectSettings>() {
    private val logger = Logger.getInstance(EgeProjectGenerator::class.java)

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
        logger.info("Creating EgeProjectGeneratorPeer...")
        println("Creating EgeProjectGeneratorPeer...")
        return EgeProjectGeneratorPeer()
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

        // 递归复制资源
        copyResourceDirectory("/assets/ege_bundle", egeDir, indicator)
    }

    /**
     * 复制 ege_src 目录（EGE 源码）
     */
    private fun copyEgeSource(targetPath: File, indicator: ProgressIndicator) {
        val egeDir = File(targetPath, "ege")
        egeDir.mkdirs()

        // 递归复制 EGE 源码
        copyResourceDirectory("/assets/ege_src", egeDir, indicator)
    }

    /**
     * 递归复制资源目录
     */
    private fun copyResourceDirectory(resourcePath: String, targetDir: File, indicator: ProgressIndicator) {
        try {
            // 获取资源 URL
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                logger.warn("Resource directory not found: $resourcePath")
                return
            }

            // 使用类加载器获取资源列表
            val uri = resourceUrl.toURI()

            // 如果是 jar 文件，需要特殊处理
            if (uri.scheme == "jar") {
                copyFromJar(resourcePath, targetDir, indicator)
            } else {
                // 开发模式，直接从文件系统复制
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
    private fun copyFromJar(resourcePath: String, targetDir: File, indicator: ProgressIndicator) {
        logger.info("Copying resources from JAR: $resourcePath")
        // 直接使用 fallback 方法，它更可靠
        copyFromClassLoader(resourcePath, targetDir)
    }

    /**
     * 从类加载器复制资源（fallback 方法）
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

            resourcePath.contains("ege_src") -> listOf(
                // CMakeLists.txt
                "CMakeLists.txt",
                // include 目录
                "include/ege.h",
                "include/ege.zh_CN.h",
                "include/graphics.h",
                "include/ege/button.h",
                "include/ege/camera_capture.h",
                "include/ege/egecontrolbase.h",
                "include/ege/fps.h",
                "include/ege/label.h",
                "include/ege/stdint.h",
                "include/ege/sys_edit.h",
                "include/ege/types.h",
                // src 目录 - 添加所有源文件
                "src/array.h",
                "src/camera_capture.cpp",
                "src/color.cpp",
                "src/color.h",
                "src/compress.cpp",
                "src/console.cpp",
                "src/console.h",
                "src/crt_compat.cpp",
                "src/debug.cpp",
                "src/debug.h",
                "src/ege_base.h",
                "src/ege_common.h",
                "src/ege_def.h",
                "src/ege_dllimport.cpp",
                "src/ege_dllimport.h",
                "src/ege_extension.h",
                "src/ege_graph.h",
                "src/ege_head.h",
                "src/ege_math.h",
                "src/ege_time.h",
                "src/egecontrolbase.cpp",
                "src/egegapi.cpp",
                "src/encodeconv.cpp",
                "src/encodeconv.h",
                "src/font.cpp",
                "src/font.h",
                "src/gdi_conv.cpp",
                "src/gdi_conv.h",
                "src/graphics.cpp",
                "src/image.cpp",
                "src/image.h",
                "src/keyboard.cpp",
                "src/keyboard.h",
                "src/logo.cpp",
                "src/math.cpp",
                "src/message.cpp",
                "src/message.h",
                "src/mouse.cpp",
                "src/mouse.h",
                "src/music.cpp",
                "src/music.h",
                "src/random.cpp",
                "src/sbt.h"
                // 注意：如果有更多文件，需要继续添加
            )

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
