var recipeCallback, jeiCallback
var ServerEvents = {recipes: function(callback) { recipeCallback = callback }}
var JEIEvents = {hideItems: function(callback) { jeiCallback = callback }}
function checkContentScripts() {
  var removed = []
  recipeCallback({remove: function(filter) { removed.push(filter) }})
  if (removed.length !== 2) throw new Error('Expected input and output recipe filtering')
  var doors = ['andesite_door','brass_door','copper_door','framed_glass_door','framed_glass_trapdoor','train_door','train_trapdoor']
  doors.forEach(function(name) {
    if (!removed[0].output.test('create:'+name) || !removed[1].input.test('create:'+name)) throw new Error('Door recipe remains: '+name)
  })
  ;['minecraft:oak_door','minecraft:iron_trapdoor','create:cuckoo_clock','create:framed_glass'].forEach(function(id) {
    if (removed[0].output.test(id)) throw new Error('Unrelated item removed: '+id)
  })
  var stacks = [], hidden = []
  function stack(id, tag) {
    var value = {id:id, nbt:tag}
    value.strongNBT = function() { return {exactStack:value} }
    stacks.push(value)
    return value
  }
  var expected = []
  ;['iron','steel','diamond'].forEach(function(material) {
    stack('overgeared:'+material+'_arrow',null)
    stack('overgeared:'+material+'_arrow',{Quality:5})
    expected.push(stack('overgeared:'+material+'_arrow',{Potion:'minecraft:poison'}))
    expected.push(stack('overgeared:'+material+'_arrow',{LingeringPotion:1,Potion:'minecraft:poison',Quality:5}))
    expected.push(stack('overgeared:'+material+'_arrow',{CustomPotionEffects:[{Id:19}]}))
  })
  stack('vinery:red_wine',{Potion:'minecraft:poison'})
  jeiCallback({
    getAllIngredients:function() { return stacks },
    hide:function(ingredient) { hidden.push(ingredient) }
  })
  if (String(hidden[0]) !== String(removed[0].output) || hidden[1] !== 'overgeared:lingering_arrow') throw new Error('Missing JEI door/lingering filter')
  var exact = hidden.slice(2).map(function(value) { return value.exactStack })
  if (exact.length !== expected.length || expected.some(function(value) { return exact.indexOf(value)<0 })) throw new Error('JEI must hide effect variants and retain ordinary arrows/wine')
  return 'PASS content scripts: 7 door types, recipe input/output, exact-NBT JEI filtering, ordinary arrows/quality/wine preserved'
}
