# Xege Creator

通过 CLion **C++ → Xege (EGE)** 向导快速创建 Easy Graphics Engine 项目。
也可通过菜单创建项目，或给现有 CMake 项目添加 EGE。

- Windows、macOS、Linux 使用同一套固定、随插件打包的源码，在目标机原生编译。
- Windows 使用 GDI，macOS 使用 CoreGraphics，Linux 使用 Cairo/X11。
- 包含绘图、游戏、算法、相机示例与图片资源。
- 无预编译库混装，无生成项目构建时下载。
- 转换项目保留现有源码和 CMake，冲突资源会报告错误而不会覆盖。

需要支持 C++17 的工具链及 CMake。macOS 需要 Xcode Command Line Tools；
Linux 还需要 pkg-config、Cairo/X11 开发包及 X11/XWayland 显示环境。
插件生成的相机示例包含辅助头文件和 macOS 相机用途声明。

资源和示例来自固定 EGE `09387a806e3d8cafde84bf0bd91b775d681b27ac`，
ccap `d1876005be7e7cc0c370fadd05dbac6c658c4a17`，附带双方许可证。
