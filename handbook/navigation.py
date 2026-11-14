"""Native Patchouli category hierarchy; existing chapter and article IDs stay stable."""
GROUPS = [
    ('world', 'Світ', 'minecraft:compass', ['start', 'survival', 'time', 'travel', 'wildlife']),
    ('crafts', 'Ремесла', 'minecraft:crafting_table', ['stone', 'minerals', 'metals', 'pottery', 'machines', 'building']),
    ('homestead', 'Господарство', 'minecraft:wheat', ['crops', 'animals', 'food', 'drinks', 'leisure']),
    ('equipment', 'Спорядження', 'minecraft:iron_chestplate', ['combat', 'guns', 'siege', 'army', 'ships']),
    ('society', 'Суспільство', 'minecraft:emerald', ['towns', 'diplomacy', 'economy', 'map']),
]

def group_categories(categories, namespace):
    covered=[child for _,_,_,children in GROUPS for child in children]
    assert len(covered)==len(set(covered)) and set(covered)==set(categories), 'Chapter missing or duplicated in navigation'
    for order,(key,name,icon,children) in enumerate(GROUPS):
        for position,child in enumerate(children):
            categories[child].update(parent=namespace+':'+key,sortnum=position)
        categories[key]={'name':name,'description':'Вибери розділ зі списку. Усі статті також доступні через пошук та загальний покажчик.',
                         'icon':icon,'sortnum':order,'mods':sorted({m for child in children for m in categories[child]['mods']})}
    return {'root_categories':len(GROUPS),'chapters':len(covered),'groups':{key:children for key,_,_,children in GROUPS},
            'native_landing_columns':4,'existing_category_and_entry_ids_preserved':True}
