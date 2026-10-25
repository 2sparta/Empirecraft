// Keep registered IDs for existing worlds; disable every Create door/trapdoor recipe.
ServerEvents.recipes(event => {
  const doors = /^create:.*(?:_door|_trapdoor)$/
  event.remove({output: doors})
  event.remove({input: doors})
})
