#!/usr/bin/env python3
"""Read the exact release and installed pack. Never opens or edits a world."""
import argparse, collections, hashlib, json, re, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent

def read_json(raw):
    try:
        return json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        return None

def refs(value):
    if isinstance(value, str): return {value}
    if isinstance(value, list): return set().union(*(refs(v) for v in value)) if value else set()
    if isinstance(value, dict): return refs(list(value.values()))
    return set()

def forbidden(identifier):
    if not identifier.startswith('tfc:'): return False
    path = identifier[4:]
    return (path in {'powderkeg', 'steel_pipe', 'hand_pump', 'windmill_blade', 'lattice_windmill_blade',
                     'rustic_windmill_blade', 'ore/graphite', 'powder/graphite',
                     'ore/cinnabar', 'ore/cryolite', 'ore/halite', 'powder/salt'}
            or path.startswith(('metal/sword/', 'metal/sword_blade/', 'metal/mace/', 'metal/mace_head/',
                                   'metal/fishing_rod/', 'metal/fish_hook/', 'metal/anvil/', 'rock/anvil/',
                                   'wood/axle/', 'wood/encased_axle/', 'wood/clutch/', 'wood/gear_box/',
                                   'wood/water_wheel/', 'wood/windmill/', 'wood/bladed_axle/',
                                   'ore/graphite/', 'ore/cinnabar/', 'ore/cryolite/', 'ore/halite/', 'alabaster/aqueduct/'))
            or '/aqueduct/' in path or path.endswith('_windmill_blade'))

def inventory(jar):
    with zipfile.ZipFile(jar) as z:
        entries = {n: read_json(z.read(n)) for n in z.namelist() if n.endswith('.json')}
    entities, tags, ores = {}, {}, {}
    for name, value in entries.items():
        if value is None: continue
        if '/lang/en_us.json' in name and isinstance(value, dict):
            entities.update({k[7:].replace('.', ':', 1): v for k, v in value.items()
                             if k.startswith('entity.') and k.count('.') == 2})
        if re.match(r'data/forge/tags/items/(ingots|ores)/[^/]+\.json$', name):
            tags[name.split('/items/')[1][:-5]] = value.get('values', [])
        if '/blockstates/' in name and re.search(r'(?:ore|kaolin)', name):
            ns, path = name.split('/blockstates/')
            ores[ns.split('/')[1] + ':' + path[:-5]] = name
    return dict(file=jar.name, sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                entities=entities, metal_tags=tags, ore_blocks=ores), entries

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tfc', type=Path, required=True)
    p.add_argument('--out', type=Path, default=ROOT/'audit')
    a = p.parse_args()
    decision = json.loads((ROOT/'decisions.json').read_text())
    assert hashlib.sha256(a.tfc.read_bytes()).hexdigest() == decision['upstream_sha256'], 'Unexpected TFC binary'
    pack, invalid = [], []
    for jar in sorted((REPO/'mods').glob('*.jar')):
        data, entries = inventory(jar)
        pack.append(data)
        invalid += [jar.name + ':' + n for n,v in entries.items() if v is None and '/recipes/' in n]
    tfc, entries = inventory(a.tfc)
    recipes = {n: d for n,d in entries.items() if '/recipes/' in n and isinstance(d, dict)}
    banned = sorted({r for d in entries.values() if d is not None for r in refs(d) if forbidden(r)})
    reasons = collections.defaultdict(list)
    for n,d in recipes.items():
        if d.get('type') in {'tfc:anvil', 'tfc:welding', 'tfc:collapse', 'tfc:landslide'}: reasons['disabled_system'].append(n)
        if refs(d) & set(banned): reasons['disabled_item'].append(n)
        if n.startswith('data/minecraft/recipes/'): reasons['vanilla_override_review'].append(n)
        if 'gunpowder' in n or 'minecraft:gunpowder' in refs(d): reasons['gunpowder'].append(n)
    overlap = {}
    for identifier, name in tfc['entities'].items():
        leaf = identifier.split(':',1)[1]
        found = [k for mod in pack for k in mod['entities'] if k.split(':',1)[1] in {leaf, 'o_'+leaf, 'forest_'+leaf}]
        if found: overlap[identifier] = sorted(found)
    report = dict(schema=1, decisions=decision, tfc=tfc, installed=pack,
                  exact_name_animal_overlaps=overlap, forbidden_items=banned,
                  affected_recipes=dict(reasons), invalid_installed_recipe_json=invalid,
                  production_world_opened=False, production_world_modified=False)
    a.out.mkdir(parents=True, exist_ok=True)
    (a.out/'inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(f"Audited {len(pack)} client JARs, {len(recipes)} TFC recipes, {len(banned)} forbidden IDs; no world changes")

if __name__ == '__main__': main()
