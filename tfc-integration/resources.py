"""Data changes shared by the build, runtime recipe guard and the audit."""
import hashlib, json, re, zipfile
from pathlib import Path
from audit import forbidden, refs

ID='empirecraft_tfc'
GUNS=['musketmod:musket','musketmod:blunderbuss','musketmod:pistol',
      'musketmod:musket_with_bayonet','musketmod:musket_with_scope',
      'medieval_boomsticks:arquebus','medieval_boomsticks:handgonne']

def raw(value): return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()

def aliases(decisions):
    result={'tfc:metal/ingot/'+k:v for k,v in decisions['canonical_metals'].items()}
    result.update({'tfc:gem/'+k:'minecraft:'+v for k,v in {'diamond':'diamond','emerald':'emerald','amethyst':'amethyst_shard','lapis_lazuli':'lapis_lazuli'}.items()})
    result['overgeared:rock']='tfc:rock/loose/granite'
    result['tfc:beer']='brewinandchewin:beer'
    result[ID+':gun_barrel']='tfc_muskets:musket_barrel'
    result[ID+':gun_stock']='tfc_muskets:wooden_stock'
    return result

def replace(value, mapping):
    if isinstance(value,str):return mapping.get(value,value)
    if isinstance(value,list):return [replace(v,mapping) for v in value]
    if isinstance(value,dict):return {k:replace(v,mapping) for k,v in value.items()}
    return value

def preservation(value):
    text=json.dumps(value)
    return any(x in text for x in ['tfc:brined','tfc:pickled','tfc:salted','tfc:preserved','tfc:vinegar'])

def transform(path, decisions):
    with zipfile.ZipFile(path) as z: files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    mapping=aliases(decisions);deleted=[];changed=[]
    # TFC sheets contain two ingots; Create sheets contain one. Keep the forms
    # distinct to prevent the press -> remelt duplication loop.
    for metal in list(decisions['canonical_metals'])+['black_bronze','bismuth_bronze','black_steel','red_steel','blue_steel','any_bronze']:
        for old,new in [('sheets','thick_sheets'),('double_sheets','sheet_packs'),('rods','metal_rods')]:
            for prefix in ['', '#']:mapping[prefix+'forge:'+old+'/'+metal]=prefix+ID+':'+new+'/'+metal
    for name,data in list(files.items()):
        if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',name,re.I):
            deleted.append(name);del files[name];continue
        if not name.endswith('.json'):continue
        try:value=json.loads(data)
        except (ValueError,UnicodeDecodeError):continue
        is_recipe='/recipes/' in name
        banned=any(forbidden(r) for r in refs(value))
        if (is_recipe and (value.get('type') in {'tfc:anvil','tfc:welding','tfc:collapse','tfc:landslide'}
                           or banned or 'gunpowder' in name or preservation(value))
                or name.startswith('data/minecraft/recipes/')
                or '/worldgen/world_preset/' in name):
            deleted.append(name);del files[name];continue
        # References to the retained compatibility IDs must not become new loot.
        if '/loot_tables/' in name and banned:
            value={'type':value.get('type','minecraft:generic'),'pools':[]}
        if name.startswith('data/'):
            value=replace(value,mapping)
            if '/tags/' in name and isinstance(value,dict) and isinstance(value.get('values'),list):
                seen=set();kept=[]
                for entry in value['values']:
                    if any(forbidden(r) for r in refs(entry)):continue
                    key=json.dumps(entry,sort_keys=True)
                    if key not in seen:kept.append(entry);seen.add(key)
                value['values']=kept
        if raw(value)!=raw(json.loads(data)):
            files[name]=raw(value);changed.append(name)
    # No graphite dependency remains in fire clay. Kaolinite and sand still gate refractories.
    name='data/tfc/recipes/crafting/fire_clay.json'
    files[name]=raw({'type':'minecraft:crafting_shaped','pattern':['KSK','SCS','KSK'],
                     'key':{'K':{'item':'tfc:powder/kaolinite'},'S':{'tag':'minecraft:sand'},'C':{'item':'minecraft:clay_ball'}},
                     'result':{'item':'tfc:fire_clay','count':2}});changed.append(name)
    # TFC machines remain obtainable after all hammering/welding recipes are removed.
    for machine,metal in [('bloomery','forge:ingots/bronze'),('blast_furnace','forge:ingots/steel')]:
        name='data/tfc/recipes/crafting/'+machine+'.json'
        files[name]=raw({'type':'minecraft:crafting_shaped','pattern':['MMM','MCM','MMM'],
                         'key':{'M':{'tag':metal},'C':{'item':'tfc:crucible'}},'result':{'item':'tfc:'+machine}})
        changed.append(name)
    files['META-INF/empirecraft-tfc.json']=raw({'version':'1.0.0','upstream_sha256':decisions['upstream_sha256'],
                                               'source_commit':decisions['upstream_commit'],'decisions':decisions})
    mixins=json.loads(files['tfc.mixins.json'])
    retired_mixins={'LevelMixin','ServerLevelMixin','DimensionTypeMixin','IceBlockMixin','SnowLayerBlockMixin',
                    'client.ClientLevelMixin','client.FogRendererMixin','client.LevelRendererMixin','client.WaterDropParticleMixin'}
    for side in ['mixins','client']:mixins[side]=[v for v in mixins[side] if v not in retired_mixins]
    files['tfc.mixins.json']=raw(mixins);changed.append('tfc.mixins.json')
    files['META-INF/mods.toml']+=b'\n[[dependencies.tfc]]\nmodId="empirecraft_tfc"\nmandatory=true\nversionRange="[1.0.0,1.1.0)"\nordering="NONE"\nside="BOTH"\n'
    return files,{'deleted':sorted(n for n in deleted if n not in files),'rewritten':sorted(set(changed)),
                  'retained_registry_ids':True,'note':'Retired IDs are retained for save compatibility. Runtime guards disable acquisition and use.'}

def forging(pattern,key,item,blueprint='firearm',nbt=False):
    return {'type':'overgeared:forging','category':'tool_heads','tier':'iron','pattern':pattern,'key':key,
            'result':{'item':item},'hammering':5,'has_quality':True,'needs_minigame':True,
            'has_polishing':False,'need_quenching':False,'requires_blueprint':True,
            'blueprint':[blueprint],'minimum_quality':'poor','quality_difficulty':'none','show_notification':True}

def generate(decisions,tfc):
    files={};overrides={};mapping=aliases(decisions)
    def add(name,data):files[name]=raw(data)
    def recipe(identifier,data):
        ns,path=identifier.split(':',1);add('data/'+ns+'/recipes/'+path+'.json',data);overrides[identifier]=data
    def tag(ns,path,values):add('data/'+ns+'/tags/items/'+path+'.json',{'replace':False,'values':list(dict.fromkeys(values))})
    with zipfile.ZipFile(tfc) as z:
        banned=sorted({r for n in z.namelist() if n.endswith('.json') for r in refs(json.loads(z.read(n))) if forbidden(r)})
        metals={n.split('/')[-1][:-5]:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/tfc/tfc/metals/') and n.endswith('.json')}
        components={kind:sorted(n.rsplit('/',1)[1][:-5] for n in z.namelist() if n.startswith('assets/tfc/models/item/metal/'+kind+'/') and n.endswith('.json'))
                    for kind in ['double_ingot','sheet','double_sheet','rod','javelin_head']}
        native_strings={lang:json.loads(z.read('assets/tfc/lang/'+lang+'.json')) if 'assets/tfc/lang/'+lang+'.json' in z.namelist() else {}
                        for lang in ['en_us','uk_ua','ru_ru']}
    # DLO ships optional TFC food definitions for fillets that this pack doesn't register.
    for fish in ['cod','salmon']:
        add('data/tfc/tfc/food_items/'+fish+'_fillet.json',{'ingredient':{'item':'minecraft:'+fish},
            'hunger':2,'saturation':0.1,'decay_modifier':0,'protein':1})
    for metal,canonical in decisions['canonical_metals'].items():tag('forge','ingots/'+metal,[canonical])
    for kind,metals_for_kind in components.items():
        for metal in metals_for_kind:
            ingot=mapping.get('tfc:metal/ingot/'+metal,'tfc:metal/ingot/'+metal)
            result='tfc:metal/'+kind+'/'+metal
            if kind=='double_ingot':
                spec={'type':'minecraft:crafting_shapeless','ingredients':[{'item':ingot},{'item':ingot}],'result':{'item':result}}
            elif kind=='sheet':
                spec={'type':'create:pressing','ingredients':[{'item':'tfc:metal/double_ingot/'+metal}],'results':[{'item':result}]}
                tag(ID,'thick_sheets/'+metal,[result])
            elif kind=='double_sheet':
                spec={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'tfc:metal/sheet/'+metal},{'item':'tfc:metal/sheet/'+metal}],'result':{'item':result}}
                tag(ID,'sheet_packs/'+metal,[result])
            elif kind=='rod':
                spec={'type':'create:cutting','ingredients':[{'item':ingot}],'results':[{'item':result,'count':2}],'processingTime':100}
                tag(ID,'metal_rods/'+metal,[result])
            else:
                spec={'type':'create:cutting','ingredients':[{'item':ingot}],'results':[{'item':result}],'processingTime':200}
            recipe(ID+':forming/'+kind+'/'+metal,spec)
    for form in ['thick_sheets','sheet_packs']:
        tag(ID,form+'/any_bronze',['#'+ID+':'+form+'/'+m for m in ['bronze','black_bronze','bismuth_bronze']])
    recipe(ID+':glassworking/blowpipe',{'type':'create:cutting','ingredients':[{'tag':ID+':metal_rods/brass'}],
        'results':[{'item':'tfc:blowpipe'}],'processingTime':100})
    recipe(ID+':glassworking/jacks',{'type':'minecraft:crafting_shaped','pattern':['R R'],
        'key':{'R':{'tag':ID+':metal_rods/brass'}},'result':{'item':'tfc:jacks'}})
    tag('tfc','lumber',['#minecraft:planks'])
    tag('overgeared','stone_smithing_hammers',[
        'tfc:stone/hammer/igneous_extrusive','tfc:stone/hammer/igneous_intrusive',
        'tfc:stone/hammer/metamorphic','tfc:stone/hammer/sedimentary'])
    tag('overgeared','smithing_hammers',['#overgeared:stone_smithing_hammers'])
    # Canonical ores enter the same temperature and liquid-unit system as TFC ores.
    for metal,ore in [('copper','minecraft:raw_copper'),('cast_iron','minecraft:raw_iron'),('gold','minecraft:raw_gold'),
                      ('tin','antiquelegacy:raw_tin'),('zinc','create:raw_zinc')]:
        if metal not in metals:continue
        recipe(ID+':heating/raw_'+metal,{'type':'tfc:heating','ingredient':{'item':ore},
               'result_fluid':{'fluid':'tfc:metal/'+metal,'amount':100},'temperature':metals[metal]['melt_temperature']})
        add('data/'+ID+'/tfc/item_heats/raw_'+metal+'.json',{'ingredient':{'item':ore},'heat_capacity':1.0})
    # Bloom/steel refining uses heat, without TFC's removed anvil minigame.
    for source,target in [('raw_iron_bloom','minecraft:iron_ingot'),('refined_iron_bloom','minecraft:iron_ingot'),
                          ('metal/ingot/high_carbon_steel','overgeared:steel_ingot'),
                          ('metal/ingot/high_carbon_black_steel','tfc:metal/ingot/black_steel'),
                          ('metal/ingot/high_carbon_red_steel','tfc:metal/ingot/red_steel'),
                          ('metal/ingot/high_carbon_blue_steel','tfc:metal/ingot/blue_steel')]:
        recipe(ID+':refining/'+source,{'type':'tfc:heating','ingredient':{'item':'tfc:'+source},
               'result_item':{'item':target},'temperature':1535})
    for source,target in [('weak_steel','black_steel'),('weak_red_steel','red_steel'),('weak_blue_steel','blue_steel')]:
        recipe(ID+':alloy/'+target,{'type':'tfc:alloy','result':'tfc:high_carbon_'+target,'contents':[
            {'metal':'tfc:'+source,'min':0.5,'max':0.5},{'metal':'tfc:'+('pig_iron' if target=='black_steel' else 'black_steel'),'min':0.5,'max':0.5}]})
    # Ordinary vanilla stone becomes knapping material without requiring a TFC terrain generator.
    tag('tfc','rock_knapping',['tfc:rock/loose/granite','tfc:rock/loose/diorite','tfc:rock/loose/andesite'])
    recipe(ID+':handstone',{'type':'minecraft:crafting_shaped','pattern':['SSS','SSS'],
                          'key':{'S':{'item':'minecraft:smooth_stone'}},'result':{'item':'tfc:handstone'}})
    recipe(ID+':nest_box',{'type':'minecraft:crafting_shaped','pattern':['H H','PHP','PPP'],
                          'key':{'P':{'tag':'minecraft:planks'},'H':{'item':'minecraft:wheat'}},'result':{'item':'tfc:nest_box'}})
    recipe(ID+':barrel',{'type':'minecraft:crafting_shaped','pattern':['P P','P P','PPP'],
                       'key':{'P':{'tag':'minecraft:planks'}},'result':{'item':'tfc:wood/barrel/oak'}})
    recipe(ID+':quern',{'type':'minecraft:crafting_shaped','pattern':['SSS','CCC'],
                       'key':{'S':{'item':'minecraft:smooth_stone'},'C':{'item':'minecraft:cobblestone'}},'result':{'item':'tfc:quern'}})
    recipe(ID+':straw',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:wheat'}],'result':{'item':'tfc:straw','count':2}})
    recipe(ID+':flux',{'type':'tfc:quern','ingredient':{'item':'minecraft:calcite'},'result':{'item':'tfc:powder/flux','count':4}})
    recipe('sulfur_gunpowder:gunpowdercraft',{'type':'minecraft:crafting_shapeless','ingredients':[
        {'item':'tfc:powder/saltpeter'},{'item':'tfc:powder/saltpeter'},{'item':'tfc:powder/saltpeter'},
        {'item':'sulfur_gunpowder:sulfur'},{'item':'minecraft:charcoal'}],'result':{'item':'minecraft:gunpowder','count':6}})
    recipe(ID+':stone_pickaxe_head',{'type':'tfc:knapping','knapping_type':'tfc:rock','pattern':['XXXXX','  X  ','  X  '],
         'result':{'item':'overgeared:stone_pickaxe_head'}})
    recipe(ID+':stone_pickaxe',{'type':'minecraft:crafting_shaped','pattern':['H','S','S'],
        'key':{'H':{'item':'overgeared:stone_pickaxe_head'},'S':{'item':'minecraft:stick'}},'result':{'item':'minecraft:stone_pickaxe'}})
    # Existing crops are accepted by native recipes where TFC's grain tags are used.
    for grain in ['wheat','barley','oat','rye','rice','maize']:
        values=['minecraft:wheat'] if grain=='wheat' else []
        if values:tag('forge','grain/'+grain,values)
    tag('forge','flour',[f'tfc:food/{grain}_flour' for grain in ['wheat','barley','oat','rye','rice','maize']])
    recipe(ID+':quern/wheat',{'type':'tfc:quern','ingredient':{'item':'minecraft:wheat'},'result':{'item':'tfc:food/wheat_flour'}})
    recipe(ID+':brewing/grain_beer',{'type':'brewinandchewin:fermenting','basefluid':{'count':1000,'fluid':'minecraft:water'},
        'experience':1.0,'fermentingtime':9600,'ingredients':[{'tag':'forge:flour'},{'item':'minecraft:wheat_seeds'},{'item':'minecraft:brown_mushroom'}],
        'recipe_book_tab':'drinks','result':{'count':1000,'fluid':'brewinandchewin:beer'},'temperature':3})
    tag(ID,'apples',['minecraft:apple','tfc:food/red_apple','tfc:food/green_apple'])
    def apple_inputs(value):
        if isinstance(value,list):return [apple_inputs(v) for v in value]
        if isinstance(value,dict):
            if value.get('item')=='minecraft:apple':return {**{k:v for k,v in value.items() if k!='item'},'tag':ID+':apples'}
            return {k:apple_inputs(v) for k,v in value.items()}
        return value
    repo=Path(__file__).resolve().parent.parent
    vinery=next((repo/'mods').glob('*vinery*.jar'))
    with zipfile.ZipFile(vinery) as z:
        for name in z.namelist():
            if not name.startswith('data/vinery/recipes/') or not name.endswith('.json'):continue
            value=json.loads(z.read(name));original=json.dumps(value)
            for key in ['key','input','ingredient','ingredients']:
                if key in value:value[key]=apple_inputs(value[key])
            if json.dumps(value)!=original:recipe('vinery:'+name.split('/recipes/')[1][:-5],value)
    recipe('starcatcher:vanilla_bobber_from_vanilla_fishing_rod',{'type':'minecraft:crafting_shapeless','ingredients':[
        {'item':'minecraft:oak_planks'},{'item':'minecraft:string'}],'result':{'item':'starcatcher:vanilla_bobber'}})
    for item in ['barrel_blank','gun_barrel','gun_lock','gun_stock']:
        add('assets/'+ID+'/models/item/'+item+'.json',{'parent':'minecraft:item/generated',
             'textures':{'layer0':'minecraft:item/'+('iron_ingot' if item=='barrel_blank' else 'iron_nugget' if item=='gun_lock' else 'stick' if item=='gun_stock' else 'iron_ingot')}})
    recipe(ID+':barrel_blank',forging(['SSS',' P ','SSS'],{'S':{'item':'overgeared:heated_steel_ingot'},'P':{'item':'overgeared:steel_plate'}},ID+':barrel_blank'))
    recipe(ID+':gun_lock',forging(['PFP',' C ',' N '],{'P':{'item':'overgeared:steel_plate'},'F':{'item':'minecraft:flint'},
             'C':{'item':'create:cogwheel'},'N':{'item':'minecraft:iron_nugget'}},ID+':gun_lock'))
    recipe(ID+':gun_barrel',{'type':'create:cutting','ingredients':[{'item':ID+':barrel_blank'}],
                           'results':[{'item':ID+':gun_barrel'}],'processingTime':200})
    recipe(ID+':gun_stock',{'type':'create:cutting','ingredients':[{'tag':'minecraft:logs'}],
                          'results':[{'item':ID+':gun_stock','count':2}],'processingTime':100})
    for gun in GUNS:
        if gun.endswith(('musket_with_bayonet','musket_with_scope')):
            addition='minecraft:iron_sword' if gun.endswith('bayonet') else 'minecraft:spyglass'
            spec=forging([' B ','AMA',' P '],{'B':{'item':addition},'A':{'item':'overgeared:steel_plate'},
                  'M':{'item':'musketmod:musket','transfer_nbt':True},'P':{'item':'minecraft:iron_nugget'}},gun)
        else:
            spec=forging([' BB','PLB',' SS'],{'B':{'item':ID+':gun_barrel'},'P':{'item':'overgeared:steel_plate'},
                 'L':{'item':ID+':gun_lock'},'S':{'item':ID+':gun_stock'}},gun)
            if gun.endswith(('pistol','handgonne')):spec['pattern']=[' B','PL',' S']
        # Reuse the uploaded addon's actual parts in the harder existing chain.
        spec=replace(spec,{ID+':gun_barrel':'tfc_muskets:blunderbuss_barrel' if gun.endswith('blunderbuss') else 'tfc_muskets:musket_barrel',
                           ID+':gun_stock':'tfc_muskets:wooden_stock'})
        recipe(gun,spec)
    from casting_muskets import generate as casting_muskets
    casting_muskets(recipe,add,tag,forging,decisions,tfc)
    from natural_materials import generate as natural_materials
    natural_materials(recipe,add,tag)
    recipe(ID+':quern_drive',{'type':'minecraft:crafting_shaped','pattern':[' I ','CPC',' S '],
           'key':{'I':{'item':'create:iron_sheet'},'C':{'item':'create:cogwheel'},'P':{'tag':'minecraft:planks'},
                  'S':{'item':'create:shaft'}},'result':{'item':ID+':quern_drive'}})
    add('assets/'+ID+'/blockstates/quern_drive.json',{'variants':{'':{'model':'create:block/andesite_casing'}}})
    add('assets/'+ID+'/models/item/quern_drive.json',{'parent':'create:block/andesite_casing'})
    add('data/'+ID+'/loot_tables/blocks/quern_drive.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':ID+':quern_drive'}]}]})
    add('data/minecraft/tags/blocks/mineable/axe.json',{'replace':False,'values':[ID+':quern_drive']})
    for tag_path in ['forge_insulation','bloomery_insulation']:
        add('data/tfc/tags/blocks/'+tag_path+'.json',{'replace':False,'values':['minecraft:stone','minecraft:cobblestone','minecraft:bricks','minecraft:stone_bricks','minecraft:deepslate_bricks']})
    add('data/forge/loot_modifiers/global_loot_modifiers.json',{'replace':False,'entries':[ID+':crop_seeds']})
    add('data/'+ID+'/loot_modifiers/crop_seeds.json',{'type':ID+':crop_seeds','conditions':[
        {'condition':'minecraft:random_chance','chance':0.03125},
        {'condition':'minecraft:any_of','terms':[{'condition':'forge:loot_table_id','loot_table_id':'minecraft:blocks/'+v}
                                               for v in ['grass','tall_grass','fern','large_fern']]}]})
    weapon_tags={k:set() for k in ['piercing','slashing','crushing']}
    damage_types=set()
    for jar in sorted((repo/'mods').glob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if re.match(r'data/[^/]+/damage_type/[^/]+\.json$',name) and any(s in name for s in ['musket','bullet','gunshot','arquebus','handgonne']):
                    damage_types.add(name.split('/')[1]+':'+name.split('/damage_type/')[1][:-5])
                if not name.endswith('/lang/en_us.json'):continue
                try:lang=json.loads(z.read(name))
                except ValueError:continue
                for key in lang:
                    if not key.startswith('item.') or key.count('.')!=2:continue
                    _,ns,path=key.split('.')
                    if ns in ['minecraft','tfc']:continue
                    kind=None
                    if any(s in path for s in ['mace','hammer','morgenstern','morningstar','flail','club','kanabo','tonfa']):kind='crushing'
                    elif any(s in path for s in ['stylet','estoc','pike','lance','ahlspiess','ranseur','spear','javelin','dagger','knife','rapier','trident','yari','sai','musket','pistol','blunderbuss','arquebus','handgonne']):kind='piercing'
                    elif any(s in path for s in ['sword','katzbalger','claymore','zweihander','axe','halberd','glaive','guisarme','falchion','sabre','saber','scythe','messer','katana','dadao','naginata','nodachi','tachi','kama']):kind='slashing'
                    if kind:weapon_tags[kind].add(ns+':'+path)
    for kind,values in weapon_tags.items():
        add('data/tfc/tags/items/deals_'+kind+'_damage.json',{'replace':False,'values':[{'id':v,'required':False} for v in sorted(values)]})
    add('data/tfc/tags/damage_type/is_piercing.json',{'replace':False,'values':[{'id':v,'required':False} for v in sorted(damage_types)]})
    # Existing animal identities participate in the native TFC hunting food chain.
    add('data/tfc/tags/entity_types/hunted_by_land_predators.json',{'replace':False,'values':[
        'naturalist:deer','naturalist:duck','ecologics:penguin','dragnlivestock:caribou',
        *['dragnlivestock:o_'+v for v in ['cow','sheep','pig','goat','chicken','rabbit']]]})
    habitats={
        '#minecraft:is_forest':['grizzly_bear','black_bear','cougar','direwolf','moose','grouse','turkey','rat'],
        '#minecraft:is_savanna':['lion','hyena','gazelle','wildebeest'],
        '#minecraft:is_jungle':['tiger','panther','bongo','peafowl'],
        '#minecraft:is_mountain':['yak','alpaca','musk_ox'],
        'minecraft:snowy_plains':['polar_bear','sabertooth'],
        'minecraft:plains':['boar','pheasant','quail'],
        '#minecraft:is_river':['bluegill','crappie','lake_trout','rainbow_trout','crayfish'],
        '#minecraft:is_ocean':['orca','isopod','lobster','horseshoe_crab','octopoteuthis','jellyfish'],
        'minecraft:swamp':['crocodile']}
    for biome,animals in habitats.items():
        for animal in animals:
            add('data/'+ID+'/forge/biome_modifier/'+animal+'.json',{'type':'forge:add_spawns','biomes':biome,
                'spawners':[{'type':'tfc:'+animal,'weight':2,'minCount':1,'maxCount':2}]})
    add('empirecraft-tfc-weapon-classification.json',{k:sorted(v) for k,v in weapon_tags.items()})
    base={'item.'+ID+'.barrel_blank':'Forged Barrel Blank','item.'+ID+'.gun_barrel':'Fitted Gun Barrel',
          'item.'+ID+'.gun_lock':'Gun Lock','item.'+ID+'.gun_stock':'Gun Stock','block.'+ID+'.quern_drive':'Quern Drive',
          'tooltype.overgeared.firearm':'Firearm','message.empirecraft_tfc.disabled':'This system is disabled in Empirecraft.',
          'message.empirecraft_tfc.familiarity':'Familiarity: %s%%'}
    for language in ['en_us','uk_ua','ru_ru']:
        strings=dict(base)
        strings['item.'+ID+'.lead_rod']='Свинцевий стрижень' if language=='uk_ua' else 'Lead Rod'
        for tool,label in [('axe','сокири'),('pickaxe','кайла'),('hoe','мотики'),('shovel','лопати')]:
            strings['item.'+ID+'.golden_'+tool+'_head_blank']='Золота відливка головки '+label
        if language=='uk_ua':strings.update({'item.'+ID+'.barrel_blank':'Кована заготовка ствола','item.'+ID+'.gun_barrel':'Припасований ствол',
              'item.'+ID+'.gun_lock':'Рушничний замок','item.'+ID+'.gun_stock':'Ложа рушниці','block.'+ID+'.quern_drive':'Привід жорна',
              'tooltype.overgeared.firearm':'Вогнепальна зброя','message.empirecraft_tfc.disabled':'Цю систему вимкнено у збірці Empirecraft.',
              'message.empirecraft_tfc.familiarity':'Звикання: %s%%'})
        for kind,amount in [('sheet',2),('double_sheet',4)]:
            for metal in components[kind]:
                key='item.tfc.metal.'+kind+'.'+metal
                label=native_strings[language].get(key,native_strings['en_us'].get(key,metal+' '+kind))
                strings[key]=label+(' ('+str(amount)+' злитки)' if language=='uk_ua' else ' ('+str(amount)+' ingots)')
        add('assets/'+ID+'/lang/'+language+'.json',strings)
    # The policy is consumed by the JSON reload guard and by validation.
    add('empirecraft-tfc-policy.json',{'forbidden':banned,'aliases':mapping,'guns':GUNS,'overrides':overrides})
    return files
