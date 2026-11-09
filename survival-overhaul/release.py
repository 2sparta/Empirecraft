#!/usr/bin/env python3
"""Install and package the exact native-verified survival adaptation and full matching update."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(build.sha(path)+'  '+path.name+'\n')
def install():
    candidate=ROOT/'dist'/build.NAME
    for side in ['mods','server/mods']:
        assert not [p for p in (REPO/side).glob('legendarysurvivaloverhaul-*.jar') if p.name!=build.NAME],'Remove the original LSO version before installing the fork'
        shutil.copyfile(candidate,REPO/side/build.NAME);checksum(REPO/side/build.NAME)
        target=REPO/('config' if side=='mods' else 'server/config')/'legendarysurvivaloverhaul/legendarysurvivaloverhaul-common.toml'
        if not target.exists():target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'defaults/legendarysurvivaloverhaul-common.toml',target)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());rows={r['id']:r for r in lock['mods']}
    if 'legendarysurvivaloverhaul' not in rows:
        row=dict(id='legendarysurvivaloverhaul',file=build.NAME,version='1.20.1-2.4.7-empirecraft.1',sides=['client','server'],added=True,updated=True,source='local:survival-overhaul',pin_reason='Vanilla health; health/limb capabilities, medical content and potion registrations removed. Physical inner armor linings, nonduplicating canteen boiling, embedded UK/RU and actual pack consumable thirst profiles. Exact sources and native checks in survival-overhaul/.');lock['mods'].append(row)
    write(path,lock)
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
    if not any(row['id']=='legendarysurvivaloverhaul' for row in manifest['releases']):manifest['releases'].append(dict(next(row for row in lock['mods'] if row['id']=='legendarysurvivaloverhaul'),artifact='survival-overhaul/dist/'+build.NAME))
    write(manifest_path,manifest)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    for folder,output in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        values=[]
        for path in sorted((REPO/folder).glob('*.jar')):
            raw=path.read_bytes();values.append(dict(name=path.name,path=str(path.relative_to(REPO)),size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/output,values)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={p.name:build.sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text())
    with zipfile.ZipFile(candidate) as z:retained[build.NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    write(path,retained)
def verify():
    candidate=ROOT/'dist'/build.NAME;static=json.loads((ROOT/'build/static-checks.json').read_text());assert static['jar_sha256']==build.sha(candidate)
    reports={};tested=ROOT/'audit/native-tested/runtime.jar';resource_delta=None
    if tested.exists() and build.sha(tested)!=build.sha(candidate):
        with zipfile.ZipFile(tested) as a,zipfile.ZipFile(candidate) as b:
            assert set(a.namelist())==set(b.namelist())
            changed=sorted(name for name in a.namelist() if a.read(name)!=b.read(name))
            assert changed and all(name.startswith('assets/legendarysurvivaloverhaul/lang/') and name.endswith('.json') for name in changed),changed
        resource_delta=dict(native_tested_sha256=build.sha(tested),final_sha256=build.sha(candidate),changed_entries=changed,all_code_gameplay_data_and_other_entries_identical=True)
    for profile in ['server','client']:
        data=json.loads((ROOT/'build'/(profile+'.json')).read_text());assert data['assertions']>150,profile
        assert data['addon_sha256']==build.sha(candidate) or resource_delta is not None and data['addon_sha256']==resource_delta['native_tested_sha256'],profile
        assert data['fixture_source_sha256']==build.sha(ROOT/'tests/SurvivalProbe.java'),profile
        reports[profile]={k:v for k,v in data.items() if k not in ['consumables','tag_overlay_sha256']}
    for side in ['mods','server/mods']:assert (REPO/side/build.NAME).read_bytes()==candidate.read_bytes()
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py'],check=True)
    subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    report=dict(status='passed',artifact=build.NAME,sha256=build.sha(candidate),static=static,native=reports,resource_only_delta_after_native=resource_delta,graphical_client_tested=False,production_world_tested=False)
    write(REPO/'validation/survival-overhaul-1.json',report);return report
def package(report):
    base=REPO/'layered-armor/dist/Empirecraft-Layered-Armor-Hotfix-1.zip';files={}
    with zipfile.ZipFile(base) as z:
        for name in z.namelist():
            if name.startswith(('client/','server/','reference-configs/')) or name in ['REMOVE-OLD.txt','TOWNYRECRUITS-2.0.md','FLEET-FIXES.md']:files[name]=z.read(name)
    for side in ['client','server']:
        files[side+'/mods/'+build.NAME]=(ROOT/'dist'/build.NAME).read_bytes();files[side+'/mods/'+build.NAME+'.sha256']=(ROOT/'dist'/(build.NAME+'.sha256')).read_bytes()
        files['reference-configs/'+side+'/legendarysurvivaloverhaul/legendarysurvivaloverhaul-common.toml']=(ROOT/'defaults/legendarysurvivaloverhaul-common.toml').read_bytes()
    name='server/config/admanticheat/idfile/admanticheat_id_list.json';files[name]=(REPO/name).read_bytes()
    files['INSTALL-SURVIVAL-UA.md']=(ROOT/'README.md').read_bytes();files['INSTALL-LAYERED-ARMOR-UA.md']=(REPO/'layered-armor/README.md').read_bytes();files['verification.json']=(REPO/'validation/survival-overhaul-1.json').read_bytes();files['reference/modpack-lock.json']=(REPO/'modpack-lock.json').read_bytes()
    files['REMOVE-OLD.txt']=files.get('REMOVE-OLD.txt',b'')+b'Remove original legendarysurvivaloverhaul-1.20.1-2.4.7.jar and any other LSO JAR/checksum from BOTH mods folders. Install only the included Empirecraft version. Copy reference configuration only on first installation; existing custom configuration may remain (health is locked off in code).\n'
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(files.items())).encode()
    target=ROOT/'dist/Empirecraft-Survival-Update-1.zip';build.archive(target,files);checksum(target)
    sources={}
    for path in ROOT.rglob('*'):
        if path.is_file() and not {'build','dist','__pycache__'}.intersection(path.relative_to(ROOT).parts):sources[str(path.relative_to(REPO))]=path.read_bytes()
    sources['validation/survival-overhaul-1.json']=(REPO/'validation/survival-overhaul-1.json').read_bytes()
    older=REPO/'layered-armor/dist/Empirecraft-Layered-Armor-Hotfix-1-sources.zip';sources[older.name]=older.read_bytes()
    sourcezip=ROOT/'dist/Empirecraft-Survival-Update-1-sources.zip';build.archive(sourcezip,sources);checksum(sourcezip)
    with zipfile.ZipFile(target) as z:assert z.testzip() is None
    print('SURVIVAL_RELEASE_PASS',build.sha(target))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');args=parser.parse_args()
    if args.install:install()
    package(verify())
if __name__=='__main__':main()
