# XEGE Creator

[![CI](https://github.com/x-ege/ege-clion-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/x-ege/ege-clion-plugin/actions/workflows/ci.yml)
[![Auto Release](https://github.com/x-ege/ege-clion-plugin/actions/workflows/auto-release.yml/badge.svg)](https://github.com/x-ege/ege-clion-plugin/actions/workflows/auto-release.yml)
[![Release](https://github.com/x-ege/ege-clion-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/x-ege/ege-clion-plugin/actions/workflows/release.yml)
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

1. 从 [Releases](https://github.com/x-ege/ege-clion-plugin/releases) 页面下载最新的插件 ZIP 文件
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

### 更新 EGE 库和源码

项目提供了两个脚本用于更新内置的 EGE 库和源码：

```bash
# 更新预编译库（从 Jenkins 下载最新版本）
./update_ege_lib.sh

# 更新源代码（从本地 xege 仓库）
./update_ege_src.sh /path/to/xege

# 更新预编译库时跳过下载（使用已下载的文件）
./update_ege_lib.sh --skip-download

# 强制重新下载预编译库
./update_ege_lib.sh --force-download
```

---

## 🧪 CI/CD

本项目使用 GitHub Actions 进行持续集成和自动发布：

- **CI Workflow**: 每次推送到 `master` 分支或创建 Pull Request 时，自动运行测试和构建
- **Auto Release Workflow**: 当 `master` 分支更新（PR 合并或直接推送）时，自动检测 `gradle.properties` 中的 `pluginVersion` 是否改变，如果改变则自动创建对应的版本 tag
- **Release Workflow**: 推送版本 tag（如 `1.0.1`）时，自动构建并发布到 GitHub Releases
- **Version Check Workflow**: 每天自动检查 CLion 新版本，确保插件兼容性

### 📦 发布流程

插件采用自动化发布流程：

1. **更新版本号**：在 `gradle.properties` 中修改 `pluginVersion`（如从 `1.0.0` 改为 `1.1.0`）
2. **提交并推送**：提交更改并推送到 `master` 分支（或通过 PR 合并）
3. **自动创建 Tag**：Auto Release workflow 检测到版本变化后，自动创建对应的 tag
4. **自动发布**：Release workflow 被 tag 触发，自动构建并发布插件到 GitHub Releases

注意：如果目标版本的 tag 已存在，Auto Release workflow 会报错，需要选择新的版本号。

### 🔧 版本管理任务

插件提供了自动化的版本管理工具：

```bash
# 检查 untilBuild 是否匹配 CLion 最新版本
./gradlew checkClionVersion

# 自动更新 untilBuild 到最新版本
./gradlew updateUntilBuild
```

详细文档请查看 [VERSION_MANAGEMENT.md](docs/VERSION_MANAGEMENT.md)

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
- 📦 **[GitHub Releases](https://github.com/x-ege/ege-clion-plugin/releases)**
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

如果这个插件对你有帮助，请在 [GitHub](https://github.com/x-ege/ege-clion-plugin) 给个 ⭐️ Star！
