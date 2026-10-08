#!/usr/bin/env python3
"""Import a clean pinned upstream checkout, or validate all bundled assets offline."""
import argparse
import hashlib
import shutil
import subprocess
import tempfile
from pathlib import Path

EGE_COMMIT = '09387a806e3d8cafde84bf0bd91b775d681b27ac'
CCAP_COMMIT = 'd1876005be7e7cc0c370fadd05dbac6c658c4a17'
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'assets'
MANIFEST = ASSETS / 'resource-manifest.sha256'


def manifest():
    files = sorted(p for p in ASSETS.rglob('*') if p.is_file() and p != MANIFEST)
    for p in files:
        if p.is_symlink() or p.suffix.lower() in {'.a', '.lib', '.dll', '.exe', '.so', '.dylib'}:
            raise ValueError(f'Unexpected binary or symlink: {p}')
    return ''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' +
                   p.relative_to(ASSETS).as_posix() + '\n' for p in files)


def git(path, *args):
    return subprocess.check_output(['git', '-C', str(path), *args], text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkout', nargs='?', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.check:
        assert MANIFEST.read_text() == manifest(), 'Assets changed; regenerate the manifest from the pinned checkout'
        pins = (ASSETS / 'ege_src/source.properties').read_text()
        assert EGE_COMMIT in pins and CCAP_COMMIT in pins, 'Wrong source pins'
        print('Verified all bundled asset SHA256 values; sources only, no precompiled libraries')
        return
    if args.checkout is None:
        parser.error('Provide the clean pinned XEGE checkout, or use --check')
    source = args.checkout.resolve()
    ccap = source / '3rdparty/ccap'
    for repo, commit in [(source, EGE_COMMIT), (ccap, CCAP_COMMIT)]:
        assert git(repo, 'rev-parse', 'HEAD') == commit, f'Wrong revision: {repo}'
        assert not git(repo, 'status', '--porcelain'), f'Checkout must be clean: {repo}'
    with tempfile.TemporaryDirectory(prefix='ege-source-', dir=ASSETS) as temporary:
        staged = Path(temporary) / 'ege_src'
        staged.mkdir()
        for name in ['src', 'include', 'cmake']:
            shutil.copytree(source / name, staged / name)
        for name in ['CMakeLists.txt', 'LICENSE']:
            shutil.copy2(source / name, staged / name)
        for name in ['src', 'include']:
            shutil.copytree(ccap / name, staged / '3rdparty/ccap' / name)
        for name in ['CMakeLists.txt', 'LICENSE']:
            shutil.copy2(ccap / name, staged / '3rdparty/ccap' / name)
        (staged / 'source.properties').write_text(
            f'ege.repository=https://github.com/x-ege/xege\nege.commit={EGE_COMMIT}\n'
            f'ccap.repository=https://github.com/wysaid/CameraCapture\nccap.commit={CCAP_COMMIT}\n')
        # Copy the upstream demo support files too; their includes must match this source pin.
        for destination in (ASSETS / 'ege_demos').glob('*.cpp'):
            upstream = source / 'demo' / destination.name
            if upstream.is_file():
                shutil.copy2(upstream, destination)
        for name in ['camera_demo_screen.h', 'macos-camera-info.plist']:
            shutil.copy2(source / 'demo' / name, ASSETS / 'ege_demos' / name)
        shutil.rmtree(ASSETS / 'ege_src')
        shutil.move(staged, ASSETS / 'ege_src')
    MANIFEST.write_text(manifest())
    print(f'Bundled XEGE {EGE_COMMIT} and ccap {CCAP_COMMIT}')


if __name__ == '__main__':
    main()
