"""Generate source-specific path geometry using textures from installed mods, without copied art."""
from hashlib import sha256
from itertools import groupby
import json
import re
import zipfile

# Keep existing specks in place as wear increases.
WEAR_PIXELS = (18, 42, 78, 132, 192)
PIXEL_ORDER = sorted(
    ((x, z) for z in range(16) for x in range(16)),
    key=lambda pixel: sha256(bytes(pixel)).digest(),
)


def generate(mods):
    assets = {}
    for pattern in ('TerraFirmaCraft*.jar', 'empirecraft-tfc-integration-*.jar'):
        paths = list(mods.glob(pattern)); assert len(paths) == 1, paths
        with zipfile.ZipFile(paths[0]) as z:
            assets.update({n: z.read(n) for n in z.namelist() if n.startswith('assets/') and n.endswith('.json')})
    sources = []
    for name in sorted(assets):
        m = re.fullmatch(r'assets/(tfc|empirecraft_tfc)/blockstates/(.+)\.json', name)
        if not m: continue
        namespace, path = m.groups()
        if not (path.startswith(('grass/', 'clay_grass/', 'dirt/', 'rooted_dirt/', 'clay/', 'sand/')) or path in ('peat','peat_grass','kaolin_clay_grass','red_kaolin_clay','white_kaolin_clay','pink_kaolin_clay')): continue
        kind = 'sand' if path.startswith('sand/') else ('grass' if 'grass' in path else 'dirt')
        sources.append(dict(source=f'{namespace}:{path}', kind=kind))
    # Native red sand and clay were also omitted by the original category checks.
    sources.extend([dict(source='minecraft:red_sand', kind='sand'), dict(source='minecraft:clay', kind='dirt')])
    out = {}
    def put(name, value): out[name] = (json.dumps(value, ensure_ascii=False, separators=(',', ':'))+'\n').encode()
    names = {'en_us':{}, 'uk_ua':{}}
    for row in sources:
        namespace, path = row['source'].split(':'); grass = row['kind'] == 'grass'
        original = f'{namespace}:block/{path}'
        soil = original
        side = None
        top = None
        tint = None
        if namespace == 'tfc' and grass:
            soil = json.loads(assets[f'assets/tfc/models/block/{path}/bottom.json'])['textures']['texture']
            top = 'tfc:block/grass_top'; tint = 1
        elif namespace == 'empirecraft_tfc' and grass:
            model = json.loads(assets[f'assets/{namespace}/models/block/{path}.json'])['textures']
            soil, top, side = model['bottom'], model['top'], model['side']
        key = 'remembered/'+namespace+'/'+path
        textures = {'soil': soil, 'side': side or soil, 'top': top or soil, 'particle': soil}
        sides = {face: dict(texture='#side', cullface=face)
                 for face in ('north', 'south', 'east', 'west')}
        sides['down'] = dict(texture='#soil', cullface='down')
        variants = {}
        for stage, pixel_count in enumerate(WEAR_PIXELS):
            height = (16, 14, 14, 12, 10)[stage] if row['kind'] == 'sand' else 16
            variants[f'wear={stage}'] = {'model': f'trmt:block/{key}/{stage}'}
            worn = set(PIXEL_ORDER[:pixel_count])
            elements = [{'from': [0, 0, 0], 'to': [16, height, 16], 'faces': sides}]
            for z in range(16):
                x = 0
                for is_worn, run in groupby((column, z) in worn for column in range(16)):
                    end = x + sum(1 for _ in run)
                    face = dict(texture='#soil' if is_worn else '#top', uv=[x, z, end, z + 1])
                    if height == 16:
                        face['cullface'] = 'up'
                    if is_worn and not grass:
                        face['forge_data'] = {'color': 'ffc8bca9'}
                    if not is_worn and tint is not None:
                        face['tintindex'] = tint
                    elements.append({'from': [x, height, z], 'to': [end, height, z + 1],
                                     'faces': {'up': face}})
                    x = end
            put(f'assets/trmt/models/block/{key}/{stage}.json',
                dict(parent='minecraft:block/block', textures=textures, elements=elements))
        put('assets/trmt/blockstates/'+key+'.json',dict(variants=variants))
        # Same mining classes as the source. These add to, rather than replace, vanilla tag entries.
        name_key='block.trmt.'+key.replace('/','.')
        human=path.replace('/',' · ').replace('_',' ')
        names['en_us'][name_key]='Worn path · '+human
        names['uk_ua'][name_key]='Протоптана стежка · '+human
    for lang, values in names.items(): put('assets/trmt/lang/'+lang+'.json',values)
    for tag in ['mineable/shovel','dirt','sand']:
        values=['trmt:remembered/'+row['source'].replace(':','/') for row in sources if tag=='mineable/shovel' or (row['kind']=='sand')==(tag=='sand')]
        put('data/minecraft/tags/blocks/'+tag+'.json',dict(replace=False,values=values))
    put('data/trmt/remembered_soils.json',sources)
    assert len(sources)==38, len(sources)
    return out
