# UI 改进测试清单

## 测试目标

验证以下 4 个问题的修复：

1. ✅ 移除新建项目菜单中的图标
2. ✅ 为插件添加图标（在插件管理界面显示）
3. ✅ 添加"直接使用 EGE 源码作为项目依赖"选项
4. ✅ 根据选项使用不同的 CMakeLists 模板

## 测试步骤

### 1. 插件图标测试

**目标**: 验证插件在插件管理界面有正确的图标

**步骤**:
1. 运行 `./gradlew runIde`
2. 在测试 IDE 中，打开 `Settings/Preferences → Plugins`
3. 搜索 "Xege"
4. 检查插件列表中是否显示蓝色的 "EGE" 图标

**预期结果**:
- [ ] 插件显示蓝底白字的 "EGE" 图标（40x40 SVG）

---

### 2. Action 图标测试

**目标**: 验证"New EGE Project..."菜单项只显示文字，不显示图标

**步骤**:
1. 在测试 IDE 的欢迎页面，查看 Quick Start 部分
2. 在 `File` 菜单中查找 "New EGE Project..."

**预期结果**:
- [ ] 欢迎页面的 "New EGE Project..." 只有文字，没有图标
- [ ] File 菜单中的 "New EGE Project..." 只有文字，没有图标
- [ ] 不会出现巨大的 logo.png 图片显示

---

### 3. 选项对话框测试

**目标**: 验证项目创建流程中出现选项对话框

**步骤**:
1. 点击 "New EGE Project..."（欢迎页面或 File 菜单）
2. 在文件选择器中选择一个空目录
3. 观察是否出现 "EGE Project Options" 对话框

**预期结果**:
- [ ] 选择目录后，弹出 "EGE Project Options" 对话框
- [ ] 对话框中有一个 CheckBox: "直接使用 EGE 源码作为项目依赖"
- [ ] CheckBox 默认未选中
- [ ] 对话框有 "OK" 和 "Cancel" 按钮

---

### 4. 使用预编译库创建项目（默认选项）

**目标**: 验证不选中源码选项时，使用预编译库

**步骤**:
1. 点击 "New EGE Project..."
2. 选择目录: `/tmp/test-ege-lib`
3. 在选项对话框中，**不选中** CheckBox（保持默认）
4. 点击 "OK" 创建项目
5. 等待项目创建完成
6. 检查生成的文件

**预期结果**:
- [ ] 项目创建成功提示
- [ ] 存在 `/tmp/test-ege-lib/CMakeLists.txt`
- [ ] 存在 `/tmp/test-ege-lib/main.cpp`
- [ ] 存在 `/tmp/test-ege-lib/ege/include/` 目录
- [ ] 存在 `/tmp/test-ege-lib/ege/lib/` 目录
- [ ] **不存在** `/tmp/test-ege-lib/ege/src/` 目录
- [ ] **不存在** `/tmp/test-ege-lib/ege/CMakeLists.txt` 文件

**验证 CMakeLists.txt 内容**:
- [ ] 打开 `CMakeLists.txt`
- [ ] **不包含** `add_subdirectory(ege)` 行
- [ ] 包含根据编译器选择库路径的逻辑（`if(MSVC)`, `if(CMAKE_HOST_UNIX)` 等）

---

### 5. 使用源码创建项目

**目标**: 验证选中源码选项时，使用 EGE 源码

**步骤**:
1. 点击 "New EGE Project..."
2. 选择目录: `/tmp/test-ege-src`
3. 在选项对话框中，**选中** CheckBox "直接使用 EGE 源码作为项目依赖"
4. 点击 "OK" 创建项目
5. 等待项目创建完成
6. 检查生成的文件

**预期结果**:
- [ ] 项目创建成功提示
- [ ] 存在 `/tmp/test-ege-src/CMakeLists.txt`
- [ ] 存在 `/tmp/test-ege-src/main.cpp`
- [ ] 存在 `/tmp/test-ege-src/ege/include/` 目录
- [ ] 存在 `/tmp/test-ege-src/ege/src/` 目录（源码目录）
- [ ] 存在 `/tmp/test-ege-src/ege/3rdparty/` 目录
- [ ] 存在 `/tmp/test-ege-src/ege/CMakeLists.txt` 文件

**验证 CMakeLists.txt 内容**:
- [ ] 打开根目录的 `CMakeLists.txt`
- [ ] **包含** `add_subdirectory(ege)` 行
- [ ] 包含 `target_link_libraries(${MY_TARGET_NAME} PRIVATE xege)` 行
- [ ] **不包含** 根据编译器选择库路径的复杂逻辑

---

### 6. 对比两种模板

**目标**: 验证两种模板的 CMakeLists.txt 确实不同

**步骤**:
1. 对比 `/tmp/test-ege-lib/CMakeLists.txt` 和 `/tmp/test-ege-src/CMakeLists.txt`

**预期差异**:

#### test-ege-lib (预编译库版本)
```cmake
# 应该包含：
- CMAKE_SYSTEM_NAME Windows (交叉编译设置)
- target_include_directories(... ege/include)
- if(MSVC) ... set(osLibDir "vs2022/x64") ...
- target_link_libraries(... ege.a 或 ege-static.lib)
```

#### test-ege-src (源码版本)
```cmake
# 应该包含：
- add_subdirectory(ege)
- target_link_libraries(... xege)
- target_link_options(... -static ...)
- 更简洁的结构
```

---

### 7. 项目可编译性测试（可选）

**目标**: 验证生成的项目可以正常编译

**步骤**:
1. 在 CLion 中打开 `/tmp/test-ege-lib`
2. 等待 CMake 加载完成
3. 尝试构建项目

**预期结果**:
- [ ] CMake 配置成功
- [ ] 项目可以编译（如果有合适的编译器）

---

## 问题排查

### 如果插件图标不显示
- 检查 `src/main/resources/META-INF/pluginIcon.svg` 是否存在
- 检查 SVG 文件是否被正确打包到插件 JAR 中
- 重新构建插件: `./gradlew clean build`

### 如果选项对话框不显示
- 检查 `CreateEgeProjectAction.kt` 中 `ProjectOptionsDialog` 的实现
- 查看 IDE 日志: `Help → Show Log in Finder/Explorer`

### 如果生成的文件不正确
- 检查 assets 目录中是否包含 `ege_src` 和 `ege_bundle` 两个目录
- 检查 `copyCMakeTemplateFiles` 和 `copyEgeBundle` 函数的逻辑
- 查看日志中的文件复制信息

---

## 测试总结

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 插件图标显示 | ⬜ | |
| Action 无图标 | ⬜ | |
| 选项对话框 | ⬜ | |
| 预编译库项目 | ⬜ | |
| 源码项目 | ⬜ | |
| CMakeLists 对比 | ⬜ | |
| 可编译性 | ⬜ | 可选 |

---

## 自动化测试命令

```bash
# 1. 构建插件
./gradlew clean build

# 2. 运行测试 IDE
./gradlew runIde

# 3. 清理测试目录
rm -rf /tmp/test-ege-lib /tmp/test-ege-src

# 4. 查看生成的文件（测试后）
tree /tmp/test-ege-lib
tree /tmp/test-ege-src

# 5. 对比 CMakeLists.txt
diff /tmp/test-ege-lib/CMakeLists.txt /tmp/test-ege-src/CMakeLists.txt
```
