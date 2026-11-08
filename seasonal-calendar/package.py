#!/usr/bin/env python3
"""Create and verify a deterministic installation overlay."""
import hashlib
import json
from pathlib import Path
import zipfile
from build import ROOT, REPO, NAME, VERSION

FAUNA = 'empirecraft-fauna-1.1.0.jar'


def main():
    jar = REPO/'mods'/NAME
    assert jar.read_bytes() == (REPO/'server/mods'/NAME).read_bytes()
    files = {
        'README.md': ROOT/'README.md',
        'audit/coverage.json': ROOT/'audit/coverage.json',
        'audit/calendar-years-native.json': ROOT/'audit/calendar-years-native.json',
        'reference/admanticheat_id_list.json': REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json',
        'tools/update_calendar_adm.py': ROOT/'update_adm.py',
        'tools/adm_format.py': REPO/'medieval-create/update_adm.py',
    }
    for side, directory in [('client', 'mods'), ('server', 'server/mods')]:
        for suffix in ['', '.sha256']: files[f'{side}/mods/{NAME}{suffix}'] = REPO/directory/(NAME+suffix)
        for name in ['seasons.toml', 'fertility.toml']:
            config = REPO/('config' if side=='client' else 'server/config')/'sereneseasons'/name
            files[f'{side}/config/sereneseasons/{name}'] = config
    for suffix in ['', '.sha256']:
        name = 'Empirecraft-Translations-uk-ru-1.1.0.zip' + suffix
        files['client/resourcepacks/'+name] = REPO/'translations/dist'/name
        files['server/mods/'+FAUNA+suffix] = REPO/'server/mods'/(FAUNA+suffix)
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
        'checks': ['native Forge/Maven dependency ranges and installed World Rules 1.0.2 compatibility', 'UTC clock, downtime, restart, TPS independence, pauses and command rebasing', 'native NBT persistence and corruption rejection', 'calendar math and boundaries', 'migration', 'native Forge sleep API', 'Brigadier aliases',
                   'fauna profiles', 'actual JAR Mixin transformations and ASM verification in SERVER/CLIENT environments',
                   'client/server config and JAR equality', 'native fertility preservation', 'ADM integrity', 'package contents'],
        'client_clock_fixture': 'Original Minecraft 1.20.1 ClientLevel, remapped with hash-pinned Mojang/MCP mappings',
        'year_schema': 2,
        'admin_commands': ['calendar set day 1-30', 'calendar set month 1-12', 'calendar set year 1+',
                           'calendar set time HH:mm|ticks', 'calendar set date DAY MONTH YEAR [HH:mm|ticks]'],
        'limitation': 'No graphical client or production world launch. Native tests use disposable Mohist worlds.',
    }
    native = ROOT/'build/native/result.json'
    if not native.exists():
        native = ROOT/'audit/calendar-years-native.json'
    if native.exists():
        result = json.loads(native.read_text())
        assert result['calendar_sha256'] == report['jar_sha256'], 'Native report belongs to a different calendar JAR'
        report['native_server'] = result
    report['checks'].extend(['persistent years and December/January rollover', 'actual administrator command tree and permissions',
                            'all 1440 displayed minutes and client year packet codec', 'native client command-tree serialization'])
    path = REPO/'validation/seasonal-calendar.json'
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    compatibility_patch(jar)
    print('PASS package:', len(files), 'verified files; SHA-256', digest)


def compatibility_patch(jar):
    files={
        'README.md': ROOT/'UPGRADE-1.2.0.md',
        'reference/admanticheat_id_list.json': REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json',
        'tools/update_calendar_adm.py': ROOT/'update_adm.py',
        'tools/adm_format.py': REPO/'medieval-create/update_adm.py',
    }
    for side in ['client','server']:
        for suffix in ['', '.sha256']:
            files[f'{side}/mods/{NAME}{suffix}']=jar.with_name(NAME+suffix)
    for suffix in ['', '.sha256']:
        name = 'Empirecraft-Translations-uk-ru-1.1.0.zip' + suffix
        files['client/resourcepacks/'+name] = REPO/'translations/dist'/name
        files['server/mods/'+FAUNA+suffix] = REPO/'server/mods'/(FAUNA+suffix)
    contents={name:path.read_bytes() for name,path in files.items()}
    contents['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(contents.items())).encode()
    target=ROOT/f'dist/Empirecraft-Calendar-Compatibility-{VERSION}.zip'
    with zipfile.ZipFile(target,'w') as archive:
        for name,data in sorted(contents.items()):
            info=zipfile.ZipInfo(name,(2026,10,25,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,data)
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist())==set(contents)
        for name,data in contents.items():assert archive.read(name)==data
        assert not any(name.startswith(('server/config/','client/config/','world/','plugins/')) for name in archive.namelist())
    target.with_suffix('.zip.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
    print('PASS minimal compatibility patch:',len(contents),'verified entries; no live configs or world data')


if __name__ == '__main__':
    main()
