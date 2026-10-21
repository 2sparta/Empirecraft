BlockEvents.broken(event => {
    const player = event.player
    if (!player) return

    const tool = player.mainHandItem
    if (!tool || tool.isEmpty()) return

    const nbt = tool.nbt
    if (!nbt) return

    const display = tool.getHoverName().getString().toLowerCase()

    let multiplier = 1.0

    // Определяем качество
    if (display.includes('masterwork')) {
        multiplier = 2.0
    }
    else if (display.includes('perfect')) {
        multiplier = 1.6
    }
    else if (display.includes('expert')) {
        multiplier = 1.3
    }
    else if (display.includes('well')) {
        multiplier = 1.1
    }
    else if (display.includes('poor')) {
        multiplier = 0.7
    }

    // Проверяем правильный инструмент
    const block = event.block
    const id = tool.id

    let correct = false

    // Кирки
    if (
        id.includes('pickaxe') &&
        block.hasTag('minecraft:mineable/pickaxe')
    ) {
        correct = true
    }

    // Топоры
    if (
        id.includes('axe') &&
        !id.includes('pickaxe') &&
        block.hasTag('minecraft:mineable/axe')
    ) {
        correct = true
    }

    // Лопаты
    if (
        id.includes('shovel') &&
        block.hasTag('minecraft:mineable/shovel')
    ) {
        correct = true
    }

    // Мотыги
    if (
        id.includes('hoe') &&
        block.hasTag('minecraft:mineable/hoe')
    ) {
        correct = true
    }

    if (!correct) return

    // Эффект haste
    const amplifier = Math.max(0, Math.floor(multiplier * 2) - 1)

    player.potionEffects.add(
        'minecraft:haste',
        2,
        amplifier,
        false,
        false
    )
})