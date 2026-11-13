#!/usr/bin/env python3
"""Verified combined update including the independent natural-materials migration."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import release as ecology

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
EXTRA=[REPO/'tfc-integration/build/libs/empirecraft-tfc-integration-1.0.2.jar',
       REPO/'tfc-muskets/dist/tfc_muskets-1.20.1-forge-1.0.3-empirecraft.1.jar',
       REPO/'sails/dist/vs_sails-1.20.1-0.3.2-empirecraft.19.1-forge.jar',
       REPO/'handbook/dist/empirecraft-handbook-1.0.1.jar']
PREFIXES=ecology.PREFIXES+('empirecraft-tfc-integration-','tfc_muskets-','vs_sails-','empirecraft-handbook-')
SOURCES=['fauna','seasonal-leaves','seasonal-calendar','layered-armor','world-rules','tfc-integration','tfc-muskets','sails','handbook']
NAME='Empirecraft-Gameplay-Update-3'
def write(path,data):ecology.write(path,data)
def verified():
    retirement=REPO/'server-cleanup/policy.json'
    if retirement.exists() and json.loads(retirement.read_text()).get('removed_server_mods'):
        raise SystemExit('Gameplay Update 3 is superseded by server-cleanup/. Use its verified release; old module JARs restore retired integrations.')
    previous=ecology.verification()
    ecology.UPDATES=[*ecology.UPDATES,*EXTRA];rows=ecology.mods()
    native=json.loads((REPO/'tfc-integration/audit/materials-update.json').read_text())
    assert len(native['phases'])==2 and all('PASS' in p for p in native['phases']) and native['current_update_markers']
    assert len(native['materials_markers'])==3
    assert not native['production_world_opened'] and len(native['actual_mods'])>100
    for row,p in zip(rows,ecology.UPDATES):assert native['jars'][p.name]==ecology.sha(p),'Tested different JAR: '+p.name
    sails=json.loads((REPO/'sails/passenger-puddles-validation.json').read_text());assert sails['sha256']==ecology.sha(EXTRA[2])
    assert any('FLOOD_MODEL_PASS' in p for p in sails['pure_tests'])
    book=json.loads((REPO/'handbook/audit/content.json').read_text());assert book['sha256']==ecology.sha(EXTRA[3]) and book['pages']>=900
    muskets=json.loads((REPO/'tfc-muskets/audit.json').read_text());assert muskets['sha256']==ecology.sha(EXTRA[1])
    with zipfile.ZipFile(EXTRA[1]) as z:
        assert {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}==muskets['java_classes']
    compatibility=json.loads((REPO/'tfc-integration/audit/compatibility-1.0.2.json').read_text())
    assert compatibility['current_sha256']==ecology.sha(EXTRA[0]) and compatibility['native_tfc_unchanged']
    return dict(status='passed',mods=rows,ecology=previous,current_update=native,sails=sails,handbook=book,muskets=muskets,tfc_compatibility=compatibility,
                graphical_client_tested=False,two_graphical_clients_tested=False,production_world_opened=False)
def install(report):
    # Existing installer preserves population settings and reconciles ADM/lock/inventories.
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json'
    manifest=json.loads(manifest_path.read_text())
    current_files={row['file'] for row in report['mods']}
    for folder in ['mods','server/mods']:
        for old in (REPO/folder).glob('*.jar'):
            if not old.name.startswith(PREFIXES) or old.name in current_files:continue
            relative=str(old.relative_to(REPO));archived='disabled-mods/replaced-2026-11-13/'+relative
            destination=REPO/archived;destination.parent.mkdir(parents=True,exist_ok=True)
            if destination.exists():assert ecology.sha(destination)==ecology.sha(old),archived
            else:shutil.copyfile(old,destination)
            destination.with_suffix('.jar.sha256').write_text(ecology.sha(old)+'  '+old.name+'\n')
            if relative not in manifest['retired']:manifest['retired'].append(relative)
            manifest.setdefault('retired_artifacts',{})[relative]=archived
    write(manifest_path,manifest)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text())
    for row,source in zip(report['mods'],SOURCES):
        found=next((r for r in lock['mods'] if r['id']==row['id']),None)
        if found is None:found=dict(row);lock['mods'].append(found)
        found.update(source='local:'+source,updated=True,pin_reason='Verified gameplay update 3; source and native checks in ecology-update/, handbook/, tfc-integration/ and sails/.')
    write(path,lock);ecology.PREFIXES=PREFIXES;ecology.install({'mods':report['mods']})
    # Restore the common description after the ecology helper's five specific entries.
    lock=json.loads(path.read_text())
    for row,source in zip(report['mods'],SOURCES):
        found=next(r for r in lock['mods'] if r['id']==row['id'])
        found.update(source='local:'+source,pin_reason='Verified gameplay update 3; see ecology-update/FULL-UPDATE.uk.md and full-verification.json.')
    write(path,lock)
    from update_settings import apply
    for folder in ['config','server/config']:apply(REPO/folder)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    subprocess.run([sys.executable,REPO/'seasonal-leaves/package.py','--adm-list',REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'],check=True)
def package(report):
    write(ROOT/'full-release.json',{'mods':report['mods'],'requires':'TFC integration from PR #125; the native TFC JAR stays unchanged'})
    write(ROOT/'full-verification.json',report)
    sources={}
    for name in ['ecology-update',*SOURCES]:
        for p in (REPO/name).rglob('*'):
            if p.is_file() and not any(c in ['build','dist','audit','__pycache__','.gradle'] for c in p.relative_to(REPO/name).parts):sources[str(p.relative_to(REPO))]=p.read_bytes()
    source=ROOT/'dist'/(NAME+'-sources.zip');ecology.archive(source,sources);ecology.checksum(source)
    install_text=(ROOT/'FULL-UPDATE.uk.md').read_text().replace('../tfc-integration/MATERIALS-UPDATE.uk.md','MATERIALS-UPDATE-UA.md')
    materials_text=(REPO/'tfc-integration/MATERIALS-UPDATE.uk.md').read_text().replace('../ecology-update/FULL-UPDATE.uk.md','INSTALL-UA.md')
    files={'INSTALL-UA.md':install_text.encode(),
           'MATERIALS-UPDATE-UA.md':materials_text.encode(),
           'natural-materials.csv':(REPO/'tfc-integration/natural-materials.csv').read_bytes(),
           'materials-coverage.json':(REPO/'tfc-integration/audit/materials-coverage.json').read_bytes(),
           'release.json':(ROOT/'full-release.json').read_bytes(),'verification.json':(ROOT/'full-verification.json').read_bytes(),
           'update_adm.py':(ROOT/'update_adm.py').read_bytes(),'update_settings.py':(ROOT/'update_settings.py').read_bytes(),
           'fauna-defaults.json':(REPO/'fauna/build/defaults/empirecraft-fauna.json').read_bytes(),
           'plants.csv':(ROOT/'plants.csv').read_bytes(),source.name:source.read_bytes()}
    for row,p in zip(report['mods'],ecology.UPDATES):
        for side in row['sides']:
            files[side+'/mods/'+p.name]=p.read_bytes();files[side+'/mods/'+p.name+'.sha256']=(ecology.sha(p)+'  '+p.name+'\n').encode()
    files['REMOVE-OLD.txt']=('Remove the following old JARs and their .sha256 files from both sides where installed:\n'+'\n'.join([*ecology.OLD,'empirecraft-tfc-integration-1.0.0.jar','empirecraft-tfc-integration-1.0.1.jar','empirecraft-handbook-1.0.0.jar','vs_sails-1.20.1-0.3.2-empirecraft.19-forge.jar'])+'\nDo not remove the native TerraFirmaCraft JAR or other mods from PR #125.\n').encode()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist'/(NAME+'-install.zip');ecology.archive(target,files);ecology.checksum(target)
def check(report):
    for row,p in zip(report['mods'],ecology.UPDATES):
        for side,folder in [('client','mods'),('server','server/mods')]:
            target=REPO/folder/p.name
            assert target.exists()==(side in row['sides']),str(target)
            if target.exists():assert ecology.sha(target)==row['sha256'],str(target)
    with zipfile.ZipFile(ROOT/'dist'/(NAME+'-install.zip')) as z:
        assert z.testzip() is None
        assert not any('test.jar' in n or 'smoke.jar' in n for n in z.namelist())
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            wanted,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==wanted,name
    subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('FULL_UPDATE_RELEASE_PASS mods='+str(len(report['mods'])))
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args()
    report=verified()
    if a.install:install(report)
    package(report);check(report)
if __name__=='__main__':main()
