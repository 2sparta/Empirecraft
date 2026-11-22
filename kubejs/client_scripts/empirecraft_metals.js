// Hide duplicate plates after their recipes are unified.
JEIEvents.hideItems(event => {
  ['magistuarmory:steel_plate', 'antiquelegacy:iron_plate'].forEach(id => event.hide(id))
})
