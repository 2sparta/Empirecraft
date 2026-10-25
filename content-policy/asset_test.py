#!/usr/bin/env python3
"""Validate exclusions, datapack priority, codec settings and exact installed dependency versions."""
import argparse,hashlib,json,tomllib,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME

def main():
    p=argparse.ArgumentParser();p.add_argument('--mods',type=Path,required=True);args=p.parse_args()
    manifest=json.loads((ROOT/'audit/generated-data.json').read_text())
    for path in manifest:
        assert (ROOT/'src/main/resources/data'/path).read_bytes()==(REPO/'server/kubejs/data'/path).read_bytes(),'High-priority datapack drift: '+path
        if '/advancements/' in path:
            data=json.loads((REPO/'server/kubejs/data'/path).read_text())
            assert 'criteria' in data and 'minecraft:blaze_powder' not in json.dumps(data),'Invalid or Nether-dependent recipe discovery advancement'
    for material in ['limonite','pyrite','magnitite']:
        biome=json.loads((REPO/f'server/kubejs/data/medieval_boomsticks/forge/biome_modifier/add_{material}_ore.json').read_text())
        assert biome=={'type':'forge:none'}
        feature=json.loads((REPO/f'server/kubejs/data/medieval_boomsticks/worldgen/placed_feature/{material}_ore_placed.json').read_text())
        assert feature['placement']==[{'type':'minecraft:count','count':0}]
    assert json.loads((REPO/'server/kubejs/data/spawn/worldgen/structure_set/volcanic_island.json').read_text())['placement']['frequency']==0
    assert json.loads((REPO/'server/kubejs/data/spawn/animal_variants/dodo/magmatic.json').read_text())['spawnWeight']==0
    for name in ['diamond_sword','netherite_sword_smithing']:
        assert json.loads((REPO/f'server/kubejs/data/minecraft/recipes/{name}.json').read_text())['conditions']==[{'type':'forge:false'}]
    versions={}
    for jar in args.mods.glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'META-INF/mods.toml' in z.namelist():
                versions.update({m['modId']:m['version'] for m in tomllib.loads(z.read('META-INF/mods.toml').decode()).get('mods',[])})
    with zipfile.ZipFile(REPO/'server/mods'/NAME) as z:
        meta=tomllib.loads(z.read('META-INF/mods.toml').decode())
        assert meta['mods'][0]['displayTest']=='IGNORE_SERVER_VERSION'
        for row in meta['dependencies']['empirecraft_content_policy']:
            assert row['side']=='SERVER'
            if row['modId'] not in ('forge','minecraft'):assert row['versionRange']=='['+versions[row['modId']]+']','Dependency differs from the actual installed version: '+row['modId']
    raw=(REPO/'server/mods'/NAME).read_bytes()
    lock=json.loads((REPO/'modpack-lock.json').read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_content_policy')
    assert row['sha256']==hashlib.sha256(raw).hexdigest() and row['git_sha']==hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest() and row['sides']==['server']
    print('PASS generation disabled at both biome and placement layers, volcanic lifecycle, high-priority recipes and exact installed dependency descriptors')

if __name__=='__main__':main()
