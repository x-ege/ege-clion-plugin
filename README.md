# XEGE Creator

[![CI](https://github.com/x-ege/ege-jetbrains-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/x-ege/ege-jetbrains-plugin/actions/workflows/ci.yml)
[![Release](https://github.com/x-ege/ege-jetbrains-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/x-ege/ege-jetbrains-plugin/actions/workflows/release.yml)
[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/28785-xege-creator.svg)](https://plugins.jetbrains.com/plugin/28785-xege-creator)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28785-xege-creator.svg)](https://plugins.jetbrains.com/plugin/28785-xege-creator)

一个用于 JetBrains CLion 的 EGE C++ 图形库项目向导插件，帮助你快速创建和配置 EGE 图形程序项目。

🔗 **[JetBrains 插件商店](https://plugins.jetbrains.com/plugin/28785-xege-creator)**

---

## 📥 安装

### 方式一：从 JetBrains 插件商店安装（推荐）

1. 打开 CLion
2. 进入 `Settings/Preferences → Plugins`
3. 在 `Marketplace` 标签页搜索 "**XEGE Creator**"
4. 点击 `Install` 安装
5. 重启 CLion

### 方式二：手动安装

1. 从 [Releases](https://github.com/x-ege/ege-jetbrains-plugin/releases) 页面下载最新的插件 ZIP 文件
2. 打开 CLion，进入 `Settings/Preferences → Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选择下载的 ZIP 文件
5. 重启 CLion

---

## 🎯 功能特性

### ✨ 一键创建 EGE 项目

- 在 CLion 新建项目向导中添加 "**EGE**" 项目类型
- 自动配置 CMake 构建系统
- 包含可运行的示例代码（绘制彩色圆形）

### 🔧 灵活的库使用方式

- **预编译库模式**（默认）：使用预编译的静态库，编译速度快
- **源码模式**：直接使用 EGE 源码，可查看和修改 EGE 内部实现

### 🌍 完整的多平台支持

- 包含 EGE 头文件和静态库
- 支持 Windows（MinGW、MSVC）
- 支持 macOS 和 Linux

### 🌐 国际化支持

- 支持中文和英文界面
- 根据系统语言自动切换

---

## 🚀 使用方法

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

### 生成的项目结构

#### 使用预编译库（默认）

```
my-ege-project/
├── CMakeLists.txt          # CMake 配置（链接静态库）
├── main.cpp                # 示例程序
└── ege/                    # EGE 库
    ├── include/            # 头文件
    └── lib/                # 预编译的静态库（支持多平台/编译器）
```

#### 使用源码

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

## 🛠️ 开发构建

如果你想参与插件开发或从源码构建：

### 环境要求

- **JDK**: 17 或更高版本
- **Gradle**: 8.x+
- **开发 IDE**: IntelliJ IDEA

### 构建命令

```bash
# 克隆项目
git clone https://github.com/x-ege/ege-jetbrains-plugin.git
cd ege-jetbrains-plugin

# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew test

# 启动调试 IDE（带插件）
./gradlew runIde
```

生成的插件位于 `build/distributions/` 目录。

---

## 🧪 CI/CD

本项目使用 GitHub Actions 进行持续集成和自动发布：

- **CI Workflow**: 每次推送到 `master` 分支或创建 Pull Request 时，自动运行测试和构建
- **Release Workflow**: 推送版本 tag（如 `1.0.1`）时，自动构建并发布到 GitHub Releases

---

## 📋 系统要求

- **CLion**: 2023.3 或更高版本（支持到 2025.1+）
- **操作系统**: Windows, macOS, Linux
- **编译器**:
  - Windows: MinGW-w64 或 MSVC (2010-2022)
  - macOS: Clang
  - Linux: GCC

---

## 📚 相关链接

- 🏪 **[JetBrains 插件商店](https://plugins.jetbrains.com/plugin/28785-xege-creator)**
- 📦 **[GitHub Releases](https://github.com/x-ege/ege-jetbrains-plugin/releases)**
- 🌐 **[EGE 官方网站](https://xege.org/)**
- 💻 **[EGE 源码仓库](https://github.com/wysaid/xege)**

---

## 📝 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 👤 作者

- **Author**: wysaid
- **Email**: <this@xege.org>
- **GitHub**: [@x-ege](https://github.com/x-ege)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

如果这个插件对你有帮助，请在 [GitHub](https://github.com/x-ege/ege-jetbrains-plugin) 给个 ⭐️ Star！
