ServerEvents.recipes(function(event) {
  var removed = EC_NO_END.replacements
  var pearl = 'minecraft:ender_pearl'
  var absent = {}
  function isRemoved(id) { return Object.prototype.hasOwnProperty.call(removed, id) }
  function values(value) {
    if (typeof value === 'string') return [value]
    if (!value || typeof value !== 'object') return []
    var result = []
    Object.keys(value).forEach(function(key) { result = result.concat(values(value[key])) })
    return result
  }
  function tagItems(tag) {
    var original = EC_NO_END.ingredient_tags[tag]
    // The live tag API also catches tags added by datapacks after our asset audit.
    if (typeof Ingredient !== 'undefined') {
      try {
        var live = []
        Ingredient.of('#' + tag).itemIds.forEach(function(id) { live.push(String(id)) })
        if (live.length) return live
      } catch (ignored) {}
    }
    return original
  }
  function rewrite(value, alternatives) {
    if (typeof value === 'string') return value === pearl ? EC_NO_END.pearl_replacement : isRemoved(value) ? absent : value
    if (!value || typeof value !== 'object') return value
    if (Array.isArray(value)) {
      var kept = []
      value.forEach(function(child) {
        var changed = rewrite(child, alternatives)
        if (changed === absent) {
          if (!alternatives) kept = absent
        } else if (kept !== absent) {
          if (alternatives && Array.isArray(changed)) kept = kept.concat(changed)
          else kept.push(changed)
        }
      })
      return kept === absent || (value.length && !kept.length) ? absent : kept
    }
    if (value.tag) {
      var items = tagItems(value.tag)
      if (items && (items.some(isRemoved) || EC_NO_END.ingredient_tags[value.tag])) {
        var allowed = []
        items.forEach(function(id) {
          var item = id === pearl ? EC_NO_END.pearl_replacement : id
          if ((id === pearl || !isRemoved(id)) && allowed.indexOf(item) < 0) allowed.push(item)
        })
        if (!allowed.length) return absent
        if (Object.keys(value).length === 1) return allowed.sort().map(function(id) { return {item:id} })
        if (value.tag === 'forge:ender_pearls' || value.tag === 'c:ender_pearls' || value.tag === 'minecraft:ender_pearls') {
          value = JSON.parse(JSON.stringify(value));delete value.tag;value.item = EC_NO_END.pearl_replacement
        }
      }
    }
    var output = {}
    var invalid = false
    Object.keys(value).forEach(function(key) {
      var changed
      if (key === 'ingredients' && Array.isArray(value[key])) {
        changed = value[key].map(function(slot) { return rewrite(slot, true) })
        if (changed.some(function(slot) { return slot === absent })) changed = absent
      } else if (key === 'key' && value[key] && typeof value[key] === 'object') {
        changed = {}
        Object.keys(value[key]).forEach(function(letter) {
          var slot = rewrite(value[key][letter], true)
          if (slot === absent) invalid = true
          changed[letter] = slot
        })
      } else changed = rewrite(value[key], ['ingredient','base','addition','template','tool','input','item'].indexOf(key) >= 0)
      if (changed === absent) invalid = true
      else output[key] = changed
    })
    return invalid ? absent : output
  }
  var changes = []
  event.forEachRecipe({}, function(recipe) {
    recipe.serialize()
    var original = JSON.parse(recipe.json.toString())
    var retiredOutput = false
    ;['result','results','output','outputs'].forEach(function(key) {
      if (values(original[key]).some(isRemoved)) retiredOutput = true
    })
    var data = retiredOutput ? absent : rewrite(original)
    if (data === absent || JSON.stringify(original) !== JSON.stringify(data)) changes.push({id:String(recipe.getId()),data:data})
  })
  changes.forEach(function(change) {
    event.remove({id:change.id})
    if (change.data !== absent) event.custom(change.data).id(change.id)
  })
  console.info('Empirecraft No End: ' + changes.length + ' recipe changes; Ender pearls -> amethyst shards')
})
