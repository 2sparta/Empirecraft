// priority: -310
// Run after the writing/progression passes; retain Create IDs for JEI and recipe sync.
ServerEvents.recipes(event => {
  const recipes = {
    "create:crafting/materials/sand_paper": {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:paper"}, [{"tag": "forge:sand/colorless"}, {"item": "tfc:sand/white"}, {"item": "tfc:sand/yellow"}, {"item": "tfc:sand/black"}, {"item": "tfc:sand/green"}, {"item": "tfc:sand/brown"}]], "result": {"item": "create:sand_paper"}},
    "create:crafting/materials/red_sand_paper": {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:paper"}, [{"tag": "forge:sand/red"}, {"item": "tfc:sand/red"}, {"item": "tfc:sand/pink"}]], "result": {"item": "create:red_sand_paper"}}
  }
  Object.keys(recipes).forEach(id => {
    event.remove({id: id})
    event.custom(recipes[id]).id(id)
  })
  console.info('Empirecraft abrasives: both Create sandpaper recipes accept matching TFC sands')
})
