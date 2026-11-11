#!/usr/bin/env python3
"""Cosmetic horse markings must survive the Nether-content filter."""
from policy import blocked
from build import trim
assert not blocked('horse_colors:blaze')
assert not blocked('horse_colors:broad_blaze')
assert blocked('minecraft:blaze') and blocked('minecraft:blaze_rod')
files={'assets/horse_colors/textures/entity/horse/face/blaze.png':b'original pixels',
 'assets/horse_colors/textures/entity/horse/face/broad_blaze.png':b'original broad pixels',
 'assets/dragnlivestock/textures/entity/horse/overlay/blaze.png':b'DLO pixels',
 'assets/horse_colors/textures/item/netherite_horse_armor.png':b'blocked armor'}
report={'deleted':[],'rewritten':[]}
result=trim(files,{'horse_colors:blaze','horse_colors:broad_blaze','dragnlivestock:blaze','horse_colors:netherite_horse_armor'},{},report)
assert len(result)==3 and all(result[n]==data for n,data in files.items() if '/textures/entity/' in n)
assert report['deleted']==['assets/horse_colors/textures/item/netherite_horse_armor.png']
print('HORSE_MARKINGS_FILTER_PASS preserved all entity markings; Nether item remains blocked')
