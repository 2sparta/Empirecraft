#!/usr/bin/env python3
"""Package only paired runtime data, configuration, translations and instructions."""
import argparse
import hashlib
import io
import zipfile
from build import ROOT, REPO, generated

NAME = 'Empirecraft-Block-Mass-1.0.0.zip'


def archive():
    entries = {str(path): data for path, data in generated().items()
               if str(path).startswith(('config/', 'server/', 'kubejs/'))}
    entries['README-UA.md'] = (ROOT/'README.md').read_bytes()
    for name in ('coverage', 'verification'):
        entries['audit/'+name+'.json'] = (ROOT/'audit'/(name+'.json')).read_bytes()
    entries['SHA256SUMS'] = ''.join(
        hashlib.sha256(data).hexdigest()+'  '+name+'\n'
        for name, data in sorted(entries.items())).encode()
    result = io.BytesIO()
    with zipfile.ZipFile(result, 'w') as out:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, data)
    with zipfile.ZipFile(io.BytesIO(result.getvalue())) as check:
        assert check.testzip() is None
        assert all(check.read(name) == data for name, data in entries.items())
        assert not any(name.endswith('.jar') for name in check.namelist())
    return result.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    target = ROOT/'dist'/NAME
    data = archive()
    checksum = (hashlib.sha256(data).hexdigest()+'  '+NAME+'\n').encode()
    if args.check:
        assert target.read_bytes() == data, 'Stale ZIP'
        assert target.with_suffix('.zip.sha256').read_bytes() == checksum, 'Stale ZIP checksum'
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        target.with_suffix('.zip.sha256').write_bytes(checksum)
    print(f'PASS {NAME}: {len(data)} bytes, SHA-256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
