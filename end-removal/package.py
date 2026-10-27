#!/usr/bin/env python3
"""Side-specific installation overlays containing only the requested End removal changes."""
import hashlib
from build import ROOT, REPO, NAMES, MOD, VERSION, archive

def main():
    for side,base in [('client','mods'),('server','server/mods')]:
        files={'README.md':(ROOT/'README.md').read_bytes(),'REMOVE-OLD-JARS.txt':('\n'.join(NAMES)+'\n').encode()}
        for name in [*NAMES.values(),MOD]:
            for suffix in ['', '.sha256']:files['mods/'+name+suffix]=(REPO/base/(name+suffix)).read_bytes()
        if side=='client':files['kubejs/client_scripts/empirecraft_no_end.js']=(REPO/'kubejs/client_scripts/empirecraft_no_end.js').read_bytes()
        else:
            files['kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js']=(REPO/'server/kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js').read_bytes()
            import json
            for name in json.loads((ROOT/'audit/generated-data.json').read_text()):files['kubejs/data/'+name]=(REPO/'server/kubejs/data'/name).read_bytes()
            files['reference-configs/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
        files['verification/content.json']=(ROOT/'audit/content.json').read_bytes();files['verification/build.json']=(ROOT/'audit/build.json').read_bytes()
        files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
        archive(ROOT/('dist/Empirecraft-No-End-'+VERSION+'-'+side+'.zip'),files)
    print('PASS separate client/server End removal overlays')

if __name__=='__main__':main()
