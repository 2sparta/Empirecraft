"""TFC moulds replace legacy casts; the uploaded Muskets addon supplies gun parts."""
import json, zipfile
from pathlib import Path

def generate(recipe, add, tag, forging, decisions, tfc):
    ns='empirecraft_tfc'
    # Keep native 100-unit TFC moulds. Extra metal is added when a larger
    # Overgeared head is finished, rather than creating metal during recycling.
    metals={'copper':('copper','copper','minecraft:copper_ingot'),
            'iron':('wrought_iron','wrought_iron','minecraft:iron_ingot'),
            'steel':('steel','steel','overgeared:steel_ingot'),
            'golden':('gold','gold','minecraft:gold_ingot')}
    costs={'axe':3,'pickaxe':3,'hoe':2,'shovel':1,'hammer':2,'sword':2}
    matrix=[]
    with zipfile.ZipFile(tfc) as z:
        temperatures={n.rsplit('/',1)[-1][:-5]:json.loads(z.read(n))['melt_temperature']
            for n in z.namelist() if n.startswith('data/tfc/tfc/metals/') and n.endswith('.json')}
    for prefix,(fluid,head_metal,ingot) in metals.items():
        for tool,cost in costs.items():
            if tool=='hammer' and prefix not in {'copper','steel'}:continue
            shape='sword_blade' if tool=='sword' else tool+'_head'
            output='overgeared:'+prefix+'_'+shape
            blank=(ns+':golden_'+shape+'_blank' if prefix=='golden'
                   else 'tfc:metal/'+shape+'/'+head_metal)
            # Sword blades go straight into the existing weapon production.
            # No retired TFC swords, maces or TFC anvil recipes are restored.
            if tool=='sword':blank=output
            amount=200 if tool=='sword' else 100
            result={'item':blank}
            if tool=='sword':result['nbt']='{Polished:0b}'
            for liquid in ([fluid,'cast_iron'] if prefix=='iron' else [fluid]):
                recipe(ns+':casting/'+prefix+'_'+shape+'_'+liquid,{
                    'type':'tfc:casting','mold':{'item':'tfc:ceramic/'+shape+'_mold'},
                    'fluid':{'ingredient':'tfc:metal/'+liquid,'amount':amount},
                    'result':result,'break_chance':1.0})
            if tool!='sword':
                ingredients=[{'item':blank}]+[{'item':ingot}]*(cost-1)
                # The real tool-damaging serializer preserves the hammer.
                ingredients.append({'tag':'tfc:hammers'})
                recipe(ns+':casting/finish_'+prefix+'_'+shape,{
                    'type':'tfc:damage_inputs_shapeless_crafting','recipe':{
                        'type':'minecraft:crafting_shapeless','ingredients':ingredients,
                        'result':{'item':output,'nbt':'{Polished:0b}'}}})
            recipe(ns+':heating/cast_'+prefix+'_'+shape,{
                'type':'tfc:heating','ingredient':{'item':output},
                'temperature':temperatures[fluid],
                'result_fluid':{'fluid':'tfc:metal/'+fluid,'amount':cost*100}})
            add('data/'+ns+'/tfc/item_heats/cast_'+prefix+'_'+shape+'.json',{
                'ingredient':{'item':output},'heat_capacity':cost})
            if prefix=='golden' and tool!='sword':
                add('assets/'+ns+'/models/item/golden_'+shape+'_blank.json',{
                    'parent':'minecraft:item/generated','textures':{'layer0':'overgeared:item/golden_'+shape}})
                add('data/'+ns+'/tfc/item_heats/golden_'+shape+'_blank.json',{
                    'ingredient':{'item':blank},'heat_capacity':1})
                recipe(ns+':heating/golden_'+shape+'_blank',{'type':'tfc:heating',
                    'ingredient':{'item':blank},'temperature':temperatures['gold'],
                    'result_fluid':{'fluid':'tfc:metal/gold','amount':100}})
            matrix.append(dict(metal=prefix,tool=tool,mold='tfc:ceramic/'+shape+'_mold',
                blank=blank,output=output,cast_units=amount,extra_ingots=0 if tool=='sword' else cost-1,total_units=cost*100))
    add('empirecraft-tfc-casting-parity.json',matrix)
    add('data/tfc/tags/fluids/usable_in_tool_head_mold.json',{
        'replace':False,'values':['tfc:metal/'+m for m in ['gold','wrought_iron','cast_iron','steel']]})
    # Addon recipes are generated here; its original recipes depend on TFC Items
    # and the retired anvil. Existing hard gun assembly stays on Overgeared.
    recipe(ns+':gun_barrel',{'type':'create:cutting','ingredients':[{'item':ns+':barrel_blank'}],
        'results':[{'item':'tfc_muskets:musket_barrel'}],'processingTime':200})
    recipe(ns+':blunderbuss_barrel',forging(['PPP',' B ','PPP'],{
        'P':{'item':'create:brass_sheet'},'B':{'item':'tfc_muskets:musket_barrel'}},'tfc_muskets:blunderbuss_barrel'))
    recipe(ns+':gun_stock',{'type':'create:cutting','ingredients':[{'tag':'minecraft:logs'}],
        'results':[{'item':'tfc_muskets:wooden_stock','count':2}],'processingTime':100})
    recipe('tfc_muskets:wooden_stock',{'type':'tfc:damage_inputs_shapeless_crafting','recipe':{
        'type':'minecraft:crafting_shapeless','ingredients':[{'tag':'tfc:lumber'},{'tag':'tfc:hammers'},{'tag':'tfc:chisels'}],
        'result':{'item':'tfc_muskets:wooden_stock'}}})
    # Keep the old item ID readable in saves, with a texture from the base game.
    # Old rods may still be consumed; new ammunition uses existing iron/cast iron.
    add('assets/'+ns+'/models/item/lead_rod.json',{'parent':'minecraft:item/generated',
        'textures':{'layer0':'minecraft:item/iron_nugget'}})
    tag(ns,'metal_rods/wrought_iron',['tfc:metal/rod/wrought_iron',ns+':lead_rod'])
    for metal in ['wrought_iron','cast_iron']:
        recipe('tfc_muskets:scrap_'+metal,{'type':'tfc:damage_inputs_shapeless_crafting','recipe':{
            'type':'minecraft:crafting_shapeless','ingredients':[{'tag':ns+':metal_rods/'+metal},{'tag':'tfc:hammers'},{'tag':'tfc:chisels'}],
            'result':{'item':'tfc_muskets:scrap','count':5}}})
    tag('forge','sandpaper',['create:sand_paper','create:red_sand_paper'])
    recipe('tfc_muskets:bullet',{'type':'tfc:damage_inputs_shapeless_crafting','recipe':{
        'type':'minecraft:crafting_shapeless','ingredients':[{'item':'tfc_muskets:scrap'},{'tag':'forge:sandpaper'}],
        'result':{'item':'tfc_muskets:bullet'}}})
    # The pack deliberately retires Create's sandpaper recipe type. Reuse its
    # enabled millstone and the existing Create-driven TFC quern instead.
    recipe('tfc_muskets:polishing/bullet',{'type':'create:milling',
        'ingredients':[{'item':'tfc_muskets:scrap'}],'results':[{'item':'tfc_muskets:bullet'}],'processingTime':100})
    recipe('tfc_muskets:quern/bullet',{'type':'tfc:quern','ingredient':{'item':'tfc_muskets:scrap'},
        'result':{'item':'tfc_muskets:bullet'}})
    recipe('tfc_muskets:gunpowder_pinch',{'type':'minecraft:crafting_shapeless',
        'ingredients':[{'item':'minecraft:gunpowder'}],'result':{'item':'tfc_muskets:gunpowder_pinch','count':9}})
    recipe('tfc_muskets:gunpowder',{'type':'minecraft:crafting_shaped','pattern':['PPP','PPP','PPP'],
        'key':{'P':{'item':'tfc_muskets:gunpowder_pinch'}},'result':{'item':'minecraft:gunpowder'}})
    recipe('musketmod:cartridge',{'type':'minecraft:crafting_shapeless','ingredients':[
        {'item':'tfc_muskets:bullet'},{'item':'tfc_muskets:gunpowder_pinch'},{'item':'minecraft:paper'}],
        'result':{'item':'musketmod:cartridge'}})
