#!/usr/bin/env python3
"""Audit every installed server JAR; append only missing native fertility assignments."""
import hashlib
import argparse
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
RES = ROOT / 'src/main/resources'
SEASONS = ['spring', 'summer', 'autumn', 'winter']


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def group(namespace, blocks, items, seasons):
    return dict(blocks=[namespace + ':' + name for name in blocks.split()],
                items=[namespace + ':' + name for name in items.split()], seasons=seasons.split())


GROUPS = [
    group('dumplings_delight', 'chinese_cabbages', 'chinese_cabbage_seeds', 'spring autumn winter'),
    group('dumplings_delight', 'eggplant', 'eggplant_seeds', 'summer autumn'),
    group('dumplings_delight', 'fennel', 'fennel_seeds', 'spring summer autumn'),
    group('dumplings_delight', 'garlic', 'garlic_clove', 'spring autumn'),
    group('dumplings_delight', 'garlic_chive', 'garlic_chive_seeds', 'spring summer autumn'),
    group('dumplings_delight', 'greenonion', 'greenonion', 'spring autumn'),
    group('farmersdelight', 'red_mushroom_colony brown_mushroom_colony', '', 'spring summer autumn winter'),
    group('farmersdelight', 'wild_beetroots', '', 'autumn'),
    group('farmersdelight', 'wild_cabbages', '', 'autumn winter'),
    group('farmersdelight', 'wild_carrots', '', 'spring autumn'),
    group('farmersdelight', 'wild_onions', '', 'spring autumn'),
    group('farmersdelight', 'wild_potatoes', '', 'spring'),
    group('farmersdelight', 'wild_rice wild_tomatoes', '', 'summer autumn'),
    group('farmersrespite', 'tea_bush small_tea_bush wild_tea_bush', 'tea_seeds wild_tea_bush', 'spring summer'),
    group('farmersrespite', 'coffee_bush coffee_bush_top coffee_stem coffee_stem_middle coffee_stem_double wild_coffee_bush', 'coffee_beans', 'summer autumn'),
    group('farmersrespite', 'wither_roots wither_roots_plant', '', 'spring summer autumn winter'),
    group('caupona', 'fig_sapling fig_leaves fig_fruits', 'fig_sapling', 'summer autumn'),
    group('caupona', 'walnut_sapling walnut_leaves walnut_fruits', 'walnut_sapling', 'autumn'),
    group('caupona', 'wolfberry_sapling wolfberry_leaves wolfberry_fruits', 'wolfberry_sapling', 'summer autumn'),
    group('caupona', 'silphium_block', 'silphium', 'spring summer'),
    group('ecologics', 'coconut_seedling coconut_leaves hanging_coconut', 'coconut_seedling coconut', 'summer autumn'),
    group('ecologics', 'walnut_sapling', 'walnut_sapling', 'spring autumn'),
    group('ecologics', 'prickly_pear', 'prickly_pear', 'summer'),
    group('fruitsdelight', 'wild_pineapple hamimelon', '', 'summer'),
    group('vinery', 'apple_tree_sapling apple_leaves', 'apple_tree_sapling', 'summer autumn'),
    group('vinery', 'dark_cherry_sapling dark_cherry_leaves', 'dark_cherry_sapling', 'spring summer'),
    group('vinery', 'red_grape_bush white_grape_bush taiga_grape_bush_red taiga_grape_bush_white savanna_grape_bush_red savanna_grape_bush_white jungle_grape_bush_red jungle_grape_bush_white grapevine_stem empty_red_vine grapevine_leaves', 'red_grape_seeds white_grape_seeds taiga_grape_seeds_red taiga_grape_seeds_white savanna_grape_seeds_red savanna_grape_seeds_white jungle_grape_seeds_red jungle_grape_seeds_white', 'summer autumn'),
    group('medieval_boomsticks', 'arnica_crop_block comfrey_crop_block lemon_balm_crop_block sage_crop_block yarrow_crop_block yellow_wood_sorrel_crop_block', 'arnica comfrey lemon_balm sage yarrow yellow_wood_sorrel', 'spring summer'),
    group('nirvana', 'wild_hemp', '', 'spring summer'),
    group('rusticdelight', 'wild_bell_peppers wild_coffee', '', 'summer autumn'),
    group('rusticdelight', 'wild_cotton', '', 'spring summer'),
    group('spawn', 'date_sapling date_leaves dangling_date_leaves', 'date_seed', 'summer autumn'),
    group('spawn', 'sunflower sunflower_plant', 'sunflower_seeds', 'summer autumn'),
    group('spawn', 'tropical_wildflowers', 'tropical_wildflowers', 'summer'),
    group('spawn', 'potted_sweet_berry_bush', '', 'spring winter'),
    group('spawn', 'red_algae red_algae_carpet hazy_algae_block', 'red_algae', 'spring summer autumn winter'),
    group('spawn', 'spring_dragon_reeds', 'spring_dragon_reeds', 'spring'),
    group('spawn', 'summer_dragon_reeds', 'summer_dragon_reeds', 'summer'),
    group('spawn', 'fall_dragon_reeds', 'fall_dragon_reeds', 'autumn'),
    group('spawn', 'winter_dragon_reeds', 'winter_dragon_reeds', 'winter'),
    group('supplementaries', 'wild_flax', '', 'spring'),
    # Optional compatibility only: the repository still contains Meadow, but it may be removed.
    group('meadow', 'pine_sapling', 'pine_sapling', 'spring summer autumn winter'),
]

EXCLUDED_AGE = {
    'brewinandchewin:unripe_flaxen_cheese_wheel': 'Food maturation; not a plant',
    'brewinandchewin:unripe_scarlet_cheese_wheel': 'Food maturation; not a plant',
    'caupona:snail_block': 'Animal food-production block; not a plant',
    'ecologics:thin_ice': 'Ice melting; not plant growth',
    'vinery:grapevine_pot': 'Grape pressing vessel; not a growing vine',
    'naturalist:chrysalis': 'Insect metamorphosis; existing animals are not killed by the calendar',
    'spawn:moray_crevice': 'Animal habitat block; not a plant',
    'supplementaries:lumisene': 'Fuel; not a plant',
    'meadow:fire_log': 'Fuel combustion; not a plant',
    'trmt:eroded_dirt': 'Soil erosion; not plant growth',
    'trmt:eroded_grass_block': 'Soil erosion; not plant growth',
    'trmt:eroded_sand': 'Soil erosion; not plant growth',
}

PROFILES = {
    'unchanged': [1] * 12,
    'farm': [0.8, 0.8, 1.2, 1.3, 1.3, 1.2, 1.1, 1.1, 1.1, 1, 0.9, 0.8],
    'temperate': [0.5, 0.6, 1.4, 1.6, 1.5, 1.2, 1, 1.2, 1.5, 1.6, 1, 0.6],
    'bear': [1.2, 3, 3, 1.2, 0.8, 0.6, 0.6, 0.8, 1.2, 1.5, 1.4, 1.2],
    'cold': [2, 2, 1.4, 1, 0.8, 0.6, 0.5, 0.6, 1, 1.4, 1.8, 2],
    'insect': [0, 0, 0.3, 1.2, 2, 2.5, 2.5, 2, 1.2, 0.4, 0.1, 0],
    'reptile': [0, 0, 0.2, 0.7, 1.4, 2, 2, 1.7, 1, 0.4, 0.1, 0],
    'wetland': [0.1, 0.1, 0.8, 2, 2, 1.6, 1.4, 1.3, 1.1, 0.7, 0.2, 0.1],
    'migrant_bird': [0.2, 0.2, 1.6, 2, 2, 1.6, 1.4, 1.2, 1.5, 1, 0.3, 0.2],
    'winter_bird': [2, 2, 1.5, 1, 0.8, 0.7, 0.7, 0.8, 1.1, 1.5, 1.8, 2],
    'tropical': [0.6, 0.6, 0.8, 1, 1.4, 1.8, 1.8, 1.6, 1.2, 1, 0.8, 0.6],
    'fish': [0.6, 0.7, 1.1, 1.4, 1.5, 1.6, 1.5, 1.4, 1.3, 1.1, 0.8, 0.6],
    'marine_migration': [0.8, 1, 1.8, 2, 1.4, 1, 0.8, 0.9, 1.4, 2, 1.8, 1],
    'night_flyer': [0.25, 0.25, 0.6, 1.1, 1.5, 1.8, 1.8, 1.6, 1.2, 0.8, 0.4, 0.25],
}


def fauna_profile(entity):
    namespace, name = entity.split(':', 1)
    if namespace not in {'minecraft', 'naturalist', 'spawn', 'ecologics', 'dragnlivestock', 'horse_colors', 'meadow', 'vinery', 'wonderoussea'}:
        return 'unchanged'
    if any(word in name for word in ['corpse', 'boat', 'wagon', 'cart', 'projectile', 'tail', 'bubble', 'seed', 'stranded', 'barbed', 'firekeeper', 'horseman', 'harbinger', 'ancient_mother', 'winemaker', 'plow', 'mower', 'cabriolet', 'coupe', 'sleigh', 'sled']):
        return 'unchanged'
    if 'polar_bear' in name: return 'cold'
    if 'bear' in name: return 'bear'
    if any(word in name for word in ['penguin', 'seal', 'caribou', 'narwhal', 'beluga', 'bowhead']): return 'cold'
    if any(word in name for word in ['bee', 'butterfly', 'firefly', 'dragonfly', 'caterpillar', 'stickbug', 'grub']) or name == 'ant': return 'insect'
    if any(word in name for word in ['snake', 'lizard', 'iguana', 'alligator', 'tortoise', 'turtle']): return 'reptile'
    if any(word in name for word in ['frog', 'snail']): return 'wetland'
    if any(word in name for word in ['cardinal', 'bluejay']): return 'winter_bird'
    if any(word in name for word in ['canary', 'finch', 'sparrow', 'robin', 'duck', 'booby', 'dodo', 'parrot', 'vulture']): return 'migrant_bird'
    if any(word in name for word in ['elephant', 'giraffe', 'hippo', 'lion', 'rhino', 'zebra', 'ostrich', 'camel']): return 'tropical'
    if any(word in name for word in ['whale', 'dolphin', 'porpoise', 'otter', 'sea_cow']): return 'marine_migration'
    if any(word in name for word in ['fish', 'salmon', 'cod', 'bass', 'krill', 'glaucus', 'pyrosome', 'clam', 'crab', 'seahorse', 'tuna', 'herring', 'blenny', 'barracuda', 'flatworm', 'squid', 'axolotl']): return 'fish'
    if 'bat' in name: return 'night_flyer'
    if any(word in name for word in ['cow', 'moobloom', 'sheep', 'pig', 'chicken', 'goat', 'horse', 'donkey', 'mule', 'llama', 'buffalo', 'cattle', 'unicorn']): return 'farm'
    return 'temperate'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jars', type=Path, default=REPO/'mods')
    args = parser.parse_args()
    inventory = json.loads((ROOT / 'installed-mods.json').read_text())
    assets = {'blocks': set(), 'items': set()}
    native = {kind: {s: set() for s in SEASONS} for kind in assets}
    raw_tags = {}
    age_blocks, entities, hashes = {}, set(), {}
    for row in inventory:
        jar = args.jars / row['name']
        data = jar.read_bytes()
        assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == row['sha'], jar
        hashes[jar.name] = hashlib.sha256(data).hexdigest()
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                match = re.match(r'assets/([^/]+)/(blockstates|models/item)/([^/]+)\.json$', name)
                if match:
                    kind = 'blocks' if match[2] == 'blockstates' else 'items'
                    key = match[1] + ':' + match[3]
                    assets[kind].add(key)
                    if kind == 'blocks':
                        value = json.loads(archive.read(name))
                        props = {part.split('=')[0] for variant in value.get('variants', {}) for part in variant.split(',') if '=' in part}
                        for part in value.get('multipart', []): props.update(part.get('when', {}))
                        if any(p.startswith(('age', 'stage', 'growth', 'bloom')) for p in props): age_blocks[key] = sorted(props)
                match = re.match(r'data/sereneseasons/tags/(blocks|items)/([^/]+)\.json$', name)
                if match:
                    value = json.loads(archive.read(name))
                    key = (match[1], match[2])
                    if value.get('replace'): raw_tags[key] = []
                    raw_tags.setdefault(key, []).extend(value.get('values', []))
                if name.endswith('/lang/en_us.json'):
                    try: value = json.loads(archive.read(name))
                    except ValueError: continue
                    entities.update(':'.join(key.split('.')[1:]) for key in value if re.fullmatch(r'entity\.[^.]+\.[^.]+', key))
    def resolve(kind, tag, visited=None):
        visited = set() if visited is None else set(visited)
        if tag in visited: return set()
        visited.add(tag)
        result = set()
        for value in raw_tags.get((kind, tag), []):
            value = value if isinstance(value, str) else value['id']
            if value.startswith('#sereneseasons:'): result.update(resolve(kind, value.split(':')[1], visited))
            elif not value.startswith('#'): result.add(value)
        return result
    for kind in assets:
        for season in SEASONS: native[kind][season] = resolve(kind, season + '_crops')
    known = {kind: set().union(*native[kind].values()) for kind in assets}
    added = {kind: {s: set() for s in SEASONS} for kind in assets}
    for definition in GROUPS:
        for kind in assets:
            for key in definition[kind]:
                assert key in assets[kind], f'Unknown {kind}: {key}'
                if key in known[kind]: continue
                for season in definition['seasons']: added[kind][season].add(key)
    combined = {kind: {s: native[kind][s] | added[kind][s] for s in SEASONS} for kind in assets}
    all_blocks = set().union(*combined['blocks'].values())
    unclassified = set(age_blocks) - all_blocks - set(EXCLUDED_AGE)
    assert not unclassified, f'Unclassified growth blocks: {sorted(unclassified)}'
    # Also check every naturally renewed crop/tree, including mature wild plants without age properties.
    renewal = (REPO / 'world-rules/src/main/java/ua/empirecraft/worldrules/RulesConfig.java').read_text()
    renewal_ids = set(re.findall(r'"([a-z0-9_]+:[a-z0-9_]+)\|', renewal))
    assert renewal_ids <= all_blocks, f'Unseasoned wilderness plants: {renewal_ids - all_blocks}'
    for kind in assets:
        for season in SEASONS:
            write(RES / f'data/sereneseasons/tags/{kind}/{season}_crops.json', {
                'replace': False, 'values': [{'id': key, 'required': False} for key in sorted(added[kind][season])]})
    for key in ['cow', 'sheep', 'pig', 'chicken', 'horse', 'donkey', 'mule', 'llama', 'goat', 'rabbit', 'fox', 'wolf', 'cat', 'ocelot', 'panda', 'polar_bear', 'bee', 'frog', 'turtle', 'dolphin', 'squid', 'glow_squid', 'bat', 'axolotl', 'cod', 'salmon', 'pufferfish', 'tropical_fish', 'parrot', 'camel', 'mooshroom']:
        entities.add('minecraft:' + key)
    fauna = {entity: fauna_profile(entity) for entity in sorted(entities)}
    write(RES / 'empirecraft-fauna.json', {'profiles': PROFILES, 'entities': fauna})
    write(ROOT / 'audit/coverage.json', {
        'scope': 'All installed server JAR assets, native Serene Seasons tags, age/stage states and explicitly reviewed plants; runtime registry report is written on startup.',
        'jar_count': len(inventory), 'jar_sha256': hashes,
        'plants': {key: {'seasons': [s for s in SEASONS if key in combined['blocks'][s]], 'source': 'native' if key in known['blocks'] else 'added'} for key in sorted(all_blocks)},
        'age_blocks': age_blocks, 'non_plant_age_states': EXCLUDED_AGE,
        'renewal_plants_checked': sorted(renewal_ids), 'fauna_profiles': fauna,
        'new_block_assignments': len(set().union(*added['blocks'].values())),
        'new_item_assignments': len(set().union(*added['items'].values())),
    })
    print(f'PASS {len(inventory)} installed JARs, {len(all_blocks)} seasonal blocks, {len(renewal_ids)} renewal plants, {len(fauna)} entity profiles')


if __name__ == '__main__':
    main()
