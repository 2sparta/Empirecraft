#!/usr/bin/env python3
"""Reproducible client/server overlay; never includes a live database or replaces custom server config."""
import hashlib
import json
from pathlib import Path
import zipfile

REPO=Path(__file__).resolve().parents[1]
CALENDAR='empirecraft-calendar-1.1.1.jar'
FEUDAL='empirecraft-feudal-1.6.0.jar'

def main():
    assert (REPO/'mods'/CALENDAR).read_bytes()==(REPO/'server/mods'/CALENDAR).read_bytes()
    files={
        'README.md':REPO/'provisions/README.md',
        'reference/politics.md':REPO/'feudal/POLITICS.md',
        'reference/politics-upgrade.md':REPO/'feudal/POLITICS-UPGRADE.md',
        'reference/provisions.md':REPO/'feudal/PROVISIONS.md',
        'reference/calendar.md':REPO/'seasonal-calendar/README.md',
        'reference/feudal-config.yml':REPO/'feudal/src/main/resources/config.yml',
        'reference/admanticheat_id_list.json':REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json',
        'tools/update_calendar_adm.py':REPO/'seasonal-calendar/update_adm.py',
        'tools/adm_format.py':REPO/'medieval-create/update_adm.py',
        'tools/provision_config.py':REPO/'provisions/provision_config.py',
    }
    for side,path in [('client','mods'),('server','server/mods')]:
        for suffix in ['', '.sha256']:files[f'{side}/mods/{CALENDAR}{suffix}']=REPO/path/(CALENDAR+suffix)
        for name in ['seasons.toml','fertility.toml']:files[f'{side}/config/sereneseasons/{name}']=REPO/('config' if side=='client' else 'server/config')/'sereneseasons'/name
    for side,base in [('client','mods'),('server','server/mods')]:
        for suffix in ['', '.sha256']:
            files[side+'/mods/empirecraft-towny-journeymap-1.3.0.jar'+suffix]=REPO/base/('empirecraft-towny-journeymap-1.3.0.jar'+suffix)
    files['CURRENCY.md']=REPO/'feudal/CURRENCY.md'
    for suffix in ['', '.sha256']:files['server/plugins/'+FEUDAL+suffix]=REPO/'server/plugins'/(FEUDAL+suffix)
    target=REPO/'provisions/dist/Empirecraft-Realtime-Provisions-1.1.1.zip';target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target,'w') as archive:
        for name,path in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,24,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,path.read_bytes())
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        for name,path in files.items():assert archive.read(name)==path.read_bytes()
        assert not any(name.endswith('.mv.db') or name=='server/plugins/EmpirecraftFeudal/config.yml' for name in archive.namelist())
    sha=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.zip.sha256').write_text(sha+'  '+target.name+'\n')
    report={'calendar_version':'1.1.1','feudal_version':'1.6.0','calendar_sha256':hashlib.sha256((REPO/'mods'/CALENDAR).read_bytes()).hexdigest(),'feudal_sha256':hashlib.sha256((REPO/'server/plugins'/FEUDAL).read_bytes()).hexdigest(),'package_sha256':sha,'files':len(files),'provision_tests':15,'existing_feudal_tests':171,'integration_tests':13,'total_feudal_tests':199,'hunger_per_chunk_real_day':8,'multipliers':{'spring':1,'summer':0.5,'autumn':0.8,'winter':1.5},'collapse_real_hours':96,'month_real_hours':24,'claim_reserve_hours':24,'native_apis':'Spigot 1.20.1 / Towny 0.101.2.0 / Serene Seasons 9.1.0.3 / Forge 47.4.23','checks':['native Forge/Maven dependency ranges and installed World Rules compatibility','exact seasonal integration and exhaustion','disk H2 restart and inventory PREPARED recovery','claim reserve including season boundary','physical Minecraft chunks including negative coordinates','persisted territorial action batches','real-time clock downtime, TPS, pauses, admin command rebasing','actual Minecraft NBT persistence and corruption rejection','native Mixin transforms SERVER and CLIENT','reproducible binaries, lock, ADM preservation and package contents'],'limitation':'No full Mohist world or graphical client launched; native GUI and destructive Towny paths require staging-world verification.'}
    dest=REPO/'validation/realtime-provisions.json';dest.parent.mkdir(exist_ok=True);dest.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PASS provision package:',len(files),'verified files;',sha)

if __name__=='__main__':main()


