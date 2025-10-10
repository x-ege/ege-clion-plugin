# EGE IntelliJ Plugin

一个用于 JetBrains IntelliJ 系列 IDE（特别是 CLion）的 EGE C++ 图形库项目向导插件。

## 🎯 功能特性

### ✅ 项目模板向导
- 在 IDE 的新建项目向导中添加 "EGE" 选项
- 一键创建包含 EGE 图形库的 C++ 项目
- 自动配置 CMake 构建系统
- 包含可运行的示例代码

### ✅ 完整的 EGE 库支持
- 包含 EGE 头文件和静态库
- 支持多种编译器（MinGW、MSVC）
- 支持多个 Visual Studio 版本

### ✅ 开箱即用
- 自动生成项目结构
- 预配置的 CMakeLists.txt
- 示例程序（绘制圆形）

## 📦 项目结构

```
xege-intellij-plugin/
├── build.gradle.kts                # Gradle 构建配置
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── org/xege/
│       │       ├── MyPluginAction.kt              # 示例 Action
│       │       └── project/
│       │           └── EgeProjectGenerator.kt     # 项目生成器
│       └── resources/
│           └── META-INF/
│               └── plugin.xml                     # 插件配置
└── assets/                         # 插件资源（会被打包）
    ├── logo.png                    # 插件图标
    ├── cmake_template/             # CMake 项目模板
    │   ├── CMakeLists_src.txt
    │   ├── CMakeLists_lib.txt
    │   └── main.cpp
    └── ege_bundle/                 # EGE 库文件
        ├── include/                # 头文件
        └── lib/                    # 静态库
```

## 🚀 快速开始

### 构建插件

```bash
./gradlew clean build
```

### 开发调试

启动带有插件的测试 IDE：

```bash
./gradlew runIde
```

**重要提示**：如果遇到 "Unknown element: icon" 错误，请查看 [错误修复文档](docs/ICON_ERROR_FIX.md)。

### 打包插件

生成可安装的插件 ZIP 文件：

```bash
./gradlew buildPlugin
```

生成的插件位于 `build/distributions/` 目录。

## 📖 使用方法

### 安装插件

1. 打包插件（见上方）
2. 打开 CLion 或其他 JetBrains IDE
3. 进入 `Settings/Preferences → Plugins`
4. 点击齿轮图标 → `Install Plugin from Disk...`
5. 选择生成的 ZIP 文件

### 创建 EGE 项目

1. 打开 IDE，点击 `File → New → Project...`
2. 在左侧项目类型列表中选择 **EGE**
3. 输入项目名称和位置
4. 点击 `Create` 创建项目

### 生成的项目结构

```
my-ege-project/
├── CMakeLists.txt          # 主 CMake 配置
├── main.cpp                # 示例程序
└── ege/                    # EGE 库
    ├── CMakeLists.txt      # EGE 库配置
    ├── include/            # 头文件
    └── lib/                # 静态库
```

### 编译运行

在 CLion 中：
1. 打开项目后，CLion 会自动加载 CMake 配置
2. 选择目标 `ege-demo`
3. 点击运行按钮（▶️）即可编译并运行

## 🛠️ 开发环境

- **JDK**: 17+
- **Gradle**: 8.13+
- **目标 IDE**: CLion 2023.3+
- **开发 IDE**: IntelliJ IDEA

## 📚 文档

- [项目向导使用指南](docs/PROJECT_WIZARD_GUIDE.md)
- [实现总结](docs/IMPLEMENTATION_SUMMARY.md)

## 🔧 开发建议

- 直接在 IntelliJ IDEA 中开发，使用 `runIde` 任务进行调试
- 插件会自动兼容 CLion、PyCharm 等其他 JetBrains IDE
- 使用 IntelliJ IDEA Community 版本进行构建，确保最大兼容性

## 📝 参考资料

- [JetBrains Plugin Development Documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [EGE 官方网站](https://xege.org/)
- [EGE GitHub](https://github.com/wysaid/xege)
