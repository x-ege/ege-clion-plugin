# EGE Options 菜单使用说明

## 菜单结构

在工具菜单中添加了一个 **EGE Options** 菜单组，包含以下选项：

```
Tools
  └─ EGE Options ►
                  ├─ 创建 EGE 项目
                  ├─ ───────────
                  └─ 关于 EGE
```

## 功能说明

### 1. 创建 EGE 项目

点击此选项后，将执行以下步骤：

#### 步骤 1: 选择项目位置
- 弹出文件夹选择对话框
- 选择要创建项目的目录
- 如果目录不为空，会询问是否继续

#### 步骤 2: 配置项目选项
弹出配置对话框，包含：

**选项说明**：
- **不勾选（推荐）**：使用预编译的 EGE 静态库
  - 优点：编译速度快，项目结构简单
  
- **勾选**：直接使用 EGE 源代码
  - 优点：可以查看和修改 EGE 内部实现，适合高级用户

**复选框**："直接使用 EGE 源码作为项目依赖"

#### 步骤 3: 创建项目
- 显示进度条
- 自动复制模板文件和库文件
- 创建完成后弹出成功提示
- 提示用户通过 File → Open... 打开项目

### 2. 关于 EGE

显示 EGE 插件的信息和帮助内容。

## 使用场景

### 场景 1: 快速创建新项目（推荐）
1. Tools → EGE Options → 创建 EGE 项目
2. 选择项目位置
3. 保持默认选项（不勾选）
4. 确定创建
5. File → Open 打开创建的项目

### 场景 2: 需要查看 EGE 源码的项目
1. Tools → EGE Options → 创建 EGE 项目
2. 选择项目位置
3. **勾选**"直接使用 EGE 源码作为项目依赖"
4. 确定创建
5. File → Open 打开创建的项目

## 与向导方式的对比

| 特性 | 菜单方式 | 向导方式 |
|------|----------|----------|
| 入口 | Tools → EGE Options | File → New → Project → EGE |
| 选项位置 | 单独对话框 | 向导界面中 |
| 项目选择 | 文件夹选择器 | 向导路径输入 |
| 适用场景 | 快速创建独立项目 | 在 IDE 中创建新项目 |

## 注意事项

1. 创建的项目需要手动通过 File → Open 打开
2. 建议选择空目录或不存在的目录作为项目位置
3. 如果不确定，推荐使用预编译库选项（不勾选）
4. 使用源码选项会增加首次编译时间，但可以调试 EGE 内部代码

## 扩展说明

### 未来可能添加的选项

在 `plugin.xml` 的 `EgeOptionsGroup` 中可以继续添加：

```xml
<!-- 在线文档 -->
<action id="org.xege.DocumentationAction"
        class="org.xege.DocumentationAction"
        text="在线文档"
        description="打开 EGE 在线文档"/>

<!-- 示例代码 -->
<action id="org.xege.ExamplesAction"
        class="org.xege.ExamplesAction"
        text="示例代码"
        description="浏览 EGE 示例代码"/>

<!-- 检查更新 -->
<action id="org.xege.CheckUpdateAction"
        class="org.xege.CheckUpdateAction"
        text="检查更新"
        description="检查插件和 EGE 库的更新"/>
```

## 开发说明

相关代码文件：
- `plugin.xml` - 菜单注册和配置
- `CreateEgeProjectAction.kt` - 创建项目的核心逻辑
- `EgeProjectGenerator.kt` - 项目生成器（向导方式使用）

两种创建方式共享相同的文件复制和项目生成逻辑。
