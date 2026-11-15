// priority: -10000
ServerEvents.recipes(event => {
  var Policy = Java.loadClass('ua.empirecraft.casting.FoodPolicy')
  var edits = []
  event.forEachRecipe({}, recipe => {
    recipe.serialize()
    var before = recipe.json.toString()
    var after = Policy.recipe(recipe.json)
    if (after === null || String(after) !== String(before)) edits.push({ id: String(recipe.getId()), data: after })
  })
  edits.forEach(edit => { event.remove({id:edit.id}); if(edit.data !== null) event.custom(edit.data).id(edit.id) })
})
ServerEvents.tags('item', event => {
  Java.loadClass('ua.empirecraft.casting.FoodPolicy').retiredItemIds().forEach(id => event.removeAllTagsFrom(String(id)))
  event.remove('tfc:fluid_item_ingredient_empty_containers', ['tfc:metal/bucket/red_steel','tfc:metal/bucket/blue_steel'])
  event.remove('forge:dough', ['create:dough','farmersdelight:wheat_dough'])
  event.remove('forge:dough/wheat', ['create:dough','farmersdelight:wheat_dough'])
})
