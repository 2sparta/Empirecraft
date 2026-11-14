JEIEvents.hideItems(event => {
  event.hide(/^tfc:.*(?:red_steel|blue_steel|black_bronze).*$/)
  event.hide('overgeared:crude_steel')
  event.hide('overgeared:heated_crude_steel')
})
JEIEvents.hideFluids(event => {
  ['red_steel', 'blue_steel', 'black_bronze', 'high_carbon_red_steel', 'high_carbon_blue_steel', 'weak_red_steel', 'weak_blue_steel'].forEach(metal => event.hide('tfc:metal/' + metal))
})
