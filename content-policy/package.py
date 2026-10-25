#!/usr/bin/env python3
"""Package the server policy/datapack and client UI overlay without live configs or world data."""
import hashlib,json,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME

def main():
    files={'README.md':ROOT/'README.md','audit/policy.json':ROOT/'audit/policy.json','audit/content.json':ROOT/'audit/content.json'}
    for suffix in ['', '.sha256']:files['server/mods/'+NAME+suffix]=REPO/'server/mods'/(NAME+suffix)
    files['server/kubejs/server_scripts/zzzzzz_empirecraft_progression.js']=REPO/'server/kubejs/server_scripts/zzzzzz_empirecraft_progression.js'
    for path in json.loads((ROOT/'audit/generated-data.json').read_text()):files['server/kubejs/data/'+path]=REPO/'server/kubejs/data'/path
    for path in ['client_scripts/empirecraft_progression.js','startup_scripts/empirecraft_progression_tabs.js']:files['client/kubejs/'+path]=REPO/'kubejs'/path
    contents={name:p.read_bytes() for name,p in files.items()}
    contents['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(contents.items())).encode()
    target=ROOT/'dist/Empirecraft-Progression-1.0.0.zip';target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target,'w') as z:
        for name,data in sorted(contents.items()):
            info=zipfile.ZipInfo(name,(2026,10,25,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for name,data in contents.items():assert z.read(name)==data
        assert not any('/config/' in n or n.endswith('.db') or '/world/' in n for n in z.namelist())
    sha=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.zip.sha256').write_text(sha+'  '+target.name+'\n');print('PASS progression package:',len(contents),'verified files;',sha)

if __name__=='__main__':main()
