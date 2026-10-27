#!/usr/bin/env python3
"""Check semantic recipe changes, preservation boundaries, native archives and real Rhino."""
import argparse, copy, json, os, subprocess, zipfile
from pathlib import Path
from audit import ROOT, REPO, recipe, load_json, expand, strings
from build import NAMES, MOD
from policy import *

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--rhino',type=Path,required=True);parser.add_argument('--classpath-dir',action='append',type=Path);args=parser.parse_args()
    report=json.loads((ROOT/'audit/content.json').read_text());policy=json.loads((ROOT/'src/main/resources/no-end.json').read_text());blocked=set(policy['replacements']);tags=policy['ingredient_tags']
    assert not any('end_table' in identity for identity in blocked)
    for identity in ['minecraft:glowstone','minecraft:obsidian','chipped:nether_engraved_glowstone','immersive_melodies:ender_bass','mcwfurnitures:oak_end_table']:
        assert not end_id(identity) and identity not in blocked
    assert load_json(b'{"url":"https://example.test/a//b",// comment\n"values":["x",],}')=={'url':'https://example.test/a//b','values':['x']}
    assert expand('a',{'a':['#b','minecraft:iron_ingot'],'b':['#a',PEARL]})=={PEARL,'minecraft:iron_ingot'}
    cases=[]
    def add(name,source,expected):cases.append({'id':'test:'+name,'source':source,'expected':expected})
    def craft(ingredients,result='minecraft:stone'):return {'type':'minecraft:crafting_shapeless','ingredients':ingredients,'result':{'item':result}}
    add('direct',craft([{'item':PEARL}]),craft([{'item':PEARL_REPLACEMENT}]))
    add('tag',craft([{'tag':'forge:ender_pearls'}]),craft([[{'item':PEARL_REPLACEMENT}]]))
    # Ingredient alternatives are flattened inside an ingredient's array, not across recipe slots.
    add('alternatives',craft([[{'item':'minecraft:chorus_fruit'},{'item':'minecraft:iron_ingot'}]]),craft([[{'item':'minecraft:iron_ingot'}]]))
    add('retired_output',craft([{'item':'minecraft:iron_ingot'}],'minecraft:ender_chest'),None)
    add('pearl_output',craft([{'item':'minecraft:iron_ingot'}],PEARL),None)
    add('required_chorus',craft([{'item':'minecraft:chorus_fruit'},{'item':'minecraft:iron_ingot'}]),None)
    add('contact',craft([{'item':'minecraft:paper'},{'item':PEARL}],'contact:ender_wrapping_paper'),None)
    add('diamond_knife',craft([{'item':'minecraft:diamond'}],'farmersdelight:diamond_knife'),None)
    add('netherite_knife',{'type':'minecraft:smithing_transform','base':{'item':'farmersdelight:diamond_knife'},'addition':{'item':'minecraft:netherite_ingot'},'result':{'item':'farmersdelight:netherite_knife'}},None)
    add('unrelated',craft([{'item':'minecraft:iron_ingot'},{'item':'minecraft:stick'}],'farmersdelight:iron_knife'),craft([{'item':'minecraft:iron_ingot'},{'item':'minecraft:stick'}],'farmersdelight:iron_knife'))
    nbt={'type':'forge:partial_nbt','item':PEARL,'nbt':{'CustomModelData':7}}
    changed=dict(nbt,item=PEARL_REPLACEMENT);add('nbt',craft([nbt]),craft([changed]))
    add('multiple_result',{'type':'test:processing','ingredients':[{'item':PEARL}],'results':[{'item':'minecraft:chorus_fruit'},{'item':'minecraft:iron_ingot'}]},None)
    add('tool_list',{'type':'overgeared:item_to_tooltype','item':[{'item':'farmersdelight:diamond_knife'},{'item':'farmersdelight:iron_knife'}]}, {'type':'overgeared:item_to_tooltype','item':[{'item':'farmersdelight:iron_knife'}]})
    add('empty_tool_list',{'type':'overgeared:item_to_tooltype','item':[{'item':'farmersdelight:diamond_knife'}]},None)
    add('live_mixed_tag',craft([{'tag':'test:mixed'}]),craft([[{'item':'minecraft:iron_ingot'}]]))
    add('live_end_tag',craft([{'tag':'test:end_only'}]),None)
    add('free_bass',craft([{'item':PEARL}],'immersive_melodies:ender_bass'),craft([{'item':PEARL_REPLACEMENT}],'immersive_melodies:ender_bass'))
    add('required_slot_and_alternatives',craft([[{'tag':'test:many'}],{'item':'minecraft:stick'}]),craft([[{'item':'minecraft:gold_ingot'},{'item':'minecraft:iron_ingot'}],{'item':'minecraft:stick'}]))
    add('shaped_tag',{'type':'minecraft:crafting_shaped','pattern':['AP'],'key':{'A':{'tag':'forge:ender_pearls'},'P':[{'item':'minecraft:chorus_fruit'},{'item':'minecraft:stone'}]},'result':{'item':'minecraft:stone'}},{'type':'minecraft:crafting_shaped','pattern':['AP'],'key':{'A':[{'item':PEARL_REPLACEMENT}],'P':[{'item':'minecraft:stone'}]},'result':{'item':'minecraft:stone'}})
    live={'test:mixed':['minecraft:iron_ingot','minecraft:chorus_fruit'],'test:end_only':['minecraft:chorus_fruit'],'test:many':['minecraft:iron_ingot','minecraft:gold_ingot','minecraft:chorus_fruit']}
    static_tags={**tags,**live}
    for case in cases:assert recipe(copy.deepcopy(case['source']),blocked,static_tags)==case['expected'],case['id']
    fixture=ROOT/'build/recipe-fixtures.json';fixture.write_text(json.dumps({'cases':cases,'live_tags':live}))
    classes=ROOT/'build/recipe-tests';classes.mkdir(exist_ok=True)
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',str(args.rhino),'-d',str(classes),str(ROOT/'tests/RecipeRhinoTest.java')],check=True)
    jars=[p for folder in (args.classpath_dir or [REPO/'server/libraries']) for p in folder.rglob('*.jar')]
    subprocess.run(['java','-cp',os.pathsep.join(map(str,[classes,args.rhino,*jars])),'RecipeRhinoTest',str(fixture),str(ROOT/'tests/recipe-fixture.js'),str(REPO/'server/kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js')],check=True)
    for source,target in NAMES.items():
        assert (REPO/'mods'/target).read_bytes()==(REPO/'server/mods'/target).read_bytes()
        with zipfile.ZipFile(REPO/'mods'/target) as archive:
            assert archive.testzip() is None
            retired=set(json.loads(archive.read('META-INF/empirecraft-no-end.json'))['removed'])
            for name in archive.namelist():
                if '/recipes/' in name and name.endswith('.json'):
                    try:assert not strings(load_json(archive.read(name)))&retired,name
                    except ValueError:pass
    assert (REPO/'mods'/MOD).read_bytes()==(REPO/'server/mods'/MOD).read_bytes()
    assert set(report['pearl_recipes'])=={'dawnoftimebuilder:water_source_trickle','immersive_melodies:ender_bass'}
    assert {'farmersdelight:diamond_knife','farmersdelight:netherite_knife_smithing','minecraft:ender_eye'}<=set(report['disabled_recipes'])
    print('PASS End audit, '+str(len(cases))+' recipe scenarios, preservation boundaries, native resource removal and identical client/server jars')

if __name__=='__main__':main()
