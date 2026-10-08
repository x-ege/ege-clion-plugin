"""Shared source-only asset and archive-entry validation."""
import re
from pathlib import PurePosixPath

BINARY_SUFFIXES = {'.a', '.lib', '.dll', '.exe', '.o', '.obj', '.lo', '.so', '.dylib',
                   '.bc', '.pch', '.gch', '.pdb', '.res', '.exp', '.ilk', '.idb'}
BINARY_MAGIC = (b'MZ', b'!<arch>\n', b'\x7fELF', b'BC\xc0\xde', b'\xde\xc0\x17\x0b',
                b'\xfe\xed\xfa\xce', b'\xce\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xcf\xfa\xed\xfe',
                b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca', b'\xca\xfe\xba\xbf', b'\xbf\xba\xfe\xca',
                b'\x00\x00\xff\xff')  # COFF bigobj header


def validate_path(name):
    if (not name or name.startswith('/') or any(c in name for c in '\\:\x00\r\n') or
            any(part in {'', '.', '..'} for part in name.split('/'))):
        raise ValueError(f'Unsafe asset path: {name!r}')


def validate_asset(name, data=None):
    validate_path(name)
    if PurePosixPath(name).suffix.lower() in BINARY_SUFFIXES or re.search(r'\.so(?:\.\d+)+$', name.lower()):
        raise ValueError(f'Unexpected compiled asset: {name}')
    if data is None:
        return
    # Standard COFF object headers have a machine ID and no optional header.
    coff = (len(data) >= 20 and int.from_bytes(data[:2], 'little') in {0x14c, 0x8664, 0xaa64, 0x1c4}
            and 0 < int.from_bytes(data[2:4], 'little') < 4096 and data[16:18] == b'\x00\x00')
    if data.startswith(BINARY_MAGIC) or coff:
        raise ValueError(f'Unexpected compiled asset signature: {name}')
