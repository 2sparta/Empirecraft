#!/usr/bin/env python3
"""Keep tag overlays usable after optional mod content has been removed."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
spec=importlib.util.spec_from_file_location('pack_tag_references',REPO/'end-removal/tag_references.py')
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    audit_path=REPO/'end-removal/audit/content.json';audit=json.loads(audit_path.read_text());changes=[]
    relative=Path('data/minecraft/tags')
    for base in [REPO/'server/kubejs',REPO/'end-removal/src/main/resources']:
        for path in sorted((base/relative).rglob('*.json')):
            before=json.loads(path.read_text());after=dict(before,values=[helper.optional_mod_reference(v) for v in before['values']])
            if args.check:assert before==after,('Required optional mod reference',path)
            elif before!=after:path.write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n');changes.append(str(path.relative_to(REPO)))
            if base.name=='resources':
                kind,*parts=path.relative_to(base/relative).parts
                key=kind+':minecraft:'+'/'.join(parts).removesuffix('.json')
                if key in audit['cleaned_tags']:
                    if args.check:assert audit['cleaned_tags'][key]==after['values'],key
                    else:audit['cleaned_tags'][key]=after['values']
    for path in sorted((REPO/'server/kubejs'/relative).rglob('*.json')):
        target=REPO/'kubejs'/path.relative_to(REPO/'server/kubejs')
        if args.check:assert target.is_file() and target.read_bytes()==path.read_bytes(),target
        elif not target.exists() or target.read_bytes()!=path.read_bytes():
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes());changes.append(str(target.relative_to(REPO)))
    if not args.check:audit_path.write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
    print('PACK_TAG_OVERLAYS_PASS mirrored vanilla overlays with optional mod references; changed=',len(changes))

if __name__=='__main__':main()
