#!/usr/bin/env python3
"""Check all installed mods, untouched resources, nested recipes and native patch contracts."""
import json,subprocess,zipfile
from build import *

def main():
    r=json.loads((ROOT/'audit/build.json').read_text());tags={
        'create:only_disabled':['create:encased_fan','create:mechanical_crafter'],
        'test:nested':['#create:only_disabled'], 'test:mixed':['create:encased_fan','minecraft:iron_ingot']}
    assert recipe_reason({'type':'minecraft:crafting_shaped','group':'create:encased_fan','result':{'item':'minecraft:stone'}},tags) is None
    assert recipe_reason({'type':'forge:conditional','recipes':[{'recipe':{'type':'create:mechanical_crafting'}}]},tags)
    assert recipe_reason({'type':'minecraft:crafting_shapeless','ingredients':[{'tag':'test:nested'}]},tags)
    assert recipe_reason({'type':'minecraft:crafting_shapeless','ingredients':[{'tag':'test:mixed'}]},tags) is None
    assert recipe_reason({'type':'create:cutting','ingredients':[{'item':'minecraft:oak_log'}],'results':[{'item':'minecraft:oak_planks'}]},tags) is None
    for typ in ['minecraft:smelting','minecraft:smoking','minecraft:blasting','minecraft:campfire_cooking']:
        assert recipe_reason({'type':typ,'ingredient':{'item':'minecraft:beef'},'result':'minecraft:cooked_beef'},tags) is None
    assert recipe_reason({'type':'create:milling','results':[{'item':'minecraft:iron_nugget'}]},tags)
    assert not forbidden_tag('absent:tag',tags)
    assert data_json(b'{"values":["http://example",/*c*/"minecraft:stone",],}')['values'][0]=='http://example'
    archives=[read_jar(p,scan=True) for p in sorted((REPO/'mods').glob('*.jar'))]
    tags=tags_from(archives)
    inspected=0;malformed=[]
    changed={row['old_file']:row for row in r['jars']}
    for side in ['mods','server/mods']:
        for path in sorted((REPO/side).glob('*.jar')):
            original=read_jar(path,scan=True);row=changed.get(path.name)
            files=read_jar(ROOT/'dist'/row['file'],scan=True) if row else original
            for name,raw in files.items():
                if not recipe_id(name):continue
                try:d=data_json(raw)
                except ValueError:malformed.append(side+'/'+path.name+':'+name);continue
                assert recipe_reason(d,tags) is None,(path,name,recipe_reason(d,tags));inspected+=1
    for row in r['jars']:
        original=read_jar(REPO/row['paths'][0]);patched=read_jar(ROOT/'dist'/row['file'])
        assert sha((ROOT/'dist'/row['file']).read_bytes())==row['sha256']
        assert not any(recipe_id(n) in row['removed_recipes'] for n in patched)
        changed_bytes=[]
        for name,raw in original.items():
            if name not in patched or patched[name]==raw:continue
            changed_bytes.append(name)
            if row['native_patch']:
                assert name.endswith('.class') or name in ['META-INF/mods.toml','META-INF/MANIFEST.MF'],name
            else:raise AssertionError('Unrelated resource rewritten: '+name)
        assert not row['native_patch'] or len([n for n in changed_bytes if n.endswith('.class')])==(4 if row['native_patch']=='wonderoussea' else 1)
    for profile in profiles().values():
        table=loot_table(profile)
        for pool in table['pools']:
            count=pool['entries'][0]['functions'][0]['count']
            assert (count if isinstance(count,int) else count['max'])<=64
    run(['java','com.sun.tools.javac.Main','--release','17','-cp',ROOT/'build/classes','-d',ROOT/'build/classes',ROOT/'tests/CategoryTest.java'])
    run(['java','-cp',ROOT/'build/classes','CategoryTest'])
    r['checks'].update(static_recipe_scan='passed',unrelated_resource_preservation='passed',jei_category_gate='passed',
        inspected_recipe_files=inspected,unchanged_malformed_upstream_recipes=sorted(set(malformed)))
    write(ROOT/'audit/build.json',r)
    print('PASS',inspected,'recipe files; removed',len(r['removed_recipes']),'recipes in',len(r['jars']),'native JARs')
if __name__=='__main__':main()
