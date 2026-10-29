#!/usr/bin/env python3
"""Package the paired JAR, complete source, tests and installer without game dependencies."""
import hashlib,json,zipfile
from pathlib import Path
import release

def main():
    rows=release.manifest();entries={}
    for row in rows:
        for folder in ['mods','server/mods']:
            for suffix in ['', '.sha256']:entries[folder+'/'+row['file']+suffix]=(release.REPO/folder/(row['file']+suffix)).read_bytes()
    selected=['src','tests']
    for directory in selected:
        for path in (release.ROOT/directory).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts:entries['ship-cannons/'+path.relative_to(release.ROOT).as_posix()]=path.read_bytes()
    for name in ['LICENSE','README.md','INSTALL-UA.txt','build.py','build-inputs.json','test.py','test_client.py','release.py','release.json','package.py','VALIDATION.txt']:
        entries['ship-cannons/'+name]=(release.ROOT/name).read_bytes()
    entries['START-HERE-UA.txt']=(release.ROOT/'INSTALL-UA.txt').read_bytes()
    entries['PACKAGE-CONTENTS.json']=(json.dumps({p:dict(size=len(data),sha256=hashlib.sha256(data).hexdigest()) for p,data in sorted(entries.items())},indent=2)+'\n').encode()
    output=release.ROOT/'dist/Empirecraft-Ship-Cannons-1.2.1.zip';output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output,'w') as z:
        for name,content in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,content)
    output.with_suffix('.zip.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(output)
if __name__=='__main__':main()
