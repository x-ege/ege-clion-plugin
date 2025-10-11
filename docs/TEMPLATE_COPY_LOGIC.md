# 模板复制逻辑说明

## 问题背景

在实现"根据选项使用不同 CMakeLists 模板"功能时，需要明确模板文件的复制逻辑。

## 正确的逻辑

### cmake_template 目录结构

```
assets/cmake_template/
├── CMakeLists_lib.txt    # 使用预编译库的 CMakeLists 模板
├── CMakeLists_src.txt    # 使用源码的 CMakeLists 模板
├── main.cpp              # 示例程序（通用）
└── ... (其他模板文件)     # 其他项目模板文件
```

### 复制规则

1. **CMakeLists.txt 的复制**：
   - 如果用户选择"使用源码"：复制 `CMakeLists_src.txt` → `CMakeLists.txt`
   - 如果用户选择"使用库"（默认）：复制 `CMakeLists_lib.txt` → `CMakeLists.txt`
   - **重要**：两个模板文件都不会保留原始文件名，只会复制选中的一个

2. **其他模板文件的复制**：
   - `cmake_template` 目录下的所有其他文件（除了 `CMakeLists_*.txt`）都应该复制到目标目录
   - 这些文件包括但不限于：
     - `main.cpp` - 示例程序
     - 其他可能的配置文件、源文件等

3. **EGE 库文件的复制**：
   - 如果用户选择"使用源码"：复制 `assets/ege_src/` → `ege/`
   - 如果用户选择"使用库"（默认）：复制 `assets/ege_bundle/` → `ege/`

## 实现细节

### CreateEgeProjectAction.kt

```kotlin
private fun copyCMakeTemplateFiles(targetDir: File, useSourceCode: Boolean) {
    // 1. 根据选项复制对应的 CMakeLists 模板
    val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
    // 复制并重命名为 CMakeLists.txt
    
    // 2. 复制 cmake_template 目录下的其他所有文件
    // 排除条件：
    // - CMakeLists_*.txt (已经在步骤1处理)
    // - 隐藏文件 (以 . 开头)
    // - 目录
}
```

### 生成的项目结构

#### 使用预编译库（默认）

```
project/
├── CMakeLists.txt          # 来自 CMakeLists_lib.txt
├── main.cpp                # 来自 cmake_template/main.cpp
├── (其他模板文件)           # 来自 cmake_template/*
└── ege/                    # 来自 ege_bundle/
    ├── include/
    └── lib/
```

#### 使用源码

```
project/
├── CMakeLists.txt          # 来自 CMakeLists_src.txt
├── main.cpp                # 来自 cmake_template/main.cpp
├── (其他模板文件)           # 来自 cmake_template/*
└── ege/                    # 来自 ege_src/
    ├── CMakeLists.txt
    ├── include/
    ├── src/
    └── 3rdparty/
```

## 关键点

1. **模板选择性**：两个 CMakeLists 模板文件只复制其中一个
2. **通用性**：其他模板文件对两种模式都是通用的，全部复制
3. **一致性**：两个生成器（EgeProjectGenerator 和 CreateEgeProjectAction）应该使用相同的逻辑

## 之前的错误

❌ **错误实现**：
```kotlin
val templateFiles = mapOf(
    "CMakeLists_src.txt" to "CMakeLists.txt",
    "CMakeLists_lib.txt" to "ege/CMakeLists.txt",  // 错误！
    "main.cpp" to "main.cpp"
)
```

这个实现的问题：
1. 同时复制了两个 CMakeLists 模板
2. 将 lib 版本放到了错误的位置（`ege/CMakeLists.txt`）
3. 硬编码了文件列表，不够灵活

✅ **正确实现**：
```kotlin
// 1. 根据条件只复制一个 CMakeLists
val cmakeTemplate = if (useSourceCode) "CMakeLists_src.txt" else "CMakeLists_lib.txt"
copyFile(cmakeTemplate, "CMakeLists.txt")

// 2. 遍历目录，复制其他所有文件
for (file in listTemplateFiles()) {
    if (!file.startsWith("CMakeLists_")) {
        copyFile(file, file)
    }
}
```

## 测试验证

### 预编译库模式
- [ ] 生成的项目中有 `CMakeLists.txt`（内容来自 `CMakeLists_lib.txt`）
- [ ] 生成的项目中有 `main.cpp`
- [ ] 生成的项目中**没有** `CMakeLists_src.txt` 或 `CMakeLists_lib.txt`
- [ ] `CMakeLists.txt` 中包含库路径选择逻辑

### 源码模式
- [ ] 生成的项目中有 `CMakeLists.txt`（内容来自 `CMakeLists_src.txt`）
- [ ] 生成的项目中有 `main.cpp`
- [ ] 生成的项目中**没有** `CMakeLists_src.txt` 或 `CMakeLists_lib.txt`
- [ ] `CMakeLists.txt` 中包含 `add_subdirectory(ege)`
