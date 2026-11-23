JEIEvents.hideItems(event => {
  ["tfc:gem/opal", "tfc:gem/pyrite", "tfc:gem/ruby", "tfc:gem/sapphire", "tfc:gem/topaz", "tfc:large_prepared_hide", "tfc:large_scraped_hide", "tfc:large_soaked_hide", "tfc:medium_prepared_hide", "tfc:medium_scraped_hide", "tfc:medium_soaked_hide", "tfc:sandpaper", "tfc:small_prepared_hide", "tfc:small_scraped_hide", "tfc:small_soaked_hide"].forEach(id => event.hide(id))
})
