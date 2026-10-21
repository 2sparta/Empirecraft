// 3_boomsticks.js - Запрет Medieval Boomsticks
console.log('Loading boomsticks ban...')

const BOOMSTICKS_ITEMS = [
    'medieval_boomsticks:eveningstar',
    'medieval_boomsticks:gotheveningstar',
    'medieval_boomsticks:morningstar',
    'medieval_boomsticks:spikedhandgonne'
]

ServerEvents.recipes(event => {
    for (let i = 0; i < BOOMSTICKS_ITEMS.length; i++) {
        event.remove({ output: BOOMSTICKS_ITEMS[i] })
    }
})

let boomCounter = 0
PlayerEvents.tick(event => {
    boomCounter++
    if (boomCounter < 20) return
    boomCounter = 0
    
    const player = event.player
    const inv = player.getInventory()
    
    for (let s = 0; s < 41; s++) {
        const checkItem = inv.getItem(s)
        if (checkItem) {
            for (let b = 0; b < BOOMSTICKS_ITEMS.length; b++) {
                if (checkItem.getId() === BOOMSTICKS_ITEMS[b]) {
                    inv.setItem(s, 'minecraft:air')
                    player.tell(Text.red('Запрещенный предмет удален!'))
                    break
                }
            }
        }
    }
})

EntityEvents.spawned(event => {
    const entity = event.entity
    if (entity.getType() === 'minecraft:item') {
        const groundItem = entity.item
        if (groundItem) {
            for (let b = 0; b < BOOMSTICKS_ITEMS.length; b++) {
                if (groundItem.getId() === BOOMSTICKS_ITEMS[b]) {
                    entity.kill()
                    break
                }
            }
        }
    }
})

ItemEvents.crafted(event => {
    const crafted = event.item
    for (let b = 0; b < BOOMSTICKS_ITEMS.length; b++) {
        if (crafted.getId() === BOOMSTICKS_ITEMS[b]) {
            crafted.setCount(0)
            event.player.tell(Text.red('Этот предмет запрещен!'))
            break
        }
    }
})