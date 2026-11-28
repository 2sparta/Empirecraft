// Content cut from Weavers' Paradise for Empirecraft realism.
// The patched JAR no longer ships these recipes; this also cleans them out of
// worlds saved before the patch. Source: weaversparadise-integration/audit/removed.json
const EC_WP_CUT = [
  'weaversparadise:jeans_cloth',
  'weaversparadise:jeans_pant_leg',
  'weaversparadise:jeans_spool',
  'weaversparadise:pants_jeans',
  'weaversparadise:cotton_hoodie',
  'weaversparadise:wool_hoodie',
  'weaversparadise:t_shirt',
  'weaversparadise:tank_top',
  'weaversparadise:cotton_crop_top_long_sleeved',
  'weaversparadise:overalls',
  'weaversparadise:ushanka',
  'weaversparadise:thigh_highs_cotton',
  'weaversparadise:thigh_highs_silk',
  'weaversparadise:thigh_highs_wool',
  'weaversparadise:spinning_jenny',
  'weaversparadise:chromatic_bloom_fruit',
  'weaversparadise:chromatic_dust',
  'weaversparadise:starbloom_fruit',
  'weaversparadise:witchroot_fruit',
  'weaversparadise:witchroot_fruit_cooked',
  'weaversparadise:flag_base',
  'weaversparadise:flag_agender',
  'weaversparadise:flag_aroace',
  'weaversparadise:flag_aromantic',
  'weaversparadise:flag_asexual',
  'weaversparadise:flag_bisexual',
  'weaversparadise:flag_demiboy',
  'weaversparadise:flag_demigender',
  'weaversparadise:flag_demigirl',
  'weaversparadise:flag_gay',
  'weaversparadise:flag_genderfluid',
  'weaversparadise:flag_genderqueer',
  'weaversparadise:flag_intersex',
  'weaversparadise:flag_lesbian',
  'weaversparadise:flag_nonbinary',
  'weaversparadise:flag_pansexual',
  'weaversparadise:flag_pride',
  'weaversparadise:flag_trans',
  'weaversparadise:flag_transfeminine',
  'weaversparadise:flag_transmasculine',
  'weaversparadise:dye_core'
]

ServerEvents.recipes(event => {
  EC_WP_CUT.forEach(id => {
    event.remove({ output: id })
    event.remove({ input: id })
  })
})
