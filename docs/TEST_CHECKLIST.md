# 测试清单

## 已完成的实现

- [x] 配置 Gradle 打包 assets 资源
- [x] 设置插件图标（logo.png）
- [x] 实现项目生成器（EgeProjectGenerator）
- [x] 注册扩展点到 plugin.xml
- [x] 实现文件复制逻辑（支持 JAR 和文件系统）
- [x] 编译通过

## 需要测试的功能

### 1. 插件加载测试
- [ ] 运行 `./gradlew runIde` 启动测试 IDE
- [ ] 检查插件是否正常加载
- [ ] 检查是否有错误日志

### 2. 图标显示测试
- [ ] 在新建项目向导中是否显示 EGE 选项
- [ ] EGE 选项旁边是否显示 logo 图标
- [ ] 插件管理页面是否显示图标

### 3. 项目创建测试
- [ ] 选择 EGE 项目类型
- [ ] 输入项目名称和位置
- [ ] 点击创建，观察进度条
- [ ] 检查是否成功创建项目

### 4. 生成文件验证
- [ ] 检查 CMakeLists.txt 是否存在
- [ ] 检查 main.cpp 是否存在
- [ ] 检查 ege/ 目录是否存在
- [ ] 检查 ege/include/ 目录及头文件
- [ ] 检查 ege/lib/ 目录及库文件

### 5. 项目编译测试
- [ ] 在 CLion 中打开生成的项目
- [ ] 检查 CMake 是否正常加载
- [ ] 尝试编译项目
- [ ] 尝试运行示例程序

## 测试命令

```bash
# 清理之前的构建
./gradlew clean

# 重新构建
./gradlew build

# 启动测试 IDE
./gradlew runIde
```

## 调试技巧

### 查看日志
在测试 IDE 中：
- `Help → Show Log in Finder/Explorer`
- 搜索 "EgeProjectGenerator" 相关日志

### 检查资源
在构建后的 JAR 中检查资源是否被正确打包：
```bash
cd build/libs
jar -tf xege-intellij-plugin-1.0-SNAPSHOT.jar | grep assets
```

### 常见问题

1. **图标不显示**
   - 检查 logo.png 是否在 assets/ 目录
   - 检查 plugin.xml 中的路径是否正确
   - 检查资源是否被正确打包到 JAR

2. **项目创建失败**
   - 查看 IDE 日志中的错误信息
   - 检查资源文件路径
   - 确认目标目录权限

3. **文件没有被复制**
   - 检查资源加载方式（JAR vs 文件系统）
   - 查看日志中的 "Copied" 信息
   - 确认 assets 目录结构正确

## 预期结果

成功的测试应该：
1. 插件正常加载，无错误
2. 新建项目向导中显示 EGE 选项及图标
3. 可以成功创建项目，所有文件都被正确复制
4. 生成的项目可以在 CLion 中正常打开和编译
5. 示例程序可以运行并显示图形窗口

## 下一步

测试通过后：
1. 创建正式的 release 版本
2. 编写用户文档
3. 准备发布到插件市场（可选）
