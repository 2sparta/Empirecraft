// Effect arrows share IDs with normal arrows: hide only their NBT variants.
JEIEvents.hideItems(event => {
  event.hide(/^create:.*(?:_door|_trapdoor)$/)
  event.hide('overgeared:lingering_arrow')
  event.getAllIngredients().forEach(stack => {
    if (!/^overgeared:(iron|steel|diamond)_arrow$/.test(String(stack.id))) return
    const tag = stack.nbt
    if (tag && (tag.Potion || tag.LingeringPotion || tag.CustomPotionEffects)) {
      event.hide(stack.strongNBT())
    }
  })
})
