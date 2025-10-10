# 插件激活验证和使用指南

## 问题分析

通过日志和文件检查，发现：
1. ✅ 插件文件已正确打包
2. ✅ plugin.xml 配置正确
3. ✅ 类文件存在
4. ✅ 插件被标记为必需插件：`idea.required.plugins.id=org.xege.ideaplugin`
5. ❌ 但是在 CLion 新建项目向导中看不到

## 根本原因

**`DirectoryProjectGenerator` 在 CLion 中不会显示在新建项目向导中**。

这个扩展点主要用于：
- PyCharm (Python 项目)
- WebStorm (Node.js 项目)
- IntelliJ IDEA (Java/Kotlin 项目)

但 **CLion 使用不同的机制来管理 C/C++ 项目模板**。

## ✅ 解决方案：使用 Action 创建项目

我已经实现了一个新的解决方案：添加了 `CreateEgeProjectAction`，它可以通过菜单直接创建 EGE 项目。

### 如何使用

#### 方法 1: 通过欢迎屏幕（推荐）

1. 运行 `./gradlew runIde`
2. 在欢迎屏幕上，你会看到 **"New EGE Project..."** 按钮（在 "New Project" 下方）
3. 点击它，选择项目位置，即可创建项目

#### 方法 2: 通过文件菜单

1. 在 CLion 中，点击 `File` 菜单
2. 找到 **"New EGE Project..."**（在 "New Project" 下方）
3. 选择项目位置，创建项目

#### 方法 3: 验证插件已加载（测试用）

1. 查看主菜单最后一项，应该能看到 **"🚀 Xege Action"**
2. 或者在 `Tools` 菜单第一项看到它
3. 点击这个 Action，会弹出对话框，说明插件已正确加载

## 创建的项目结构

使用任一方法创建的项目都包含：

```
my-ege-project/
├── CMakeLists.txt          # 主 CMake 配置
├── main.cpp                # 示例程序（绘制圆形）
└── ege/
    ├── CMakeLists.txt      # EGE 库配置
    ├── include/            # 完整的头文件
    └── lib/                # 多平台静态库
```

## 测试步骤

1. **构建插件**
   ```bash
   ./gradlew clean build
   ```

2. **运行测试 IDE**
   ```bash
   ./gradlew runIde
   ```

3. **在欢迎屏幕或文件菜单中找到 "New EGE Project..."**

4. **选择项目位置并创建**

5. **使用 `File → Open...` 打开创建的项目**

## 技术实现

### CreateEgeProjectAction

- 继承自 `AnAction`
- 使用文件选择器让用户选择项目位置
- 在后台任务中复制模板文件和 EGE 库
- 显示进度条
- 创建完成后显示成功消息

### 菜单位置

插件在以下位置添加了创建项目的入口：

1. **欢迎屏幕**: `WelcomeScreen.QuickStart` 组，位于 "New Project" 之后
2. **文件菜单**: `FileMenu` 组，位于 "New Project" 之后

## 为什么这个方案更好

1. **明确可见**: 在欢迎屏幕和文件菜单中都有明显的入口
2. **CLion 友好**: 不依赖于 `DirectoryProjectGenerator`，完全兼容 CLion
3. **简单直接**: 一键创建，无需复杂的向导步骤
4. **跨平台**: 在所有 JetBrains IDE 中都能工作

## 故障排除

### 如果看不到 "New EGE Project..." 菜单

1. **检查插件是否加载**
   - 进入 `Settings/Preferences → Plugins`
   - 搜索 "Xege"
   - 确认插件已启用

2. **查看日志**
   - `Help → Show Log in Finder/Explorer`
   - 搜索 "CreateEgeProjectAction" 或 "xege"
   - 查看是否有错误

3. **重新构建并运行**
   ```bash
   ./gradlew clean build
   ./gradlew runIde
   ```

### 如果创建项目失败

1. 检查目标目录权限
2. 查看 IDE 日志中的错误信息
3. 确保有足够的磁盘空间

## 总结

✅ **问题已解决**

- 插件已正确加载
- 通过 `CreateEgeProjectAction` 提供了创建 EGE 项目的功能
- 在欢迎屏幕和文件菜单中都有明显的入口
- 完全兼容 CLion 和其他 JetBrains IDE

**现在你可以**：
1. 运行 `./gradlew runIde`
2. 在欢迎屏幕或文件菜单中点击 "New EGE Project..."
3. 选择位置并创建项目
4. 享受 EGE 图形编程！🎨

