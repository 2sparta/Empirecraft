"""Check deliverable identity, preserved exclusions, station tags and bounded guide pages."""
import hashlib,json,sys,tomllib,zipfile
from pathlib import Path
from build import INPUTS
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(REPO/'handbook'));import layout
generated=json.loads((ROOT/'audit/generated.json').read_text());pages=0
for name,digest in generated['files'].items():
    raw=(REPO/name).read_bytes();assert hashlib.sha256(raw).hexdigest()==digest,name
    assert raw==(REPO/'server'/name).read_bytes(),name
    if name.endswith('.json'):
        value=json.loads(raw)
        if '/entries/' in name:
            assert layout.width(value['name'])<=layout.TEXT_WIDTH
            for p in value['pages']:
                assert layout.lines(p['text'])<=layout.MAX_LINES,name
                pages+=1
for name,digest in INPUTS.items():assert hashlib.sha256((ROOT/'upstream'/name).read_bytes()).hexdigest()==digest,name
for folder in [REPO/'mods',REPO/'server/mods']:
    ids={}
    for p in folder.glob('*.jar'):
        with zipfile.ZipFile(p) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            for mod in tomllib.loads(z.read('META-INF/mods.toml').decode()).get('mods',[]):ids.setdefault(mod['modId'],[]).append(p)
    for id in ['farmersrespite','farmersdelight','handson_delight','diegesis']:
        assert len(ids[id])==1,(id,ids[id])
        p=ids[id][0];assert (REPO/'mods'/p.name).read_bytes()==(REPO/'server/mods'/p.name).read_bytes()
    assert '1.3.4' in ids['farmersdelight'][0].name
with zipfile.ZipFile(next((REPO/'mods').glob('farmersrespite*.jar'))) as current,zipfile.ZipFile(ROOT/'upstream/farmersrespite-1.20.1-2.1.2.jar') as source:
    for n in current.namelist():
        if n.endswith('.class') and '/registry/' not in n and n!='umpaz/farmersrespite/common/FRCommonSetup.class' and not n.startswith('ua/empirecraft/'):
            assert current.read(n)==source.read(n),('Respite gameplay changed',n)
for pattern in ['diegesis-*.jar','handson_delight-*.jar']:
    p=next((REPO/'mods').glob(pattern));assert p.read_bytes()==(ROOT/'upstream'/p.name).read_bytes()
    with zipfile.ZipFile(p) as z:
        for n in z.namelist():
            if n.endswith('.class'):assert int.from_bytes(z.read(n)[6:8],'big')<=61,('Requires newer Java',p.name,n)
with zipfile.ZipFile(next((REPO/'mods').glob('*legendary*.jar'))) as z:
    for name in ['green_tea','yellow_tea','black_tea','coffee','rose_hip_tea']:
        data=json.loads(z.read('data/farmersrespite/legendarysurvivaloverhaul/thirst/consumables/'+name+'.json'))
        assert data[0]['hydration']>0,name
ready=json.loads((REPO/'kubejs/data/empirecraft_kitchen/tags/items/brewing_coffee.json').read_text())['values']
assert 'rusticdelight:coffee_beans' not in ready and 'rusticdelight:roasted_coffee_beans' in ready
assert 'remove_crafting_recipes=false' in (REPO/'config/handson_delight.properties').read_text()
result=dict(status='passed',generated_files=len(generated['files']),guide_pages=pages,identical_sides=True,upstream_hands_on_and_diegesis_unchanged=True,respite_existing_gameplay_preserved=True,existing_lso_drinks_verified=True,raw_coffee_requires_roasting=True)
(ROOT/'audit/resources.json').write_text(json.dumps(result,indent=2)+'\n')
print('TEA_RESOURCES_PASS files='+str(len(generated['files']))+' guide_pages='+str(pages))
