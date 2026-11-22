// priority: -100
// Keep TFC steel refining as the source of steel. Create handles sheet and ring production.
ServerEvents.recipes(event => {
  var steel = 'tfc:metal/ingot/steel'
  var sheet = 'tfc:metal/sheet/steel'
  var nugget = 'empirecraft_armory:steel_nugget'
  event.remove({ id: 'magistuarmory:steel_plate' })
  event.remove({ id: 'magistuarmory:steel_ingot' })
  event.remove({ id: 'magistuarmory:steel_nugget' })
  event.remove({ id: 'magistuarmory:steel_block' })
  event.custom({ type: 'create:pressing', ingredients: [{ item: nugget }], results: [{ item: 'magistuarmory:steel_ring', count: 2 }] }).id('empirecraft:smithing/pressing/steel_rings')
  event.custom({ type: 'create:cutting', ingredients: [{ item: sheet }], results: [{ item: 'magistuarmory:small_steel_plate', count: 12 }], processingTime: 100 }).id('empirecraft:smithing/cutting/small_steel_plates')
})
