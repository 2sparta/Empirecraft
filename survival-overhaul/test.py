#!/usr/bin/env python3
"""Verify the compiled fork, native resources and embedded locale contracts."""
import hashlib,json,re,zipfile
from pathlib import Path
import build
ROOT=build.ROOT
def main():
    pin=json.loads((ROOT/'sources.json').read_text());source=ROOT/'native-inputs'/pin['input'];candidate=ROOT/'dist'/build.NAME
    assert build.sha(source)==pin['sha256']
    with zipfile.ZipFile(source) as a,zipfile.ZipFile(candidate) as b:
        assert b.testzip() is None
        original={name:a.read(name) for name in a.namelist() if name.endswith('.class')}
        current={name:b.read(name) for name in b.namelist() if name.endswith('.class')}
        assert not original.keys()-current.keys(),'Keep the disabled API compatibility types for optional integrations'
        assert not any(name.startswith(('net/minecraft/','net/minecraftforge/')) for name in current),'No substitute API classes'
        unchanged=[name for name in original if original[name]==current[name]];changed=sorted(name for name in original if original[name]!=current[name]);assert len(unchanged)>len(original)*0.85
        assert all(name.startswith('sfiomn/legendarysurvivaloverhaul/') for name in changed)
        for name in b.namelist():
            assert not(name.startswith('data/') and ('/body_damage/' in name or '/bodydamage/' in name)),name
        overlays=current['ua/empirecraft/survival/SurvivalClient.class']
        assert b'RenderHealthGui' not in overlays and b'RenderBodyDamageGui' not in overlays
        for value in [b'RenderThirstGui',b'RenderTemperatureGui',b'RenderWetnessGui']:assert value in overlays
        langs={key:json.loads(b.read('assets/legendarysurvivaloverhaul/lang/'+key+'.json')) for key in ['en_us','uk_ua','ru_ru']}
        assert set(langs['en_us'])==set(langs['uk_ua'])==set(langs['ru_ru'])
        for key,english in langs['en_us'].items():
            for locale in ['uk_ua','ru_ru']:
                assert sorted(re.findall(r'%(?:[0-9]+\$)?[sd]',english))==sorted(re.findall(r'%(?:[0-9]+\$)?[sd]',langs[locale][key])),(key,locale)
        forbidden={'minecraft:diamond','minecraft:netherite_ingot','minecraft:blaze_rod','minecraft:blaze_powder','minecraft:potion','minecraft:magma_cream','minecraft:chorus_fruit'}
        recipes={name:json.loads(b.read(name)) for name in b.namelist() if name.startswith('data/legendarysurvivaloverhaul/recipes/') and name.endswith('.json')}
        for name,data in recipes.items():
            encoded=json.dumps(data);assert not any('"'+value+'"' in encoded for value in forbidden),name
        hydration=json.loads((ROOT/'audit/hydration.json').read_text());live=json.loads((ROOT/'audit/consumables.json').read_text())
        assert set(hydration)<=set(live),sorted(set(hydration)-set(live))
        assert len(hydration)>=50
        for name,data in hydration.items():
            assert all(0<=row['hydration']<=4 for row in data),name
        report=dict(status='passed',jar_sha256=build.sha(candidate),original_classes=len(original),unchanged_classes=len(unchanged),changed_classes=changed,
            embedded_locale_keys={key:len(value) for key,value in langs.items()},recipes=len(recipes),hydration_profiles=len(hydration),real_api=True)
        path=ROOT/'build/static-checks.json';path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print('SURVIVAL_STATIC_CHECKS_PASS',json.dumps({k:v for k,v in report.items() if k!='changed_classes'}))
if __name__=='__main__':main()
