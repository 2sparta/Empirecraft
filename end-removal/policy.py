"""Reviewed End identities; an end table is furniture, and the bass remains an ordinary instrument."""
import re

PEARL = 'minecraft:ender_pearl'
PEARL_REPLACEMENT = 'minecraft:amethyst_shard'
PEARL_TAGS = {'forge:ender_pearls', 'c:ender_pearls', 'minecraft:ender_pearls'}
CHIPPED_FAMILIES = {'end_stone', 'purpur_block'}
KNIVES = {'farmersdelight:diamond_knife', 'farmersdelight:netherite_knife'}
CONTACT = {'contact:ender_parcel', 'contact:ender_postcard', 'contact:ender_wrapping_paper'}
VANILLA = set('''end_stone end_stone_bricks end_stone_brick_stairs end_stone_brick_slab
end_stone_brick_wall purpur_block purpur_pillar purpur_stairs purpur_slab
chorus_plant chorus_flower chorus_fruit popped_chorus_fruit dragon_head dragon_wall_head
dragon_breath dragon_egg end_rod elytra shulker_shell shulker_box ender_pearl ender_eye
ender_chest end_crystal end_portal end_portal_frame end_gateway
enderman_spawn_egg endermite_spawn_egg shulker_spawn_egg ender_dragon_spawn_egg'''.split())
VANILLA.update(color + '_shulker_box' for color in '''white orange magenta light_blue yellow lime pink gray
light_gray cyan purple blue brown green red black'''.split())
ENTITIES = {'minecraft:' + name for name in '''enderman endermite shulker ender_dragon ender_pearl
eye_of_ender end_crystal shulker_bullet dragon_fireball'''.split()}
PRESERVED = {'immersive_melodies:ender_bass', 'immersive_melodies:ender_bass_hand'}

def end_id(identity):
    if identity in PRESERVED:
        return False
    namespace, name = identity.split(':', 1)
    if '/' in name:
        return False  # A nested render model is not a registered item.
    if namespace == 'minecraft':
        return name in VANILLA
    return bool(re.search(r'end_stone|endstone|end_brick|ender_brick|purpur|chorus|shulker|elytra|'
                          r'(^|_)ender(_|$)|^end_(?!table(?:_|$))|dragon_(?:head|breath|egg)', name))

def replacement(identity, blocks):
    if identity in KNIVES:
        return 'farmersdelight:iron_knife'
    if identity in CONTACT:
        return identity.replace(':ender_', ':')
    if identity == PEARL:
        return PEARL_REPLACEMENT
    if identity.endswith('shulker_box') or identity == 'minecraft:ender_chest':
        return 'minecraft:chest'
    if identity in blocks:
        return 'minecraft:stone'
    if 'chorus' in identity:
        return 'minecraft:apple'
    return 'minecraft:paper'
