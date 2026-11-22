JEIEvents.hideItems(event => {
  event.hide(/^tfc:metal\/(?:pickaxe|axe|shovel|hoe)\/wrought_iron$/)
  event.hide(/^(?:create:dough|create:wheat_flour|farmersdelight:wheat_dough)$/)
  event.hide(/^tfc:(?:metal\/(?:unfinished_)?(?:helmet|chestplate|greaves|boots)\/.*|(?:chainmail|leather)_(?:helmet|chestplate|leggings|boots))$/)
  event.hide(/^tfc:.*(?:red_steel|blue_steel|black_bronze|bismuth_bronze).*$/)
})
JEIEvents.hideFluids(event => {
  ['bismuth_bronze', 'red_steel', 'blue_steel', 'black_bronze', 'high_carbon_red_steel', 'high_carbon_blue_steel', 'weak_red_steel', 'weak_blue_steel'].forEach(metal => event.hide('tfc:metal/' + metal))
})
