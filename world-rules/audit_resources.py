#!/usr/bin/env python3
"""Inventory static worldgen dependencies and brewing integrations in the installed JARs.

This is a source inventory, not proof that a particular imported map lacks an item.
Runtime trades, scripted loot, biome placement and the existing map require separate review.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

def objects(value):
    if isinstance(value,dict):
        yield value
        for child in value.values():yield from objects(child)
    elif isinstance(value,list):
        for child in value:yield from objects(child)

def outputs(recipe):
    found=set()
    for key in ['result','results','output','outputs']:
        value=recipe.get(key)
        if isinstance(value,str) and ':' in value:found.add(value)
        for obj in objects(value):
            if isinstance(obj.get('item'),str):found.add(obj['item'])
    return found

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('jars',type=Path);p.add_argument('--output',type=Path,default=Path(__file__).parent/'audit/static-resources.json')
    args=p.parse_args()
    reports=[];recipes=set();loot={};generated={}
    for file in sorted(args.jars.rglob('*.jar')):
        try:z=zipfile.ZipFile(file)
        except zipfile.BadZipFile:continue
        report={'jar':file.name,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'recipes':0,'configured_features':0,'worldgen_blocks':[], 'brewing_classes':[], 'ore_blocks':[]}
        blocks=set()
        for name in z.namelist():
            if name.endswith('.class'):
                data=z.read(name)
                if any(ref in data for ref in [b'BrewingStandBlockEntity',b'BrewingRecipeRegistry',b'PotionBrewing']):report['brewing_classes'].append(name)
                continue
            match=re.match(r'assets/([^/]+)/blockstates/([^/]+)\.json$',name)
            if match and ('ore' in match[2] or 'salt' in match[2] or 'limestone'==match[2]):report['ore_blocks'].append(match[1]+':'+match[2])
            if not name.endswith('.json') or not name.startswith('data/'):continue
            try:data=json.loads(z.read(name))
            except (ValueError,UnicodeDecodeError):continue
            if re.match(r'data/[^/]+/recipes/',name):
                report['recipes']+=1;recipes.update(outputs(data))
            if '/worldgen/configured_feature/' in name:
                report['configured_features']+=1
                for obj in objects(data):
                    value=obj.get('Name')
                    if isinstance(value,str) and ':' in value and not value.startswith('minecraft:'):
                        blocks.add(value);generated.setdefault(value,[]).append(file.name+':'+name)
            match=re.match(r'data/([^/]+)/loot_tables/blocks/(.+)\.json$',name)
            if match:
                block=match[1]+':'+match[2]
                loot.setdefault(block,set()).update(obj['name'] for obj in objects(data) if obj.get('type')=='minecraft:item' and isinstance(obj.get('name'),str))
        report['worldgen_blocks']=sorted(blocks);reports.append(report)
    roots=[]
    for block,features in sorted(generated.items()):
        drops=sorted(loot.get(block,set()))
        roots.append({'block':block,'configured_features':sorted(set(features)),'drops':drops,'drops_without_direct_json_recipe':[item for item in drops if item not in recipes]})
    result={'scope':'Static JAR JSON/class references; not a live recipe reachability proof or map scan.',
            'jar_count':len(reports),'recipe_count':sum(r['recipes'] for r in reports),'configured_feature_count':sum(r['configured_features'] for r in reports),
            'jars':reports,'worldgen_roots':roots}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print('Audited',len(reports),'JARs;',result['recipe_count'],'JSON recipes;',result['configured_feature_count'],'configured features;',len(roots),'worldgen block roots')
    print(args.output)

if __name__=='__main__':main()
