// 2_siege.js - Запрет Siege Workbench
console.log('Loading siege ban...')

ServerEvents.recipes(event => {
    event.remove({ output: 'siegemachines:siege_workbench' })
})

// Блокировка установки (исправлено: BlockEvents.placed)
BlockEvents.placed(event => {
    if (event.block.id === 'siegemachines:siege_workbench') {
        event.cancel()
        event.player.tell(Text.red('Размещение Siege Workbench запрещено!'))
    }
})

// Блокировка взаимодействия (правый клик)
BlockEvents.rightClicked(event => {
    if (event.block.id === 'siegemachines:siege_workbench') {
        event.cancel()
        event.player.tell(Text.red('Этот блок запрещён!'))
    }
})

// Удаление из инвентаря каждые 20 тиков
var siegeCounter = 0
PlayerEvents.tick(event => {
    siegeCounter++
    if (siegeCounter < 20) return
    siegeCounter = 0

    var player = event.player
    var inv = player.inventory

    for (var i = 0; i < 41; i++) {
        var stack = inv.getItem(i)
        if (stack && stack.id === 'siegemachines:siege_workbench') {
            inv.setItem(i, Item.empty)
            player.tell(Text.red('Siege Workbench удалён из инвентаря!'))
        }
    }
})

// Удаление выброшенных предметов
EntityEvents.spawned(event => {
    var entity = event.entity
    if (entity.type === 'minecraft:item') {
        var groundItem = entity.item
        if (groundItem && groundItem.id === 'siegemachines:siege_workbench') {
            entity.kill()
        }
    }
})