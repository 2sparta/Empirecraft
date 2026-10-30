#!/usr/bin/env python3
"""Package the already-tested political upgrade without a world, database or user configuration."""
from pathlib import Path
import hashlib
import zipfile
from release import PLUGIN, PLUGIN_VERSION, bridge_name
BRIDGE = bridge_name()

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent

def main():
    jar = REPO / 'server/plugins' / PLUGIN
    with zipfile.ZipFile(jar) as z:
        descriptor = z.read('plugin.yml').decode()
        assert 'version: ' + PLUGIN_VERSION in descriptor
        assert 'org/h2/Driver.class' in z.namelist()
        assert 'ua/empirecraft/feudal/PoliticalNativeGuard.class' in z.namelist()
        assert 'ua/empirecraft/feudal/ProvisionController.class' in z.namelist()
        assert not any('/test/' in n or n.endswith('Tests.class') for n in z.namelist())
    files = {
        'plugins/' + jar.name: jar.read_bytes(),
        'plugins/' + jar.name + '.sha256': jar.with_suffix('.jar.sha256').read_bytes(),
        'REMOVE-OLD-JARS.txt': ('empirecraft-feudal-1.7.0.jar\nempirecraft-towny-journeymap-1.4.0.jar\n').encode(),
        'README.md': (ROOT / 'CURRENCY.md').read_bytes(),
        'reference/POLITICS-UPGRADE.md': (ROOT / 'POLITICS-UPGRADE.md').read_bytes(),
        'mods/'+BRIDGE: (REPO/'server/mods'/BRIDGE).read_bytes(),
        'mods/'+BRIDGE+'.sha256': (REPO/'server/mods'/(BRIDGE+'.sha256')).read_bytes(),
        'CURRENCY.md': (ROOT / 'CURRENCY.md').read_bytes(),
        'PROVISIONS.md': (ROOT / 'PROVISIONS.md').read_bytes(),
        'FOUNDING-FIX.md': (ROOT / 'FOUNDING-FIX.md').read_bytes(),
        'TRADE-TOWNY-UPGRADE.md': (ROOT / 'TRADE-TOWNY-UPGRADE.md').read_bytes(),
        'POLITICS.md': (ROOT / 'POLITICS.md').read_bytes(),
        'STATE-DEBUG.md': (ROOT / 'STATE-DEBUG.md').read_bytes(),
        'VALIDATION.md': (REPO / 'validation/politics-1.4.0.md').read_bytes(),
        'reference-configs/config.yml': (ROOT / 'src/main/resources/config.yml').read_bytes(),
    }
    destination = ROOT / f'dist/EmpireCraft-Feudal-{PLUGIN_VERSION}-server.zip'
    destination.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(destination, 'w') as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 24, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, data)
    checksum = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix('.zip.sha256').write_text(checksum + '  ' + destination.name + '\n')
    print(destination)
    print('SHA-256:', checksum)

if __name__ == '__main__':
    main()




