# UI 选项显示问题修复验证指南

## 问题描述

在 CLion 的新建项目向导中，选择 EGE 项目模板后，无法看到自定义选项界面（包括说明标签和复选框）。

## 问题根源

`EgeProjectGeneratorPeer.buildUI()` 方法的实现不正确：

**之前的错误实现**:
```kotlin
override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {
    settingsStep.addSettingsComponent(panel)  // ❌ 错误：重复添加组件
}
```

**问题分析**:
- `getComponent()` 方法已经返回了 `panel`，框架会自动显示它
- `buildUI()` 中再次调用 `addSettingsComponent(panel)` 造成冲突
- 导致界面无法正确显示

## 修复方案

**修复后的实现**:
```kotlin
override fun buildUI(settingsStep: com.intellij.ide.util.projectWizard.SettingsStep) {
    // 不需要在这里添加组件，getComponent() 返回的组件会自动显示
    // 这个方法可以用于添加额外的设置字段到 settingsStep，但我们不需要
}
```

## 正确的实现方式

对于 `ProjectGeneratorPeer<T>` 接口：

1. **`getComponent()`**: 返回你的 UI 组件（panel），这个组件会自动显示在向导中
2. **`buildUI(settingsStep)`**: 
   - 用于向 `SettingsStep` 添加**额外的**标准设置字段（如文本框、组合框等）
   - 不应该添加 `getComponent()` 返回的组件
   - 如果不需要额外字段，可以留空
3. **`getSettings()`**: 从 UI 中读取用户设置并返回设置对象
4. **`validate()`**: 验证用户输入，返回验证错误（如果有）

## 测试步骤

### 1. 重新构建插件

```bash
cd /Volumes/HikData/work/git/xege-intellij-plugin
./gradlew clean build
```

### 2. 运行测试 IDE

```bash
./gradlew runIde
```

### 3. 创建新项目

在启动的 CLion 中：

1. 点击 **`File`** → **`New`** → **`Project...`**
2. 在左侧列表中找到并选择 **`Easy Graphics Engine`**
3. **验证以下内容是否显示**：
   
   ✅ 应该看到说明文字：
   ```
   选择项目依赖方式：
   • 不勾选：使用预编译的 EGE 静态库（推荐）
   • 勾选：直接使用 EGE 源代码（可以查看和修改 EGE 内部实现）
   ```
   
   ✅ 应该看到复选框：
   ```
   ☐ 直接使用 EGE 源码作为项目依赖
   ```

4. 输入项目名称和位置
5. **测试不勾选的情况**：
   - 不勾选复选框
   - 点击 **Create**
   - 验证创建的项目包含：
     - `CMakeLists.txt`（使用库的版本）
     - `ege/include/` 头文件目录
     - `ege/lib/` 库文件目录
     - `main.cpp`

6. **测试勾选的情况**：
   - 创建新项目，这次勾选复选框
   - 点击 **Create**
   - 验证创建的项目包含：
     - `CMakeLists.txt`（使用源码的版本）
     - `ege/include/` 头文件目录
     - `ege/src/` 源码目录
     - `main.cpp`

### 4. 查看日志（如果仍有问题）

如果修复后仍然看不到选项界面：

1. 在测试 IDE 中，点击 **`Help`** → **`Show Log in Finder`**
2. 打开日志文件，搜索 "EgeProjectGenerator"
3. 查看是否有错误或警告信息

## 预期结果

✅ **修复后，应该能够在新建项目向导中看到**：
- 说明标签（描述两种依赖方式的区别）
- 复选框（允许用户选择使用源码还是库）
- 选项界面应该显示在项目名称和位置输入框的下方

## 技术说明

### DirectoryProjectGenerator 的 UI 显示流程

```
1. 用户选择项目模板（EGE）
   ↓
2. 框架调用 createPeer() 创建 ProjectGeneratorPeer 实例
   ↓
3. 框架调用 peer.getComponent() 获取 UI 组件
   ↓
4. 框架自动将该组件显示在向导界面中
   ↓
5. 用户填写设置并点击 Create
   ↓
6. 框架调用 peer.getSettings() 获取用户设置
   ↓
7. 框架调用 generator.generateProject(settings) 创建项目
```

### buildUI() 方法的用途

`buildUI(settingsStep)` 方法主要用于：
- 向 `SettingsStep` 添加**标准化的**设置字段
- 这些字段会和其他标准字段（如 SDK 选择器）一起显示
- 示例：
  ```kotlin
  override fun buildUI(settingsStep: SettingsStep) {
      // 添加一个文本字段
      settingsStep.addSettingsField("Framework version:", versionComboBox)
  }
  ```

在我们的场景中，由于我们需要显示自定义的说明文字和布局，使用 `getComponent()` 返回完整的 panel 更合适。

## 常见问题

### Q1: 为什么之前的实现会导致 UI 不显示？

A: 因为在 `buildUI()` 中调用 `addSettingsComponent(panel)` 与 `getComponent()` 返回的 panel 产生了冲突。框架可能无法正确处理同一个组件被添加两次的情况。

### Q2: 如果还是看不到选项怎么办？

A: 可能的原因：
1. 插件没有正确重新加载：尝试重启测试 IDE
2. 缓存问题：运行 `./gradlew clean` 再重新构建
3. CLion 版本兼容性：确认使用的是 2023.3 或更新版本

### Q3: 能否在 buildUI() 中添加更多设置？

A: 可以，但不要添加 `getComponent()` 返回的组件。例如：
```kotlin
override fun buildUI(settingsStep: SettingsStep) {
    // 添加额外的标准设置字段（如果需要）
    settingsStep.addSettingsField("编译器:", compilerComboBox)
}
```

## 总结

修复方法很简单：**移除 `buildUI()` 中的 `addSettingsComponent(panel)` 调用**。框架会自动使用 `getComponent()` 返回的组件，无需在 `buildUI()` 中重复添加。

这是一个常见的 IntelliJ Platform 插件开发错误，正确理解 `ProjectGeneratorPeer` 接口各个方法的用途很重要。
