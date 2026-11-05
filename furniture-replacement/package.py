#!/usr/bin/env python3
"""Build an installation delta, including explicit removal instructions; no existing world data."""
import hashlib,io,json,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
BASE='f032ad84c8a4b0255f9534b8afcaf4dbe9553b7a'

def main():
    if subprocess.run(['git','cat-file','-e',BASE],cwd=REPO,stderr=subprocess.DEVNULL).returncode:
        subprocess.run(['git','fetch','--filter=blob:none','--depth=1','origin',BASE],cwd=REPO,check=True)
    changed=subprocess.check_output(['git','diff','--name-only',BASE],cwd=REPO,text=True).splitlines()
    entries={}
    for name in changed:
        path=REPO/name
        if not path.is_file():continue
        allowed=name.startswith(('mods/','server/mods/','config/','server/config/','kubejs/','server/kubejs/','server/defaultconfigs/','server/mohist-config/')) or name=='modpack-lock.json'
        allowed|=name in ['server/world/serverconfig/locks-server.toml','server/world_europe/serverconfig/locks-server.toml']
        if allowed:entries[name]=path.read_bytes()
    for folder in ['mods','server/mods']:
        for pattern in ['handcrafted-*.jar*','timber_joinery-*.jar*']:
            for path in (REPO/folder).glob(pattern):entries[path.relative_to(REPO).as_posix()]=path.read_bytes()
    pack=REPO/'translations/dist/Empirecraft-Translations-uk-ru-1.1.0.zip'
    entries['resourcepacks/'+pack.name]=pack.read_bytes()
    entries['README-UA.md']=(ROOT/'README.md').read_bytes()
    entries['removed-mods.json']=(ROOT/'removed-mods.json').read_bytes()
    retired=json.loads(entries['removed-mods.json'])
    entries['REMOVE-MACAWS.txt']=('Before copying files, remove these JARs and their .sha256 files from BOTH client and server mods folders:\n'+'\n'.join(row['file'] for row in retired)+'\nWorld conversion is not included. Back up existing worlds and inventories before applying.\n').encode()
    runtime=ROOT/'build/runtime/furniture-runtime.json'
    if runtime.exists():
        report=json.loads(runtime.read_text());assert report['passed'],report
        entries['validation/furniture-runtime.json']=runtime.read_bytes()
    entries['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(entries.items())).encode()
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,5,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
    with zipfile.ZipFile(io.BytesIO(out.getvalue())) as z:
        assert z.testzip() is None
        assert len([n for n in z.namelist() if n.endswith('.jar')])==4
        assert not any('furniture-audit' in n or n.startswith('server/world/region/') for n in z.namelist())
    target=ROOT/'dist/Empirecraft-Handcrafted-Timber-1.0.0-update.zip';target.parent.mkdir(exist_ok=True)
    target.write_bytes(out.getvalue());digest=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest+'  '+target.name+'\n')
    print('PASS replacement installation package:',target.name,'SHA-256:',digest)

if __name__=='__main__':main()
