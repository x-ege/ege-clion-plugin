# EGE IntelliJ 插件开发总结

## 已完成的任务

### ✅ 任务 1: Assets 资源打包

**实现位置**: `build.gradle.kts`

```kotlin
tasks {
    processResources {
        from("assets") {
            into("assets")
        }
    }
}
```

**效果**:
- `assets` 目录下的所有文件会被打包到插件 JAR 的 `assets` 目录中
- 可以通过 `javaClass.getResource("/assets/...")` 访问资源
- 包含 `cmake_template`、`ege_bundle`、`logo.png` 等所有资源

---

### ✅ 任务 2: 插件图标设置

**实现位置**: `src/main/resources/META-INF/plugin.xml`

```xml
<!-- 插件图标 -->
<icon src="/assets/logo.png"/>
```

**效果**:
- 图标会显示在 IDE 的插件管理页面
- 图标会显示在新建项目向导的 EGE 选项旁边
- 增强插件的视觉识别度

---

### ✅ 任务 3: 创建项目向导

**实现文件**: `src/main/kotlin/org/xege/project/EgeProjectGenerator.kt`

**注册扩展点**: `src/main/resources/META-INF/plugin.xml`

```xml
<extensions defaultExtensionNs="com.intellij">
    <directoryProjectGenerator implementation="org.xege.project.EgeProjectGenerator"/>
</extensions>
```

**核心功能**:

1. **项目生成器类** (`EgeProjectGenerator`)
   - 实现 `DirectoryProjectGenerator<Any>` 接口
   - 提供项目名称 "EGE"
   - 加载并显示 logo 图标
   - 创建简单的 UI 界面，显示项目说明

2. **UI 界面**
   - 显示项目描述信息
   - 列出将要创建的文件内容
   - 只需要用户选择项目位置（Location）

3. **文件复制逻辑**
   - 复制 `cmake_template` 中的模板文件：
     - `CMakeLists_src.txt` → `CMakeLists.txt`
     - `CMakeLists_lib.txt` → `ege/CMakeLists.txt`
     - `main.cpp` → `main.cpp`
   
   - 递归复制 `ege_bundle` 目录：
     - `include/` 目录（包含所有头文件）
     - `lib/` 目录（包含所有平台的库文件）

4. **支持多种资源加载方式**
   - 开发模式：从文件系统直接复制
   - 发布模式：从 JAR 文件中提取资源
   - Fallback 机制：确保在各种环境下都能正常工作

5. **用户体验优化**
   - 进度条显示复制进度
   - 后台任务避免阻塞 UI
   - 详细的日志记录便于调试
   - 目录验证（确保目标目录为空）

---

## 创建的项目结构

使用 EGE 向导创建的项目包含：

```
my-ege-project/
├── CMakeLists.txt              # 主 CMake 配置（构建可执行文件）
├── main.cpp                    # 示例程序（绘制圆形）
└── ege/
    ├── CMakeLists.txt          # EGE 库的 CMake 配置
    ├── include/                # EGE 头文件
    │   ├── ege.h
    │   ├── ege.zh_CN.h
    │   ├── graphics.h
    │   └── ege/                # 更多头文件
    └── lib/                    # 静态库文件
        ├── mingw64/            # MinGW64 库
        ├── vs2022/             # Visual Studio 2022 库
        └── ...                 # 其他平台的库
```

---

## 测试方法

### 1. 开发环境测试

```bash
# 构建插件
./gradlew build

# 运行插件（会启动一个测试 IDE）
./gradlew runIde
```

在测试 IDE 中：
1. 点击 `File → New → Project...`
2. 在左侧列表选择 "EGE"
3. 输入项目名称和位置
4. 点击 "Create"

### 2. 打包安装测试

```bash
# 生成插件 ZIP 文件
./gradlew buildPlugin
```

生成的插件位于: `build/distributions/xege-intellij-plugin-1.0-SNAPSHOT.zip`

安装方法：
1. 打开 CLion/IDEA
2. `Settings → Plugins`
3. 齿轮图标 → `Install Plugin from Disk...`
4. 选择生成的 ZIP 文件

---

## 技术细节

### 资源访问方式

在插件代码中访问资源：

```kotlin
// 加载图片
val imageUrl = javaClass.getResource("/assets/logo.png")
val icon = ImageIcon(imageUrl)

// 读取文本文件
val stream = javaClass.getResourceAsStream("/assets/cmake_template/main.cpp")
val content = stream.bufferedReader().use { it.readText() }
```

### 从 JAR 提取文件

为了支持从打包的 JAR 中提取文件，实现了三种方式：

1. **从 JAR 文件遍历**：遍历 JAR 中的条目并提取
2. **从类加载器**：直接通过类加载器读取已知文件
3. **从文件系统**：开发模式下直接复制文件

### 目录验证

创建项目前会检查：
- 目录是否存在
- 如果存在，是否为空
- 如果不为空，显示错误提示

### 进度指示

使用 `ProgressIndicator` 显示复制进度：
- 30% - 复制 CMake 模板文件
- 70% - 复制 EGE 库文件
- 100% - 完成

---

## 已知问题与改进建议

### 当前限制

1. **平台限制**: 主要针对 Windows 平台（MinGW/MSVC）
2. **模板单一**: 只有一个基础的绘图示例
3. **配置固定**: 没有可选的配置项（如选择编译器、库版本等）

### 建议改进

1. **多模板支持**
   - 添加更多示例模板（游戏、动画、图表等）
   - 允许用户选择模板类型

2. **配置选项**
   - 选择目标编译器（GCC、MSVC、Clang）
   - 选择 EGE 版本
   - 是否包含示例代码

3. **跨平台支持**
   - 添加 macOS 和 Linux 支持
   - 自动检测系统并选择合适的库

4. **IDE 集成**
   - 添加 EGE API 代码补全
   - 集成 API 文档
   - 添加代码模板（Live Templates）

5. **项目管理**
   - 支持向现有项目添加 EGE 库
   - 提供库更新功能

---

## 项目文件说明

### 核心文件

| 文件路径 | 作用 |
|---------|------|
| `build.gradle.kts` | Gradle 构建配置，包含资源打包配置 |
| `src/main/resources/META-INF/plugin.xml` | 插件元数据和扩展点注册 |
| `src/main/kotlin/org/xege/project/EgeProjectGenerator.kt` | 项目生成器实现 |

### 资源文件

| 文件路径 | 作用 |
|---------|------|
| `assets/logo.png` | 插件图标 |
| `assets/cmake_template/` | CMake 项目模板 |
| `assets/ege_bundle/` | EGE 库文件（头文件和静态库） |

---

## 开发者说明

### 依赖项

- Kotlin 2.1.21
- IntelliJ Platform Plugin SDK 1.17.4
- 目标 IDE: CLion 2023.3+

### 开发环境

- JDK 17
- Gradle 8.13
- IntelliJ IDEA / CLion

### 日志调试

查看日志：
- 开发模式：IDE Console 输出
- 生产环境：`Help → Show Log in Finder/Explorer`

日志级别：
```kotlin
private val logger = Logger.getInstance(EgeProjectGenerator::class.java)
logger.info("...")   // 信息
logger.warn("...")   // 警告
logger.error("...", exception)  // 错误
```

---

## 总结

✅ 所有三个任务都已完成：

1. ✅ Assets 资源已正确打包到插件中
2. ✅ Logo 图标已设置并显示
3. ✅ 项目向导已实现，可以创建 EGE C++ 项目

插件现在可以：
- 在新建项目向导中显示 "EGE" 选项
- 显示自定义图标和说明
- 一键创建包含 EGE 库的 C++ 项目
- 自动配置 CMake 构建系统
- 提供可运行的示例代码

**构建状态**: ✅ 编译通过  
**测试状态**: 待测试（需要运行 `./gradlew runIde` 进行实际测试）
