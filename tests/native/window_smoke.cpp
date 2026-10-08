#include <graphics.h>
#include <cstdint>
#include <cstdio>
#include <initializer_list>
#ifdef __linux__
#include <X11/Xlib.h>
#endif

int main()
{
    ege::initgraph(320, 240, ege::INIT_NOFORCEEXIT);
    if (!ege::getHWnd() || !ege::is_run() || ege::getwidth() != 320 || ege::getheight() != 240) {
        std::fprintf(stderr, "EGE native window initialization failed\n");
        return 1;
    }
    ege::setfillcolor(EGERGB(20, 80, 160));
    ege::bar(10, 10, 120, 100);
    ege::circle(160, 120, 30);
    ege::PIMAGE image = ege::newimage();
    for (const char* file : {"getimage.png", "getimage.jpg"}) {
        if (ege::getimage(image, file) != 0 || ege::getwidth(image) <= 0) {
            std::fprintf(stderr, "Cannot load packaged image: %s\n", file);
            return 2;
        }
        ege::putimage(0, 0, image);
    }
    ege::delimage(image);
    ege::delay_ms(100);
#ifdef __linux__
    Display* display = XOpenDisplay(nullptr);
    XWindowAttributes attributes{};
    const auto window = static_cast<::Window>(reinterpret_cast<std::uintptr_t>(ege::getHWnd()));
    if (!display || !XGetWindowAttributes(display, window, &attributes) || attributes.map_state != IsViewable) {
        std::fprintf(stderr, "EGE did not create a viewable native X11 window\n");
        return 3;
    }
    XCloseDisplay(display);
#endif
    ege::closegraph();
    std::puts("EGE native window, drawing and PNG/JPEG smoke passed");
    return 0;
}
