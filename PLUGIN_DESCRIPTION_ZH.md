# 📦 XEGE Creator - EGE 图形库项目创建向导

一个专为 **JetBrains CLion** 设计的 EGE (Easy Graphics Engine) C++ 图形库项目向导插件，帮助你快速创建和配置 EGE 图形程序项目。

🔗 **[JetBrains 插件商店](https://plugins.jetbrains.com/plugin/28785-xege-creator)**

---

## ✨ 主要功能

- **一键创建 EGE 项目**：在 CLion 新建项目向导中提供 "EGE" 项目类型
- **自动配置 CMake 构建系统**：无需手动编写构建脚本，开箱即用
- **示例代码**：包含可运行的彩色圆形动画示例
- **灵活的库使用方式**：
  - **预编译库模式**（默认）：使用预编译的静态库，编译速度快
  - **源码模式**：直接使用 EGE 源码，可查看和修改内部实现
- **完整的多平台支持**：Windows (MinGW/MSVC)、macOS、Linux
- **国际化界面**：支持中文和英文，根据系统语言自动切换

---

## 📥 如何安装

### 方式一：从 JetBrains 插件商店安装（推荐）

1. 打开 CLion
2. 进入 `Settings/Preferences → Plugins`
3. 在 `Marketplace` 标签页搜索 "**XEGE Creator**"
4. 点击 `Install` 安装
5. 重启 CLion

### 方式二：手动安装

1. 从 [GitHub Releases](https://github.com/x-ege/ege-clion-plugin/releases) 页面下载最新的插件 ZIP 文件
2. 打开 CLion，进入 `Settings/Preferences → Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选择下载的 ZIP 文件
5. 重启 CLion

---

## 🚀 如何使用

### 创建 EGE 项目

1. 打开 CLion，点击 `File → New → Project...`
2. 在左侧项目类型列表中选择 **EGE**
3. 配置项目选项：
   - **项目名称和位置**：输入你的项目名称和保存路径
   - **使用 EGE 源码**：勾选复选框 "直接使用 EGE 源码作为项目依赖"
     - ✅ 不勾选（默认）：使用预编译的静态库（推荐，编译更快）
     - ☑️ 勾选：使用 EGE 源码（可查看和修改源码）
4. 点击 `Create` 创建项目

### 编译和运行

1. CLion 会自动加载 CMake 配置
2. 在工具栏选择 `ege-demo` 目标
3. 点击运行按钮 ▶️ 编译并运行
4. 程序会打开一个图形窗口，显示彩色圆形动画

### 在现有项目中使用

如果你已经有一个项目，想要添加 EGE 支持：

1. 打开你的项目
2. 点击 `Tools → EGE 选项 → 初始化为 EGE 项目`
3. 选择你需要的配置选项
4. 插件会自动创建必要的文件和配置

---

## 🛠️ 兼容性

- **CLion 版本**：2023.3 或更高版本（支持到 2025.2+）
- **操作系统**：Windows、macOS、Linux
- **编译器**：
  - Windows: MinGW-w64 或 MSVC (2010-2022)
  - macOS: Clang
  - Linux: GCC

---

## 📁 生成的项目结构

### 使用预编译库（默认）

```
my-ege-project/
├── CMakeLists.txt          # CMake 配置（链接静态库）
├── main.cpp                # 示例程序
└── ege/                    # EGE 库
    ├── include/            # 头文件
    └── lib/                # 预编译的静态库（支持多平台/编译器）
```

### 使用源码

```
my-ege-project/
├── CMakeLists.txt          # CMake 配置（编译源码）
├── main.cpp                # 示例程序
└── ege/                    # EGE 源码
    ├── CMakeLists.txt      # EGE 构建配置
    ├── include/            # 头文件
    ├── src/                # EGE 源代码
    └── 3rdparty/           # 第三方依赖（zlib, libpng）
```

---

## 🎯 包含的功能

- **项目向导集成**：在 CLion 新建项目向导中无缝集成
- **CMake 模板**：自动生成适配的 CMakeLists.txt
- **示例代码**：包含 Hello World 级别的图形动画示例
- **多编译器支持**：支持主流 C++ 编译器
- **库文件管理**：自动复制和配置 EGE 库文件
- **项目初始化**：可将现有项目初始化为 EGE 项目
- **工具菜单**：提供便捷的 EGE 相关操作入口

---

## 📚 相关链接

- 🌐 [EGE 官方网站](https://xege.org/)
- 💻 [EGE 源码仓库](https://github.com/wysaid/xege)
- 🔧 [插件源码仓库](https://github.com/x-ege/ege-clion-plugin)
- 📦 [GitHub Releases](https://github.com/x-ege/ege-clion-plugin/releases)
- 📖 [EGE 文档](https://xege.org/docs/)

---

## ❓ 常见问题

### Q: EGE 是什么？

A: EGE (Easy Graphics Engine) 是一个简单易用的 C++ 图形库，提供类似 Turbo C 的图形 API，适合初学者学习图形编程。

### Q: 我应该选择预编译库还是源码模式？

A: 对于大多数用户，推荐使用预编译库模式（默认），编译速度更快。如果你需要查看或修改 EGE 内部实现，可以选择源码模式。

### Q: 支持哪些平台？

A: 插件支持 Windows、macOS 和 Linux。Windows 下支持 MinGW-w64 和 MSVC 编译器。

### Q: 遇到问题怎么办？

A: 你可以在 [GitHub Issues](https://github.com/x-ege/ege-clion-plugin/issues) 提交问题，或访问 [EGE 官方网站](https://xege.org/) 获取帮助。

### Q: 如何更新插件？

A: 在 CLion 的 `Settings/Preferences → Plugins` 中，如果有新版本，会显示更新按钮。点击更新并重启 CLion 即可。

### Q: 可以在其他 JetBrains IDE 中使用吗？

A: 本插件专为 CLion 设计，因为它依赖 CLion 的 C/C++ 项目支持。其他 IDE（如 IntelliJ IDEA）不支持。

---

## 🎬 使用演示

### 创建项目演示

1. **打开新建项目向导**

   ![新建项目](https://via.placeholder.com/800x500?text=File+→+New+→+Project)

2. **选择 EGE 项目类型**

   ![选择 EGE](https://via.placeholder.com/800x500?text=选择+EGE+项目类型)

3. **配置项目选项**

   ![配置选项](https://via.placeholder.com/800x500?text=配置项目名称和选项)

4. **运行示例程序**

   ![运行程序](https://via.placeholder.com/800x500?text=彩色圆形动画)

---

## 🛠️ 开发者信息

### 项目构建

如果你想参与插件开发或从源码构建：

```bash
# 克隆项目
git clone https://github.com/x-ege/ege-clion-plugin.git
cd ege-jetbrains-plugin

# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew test

# 启动调试 IDE（带插件）
./gradlew runIde
```

生成的插件位于 `build/distributions/` 目录。

### 环境要求

- **JDK**: 17 或更高版本
- **Gradle**: 8.x+
- **开发 IDE**: IntelliJ IDEA

### CI/CD

本项目使用 GitHub Actions 进行持续集成和自动发布：

- **CI Workflow**: 每次推送到 `master` 分支或创建 Pull Request 时，自动运行测试和构建
- **Release Workflow**: 推送版本 tag（如 `1.0.1`）时，自动构建并发布到 GitHub Releases
- **Version Check Workflow**: 每天自动检查 CLion 新版本，确保插件兼容性

---

## 👤 关于

- **作者**: wysaid
- **邮箱**: this@xege.org
- **GitHub**: [@x-ege](https://github.com/x-ege)
- **许可证**: MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

如果你有任何建议或发现了 bug，请：

1. 在 [GitHub Issues](https://github.com/x-ege/ege-clion-plugin/issues) 创建 issue
2. Fork 项目并创建你的分支
3. 提交你的修改
4. 创建 Pull Request

---

## 📝 更新日志

### Version 1.0.2

- 支持 EGE 图形库项目向导
- CLion 专属集成
- 跨平台支持（Windows、macOS、Linux）
- 支持 CLion 2023.3 及以上版本
- 更新已弃用的 API

### Version 1.0.1

- 初始版本发布
- 基础项目创建功能
- 预编译库和源码两种模式

---

## ⭐ 支持项目

如果这个插件对你有帮助，请考虑：

- 在 [GitHub](https://github.com/x-ege/ege-clion-plugin) 上给项目加星 ⭐
- 在 [JetBrains 插件商店](https://plugins.jetbrains.com/plugin/28785-xege-creator) 上评分和评论
- 分享给其他需要的开发者
- 为项目做出贡献

---

**感谢使用 XEGE Creator！祝你编程愉快！** 🎉
