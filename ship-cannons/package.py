#!/usr/bin/env python3
"""Package only the two ship fixes and their local installer; never include private inventories."""
import hashlib
from pathlib import Path
import zipfile
import release

def main():
    release.manifest()
    entries={}
    for _,_,name,_ in release.MODS:
        for folder in ['mods','server/mods']:
            for suffix in ['', '.sha256']:entries[folder+'/'+name+suffix]=(release.REPO/folder/(name+suffix)).read_bytes()
    for name in ['release.py','release.json','INSTALL-UA.txt','README.md']:
        entries['ship-cannons/'+name]=(release.ROOT/name).read_bytes()
    output=release.ROOT/'dist/Empirecraft-Ship-Safety.zip';output.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(output,'w') as z:
        for name,content in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,26,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,content)
    output.with_suffix('.zip.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(output)
if __name__=='__main__':main()
