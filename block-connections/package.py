#!/usr/bin/env python3
"""Package the verified connection tags as a small data-only client/server hotfix."""
import hashlib
import json
import zipfile
from repair_tags import ROOT, REPO, TAGS, RELATIVE


def main():
    files = {'README.md': (ROOT/'README.md').read_bytes()}
    for side, folder in [('client', 'kubejs'), ('server', 'server/kubejs')]:
        for name in TAGS:
            path = RELATIVE/(name+'.json')
            raw = (REPO/folder/path).read_bytes()
            assert json.loads(raw)['replace'] is True
            files[side+'/kubejs/'+path.as_posix()] = raw
    for name in TAGS:
        path = RELATIVE/(name+'.json')
        assert files['client/kubejs/'+path.as_posix()] == files['server/kubejs/'+path.as_posix()]
    files['SHA256SUMS.txt'] = ''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name, raw in sorted(files.items())).encode()
    destination = ROOT/'dist/Empirecraft-Fence-Wall-Fix-1.0.0.zip'
    destination.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(destination, 'w') as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 5, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None and set(archive.namelist()) == set(files)
        for name, raw in files.items():
            assert archive.read(name) == raw, name
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix('.zip.sha256').write_text(digest+'  '+destination.name+'\n')
    print('PASS data-only fence/wall hotfix:', len(files), 'verified entries;', digest)


if __name__ == '__main__':
    main()
