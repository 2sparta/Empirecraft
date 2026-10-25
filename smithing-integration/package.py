#!/usr/bin/env python3
"""Build a deterministic server/client overlay; never bundle or alter native JARs."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
NAME = 'Empirecraft-Smithing-Integration-1.0.1'


def main():
    files = {
        'README.md': ROOT / 'README.md',
        'audit/compatibility.json': ROOT / 'audit/compatibility.json',
        'server/config/overgeared-common.toml': REPO / 'server/config/overgeared-common.toml',
        'server/kubejs/server_scripts/4_tables.js': REPO / 'server/kubejs/server_scripts/4_tables.js',
        'server/kubejs/server_scripts/1_steel.js': REPO / 'server/kubejs/server_scripts/1_steel.js',
        'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js': REPO / 'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js',
        'server/kubejs/startup_scripts/empirecraft_steel_trades.js': REPO / 'server/kubejs/startup_scripts/empirecraft_steel_trades.js',
        'client/config/overgeared-common.toml': REPO / 'config/overgeared-common.toml',
        'client/kubejs/client_scripts/empirecraft_metals.js': REPO / 'kubejs/client_scripts/empirecraft_metals.js',
    }
    for source in sorted((REPO / 'server/kubejs/data/overgeared/loot_modifiers').glob('steel_ingot_from_*.json')):
        files[source.relative_to(REPO).as_posix()] = source
    assert len(files) == 23, 'Expected 14 steel loot overrides and 9 other files'
    destination = ROOT / 'dist'
    destination.mkdir(exist_ok=True)
    archive = destination / (NAME + '.zip')
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for name, source in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 24, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, source.read_bytes())
    with zipfile.ZipFile(archive) as bundle:
        assert bundle.testzip() is None
        assert set(bundle.namelist()) == set(files)
        for name, source in files.items():
            assert bundle.read(name) == source.read_bytes()
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    archive.with_suffix('.zip.sha256').write_text(f'{digest}  {archive.name}\n')
    hotfix(files, destination)
    print(f'PASS {archive.name}: {len(files)} verified files; SHA-256 {digest}')


def hotfix(files, destination):
    selected = {
        'README.md': ROOT / 'HOTFIX.md',
        'audit/compatibility.json': ROOT / 'audit/compatibility.json',
    }
    for path in ['server/kubejs/server_scripts/1_steel.js', 'server/kubejs/server_scripts/4_tables.js', 'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js', 'server/kubejs/startup_scripts/empirecraft_steel_trades.js']:
        selected[path] = REPO / path
    target = destination / 'Empirecraft-Startup-Hotfix-1.0.1.zip'
    with zipfile.ZipFile(target, 'w') as bundle:
        for name, source in sorted(selected.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 25, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, source.read_bytes())
    with zipfile.ZipFile(target) as bundle:
        assert bundle.testzip() is None
        assert set(bundle.namelist()) == set(selected)
        for name, source in selected.items(): assert bundle.read(name) == source.read_bytes()
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest + '  ' + target.name + '\n')
    print('PASS startup hotfix: 4 server scripts, README and audit; SHA-256', digest)


if __name__ == '__main__':
    main()
