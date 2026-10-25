// 4_tables.js - Рецепты столов Valhelsia
console.log('Loading valhelsia tables...')

ServerEvents.recipes(event => {
    var TABLES_DATA = [
        { id: 'valhelsia_furniture:oak_table', plank: 'minecraft:oak_planks' },
        { id: 'valhelsia_furniture:spruce_table', plank: 'minecraft:spruce_planks' },
        { id: 'valhelsia_furniture:birch_table', plank: 'minecraft:birch_planks' },
        { id: 'valhelsia_furniture:jungle_table', plank: 'minecraft:jungle_planks' },
        { id: 'valhelsia_furniture:acacia_table', plank: 'minecraft:acacia_planks' },
        { id: 'valhelsia_furniture:dark_oak_table', plank: 'minecraft:dark_oak_planks' },
        { id: 'valhelsia_furniture:cherry_table', plank: 'minecraft:cherry_planks' },
        { id: 'valhelsia_furniture:crimson_table', plank: 'minecraft:crimson_planks' },
        { id: 'valhelsia_furniture:warped_table', plank: 'minecraft:warped_planks' }
    ]
    
    for (var t = 0; t < TABLES_DATA.length; t++) {
        var table = TABLES_DATA[t]
        // This optional mod is absent in the current pack; skip unregistered outputs.
        if (!Item.exists(table.id)) continue
        event.remove({ output: table.id })
        event.shaped(table.id, ['PPP', 'S S'], {
            P: table.plank,
            S: 'minecraft:stick'
        })
    }
})
