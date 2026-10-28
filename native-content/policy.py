"""Gameplay identities for the installed 1.20.1 pack, with reviewed false positives."""
import re

EXEMPT={'naturalist:crimson_froglass','naturalist:crimson_froglass_pane'}
METALS={
 'magistuarmory:steel_ingot':'overgeared:steel_ingot',
 'magistuarmory:steel_nugget':'overgeared:steel_nugget',
 'antiquelegacy:bronze_ingot':'createbigcannons:bronze_ingot',
 'antiquelegacy:bronze_nugget':'createbigcannons:bronze_scrap',
}
THEME=re.compile(r'nether|netherrack|blackstone|basalt|glowstone|quartz|crimson|warped|'
 r'(?:^|_)soul(?:_|$)|(?:^|_)magma|magmatic|(?:^|_)blaze(?:_|$)|(?:^|_)ghast(?:_|$)|'
 r'(?:^|_)wither(?:_|$)|(?:^|_)ender(?:_|$)|end_stone|endstone|end_brick|purpur|chorus|shulker|elytra|'
 r'^end_(?!table(?:_|$))|dragon_(?:head|breath|egg)')

def blocked(identity):
 if identity in EXEMPT:return False
 ns,name=identity.split(':',1)
 if '/' in name:return False
 return identity in METALS or bool(THEME.search(name)) or (ns=='create' and (name.endswith('_door') or name=='framed_glass_trapdoor'))

def alias(identity,all_ids,blocks):
 if identity in METALS:return METALS[identity]
 ns,name=identity.split(':',1)
 variants=[name]
 for old,new in [('crimson','oak'),('warped','oak'),('nethersteel','bronze'),('netherite','steel' if ns in {'magistuarmory','magistuarmoryaddon','antiquelegacy','slavicarmory','darkagesarmory'} else 'iron'),('red_nether_brick','stone_brick'),('nether_brick','stone_brick'),('end_stone_brick','stone_brick'),('end_stone','stone'),('endstone','stone'),('blackstone','cobblestone'),('basalt','stone'),('quartz','stone'),('purpur','stone'),('chorus','apple'),('soul_',''),('_soul',''),('ender_',''),('_ender',''),('magma_',''),('_magma','')]:
  variants += [v.replace(old,new) for v in list(variants) if old in v]
 for name in variants[1:]:
  target=ns+':'+name
  if target in all_ids and not blocked(target):return target
 if identity in blocks:
  for suffix,target in [('trapdoor','oak_trapdoor'),('door','oak_door'),('stairs','stone_stairs'),('slab','stone_slab'),('wall','cobblestone_wall'),('fence','oak_fence'),('chest','chest')]:
   if name.endswith(suffix):return 'minecraft:'+target
  return 'minecraft:stone'
 if name.endswith('_bucket'):return 'minecraft:bucket'
 if 'horse_armor' in name:return 'minecraft:iron_horse_armor'
 if 'ender_bass' in name:return 'immersive_melodies:lute'
 if any(x in name for x in ['jelly','jello','salad','sourdough','crab','fish']):return 'minecraft:apple'
 return 'minecraft:paper'
