#!/usr/bin/env python3
"""Refresh only the ADM reference in existing ChatPlus installers, preserving native binaries."""
import hashlib, io, zipfile
from build import REPO

def main():
    adm=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    for target in sorted((REPO/'chat-channels/dist').glob('*.zip')):
        with zipfile.ZipFile(target) as original:
            names=[n for n in original.namelist() if n.endswith('/admanticheat_id_list.json')]
            if not names: continue
            output=io.BytesIO()
            with zipfile.ZipFile(output,'w') as archive:
                for entry in original.infolist(): archive.writestr(entry,adm if entry.filename in names else original.read(entry))
            for name in names: assert zipfile.ZipFile(io.BytesIO(output.getvalue())).read(name)==adm
        target.write_bytes(output.getvalue())
        target.with_suffix('.zip.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
        print('PASS refreshed shared ADM reference:',target.name)

if __name__=='__main__':main()
