# 错误修复：Unknown element: icon

## 问题描述

运行 `./gradlew runIde` 时出现错误：

```
ERROR: Unknown element: icon
java.lang.Throwable: Unknown element: icon
```

## 原因分析

在 IntelliJ Platform 2023.3 中，`<icon>` **不是** `plugin.xml` 的顶级元素。

### 错误的用法 ❌

```xml
<idea-plugin>
    <!-- 插件图标 -->
    <icon src="/assets/logo.png"/>  <!-- 这是错误的！ -->
    
    <actions>
        <action icon="/assets/logo.png"> <!-- Action 中也不推荐这样用 -->
        </action>
    </actions>
</idea-plugin>
```

### 正确的用法 ✅

#### 方法 1: 在代码中设置 Action 图标（推荐）

```kotlin
class CreateEgeProjectAction : AnAction() {
    init {
        try {
            val imageUrl = javaClass.getResource("/assets/logo.png")
            if (imageUrl != null) {
                templatePresentation.icon = ImageIcon(imageUrl)
            }
        } catch (e: Exception) {
            logger.warn("Failed to load action icon", e)
        }
    }
}
```

#### 方法 2: 使用 Icon 扩展点（如果需要全局图标）

```xml
<extensions defaultExtensionNs="com.intellij">
    <iconProvider implementation="your.icon.provider.Class"/>
</extensions>
```

## 已修复的问题

1. ✅ 删除了 plugin.xml 中的 `<icon src="/assets/logo.png"/>` 顶级元素
2. ✅ 删除了 Action 中的 `icon="/assets/logo.png"` 属性
3. ✅ 在 `CreateEgeProjectAction` 的 `init` 块中动态设置图标

## 修改的文件

1. **src/main/resources/META-INF/plugin.xml**
   - 删除了 `<icon>` 顶级元素
   - 删除了 Action 中的 `icon` 属性

2. **src/main/kotlin/org/xege/project/CreateEgeProjectAction.kt**
   - 添加了 `init` 块来动态设置图标
   - 图标会在运行时从资源加载

## 测试步骤

1. **清理并构建**
   ```bash
   ./gradlew clean build
   ```

2. **运行插件**
   ```bash
   ./gradlew runIde
   ```

3. **验证**
   - 不应该再看到 "Unknown element: icon" 错误
   - CLion 应该正常启动
   - 欢迎屏幕和文件菜单中应该能看到 "New EGE Project..." 选项

## 其他警告说明

你在日志中看到的其他 WARN 信息都是正常的：

- ✅ `The dependency on the Kotlin Standard Library` - 这只是一个信息提示，不影响功能
- ✅ `preload=true must be used only for core services` - 来自其他插件，不是我们的问题
- ✅ `No URL bundle (CFBundleURLTypes)` - CLion 的正常警告
- ✅ 其他 WARN - 都是 IDE 启动时的常规警告，不影响插件功能

## 总结

现在插件应该可以正常运行了！🎉

**下一步**：
1. 运行 `./gradlew runIde`
2. 在欢迎屏幕或 `File` 菜单中找到 "New EGE Project..."
3. 创建你的第一个 EGE 项目！
