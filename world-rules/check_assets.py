#!/usr/bin/env python3
"""Catch misspelled renewal/trade IDs and pack integration errors using installed JAR assets."""
import argparse
import json
from pathlib import Path
import re
import tomllib
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('jars',type=Path);args=p.parse_args()
    entries=set()
    for file in args.jars.rglob('*.jar'):
        with zipfile.ZipFile(file) as jar:entries.update(jar.namelist())
    def asset(item,kind):
        if item.startswith('minecraft:'):return
        namespace,name=item.split(':',1)
        path=f'assets/{namespace}/{kind}/{name}.json'
        if path not in entries:raise AssertionError('Missing native asset: '+path)
    source=(ROOT/'src/main/java/ua/empirecraft/worldrules/RulesConfig.java').read_text()
    plant_source,tree_source=source.split("DEFAULT_TREES =",1)
    plants=re.findall(r'"([a-z0-9_]+:[a-z0-9_]+\|[a-z]+)"',plant_source)
    config=tomllib.loads((REPO/'server/config/empirecraft_world_rules-common.toml').read_text())
    trees=re.findall(r'"([a-z0-9_]+:[a-z0-9_]+\|[a-z]+)"',tree_source)
    assert config['plants']==plants and len(plants)==33,'Config/default plant drift'
    assert config['trees']==trees and len(trees)==21,'Config/default tree drift'
    assert len(set(plants+trees))==len(plants)+len(trees),'Duplicate renewal species'
    for plant in plants+trees:asset(plant.split('|')[0],'blockstates')
    assert len([plant for plant in plants if plant.startswith('vinery:')])==8,'All eight Vinery grape bushes must be covered'
    assert not any('salt' in key.lower() or key.startswith('ore') for key in config),'Obsolete ore conversion setting'
    assert config['intervalTicks']==100 and config['chunksPerInterval']==4
    assert config['plantsPerPass']==3 and config['plantCooldownTicks']==1200 and config['treeCooldownTicks']==2400
    market=(ROOT/'src/main/java/ua/empirecraft/worldrules/SeedMarket.java').read_text()
    stock=set(re.findall(r'"([a-z0-9_]+:[a-z0-9_]+)"',market))
    fruits=re.search(r'for\(String fruit:List.of\((.*?)\)\)',market).group(1)
    stock.update('fruitsdelight:'+fruit+'_sapling' for fruit in re.findall(r'"([a-z]+)"',fruits))
    assert not any(item.startswith('meadow:') for item in stock),'Removed mod must not supply market stock'
    for item in stock:asset(item,'models/item')
    resources=ROOT/'src/main/resources/data'
    bong=json.loads((resources/'empirecraft_world_rules/recipes/plain_bong.json').read_text())
    assert bong['conditions']==[{'type':'forge:mod_loaded','modid':'nirvana'}]
    assert 'forge:conditions' not in bong,'Forge 1.20.1 uses conditions, not forge:conditions'
    for item in [*(i['item'] for i in bong['ingredients']),bong['result']['item']]:asset(item,'models/item')
    for item in ['enchanting_table','brewing_stand']:
        recipe=json.loads((resources/f'minecraft/recipes/{item}.json').read_text())
        assert recipe['conditions']==[{'type':'forge:false'}]
    vinery=(REPO/'server/config/vinery.toml').read_text()
    for wrong in ['vinery:taiga_red_grape_seeds','vinery:taiga_white_grape_seeds']:assert wrong not in vinery
    for corrected in ['vinery:taiga_grape_seeds_red','vinery:taiga_grape_seeds_white']:
        assert corrected in vinery;asset(corrected,'models/item')
    rustic=tomllib.loads((REPO/'server/config/rusticdelight-common.toml').read_text())
    assert rustic['enable_potions'] is False
    script=(REPO/'server/kubejs/server_scripts/5_enchantments.js').read_text()
    assert 'ServerEvents.recipes' in script
    assert not re.search(r'(EntityEvents\.tick|ForgeEvents\.\w+)\s*\(',script)
    print(f'PASS assets: {len(plants)} renewal plants, {len(trees)} tree saplings, {len(stock)} market/native item references, bong and recipe/config integrations')

if __name__=='__main__':main()

