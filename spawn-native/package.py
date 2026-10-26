#!/usr/bin/env python3
"""Create a complete client/server update without configs or world files."""
import hashlib,json,zipfile
from build import ROOT,REPO,NAME,jar
def main():
    files={'README.md':(ROOT/'README.md').read_bytes(),'install.py':(ROOT/'install.py').read_bytes(),'audit/removal.json':(ROOT/'audit/removal.json').read_bytes(),'release.json':(ROOT/'release.json').read_bytes()}
    for side in ['client','server']:
        for suffix in ['', '.sha256']:files[side+'/mods/'+NAME+suffix]=(REPO/('mods' if side=='client' else 'server/mods')/(NAME+suffix)).read_bytes()
    for suffix in ['', '.sha256']:files['server/mods/empirecraft-content-policy-1.0.1.jar'+suffix]=(REPO/'server/mods'/('empirecraft-content-policy-1.0.1.jar'+suffix)).read_bytes()
    paths=['server/kubejs/server_scripts/zzzzzz_empirecraft_progression.js','kubejs/client_scripts/empirecraft_progression.js','kubejs/startup_scripts/empirecraft_progression_tabs.js']
    paths+=['server/kubejs/data/'+p for p in json.loads((REPO/'content-policy/audit/generated-data.json').read_text())]
    for path in paths:files[('client/'+path if path.startswith('kubejs/') else path)]=(REPO/path).read_bytes()
    # Exact obsolete paths are reviewable and must be deleted when installing over an old pack.
    old=json.loads((ROOT/'obsolete-files.json').read_text());files['DELETE-THESE-FILES.json']=(json.dumps(old,indent=2)+'\n').encode()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(files.items())).encode()
    target=ROOT/'dist/Empirecraft-Spawn-Native-1.0.0.zip';jar(target,files)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        assert all(z.read(name)==data for name,data in files.items())
        assert not any('/config/' in name or '/world/' in name or 'SmokeMod' in name for name in z.namelist())
    sha=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.zip.sha256').write_text(sha+'  '+target.name+'\n')
    print('PASS native Spawn installation package:',len(files),'files;',sha)
if __name__=='__main__':main()
