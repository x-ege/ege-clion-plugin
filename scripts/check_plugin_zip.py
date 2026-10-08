#!/usr/bin/env python3
"""Verify the assets actually shipped inside a buildPlugin ZIP, not the source tree."""
import argparse
import hashlib
import io
import json
import re
import stat
import zipfile
from pathlib import Path
from source_assets import validate_asset, validate_path

EGE_COMMIT = '09387a806e3d8cafde84bf0bd91b775d681b27ac'
CCAP_COMMIT = 'd1876005be7e7cc0c370fadd05dbac6c658c4a17'
REQUIRED = {
    'assets/ege_src/CMakeLists.txt',
    'assets/ege_src/cmake/EgeBackends.cmake',
    'assets/ege_src/cmake/EgeSources.cmake',
    'assets/ege_src/src/backend/linux/LinuxWindow.cpp',
    'assets/ege_src/src/backend/macos/MacWindow.mm',
    'assets/ege_src/3rdparty/ccap/src/ccap_imp_apple.mm',
    'assets/ege_src/3rdparty/ccap/LICENSE', 'assets/ege_src/LICENSE',
    'assets/ege_demos/camera_demo_screen.h', 'assets/ege_demos/macos-camera-info.plist',
    'assets/ege_demos/getimage.png', 'assets/ege_demos/getimage.jpg',
    'assets/cmake_template/CMakeLists_src.txt', 'assets/cmake_template/ege-project.cmake',
    'assets/cmake_template/main.cpp', 'assets/ege_src/source.properties',
}


def validate_entry(entry):
    validate_asset(entry.filename.rstrip('/'))
    if stat.S_ISLNK(entry.external_attr >> 16):
        raise ValueError(f'Unexpected archive symlink: {entry.filename}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('zip', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.zip) as outer:
        assert outer.testzip() is None, 'ZIP CRC failure'
        for entry in outer.infolist():
            validate_entry(entry)
        jars = [name for name in outer.namelist() if name.endswith('.jar')]
        resources = {}
        resource_jar = None
        for name in jars:
            with zipfile.ZipFile(io.BytesIO(outer.read(name))) as jar:
                assert jar.testzip() is None, f'JAR CRC failure: {name}'
                for entry in jar.infolist():
                    validate_entry(entry)
                for member in jar.namelist():
                    if member.startswith('assets/') and not member.endswith('/'):
                        assert member not in resources, f'Duplicate packaged resource: {member}'
                        resources[member] = jar.read(member)
                        if member == 'assets/resource-manifest.sha256':
                            resource_jar = name
        assert resource_jar, 'Packaged resource manifest is absent'
        expected = {}
        for line in resources['assets/resource-manifest.sha256'].decode().splitlines():
            checksum, relative = line.split('  ', 1)
            validate_path(relative)
            assert re.fullmatch('[a-f0-9]{64}', checksum), 'Invalid asset checksum'
            name = 'assets/' + relative
            assert name not in expected, f'Duplicate manifest path: {name}'
            expected[name] = checksum
        actual = set(resources) - {'assets/resource-manifest.sha256'}
        assert actual == set(expected), f'Manifest mismatch: missing={set(expected)-actual}, extra={actual-set(expected)}'
        for name, checksum in expected.items():
            data = resources[name]
            assert hashlib.sha256(data).hexdigest() == checksum, f'Packaged checksum mismatch: {name}'
            validate_asset(name, data)
            assert 'ege_bundle/' not in name, f'Legacy precompiled bundle: {name}'
        assert REQUIRED <= actual, f'Missing runtime resources: {REQUIRED-actual}'
        pins = resources['assets/ege_src/source.properties'].decode()
        assert EGE_COMMIT in pins and CCAP_COMMIT in pins, 'Wrong source pins'
        template = resources['assets/cmake_template/CMakeLists_src.txt'].decode()
        integration = resources['assets/cmake_template/ege-project.cmake'].decode()
        assert 'ege-project.cmake' in template and 'add_subdirectory' in integration
        assert 'CMAKE_SYSTEM_NAME Windows' not in template + integration
        assert '-mwindows' not in template + integration
        report = {
            'zip': str(args.zip.resolve()), 'zip_bytes': args.zip.stat().st_size,
            'sha256': hashlib.sha256(args.zip.read_bytes()).hexdigest(),
            'resource_jar': resource_jar, 'verified_assets': len(expected),
            'verified_source_files': sum(n.startswith('assets/ege_src/') for n in expected),
            'ege_commit': EGE_COMMIT, 'ccap_commit': CCAP_COMMIT,
            'all_manifest_checksums_match': True, 'precompiled_binaries': 0,
            'required_runtime_resources_and_licenses_present': True,
        }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
