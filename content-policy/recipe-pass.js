ServerEvents.recipes(event => {
  var knightNamespaces = ['magistuarmory','magistuarmoryaddon','antiquelegacy','darkagesarmory','slavicarmory','epic_knights__japanese_armory']
  function strings(value) {
    if (typeof value === 'string') return [value]
    if (!value || typeof value !== 'object') return []
    var result = []
    Object.keys(value).forEach(key => result = result.concat(strings(value[key])))
    return result
  }
  function premium(value) {
    if (typeof value === 'string') {
      if (value === 'minecraft:blaze_powder') return 'minecraft:diamond'
      if (value === 'minecraft:netherite_upgrade_smithing_template') return 'overgeared:steel_block'
      return value
    }
    if (Array.isArray(value)) return value.map(premium)
    if (!value || typeof value !== 'object') return value
    var result = {}
    Object.keys(value).forEach(key => result[key] = premium(value[key]))
    return result
  }
  var changes = []
  event.forEachRecipe({}, recipe => {
    recipe.serialize()
    var data = JSON.parse(recipe.json.toString())
    var id = String(recipe.getId())
    if (data.type === 'overgeared:item_to_tooltype' && Array.isArray(data.item)) {
      var kept = data.item.filter(ingredient => EC_REMOVED_ITEMS.indexOf(ingredient.item) < 0)
      if (kept.length !== data.item.length) { data.item = kept; changes.push({id:id,data:kept.length ? data : null}) }
      return
    }
    var all = strings(data)
    if (all.some(item => EC_REMOVED_ITEMS.indexOf(item) >= 0)) { changes.push({id:id,data:null}); return }
    var output = data.result && (typeof data.result === 'string' ? data.result : data.result.item)
    if (output && knightNamespaces.indexOf(output.split(':')[0]) >= 0 && all.indexOf('minecraft:blaze_powder') >= 0) {
      changes.push({id:id,data:premium(data)})
    }
  })
  changes.forEach(change => {
    event.remove({id:change.id})
    if (change.data) event.custom(change.data).id(change.id)
  })
  console.info('Empirecraft progression: ' + changes.length + ' recipe exclusions/upgrades; 1 diamond per blaze powder; 1 steel block per Nether upgrade template')
})
