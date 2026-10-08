#!/usr/bin/env python3
"""Build projects produced by the real generator loaded from a buildPlugin ZIP."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import sys
import tempfile
import zipfile

REPO = Path(__file__).resolve().parents[1]


def run(command, log, cwd=None, timeout=600, env=None):
    print(f'[{log.stem}] {command[0]}', flush=True)
    with log.open('w', encoding='utf-8') as output:
        try:
            process = subprocess.Popen([str(x) for x in command], cwd=cwd, env=env,
                                       stdout=output, stderr=subprocess.STDOUT,
                                       start_new_session=os.name != 'nt')
            try:
                code = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                # Stop the whole xvfb-run/CMake child tree, not only its wrapper process.
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/F', '/T', '/PID', str(process.pid)],
                                   stdout=output, stderr=subprocess.STDOUT, timeout=15)
                else:
                    os.killpg(process.pid, signal.SIGKILL)
                process.kill()
                process.wait(timeout=15)
                raise
            if code:
                raise subprocess.CalledProcessError(code, command)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            print(f'FAILED: {log}\n' + '\n'.join(log.read_text(errors='replace').splitlines()[-60:]), file=sys.stderr)
            raise


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kit', type=Path, default=REPO / 'build')
    parser.add_argument('--work-dir', type=Path, default=REPO / 'build/native-ci')
    parser.add_argument('--cmake', default='cmake')
    parser.add_argument('--java', default='java')
    parser.add_argument('--linux-window', action='store_true')
    args = parser.parse_args()
    system = platform.system()
    require(system in {'Linux', 'Darwin', 'Windows'}, f'Unsupported platform: {system}')
    require(not args.linux_window or system == 'Linux', '--linux-window requires Linux')
    zips = list((args.kit / 'distributions').glob('*.zip'))
    require(len(zips) == 1, 'Kit must contain exactly one formal plugin ZIP')
    runtime = list((args.kit / 'native-smoke-runtime').glob('*.jar'))
    require(len(runtime) == 2 and any(p.name == 'native-smoke-tool.jar' for p in runtime),
            'Kit must contain the smoke launcher and exactly one Kotlin stdlib JAR')
    args.work_dir.mkdir(parents=True, exist_ok=True)
    root = Path(tempfile.mkdtemp(prefix='packaged native projects ', dir=args.work_dir)).resolve()
    # Check the delivered artifact before loading any classes. Extract only the selected JAR bytes.
    run([sys.executable, REPO / 'scripts/check_plugin_zip.py', zips[0]], root / 'zip-check.log', timeout=60)
    with zipfile.ZipFile(zips[0]) as outer:
        matches = []
        for member in outer.namelist():
            if member.endswith('.jar'):
                data = outer.read(member)
                with zipfile.ZipFile(io.BytesIO(data)) as jar:
                    if 'org/xege/project/EgeResourceCopier.class' in jar.namelist():
                        matches.append(data)
        require(len(matches) == 1, 'Expected exactly one packaged EgeResourceCopier')
        plugin_jar = root / 'delivered-plugin.jar'
        plugin_jar.write_bytes(matches[0])
    classpath = os.pathsep.join(str(p.resolve()) for p in [plugin_jar, *runtime])

    def generate(project, demo=None):
        command = [args.java, '-cp', classpath, 'org.xege.project.EgeNativeSmokeTool', project]
        if demo:
            command.append(demo)
        run(command, root / f'{project.name}-generate.log', timeout=60)

    fresh = root / 'fresh camera project'
    generate(fresh, 'camera_base.cpp')
    # The shipped camera demo stays unchanged. Add a separate noninteractive test executable.
    app = fresh / 'app with spaces'
    app.mkdir()
    (app / 'window_smoke.cpp').write_bytes((REPO / 'tests/native/window_smoke.cpp').read_bytes())
    (app / 'CMakeLists.txt').write_text('''add_library(smoke-helper INTERFACE)
add_executable(native-window-smoke window_smoke.cpp)
target_link_libraries(native-window-smoke smoke-helper)
add_custom_target(native-window-smoke_ege_assets)
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    find_package(X11 REQUIRED)
    target_include_directories(native-window-smoke PRIVATE ${X11_INCLUDE_DIR})
    target_link_libraries(native-window-smoke ${X11_LIBRARIES})
endif()
''', encoding='utf-8')
    entry = fresh / 'CMakeLists.txt'
    text = entry.read_text()
    include = 'include("${CMAKE_CURRENT_LIST_DIR}/ege-project.cmake")'
    require(include in text, 'Generated CMake entrypoint missing')
    entry.write_text(text.replace(include, 'add_subdirectory("app with spaces")\n' + include), encoding='utf-8')

    converted = root / 'existing user project'
    converted.mkdir()
    user_source = '// preserved user source\n#include <graphics.h>\nint main() { return ege::getwidth(); }\n'
    user_cmake = '''cmake_minimum_required(VERSION 3.14)
project(existing LANGUAGES C CXX)
add_library(existing-helper INTERFACE)
add_executable(existing-app main.cpp)
target_link_libraries(existing-app existing-helper)
add_custom_target(existing-app_ege_assets)
'''
    (converted / 'main.cpp').write_text(user_source, encoding='utf-8')
    (converted / 'CMakeLists.txt').write_text(user_cmake, encoding='utf-8')
    (converted / '.gitignore').write_text('user-ignore\n', encoding='utf-8')
    (converted / '.vscode').mkdir()
    (converted / '.vscode/settings.json').write_text('{"user": true}\n', encoding='utf-8')
    generate(converted)
    require((converted / 'main.cpp').read_text() == user_source, 'Conversion changed user source')
    require((converted / 'CMakeLists.txt').read_text().startswith(user_cmake), 'Conversion changed user CMake')
    require((converted / '.gitignore').read_text() == 'user-ignore\n', 'Conversion changed user ignore')
    require((converted / '.vscode/settings.json').read_text() == '{"user": true}\n', 'Conversion changed user settings')

    results = []
    for project, names in [(fresh, ['ege-demo', 'native-window-smoke']), (converted, ['existing-app'])]:
        build = project / 'build with spaces'
        configure = [args.cmake, '-S', project, '-B', build]
        if system == 'Windows':
            configure += ['-G', 'Visual Studio 17 2022', '-A', 'x64']
        else:
            configure += ['-G', 'Ninja', '-DCMAKE_BUILD_TYPE=Release']
            if system == 'Darwin':
                configure += ['-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0']
        run(configure, root / f'{project.name}-configure.log', timeout=180)
        run([args.cmake, '--build', build, '--config', 'Release', '--parallel', '2'],
            root / f'{project.name}-build.log', timeout=900)
        cache = (build / 'CMakeCache.txt').read_text(errors='replace')
        backend = {'Linux': 'CAIRO', 'Darwin': 'COREGRAPHICS', 'Windows': 'GDI'}[system]
        require(f'EGE_RESOLVED_BACKEND:INTERNAL={backend}' in cache, 'Incorrect platform backend')
        # XEGE compiles ccap into graphics itself; option() can retain a normal variable.
        camera_objects = [p for p in build.rglob('ccap_core*') if p.suffix in {'.o', '.obj'}]
        build_definition = build / ('ege/xege.vcxproj' if system == 'Windows' else 'build.ninja')
        require(camera_objects and 'EGE_ENABLE_CAMERA_CAPTURE=1' in build_definition.read_text(errors='replace'),
                'Camera sources were not compiled into XEGE')
        compiler_configs = list((build / 'CMakeFiles').glob('*/CMakeCXXCompiler.cmake'))
        compiler = '\n'.join(p.read_text(errors='replace') for p in compiler_configs)
        if system == 'Windows':
            require('CMAKE_CXX_COMPILER_ID "MSVC"' in compiler, 'Windows job did not use MSVC')
        for name in names:
            binaries = [p for p in build.rglob(name + ('.exe' if system == 'Windows' else '')) if p.is_file()]
            require(len(binaries) == 1, f'Expected one native executable: {name}')
            binary = binaries[0]
            magic = binary.read_bytes()[:4]
            require({'Linux': magic == b'\x7fELF', 'Darwin': magic in (b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf'),
                     'Windows': magic[:2] == b'MZ'}[system], f'Wrong executable format: {binary}')
            for image in ('getimage.png', 'getimage.jpg'):
                require((binary.parent / image).read_bytes() == (project / image).read_bytes(), f'Missing/copied wrong image: {image}')
            if args.linux_window and name == 'native-window-smoke':
                env = os.environ.copy()
                env.pop('EGE_HEADLESS', None)
                run(['xvfb-run', '-a', '-s', '-screen 0 1024x768x24', binary],
                    root / 'linux-window.log', cwd=binary.parent, env=env, timeout=30)
            results.append({'project': project.name, 'target': name, 'binary': str(binary), 'backend': backend})
    report = {'platform': system, 'zip_sha256': hashlib.sha256(zips[0].read_bytes()).hexdigest(),
              'generator': 'EgeResourceCopier from formal plugin ZIP', 'projects_with_spaces': True,
              'existing_user_files_preserved': True, 'native_builds': results,
              'linux_xvfb_window_smoke': args.linux_window, 'logs': str(root)}
    (args.work_dir / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        print(f'Native plugin verification failed: {error}', file=sys.stderr)
        sys.exit(1)
