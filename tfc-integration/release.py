#!/usr/bin/env python3
"""Install verified artifacts into the checkout and package the exact same bytes."""
import argparse, hashlib, json, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAMES=['TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.1.jar','empirecraft-tfc-integration-1.0.0.jar']
COMPANIONS=['empirecraft-equine-genetics-1.0.0.jar','empirecraft-layered-armor-1.0.2.jar']
RETIRED=['empirecraft-layered-armor-1.0.1.jar','realistic_horse_genetics-1.20.1-13.5.jar']
ARCHIVE=ROOT/'dist/Empirecraft-TFC-Integration-1.0.0.zip'
METADATA=['modpack-lock.json','server/config/admanticheat/idfile/admanticheat_id_list.json',
          'config/overgeared-common.toml','server/config/overgeared-common.toml',
          'config/empirecraft_equine-common.toml','server/config/empirecraft_equine-common.toml',
          'server/kubejs/server_scripts/zzzz_empirecraft_smithing.js']

def sha(raw):return hashlib.sha256(raw).hexdigest()
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(sha(path.read_bytes())+'  '+path.name+'\n')

def verified():
    report=json.loads((ROOT/'audit/full-pack.json').read_text())
    assert len(report['phases'])==2 and all('TFC_NATIVE_PASS' in phase for phase in report['phases'])
    for name in NAMES:assert sha((ROOT/'build/libs'/name).read_bytes())==report['jars'][name],('Untested artifact',name)
    for name in COMPANIONS:
        assert name in report['actual_mods'],('Missing tested companion',name)
        assert (REPO/'mods'/name).read_bytes()==(REPO/'server/mods'/name).read_bytes()
    assert not report['production_world_opened']
    return report

def package_files():
    entries={}
    for folder in ['mods','server/mods']:
        for name in NAMES+COMPANIONS:
            for suffix in ['', '.sha256']:
                path=REPO/folder/(name+suffix);entries[path.relative_to(REPO).as_posix()]=path.read_bytes()
    for name in METADATA:entries[name]=(REPO/name).read_bytes()
    for path in ROOT.rglob('*'):
        if not path.is_file() or any(part in {'build','dist','__pycache__'} for part in path.relative_to(ROOT).parts):continue
        entries['source/tfc-integration/'+path.relative_to(ROOT).as_posix()]=path.read_bytes()
    entries['README.uk.md']=(ROOT/'README.uk.md').read_bytes()
    entries['minerals.csv']=(ROOT/'minerals.csv').read_bytes()
    entries['source/Empirecraft-Armor-DLO-Update-1-sources.zip']=(REPO/'horse-genetics/dist/Empirecraft-Armor-DLO-Update-1-sources.zip').read_bytes()
    entries['remove-before-install.txt']=('Remove these old files from client and server before copying this package:\n'
        +'\n'.join(folder+'/'+name+suffix for folder in ['mods','server/mods'] for name in RETIRED for suffix in ['', '.sha256'])+'\n').encode()
    return entries

def install():
    native=verified()
    for folder in ['mods','server/mods']:
        for name in RETIRED:assert not (REPO/folder/name).exists(),('Retired base mod is still installed',folder,name)
    for folder in ['mods','server/mods']:
        for name in NAMES:
            shutil.copyfile(ROOT/'build/libs'/name,REPO/folder/name);checksum(REPO/folder/name)
    lock=json.loads((REPO/'modpack-lock.json').read_text())
    for mid,name,version,reason in [
        ('tfc',NAMES[0],'3.2.30','Exact TFC release with reviewed native gameplay removals; requires empirecraft_tfc. Source, original pin and tests in tfc-integration/.'),
        ('empirecraft_tfc',NAMES[1],'1.0.0','TFC metals, agriculture, pottery, glass, animals, Create quern drive and persistent one-time terrain integration.')]:
        rows=[row for row in lock['mods'] if row['id']==mid]
        if not rows:lock['mods'].append({'id':mid});rows=[lock['mods'][-1]]
        rows[0].update(file=name,version=version,source='local:tfc-integration',updated=True,pin_reason=reason)
    write(REPO/'modpack-lock.json',lock)
    subprocess.run([sys.executable,str(REPO/'validation/reconcile_modpack.py'),'--apply'],check=True)
    lock=json.loads((REPO/'modpack-lock.json').read_text())
    # Refresh the established installed-JAR inventories without changing unrelated records.
    for path,folder in [(REPO/'seasonal-calendar/installed-mods.json','server/mods'),
                        (REPO/'forging-balance/installed-client-mods.json','mods')]:
        rows=[row for row in json.loads(path.read_text()) if row['name'] not in NAMES]
        for name in NAMES:
            raw=(REPO/folder/name).read_bytes()
            rows.append({'name':name,'path':folder+'/'+name,'size':len(raw),
                'sha':hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()})
        write(path,sorted(rows,key=lambda row:row['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text())
    coverage['jar_sha256'].update({name:native['jars'][name] for name in NAMES})
    coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text())
    for name in NAMES:
        with zipfile.ZipFile(REPO/'mods'/name) as z:retained[name]={n:sha(z.read(n)) for n in sorted(z.namelist()) if n.endswith('.class')}
    write(path,retained)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    manifest['releases']=[row for row in manifest['releases'] if row['id'] not in {'tfc','empirecraft_tfc'}]
    for mid in ['tfc','empirecraft_tfc']:
        row=next(row for row in lock['mods'] if row['id']==mid);manifest['releases'].append(dict(row,artifact='server/mods/'+row['file']))
    write(path,manifest)
    write(ROOT/'audit/release.json',{'version':'1.0.0','artifacts':{name:native['jars'][name] for name in NAMES},
        'companions':{name:sha((REPO/'mods'/name).read_bytes()) for name in COMPANIONS},
        'pack_main_sha':subprocess.check_output(['git','rev-parse','origin/main'],cwd=REPO,text=True).strip(),
        'native_report':'full-pack.json','production_world_opened':False,'graphical_client_tested':False})
    ARCHIVE.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(ARCHIVE,'w',zipfile.ZIP_DEFLATED) as z:
        for name,raw in sorted(package_files().items()):
            info=zipfile.ZipInfo(name,(2026,11,11,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    checksum(ARCHIVE)

def check():
    native=verified()
    for name in NAMES:
        for folder in ['mods','server/mods']:
            path=REPO/folder/name;assert sha(path.read_bytes())==native['jars'][name]
            assert path.with_suffix('.jar.sha256').read_text()==native['jars'][name]+'  '+name+'\n'
    with zipfile.ZipFile(ARCHIVE) as z:
        expected=package_files();assert set(z.namelist())==set(expected)
        for name,raw in expected.items():assert z.read(name)==raw,name
        assert not any('empirecraft-tfc-test.jar' in name for name in z.namelist())
    assert ARCHIVE.with_suffix('.zip.sha256').read_text()==sha(ARCHIVE.read_bytes())+'  '+ARCHIVE.name+'\n'
    subprocess.run([sys.executable,str(REPO/'validation/reconcile_modpack.py')],check=True)
    print('PASS: tested client/server bytes, checksums, complete installation archive and metadata')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);g=p.add_mutually_exclusive_group(required=True);g.add_argument('--install',action='store_true');g.add_argument('--check',action='store_true');a=p.parse_args()
    if a.install:install()
    check()
