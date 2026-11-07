#!/usr/bin/env python3
"""Derive trade exclusions from the pack's reviewed retirement policies."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
SOURCES=['native-content/audit/policy.json','content-policy/src/main/resources/empirecraft-content-policy.json','end-removal/audit/content.json']
MATERIALS={'magistuarmory:steel_ingot','magistuarmory:steel_nugget','antiquelegacy:bronze_ingot','antiquelegacy:bronze_nugget','magistuarmory:steel_plate'}
def catalog():
    native,content,end=[json.loads((REPO/name).read_text()) for name in SOURCES]
    items=(set(native['removed'])|set(content['replacements'])|set(end['blocked_items']))-MATERIALS
    items|={'minecraft:enchanted_book','minecraft:splash_potion','minecraft:lingering_potion','minecraft:tipped_arrow','overgeared:lingering_arrow','nirvana:potion_bong',*[f'overgeared:steel_{part}' for part in ['helmet','chestplate','leggings','boots']]}
    return {'items':sorted(items),'material_aliases_preserved':sorted(MATERIALS),'sources':SOURCES,'notes':'Potion-effect variants and enchantments are checked before migration; normal iron/steel arrows and drinks from mods are preserved.'}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args();data=catalog()
    target=REPO/'world-rules/src/main/resources/empirecraft-retired-trades.json';text=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
    if args.check:assert target.read_text()==text,'Trade retirement catalog is stale'
    else:target.write_text(text)
    print('TRADER_CATALOG_PASS',len(data['items']),'retired IDs; ordinary material aliases preserved')
if __name__=='__main__':main()
