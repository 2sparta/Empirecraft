// Plate compatibility remains separate from the native removal of duplicate raw metals.
const EC_LEGACY_PLATES = {
  'magistuarmory:steel_plate': 'overgeared:steel_plate',
  'overgeared:iron_plate': 'create:iron_sheet',
  'antiquelegacy:iron_plate': 'create:iron_sheet',
  'overgeared:copper_plate': 'create:copper_sheet'
}
ServerEvents.tags('item', event => {
  var canonical = {
    'forge:ingots/steel': 'overgeared:steel_ingot',
    'forge:nuggets/steel': 'overgeared:steel_nugget',
    'forge:plates/steel': 'overgeared:steel_plate',
    'forge:storage_blocks/steel': 'overgeared:steel_block',
    'forge:plates/iron': 'create:iron_sheet',
    'forge:plates/copper': 'create:copper_sheet'
  }
  Object.keys(canonical).forEach(tag => { event.removeAll(tag); event.add(tag, canonical[tag]) })
  Object.keys(EC_LEGACY_PLATES).forEach(id => event.removeAllTagsFrom(id))
})
PlayerEvents.inventoryChanged(event => {
  if (!EC_LEGACY_PLATES[String(event.item.id)]) return
  var inv = event.player.inventory
  for (var i = 0; i < 41; i++) {
    var stack = inv.getItem(i)
    var replacement = EC_LEGACY_PLATES[String(stack.id)]
    if (replacement) inv.setItem(i, Item.of(replacement, stack.count, stack.nbt))
  }
})
ItemEvents.entityInteracted(event => {
  var Villager = Java.loadClass('net.minecraft.world.entity.npc.AbstractVillager')
  if (!(event.target instanceof Villager)) return
  var raw = ['overgeared:steel_ingot', 'overgeared:steel_nugget', 'overgeared:steel_plate', 'overgeared:steel_block', 'overgeared:crude_steel', 'magistuarmory:steel_plate']
  var offers = typeof event.target.getOffers === 'function' ? event.target.getOffers() : event.target.m_6616_()
  offers.removeIf(offer => {
    var result = typeof offer.getResult === 'function' ? offer.getResult() : offer.m_45368_()
    return raw.indexOf(String(result.id)) >= 0
  })
})
