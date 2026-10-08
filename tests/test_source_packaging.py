import hashlib
import io
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / 'scripts'))
import package_ege_source as pack
from source_assets import validate_asset, validate_path


class SourcePackagingTest(unittest.TestCase):
    def git(self, repo, *args):
        # Disposable fixtures do not inherit the user's signing key or commit hooks.
        return subprocess.check_output(['git', '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null',
                                        '-C', str(repo), *args], stderr=subprocess.PIPE).decode().strip()

    def commit(self, repo):
        self.git(repo, 'add', '-A')
        self.git(repo, '-c', 'user.name=Packaging Fixture', '-c', 'user.email=fixture@example.invalid',
                 'commit', '-qm', 'fixture')
        return self.git(repo, 'rev-parse', 'HEAD')

    def fixture(self, root):
        source = root / 'checkout'
        camera = source / '3rdparty/ccap'
        for repo, entries in [
            (source, {'src/base.cpp': '// EGE tracked\n', 'include/ege.h': '// header\n',
                      'cmake/support.cmake': '# tracked\n', 'CMakeLists.txt': '# EGE CMake\n',
                      'LICENSE': 'EGE license\n', 'demo/example.cpp': '// tracked demo\n',
                      'demo/camera_demo_screen.h': '// tracked helper\n', 'demo/macos-camera-info.plist': '<plist/>\n'}),
            (camera, {'src/ccap_core.cpp': '// ccap tracked\n', 'include/ccap.h': '// camera header\n',
                      'CMakeLists.txt': '# camera CMake\n', 'LICENSE': 'ccap license\n'}),
        ]:
            repo.mkdir(parents=True)
            self.git(repo, 'init', '-q')
            for name, text in entries.items():
                p = repo / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            (repo / '.gitignore').write_text('*.o\nbuild/\n')
            self.commit(repo)
        assets = root / 'assets'
        (assets / 'ege_src').mkdir(parents=True)
        (assets / 'ege_src/old.cpp').write_text('// replaced fixture\n')
        (assets / 'ege_demos').mkdir()
        (assets / 'ege_demos/example.cpp').write_text('// old demo\n')
        return source, camera, assets

    def import_fixture(self, source, camera, assets):
        with patch.multiple(pack, ASSETS=assets, MANIFEST=assets / 'resource-manifest.sha256',
                            EGE_COMMIT=self.git(source, 'rev-parse', 'HEAD'),
                            CCAP_COMMIT=self.git(camera, 'rev-parse', 'HEAD')):
            pack.import_checkout(source)
            self.assertEqual((assets / 'resource-manifest.sha256').read_text(), pack.manifest())

    def test_ignored_objects_build_files_and_untracked_sources_never_enter_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            source, camera, assets = self.fixture(Path(temporary))
            for repo in [source, camera]:
                (repo / 'src/foo.o').write_bytes(b'\x7fELFignored object')
                (repo / 'src/build').mkdir()
                (repo / 'src/build/generated.cpp').write_text('// ignored generated source\n')
                (repo / 'src/untracked.cpp').write_text('// untracked source\n')
                self.assertEqual(self.git(repo, 'check-ignore', 'src/foo.o'), 'src/foo.o')
            # A worktree helper with an ignored filename must not replace a tracked export either.
            (source / 'demo/ignored.o').write_bytes(b'\x7fELF')
            self.import_fixture(source, camera, assets)
            first = {p.relative_to(assets).as_posix(): p.read_bytes() for p in assets.rglob('*') if p.is_file()}
            self.assertEqual((assets / 'ege_src/src/base.cpp').read_text(), '// EGE tracked\n')
            self.assertEqual((assets / 'ege_src/3rdparty/ccap/src/ccap_core.cpp').read_text(), '// ccap tracked\n')
            self.assertEqual((assets / 'ege_demos/example.cpp').read_text(), '// tracked demo\n')
            self.assertFalse(any('foo.o' in name or 'untracked' in name or '/build/' in name for name in first))
            for repo in [source, camera]:
                (repo / 'src/foo.o').write_bytes(b'MZdifferent ignored object')
                (repo / 'src/untracked.cpp').write_text('// changed untracked source\n')
            self.import_fixture(source, camera, assets)
            second = {p.relative_to(assets).as_posix(): p.read_bytes() for p in assets.rglob('*') if p.is_file()}
            self.assertEqual(first, second)

    def test_export_reads_fixed_blobs_despite_modified_worktree_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            source, _, _ = self.fixture(Path(temporary))
            commit = self.git(source, 'rev-parse', 'HEAD')
            (source / 'src/base.cpp').write_text('// later working-directory edit\n')
            self.assertEqual(pack.tracked_blobs(source, commit, ['src'])['src/base.cpp'][0], b'// EGE tracked\n')

    def test_tracked_unsafe_entries_are_rejected_before_bundle_changes(self):
        for filename, symlink, data in [('link.cpp', True, b''), ('drive:name.cpp', False, b'// unsafe name\n'),
                                        ('tracked.o', False, b'object'), ('disguised.cpp', False, b'\x7fELF')]:
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as temporary:
                source, camera, assets = self.fixture(Path(temporary))
                target = source / 'src' / filename
                if symlink:
                    target.symlink_to('../LICENSE')
                else:
                    target.write_bytes(data)
                    self.git(source, 'add', '-f', 'src/' + filename)
                self.commit(source)
                with self.assertRaises(ValueError):
                    self.import_fixture(source, camera, assets)
                self.assertEqual((assets / 'ege_src/old.cpp').read_text(), '// replaced fixture\n')

    def test_object_suffixes_and_native_signatures_cannot_be_disguised_as_source(self):
        for name in ['x.o', 'x.obj', 'x.lo', 'x.bc', 'lib.so.1']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                validate_asset(name, b'ordinary text')
        coff = b'\x64\x86\x01\x00' + b'\x00' * 16
        for data in [b'\x7fELF', b'\xcf\xfa\xed\xfe', b'BC\xc0\xde', coff, b'\x00\x00\xff\xff']:
            with self.subTest(data=data), self.assertRaises(ValueError):
                validate_asset('src/disguised.cpp', data)
        for name in ['../escape', '/absolute', 'a\\b', 'C:drive', 'src/./x', 'src//x', 'src/name\nline']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                validate_path(name)

    def test_asset_symlink_directories_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets = root / 'assets'
            assets.mkdir()
            (assets / 'linked-directory').symlink_to(root, target_is_directory=True)
            with patch.multiple(pack, ASSETS=assets, MANIFEST=assets / 'resource-manifest.sha256'):
                with self.assertRaisesRegex(ValueError, 'symlink'):
                    pack.manifest()

    def test_delivered_zip_checker_rejects_objects_signatures_and_symlinks(self):
        cases = [
            ('assets/ege_src/src/foo.o', b'object', False),
            ('assets/ege_src/src/disguised.cpp', b'\x7fELF', False),
            ('assets/ege_src/src/link.cpp', b'../LICENSE', True),
            ('assets/ege_src/../escape.cpp', b'unsafe', False),
        ]
        for name, data, symlink in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                entries = {'assets/' + p.relative_to(REPO / 'assets').as_posix(): p.read_bytes()
                           for p in (REPO / 'assets').rglob('*') if p.is_file()}
                entries[name] = data
                entries['assets/resource-manifest.sha256'] = ''.join(
                    hashlib.sha256(value).hexdigest() + '  ' + key.removeprefix('assets/') + '\n'
                    for key, value in sorted(entries.items()) if key != 'assets/resource-manifest.sha256').encode()
                jar_data = io.BytesIO()
                with zipfile.ZipFile(jar_data, 'w') as jar:
                    for key, value in entries.items():
                        info = zipfile.ZipInfo(key)
                        if symlink and key == name:
                            info.create_system = 3
                            info.external_attr = (stat.S_IFLNK | 0o777) << 16
                        jar.writestr(info, value)
                archive = Path(temporary) / 'plugin.zip'
                with zipfile.ZipFile(archive, 'w') as outer:
                    outer.writestr('plugin/lib/plugin.jar', jar_data.getvalue())
                checked = subprocess.run([sys.executable, str(REPO / 'scripts/check_plugin_zip.py'), str(archive)],
                                         capture_output=True, text=True)
                self.assertNotEqual(checked.returncode, 0)
                self.assertRegex(checked.stderr, 'compiled asset|archive symlink|Unsafe asset path')


if __name__ == '__main__':
    unittest.main()
