# JAR 资源访问问题修复

## 问题描述

在 IntelliJ 插件的沙盒环境中运行时，遇到 `NullPointerException`:

```
java.lang.NullPointerException: Cannot invoke "java.net.URL.toURI()" 
because the return value of "java.security.CodeSource.getLocation()" is null
```

这个错误出现在两个地方：
1. `copyOtherTemplateFilesFromJar()` - 复制 cmake_template 其他文件时
2. `copyFromJar()` - 复制 ege_bundle 目录时

## 根本原因

在某些环境下（特别是 IDE 的插件沙盒环境），`javaClass.protectionDomain.codeSource.location` 会返回 `null`。这是因为：

1. 插件在沙盒环境中运行时，安全策略可能限制了对代码源位置的访问
2. 类加载器的实现可能不提供代码源信息
3. 插件的加载方式可能与常规 JAR 文件不同

## 尝试遍历 JAR 的问题

原始代码尝试直接访问 JAR 文件并遍历其条目：

```kotlin
// ❌ 不可靠的方法
val jarFile = javaClass.protectionDomain.codeSource.location.toURI()
java.util.jar.JarFile(File(jarFile)).use { jar ->
    val entries = jar.entries()
    // 遍历所有条目...
}
```

这种方法的问题：
- 依赖于 `codeSource.location`，在某些环境下为 `null`
- 需要文件系统访问权限
- 在某些类加载器实现下不工作

## 解决方案

### 方案1: 小文件列表（cmake_template）

对于文件较少的目录，直接列出所有文件：

```kotlin
private fun copyOtherTemplateFilesFromJar(targetDir: File) {
    val knownTemplateFiles = listOf(
        "main.cpp"
        // 其他模板文件...
    )
    
    knownTemplateFiles.forEach { fileName ->
        val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$fileName")
        if (resourceStream != null) {
            // 复制文件...
        }
    }
}
```

### 方案2: 大文件列表（ege_bundle）

对于文件较多的目录，使用完整的文件列表：

```kotlin
private fun copyFromClassLoader(resourcePath: String, targetDir: File) {
    val knownFiles = when {
        resourcePath.contains("ege_bundle") -> listOf(
            // 27 个文件的完整列表
            "include/ege.h",
            "include/ege.zh_CN.h",
            // ... 所有文件
        )
        else -> emptyList()
    }
    
    knownFiles.forEach { relPath ->
        val stream = javaClass.getResourceAsStream("$resourcePath/$relPath")
        // 复制文件...
    }
}
```

### 方案3: 简化 copyFromJar

直接调用 `copyFromClassLoader`，不再尝试遍历 JAR：

```kotlin
private fun copyFromJar(resourcePath: String, targetDir: File) {
    logger.info("Copying resources from JAR: $resourcePath")
    copyFromClassLoader(resourcePath, targetDir)
}
```

## 优势

1. **兼容性**: 在所有环境下都能工作（开发、测试、生产）
2. **简单性**: 不需要处理 URI、文件系统路径等复杂问题
3. **安全性**: 不依赖安全敏感的 API
4. **明确性**: 清楚地列出需要复制的文件

## 权衡

这种方法需要明确列出所有要复制的文件，而不是动态遍历目录。但这实际上是一个优点：

- **版本控制**: 清楚地知道插件包含哪些文件
- **可维护性**: 添加新模板文件时必须显式添加到列表
- **可测试性**: 容易验证所有必需的文件都被复制了

## 应用范围

这个修复应用于两个文件：

1. `EgeProjectGenerator.kt` - DirectoryProjectGenerator 实现
2. `CreateEgeProjectAction.kt` - 菜单 Action 实现

两个文件都使用相同的可靠方法来复制模板文件。

## 测试

修复后，插件在以下环境下都能正常工作：

- ✅ 开发环境（`./gradlew runIde`）
- ✅ 测试环境
- ✅ 打包后的插件

## 最佳实践

在 IntelliJ 插件中访问资源时，应该：

1. **优先使用类加载器**: `javaClass.getResourceAsStream()`
2. **避免文件系统操作**: 不要假设资源在文件系统上
3. **明确列出资源**: 不要依赖目录遍历
4. **处理空值**: 资源可能不存在，总是检查 `null`

## 相关代码

### 修复前
```kotlin
val jarFile = javaClass.protectionDomain.codeSource.location.toURI()  // ❌ 可能为 null
java.util.jar.JarFile(File(jarFile)).use { jar ->
    // 遍历 JAR...
}
```

### 修复后
```kotlin
val knownTemplateFiles = listOf("main.cpp")
knownTemplateFiles.forEach { fileName ->
    val resourceStream = javaClass.getResourceAsStream("/assets/cmake_template/$fileName")  // ✅ 总是有效
    if (resourceStream != null) {
        // 复制文件...
    }
}
```
