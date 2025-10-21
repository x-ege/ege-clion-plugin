# CLion 项目向导问题排查报告

## 问题描述

在 CLion 的新建项目向导中无法看到 EGE 项目选项。

## 问题原因

`DirectoryProjectGenerator` 在 CLion 2023.3+ 版本中的工作方式可能与预期不同。CLion 的新建项目向导可能需要特定的配置或不同的扩展点。

## 解决方案

已完成以下修改：

### 1. 添加 CLion 模块依赖

在 `plugin.xml` 中添加：
```xml
<depends optional="true" config-file="clion-only.xml">com.intellij.modules.clion</depends>
```

### 2. 创建 CLion 特定配置

创建了 `clion-only.xml`，虽然目前为空，但为将来的 CLion 特定功能预留了位置。

### 3. 保持现有实现

继续使用 `EgeProjectGenerator` 实现 `DirectoryProjectGenerator<Any>` 接口。

## 测试步骤

### 方法 1: 开发环境测试

```bash
cd /Volumes/HikData/work/git/xege-intellij-plugin
./gradlew runIde
```

在启动的 CLion 实例中：
1. 点击 `File → New → Project...` 或欢迎页面的 `New Project`
2. 在左侧项目类型列表中查找 "EGE"
3. 如果看到 "EGE"，选择它并创建测试项目

### 方法 2: 检查插件是否加载

在测试 IDE 中：
1. 进入 `Settings/Preferences → Plugins`
2. 查找 "Xege IntelliJ Creator"
3. 确认插件已启用

### 方法 3: 查看日志

如果看不到 EGE 选项：
1. 在测试 IDE 中打开 `Help → Show Log in Finder/Explorer`
2. 搜索 "EgeProjectGenerator" 或 "xege"
3. 查看是否有加载错误

## 可能的备选方案

如果 `DirectoryProjectGenerator` 在 CLion 中仍然不工作，可以考虑以下替代方案：

### 方案 A: 使用 Action 触发

添加一个菜单 Action 来创建 EGE 项目：

```kotlin
class CreateEgeProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // 弹出文件选择器
        // 创建项目
    }
}
```

在 plugin.xml 中注册：
```xml
<action id="org.xege.CreateEgeProject"
        class="org.xege.CreateEgeProjectAction"
        text="New EGE Project..."
        description="Create a new EGE C++ project">
    <add-to-group group-id="NewGroup" anchor="after" relative-to-action="NewProjectAction"/>
</action>
```

### 方案 B: 使用文件模板

创建 CMake 项目的文件模板，用户可以通过 `File → New → ...` 添加到现有项目。

### 方案 C: 项目导入

实现一个项目导入器，用户可以从模板导入项目。

## 当前状态

- ✅ 插件构建成功
- ✅ 资源正确打包
- ⏳ 需要在 CLion 中测试新建项目向导
- ⏳ 如果不工作，需要实现备选方案

## 调试建议

### 1. 启用详细日志

在测试 IDE 的 `Help → Diagnostic Tools → Debug Log Settings` 中添加：
```
#org.xege
```

### 2. 检查扩展点

在测试 IDE 中打开 `Tools → Internal Actions → UI → UI Inspector`，查看新建项目对话框的结构。

### 3. 验证模块加载

在测试 IDE 的控制台执行：
```kotlin
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.openapi.extensions.Extensions
Extensions.getExtensions(DirectoryProjectGeneratorBase.EP_NAME)
```

## 下一步行动

1. **立即测试**: 运行 `./gradlew runIde` 并测试新建项目功能
2. **查看日志**: 如果看不到选项，检查日志找出原因
3. **实现备选方案**: 如果 DirectoryProjectGenerator 不工作，实现 Action 方案
4. **更新文档**: 根据测试结果更新用户文档

## 技术说明

### DirectoryProjectGenerator 的工作原理

这个扩展点主要用于：
- PyCharm: Python 项目模板
- WebStorm: Node.js、Web 项目模板
- IntelliJ IDEA: 通用项目模板

对于 CLion 的 C/C++ 项目，可能需要：
- 使用 CMake 项目配置
- 实现特定的 C/C++ 项目接口
- 或使用不同的扩展点

### CLion 特定的考虑

CLion 主要处理：
1. **CMake 项目**: 通过 CMakeLists.txt
2. **Makefile 项目**: 通过 Makefile
3. **编译数据库项目**: 通过 compile_commands.json

我们的 EGE 项目使用 CMake，所以理论上应该能正常工作。

##总结

已经完成了基础配置，现在需要实际测试。如果 `DirectoryProjectGenerator` 在 CLion 中不显示，我们有多个备选方案可以实现。最重要的是先运行测试看看实际效果。
