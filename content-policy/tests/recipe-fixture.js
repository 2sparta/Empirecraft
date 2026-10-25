var recipeCallbacks=[],hideCallbacks=[],tabCallbacks={}
var ServerEvents={recipes:function(f){recipeCallbacks.push(f)},tags:function(){}}
var PlayerEvents={inventoryChanged:function(){}}
var ItemEvents={entityInteracted:function(){}}
var JEIEvents={hideItems:function(f){hideCallbacks.push(f)}}
var StartupEvents={modifyCreativeTab:function(id,f){tabCallbacks[id]=f}}
var Ingredient={of:function(ids){return ids}}
var console={info:function(){},log:function(){}}
function Armor(){};function Shield(){};function Tiered(){};function Projectile(){}
var Java={loadClass:function(id){
  if(id==='net.minecraft.world.item.ArmorItem')return Armor
  if(id==='net.minecraft.world.item.ShieldItem')return Shield
  if(id==='net.minecraft.world.item.TieredItem')return Tiered
  if(id==='net.minecraft.world.item.ProjectileWeaponItem')return Projectile
  return function(){}
}}
var Item={of:function(id){
  var n=id.split(':')[1]||''
  var value=/sword|spear|halberd|pike|stylet|katzbalger|ranseur|ahlspiess|chivalrylance|estoc|claymore|zweihander|lochaberaxe|heavymace|heavywarhammer|lucernhammer|morgenstern|guisarme|katana|naginata/.test(n)?new Tiered():/helmet|chestplate|leggings|boots|armet|sallet|bascinet|barbute|kettlehat|greathelm|stechhelm/.test(n)?new Armor():/shield|buckler|rondache|target/.test(n)?new Shield():{}
  return {id:id,item:value}
}}
function clone(value){return JSON.parse(JSON.stringify(value))}
var registry=clone(fixtures.recipes)
function allStrings(value){
  if(typeof value==='string')return [value]
  if(!value || typeof value!=='object')return []
  var result=[];Object.keys(value).forEach(function(key){result=result.concat(allStrings(value[key]))});return result
}
function outputs(data){var out=data.results||data.result||data.output||[];if(!Array.isArray(out))out=[out];return out.map(function(v){return typeof v==='string'?v:v.item}).filter(function(v){return !!v})}
function matches(id,data,filter){
  if(filter.id && id!==String(filter.id))return false
  if(filter.type && data.type!==filter.type)return false
  if(filter.output && !outputs(data).some(function(v){return typeof filter.output==='string'?v===filter.output:filter.output.test(v)}))return false
  if(filter.input && !allStrings(data).some(function(v){return typeof filter.input==='string'?v===filter.input:filter.input.test(v)}))return false
  return true
}
var iterating=false
var recipeEvent={
  remove:function(filter){if(iterating)throw Error('Mutation during traversal');Object.keys(registry).forEach(function(id){if(matches(id,registry[id],filter))delete registry[id]})},
  custom:function(data){if(iterating)throw Error('Mutation during traversal');return {id:function(id){registry[String(id)]=clone(data)}}},
  forEachRecipe:function(filter,callback){iterating=true;try{Object.keys(registry).forEach(function(id){if(matches(id,registry[id],filter))callback({serialize:function(){},getId:function(){return id},json:{toString:function(){return JSON.stringify(registry[id])}}})})}finally{iterating=false}}
}
function check(value,message){if(!value)throw Error(message)}
function cost(data,item){
  if(data.pattern){var total=0;data.pattern.forEach(function(row){row.split('').forEach(function(symbol){if(data.key[symbol] && data.key[symbol].item===item)total++})});return total}
  return ['template','addition','base'].filter(function(key){return data[key] && data[key].item===item}).length
}
function verifyProgression(){
  recipeCallbacks.forEach(function(f){f(recipeEvent)})
  var blocked=fixtures.policy.replacements
  Object.keys(registry).forEach(function(id){
    var data=registry[id]
    check(!allStrings(data).some(function(s){return Object.prototype.hasOwnProperty.call(blocked,s)}),'Retired recipe input/output remains: '+id)
    var out=outputs(data)[0]
    if(out && fixtures.knights.indexOf(out.split(':')[0])>=0)check(allStrings(data).indexOf('minecraft:blaze_powder')<0,'Blaze powder remains: '+id)
  })
  fixtures.premium.forEach(function(definition){
    var data=registry[definition.id];check(!!data,'Premium recipe lost: '+definition.id)
    check(cost(data,'minecraft:diamond')===cost(definition.source,'minecraft:blaze_powder'),'Diamond cost changed: '+definition.id)
    if(definition.source.type==='minecraft:smithing_transform')check(data.template.item==='overgeared:steel_block','Nether template remains: '+definition.id)
  })
  var blade=registry['magistuarmory:steel_flamebladedsword']
  check(blade.type==='overgeared:forging' && cost(blade,'minecraft:diamond')===4,'Flame blade must retain Overgeared forging and cost 4 diamonds')
  check(Object.keys(blade.key).some(function(k){return blade.key[k].transfer_nbt}),'Upgrade loses quality transfer')
  check(registry['overgeared:crude_steel_from_alloying'],'Only steel furnace chain removed')
  check(!registry['medieval_boomsticks:magentitesmeltingblast'],'Magnetite steel bypass remains')
  var tools=registry['empirecraft:smithing/tooltype/sword'];check(tools && tools.item.length>20,'Retained sword blueprints were deleted')
  check(allStrings(tools).indexOf('magistuarmory:steel_claymore')>=0,'Steel claymore blueprint lost')
  var hidden=[];hideCallbacks.forEach(function(f){f({hide:function(id){hidden.push(id)}})})
  check(hidden.length===Object.keys(blocked).length,'JEI coverage mismatch')
  check(hidden.indexOf('minecraft:diamond_pickaxe')<0 && hidden.indexOf('magistuarmory:diamond_kiteshield')<0,'Tools/shields incorrectly hidden')
  Object.keys(tabCallbacks).forEach(function(tab){tabCallbacks[tab]({remove:function(ids){check(ids.length===hidden.length,'Creative tab coverage mismatch')}})})
  check(tabCallbacks['spawn:item_group'] && tabCallbacks['overgeared:lingering_arrows_tab'] && tabCallbacks['minecraft:combat'],'Missing native creative tabs')
  return 'PASS complete recipe pipeline: '+fixtures.premium.length+' premium upgrades, all removed outputs/inputs, steel gate, retained quality/blueprints, JEI and creative tabs'
}
