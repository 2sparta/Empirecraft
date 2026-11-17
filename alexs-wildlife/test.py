#!/usr/bin/env python3
"""Geographic positives/negatives, exact pack duplicates, recipe cuts and retained native bytes."""
import json,os,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,WORK,JDK17,AM,MOD,SPAWN,read,refs,sha

def main():
    report=json.loads((ROOT/'audit/build.json').read_text());dist=ROOT/'dist'
    for name,digest in report['sha256'].items():assert sha(dist/name)==digest,name
    classes=WORK/'policy-tests';classes.mkdir(exist_ok=True)
    gson=next((REPO/'server/libraries/com/google/code/gson').rglob('*.jar'))
    cp=os.pathsep.join(map(str,[classes,dist/MOD,gson]))
    subprocess.run([JDK17/'bin/javac','--release','17','-cp',cp,'-d',classes,ROOT/'tests/PolicyTest.java'],check=True)
    result=subprocess.run([JDK17/'bin/java','-ea','-cp',cp,'ua.empirecraft.wildlife.PolicyTest'],check=True,text=True,stdout=subprocess.PIPE);print(result.stdout,end='')
    policy=json.loads((ROOT/'src/main/resources/wildlife-policy.json').read_text());hidden=set(policy['hidden_items'])
    known=json.loads((REPO/'server/config/empirecraft-calendar-coverage.json').read_text())['animals']
    for name,why in policy['removed'].items():
        if why.startswith('duplicate: '):assert why.removeprefix('duplicate: ') in known,(name,why)
    original=read(ROOT/'upstream/alexsmobs-1.22.9.jar');edited=read(dist/AM)
    changed_classes=[n for n in original if n.endswith('.class') and original[n]!=edited[n]]
    assert set(changed_classes)=={'com/github/alexthe666/alexsmobs/world/AMMobSpawnBiomeModifier.class','com/github/alexthe666/alexsmobs/world/FeatureLeafcutterAnthill.class'},changed_classes
    assert b'empirecraft_wildlife' in edited['META-INF/mods.toml']
    recipes=0
    for name,raw in edited.items():
        if name.startswith('data/') and '/recipes/' in name and name.endswith('.json'):
            assert not refs(json.loads(raw))&hidden,name;recipes+=1
    spawn=read(dist/SPAWN)
    assert 'data/spawn/forge/biome_modifier/add_small_anthill.json' not in spawn
    assert json.loads(spawn['data/spawn/worldgen/configured_feature/anthill.json'])['config']['anthill_chance']==0
    assert 'spawn:anthill' not in refs(json.loads(spawn['data/spawn/worldgen/biome/ant_gardens.json']))
    baseline=next(p for p in [REPO/'mods/spawn-4.0.8-1.20.1-empirecraft.1.jar',REPO/'disabled-mods/alexs-wildlife/mods/spawn-4.0.8-1.20.1-empirecraft.1.jar'] if p.exists())
    for n,raw in read(baseline).items():
        if n.endswith('.class'):assert spawn[n]==raw,n
    with zipfile.ZipFile(dist/MOD) as z:
        assert z.testzip() is None
        assert not any('Probe' in n or 'PolicyTest' in n or 'PatchBiome' in n for n in z.namelist())
        assert z.read('earth-defaults.json')==(REPO/'earth-geography/src/main/resources/earth-defaults.json').read_bytes()
    output=dict(status='passed',geography=result.stdout.strip(),kept_recipes=recipes,retained_alexs_classes=sum(n.endswith('.class') for n in original)-len(changed_classes),patched_classes=changed_classes,sha256=report['sha256'])
    (ROOT/'audit/static.json').write_text(json.dumps(output,indent=2)+'\n');print('WILDLIFE_STATIC_PASS')
if __name__=='__main__':main()
