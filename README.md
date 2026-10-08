# Xege Creator for CLion

在 CLion 的新建项目向导中选择 **C++ → Xege (EGE)**，选择示例后创建项目。
插件保留菜单新建项目与“Setup as EGE Project”转换入口，支持 CLion 2023.3 及以上。

新项目包含 `CMakeLists.txt`、`ege-project.cmake`、示例 `main.cpp`、图片、相机示例支持文件，
以及完整的 `ege/` 固定源码。选择 CLion 的原生工具链即可编译，无需 Wine 或 MinGW 交叉工具链。
Windows 用户可选 MSVC 或原生 MinGW。

## 固定源码与原生构建

插件现在在 Windows、macOS、Linux 上统一生成源码项目，不再携带预编译库。
EGE 固定为 `09387a806e3d8cafde84bf0bd91b775d681b27ac`，ccap 固定为
`d1876005be7e7cc0c370fadd05dbac6c658c4a17`。完整平台源码、CMake 模块和许可证随插件打包，
生成项目的配置与编译不会下载依赖或自动运行 Git。

- Windows：支持 C++17 的 MSVC 或原生 MinGW，使用 GDI 后端。MSVC 需要 Visual Studio 2022/Build Tools 的“使用 C++ 的桌面开发”工作负载与 Windows SDK；CLion 选择对应 Visual Studio 工具链。CI 使用 `Visual Studio 17 2022`、x64。首次构建会编译 EGE/ccap，较旧预编译模式耗时更长；不再支持仅复制旧库而不编译依赖的模式。
- macOS：Xcode Command Line Tools/AppleClang、CMake，使用系统 CoreGraphics/AppKit；最低目标版本 11.0。
- Linux：C++17 工具链、CMake、pkg-config、Cairo/X11 开发包；运行窗口需要 X11 或 XWayland。
  Debian/Ubuntu 的包为 `build-essential cmake ninja-build pkg-config libcairo2-dev libx11-dev`。

创建或转换项目会校验所有资源 SHA256，缺少或损坏资源会报告失败。转换会保留现有
`main.cpp` 和 CMake 内容，在 CMake 末尾加入 `ege-project.cmake`，给已有可执行目标关联 EGE。
现有 `ege` 目录或冲突支持文件不会被覆盖，请先备份并移开。图片同时位于项目根目录和目标输出目录，
camera 示例包含辅助头文件及 macOS 相机用途声明。已由 CLion 管理的 CMake 项目会在刷新后重载；
菜单新建项目仍通过 CLion 的 open/import 流程打开。

维护资源时从**独立、tracked 文件干净**的固定版本 checkout 更新，不能使用包含本机改动的工作副本。
更新器从 EGE 和 ccap 各自固定 Git tree 读取 tracked blob；ignored/untracked 文件不会进入 bundle，
符号链接、危险路径和编译产物会被拒绝：

```sh
./update_ege_src.sh /path/to/pinned-xege-checkout
python3 scripts/package_ege_source.py --check
./gradlew test buildPlugin
```

打包会自动检查资源清单与内容，不允许加入预编译库。

维护者构建时，Gradle IntelliJ Plugin 1.x 默认在非 CI 环境下载 IntelliJ Platform 源码，
用于源码导航。若不需要，可在 `intellij` 配置中设置 `downloadSources.set(false)`；
该选项不影响插件随附的 EGE/ccap 原生源码。
参见 [官方 downloadSources 说明](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#downloadsources)。

## 正式插件包的原生验证

CI 只构建一次 `buildPlugin` ZIP，Linux/macOS/Windows 三个作业下载同一份产物。
测试启动器从 ZIP 内的插件 JAR 调用实际 `EgeResourceCopier`，不会从工作树复制资源或另写生成器。
新建相机示例与转换现有项目均在含空格路径配置、编译，检查原生 ELF/Mach-O/PE 格式、图片复制、
已有 CMake plain 链接签名和用户文件保留。Linux 在 Xvfb 下运行独立的非交互图形测试，
确认真实 X11 可见窗口、绘图及 PNG/JPEG 加载；运行限时 30 秒。macOS/Windows 仅编译，不宣称窗口运行已验证。
失败输出最后 60 行日志，各平台完整日志与成功报告作为 CI artifact 保存。

本机复现（安装上述原生依赖，Java 17 与 Python 3；非 Windows 另需 Ninja）：

```sh
./gradlew test buildPlugin stageNativeSmokeRuntime
python3 scripts/native_plugin_smoke.py --kit build
# Linux 额外安装 xvfb 与 xauth，并追加 --linux-window
```

此检查覆盖包内生成器与原生构建；CLion GUI 向导、打开项目和 CMake 自动关联仍需单独人工验证。
