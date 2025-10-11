# 架构整合：统一项目创建入口

## 背景

在之前的实现中，插件提供了两个创建 EGE 项目的入口：

1. **标准入口**：通过 `DirectoryProjectGenerator` 在 CLion 的 "New Project" 向导中显示 "EGE" 项目类型
2. **快捷入口**：通过 `CreateEgeProjectAction` 在欢迎界面和文件菜单中显示 "New EGE Project..." 快速操作

这种双入口设计带来了以下问题：

- **用户困惑**：两个入口功能重复，用户不清楚应该使用哪个
- **维护成本**：需要维护两套几乎相同的代码逻辑
- **不符合 IDE 规范**：标准做法是使用项目向导，而不是自定义快捷操作
- **UI 不一致**：快捷入口使用弹出对话框选择选项，而标准入口应该在向导界面中显示选项

## 架构决策

经过重新审视，决定采用**单一标准入口**方案：

- **保留**：`DirectoryProjectGenerator` 作为唯一的项目创建方式
- **移除**：`CreateEgeProjectAction` 不再注册到 IDE 菜单中
- **改进**：在项目向导界面中直接显示选项（而不是弹出对话框）

## 实现方案

### 1. EgeProjectSettings 数据类

定义项目设置，用于在向导和生成器之间传递配置：

```kotlin
data class EgeProjectSettings(
    val useSourceCode: Boolean = false
)
```

### 2. EgeProjectGeneratorPeer 界面组件

实现 `ProjectGeneratorPeer<EgeProjectSettings>` 接口，在向导中显示项目选项：

```kotlin
class EgeProjectGeneratorPeer : ProjectGeneratorPeer<EgeProjectSettings> {
    private val useSourceCodeCheckbox = JCheckBox("直接使用 EGE 源码作为项目依赖", false)
    private val panel: JPanel
    
    init {
        // 创建包含说明和复选框的界面
    }
    
    override fun getSettings(): EgeProjectSettings {
        return EgeProjectSettings(useSourceCode = useSourceCodeCheckbox.isSelected)
    }
    
    override fun getComponent(): JComponent {
        return panel
    }
    
    // 其他必需的方法...
}
```

### 3. EgeProjectGenerator 重构

- 修改泛型参数：从 `DirectoryProjectGenerator<Any>` 改为 `DirectoryProjectGenerator<EgeProjectSettings>`
- `createPeer()` 返回 `EgeProjectGeneratorPeer` 实例
- `generateProject()` 接收 `EgeProjectSettings` 参数，根据用户选择复制相应资源：
  - `useSourceCode = false`：复制 `CMakeLists_lib.txt` 和 `ege_bundle`
  - `useSourceCode = true`：复制 `CMakeLists_src.txt` 和 `ege_src`

### 4. plugin.xml 简化

从 `plugin.xml` 中移除 `CreateEgeProjectAction` 的注册：

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- 只保留标准的项目生成器 -->
    <directoryProjectGenerator implementation="org.xege.project.EgeProjectGenerator"/>
</extensions>

<actions>
    <!-- 不再注册 CreateEgeProjectAction -->
</actions>
```

## 用户体验变化

### 之前（双入口）

1. **标准入口**：File → New → Project → EGE（但没有选项界面）
2. **快捷入口**：欢迎界面 → New EGE Project...（弹出对话框选择选项）

用户可能会疑惑：这两个有什么区别？应该用哪个？

### 之后（单一入口）

1. **唯一入口**：File → New → Project → EGE
2. **选项显示**：在向导界面中直接显示 "直接使用 EGE 源码作为项目依赖" 复选框
3. **无干扰**：欢迎界面不再显示自定义的快速操作

用户体验更加清晰，符合 IDE 的标准使用习惯。

## 技术优势

### 1. 符合平台规范

- 遵循 IntelliJ Platform 的最佳实践
- 使用标准的 `DirectoryProjectGenerator` API
- 项目选项在向导中显示，而不是通过弹出对话框

### 2. 代码简化

- 移除了 `CreateEgeProjectAction` 的注册（保留代码作为参考）
- 只需维护一套项目创建逻辑
- 减少了代码重复

### 3. 类型安全

- 使用 `EgeProjectSettings` 而不是 `Any` 类型
- 编译时类型检查，减少运行时错误

### 4. 易于扩展

- 如果未来需要添加更多选项，只需修改：
  - `EgeProjectSettings` 添加新字段
  - `EgeProjectGeneratorPeer` 添加新 UI 组件
  - `EgeProjectGenerator.generateProject()` 处理新选项

## 向后兼容性

- **插件升级**：用户升级插件后，"New EGE Project..." 快速操作将不再出现
- **现有项目**：已创建的项目不受影响
- **行为一致**：项目创建的核心逻辑（文件复制、模板选择）保持不变

## 测试要点

1. ✅ 验证 CLion 的 "New Project" 向导中显示 "EGE" 项目类型
2. ✅ 验证向导界面显示 "直接使用 EGE 源码作为项目依赖" 复选框和说明
3. ✅ 验证不勾选时创建的项目使用 `CMakeLists_lib.txt` 和 `ege_bundle`
4. ✅ 验证勾选时创建的项目使用 `CMakeLists_src.txt` 和 `ege_src`
5. ✅ 验证欢迎界面和文件菜单中不再显示 "New EGE Project..." 快速操作
6. ✅ 验证插件构建成功，无编译错误

## 代码参考

如果将来需要快速操作方式，可以参考保留的 `CreateEgeProjectAction.kt` 代码，但不建议同时使用两种方式。

## 总结

通过统一项目创建入口，插件的架构更加清晰、简洁，用户体验也更符合 IDE 的使用习惯。这次重构不仅简化了代码维护，也为未来的功能扩展奠定了更好的基础。

**核心原则**：遵循平台规范，提供标准一致的用户体验。
