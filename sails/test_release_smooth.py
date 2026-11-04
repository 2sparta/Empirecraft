#!/usr/bin/env python3
"""Verify that release checks reject damaged artifacts and stale installation records."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from build import VERSION

REPO = Path(__file__).resolve().parent.parent
NAME = 'vs_sails-1.20.1-'+VERSION+'-forge.jar'
PACKAGE = 'sails/dist/Empirecraft-Sails-14-Sea-Motion.zip'
FILES = [
    'sails/build.py', 'sails/release_smooth.py', 'ship-cannons/release.py',
    'sails/smooth-release.json', 'sails/SEA-MOTION.md', 'server/config/vs_sails/ship-protection.json',
    'config/valkyrienskies/valkyrienskies-client.toml', 'defaultconfigs/valkyrienskies/vs-core-server.toml',
    'server/defaultconfigs/valkyrienskies/vs-core-server.toml', 'server/config/vs_sails/config.cfg',
    PACKAGE,
    PACKAGE+'.sha256',
    'sails/native-inputs/' + NAME,
    'native-content/inputs.json', 'native-content/audit/build.json',
    'modpack-lock.json', 'seasonal-calendar/installed-mods.json',
    'seasonal-calendar/audit/coverage.json', 'forging-balance/installed-client-mods.json',
    'server/config/admanticheat/idfile/admanticheat_id_list.json',
    *[side + '/' + NAME + suffix for side in ['mods', 'server/mods'] for suffix in ['', '.sha256']],
]


def main():
    with tempfile.TemporaryDirectory(prefix='sails-release-check-') as directory:
        root = Path(directory)
        for name in FILES:
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / name, target)

        def check():
            return subprocess.run([sys.executable, root / 'sails/release_smooth.py', '--check'],
                                  cwd=root, capture_output=True, text=True)

        def snapshot():
            return {str(p.relative_to(root)): (hashlib.sha256(p.read_bytes()).hexdigest(), p.stat().st_mtime_ns)
                    for p in root.rglob('*') if p.is_file() and '__pycache__' not in p.parts}

        before = snapshot()
        result = check()
        assert result.returncode == 0, result.stderr
        assert snapshot() == before, '--check modified the release'

        def reject(name, damage):
            path = root / name
            original = path.read_bytes()
            path.write_bytes(damage(original))
            try:
                result = check()
                assert result.returncode != 0, 'Accepted damaged ' + name
            finally:
                path.write_bytes(original)

        for name in ['seasonal-calendar/installed-mods.json', 'forging-balance/installed-client-mods.json']:
            def stale_inventory(raw):
                rows = json.loads(raw)
                row, = [row for row in rows if row['name'] == NAME]
                row['sha'] = '0' * 40
                return json.dumps(rows).encode()
            reject(name, stale_inventory)

        def stale_coverage(raw):
            data = json.loads(raw)
            data['jar_sha256'][NAME] = '0' * 64
            return json.dumps(data).encode()

        reject('seasonal-calendar/audit/coverage.json', stale_coverage)
        reject('sails/native-inputs/' + NAME, lambda raw: raw + b'damaged')
        reject(PACKAGE, lambda raw: raw + b'damaged')
        reject(PACKAGE+'.sha256', lambda raw: b'0' + raw[1:])
        print('SAILS_RELEASE_CHECK_PASS: read-only validation and 6 corruption/stale-record controls')


if __name__ == '__main__':
    main()
