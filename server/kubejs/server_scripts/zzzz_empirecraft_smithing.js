// priority: -100
// Run after zz_medieval_create: use the active recipes, so disabled Create content stays disabled.
const EC_SMITHING = (() => {
  var aliases = {
    'magistuarmory:steel_plate': 'overgeared:steel_plate',
    'overgeared:iron_plate': 'create:iron_sheet',
    'antiquelegacy:iron_plate': 'create:iron_sheet',
    'overgeared:copper_plate': 'create:copper_sheet'
  }
  var knights = ['magistuarmory', 'magistuarmoryaddon', 'antiquelegacy', 'darkagesarmory', 'slavicarmory', 'epic_knights__japanese_armory']
  var createParts = ('andesite_alloy shaft cogwheel large_cogwheel gearbox vertical_gearbox gearshift clutch encased_chain_drive adjustable_chain_gearshift sequenced_gearshift mechanical_bearing windmill_bearing rope_pulley mechanical_piston sticky_mechanical_piston piston_extension_pole gantry_shaft gantry_carriage mechanical_press mechanical_saw millstone wrench contraption_controls redstone_contact andesite_funnel andesite_belt_funnel andesite_tunnel').split(' ')
  var steelChain = [
    'overgeared:crude_steel_from_alloying', 'overgeared:crude_steel_from_nether_alloying',
    'overgeared:crude_steel_from_cooling', 'overgeared:heated_crude_steel_from_blasting_crude_steel',
    'overgeared:heated_steel_ingot', 'overgeared:heated_steel_ingot_from_blasting_steel_ingot',
    'overgeared:steel_ingot_from_cooling', 'overgeared:steel_block',
    'overgeared:steel_ingot_from_block', 'overgeared:steel_ingot_from_nuggets', 'overgeared:steel_nugget_from_ingot'
  ]
  var rawSteel = ['overgeared:steel_ingot', 'overgeared:steel_nugget', 'overgeared:steel_block', 'overgeared:crude_steel', 'overgeared:heated_crude_steel', 'overgeared:heated_steel_ingot']
  var heated = {
    'minecraft:iron_ingot': 'overgeared:heated_iron_ingot',
    'forge:ingots/iron': 'overgeared:heated_iron_ingot',
    'minecraft:copper_ingot': 'overgeared:heated_copper_ingot',
    'forge:ingots/copper': 'overgeared:heated_copper_ingot',
    'overgeared:steel_ingot': 'overgeared:heated_steel_ingot',
    'forge:ingots/steel': 'overgeared:heated_steel_ingot'
  }
  function canonical(value) {
    if (typeof value === 'string') return aliases[value] || value
    if (Array.isArray(value)) return value.map(canonical)
    if (value && typeof value === 'object') {
      // Function-scoped locals avoid Rhino 2001 captured-const redeclaration on re-entry.
      var result = {}
      Object.keys(value).forEach(key => result[key] = canonical(value[key]))
      return result
    }
    return value
  }
  function results(recipe) {
    var output = recipe.results || recipe.result || recipe.output || []
    if (!Array.isArray(output)) output = [output]
    return output.map(value => typeof value === 'string' ? value : value.item).filter(value => value)
  }
  function strings(value) {
    if (typeof value === 'string') return [value]
    if (!value || typeof value !== 'object') return []
    var all = []
    Object.keys(value).forEach(key => all = all.concat(strings(value[key])))
    return all
  }
  function metal(value) {
    return strings(value).some(id => /^(forge:)?(?:ingots|nuggets|plates|storage_blocks)\/(?:iron|steel|gold|copper|silver|bronze|brass|tin)$/.test(id) || /:(?:(?:small_|heated_)?(?:iron|steel|golden?|copper|silver|bronze|brass|tin)_(?:ingot|nugget|plate|ring|chain|chainmail|lamellar_row)|andesite_alloy)$/.test(id))
  }
  return { aliases: aliases, knights: knights, createParts: createParts, steelChain: steelChain, rawSteel: rawSteel, heated: heated, canonical: canonical, results: results, strings: strings, metal: metal }
})()

ServerEvents.recipes(event => {
  var Armor = Java.loadClass('net.minecraft.world.item.ArmorItem')
  var Shield = Java.loadClass('net.minecraft.world.item.ShieldItem')
  var Tiered = Java.loadClass('net.minecraft.world.item.TieredItem')
  var Projectile = Java.loadClass('net.minecraft.world.item.ProjectileWeaponItem')
  function equipment(id) {
    var item = Item.of(id).item
    if (item instanceof Armor) return 'armor'
    if (item instanceof Shield) return 'shield'
    if (item instanceof Tiered || item instanceof Projectile) return /(?:^|_)(?:axe|battleaxe|broadaxe|daneaxe|war_axe)$/.test(id.split(':')[1]) ? 'axe' : 'sword'
    return null
  }
  var edits = []
  var toolTypes = { armor: [], shield: [], sword: [], axe: [] }
  var forged = 0
  var blocked = 0
  event.forEachRecipe({}, recipe => {
    recipe.serialize()
    var original = JSON.parse(recipe.json.toString())
    var data = EC_SMITHING.canonical(original)
    var id = String(recipe.getId())
    // Never turn an iron smelt, recycling, casting or Create processing recipe into new steel.
    if (EC_SMITHING.results(data).some(output => EC_SMITHING.rawSteel.indexOf(output) >= 0) && EC_SMITHING.steelChain.indexOf(id) < 0) {
      edits.push({ id: id, remove: true }); blocked++; return
    }
    // One steel plate recipe on the Overgeared anvil, plus the explicit Create press recipe below.
    if (id === 'magistuarmory:steel_plate') { edits.push({ id: id, remove: true }); return }
    var output = data.result && data.result.item
    var namespace = output ? output.split(':')[0] : ''
    var isKnight = EC_SMITHING.knights.indexOf(namespace) >= 0
    var isCreatePart = namespace === 'create' && EC_SMITHING.createParts.indexOf(output.split(':')[1]) >= 0
    var outputType = isKnight ? equipment(output) : null
    var hasMetal = EC_SMITHING.metal(data.key)
    var hasEquipmentBase = data.key && Object.keys(data.key).some(symbol => data.key[symbol].item && equipment(data.key[symbol].item))
    if (data.type === 'minecraft:crafting_shaped' && (isKnight || isCreatePart) && (hasMetal || (outputType && hasEquipmentBase))) {
      var type = outputType
      var key = {}
      var hasHotMetal = false
      Object.keys(data.key).forEach(symbol => {
        var ingredient = data.key[symbol]
        // The pinned recipes use object ingredients, as required by Overgeared's serializer.
        if (Array.isArray(ingredient)) throw new Error('Unsupported forging alternatives in ' + id + '; add an explicit compatibility tag')
        ingredient = Object.assign({}, ingredient)
        if (type) {
          var hot = EC_SMITHING.heated[ingredient.item || ingredient.tag]
          if (hot && hasMetal) { ingredient = { item: hot }; hasHotMetal = true }
          if (ingredient.item && equipment(ingredient.item)) ingredient.transfer_nbt = true
        }
        key[symbol] = ingredient
      })
      var steel = EC_SMITHING.strings(data.key).some(value => value.indexOf('steel') >= 0)
      var forging = {
        type: 'overgeared:forging', category: type === 'armor' ? 'armors' : 'misc',
        pattern: data.pattern, key: key, result: data.result, tier: steel ? 'iron' : 'stone',
        hammering: steel ? 4 : 3, has_quality: !!type && hasMetal, needs_minigame: !!type && hasMetal,
        has_polishing: false, need_quenching: !!type && hasHotMetal,
        requires_blueprint: false, quality_difficulty: 'none', show_notification: true
      }
      if (data.conditions) forging.conditions = data.conditions
      if (type) { forging.blueprint = [type]; if (toolTypes[type].indexOf(output) < 0) toolTypes[type].push(output) }
      edits.push({ id: id, data: forging }); forged++
    } else if (JSON.stringify(data) !== JSON.stringify(original)) edits.push({ id: id, data: data })
  })
  // Apply after the traversal; never mutate the recipe collection while iterating it.
  edits.forEach(edit => {
    event.remove({ id: edit.id })
    if (!edit.remove) event.custom(edit.data).id(edit.id)
  })
  Object.keys(toolTypes).forEach(type => {
    if (toolTypes[type].length) event.custom({ type: 'overgeared:item_to_tooltype', item: toolTypes[type].map(id => ({ item: id })), tooltype: type }).id('empirecraft:smithing/tooltype/' + type)
  })
  event.custom({ type: 'create:pressing', ingredients: [{ item: 'overgeared:steel_ingot' }], results: [{ item: 'overgeared:steel_plate' }] }).id('empirecraft:smithing/pressing/steel_plate')
  // Preserve the native material ratios: 4 nuggets -> 8 rings; 6 nuggets -> 8 small plates.
  event.custom({ type: 'create:pressing', ingredients: [{ item: 'overgeared:steel_nugget' }], results: [{ item: 'magistuarmory:steel_ring', count: 2 }] }).id('empirecraft:smithing/pressing/steel_rings')
  event.custom({ type: 'create:cutting', ingredients: [{ item: 'overgeared:steel_plate' }], results: [{ item: 'magistuarmory:small_steel_plate', count: 12 }], processingTime: 100 }).id('empirecraft:smithing/cutting/small_steel_plates')
  console.info('Empirecraft smithing: ' + forged + ' forging recipes; ' + blocked + ' alternate steel sources blocked')
})

