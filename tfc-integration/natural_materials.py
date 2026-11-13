"""Acquisition bridges for retained pottery and glass, using canonical existing ores."""
def generate(recipe,add,tag):
    base='empirecraft_tfc:'
    # Ore dust is a consumable colourant, not a second metal ingot/ore provider.
    for source,powder in [('minecraft:raw_iron','hematite'),('minecraft:raw_copper','native_copper'),
                          ('minecraft:raw_gold','native_gold'),('antiquelegacy:raw_tin','cassiterite')]:
        recipe(base+'quern/pigment_'+powder,{'type':'tfc:quern','ingredient':{'item':source},
            'result':{'item':'tfc:powder/'+powder,'count':5}})
    recipe(base+'quern/glass_sulfur',{'type':'tfc:quern','ingredient':{'item':'sulfur_gunpowder:sulfur'},
        'result':{'item':'tfc:powder/sulfur'}})
    for source,color in [('sand','white'),('red_sand','red')]:
        recipe(base+'quern/glass_sand_'+color,{'type':'tfc:quern','ingredient':{'item':'minecraft:'+source},
            'result':{'item':'tfc:sand/'+color}})
    recipe(base+'heating/soda_ash_from_vanilla_kelp',{'type':'tfc:heating','ingredient':{'item':'minecraft:dried_kelp'},
        'result_item':{'item':'tfc:powder/soda_ash','count':3},'temperature':500})
    add('data/empirecraft_tfc/tfc/item_heats/dried_kelp.json',{'ingredient':{'item':'minecraft:dried_kelp'},'heat_capacity':1.0})
    tag('tfc','powders',['minecraft:charcoal'])
    for language,label in [('en_us','Charcoal'),('uk_ua','Деревне вугілля'),('ru_ru','Древесный уголь')]:
        add('assets/tfc/lang/'+language+'.json',{'tfc.enum.glassoperation.graphite':label})
