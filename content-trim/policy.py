"""Remove Nether material/decor variants from Chipped; preserve every glowstone variant."""
FAMILIES = '''ancient_debris netherite_block lodestone blackstone gilded_blackstone basalt
red_nether_bricks crimson_door crimson_fungus crimson_planks crimson_roots
crimson_stem crimson_trapdoor stripped_crimson_stem warped_door warped_fungus
warped_planks warped_roots warped_stem warped_trapdoor stripped_warped_stem
warped_wart_block nether_wart_block nether_sprouts shroomlight
ochre_froglight pearlescent_froglight verdant_froglight
netherrack nether_bricks quartz_block magma_block soul_sand soul_lantern
special_soul_lantern crying_obsidian'''.split()
# Explicit variants in otherwise preserved Overworld palettes (including wall torches).
MIXED_BLOCKS = '''basalt_pointed_dripstone blackstone_pointed_dripstone
crimson_barrel crimson_crate crimson_dried_kelp_block crimson_mangrove_roots
crimson_redstone_torch crimson_redstone_wall_torch crimson_torch crimson_wall_torch
gilded_basalt_bone_block nether_carved_pumpkin nether_jack_o_lantern nether_pumpkin
nether_vine netherite_safe netherrack_pointed_dripstone netherwart_dried_kelp_block
reinforced_crimson_crate reinforced_warped_crate rough_crimson_dried_kelp_block
smooth_basalt_pointed_dripstone warped_crate warped_torch warped_vine
warped_wall_torch'''.split()
SHIELDS = 'heatershield target buckler rondache tartsche ellipticalshield roundshield pavese kiteshield'.split()
MOBS = '''creeper spider cave_spider zombie zombie_villager husk drowned skeleton
stray wither_skeleton witch slime magma_cube phantom pillager'''.split()
INPUTS = {
 'epic-knights-all-in-one-1_20_1-forge (1).jar': '01fc26e1416fb38c3d3cf8cd8c9c5458690f4bd2ee58a534ae1641a2bf42816a',
 'chipped-forge-1.20.1-3.0.7.jar': '300e02535342b710fb34409821de9d097b0d8b17f56a57a17d75d05c4c1c5b63',
 'chippedexttras-1.4.jar': '3e12ded6edd96547ff91b1b524e2f4b3bd513e090f29b9d7f03894c3598cd5f1',
}

