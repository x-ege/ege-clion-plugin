# IntelliJ 平台线程问题修复

## 问题描述

在后台任务中显示对话框时遇到错误：

```
com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments: 
Access is allowed from Event Dispatch Thread (EDT) only
```

错误发生在：
```kotlin
// ❌ 在后台线程中调用 UI 操作
Messages.showInfoMessage(...)
Messages.showErrorDialog(...)
```

## 根本原因

IntelliJ Platform 有严格的线程模型：

1. **EDT (Event Dispatch Thread)**：所有 UI 操作必须在这个线程上执行
2. **后台线程**：用于执行耗时操作，不能直接操作 UI

我们在 `Task.Backgroundable` 中直接调用了 `Messages.showInfoMessage()` 和 `Messages.showErrorDialog()`，这违反了线程模型。

## IntelliJ 平台线程模型

### EDT (Event Dispatch Thread)
- **用途**：所有 UI 操作、对话框显示、组件更新
- **特点**：不能执行耗时操作，否则会冻结 UI
- **访问方式**：使用 `ApplicationManager.getApplication().invokeLater {}`

### 后台线程
- **用途**：文件 I/O、网络请求、计算密集型任务
- **特点**：不能直接操作 UI
- **创建方式**：`Task.Backgroundable`、`ProgressManager`

### 读取线程 (Read Action)
- **用途**：读取项目结构、PSI 树等
- **访问方式**：`ReadAction.run()`

### 写入线程 (Write Action)
- **用途**：修改项目结构、写入文件等
- **访问方式**：`WriteAction.run()`

## 解决方案

使用 `ApplicationManager.getApplication().invokeLater {}` 将 UI 操作调度到 EDT：

### 修复前 ❌

```kotlin
ProgressManager.getInstance().run(object : Task.Backgroundable(...) {
    override fun run(indicator: ProgressIndicator) {
        try {
            // 执行后台任务...
            
            // ❌ 错误：在后台线程中显示对话框
            Messages.showInfoMessage("Success!", "Title")
            
        } catch (e: Exception) {
            // ❌ 错误：在后台线程中显示错误对话框
            Messages.showErrorDialog("Error: ${e.message}", "Error")
        }
    }
})
```

### 修复后 ✅

```kotlin
ProgressManager.getInstance().run(object : Task.Backgroundable(...) {
    override fun run(indicator: ProgressIndicator) {
        try {
            // 执行后台任务...
            
            // ✅ 正确：在 EDT 上显示对话框
            ApplicationManager.getApplication().invokeLater {
                Messages.showInfoMessage("Success!", "Title")
            }
            
        } catch (e: Exception) {
            // ✅ 正确：在 EDT 上显示错误对话框
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog("Error: ${e.message}", "Error")
            }
        }
    }
})
```

## 应用到本项目

在 `CreateEgeProjectAction.kt` 中：

```kotlin
private fun createEgeProject(projectPath: String, useSourceCode: Boolean) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Creating EGE Project...", false) {
        override fun run(indicator: ProgressIndicator) {
            try {
                // 在后台线程执行文件复制等耗时操作
                copyCMakeTemplateFiles(targetDir, useSourceCode)
                copyEgeBundle(targetDir, indicator, useSourceCode)
                
                // ✅ 在 EDT 上显示成功消息
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        "EGE project created successfully at:\n$projectPath",
                        "Project Created"
                    )
                }
                
            } catch (e: Exception) {
                // ✅ 在 EDT 上显示错误消息
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        "Failed to create EGE project: ${e.message}",
                        "Error"
                    )
                }
            }
        }
    })
}
```

## 其他常见的 EDT 违规

### 1. 更新 UI 组件
```kotlin
// ❌ 错误
myLabel.text = "New Text"

// ✅ 正确
ApplicationManager.getApplication().invokeLater {
    myLabel.text = "New Text"
}
```

### 2. 创建对话框
```kotlin
// ❌ 错误
val dialog = MyDialog(project)
dialog.show()

// ✅ 正确
ApplicationManager.getApplication().invokeLater {
    val dialog = MyDialog(project)
    dialog.show()
}
```

### 3. 打开文件
```kotlin
// ❌ 错误
FileEditorManager.getInstance(project).openFile(file, true)

// ✅ 正确
ApplicationManager.getApplication().invokeLater {
    FileEditorManager.getInstance(project).openFile(file, true)
}
```

## 调试技巧

### 1. 检查当前线程
```kotlin
if (ApplicationManager.getApplication().isDispatchThread) {
    // 当前在 EDT
} else {
    // 当前在后台线程
}
```

### 2. 断言 EDT
```kotlin
ApplicationManager.getApplication().assertIsDispatchThread()
```

### 3. 使用 invokeLater vs invokeAndWait

- `invokeLater`：异步，不等待 UI 操作完成
- `invokeAndWait`：同步，等待 UI 操作完成（可能导致死锁，谨慎使用）

```kotlin
// 异步（推荐）
ApplicationManager.getApplication().invokeLater {
    // UI 操作
}

// 同步（谨慎使用）
ApplicationManager.getApplication().invokeAndWait {
    // UI 操作
}
```

## 参考资料

- [IntelliJ Platform Threading Guidelines](https://jb.gg/ij-platform-threading)
- [Background Tasks](https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html)
- [UI Threading Best Practices](https://plugins.jetbrains.com/docs/intellij/threading-model.html)

## 总结

**核心规则**：
1. 所有 UI 操作必须在 EDT 上执行
2. 耗时操作必须在后台线程执行
3. 使用 `invokeLater` 从后台线程切换到 EDT
4. 遵循 IntelliJ Platform 的线程模型，避免死锁和 UI 冻结
