// Full server-side enforcement is in empirecraft-world-rules-1.0.0.jar.
// Use only supported KubeJS 2001.6 server events here: no EntityEvents.tick or ForgeEvents.
ServerEvents.recipes(event => {
    event.remove({ output: 'minecraft:enchanting_table' })
    event.remove({ output: 'minecraft:enchanted_book' })
    event.remove({ output: 'minecraft:brewing_stand' })
})
