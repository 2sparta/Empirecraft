"""Native datapack and embedded translations for physical, obtainable survival equipment."""
import json,re,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;MID='legendarysurvivaloverhaul'
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def craft(result,ingredients,count=1):return dict(type='minecraft:crafting_shapeless',ingredients=[{'tag':v[1:]} if v.startswith('#') else {'item':v} for v in ingredients],result=dict(item=result,count=count))
def shaped(result,pattern,keys,count=1):return dict(type='minecraft:crafting_shaped',pattern=pattern,key={k:({'tag':v[1:]} if v.startswith('#') else {'item':v}) for k,v in keys.items()},result=dict(item=result,count=count))
def hydration(value,saturation=0.25,effects=None):return [dict(hydration=value,saturation=saturation,effects=effects or [],properties={})]
def adapt(files):
    source=(ROOT/'src/main/java/ua/empirecraft/survival/SurvivalRules.java').read_text();section=source.split('REMOVED_ITEMS=Set.of(',1)[1].split(');',1)[0];retired=set(re.findall('"([a-z0-9_]+)"',section))
    # Remove every original LSO recipe/advancement/loot injection and all limb data.
    for name in list(files):
        if name.startswith(f'assets/{MID}/models/item/') and Path(name).stem in retired or name.startswith(f'assets/{MID}/textures/item/') and Path(name).stem in retired:files.pop(name)
    for name in list(files):
        if name.startswith(f'data/{MID}/recipes/') or name.startswith(f'data/{MID}/advancements/') or name.startswith('data/') and ('/bodydamage/' in name or '/body_damage/' in name) or name.startswith(f'data/{MID}/loot_tables/inject/') or name.startswith(f'data/{MID}/loot_modifiers/'):
            files.pop(name);continue
        if name=='data/forge/loot_modifiers/global_loot_modifiers.json':files[name]=encoded(dict(replace=False,entries=[]));continue
        if name.startswith('data/') and name.endswith('.json'):
            text=files[name].decode()
            if any(f'{MID}:{item}"' in text for item in retired):files.pop(name)
    metadata=files['META-INF/mods.toml'].decode().replace('version="1.20.1-2.4.7"','version="1.20.1-2.4.7-empirecraft.1"')
    metadata+='\n[[dependencies.legendarysurvivaloverhaul]]\nmodId="empirecraft_armor"\nmandatory=true\nversionRange="[1.0.0,1.1)"\nordering="AFTER"\nside="BOTH"\n';files['META-INF/mods.toml']=metadata.encode()
    recipes={
        'heating_coat_1':craft(f'{MID}:heating_coat_1',['farmersdelight:canvas','#minecraft:wool','minecraft:string']),
        'cooling_coat_1':craft(f'{MID}:cooling_coat_1',['farmersdelight:canvas','supplementaries:flax','minecraft:string']),
        'thermal_coat_1':craft(f'{MID}:thermal_coat_1',[f'{MID}:cooling_coat_1','minecraft:honeycomb']),
        'canteen':shaped(f'{MID}:canteen',[' S ','L L','LLL'],dict(S='minecraft:string',L='minecraft:leather')),
        'large_canteen':craft(f'{MID}:large_canteen',[f'{MID}:canteen','minecraft:leather','minecraft:leather','minecraft:string']),
        'sewing_table':shaped(f'{MID}:sewing_table',['SIS','PPP','P P'],dict(S='minecraft:string',I='minecraft:iron_ingot',P='#minecraft:planks')),
        'thermometer':craft(f'{MID}:thermometer',['minecraft:glass','minecraft:copper_ingot','minecraft:red_dye']),
        'seasonal_calendar':craft(f'{MID}:seasonal_calendar',['minecraft:paper','minecraft:paper','minecraft:paper','minecraft:ink_sac']),
        'heater':shaped(f'{MID}:heater',['BBB','BCB','BIB'],dict(B='minecraft:brick',C='minecraft:coal',I='minecraft:iron_ingot')),
        'cooler':shaped(f'{MID}:cooler',['WWW','ICI','III'],dict(W='#minecraft:wool',I='minecraft:iron_ingot',C='minecraft:ice')),
        'boiled_water':dict(type='minecraft:smelting',ingredient=dict(item=f'{MID}:raw_water_bottle'),result=f'{MID}:purified_water_bottle',experience=0.1,cookingtime=200)
    }
    recipes['remove_coat']=dict(type=f'{MID}:remove_coat')
    # Existing canteens keep their content/NBT when their native purification recipe runs.
    with zipfile.ZipFile(next((ROOT/'native-inputs').glob('*.jar'))) as z:
        for name in z.namelist():
            if name.startswith(f'data/{MID}/recipes/') and 'purification_' in name and 'potion' not in name:files[name]=z.read(name)
    for name,recipe in recipes.items():files[f'data/{MID}/recipes/{name}.json']=encoded(recipe)
    for plant,food in [('apple','minecraft:apple'),('beetroot','minecraft:beetroot'),('cactus','minecraft:cactus'),('carrot','minecraft:carrot'),('melon','minecraft:melon_slice'),('pumpkin','minecraft:pumpkin'),('sweet_berries','minecraft:sweet_berries'),('glow_berries','minecraft:glow_berries')]:
        files[f'data/{MID}/recipes/{plant}_juice.json']=encoded(craft(f'{MID}:{plant}_juice',['minecraft:glass_bottle',food,food]))
    drinks={f'{MID}:raw_water_bottle':hydration(3,0,[dict(effect=f'{MID}:thirst',duration=300,amplifier=0,chance=0.35)]),f'{MID}:purified_water_bottle':hydration(4,0.5),'minecraft:milk_bucket':hydration(4,0.25),'minecraft:melon_slice':hydration(1,0),'minecraft:apple':hydration(1,0),'minecraft:mushroom_stew':hydration(2,0.25),'minecraft:beetroot_soup':hydration(2,0.25),'minecraft:rabbit_stew':hydration(2,0.25)}
    for name in ['apple','beetroot','cactus','carrot','melon','pumpkin','sweet_berries','glow_berries']:drinks[f'{MID}:{name}_juice']=hydration(4,0.4)
    # Candidates are grounded in installed item models; the native registry audit narrows them to live consumables.
    live=ROOT/'audit/consumables.json';inventory=json.loads(live.read_text()) if live.exists() else None
    if inventory is not None:
        for name in list(files):
            match=re.fullmatch(r'data/([^/]+)/legendarysurvivaloverhaul/thirst/consumables/(.+)\.json',name)
            if match and (match[1]+':'+match[2] not in inventory or match[1]+':'+match[2] in {'minecraft:potion','minecraft:splash_potion','minecraft:lingering_potion'}):files.pop(name)
    for jar in sorted((REPO/'mods').glob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            names=z.namelist()
            for name in names:
                match=re.fullmatch(r'assets/([^/]+)/models/item/([^/]+)\.json',name)
                if not match:continue
                namespace,path=match.groups();item=namespace+':'+path
                if inventory is not None and item not in inventory:continue
                if re.search(r'(^|_)(soup|stew|broth)($|_)',path):drinks[item]=hydration(2,0.25)
                elif any(token in path for token in ['juice','milkshake']) and not any(t in path for t in ['bottle','bucket','block','leaves','seeds','slice']):drinks[item]=hydration(4,0.4)
                elif re.search(r'(^|_)(tea|coffee)($|_)',path) and not any(t in path for t in ['leaves','berries','beans','cake','cookie','braised','curry','crate','seeds']):drinks[item]=hydration(4,0.25)
                elif namespace=='vinery' and ('wine' in path or 'pinot' in path) and not any(t in path for t in ['bottle','wine_rack','winemaker','spawn_egg']):drinks[item]=hydration(1,0,[dict(effect=f'{MID}:thirst',duration=200,amplifier=0,chance=1.0)])
                elif namespace=='brewinandchewin' and path in {'beer','rice_wine','vodka','mead','strongroot_ale','salty_folly','pale_jane','saccharine_rum','bloody_mary','red_rum'}:drinks[item]=hydration(1,0,[dict(effect=f'{MID}:thirst',duration=200,amplifier=0,chance=1.0)])
    alcohol=lambda:hydration(1,0,[dict(effect=f'{MID}:thirst',duration=200,amplifier=0,chance=1.0)])
    if inventory is not None:
        for item,properties in inventory.items():
            if properties['animation']!='DRINK':continue
            namespace,path=item.split(':',1)
            if namespace=='vinery' and 'juice' not in path:drinks[item]=alcohol()
            elif namespace=='brewinandchewin':
                drinks[item]=hydration(1,0.1) if any(word in path for word in ['jelly','marmalade','jam']) else hydration(2,0.1) if path=='kombucha' else alcohol()
            elif namespace=='caupona':drinks[item]=hydration(4,0.25) if path in {'water','milk','scalded_milk'} else hydration(1,0.1) if any(v in path for v in ['custard','pudding','porridge','gelatin']) else hydration(2,0.25)
            elif namespace=='dragnlivestock' and path.endswith('milk_bucket'):drinks[item]=hydration(4,0.25)
            elif namespace in {'farmersdelight','farmersrespite'} and any(v in path for v in ['milk_bottle','apple_cider','hot_cocoa']):drinks[item]=hydration(4,0.25)
            elif namespace=='fruitsdelight' and 'jelly' in path:drinks[item]=hydration(1,0.1)
            elif namespace=='fruitsdelight' and 'cocktail' in path:drinks[item]=alcohol()
            elif namespace=='fruitsdelight' and 'custard' in path:drinks[item]=hydration(1,0.1)
            elif namespace=='fruitsdelight' and 'shaved_ice' in path:drinks[item]=hydration(3,0.1)
    drinks['minecraft:honey_bottle']=hydration(1,0.1)
    drinks[f'{MID}:water_plant_bag']=hydration(2,0.1)
    for item,data in drinks.items():namespace,path=item.split(':',1);files[f'data/{namespace}/{MID}/thirst/consumables/{path}.json']=encoded(data)
    (ROOT/'audit/hydration.json').write_bytes(encoded(drinks))
    # Remove magical item resistance definitions; coatings supply their modest physical values in code.
    for name in list(files):
        if name.startswith(f'data/{MID}/{MID}/temperature/items/') and any(token in name for token in ['coat','ring','snow_','desert_']):files.pop(name)
    files[f'assets/{MID}/models/item/raw_water_bottle.json']=encoded(dict(parent='minecraft:item/generated',textures=dict(layer0=f'{MID}:item/purified_water_bottle')))
    translations={
        'en_us':{'heating_coat_1':'Wool lining','cooling_coat_1':'Linen lining','thermal_coat_1':'Waxed canvas lining','raw_water_bottle':'Untreated water'},
        'uk_ua':{'heating_coat_1':'Вовняна підкладка','cooling_coat_1':'Лляна підкладка','thermal_coat_1':'Підкладка з вощеного брезенту','raw_water_bottle':'Некип’ячена вода'},
        'ru_ru':{'heating_coat_1':'Шерстяная подкладка','cooling_coat_1':'Льняная подкладка','thermal_coat_1':'Подкладка из вощёного брезента','raw_water_bottle':'Некипячёная вода'}}
    english=json.loads(files[f'assets/{MID}/lang/en_us.json'])
    def active(key):
        if key.startswith(('advancement','enchantment.','item.minecraft.')):return False
        if any(token in key for token in ['body','health','heart','pain','morphine','bandage','medkit','plaster','tonic','healing','recovery','vulnerability','hard_falling','headache','headshot']):return False
        if any('.'+item in key for item in retired):return False
        if key.startswith('attribute.') and not any(v in key for v in ['heating_temperature','cooling_temperature','heat_resistance','cold_resistance','thermal_resistance']):return False
        return not(key.startswith('tooltip.') and any(v in key for v in ['heating2','heating3','cooling2','cooling3','thermal2','thermal3']))
    english={key:value for key,value in english.items() if active(key)}
    extra={
      'en_us':dict(inner_only='Only leather or padded inner armor; one lining per piece.',heating_coat_1='Wool: +2 cold resistance.',cooling_coat_1='Linen: +1.5 heat resistance.',thermal_coat_1='Waxed canvas: +0.5 cold resistance; slows rain soaking. Does not protect against immersion.'),
      'ru_ru':dict(inner_only='Только кожаный или стёганый нижний слой; одна подкладка на вещь.',heating_coat_1='Шерсть: +2 к устойчивости к холоду.',cooling_coat_1='Лён: +1,5 к устойчивости к жаре.',thermal_coat_1='Вощёный брезент: +0,5 к устойчивости к холоду; медленнее намокает под дождём. В воде намокает обычно.')}
    for locale,changes in translations.items():
        path=f'assets/{MID}/lang/{locale}.json';lang=json.loads(files.get(path,b'{}'))
        lang={key:lang.get(key,value) for key,value in english.items()}
        local=ROOT/'lang'/(locale+'.json')
        if local.exists():lang.update(json.loads(local.read_text()))
        for name,label in changes.items():lang[f'item.{MID}.{name}']=label
        for name,label in extra.get(locale,{}).items():lang['tooltip.empirecraft_survival.'+name]=label
        for item,coat in [('heating_coat_1','heating1'),('cooling_coat_1','cooling1'),('thermal_coat_1','thermal1')]:lang[f'tooltip.{MID}.armor_coat.{coat}']=changes[item]
        files[path]=encoded(lang)
    (ROOT/'audit/recipes.json').write_bytes(encoded(recipes));return files
