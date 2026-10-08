#!/usr/bin/env python3
"""Export tracked blobs from pinned Git trees, or validate bundled assets offline."""
import argparse
import hashlib
import shutil
import subprocess
import tempfile
from pathlib import Path
from source_assets import validate_asset, validate_path

EGE_COMMIT = '09387a806e3d8cafde84bf0bd91b775d681b27ac'
CCAP_COMMIT = 'd1876005be7e7cc0c370fadd05dbac6c658c4a17'
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'assets'
MANIFEST = ASSETS / 'resource-manifest.sha256'


def manifest():
    entries = sorted(ASSETS.rglob('*'))
    for p in entries:
        if p.is_symlink():
            raise ValueError(f'Unexpected asset symlink: {p}')
    files = [p for p in entries if p.is_file() and p != MANIFEST]
    for p in files:
        validate_asset(p.relative_to(ASSETS).as_posix(), p.read_bytes())
    return ''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' +
                   p.relative_to(ASSETS).as_posix() + '\n' for p in files)


def git(path, *args):
    return subprocess.check_output(['git', '--no-replace-objects', '-C', str(path), *args], text=True).strip()


def tracked_blobs(repo, commit, paths):
    """Read tree modes/paths and blob bytes from Git, never working-directory files."""
    listing = subprocess.check_output(['git', '--no-replace-objects', '-C', str(repo), 'ls-tree', '-r', '-z', commit, '--', *paths])
    records = []
    for entry in listing.split(b'\0'):
        if not entry:
            continue
        metadata, raw_path = entry.split(b'\t', 1)
        mode, kind, oid = metadata.split()
        path = raw_path.decode('utf-8')
        validate_path(path)
        if kind != b'blob' or mode not in {b'100644', b'100755'}:
            raise ValueError(f'Non-regular tracked asset (symlink/submodule): {path}')
        records.append((path, mode, oid))
    batch = subprocess.check_output(['git', '--no-replace-objects', '-C', str(repo), 'cat-file', '--batch'],
                                    input=b''.join(oid + b'\n' for _, _, oid in records))
    offset = 0
    result = {}
    for path, mode, oid in records:
        end = batch.index(b'\n', offset)
        actual_oid, kind, size = batch[offset:end].split()
        size = int(size)
        offset = end + 1
        data = batch[offset:offset + size]
        if actual_oid != oid or kind != b'blob' or len(data) != size or batch[offset + size:offset + size + 1] != b'\n':
            raise ValueError(f'Invalid Git blob response: {path}')
        offset += size + 1
        validate_asset(path, data)
        result[path] = (data, int(mode, 8) & 0o777)
    if offset != len(batch):
        raise ValueError('Unexpected trailing Git blob data')
    return result


def write_blobs(destination, blobs):
    for name, (data, mode) in blobs.items():
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        target.chmod(mode)


def import_checkout(checkout):
    source = checkout.resolve()
    ccap = source / '3rdparty/ccap'
    for repo, commit in [(source, EGE_COMMIT), (ccap, CCAP_COMMIT)]:
        assert git(repo, 'rev-parse', 'HEAD') == commit, f'Wrong revision: {repo}'
        assert not git(repo, 'status', '--porcelain', '--untracked-files=no', '--ignore-submodules=untracked'), f'Tracked checkout files must be clean: {repo}'
    sources = tracked_blobs(source, EGE_COMMIT, ['src', 'include', 'cmake', 'CMakeLists.txt', 'LICENSE'])
    camera = tracked_blobs(ccap, CCAP_COMMIT, ['src', 'include', 'CMakeLists.txt', 'LICENSE'])
    demo_names = [p.name for p in (ASSETS / 'ege_demos').glob('*.cpp')]
    helpers = ['camera_demo_screen.h', 'macos-camera-info.plist']
    demos = tracked_blobs(source, EGE_COMMIT, ['demo/' + name for name in demo_names + helpers])
    for name in helpers:
        if 'demo/' + name not in demos:
            raise ValueError(f'Missing tracked demo helper: {name}')
    for blobs in [sources, camera]:
        for name in ['CMakeLists.txt', 'LICENSE']:
            if name not in blobs:
                raise ValueError(f'Missing tracked source file: {name}')
    with tempfile.TemporaryDirectory(prefix='ege-source-', dir=ASSETS) as temporary:
        staged = Path(temporary) / 'ege_src'
        write_blobs(staged, sources)
        write_blobs(staged / '3rdparty/ccap', camera)
        (staged / 'source.properties').write_text(
            f'ege.repository=https://github.com/x-ege/xege\nege.commit={EGE_COMMIT}\n'
            f'ccap.repository=https://github.com/wysaid/CameraCapture\nccap.commit={CCAP_COMMIT}\n')
        staged_demos = Path(temporary) / 'demos'
        write_blobs(staged_demos, {path.removeprefix('demo/'): value for path, value in demos.items()})
        for path in staged_demos.iterdir():
            shutil.copy2(path, ASSETS / 'ege_demos' / path.name)
        shutil.rmtree(ASSETS / 'ege_src')
        shutil.move(staged, ASSETS / 'ege_src')
    MANIFEST.write_text(manifest())
    print(f'Bundled XEGE {EGE_COMMIT} and ccap {CCAP_COMMIT} from tracked Git blobs')


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
        parser.error('Provide the pinned XEGE checkout, or use --check')
    import_checkout(args.checkout)


if __name__ == '__main__':
    main()
