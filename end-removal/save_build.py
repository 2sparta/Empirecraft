#!/usr/bin/env python3
"""Explicit write-back allowlist for the verified feature branch; never stage unrelated mods."""
import json,os,subprocess
from pathlib import Path
from build import NAMES,MOD

def main():
    assert os.environ.get('GITHUB_REF_NAME')=='codex/remove-end-content'
    paths=['end-removal/audit','end-removal/src/main/resources','end-removal/dist','kubejs/client_scripts/empirecraft_no_end.js','server/kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js','modpack-lock.json','seasonal-calendar/installed-mods.json','seasonal-calendar/audit/coverage.json','forging-balance/installed-client-mods.json','server/config/admanticheat/idfile/admanticheat_id_list.json']
    for side in ['mods','server/mods']:
        for old in NAMES:
            subprocess.run(['git','rm','-f','--ignore-unmatch','--',side+'/'+old,side+'/'+old+'.sha256'],check=True)
        for name in [*NAMES.values(),MOD]:
            paths.extend([side+'/'+name,side+'/'+name+'.sha256'])
    for name in json.loads(Path('end-removal/audit/generated-data.json').read_text()):
        assert '..' not in Path(name).parts and not name.startswith('/')
        paths.append('server/kubejs/data/'+name)
    subprocess.run(['git','add','-f','--',*paths],check=True)
    if subprocess.run(['git','diff','--cached','--quiet']).returncode==0:return
    subprocess.run(['git','config','user.name','github-actions[bot]'],check=True)
    subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
    subprocess.run(['git','commit','-m','Package verified End removal, obtainable recipes and matching client/server jars'],check=True)
    subprocess.run(['git','push','origin','HEAD:codex/remove-end-content'],check=True)

if __name__=='__main__':main()
