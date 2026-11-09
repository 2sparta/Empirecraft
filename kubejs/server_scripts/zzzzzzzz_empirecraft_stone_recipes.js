// priority: -10000
// Restore the vanilla stone family after equipment conversion and retirement passes.
ServerEvents.recipes(function(event) {
  event.remove({id:'minecraft:stone_slab'})
  event.shaped('6x minecraft:stone_slab',['SSS'],{S:'minecraft:stone'}).id('minecraft:stone_slab')
  event.remove({id:'minecraft:smooth_stone'})
  event.smelting('minecraft:smooth_stone','minecraft:stone',0.1,200).id('minecraft:smooth_stone')
  event.remove({id:'minecraft:smooth_stone_slab'})
  event.shaped('6x minecraft:smooth_stone_slab',['SSS'],{S:'minecraft:smooth_stone'}).id('minecraft:smooth_stone_slab')
  event.remove({id:'minecraft:stone_slab_from_stone_stonecutting'})
  event.stonecutting('2x minecraft:stone_slab','minecraft:stone').id('minecraft:stone_slab_from_stone_stonecutting')
  event.remove({id:'minecraft:smooth_stone_slab_from_smooth_stone_stonecutting'})
  event.stonecutting('2x minecraft:smooth_stone_slab','minecraft:smooth_stone').id('minecraft:smooth_stone_slab_from_smooth_stone_stonecutting')
})
