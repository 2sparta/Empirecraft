"""Explicit medieval Create 6.0.8 profile; unlisted Create content is disabled."""
import re

CORE = set('''andesite_alloy andesite_alloy_block andesite_bars andesite_casing
andesite_door andesite_encased_cogwheel andesite_encased_large_cogwheel
andesite_encased_shaft andesite_funnel andesite_belt_funnel andesite_ladder
andesite_scaffolding andesite_tunnel brass_bars brass_block brass_casing brass_door
brass_encased_cogwheel brass_encased_large_cogwheel brass_encased_shaft brass_ingot
brass_ladder brass_nugget brass_scaffolding brass_sheet copper_bars copper_casing
andesite_table_cloth brass_table_cloth copper_table_cloth
copper_door copper_ladder copper_scaffolding copper_sheet copper_nugget
belt belt_connector chute clipboard clutch cogwheel contraption_controls copycat_bars
encased_chain_drive adjustable_chain_gearshift
copycat_base copycat_panel copycat_step crafting_blueprint cuckoo_clock depot desk_bell
dough empty_schematic framed_glass framed_glass_door framed_glass_pane
framed_glass_trapdoor gantry_carriage gantry_shaft gearbox gearshift goggles golden_sheet
hand_crank honey_bucket honeyed_apple horizontal_framed_glass horizontal_framed_glass_pane
iron_sheet large_cogwheel large_water_wheel linear_chassis mechanical_bearing
mechanical_piston mechanical_piston_head mechanical_press mechanical_saw metal_bracket
millstone piston_extension_pole placard pulley_magnet radial_chassis raw_zinc raw_zinc_block
red_sand_paper rope rope_pulley sail_frame sand_paper schematic schematic_and_quill
schematic_table secondary_linear_chassis sequenced_gearshift shaft sticker sticky_mechanical_piston
super_glue sweet_roll tiled_glass tiled_glass_pane turntable vertical_framed_glass
vertical_framed_glass_pane vertical_gearbox water_wheel water_wheel_structure
wheat_flour white_sail windmill_bearing wooden_bracket wrench zinc_block zinc_ingot
zinc_nugget zinc_ore deepslate_zinc_ore analog_lever redstone_contact'''.split())
COLORS = 'white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
for color in COLORS:
    CORE.update(color + '_' + tail for tail in ('sail', 'seat', 'table_cloth', 'toolbox', 'valve_handle'))
CORE.add('copper_valve_handle')
STONES = 'andesite asurine calcite crimsite deepslate diorite dripstone granite limestone ochrum scorchia scoria tuff veridium'.split()
DECOR_PATTERNS = [
    re.compile(r'(?:cut_|polished_cut_|small_|layered_)?(?:' + '|'.join(STONES) + r')(?:_pillar|_bricks?|_brick_(?:slab|stairs|wall)|_(?:slab|stairs|wall))?'),
    re.compile(r'(?:acacia|bamboo|birch|cherry|crimson|dark_oak|jungle|mangrove|oak|spruce|warped)_window(?:_pane)?'),
    re.compile(r'(?:waxed_)?(?:(?:exposed|weathered|oxidized)_)?copper_(?:shingles|tiles|shingle_(?:slab|stairs)|tile_(?:slab|stairs))'),
    re.compile(r'ornate_iron_window(?:_pane)?'),
]

def allowed(item_id):
    if not item_id.startswith('create:'):
        return True
    name = item_id.split(':', 1)[1]
    return name in CORE or any(p.fullmatch(name) for p in DECOR_PATTERNS)

ALLOWED_TYPES = {
    'minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:stonecutting',
    'minecraft:smelting', 'minecraft:blasting', 'minecraft:campfire_cooking', 'minecraft:smoking',
    'create:cutting', 'create:pressing', 'create:milling', 'create:item_application',
    'create:item_copying', 'create:toolbox_dyeing',
}
DISABLED_TYPES = {'create:' + x for x in '''splashing haunting crushing deploying filling
emptying sequenced_assembly mechanical_crafting mixing compacting sandpaper_polishing'''.split()}

# Water mills may grind food/dyes, but may not turn renewable stone into metal or XP.
METAL_OUTPUT = re.compile(r'(?:iron|gold|copper|zinc|brass|silver|tin|lead|nickel|aluminum|aluminium|osmium|uranium|platinum|quicksilver|experience|raw_|crushed_)')

def recipe_reason(recipe):
    typ = recipe.get('type')
    if typ not in ALLOWED_TYPES:
        return 'disabled processing type'
    def strings(value):
        if isinstance(value, dict):
            for key, val in value.items():
                if key in ('item', 'result') and isinstance(val, str):
                    yield val
                else:
                    yield from strings(val)
        elif isinstance(value, list):
            for val in value:
                yield from strings(val)
    if any(not allowed(item) for item in strings(recipe)):
        return 'disabled ingredient or result'
    if typ == 'create:milling':
        if any(METAL_OUTPUT.search(item.split(':')[-1]) for item in strings(recipe.get('results', []))):
            return 'metal or experience milling'
    return None
