// Empirecraft: migrate legacy raw metals without deleting, giving or merging stacks.
// Production restrictions and recipe integration run last in zzzz_empirecraft_smithing.js.
const EC_LEGACY_METALS = {
  'magistuarmory:steel_ingot': 'overgeared:steel_ingot',
  'magistuarmory:steel_nugget': 'overgeared:steel_nugget',
  'magistuarmory:steel_plate': 'overgeared:steel_plate',
  'overgeared:iron_plate': 'create:iron_sheet',
  'antiquelegacy:iron_plate': 'create:iron_sheet',
  'overgeared:copper_plate': 'create:copper_sheet'
}

ServerEvents.tags('item', event => {
  const canonical = {
    'forge:ingots/steel': 'overgeared:steel_ingot',
    'forge:nuggets/steel': 'overgeared:steel_nugget',
    'forge:plates/steel': 'overgeared:steel_plate',
    'forge:storage_blocks/steel': 'overgeared:steel_block',
    'forge:plates/iron': 'create:iron_sheet',
    'forge:plates/copper': 'create:copper_sheet'
  }
  Object.keys(canonical).forEach(tag => {
    event.removeAll(tag)
    event.add(tag, canonical[tag])
  })
  Object.keys(EC_LEGACY_METALS).forEach(id => event.removeAllTagsFrom(id))
})

PlayerEvents.inventoryChanged(event => {
  if (!EC_LEGACY_METALS[String(event.item.id)]) return
  const inv = event.player.inventory
  // KubeJS 6 reports a MENU slot number, which differs between chests and the player screen.
  // Use actual player inventory indices so an unrelated slot can never be overwritten.
  for (let i = 0; i < 41; i++) {
    const stack = inv.getItem(i)
    const replacement = EC_LEGACY_METALS[String(stack.id)]
    if (replacement) inv.setItem(i, Item.of(replacement, stack.count, stack.nbt))
  }
})

// Remove already-saved steel sales before opening an old villager's trading screen.
// New villagers and later profession levels use the cleaned factories from the startup hook.
ItemEvents.entityInteracted(event => {
  const Villager = Java.loadClass('net.minecraft.world.entity.npc.AbstractVillager')
  if (!(event.target instanceof Villager)) return
  const raw = ['overgeared:steel_ingot', 'overgeared:steel_nugget', 'overgeared:steel_plate', 'overgeared:steel_block', 'overgeared:crude_steel', 'magistuarmory:steel_ingot', 'magistuarmory:steel_nugget', 'magistuarmory:steel_plate']
  const offers = typeof event.target.getOffers === 'function' ? event.target.getOffers() : event.target.m_6616_()
  offers.removeIf(offer => {
    const result = typeof offer.getResult === 'function' ? offer.getResult() : offer.m_45368_()
    return raw.indexOf(String(result.id)) >= 0
  })
})
