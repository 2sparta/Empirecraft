const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const recipes = new Map();
const callbacks=[];
const context = vm.createContext({console,ServerEvents:{recipes:fn=>callbacks.push(fn)}});
for (const file of ['kubejs/server_scripts/zzzzzzzzzzzz_thirtyyearswar.js','kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js']) vm.runInContext(fs.readFileSync(file,'utf8'),context);
const event={
 addedRecipes:{forEach(fn){for(const [key,value] of recipes)fn({json:{toString:()=>JSON.stringify(value)},remove:()=>recipes.delete(key)})}},
 remove(filter){for(const [key,value] of recipes)if(filter.id===key||filter.output===value.result?.item)recipes.delete(key)},
 custom(value){return {id:key=>{assert(!recipes.has(key),'duplicate added ID '+key);recipes.set(key,value)}}},
 shaped(output,pattern,key){return {id:id=>recipes.set(id,{result:{item:output},pattern,key})}}
};
for(const callback of callbacks)callback(event);
for(const gun of ['thirtyyearswar:baroque_matchlock_musket','thirtyyearswar:baroque_wheellock_pistol']){
 const entries=[...recipes.values()].filter(recipe=>recipe.result?.item===gun);
 assert.equal(entries.length,1,'exactly one recipe for '+gun);
 assert.equal(entries[0].type,'overgeared:forging');
 assert.equal(entries[0].needs_minigame,true);
}
const pistol=recipes.get('empirecraft_forging:assembly/thirtyyearswar/knights_baroque_wheellock_pistol');
assert.equal(pistol.key.L.item,'empirecraft_forging:wheellock');
assert.equal(pistol.key.P.min_temp,900);
const lock=JSON.parse(fs.readFileSync('forging-balance/src/main/resources/data/empirecraft_forging/recipes/wheellock.json','utf8'));
assert.equal(lock.result.item,'empirecraft_forging:wheellock');
assert.equal(lock.key.L.item,'tfc:ore/pyrite');
assert(!JSON.stringify([...recipes.values()]).includes('musketmod:'));
assert(!JSON.stringify([...recipes.values()]).includes('medieval_boomsticks:'));
const latest=recipes.get('empirecraft_forging:assembly/thirtyyearswar/knights_baroque_matchlock_musket');
assert.equal(latest.key.M.item,'empirecraft_forging:lockwork');
assert(!JSON.stringify([...recipes.values()]).includes('create:precision_mechanism'));
assert(fs.readFileSync('kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js','utf8').startsWith('// priority: -21000'));
assert.equal(fs.readFileSync('kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js','utf8'),fs.readFileSync('server/kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js','utf8'));
console.log('PASS ordered recipe replacement, unique gun outputs, heated steel, available lockwork and paired scripts');
