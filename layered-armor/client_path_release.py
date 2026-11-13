#!/usr/bin/env python3
"""Release the client pack-finder fix and reconcile the confirmed client mod withdrawals."""
import argparse,hashlib,json,re,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
import build
from update_armor_adm import update,integrity

ROOT=build.ROOT;REPO=build.REPO;NAME='Empirecraft-Armor-Client-Fix-1'
WITHDRAWN=('emotecraft','pop_up_emotes')
FILES=('emotecraft-for-MC1.20.1-2.2.7-b.build.50-forge.jar','pop_up_emotes-1.5.6.jar')
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def archive(path,files):build.archive(path,files);path.with_suffix(path.suffix+'.sha256').write_text(build.sha(path)+'  '+path.name+'\n')
def verify_adm(candidate):
    data=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
    for mod in WITHDRAWN:
        if not any(r['id']==mod for r in data['modIds']):data['modIds'].append(dict(id=mod,version='fixture',sha256='0'*64,filePresent=True))
    next(r for r in data['modIds'] if r['id']=='empirecraft_armor')['version']='1.0.3'
    data['integrityHash']=integrity(data)
    unrelated=[r for r in data['modIds'] if r['id'] not in {*WITHDRAWN,'empirecraft_armor'}]
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'adm.json';write(path,data)
        assert update(path,candidate,WITHDRAWN)
        actual=json.loads(path.read_text());assert integrity(actual)==actual['integrityHash']
        assert [r for r in actual['modIds'] if r['id'] not in {*WITHDRAWN,'empirecraft_armor'}]==unrelated
        assert not any(r['id'] in WITHDRAWN or r['id']=='caupona' for r in actual['modIds'])
        assert path.with_name(path.name+'.before-armor-1.0.4').is_file()
        raw=path.read_bytes();assert not update(path,candidate,WITHDRAWN) and path.read_bytes()==raw
    return dict(status='passed',preserves_unrelated_entries=True,valid_integrity=True,idempotent=True,removed_client_ids=list(WITHDRAWN),updater_sha256=build.sha(ROOT/'update_armor_adm.py'))
def audit_withdrawals(java):
    import tomllib
    classes=[('emotecraft',FILES[0],'io.github.kosmx.emotes.forge.network.ServerNetwork'),('pop_up_emotes',FILES[1],'io.github.catomon.popupemotes.network.NetworkHandler')]
    result=[]
    for mid,file,cls in classes:
        path=REPO/'server/mods'/file
        process=subprocess.run([str(java.with_name('javap')),'-c','-p','-classpath',str(path),cls],capture_output=True,text=True,check=True)
        predicates=re.findall(r'private static boolean (lambda\$static\$\d+)\(java.lang.String\);\s+Code:\s+0: iconst_1\s+1: ireturn',process.stdout)
        all_predicates=re.findall(r'private static boolean (lambda\$static\$\d+)\(java.lang.String\);',process.stdout)
        assert predicates and set(predicates)==set(all_predicates),(mid,process.stdout[-2000:])
        dependents=[]
        for jar in (REPO/'mods').glob('*.jar'):
            with zipfile.ZipFile(jar) as z:
                if 'META-INF/mods.toml' not in z.namelist():continue
                descriptor=tomllib.loads(z.read('META-INF/mods.toml').decode())
                if any(d.get('mandatory') and d['modId']==mid for group in descriptor.get('dependencies',{}).values() for d in group):dependents.append(jar.name)
        assert not dependents and not (REPO/'mods'/file).exists()
        result.append(dict(id=mid,client_jar_absent=True,server_jar=file,server_sha256=build.sha(path),mandatory_client_dependents=dependents,
            network_class=cls,version_predicates=predicates,all_version_predicates_accept_absent_clients=True))
    write(ROOT/'audit/withdrawn-client-mods.json',dict(client_deletion_commit='a4b1f6ce',caupona='removed from both sides by PR 128',review=result))
    return result
def install(candidate):
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
    # The user deleted the client Caupona JAR before merging PR 128; Git's
    # delete/rename merge also removed its duplicate client archive. Both old
    # sides had identical bytes, so reference the existing disabled server copy.
    caupona='mods/caupona-1.20.1-0.4.14.jar'
    archived=manifest.get('retired_artifacts',{}).get(caupona)
    if archived and not (REPO/archived).is_file():
        shared='disabled-mods/caupona-removal-2026-11-13/server/mods/caupona-1.20.1-0.4.14.jar'
        assert build.sha(REPO/shared)=='4f12edee42b1238ccb0589031ccb70f5a71d13ed6691790a71422e87420f8f48'
        manifest['retired_artifacts'][caupona]=shared
    def retire(relative,source):
        dest=REPO/'disabled-mods/armor-client-path-2026-11-13'/relative;dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists():assert build.sha(dest)==build.sha(source)
        else:shutil.copyfile(source,dest)
        dest.with_suffix('.jar.sha256').write_text(build.sha(dest)+'  '+dest.name+'\n')
        if relative not in manifest['retired']:manifest['retired'].append(relative)
        manifest.setdefault('retired_artifacts',{})[relative]=dest.relative_to(REPO).as_posix()
    for folder in ['mods','server/mods']:
        for old in (REPO/folder).glob('empirecraft-layered-armor-*.jar'):
            if old.name==candidate.name:continue
            retire(old.relative_to(REPO).as_posix(),old);old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        target=REPO/folder/candidate.name;shutil.copyfile(candidate,target);target.with_suffix('.jar.sha256').write_text(build.sha(target)+'  '+target.name+'\n')
    for file in FILES:retire('mods/'+file,REPO/'server/mods'/file)
    for entry in manifest['releases']:
        if entry['id']=='empirecraft_armor':entry['artifact']='mods/'+candidate.name
    write(manifest_path,manifest)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_armor')
    row.update(updated=True,pin_reason='1.0.4: fix client AddPackFindersEvent Missing path; only ArmorLanguages.class changes; real Forge 47.4.23 and 47.4.28 regression in layered-armor/audit/client-path.json.')
    write(path,lock)
    adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';update(adm,candidate,WITHDRAWN)
    backup=adm.with_name(adm.name+'.before-armor-1.0.4')
    if backup.exists():
        target=REPO/'disabled-mods/armor-client-path-2026-11-13/server/config/admanticheat/idfile'/backup.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.move(backup,target)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        rows=[]
        for p in sorted((REPO/folder).glob('*.jar')):
            raw=p.read_bytes();rows.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/inventory,rows)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={p.name:build.sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained={n:v for n,v in json.loads(path.read_text()).items() if not n.startswith('empirecraft-layered-armor-')}
    with zipfile.ZipFile(candidate) as z:retained[candidate.name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    write(path,retained)
def package(candidate,report):
    write(ROOT/'audit/client-path-release.json',report)
    files={'INSTALL-UA.md':(ROOT/'CLIENT-PATH-FIX-UA.md').read_bytes(),'update_armor_adm.py':(ROOT/'update_armor_adm.py').read_bytes(),
           'verification.json':(ROOT/'audit/client-path-release.json').read_bytes()}
    for side in ['client','server']:
        files[side+'/mods/'+candidate.name]=candidate.read_bytes();files[side+'/mods/'+candidate.name+'.sha256']=candidate.with_suffix('.jar.sha256').read_bytes()
    sources={}
    for p in ROOT.rglob('*'):
        if p.is_file() and not any(c in ['build','dist','audit','__pycache__'] for c in p.relative_to(ROOT).parts):sources['layered-armor/'+p.relative_to(ROOT).as_posix()]=p.read_bytes()
    source=ROOT/'dist'/(NAME+'-sources.zip');archive(source,sources);files[source.name]=source.read_bytes()
    files['REMOVE-OLD.txt']=b'Remove previous empirecraft-layered-armor JARs and their .sha256 files on both sides. Keep only 1.0.4. Client Emotecraft and Pop-up Emotes have been withdrawn; do not restore them. Server copies of those optional mods are retained.\n'
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist'/(NAME+'-install.zip');archive(target,files)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    candidate=ROOT/'dist'/build.NAME;native=json.loads((ROOT/'audit/client-path.json').read_text());delta=json.loads((ROOT/'audit/client-path-build.json').read_text())
    assert native['sha256']==delta['sha256']==build.sha(candidate) and len(native['tests'])==4 and native['language']
    baseline=REPO/'mods/empirecraft-layered-armor-1.0.3.jar'
    if not baseline.exists():baseline=next((REPO/'disabled-mods').rglob(baseline.name))
    def refs(jar):
        text=subprocess.check_output([str(a.java_home/'bin/javap'),'-c','-p','-classpath',str(jar),'ua.empirecraft.armor.client.ArmorLanguages'],text=True)
        return sorted(set(re.findall(r'// (?:InterfaceMethod|Method|Field) (net/minecraft/[^\n]+)',text)))
    assert refs(baseline)==refs(candidate),'Production Minecraft member mappings changed'
    report=dict(status='passed',file=candidate.name,sha256=build.sha(candidate),native=native,compatibility=delta,adm=verify_adm(candidate),
        production_minecraft_member_references_preserved=refs(candidate),
        withdrawn_client_mods=audit_withdrawals(a.java_home/'bin/java'),graphical_client_tested=False,production_world_opened=False)
    if a.install:install(candidate)
    package(candidate,report)
    subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('ARMOR_CLIENT_FIX_RELEASE_PASS',build.sha(candidate))
if __name__=='__main__':main()
