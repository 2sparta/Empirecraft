// Old registry IDs remain for existing worlds; only the canonical forms are shown in JEI.
JEIEvents.hideItems(event => {
  ['magistuarmory:steel_ingot', 'magistuarmory:steel_nugget', 'magistuarmory:steel_plate',
   'overgeared:iron_plate', 'antiquelegacy:iron_plate', 'overgeared:copper_plate'].forEach(id => event.hide(id))
})
ClientEvents.lang('en_us', event => {
  event.add('tooltype.overgeared.armor', 'Armor')
  event.add('tooltype.overgeared.shield', 'Shield')
})
ClientEvents.lang('uk_ua', event => {
  event.add('tooltype.overgeared.armor', 'Обладунок')
  event.add('tooltype.overgeared.shield', 'Щит')
})
