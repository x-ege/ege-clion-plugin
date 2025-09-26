# Xege IntelliJ Plugin

一个用于 JetBrains IntelliJ IDEA 系列 IDE 的示例插件项目，兼容 IntelliJ IDEA、CLion、PyCharm 等。

## 项目结构
- `build.gradle.kts`：Gradle 构建脚本，已集成 JetBrains 插件开发支持。
- `src/main/resources/META-INF/plugin.xml`：插件声明文件。
- `src/main/kotlin/org/xege/MyPluginAction.kt`：插件主类，实现菜单 Action。

## 开发环境
推荐在 IntelliJ IDEA 中直接开发此插件，这样可以：
- 在熟悉的 IDE 中开发调试
- 无需额外配置
- 生成的插件兼容所有 JetBrains IDE

## 构建流程
1. 安装 JDK 17（推荐使用 JetBrains Runtime）。
2. 在 IntelliJ IDEA 中打开此项目。
3. 执行以下命令生成插件包：
   ```sh
   ./gradlew buildPlugin
   ```
   生成的插件包位于 `build/distributions/` 目录下。

## 调试流程
1. 在 IntelliJ IDEA 中执行以下命令启动新的 IDEA 实例并加载插件：
   ```sh
   ./gradlew runIde
   ```
   或者在 IDEA 中运行 Gradle 任务：`Tasks > intellij > runIde`

2. 这会启动一个新的 IntelliJ IDEA 实例，其中已加载你的插件进行测试。

## 安装流程
1. 构建后，找到 `build/distributions/Xege_IntelliJ_Plugin-1.0-SNAPSHOT.zip`。
2. 打开任意 JetBrains IDE（IntelliJ IDEA、CLion、PyCharm 等），进入 `Settings > Plugins > Install Plugin from Disk`，选择上述 zip 文件安装。

## 插件功能
- 在主菜单添加 "Xege Action"，点击后弹出消息框。
- 兼容所有 JetBrains IDE 平台。

## 开发建议
- 直接在 IntelliJ IDEA 中开发，使用 `runIde` 任务进行调试
- 插件会自动兼容 CLion、PyCharm 等其他 JetBrains IDE
- 使用 IntelliJ IDEA Community 版本进行构建，确保最大兼容性

## 参考
- [JetBrains Plugin Development Documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html)
