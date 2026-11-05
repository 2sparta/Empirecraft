#!/usr/bin/env python3
"""Check the exact uploaded artifacts, their dependencies and active pack integration."""
import hashlib, json, re, tomllib, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
EXPECTED={'handcrafted':('handcrafted-forge-1.20.1-3.0.6.jar','1da3c7c524bfb656bb370757af55f08f1678e8e1cb934e1c4c04aa773ed64a79'),
          'timber_joinery':('timber_joinery-1.1.0.jar','102565d4dc1e1d2a027ea0d904fdfe3faa05802fa5be154aac06868cad1c275a')}

def main():
    lock=json.loads((ROOT/'modpack-lock.json').read_text());rows={m['id']:m for m in lock['mods']}
    retired={m['id'] for m in json.loads((ROOT/'furniture-replacement/removed-mods.json').read_text())}
    assert len(retired)==7 and not retired.intersection(rows)
    for folder in ['mods','server/mods']:
        assert not list((ROOT/folder).glob('mcw*.jar*'))
        for mid,(name,digest) in EXPECTED.items():
            path=ROOT/folder/name;raw=path.read_bytes()
            assert hashlib.sha256(raw).hexdigest()==digest==rows[mid]['sha256']
            assert set(rows[mid]['sides'])=={'client','server'}
        # No retained mod may require a removed Macaw ID.
        for jar in (ROOT/folder).glob('*.jar'):
            with zipfile.ZipFile(jar) as z:
                if 'META-INF/mods.toml' not in z.namelist():continue
                data=tomllib.loads(z.read('META-INF/mods.toml').decode())
                for deps in data.get('dependencies',{}).values():
                    assert not any(d.get('mandatory') and d['modId'] in retired for d in deps),jar.name
    assert tuple(map(int,rows['resourcefullib']['version'].split('.'))) >= (2,1,1)
    mass=json.loads((ROOT/'block-mass/catalog.json').read_text())
    ids={bid for values in mass.values() for bid in values}
    assets=json.loads((ROOT/'furniture-replacement/expected-block-assets.json').read_text())
    assert sum(map(len,assets.values()))==412
    assert all(set(values)<=ids for values in assets.values())
    assert not any(bid.split(':')[0] in retired for bid in ids)
    for folder in ['kubejs','server/kubejs']:
        for path in (ROOT/folder/'data').rglob('*.json'):
            if '/tags/' not in path.as_posix():continue
            values=json.loads(path.read_text()).get('values',[])
            assert not any((v.get('id','') if isinstance(v,dict) else v).lstrip('#').split(':')[0] in retired for v in values),path
    configs=[]
    for name in ['server/defaultconfigs/locks-server.toml','server/world/serverconfig/locks-server.toml','server/world_europe/serverconfig/locks-server.toml']:
        path=ROOT/name
        if not path.exists():continue  # Sparse CI omits world directories; shipping package includes these files.
        config=tomllib.loads(path.read_text());patterns=config['Lockable Blocks'];configs.append(patterns)
        assert not any('mcw' in p for p in patterns)
        for bid in ['handcrafted:oak_cupboard','handcrafted:oak_drawer','timber_joinery:oak_cabinet','timber_joinery:oak_shutter']:
            assert any(re.fullmatch(pattern,bid) for pattern in patterns),bid
    assert configs and all(config==configs[0] for config in configs)
    with zipfile.ZipFile(ROOT/'translations/dist/Empirecraft-Translations-uk-ru-1.1.0.zip') as z:
        assert not any(name.startswith('assets/mcw') for name in z.namelist())
    for mid,(name,_) in EXPECTED.items():
        with zipfile.ZipFile(ROOT/'mods'/name) as z:
            english=json.loads(z.read(f'assets/{mid}/lang/en_us.json'))
            for locale in ['uk_ua','ru_ru']:
                source=f'assets/{mid}/lang/{locale}.json'
                native=json.loads(z.read(source)) if source in z.namelist() else {}
                overlay=json.loads((ROOT/'translations/pack'/source).read_text())
                assert set(english)<=set(native|overlay),(mid,locale)
    print('PASS exact uploaded JARs on both sides, seven retired mods, mandatory dependencies, 412 mass assignments, active tags, Locks presets and UK/RU coverage')

if __name__=='__main__':main()
