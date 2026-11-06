#!/usr/bin/env python3
"""Install the current paired mod and reproduce its source archive from tracked inputs."""
import argparse,hashlib,json,shutil,subprocess,sys,tomllib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
VERSION=tomllib.loads((ROOT/'src/main/resources/META-INF/mods.toml').read_text())['mods'][0]['version'];NAME=f'townyrecruits-{VERSION}.jar'
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def install():
    raw=(ROOT/'dist'/NAME).read_bytes();digest=sha(raw)
    for side in ['mods','server/mods']:
        folder=REPO/side
        for old in folder.glob('townyrecruits-*.jar'):
            if old.name!=NAME:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        (folder/NAME).write_bytes(raw);(folder/(NAME+'.sha256')).write_text(digest+'  '+NAME+'\n')
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());row=next(x for x in lock['mods'] if x['id']=='townyrecruits')
    previous=row['version'];row.update(file=NAME,version=VERSION,old_version=previous if previous!=VERSION else row.get('old_version'),updated=True,
        pin_reason='City-owned regiments with allied commanders, preserved national quota and combat ownership; paired with Feudal 1.17.0 and Engagement 1.1.0.')
    write(path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    row=next(x for x in json.loads(path.read_text())['mods'] if x['id']=='townyrecruits')
    for relative,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
        path=REPO/relative;items=json.loads(path.read_text());items=[x for x in items if not x['name'].startswith('townyrecruits-')];items.append(dict(name=NAME,path=side+'/'+NAME,size=len(raw),sha=row['git_sha']));write(path,sorted(items,key=lambda x:x['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(path.read_text());data['jar_sha256']={k:v for k,v in data['jar_sha256'].items() if not k.startswith('townyrecruits-')};data['jar_sha256'][NAME]=digest;write(path,data)
def package(check):
    from io import BytesIO
    current=(ROOT/'dist'/NAME).read_bytes()
    with zipfile.ZipFile(ROOT/'dist'/NAME) as archive:
        assert archive.testzip() is None
        assert tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods'][0]['version']==VERSION
    if check:
        for side in ['mods','server/mods']:
            path=REPO/side/NAME;assert path.read_bytes()==current;assert path.with_suffix('.jar.sha256').read_text()==sha(current)+'  '+NAME+'\n'
        row=next(x for x in json.loads((REPO/'modpack-lock.json').read_text())['mods'] if x['id']=='townyrecruits');assert row['version']==VERSION and row['sha256']==sha(current)
    files={}
    for folder in ['src','tools','assets']:
        for path in (ROOT/folder).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts:files['towny-recruits/'+str(path.relative_to(ROOT))]=path.read_bytes()
    for name in ['build.py','test.py','release.py','requirements-assets.txt','README.md','TACTICS.md','ARMY.md','MAP.md','CONTRACTS.md','CHANGELOG.md','VALIDATION.md','LICENSE']:
        files['towny-recruits/'+name]=(ROOT/name).read_bytes()
    output=BytesIO()
    with zipfile.ZipFile(output,'w') as archive:
        for name,raw in sorted(files.items()):
            item=zipfile.ZipInfo(name,(2026,11,6,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;archive.writestr(item,raw)
    path=ROOT/f'dist/townyrecruits-{VERSION}-source.zip';raw=output.getvalue();checksum=sha(raw)+'  '+path.name+'\n'
    if check:assert path.read_bytes()==raw;assert path.with_suffix('.zip.sha256').read_text()==checksum
    else:path.write_bytes(raw);path.with_suffix('.zip.sha256').write_text(checksum)
    print('RECRUITS_RELEASE_PASS',VERSION,sha((ROOT/'dist'/NAME).read_bytes()))
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--install',action='store_true');parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.install:install()
    package(args.check)
if __name__=='__main__':main()
