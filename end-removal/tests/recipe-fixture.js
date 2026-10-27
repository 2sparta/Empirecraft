var records = {}
fixtures.cases.forEach(function(test) { records[test.id] = test.source })
var console = {info:function(message) {}}
var Ingredient = {of:function(tag) { return {itemIds:fixtures.live_tags[tag.substring(1)] || []} }}
var ServerEvents = {recipes:function(callback) {
  callback({
    forEachRecipe:function(filter, action) {
      Object.keys(records).forEach(function(id) {
        action({serialize:function(){},getId:function(){return id},json:{toString:function(){return JSON.stringify(records[id])}}})
      })
    },
    remove:function(filter) { delete records[filter.id] },
    custom:function(data) { return {id:function(id) { records[id]=data }} }
  })
}}
function normalized(value) {
  if (Array.isArray(value)) return value.map(normalized)
  if (!value || typeof value !== 'object') return value
  var result={}
  Object.keys(value).sort().forEach(function(key){result[key]=normalized(value[key])})
  return result
}
function verifyNoEnd() {
  fixtures.cases.forEach(function(test) {
    var actual=records[test.id]
    if(test.expected===null) {
      if(actual!==undefined) throw new Error('Recipe should be removed: '+test.id)
    } else if(JSON.stringify(normalized(actual))!==JSON.stringify(normalized(test.expected))) {
      throw new Error('Incorrect recipe '+test.id+': '+JSON.stringify(actual))
    }
  })
  return 'PASS actual installed Rhino recipe pass: '+fixtures.cases.length+' scenarios'
}
