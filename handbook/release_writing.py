#!/usr/bin/env python3
"""Package and optionally install the writing and handbook layout repair."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
from update_installation import apply,plan

ROOT=build.ROOT;REPO=build.REPO;NAME='Empirecraft-Handbook-Writing-Fix-1'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(build.encode(data))
def stage():
    book=ROOT/'dist'/build.NAME;files={};release={'id':build.NS,'version':build.VERSION,'file':book.name,'sha256':sha(book),'files':{}}
    for side,folder in [('client','kubejs'),('server','server/kubejs')]:
        relative={ 'mods/'+book.name:book.read_bytes(),'mods/'+book.name+'.sha256':book.with_suffix('.jar.sha256').read_bytes() }
        script='server_scripts/zzzzzzzzz_empirecraft_paper_books.js'
        relative['kubejs/'+script]=(REPO/folder/script).read_bytes()
        for p in sorted((REPO/folder/'data/empirecraft_guidebook/advancements/recipes').glob('*.json')):relative['kubejs/'+p.relative_to(REPO/folder).as_posix()]=p.read_bytes()
        release['files'][side]={n:hashlib.sha256(raw).hexdigest() for n,raw in relative.items()}
        for n,raw in relative.items():files[side+'/'+n]=raw
    assert release['files']['client']==release['files']['server']
    bundle=ROOT/'build/writing-bundle'
    for n,raw in files.items():p=bundle/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
    write(bundle/'release.json',release);write(ROOT/'writing-release.json',release)
    return release,files,bundle
def install(release,bundle):
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
    backups=REPO/'disabled-mods/handbook-writing-2026-11-13'
    prepared=[(side,root,plan(root,side,bundle,release)) for side,root in [('client',REPO),('server',REPO/'server')]]
    old=[]
    for side,root,prepared_plan in prepared:
        old.extend((side,root,p,p.relative_to(REPO).as_posix()) for p in prepared_plan[2] if p.suffix=='.jar')
        apply(prepared_plan)
    for side,root,p,relative in old:
        source=root/'.empirecraft-backups/handbook-1.0.3'/p.relative_to(root)
        target=backups/side/'mods'/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
        target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+p.name+'\n')
        if relative not in manifest['retired']:manifest['retired'].append(relative)
        manifest.setdefault('retired_artifacts',{})[relative]=target.relative_to(REPO).as_posix()
    # Keep transient installer backups out of active folders and the public release.
    for side,root,_ in prepared:
        source=root/'.empirecraft-backups/handbook-1.0.3'
        if source.exists():
            dest=ROOT/'build/installation-backup'/side
            shutil.copytree(source,dest,dirs_exist_ok=True);shutil.rmtree(source)
            if not any(source.parent.iterdir()):source.parent.rmdir()
    for entry in manifest['releases']:
        if entry['id']==build.NS:entry['artifact']='mods/'+release['file']
    write(manifest_path,manifest)
    lock_path=REPO/'modpack-lock.json';lock=json.loads(lock_path.read_text())
    next(r for r in lock['mods'] if r['id']==build.NS).update(updated=True,pin_reason='1.0.3: wrapped Patchouli headings, writing recipes recovered through late KubeJS; handbook/audit/writing-release.json.')
    write(lock_path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    for folder,inventory in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        rows=[]
        for p in sorted((REPO/folder).glob('*.jar')):
            raw=p.read_bytes();rows.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/inventory,rows)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={p.name:sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained={k:v for k,v in json.loads(path.read_text()).items() if not k.startswith('empirecraft-handbook-')};retained[release['file']]={};write(path,retained)
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--stage-only',action='store_true');a=p.parse_args()
    release,files,bundle=stage()
    if a.stage_only:print('HANDBOOK_WRITING_STAGED',release['sha256']);return
    native=json.loads((ROOT/'audit/access.json').read_text());layout=json.loads((ROOT/'audit/layout-test.json').read_text());installer=json.loads((ROOT/'audit/writing-installer.json').read_text());client=json.loads((ROOT/'audit/client-template.json').read_text())
    assert native['status']==layout['status']==installer['status']==client['status']=='passed'
    assert native['sha256']==layout['sha256']==installer['sha256']==client['sha256']==release['sha256']
    assert native['script_sha256']==sha(REPO/'server/kubejs/server_scripts/zzzzzzzzz_empirecraft_paper_books.js')
    report={'status':'passed','release':release,'native':native,'layout':layout,'client_template':client,'installer':installer,'graphical_client_tested':False,'production_world_opened':False}
    write(ROOT/'audit/writing-release.json',report)
    if a.install:install(release,bundle)
    files.update({'release.json':build.encode(release),'INSTALL-UA.md':(ROOT/'WRITING-FIX-UA.md').read_bytes(),'update_installation.py':(ROOT/'update_installation.py').read_bytes(),'verification.json':build.encode(report)})
    source_files={}
    for path in ROOT.rglob('*'):
        if path.is_file() and not any(c in ['build','dist','audit','__pycache__'] for c in path.relative_to(ROOT).parts):source_files['handbook/'+path.relative_to(ROOT).as_posix()]=path.read_bytes()
    source=ROOT/'dist'/(NAME+'-sources.zip');build.archive(source,source_files);files[source.name]=source.read_bytes()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist'/(NAME+'-install.zip');build.archive(target,files);target.with_suffix('.zip.sha256').write_text(sha(target)+'  '+target.name+'\n')
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
    if a.install:subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('HANDBOOK_WRITING_RELEASE_PASS',sha(target))
if __name__=='__main__':main()
