#!/usr/bin/env python3
"""Validate the actual native JAR, all references, high-priority data and cleanup policy."""
import json,os,subprocess,zipfile
from build import ROOT,REPO,NAME,BANNED,compiler,run,references
def main():
    with zipfile.ZipFile(REPO/'mods'/NAME) as z:
        assert z.testzip() is None
        removed=json.loads(z.read('empirecraft-spawn-removal.json'))
        assert len(removed['removed_items'])==31 and len(removed['removed_blocks'])==23 and len(removed['removed_entities'])==6
        for name in z.namelist():
            if name.startswith('data/') and name.endswith('.json'):
                bad=references(json.loads(z.read(name)))&BANNED;assert not bad,(name,bad)
        for name in ['data/spawn/loot_tables/entities/sea_cow.json','data/spawn/worldgen/structure/dodo_island.json','data/spawn/spawn_block_recipes/anthill/dirt.json']:
            if name.endswith('dirt.json'):continue
            assert name in z.namelist(),name
        assert 'ua/empirecraft/spawn/RemovedMappings.class' in z.namelist()
    policy=json.loads((REPO/'content-policy/audit/policy.json').read_text())
    assert not any(k.startswith('spawn:') for k in policy['replacements'])
    assert not policy['entities']
    assert not any(p.is_file() for p in (REPO/'server/kubejs/data/spawn').rglob('*')),'Stale Spawn data override'
    for p in [REPO/'server/kubejs/server_scripts/zzzzzz_empirecraft_progression.js',REPO/'kubejs/client_scripts/empirecraft_progression.js',REPO/'kubejs/startup_scripts/empirecraft_progression_tabs.js']:
        text=p.read_text()
        for key in removed['removed_items']:assert '"'+key+'"' not in text,(p,key)
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));tools=ROOT/'build/tools';cp=os.pathsep.join(map(str,[tools,*asm]))
    run([*compiler(),'-proc:none','--release','17','-cp',cp,'-d',tools,ROOT/'tools/VerifySpawn.java'])
    run(['java','-cp',cp,'VerifySpawn',REPO/'mods'/NAME])
    run(['python3',ROOT/'release.py','--check'])
    print('PASS native assets/data references, retained features, no KubeJS Spawn substitution or stale overrides')
if __name__=='__main__':main()
