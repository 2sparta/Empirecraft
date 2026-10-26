#!/usr/bin/env python3
"""Build the pack policy and datapack overrides from the pinned, unmodified mod assets."""
import argparse,hashlib,json,re,zipfile,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
FAMILIES='stylet katzbalger pike ranseur ahlspiess chivalrylance bastardsword estoc claymore zweihander flamebladedsword lochaberaxe concavehalberd heavymace heavywarhammer lucernhammer morgenstern shortsword chainmorgenstern guisarme'.split()
SPAWN_BLOCKS=[] # Spawn now removes these registrations natively.

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')

def main():
    p=argparse.ArgumentParser();p.add_argument('--mods',type=Path,required=True);args=p.parse_args()
    names={'spawn':'spawn-4.0.8-1.20.1-empirecraft.1.jar','knights':'epic-knights-all-in-one-1_20_1-forge (1).jar','boomsticks':'medieval_boomsticks-no_stars (8).jar','overgeared':'overgeared-forge-1.20.1-1.6.36.jar'}
    jars={key:zipfile.ZipFile(args.mods/name) for key,name in names.items()}
    assets=set().union(*(set(j.namelist()) for j in jars.values()))
    def exists(id):
        if id.startswith('minecraft:'):return True
        namespace,name=id.split(':');return f'assets/{namespace}/models/item/{name}.json' in assets
    replacements={'minecraft:diamond_sword':'overgeared:steel_sword','minecraft:netherite_sword':'overgeared:steel_sword',
                  'overgeared:diamond_arrow':'overgeared:steel_arrow'}
    for block in SPAWN_BLOCKS:
        if not exists('spawn:'+block):raise AssertionError(block)
        target='minecraft:stone'
        if 'sandstone' in block:target='minecraft:sandstone'
        if block.endswith('_slab'):target='minecraft:stone_slab'
        if block.endswith('_stairs'):target='minecraft:stone_stairs'
        if block.endswith('_wall'):target='minecraft:cobblestone_wall'
        if block=='blasted_sand':target='minecraft:sand'
        replacements['spawn:'+block]=target
    ores=[]
    for material in ['limonite','pyrite','magnitite']:
        for suffix in ['_ore','_ore_deepslate']:
            key='medieval_boomsticks:'+material+suffix;ores.append(key)
            replacements[key]='minecraft:cobbled_deepslate' if suffix.endswith('deepslate') else 'minecraft:cobblestone'
        replacements['medieval_boomsticks:raw_'+material]='minecraft:iron_nugget' if material=='limonite' else 'minecraft:raw_iron'
    weapons=[]
    for family in FAMILIES:
        for material in ['diamond','netherite']:
            item='magistuarmory:'+material+'_'+family
            if exists(item):replacements[item]='magistuarmory:steel_'+family;weapons.append(item)
    for source,target in replacements.items():
        assert exists(source) and exists(target),(source,target)
    policy=dict(replacements=dict(sorted(replacements.items())),blocks=sorted(['spawn:'+b for b in SPAWN_BLOCKS]+ores),
                entities=[],weapons=sorted(weapons+['minecraft:diamond_sword','minecraft:netherite_sword','overgeared:diamond_arrow']),
                premium={'minecraft:blaze_powder':'minecraft:diamond','minecraft:netherite_upgrade_smithing_template':'overgeared:steel_block'})
    write(ROOT/'src/main/resources/empirecraft-content-policy.json',policy)
    write(ROOT/'audit/policy.json',policy)
    resource=ROOT/'src/main/resources/data'
    if resource.exists():shutil.rmtree(resource)
    disabled=[];premium=[]
    for kind,jar in jars.items():
        for name in jar.namelist():
            if not re.fullmatch(r'data/[^/]+/recipes/.+\.json',name):continue
            try:data=json.loads(jar.read(name))
            except ValueError:continue
            strings=re.findall(r'"([a-z0-9_]+:[a-z0-9_/]+)"',json.dumps(data))
            if any(s in replacements for s in strings):
                disabled_recipe=dict(data);disabled_recipe['conditions']=[{'type':'forge:false'}]
                write(resource/name[5:],disabled_recipe);disabled.append(name)
            elif kind=='knights' and 'minecraft:blaze_powder' in strings:
                premium.append(dict(id=name[5:].replace('/recipes/','/').removesuffix('.json').replace('/',':',1),source=data))
    for material in ['limonite','pyrite','magnitite']:
        write(resource/f'medieval_boomsticks/forge/biome_modifier/add_{material}_ore.json',{'type':'forge:none'})
        write(resource/f'medieval_boomsticks/worldgen/placed_feature/{material}_ore_placed.json',{'feature':'medieval_boomsticks:'+material+'_ore','placement':[{'type':'minecraft:count','count':0}]})
    for name in ['diamond_sword','netherite_sword_smithing']:
        write(resource/f'minecraft/recipes/{name}.json',{'type':'minecraft:crafting_shapeless','conditions':[{'type':'forge:false'}],'ingredients':[{'item':'minecraft:stick'}],'result':{'item':'minecraft:stick'}})
    # Recipe-book discovery must also work without ever obtaining blaze powder or Nether templates.
    premium_ids={row['id'] for row in premium};unlocks=[]
    def replace_premium(value):
        if isinstance(value,str):return policy['premium'].get(value,value)
        if isinstance(value,list):return [replace_premium(v) for v in value]
        if isinstance(value,dict):return {k:replace_premium(v) for k,v in value.items()}
        return value
    for name in jars['knights'].namelist():
        if not re.fullmatch(r'data/[^/]+/advancements/.+\.json',name):continue
        data=json.loads(jars['knights'].read(name))
        if premium_ids.intersection(data.get('rewards',{}).get('recipes',[])):
            changed=replace_premium(data)
            if changed!=data:write(resource/name[5:],changed);unlocks.append(name)
    # KubeJS data has higher priority than mod data; never rely on alphabetical mod-pack order.
    mirrored=REPO/'server/kubejs/data'
    generated=[]
    for source in sorted(resource.rglob('*.json')):
        path=source.relative_to(resource);target=mirrored/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes());generated.append(path.as_posix())
    manifest=ROOT/'audit/generated-data.json'
    if manifest.exists():
        for obsolete in set(json.loads(manifest.read_text()))-set(generated):(mirrored/obsolete).unlink(missing_ok=True)
    write(manifest,generated)
    report=dict(inputs={name:hashlib.sha256((args.mods/name).read_bytes()).hexdigest() for name in names.values()},blocked_items=len(replacements),blocked_blocks=len(policy['blocks']),weapons=len(policy['weapons']),disabled_native_recipes=sorted(disabled),premium_recipes=premium,premium_unlocks=unlocks)
    write(ROOT/'audit/content.json',report)
    blocked=json.dumps(list(policy['replacements']))
    server='// priority: -200\n// Generated policy; the late pass follows the existing Overgeared forging conversion.\nvar EC_REMOVED_ITEMS = '+blocked+'\n'
    server+=(ROOT/'recipe-pass.js').read_text()
    dest=REPO/'server/kubejs/server_scripts/zzzzzz_empirecraft_progression.js';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(server)
    client='// Generated from content-policy/generate.py.\nJEIEvents.hideItems(event => {\n  '+blocked+'.forEach(id => event.hide(id))\n})\n'
    dest=REPO/'kubejs/client_scripts/empirecraft_progression.js';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(client)
    tabs=['minecraft:combat','minecraft:ingredients','minecraft:functional_blocks','minecraft:building_blocks','minecraft:natural_blocks','minecraft:spawn_eggs','spawn:item_group','spawn:spawn_eggs','magistuarmory:weapons','magistuarmory:particular_weapons','overgeared:overgeared_tab','overgeared:lingering_arrows_tab','medieval_boomsticks:medieval_boomsticks']
    startup='// Generated: remove retired IDs and every NBT variant from display and search tabs.\nvar EC_CLIENT_RETIRED = '+blocked+';\n'+json.dumps(tabs)+'.forEach(tab => {\n  StartupEvents.modifyCreativeTab(tab, event => event.remove(Ingredient.of(EC_CLIENT_RETIRED)))\n})\n'
    dest=REPO/'kubejs/startup_scripts/empirecraft_progression_tabs.js';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(startup)
    print('PASS generated policy:',len(replacements),'items;',len(policy['blocks']),'blocks;',len(premium),'premium recipes;',len(disabled),'disabled recipes')

if __name__=='__main__':main()
