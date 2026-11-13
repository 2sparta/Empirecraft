#!/usr/bin/env python3
"""Install/package only the binaries proven in the native server-cleanup suite."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
from filtering import REMOVED
from update_installation import apply

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;NAME='Empirecraft-Caupona-Removal-1'
SOURCES=['server-cleanup','tfc-integration','world-rules','seasonal-calendar','seasonal-leaves','end-removal','pack-fixes','handbook']
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            i=zipfile.ZipInfo(name,(2026,11,13,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
    path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def verified(release):
    native=json.loads((REPO/'tfc-integration/audit/server-cleanup.json').read_text())
    assert len(native['phases'])==2 and all('PASS' in p for p in native['phases']) and native['cleanup_markers'] and native['current_update_markers']
    assert set(native['removed_mods'])==REMOVED and not native['production_world_opened']
    for row in release['mods']:assert native['jars'][row['file']]==row['sha256']==sha(ROOT/'build/staged'/row['file'])
    installer=json.loads((ROOT/'audit/installer.json').read_text())
    assert installer['source_sha256']==sha(ROOT/'update_installation.py') and installer['mods']=={r['file']:r['sha256'] for r in release['mods']}
    return dict(status='passed',native=native,installer=installer,compatibility=json.loads((ROOT/'audit/compatibility.json').read_text()),
        dependencies=json.loads((ROOT/'audit/dependencies.json').read_text()),production_world_opened=False,graphical_client_tested=False)
def install(release):
    bundle=ROOT/'build/bundle'
    for row in release['mods']:
        for side in row['sides']:
            target=bundle/side/'mods'/row['file'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'build/staged'/row['file'],target)
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
    old=[];ids={r['id'] for r in release['mods']}
    for side,folder in [('client','mods'),('server','server/mods')]:
        for p in (REPO/folder).glob('*.jar'):
            with zipfile.ZipFile(p) as z:
                if 'META-INF/mods.toml' not in z.namelist():continue
                declared={m['modId'] for m in __import__('tomllib').loads(z.read('META-INF/mods.toml').decode())['mods']}
            if declared&(ids|REMOVED) and not any(r['file']==p.name and r['sha256']==sha(p) for r in release['mods']):old.append((side,p))
    backup=REPO/'disabled-mods/caupona-removal-2026-11-13'
    apply(REPO,'client',release,bundle,backup);apply(REPO/'server','server',release,bundle,backup)
    for side,p in old:
        relative=p.relative_to(REPO).as_posix();target=backup/side/'mods'/p.name
        assert target.exists() and not p.exists()
        if relative not in manifest['retired']:manifest['retired'].append(relative)
        manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
    manifest['releases']=[r for r in manifest['releases'] if r['id'] not in REMOVED]
    for row in release['mods']:
        entry=next((r for r in manifest['releases'] if r['id']==row['id']),None)
        if entry is None:entry={'id':row['id']};manifest['releases'].append(entry)
        entry['artifact']=('server/mods/' if row['sides']==['server'] else 'mods/')+row['file']
    write(manifest_path,manifest)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text())
    for row in release['mods']:
        found=next(r for r in lock['mods'] if r['id']==row['id']);found.update(source=row['source'],updated=True,pin_reason='Caupona removal: live namespaces cleaned; server-cleanup/verification.json contains native and compatibility evidence.')
    write(path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    active={}
    for side,folder,inventory in [('client','mods','forging-balance/installed-client-mods.json'),('server','server/mods','seasonal-calendar/installed-mods.json')]:
        rows=[]
        for p in sorted((REPO/folder).glob('*.jar')):
            raw=p.read_bytes();active[p.name]=p
            rows.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/inventory,rows)
    path=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(path.read_text()).items() if k in active}
    for row in release['mods']:
        with zipfile.ZipFile(ROOT/'build/staged'/row['file']) as z:retained[row['file']]={n:hashlib.sha256(z.read(n)).hexdigest() for n in sorted(z.namelist()) if n.endswith('.class')}
    write(path,retained)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={p.name:sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
def package(release,report):
    write(ROOT/'verification.json',report)
    files={n:(ROOT/n).read_bytes() for n in ['README.uk.md','policy.json','filtering.py','update_installation.py','release.json','verification.json']}
    for row in release['mods']:
        raw=(ROOT/'build/staged'/row['file']).read_bytes()
        for side in row['sides']:
            name=side+'/mods/'+row['file'];files[name]=raw;files[name+'.sha256']=(row['sha256']+'  '+row['file']+'\n').encode()
    sources={}
    for module in SOURCES:
        for p in (REPO/module).rglob('*'):
            if p.is_file() and not any(x in ['build','dist','audit','__pycache__','.gradle'] for x in p.relative_to(REPO/module).parts):sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
    source=ROOT/'dist'/(NAME+'-sources.zip');archive(source,sources);files[source.name]=source.read_bytes()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist'/(NAME+'-install.zip');archive(target,files)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args()
    release=json.loads((ROOT/'release.json').read_text());report=verified(release)
    if a.install:install(release)
    package(release,report)
    subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('CAUPONA_REMOVAL_RELEASE_PASS mods='+str(len(release['mods'])))
if __name__=='__main__':main()
