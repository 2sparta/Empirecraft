// 1_steel.js - Автоматическая замена стали
console.log('Loading steel replacement...')

ServerEvents.recipes(event => {
    event.replaceOutput({}, 'magistuarmory:steel_ingot', 'overgeared:steel_ingot')
    event.replaceInput({}, 'magistuarmory:steel_ingot', 'overgeared:steel_ingot')
})

let steelCounter = 0
PlayerEvents.tick(event => {
    steelCounter++
    if (steelCounter < 20) return
    steelCounter = 0

    const player = event.player
    const inv = player.inventory
    let totalReplaced = 0

    // Проверяем все слоты (0-35 инвентарь, 36-39 броня, 40 offhand)
    for (let i = 0; i < 41; i++) {
        let stack = inv.getItem(i)
        if (stack && stack.id === 'magistuarmory:steel_ingot') {
            totalReplaced += stack.count
            inv.setItem(i, Item.empty) // удаляем старый слиток
        }
    }

    if (totalReplaced > 0) {
        player.give(Item.of('overgeared:steel_ingot', totalReplaced))
        player.tell(Text.green('Заменено ' + totalReplaced + ' старых слитков стали.'))
    }
})