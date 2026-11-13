#!/usr/bin/env python3
"""Bundle the validated runtime fixes together with the already released handbook repair."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
from runtime_installation import plan,apply,encode
ROOT=build.ROOT;REPO=build.REPO;NAME='Empirecraft-Runtime-Repair-1'
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encode(data))
def stage():
    files={}
    with zipfile.ZipFile(REPO/'handbook/dist/Empirecraft-Handbook-Writing-Fix-1-install.zip') as z:
        book=json.loads(z.read('release.json'))
        for side,rows in book['files'].items():
            for relative,digest in rows.items():
                raw=z.read(side+'/'+relative);assert hashlib.sha256(raw).hexdigest()==digest;files[side+'/'+relative]=raw
        files['HANDBOOK-INSTALL-UA.md']=z.read('INSTALL-UA.md').decode().replace('update_installation.py','runtime_installation.py').replace('.empirecraft-backups/handbook-1.0.3','.empirecraft-backups/runtime-1').replace('замінює тільки довідник і рецепти письмових матеріалів','встановлює виправлення з цього спільного пакета').encode()
    candidate=ROOT/'dist'/build.NAME;plugin=REPO/'chat-channels/dist/empirecraft-chat-1.1.1.jar'
    modules=[dict(id=book['id'],version=book['version'],file=book['file'],sha256=book['sha256'],sides=['client','server']),dict(id='empirecraft_pack_fixes',version='1.0.2',file=candidate.name,sha256=build.sha(candidate),sides=['client','server'])]
    for side in ['client','server']:
        files[side+'/mods/'+candidate.name]=candidate.read_bytes();files[side+'/mods/'+candidate.name+'.sha256']=candidate.with_suffix('.jar.sha256').read_bytes()
    files['server/plugins/'+plugin.name]=plugin.read_bytes();files['server/plugins/'+plugin.name+'.sha256']=plugin.with_suffix('.jar.sha256').read_bytes()
    release={'mods':modules,'plugins':[dict(name='EmpirecraftChat',version='1.1.1',file=plugin.name,sha256=build.sha(plugin))],'server_properties':{'simulation-distance':'8'},
        'files':{side:{n.removeprefix(side+'/'):hashlib.sha256(raw).hexdigest() for n,raw in files.items() if n.startswith(side+'/')} for side in ['client','server']}}
    bundle=ROOT/'build/runtime-bundle'
    for name,raw in files.items():p=bundle/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
    write(bundle/'release.json',release);write(ROOT/'runtime-release.json',release)
    return release,files,bundle
def install(release,bundle):
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text());old=[]
    prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
    for side,root,operation in prepared:
        old.extend((side,root,p,p.relative_to(REPO).as_posix()) for p in operation[2] if p.suffix=='.jar');apply(operation)
    for side,root,path,relative in old:
        source=root/'.empirecraft-backups/runtime-1'/path.relative_to(root);target=REPO/'disabled-mods/runtime-fixes-2026-11-13'/side/path.relative_to(root)
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);target.with_suffix('.jar.sha256').write_text(build.sha(target)+'  '+target.name+'\n')
        if relative not in manifest['retired']:manifest['retired'].append(relative)
        manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
    for side,root,_ in prepared:
        source=root/'.empirecraft-backups/runtime-1'
        if source.exists():
            shutil.copytree(source,ROOT/'build/runtime-install-backup'/side,dirs_exist_ok=True);shutil.rmtree(source)
            if not any(source.parent.iterdir()):source.parent.rmdir()
    modules={r['id']:r for r in release['mods']}
    for entry in manifest['releases']:
        if entry['id'] in modules:entry['artifact']='mods/'+modules[entry['id']]['file']
    write(manifest_path,manifest)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());next(r for r in lock['mods'] if r['id']=='empirecraft_pack_fixes').update(updated=True,pin_reason='1.0.2: chunk palette serialization, safe Ponder/JEI and Bukkit mount getters. Exact native and bytecode checks: pack-fixes/audit/runtime-release.json.');write(path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        rows=[]
        for p in sorted((REPO/folder).glob('*.jar')):
            raw=p.read_bytes();rows.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/inventory,rows)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={p.name:build.sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(path.read_text()).items() if not k.startswith('empirecraft-pack-fixes-')}
    jar=ROOT/'dist'/build.NAME
    with zipfile.ZipFile(jar) as z:retained[jar.name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    write(path,retained)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--install',action='store_true');parser.add_argument('--stage-only',action='store_true');a=parser.parse_args()
    release,files,bundle=stage()
    if a.stage_only:print('RUNTIME_REPAIR_STAGED');return
    evidence={key:json.loads((ROOT/'audit'/path).read_text()) for key,path in [('build','runtime-build.json'),('tests','runtime-tests.json'),('native','runtime-native.json'),('installer','runtime-installer.json')]}
    digest=build.sha(ROOT/'dist'/build.NAME)
    for key in ['build','tests','native']:assert evidence[key]['sha256']==digest
    assert evidence['tests']['status']==evidence['native']['status']==evidence['installer']['status']=='passed'
    assert evidence['installer']['mods']=={r['id']:r['sha256'] for r in release['mods']}
    assert evidence['installer']['plugins']=={r['name']:r['sha256'] for r in release['plugins']}
    assert evidence['installer']['updater_sha256']==build.sha(ROOT/'runtime_installation.py')
    chat=json.loads((REPO/'chat-channels/dist/catalog-fix-tests.json').read_text());assert chat['status']=='passed' and chat['sha256']==release['plugins'][0]['sha256']
    scan=json.loads((ROOT/'audit/ponder-pack-scan.json').read_text());assert scan['status']=='passed' and scan['all_found_schematics_audited']
    report={'status':'passed','release':release,'evidence':evidence,'chat':chat,'ponder_pack_scan':scan,'server_profile':json.loads((ROOT/'audit/server-profile-summary.json').read_text()),'graphical_client_tested':False,'production_world_opened':False}
    write(ROOT/'audit/runtime-release.json',report)
    if a.install:install(release,bundle)
    files.update({'release.json':encode(release),'INSTALL-UA.md':(ROOT/'RUNTIME-FIX-UA.md').read_bytes(),'runtime_installation.py':(ROOT/'runtime_installation.py').read_bytes(),'verification.json':encode(report)})
    chart='Empirecraft-Server-Profile-2026-11-13.png'
    files[chart]=(ROOT/'dist'/chart).read_bytes();files['PERFORMANCE-UA.md']=(ROOT/'PERFORMANCE-UA.md').read_text().replace('(dist/'+chart+')','('+chart+')').encode()
    source_files={}
    for path in ROOT.rglob('*'):
        if path.is_file() and not any(c in ['build','dist','audit','inputs','__pycache__'] for c in path.relative_to(ROOT).parts):source_files['pack-fixes/'+path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for path in (REPO/'chat-channels/src/server/java').rglob('*.java'):source_files[path.relative_to(REPO).as_posix()]=path.read_bytes()
    for relative in ['chat-channels/server_catalog_fix.py','chat-channels/tests/CosmeticFallbackTest.java','chat-channels/build.py','chat-channels/assets.py','chat-channels/LICENSE-ChatPlus.txt']:source_files[relative]=(REPO/relative).read_bytes()
    source=ROOT/'dist'/(NAME+'-sources.zip');build.archive(source,source_files);files[source.name]=source.read_bytes()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist'/(NAME+'-install.zip');build.archive(target,files);target.with_suffix('.zip.sha256').write_text(build.sha(target)+'  '+target.name+'\n')
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            checksum,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==checksum
    if a.install:subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('RUNTIME_REPAIR_RELEASE_PASS',build.sha(target))
if __name__=='__main__':main()
