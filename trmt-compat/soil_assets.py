"""Generate source-specific path geometry using textures from installed mods, without copied art."""
import json
import re
import zipfile

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
        top = None
        tint = None
        if namespace == 'tfc' and grass:
            soil = json.loads(assets[f'assets/tfc/models/block/{path}/bottom.json'])['textures']['texture']
            top = 'tfc:block/grass_top'; tint = 1
        elif namespace == 'empirecraft_tfc' and grass:
            model = json.loads(assets[f'assets/{namespace}/models/block/{path}.json'])['textures']
            soil, top = model['bottom'], model['top']
        key = 'remembered/'+namespace+'/'+path
        variants = {}
        for stage in range(5):
            model_id = f'trmt:block/{key}/{stage}'
            for facing, angle in [('north',0),('east',90),('south',180),('west',270)]:
                variants[f'wear={stage},facing={facing}'] = dict(model=model_id,y=angle,uvlock=True)
            def cube(x1,y1,z1,x2,y2,z2,up,up_tint=None):
                faces={side:dict(texture='#soil') for side in ['down','north','south','east','west']}
                faces['up']=dict(texture=up)
                if up_tint is not None: faces['up']['tintindex']=up_tint
                return dict(**{'from':[x1,y1,z1],'to':[x2,y2,z2]},faces=faces)
            # Two shallow wheel/foot tracks widen with wear. Raised edges retain the actual soil/grass top.
            widths=[1.5,2.5,3.5,5.5,7.5]; width=widths[stage]
            a,b,c,d=4-width/2,4+width/2,12-width/2,12+width/2
            elements=[cube(0,0,0,16,15,16,'#soil')]
            for left,right in [(0,a),(b,c),(d,16)]:
                if right>left:elements.append(cube(left,15,0,right,16,16,'#top',tint))
            textures={'soil':soil,'top':top or soil,'particle':soil}
            put('assets/trmt/models/block/'+key+f'/{stage}.json',dict(parent='minecraft:block/block',textures=textures,elements=elements))
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
