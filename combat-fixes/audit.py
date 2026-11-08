#!/usr/bin/env python3
"""Compare actual before/after attributes against the reviewed per-item balance table."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def verify(report):
    before=json.loads((ROOT/'armor-before.json').read_text())
    expected={row['id']:row for row in before['armor']};totals=[]
    for phase in report['results']:
        actual={row['id']:row for row in phase['armor_audit']}
        assert actual.keys()==expected.keys(),'Registered armor changed; review the per-item balance table'
        for id,old in expected.items():
            current=actual[id];assert current['base']==old['base'] and current['slot']==old['slot'],id+' changed its native forging tier/slot'
            value=old['actual']+old['expected_base_bonus']
            assert abs(current['actual']-value)<.00001,(id,value,current['actual'])
        totals.append(len(actual))
    return {'status':'passed','armor_items_per_boot':totals,'checks':sum(totals)*2,'baseline':'combat-fixes/armor-before.json'}
def main():
    report=json.loads((REPO/'validation/combat-armor-recruits.json').read_text());print(json.dumps(verify(report),indent=2))
if __name__=='__main__':main()
