// priority: -300
// Keep basic writing materials available after all progression recipe passes.
// The handbook recipe also works when its optional server data pack was disabled.
ServerEvents.recipes(event => {
  const recipes = {
    'minecraft:paper': {
      type: 'minecraft:crafting_shaped', pattern: ['SSS'],
      key: {S: {item: 'minecraft:sugar_cane'}},
      result: {item: 'minecraft:paper', count: 3}
    },
    'minecraft:book': {
      type: 'minecraft:crafting_shapeless',
      ingredients: [{item: 'minecraft:paper'}, {item: 'minecraft:paper'}, {item: 'minecraft:paper'}, {tag: 'forge:leather'}],
      result: {item: 'minecraft:book'}
    },
    'empirecraft_guidebook:paper_from_unrefined': {
      type: 'tfc:damage_inputs_shapeless_crafting',
      recipe: {
        type: 'minecraft:crafting_shapeless',
        ingredients: [{item: 'tfc:unrefined_paper'}, [{tag: 'tfc:knives'}, {tag: 'forge:tools/knives'}]],
        result: {item: 'minecraft:paper'}
      }
    },
    'empirecraft_guidebook:handbook': {
      type: 'minecraft:crafting_shapeless', ingredients: [{item: 'minecraft:paper'}],
      result: {item: 'patchouli:guide_book', count: 1, nbt: {'patchouli:book': 'empirecraft_guidebook:handbook'}}
    }
  }
  Object.keys(recipes).forEach(id => {
    event.remove({id: id})
    event.custom(recipes[id]).id(id)
  })
  console.info('Empirecraft writing: paper, book, TFC paper finishing and handbook recipes restored')
})
