const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'../..');
const fixtures=JSON.parse(fs.readFileSync(path.join(root,'smithing-integration/build/fixtures.json'),'utf8'));
const registry=new Map(Object.entries(fixtures.recipes).map(([id,data])=>[id,structuredClone(data)]));
const callbacks={recipes:[],tags:[],inventory:[],interaction:[],hide:[],lang:[],native:[]};
class Base{};class Tiered extends Base{};class Armor extends Base{};class Shield extends Base{};class Projectile extends Base{};
class Villager extends Base{};class SteelTrade{};class NormalTrade{};
const classMap={
 'net.minecraft.world.item.TieredItem':Tiered,'net.minecraft.world.item.ArmorItem':Armor,
 'net.minecraft.world.item.ShieldItem':Shield,'net.minecraft.world.item.ProjectileWeaponItem':Projectile,
 'net.minecraft.world.entity.npc.AbstractVillager':Villager,'net.stirdrem.overgeared.event.SteelEmeraldTrade':SteelTrade,
 'net.minecraftforge.event.village.VillagerTradesEvent':class{},'net.minecraftforge.eventbus.api.EventPriority':{LOWEST:'LOWEST'}
};
const swordTag=(fixtures.tags['minecraft:swords']||[]).map(x=>typeof x==='string'?x:x.id);
const itemTypes={};
function nativeItem(id){
 if(itemTypes[id])return new itemTypes[id]();
 const name=id.split(':')[1]||'';
 if(swordTag.includes(id)||/sword|dagger|halberd|pike$|spear|battleaxe|longsword|katana|naginata|sabre|stylet/.test(name))return new Tiered();
 if(/shield|buckler|target$|pavise|rondache/.test(name))return new Shield();
 if(/helmet|chestplate|leggings|boots|armet|sallet|bascinet|barbute|kettlehat|greathelm|stechhelm/.test(name))return new Armor();
 return new Base();
}
function stack(id,count=1,nbt=null){return {id,count,nbt:structuredClone(nbt),item:nativeItem(id)}}
const log=[];
const scope={console:{info:s=>log.push(s),log:s=>log.push(s)},Java:{loadClass:n=>{assert(n in classMap,n);return classMap[n]}},Item:{of:stack},
 ServerEvents:{recipes:f=>callbacks.recipes.push(f),tags:(t,f)=>callbacks.tags.push(f)},
 PlayerEvents:{inventoryChanged:f=>callbacks.inventory.push(f)},ItemEvents:{entityInteracted:f=>callbacks.interaction.push(f)},
 JEIEvents:{hideItems:f=>callbacks.hide.push(f)},ClientEvents:{lang:(l,f)=>callbacks.lang.push([l,f])},
 ForgeEvents:{eventBus:()=>({addListener:(priority,cancelled,type,callback)=>{assert.equal(priority,'LOWEST');callbacks.native.push(callback)}})}
};
const context=vm.createContext(scope);
function load(file){vm.runInContext(fs.readFileSync(path.join(root,file),'utf8'),context,{filename:file})}
load('server/kubejs/startup_scripts/empirecraft_steel_trades.js');
load('server/kubejs/server_scripts/empirecraft_plate_compat.js');
load('server/kubejs/server_scripts/zz_medieval_create.js');
load('server/kubejs/server_scripts/zzzz_empirecraft_smithing.js');
load('kubejs/client_scripts/empirecraft_metals.js');
function allStrings(value){if(typeof value==='string')return [value];if(!value||typeof value!=='object')return [];return Object.values(value).flatMap(allStrings)}
function outputs(r){let v=r.results||r.result||r.output||[];if(!Array.isArray(v))v=[v];return v.map(x=>typeof x==='string'?x:x.item).filter(Boolean)}
function inputs(r){const {result,results,output,type,...rest}=r;return allStrings(rest)}
function matches(id,r,f){
 if(f.id&&id!==String(f.id))return false;if(f.type&&r.type!==f.type)return false;
 const match=(needle,hay)=>typeof needle==='string'?hay.includes(needle):hay.some(x=>needle.test(x));
 if(f.output&&!match(f.output,outputs(r)))return false;if(f.input&&!match(f.input,inputs(r)))return false;return true;
}
let iterating=false;
const event={
 remove:f=>{assert(!iterating,'Recipe collection mutated while iterating');for(const [id,r] of registry)if(matches(id,r,f))registry.delete(id)},
 custom:r=>{assert(!iterating);return {id:id=>registry.set(String(id),structuredClone(r))}},
 forEachRecipe:(filter,fn)=>{iterating=true;try{for(const [id,r] of registry)if(matches(id,r,filter))fn({serialize(){},getId:()=>id,json:{toString:()=>JSON.stringify(r)}})}finally{iterating=false}}
};
callbacks.recipes.forEach(f=>f(event));
const raw=['overgeared:steel_ingot','overgeared:steel_nugget','overgeared:steel_block','overgeared:crude_steel','overgeared:heated_crude_steel','overgeared:heated_steel_ingot'];
assert(!registry.has('magistuarmory:steel_ingot_blasting'));
assert(!registry.has('magistuarmory:furnace/steel_ingot_blasting'));
assert(registry.has('overgeared:crude_steel_from_alloying'));
assert(!registry.has('overgeared:crude_steel_from_nether_alloying'),'Removed Nether furnace must not retain its steel recipe');
for(const [id,recipe] of registry)assert(!['overgeared:casting','overgeared:nether_alloy_smelting','overgeared:shaped_nether_alloy_smelting'].includes(recipe.type),'Removed machine recipe restored: '+id);
assert.equal(registry.get('overgeared:heated_steel_ingot').type,'overgeared:forging');
for(const [id,r] of registry){
 const text=JSON.stringify(r);
 for(const alias of ['magistuarmory:steel_ingot','magistuarmory:steel_nugget','magistuarmory:steel_plate','overgeared:iron_plate','antiquelegacy:iron_plate','overgeared:copper_plate'])assert(!text.includes('"'+alias+'"'),'Legacy metal remains in '+id);
 if(outputs(r).some(x=>raw.includes(x)))assert(id.startsWith('overgeared:'),'Alternative steel producer: '+id);
 if(r.type==='minecraft:blasting'||r.type==='minecraft:smelting')assert(!outputs(r).some(x=>['overgeared:steel_ingot','overgeared:steel_nugget','overgeared:crude_steel'].includes(x)),'Smelting produces new steel: '+id);
 if(id.startsWith('create:')&&r.type==='overgeared:forging')assert(!r.has_quality,'Machine parts must not acquire equipment quality');
 if(r.type==='overgeared:forging'){
  assert(r.pattern.every(row=>row.length===r.pattern[0].length)&&r.pattern.length<=3&&r.pattern[0].length<=3,'Invalid native forging pattern '+id);
  for(const row of r.pattern)for(const c of row)assert(c===' '||r.key[c],'Missing key '+id);
  for(const value of Object.values(r.key))assert(!Array.isArray(value),'Native forging requires object ingredients');
 }
}
const coverage={};for(const [id,r] of registry)if(r.type==='overgeared:forging'&&r.result?.item){const ns=r.result.item.split(':')[0];coverage[ns]=(coverage[ns]||0)+1;}
for(const ns of ['magistuarmory','magistuarmoryaddon','antiquelegacy','darkagesarmory','slavicarmory','epic_knights__japanese_armory','create'])assert(coverage[ns]>0,'Missing forging integration for '+ns);
assert.deepEqual(registry.get('empirecraft:smithing/pressing/steel_plate').ingredients,[{item:'overgeared:steel_ingot'}]);
assert.equal(registry.get('empirecraft:smithing/pressing/steel_rings').results[0].count,2);
assert.equal(registry.get('empirecraft:smithing/cutting/small_steel_plates').results[0].count,12);
const helmet=registry.get('magistuarmoryaddon:burgundian_kettlehat');assert(helmet.has_quality&&helmet.needs_minigame);assert.equal(helmet.blueprint[0],'armor');
const style=registry.get('magistuarmoryaddon:saracen_helmet');assert.equal(style.type,'overgeared:forging');assert(!style.has_quality);assert(Object.values(style.key).some(x=>x.transfer_nbt));
assert.equal(registry.get('overgeared:iron_plate').result.item,'create:iron_sheet');
const forbidden=fs.readFileSync(path.join(root,'server/kubejs/server_scripts/zz_medieval_create.js'),'utf8').match(/const disabled = (\[.*?\]);/)[1];
const denied=new Set(JSON.parse(forbidden));for(const [id,r] of registry)assert(!outputs(r).some(x=>denied.has(x)),'Disabled Create content restored: '+id);
const tags=new Map(Object.entries(fixtures.tags));
callbacks.tags.forEach(f=>f({removeAll:id=>tags.set(id,[]),add:(id,value)=>tags.get(id).push(value),removeAllTagsFrom:id=>{for(const [key,values] of tags)tags.set(key,values.filter(x=>(typeof x==='string'?x:x.id)!==id))}}));
assert.deepEqual(tags.get('forge:ingots/steel'),['overgeared:steel_ingot']);assert.deepEqual(tags.get('forge:nuggets/steel'),['overgeared:steel_nugget']);assert.deepEqual(tags.get('forge:plates/steel'),['overgeared:steel_plate']);
const inv=Array.from({length:41},()=>null);inv[7]=stack('magistuarmory:steel_plate',64,{display:{Name:'Legacy'},ForgingQuality:'expert',custom:{retained:1}});
let writes=0;callbacks.inventory.forEach(f=>f({item:inv[7],slot:61,player:{inventory:{getItem:slot=>inv[slot]||stack('minecraft:air'),setItem:(slot,item)=>{inv[slot]=item;writes++}}}}));
assert.equal(writes,1);assert.equal(inv[7].id,'overgeared:steel_plate');assert.equal(inv[7].count,64);assert.equal(inv[7].nbt.custom.retained,1);assert.equal(inv[7].nbt.ForgingQuality,'expert');assert.equal(inv.filter(Boolean).length,1);
callbacks.inventory.forEach(f=>f({item:inv[7],slot:7,player:{inventory:{setItem:()=>{throw Error('Recursive migration')}}}}));
function javaList(items){const arr=[...items];arr.removeIf=p=>{for(let i=arr.length-1;i>=0;i--)if(p(arr[i]))arr.splice(i,1)};return arr}
let trades=javaList([new SteelTrade(),new NormalTrade()]);callbacks.native.forEach(f=>f({trades:{1:trades}}));assert.equal(trades.length,1);assert(trades[0] instanceof NormalTrade);
const offers=javaList([{m_45368_:()=>stack('overgeared:steel_ingot')},{m_45368_:()=>stack('minecraft:emerald')}]);const oldVillager=new Villager();oldVillager.m_6616_=()=>offers;
callbacks.interaction.forEach(f=>f({target:oldVillager}));assert.equal(offers.length,1);assert.equal(offers[0].m_45368_().id,'minecraft:emerald');
const hidden=[];callbacks.hide.forEach(f=>f({hide:id=>hidden.push(id)}));assert(hidden.includes('magistuarmory:steel_plate')&&!hidden.includes('overgeared:steel_ingot'));
const result={coverage,final_recipes:registry.size,log,steel_producers:[...registry].filter(([id,r])=>outputs(r).some(x=>raw.includes(x))).map(([id,r])=>({id,type:r.type,ingredients:inputs(r),outputs:outputs(r)}))};
fs.writeFileSync(path.join(root,'smithing-integration/build/results.json'),JSON.stringify(result,null,2));
fs.writeFileSync(path.join(root,'smithing-integration/build/recipes.json'),JSON.stringify(Object.fromEntries(registry)));
console.log('PASS installed recipe fixtures, all six Knights namespaces, Create restrictions, steel production gates, tags, lossless migration and new/saved villager trades');console.log(JSON.stringify({coverage:result.coverage,log:result.log}));

