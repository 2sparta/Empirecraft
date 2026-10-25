#!/usr/bin/env python3
"""Create and verify a deterministic installation overlay."""
import hashlib
import json
from pathlib import Path
import zipfile
from build import ROOT, REPO, NAME, VERSION


def main():
    jar = REPO/'mods'/NAME
    assert jar.read_bytes() == (REPO/'server/mods'/NAME).read_bytes()
    files = {
        'README.md': ROOT/'README.md',
        'audit/coverage.json': ROOT/'audit/coverage.json',
        'reference/admanticheat_id_list.json': REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json',
        'tools/update_calendar_adm.py': ROOT/'update_adm.py',
        'tools/adm_format.py': REPO/'medieval-create/update_adm.py',
    }
    for side, directory in [('client', 'mods'), ('server', 'server/mods')]:
        for suffix in ['', '.sha256']: files[f'{side}/mods/{NAME}{suffix}'] = REPO/directory/(NAME+suffix)
        for name in ['seasons.toml', 'fertility.toml']:
            config = REPO/('config' if side=='client' else 'server/config')/'sereneseasons'/name
            files[f'{side}/config/sereneseasons/{name}'] = config
    target = ROOT/f'dist/Empirecraft-Seasonal-Calendar-{VERSION}.zip'
    target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target, 'w') as archive:
        for name, path in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 24, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(files)
        for name, path in files.items(): assert archive.read(name) == path.read_bytes()
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest+'  '+target.name+'\n')
    coverage = json.loads((ROOT/'audit/coverage.json').read_text())
    report = {
        'version': VERSION, 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
        'package_sha256': digest, 'package_files': len(files),
        'day_ticks': 57_600, 'month_days': 30, 'month_ticks': 1_728_000,
        'audited_server_jars': coverage['jar_count'], 'seasonal_blocks': len(coverage['plants']),
        'added_block_assignments': coverage['new_block_assignments'],
        'added_item_assignments': coverage['new_item_assignments'],
        'renewal_plants': len(coverage['renewal_plants_checked']),
        'checks': ['UTC clock, downtime, restart, TPS independence, pauses and command rebasing', 'native NBT persistence and corruption rejection', 'calendar math and boundaries', 'migration', 'native Forge sleep API', 'Brigadier aliases',
                   'fauna profiles', 'actual JAR Mixin transformations and ASM verification in SERVER/CLIENT environments',
                   'client/server config and JAR equality', 'native fertility preservation', 'ADM integrity', 'package contents'],
        'client_clock_fixture': 'Original Minecraft 1.20.1 ClientLevel, remapped with hash-pinned Mojang/MCP mappings',
        'limitation': 'No full Mohist world or graphical client launch; verification covers bytecode transformations and isolated contracts.',
    }
    path = REPO/'validation/seasonal-calendar.json'
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print('PASS package:', len(files), 'verified files; SHA-256', digest)


if __name__ == '__main__':
    main()
