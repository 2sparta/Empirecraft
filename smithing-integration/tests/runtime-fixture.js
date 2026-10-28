// Deliberately ES5-compatible helpers; the production scripts run unchanged in the installed Rhino.
function check(value,message){if(!value)throw new Error(message)}
function clone(value){return JSON.parse(JSON.stringify(value))}
var recipeCallbacks=[],nativeCallbacks=[],runtimeLog=[],tagCallbacks=[],inventoryCallbacks=[],interactionCallbacks=[];
function Base(){};function Tiered(){};function Armor(){};function Shield(){};function Projectile(){};function Villager(){};
var classMap={
 'net.minecraft.world.item.TieredItem':Tiered,'net.minecraft.world.item.ArmorItem':Armor,
 'net.minecraft.world.item.ShieldItem':Shield,'net.minecraft.world.item.ProjectileWeaponItem':Projectile,
 'net.minecraft.world.entity.npc.AbstractVillager':Villager,
 'net.stirdrem.overgeared.event.SteelEmeraldTrade':RhinoSteelTrade,
 'net.minecraftforge.event.village.VillagerTradesEvent':RhinoTradeEvent,
 'net.minecraftforge.eventbus.api.EventPriority':{LOWEST:'LOWEST'}
};
var swordTag=(fixtures.tags['minecraft:swords']||[]).map(function(x){return typeof x==='string'?x:x.id});
function nativeItem(id){
 var name=id.split(':')[1]||'';
 if(swordTag.indexOf(id)>=0 || /sword|dagger|halberd|pike$|spear|battleaxe|longsword|katana|naginata|sabre|stylet/.test(name))return new Tiered();
 if(/shield|buckler|target$|pavise|rondache/.test(name))return new Shield();
 if(/helmet|chestplate|leggings|boots|armet|sallet|bascinet|barbute|kettlehat|greathelm|stechhelm/.test(name))return new Armor();
 return new Base();
}
var existsIds={};
var Item={of:function(id,count,nbt){return {id:id,count:count===undefined?1:count,nbt:nbt||null,item:nativeItem(id)}},exists:function(id){return !!existsIds[id]}};
var Java={loadClass:function(name){check(name in classMap,'Unknown native class '+name);return classMap[name]}};
var console={info:function(s){runtimeLog.push(s)},log:function(s){runtimeLog.push(s)}};
var ServerEvents={recipes:function(callback){recipeCallbacks.push(callback)},tags:function(type,callback){tagCallbacks.push(callback)}};
var PlayerEvents={inventoryChanged:function(callback){inventoryCallbacks.push(callback)}};
var ItemEvents={entityInteracted:function(callback){interactionCallbacks.push(callback)}};
var ForgeEvents={eventBus:function(){return {addListener:function(priority,cancelled,type,callback){check(priority==='LOWEST','Wrong priority');check(!cancelled,'Receive cancelled');nativeCallbacks.push(callback)}}}};
var registry=clone(fixtures.recipes),iterating=false;
function allStrings(value){
 if(typeof value==='string')return [value];if(!value || typeof value!=='object')return [];
 var result=[];Object.keys(value).forEach(function(key){result=result.concat(allStrings(value[key]))});return result;
}
function outputs(recipe){var value=recipe.results||recipe.result||recipe.output||[];if(!Array.isArray(value))value=[value];return value.map(function(x){return typeof x==='string'?x:x.item}).filter(function(x){return !!x})}
function inputs(recipe){var rest=clone(recipe);delete rest.result;delete rest.results;delete rest.output;delete rest.type;return allStrings(rest)}
function matches(id,recipe,filter){
 if(filter.id && id!==String(filter.id))return false;if(filter.type && recipe.type!==filter.type)return false;
 function match(needle,hay){return typeof needle==='string'?hay.indexOf(needle)>=0:hay.some(function(x){return needle.test(x)})}
 if(filter.output && !match(filter.output,outputs(recipe)))return false;
 if(filter.input && !match(filter.input,inputs(recipe)))return false;return true;
}
var recipeEvent={
 remove:function(filter){check(!iterating,'Changed recipe collection during traversal');Object.keys(registry).forEach(function(id){if(matches(id,registry[id],filter))delete registry[id]})},
 custom:function(data){check(!iterating,'Changed recipe collection during traversal');return {id:function(id){registry[String(id)]=clone(data)}}},
 forEachRecipe:function(filter,callback){iterating=true;try{Object.keys(registry).forEach(function(id){var recipe=registry[id];if(matches(id,recipe,filter))callback({serialize:function(){},getId:function(){return id},json:{toString:function(){return JSON.stringify(recipe)}}})})}finally{iterating=false}}
};
function runRecipes(){recipeCallbacks.forEach(function(callback){callback(recipeEvent)})}
function testTables(){
 var callback=recipeCallbacks[recipeCallbacks.length-1],removed=[],added=[];
 var tableEvent={remove:function(filter){removed.push(filter.output)},shaped:function(id,pattern,key){check(Item.exists(id),'Missing table output');added.push(id)}};
 existsIds={};callback(tableEvent);check(removed.length===0 && added.length===0,'Absent tables must be skipped before removal');
 existsIds={'valhelsia_furniture:oak_table':true};callback(tableEvent);check(removed.length===1 && added.length===1 && added[0]==='valhelsia_furniture:oak_table','Only registered table may change: '+JSON.stringify({removed:removed,added:added}));
 ['oak','spruce','birch','jungle','acacia','dark_oak','cherry','crimson','warped'].forEach(function(wood){existsIds['valhelsia_furniture:'+wood+'_table']=true});
 removed=[];added=[];callback(tableEvent);check(removed.length===9 && added.length===9,'All registered tables must remain configurable');
}

function testLegacy(){
 var inv=[],writes=0,reads=[];
 inv[7]=Item.of('magistuarmory:steel_plate',64,{custom:{retained:1},ForgingQuality:'expert'});
 inv[32]=Item.of('overgeared:copper_plate',12,{custom:{retained:2}});
 var before=clone(inv),original=inv[7];
 var event={item:original,slot:61,player:{inventory:{getItem:function(slot){reads.push(slot);return inv[slot]||Item.of('minecraft:air',0)},setItem:function(slot,item){inv[slot]=item;writes++}}}};
 inventoryCallbacks.forEach(function(callback){callback(event)});
 check(writes===2,'Both later legacy slots must migrate even with an empty first slot; got '+writes+' writes');
 check(inv[7].id==='overgeared:steel_plate' && inv[7].count===64 && inv[7].nbt.ForgingQuality==='expert' && inv[7].nbt.custom.retained===1,'Steel slot/count/NBT changed');
 check(inv[32].id==='create:copper_sheet' && inv[32].count===12 && inv[32].nbt.custom.retained===2,'Copper slot/count/NBT changed');
 inventoryCallbacks.forEach(function(callback){callback(event)});check(writes===2,'Repeated migration must be idempotent');
 var offers=[{m_45368_:function(){return Item.of('overgeared:steel_ingot')}},{m_45368_:function(){return Item.of('minecraft:emerald')}}];
 offers.removeIf=function(predicate){for(var i=this.length-1;i>=0;i--)if(predicate(this[i]))this.splice(i,1)};
 var villager=new Villager();villager.m_6616_=function(){return offers};
 interactionCallbacks.forEach(function(callback){callback({target:villager});callback({target:villager})});
 check(offers.length===1 && offers[0].m_45368_().id==='minecraft:emerald','Saved offers must preserve ordinary trades');
 var tags={};
 var tagEvent={removeAll:function(id){tags[id]=[]},add:function(id,value){tags[id].push(value)},removeAllTagsFrom:function(){}};
 tagCallbacks.forEach(function(callback){callback(tagEvent);callback(tagEvent)});
 check(tags['forge:ingots/steel'].length===1 && tags['forge:ingots/steel'][0]==='overgeared:steel_ingot','Canonical tags must remain stable');
}

