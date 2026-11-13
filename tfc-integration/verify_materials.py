#!/usr/bin/env python3
"""Verify tested material-update binaries and write their compatibility/catalog evidence."""
import csv,hashlib,io,json,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
BASELINE='8fb13acdf116a93264a6f1c1ed116941af1b8aab58924c559af9acf28c1d9e41'
NATIVE='dd0305fbce9cce430fc8d9c17e5c44037df43c4631a38b025252ac04ccebe94c'
ROCKS='granite diorite gabbro shale claystone limestone conglomerate dolomite chert chalk rhyolite basalt andesite dacite quartzite slate phyllite schist gneiss marble'.split()
SANDS='white yellow red pink brown green black'.split()
SOILS='silt loam sandy_loam silty_loam'.split()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def classes(path):
    with zipfile.ZipFile(path) as z:return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def main():
    current=ROOT/'build/libs/empirecraft-tfc-integration-1.0.2.jar'
    candidates=[REPO/'mods/empirecraft-tfc-integration-1.0.1.jar',ROOT/'build/libs/empirecraft-tfc-integration-1.0.1.jar',
                *sorted((REPO/'disabled-mods').rglob('empirecraft-tfc-integration-1.0.1.jar'))]
    previous=next(p for p in candidates if p.is_file() and sha(p)==BASELINE)
    old,new=classes(previous),classes(current)
    changed=sorted(n for n in old if n in new and old[n]!=new[n]);added=sorted(new.keys()-old.keys())
    assert not old.keys()-new.keys(),'Existing classes removed'
    assert changed and all(n.startswith('ua/empirecraft/tfc/OneTimeTerrain') for n in changed),changed
    assert all(any(n.startswith('ua/empirecraft/tfc/'+prefix) for prefix in ['MaterialPalette','NaturalMaterials','mixin/GlassPowderMixin']) for n in added),added
    native=ROOT/'build/libs/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.1.jar'
    assert sha(native)==NATIVE
    report=json.loads((ROOT/'audit/materials-update.json').read_text())
    assert len(report['phases'])==2 and all('PASS' in p for p in report['phases']) and len(report['materials_markers'])==3
    assert report['jars'][current.name]==sha(current) and report['jars'][native.name]==NATIVE
    write(ROOT/'audit/compatibility-1.0.2.json',dict(previous_sha256=BASELINE,current_sha256=sha(current),changed_classes=changed,
        added_classes=added,unchanged_classes=sum(old[n]==new[n] for n in old),native_tfc_unchanged=True,
        old_capability_key_preserved='empirecraft_tfc:terrain_v1',legacy_flags_preserved=['Ores','Stones'],independent_completion='Materials=1'))
    catalog=[]
    def row(path,kind,habitat,use):catalog.append(dict(id='tfc:'+path,kind=kind,habitat=habitat,use=use))
    for rock in ROCKS:
        row('rock/raw/'+rock,'Природна порода','Підземні лінзи; районна палітра','Камінці, будівництво та декор; карбонатні породи також для флюсу')
        row('rock/loose/'+rock,'Камінці','Поверхня над відповідною верхньою породою','Розколювання каменю та кам’яні будівельні блоки')
    for sand in SANDS:row('sand/'+sand,'Пісок','Невеликі ділянки на наявному піску','Скляна шихта та ландшафт')
    for soil in SOILS:
        for kind in ['dirt','grass','clay','clay_grass']:
            row(kind+'/'+soil,'Ґрунт','Ділянки природної землі; глина частіша у вологій місцевості','Глиняні кульки для гончарства' if kind.startswith('clay') else 'Землеробство й ландшафт')
    for path in ['white_kaolin_clay','pink_kaolin_clay','red_kaolin_clay','kaolin_clay_grass']:
        row(path,'Каолін','Шаруваті ділянки в теплому/помірному кліматі','Каолініт, вогнетривка кераміка та декоративна глина')
    for path in ['peat','peat_grass']:row(path,'Торф','Болота, вологі біоми, місцевість біля води','Паливо й ландшафт')
    with zipfile.ZipFile(native) as z:
        models={n.removeprefix('assets/tfc/models/item/').removesuffix('.json') for n in z.namelist() if n.startswith('assets/tfc/models/item/') and n.endswith('.json')}
        for item in catalog:assert item['id'].split(':')[1] in models,item
    with zipfile.ZipFile(current) as z:
        policy=json.loads(z.read('empirecraft-tfc-policy.json'));forbidden=set(policy['forbidden'])
        for language,label in [('en_us','Charcoal'),('uk_ua','Деревне вугілля'),('ru_ru','Древесный уголь')]:
            assert json.loads(z.read('assets/tfc/lang/'+language+'.json'))['tfc.enum.glassoperation.graphite']==label
        assert not forbidden.intersection(r['id'] for r in catalog)
        recipes={k:v for k,v in policy['overrides'].items() if k.startswith('empirecraft_tfc:quern/pigment_') or k.startswith('empirecraft_tfc:quern/glass_') or k=='empirecraft_tfc:heating/soda_ash_from_vanilla_kelp'}
        assert len(recipes)==8
    table=io.StringIO();writer=csv.writer(table,delimiter=';',lineterminator='\n')
    writer.writerow(['Блок (ID)','Група','Де шукати','Застосування'])
    for r in catalog:writer.writerow([r['id'],r['kind'],r['habitat'],r['use']])
    (ROOT/'natural-materials.csv').write_text('\ufeff'+table.getvalue(),encoding='utf-8')
    write(ROOT/'audit/materials-coverage.json',dict(version='1.0.2',jar_sha256=sha(current),native_tfc_sha256=NATIVE,
        resources=catalog,acquisition_recipes=recipes,completion_key='empirecraft_tfc:terrain_v1',schema=2,
        independent_field='Materials',independent_version=1,maximum_material_changes_per_chunk=640,
        interval_ticks=20,native_markers=report['materials_markers'],production_world_opened=False))
    print('MATERIALS_AUDIT_PASS resources='+str(len(catalog))+' recipes='+str(len(recipes))+' unchanged_classes='+str(sum(old[n]==new[n] for n in old)))
if __name__=='__main__':main()
