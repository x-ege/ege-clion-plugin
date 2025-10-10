# EGE 项目向导使用说明

## 功能概览

这个插件为 IntelliJ 系列 IDE（特别是 CLion）添加了 EGE C++ 图形库项目模板支持。

## 已实现的功能

### 1. ✅ Assets 资源打包
- `assets` 目录已经配置为自动打包到插件中
- 在 `build.gradle.kts` 中通过 `processResources` 任务实现
- 包含了 EGE 的头文件、库文件和项目模板

### 2. ✅ 插件图标
- 使用 `assets/logo.png` 作为插件图标
- 图标会显示在 IDE 的插件管理页面和新建项目向导中
- 在 `plugin.xml` 中通过 `<icon>` 标签配置

### 3. ✅ EGE 项目向导
- 在 IDE 的"新建项目"对话框中添加了 "EGE" 选项
- 提供简单的项目创建界面，只需选择项目位置
- 自动复制 `cmake_template` 中的模板文件到目标目录
- 自动复制 `ege_bundle` 中的 EGE 库文件

## 使用方法

### 开发环境测试

1. **构建插件**
   ```bash
   ./gradlew build
   ```

2. **运行插件（开发模式）**
   ```bash
   ./gradlew runIde
   ```
   这将启动一个带有插件的 IDE 实例

3. **在测试 IDE 中创建 EGE 项目**
   - 点击 `File → New → Project...`
   - 在左侧列表中选择 "EGE"
   - 输入项目名称和位置
   - 点击 "Create" 创建项目

### 安装到本地 IDE

1. **打包插件**
   ```bash
   ./gradlew buildPlugin
   ```
   插件 ZIP 文件将生成在 `build/distributions/` 目录

2. **安装插件**
   - 打开 CLion 或其他 JetBrains IDE
   - 进入 `Settings/Preferences → Plugins`
   - 点击齿轮图标 → `Install Plugin from Disk...`
   - 选择生成的 ZIP 文件

## 项目结构

创建的 EGE 项目包含以下文件：

```
my-ege-project/
├── CMakeLists.txt          # 主 CMake 配置文件
├── main.cpp                # 示例程序（画圆）
└── ege/                    # EGE 库目录
    ├── CMakeLists.txt      # EGE 库的 CMake 配置
    ├── include/            # EGE 头文件
    │   ├── ege.h
    │   ├── graphics.h
    │   └── ...
    └── lib/                # EGE 静态库文件
        ├── mingw64/
        ├── vs2022/
        └── ...
```

## 技术实现细节

### 1. 资源打包
在 `build.gradle.kts` 中配置：
```kotlin
tasks {
    processResources {
        from("assets") {
            into("assets")
        }
    }
}
```

### 2. 插件图标
在 `plugin.xml` 中配置：
```xml
<icon src="/assets/logo.png"/>
```

### 3. 项目生成器
- 实现了 `DirectoryProjectGenerator<Any>` 接口
- 在 `plugin.xml` 中注册扩展点：
  ```xml
  <directoryProjectGenerator implementation="org.xege.project.EgeProjectGenerator"/>
  ```
- 支持从 JAR 和文件系统两种方式复制资源文件

## 注意事项

1. **目标平台**: 目前模板主要针对 Windows + MinGW 环境
2. **CMake 版本**: 需要 CMake 3.13 或更高版本
3. **编译器**: 支持 GCC (MinGW) 和 MSVC
4. **目录验证**: 创建项目时会检查目标目录是否为空

## 后续改进建议

1. **多模板支持**: 可以添加更多项目模板（如游戏、动画等示例）
2. **配置选项**: 在创建向导中添加更多配置选项（编译器选择、库版本等）
3. **平台适配**: 添加对 macOS 和 Linux 的支持
4. **在线文档**: 集成 EGE API 文档和教程链接
5. **代码补全**: 为 EGE API 添加智能代码补全支持

## 开发者信息

- **插件 ID**: `org.xege.ideaplugin`
- **最小 IDE 版本**: 2023.3
- **主要目标 IDE**: CLion
- **开发语言**: Kotlin
