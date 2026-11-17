#!/usr/bin/env python3
"""Curated regional approximations; not measured climate or a surveyed mineral inventory."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
features=[]
def poly(id,layer,label,points,values,priority=10):
    features.append(dict(id=id,layer=layer,label=label,priority=priority,rings=[points+[points[0]]],values=values))
def box(id,layer,label,w,s,e,n,values,priority=10):
    poly(id,layer,label,[[w,s],[e,s],[e,n],[w,n]],values,priority)
def climate(id,label,w,s,e,n,mean,amp,rain):
    box(id,'climate',label,w,s,e,n,dict(mean_c=mean,amplitude_c=amp,annual_rain_mm=rain,source='curated_approximation'))
for row in [
 ('ukraine_continental','Помірний континентальний',22,44,40,53,9,13,550),
 ('mediterranean','Середземноморський',-10,30,37,44,18,9,550),
 ('western_europe','Помірний океанічний',-11,44,15,59,10,7,900),
 ('sahara','Сахара',-17,16,35,30,26,10,70),
 ('arabia','Аравійська пустеля',35,15,60,32,27,10,100),
 ('amazon','Амазонія',-76,-12,-48,5,26,1.5,2400),
 ('congo','Басейн Конго',10,-6,30,5,25,1.5,1900),
 ('southeast_asia','Вологі тропіки Південно-Східної Азії',95,-10,140,15,27,2,2400),
 ('central_australia','Внутрішня Австралія',118,-32,140,-20,23,9,220),
 ('siberia','Сибірський континентальний',60,50,140,67,-2,25,400),
 ('india_monsoon','Мусонна Індія',72,8,89,27,26,6,1300),
 ('north_america_temperate','Помірна Північна Америка',-100,35,-65,49,10,15,900),
 ('southern_africa','Південь Африки',18,-34,34,-22,19,6,600),
 ('pampas','Пампа',-65,-39,-53,-29,16,7,900),
 ('antarctica','Антарктида',-180,-90,180,-66,-28,15,150),
]: climate(*row)
# Natural zones intentionally use independent polygons, not national borders.
for id,label,pts,zone,soil_class,soil in [
 ('ukraine_polissia','Полісся',[[23,50.5],[29,50.2],[34,51],[34,52.5],[23,52.5]],'mixed_forest','sod_podzolic','sandy_loam'),
 ('ukraine_forest_steppe','Лісостеп України',[[24,48.5],[29,48],[37,49],[35,51.2],[29,50.2],[24,50.5]],'forest_steppe','chernozem_and_grey_forest','silty_loam'),
 ('ukraine_steppe','Степ України',[[27,46],[33,45.5],[39,47],[39,49.5],[37,49],[29,48]],'steppe','chernozem','loam'),
 ('ukraine_dry_steppe','Сухий південний степ',[[29,45.5],[35.5,45.3],[36,46.6],[33,47],[29,46.7]],'dry_steppe','southern_chernozem_and_kastanozem','loam'),
 ('carpathians','Карпати',[[22,47],[25,47],[26,49],[23,50]],'montane_forest','brown_mountain_forest','silty_loam'),
]:poly(id,'ecology',label,pts,dict(zone=zone,soil_class=soil_class,tfc_soil=soil,source='curated_approximation'),30 if id=='carpathians' else 20)
for id,label,w,s,e,n,zone,soilclass,soil in [
 ('eurasian_steppe','Євразійський степ',40,46,90,54,'steppe','chernozem_and_kastanozem','loam'),
 ('great_plains','Великі рівнини',-106,32,-96,52,'steppe','mollisol','loam'),
 ('pampas_soils','Пампа',-65,-39,-53,-29,'steppe','mollisol','silty_loam'),
 ('sahara_soils','Сахара',-17,16,35,30,'desert','arid','sandy_loam'),
 ('amazon_soils','Амазонські ліси',-76,-12,-48,5,'rainforest','ferralsol','silty_loam'),
 ('congo_soils','Ліси Конго',10,-6,30,5,'rainforest','ferralsol','silty_loam'),
 ('siberian_taiga','Тайга',60,55,140,67,'taiga','podzol','sandy_loam'),
 ('scandinavia','Скандинавські ліси',5,57,32,67,'taiga','podzol','sandy_loam'),
 ('mediterranean_scrub','Середземноморські ліси й чагарники',-10,30,37,43,'mediterranean','cambisol','loam'),
]:box(id,'ecology',label,w,s,e,n,dict(zone=zone,soil_class=soilclass,tfc_soil=soil,source='curated_approximation'))
for id,label,w,s,e,n,rocks,sand in [
 ('ukrainian_shield','Український щит',27,47,33,51,['granite','gneiss','quartzite'],'pink'),
 ('dnipro_donets','Дніпровсько-Донецька западина',32,49,38,52,['shale','limestone','claystone'],'yellow'),
 ('donbas_geology','Донецький басейн',37,47,40,50,['shale','limestone','conglomerate'],'brown'),
 ('carpathian_flysch','Карпатський фліш',22,47,26,50,['shale','conglomerate','slate'],'brown'),
 ('crimea_geology','Кримські осадові породи',32,44,37,46,['limestone','shale','chalk'],'white'),
 ('iceland','Ісландський вулканізм',-25,63,-13,67,['basalt','gabbro','basalt'],'black'),
 ('andes','Андійський пояс',-78,-50,-67,8,['andesite','dacite','granite'],'brown'),
 ('brazilian_shield','Бразильський щит',-60,-25,-40,-5,['gneiss','granite','quartzite'],'yellow'),
 ('australian_shield','Західноавстралійський щит',113,-35,130,-15,['granite','gneiss','quartzite'],'red'),
]:box(id,'geology',label,w,s,e,n,dict(rocks=rocks,sand=sand,source='curated_approximation'),20)
# Separate geographic basins. Actual water/land and river biome masks are applied at runtime.
for id,label,w,s,e,n,priority in [
 ('arctic','Північний Льодовитий океан',-180,66,180,90,1),
 ('atlantic_north','Північна Атлантика',-100,0,20,66,1),
 ('atlantic_south','Південна Атлантика',-70,-60,20,0,1),
 ('pacific_west','Тихий океан (захід)',100,-60,180,66,1),
 ('pacific_east','Тихий океан (схід)',-180,-60,-70,66,1),
 ('indian','Індійський океан',20,-60,100,30,1),
 ('southern','Південний океан',-180,-90,180,-60,2),
 ('mediterranean','Середземне море',-5.7,30,36.5,46,10),
 ('black_sea','Чорне море',27,40.8,42,47,20),
 ('azov','Азовське море',34.5,45.3,39.5,47.5,30),
 ('baltic','Балтійське море',9,53,31,66,20),
 ('red_sea','Червоне море',32,12,44,30,20),
 ('caribbean','Карибське море',-89,9,-60,23,20),
 ('gulf_mexico','Мексиканська затока',-98,18,-81,31,20),
 ('caspian','Каспійське море',46,36,55,48,30),
]:box(id,'marine',label,w,s,e,n,dict(source='curated_approximation'),priority)
for id,label,w,s,e,n,ores in [
 ('kryvbas','Криворізький залізорудний басейн',32.8,47.5,34.1,49.1,['hematite','magnetite']),
 ('kerch_iron','Керченські залізні руди',35.6,45,36.8,45.7,['limonite']),
 ('donbas_coal','Донбаський вугільний басейн',37,47,40,50,['bituminous_coal','pyrite']),
 ('lviv_volyn_coal','Львівсько-Волинський басейн',23.5,49.7,25.5,51.5,['bituminous_coal']),
 ('dnipro_lignite','Дніпровський буровугільний басейн',28,47.5,33.5,50.5,['lignite']),
 ('ukraine_nickel','Побузький нікеленосний район',29.5,47.8,31.3,49.2,['garnierite']),
 ('carpathian_salts','Передкарпатський соленосний район',23,48,25,50,['sylvite','gypsum','sulfur']),
 ('donets_salts','Бахмутський соленосний район',37.5,48.4,38.7,49.4,['sylvite','gypsum']),
 ('cornwall','Корнуольський рудний район',-5.8,49.8,-3.5,51.1,['cassiterite','native_copper']),
 ('iberian_pyrite','Іберійський піритовий пояс',-9,36.5,-5.5,38.5,['pyrite','native_copper','sphalerite']),
 ('kupferschiefer','Центральноєвропейський мідний пояс',10,50,18,53,['native_copper','native_silver']),
 ('andes_copper','Андійський мідний пояс',-74,-35,-67,-15,['native_copper','native_silver','native_gold']),
 ('central_african_copper','Центральноафриканський мідний пояс',23,-14,30,-8,['native_copper']),
 ('bushveld','Бушвелдський комплекс',26,-27,31,-23,['garnierite','magnetite']),
 ('pilbara','Пілбара',116,-24,122,-20,['hematite','magnetite']),
 ('carajas','Каражас',-52,-8,-48,-4,['hematite','native_copper']),
 ('minas_gerais','Мінас-Жерайс',-46,-22,-42,-18,['hematite','native_gold']),
 ('great_lakes_iron','Залізорудний район Великих озер',-94,45,-85,49,['hematite','magnetite']),
 ('appalachian_coal','Аппалацький вугільний басейн',-85,34,-76,42,['bituminous_coal']),
 ('ruhr','Рурський басейн',6,50.5,8.5,52,['bituminous_coal']),
 ('kuzbass','Кузбас',85,53,89,57,['bituminous_coal']),
 ('se_asian_tin','Олово Південно-Східної Азії',97,-4,108,14,['cassiterite']),
 ('yunnan_tin','Юньнанський оловоносний район',99,22,105,26,['cassiterite','sphalerite']),
 ('bolivia_tin','Болівійський оловоносний пояс',-69,-22,-65,-15,['cassiterite','native_silver']),
 ('norilsk','Норильський район',87,68,91,70,['garnierite','native_copper']),
 ('kazakhstan_copper','Центральноказахстанський мідний район',65,46,77,50,['native_copper']),
 ('canada_nickel','Садбері',-82,45.5,-80,47.5,['garnierite','native_copper']),
 ('boron_california','Каліфорнійські боратні басейни',-118,34,-115,37,['borax']),
 ('afghanistan_lapis','Бадахшанський лазурит',70,35,72,38,['lapis_lazuli']),
 ('colombia_emerald','Колумбійський смарагдовий район',-75,4,-72,7,['emerald']),
 ('kimberley','Кімберлійський алмазоносний район',23,-30,26,-27,['diamond']),
 ('siberian_diamonds','Якутські алмазоносні райони',110,61,121,68,['diamond']),
 ('sri_lanka_gems','Самоцвіти Шрі-Ланки',79.5,5.5,82,10,['ruby','sapphire','topaz']),
 ('australian_opal','Австралійські опалові поля',130,-33,149,-22,['opal']),
]:box(id,'deposit',label,w,s,e,n,dict(ores=ores,source='curated_province_approximation'))
plants={}
def plant(ids,minc,maxc,minrain,maxrain,months,eq=False):
    for id in ids:plants[id]=dict(min_c=minc,max_c=maxc,min_rain_mm=minrain,max_rain_mm=maxrain,local_months=months,equatorial_year_round=eq)
plant(['minecraft:wheat','minecraft:carrots','minecraft:potatoes','minecraft:beetroots'],3,32,250,1800,list(range(2,10)))
plant(['minecraft:cocoa','minecraft:jungle_sapling'],18,40,1200,5000,list(range(12)),True)
plant(['minecraft:oak_sapling','minecraft:birch_sapling'],2,33,400,2200,list(range(2,10)))
plant(['minecraft:spruce_sapling'],-3,25,300,2000,list(range(3,10)))
plant(['minecraft:acacia_sapling'],15,42,250,1600,list(range(12)),True)
plant(['minecraft:cactus'],8,48,0,500,list(range(12)),True)
plant(['minecraft:rice','tfc:crop/rice'],15,38,800,5000,list(range(3,10)),True)
plant(['tfc:crop/wheat','tfc:crop/barley','tfc:crop/rye','tfc:crop/oat'],2,32,250,1800,list(range(2,10)))
animals={}
def animal(ids,habitat,basins,mind=0,maxd=512):
    for id in ids:animals[id]=dict(habitat=habitat,basins=basins,min_depth=mind,max_depth=maxd)
animal(['minecraft:cod','tfc:cod'],'marine',['atlantic_north','arctic','baltic'],1,150)
animal(['minecraft:salmon','tfc:salmon','minecraft:axolotl'],'fresh',[],0,64)
animal(['minecraft:tropical_fish','tfc:tropical_fish'],'marine',['caribbean','gulf_mexico','red_sea','indian','pacific_west','pacific_east'],0,40)
animal(['minecraft:pufferfish','tfc:pufferfish'],'marine',['caribbean','gulf_mexico','mediterranean','red_sea','indian','pacific_west','pacific_east'],0,60)
animal(['minecraft:dolphin','tfc:dolphin'],'marine',['atlantic_north','atlantic_south','mediterranean','black_sea','caribbean','gulf_mexico','indian','pacific_west','pacific_east'],2,512)
animal(['minecraft:squid','tfc:squid'],'marine',['atlantic_north','atlantic_south','mediterranean','arctic','caribbean','gulf_mexico','indian','pacific_west','pacific_east','southern'],2,512)
animal(['tfc:orca'],'marine',['atlantic_north','atlantic_south','arctic','pacific_west','pacific_east','southern','indian'],5,512)
for id in ['minecraft:salmon','tfc:salmon']:
    animals[id]['habitats']=['fresh','marine']
    animals[id]['basins']=['atlantic_north','arctic','pacific_west','pacific_east']
for f in features:
    if f['layer']!='climate': continue
    weights=[1]*12
    if f['id']=='mediterranean':weights=[1.5,1.3,1.2,.8,.5,.2,.1,.2,.6,1.1,1.4,1.5]
    if f['id']=='india_monsoon':weights=[.1,.1,.2,.3,.6,1.6,2.4,2.2,1.7,.6,.15,.05]
    if f['id']=='ukraine_continental':weights=[.7,.65,.75,1,1.25,1.5,1.4,1.15,.9,.8,.8,.8]
    f['values']['monthly_rain_mm']=[round(f['values']['annual_rain_mm']*w/sum(weights),3) for w in weights]
root=dict(schema_version=1,projection=dict(name='plate_carree',blocks_per_degree=204.8,offset_x=0,offset_z=0,sea_level=63,metres_per_height_block=1),dimensions=['minecraft:overworld','minecraft:world_europe'],void_missing_chunks=True,features=features,plants=plants,aquatic_animals=animals)
(ROOT/'src/main/resources/earth-defaults.json').write_text(json.dumps(root,ensure_ascii=False,indent=2)+'\n')
print('Wrote',len(features),'regional features')
