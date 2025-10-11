# UI 改进文档

## 修改概述

本次修改主要解决了以下几个问题：

1. 移除了新建项目 Action 的图标（只显示文字）
2. 为插件本身添加了图标（显示在插件安装界面）
3. 添加了"直接使用 EGE 源码作为项目依赖"的选项
4. 根据用户选择，使用不同的 CMakeLists.txt 模板

## 详细修改

### 1. 移除 Action 图标

**位置**: `src/main/kotlin/org/xege/project/CreateEgeProjectAction.kt`

**修改内容**:
- 移除了 `init` 块中的图标加载代码
- 移除了相关的 `Icon` 和 `ImageIcon` 导入

**原因**: 在新建项目界面，图标显示过大，影响用户体验。只显示文字更简洁。

### 2. 添加插件图标

**位置**: `src/main/resources/META-INF/pluginIcon.svg`

**修改内容**:
- 创建了一个 40x40 的 SVG 图标
- 蓝色背景（#3369D6）
- 白色的 "EGE" 文字

**使用方式**: 
IntelliJ Platform 会自动识别 `META-INF/pluginIcon.svg` 文件，并在插件管理界面显示该图标。

### 3. 添加项目选项对话框

**位置**: `src/main/kotlin/org/xege/project/CreateEgeProjectAction.kt`

**修改内容**:
- 创建了内部类 `ProjectOptionsDialog` 继承自 `DialogWrapper`
- 添加了一个 CheckBox: "直接使用 EGE 源码作为项目依赖"
- 默认不选中

**实现方式**:
```kotlin
private class ProjectOptionsDialog(project: Project?) : DialogWrapper(project) {
    private val useSourceCheckBox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
    
    val useSourceCode: Boolean
        get() = useSourceCheckBox.isSelected
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(useSourceCheckBox, BorderLayout.CENTER)
        return panel
    }
}
```

### 4. 根据选项使用不同的 CMakeLists 模板

**位置**: `src/main/kotlin/org/xege/project/CreateEgeProjectAction.kt`

**修改内容**:

#### copyCMakeTemplateFiles 函数
- 添加了 `useSourceCode` 参数
- 根据参数值选择不同的模板：
  - `useSourceCode = true`: 使用 `CMakeLists_src.txt`
  - `useSourceCode = false`: 使用 `CMakeLists_lib.txt`

```kotlin
private fun copyCMakeTemplateFiles(targetDir: File, useSourceCode: Boolean) {
    val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
    
    val templateFiles = mapOf(
        cmakeTemplate to "CMakeLists.txt",
        "main.cpp" to "main.cpp"
    )
    // ...
}
```

#### copyEgeBundle 函数
- 添加了 `useSourceCode` 参数
- 根据参数值复制不同的目录：
  - `useSourceCode = true`: 复制 `/assets/ege_src`（源码版本）
  - `useSourceCode = false`: 复制 `/assets/ege_bundle`（预编译库版本）

```kotlin
private fun copyEgeBundle(targetDir: File, indicator: ProgressIndicator, useSourceCode: Boolean) {
    val egeDir = File(targetDir, "ege")
    egeDir.mkdirs()
    
    val bundlePath = if (useSourceCode) "/assets/ege_src" else "/assets/ege_bundle"
    copyResourceDirectory(bundlePath, egeDir, indicator)
}
```

## 两种模板的区别

### CMakeLists_lib.txt（预编译库版本）
- 使用预编译的静态库
- 根据不同的编译器（MSVC、MinGW）选择对应的库文件
- 需要链接到 `ege` 静态库

### CMakeLists_src.txt（源码版本）
- 直接编译 EGE 源码
- 使用 `add_subdirectory(ege)` 将 EGE 作为子项目
- 链接到 `xege` 目标（从源码构建）

## 用户体验流程

1. 用户选择 "File → New EGE Project..." 或在欢迎页面选择 "New EGE Project..."
2. 选择项目位置（文件选择器）
3. 如果目录非空，提示用户确认是否继续
4. 显示选项对话框，用户可以选择是否使用源码版本
5. 创建项目，根据用户选择复制相应的文件
6. 显示成功消息

## 技术要点

### DialogWrapper 的使用
IntelliJ Platform 推荐使用 `DialogWrapper` 而不是直接使用 Swing 对话框。这样可以保证对话框的样式和行为与 IDE 一致。

### 文件复制逻辑
- 支持从 JAR 中复制资源（发布版本）
- 支持从文件系统复制资源（开发版本）
- 递归复制整个目录结构

### 进度指示
使用 `ProgressManager` 和 `ProgressIndicator` 提供友好的进度提示。

## 测试建议

1. 测试插件图标是否显示在插件管理界面
2. 测试 Action 菜单项是否只显示文字（不显示图标）
3. 测试选项对话框是否正确显示
4. 测试选择"使用源码"选项后，生成的项目是否包含正确的 CMakeLists.txt
5. 测试选择"使用库"选项后，生成的项目是否包含正确的 CMakeLists.txt
6. 测试生成的项目是否可以正常编译
