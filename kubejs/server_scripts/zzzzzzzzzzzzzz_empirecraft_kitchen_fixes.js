// priority: -22000
// Runs after the shared food, metallurgy and kitchen compatibility passes.
ServerEvents.recipes(function (event) {
  var edits = {};
  function rewrite(value) {
    if (typeof value === 'string') return value === 'tfc:food/cooked_egg' || value === 'farmersdelight:fried_egg' ? 'naturalist:cooked_egg' : value;
    if (Array.isArray(value)) return value.map(rewrite);
    if (value && typeof value === 'object') { var result = {}; Object.keys(value).forEach(function (key) { result[key] = rewrite(value[key]); }); return result; }
    return value;
  }
  function normalize(recipe) {
    if (recipe.removed) return;
    recipe.serialize();
    var text = recipe.json.toString();
    if (text.indexOf('tfc:food/cooked_egg') >= 0 || text.indexOf('farmersdelight:fried_egg') >= 0)
      edits[String(recipe.getId())] = rewrite(JSON.parse(text));
  }
  event.forEachRecipe({}, normalize);
  event.addedRecipes.forEach(normalize);
  Object.keys(edits).forEach(function (id) {
    event.addedRecipes.removeIf(function (recipe) { return String(recipe.getId()) === id; });
    event.remove({id:id});
    event.custom(edits[id]).id(id);
  });
});
