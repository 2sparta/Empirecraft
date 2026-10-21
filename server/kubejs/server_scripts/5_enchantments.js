// priority: 0
console.info('Hello, World! (Loaded server scripts)')

ServerEvents.recipes(event => {
  event.remove({ output: 'darkagesarmory:steel_small_seax' })
})

var BANNED_ITEM = 'siegemachines:siege_workbench'

// Разрешённые зачарования (только эти можно использовать)
var ALLOWED_ENCHANTS = [
    'minecraft:efficiency',      // Эффективность
    'minecraft:unbreaking',      // Прочность
    'minecraft:fortune',         // Удача
    'minecraft:mending'          // Починка
]

// Все остальные зачарования запрещены
var isEnchantAllowed = function(enchantId) {
    return ALLOWED_ENCHANTS.indexOf(enchantId) !== -1
}

// Удаление рецепта
ServerEvents.recipes(function(event) {
    event.remove({ output: BANNED_ITEM })
})

// Безопасная уборка предметов на земле (используем EntityEvents вместо ServerEvents.entityTick)
EntityEvents.tick('minecraft:item', function(event) {
    var entity = event.entity
    
    // Проверяем, что энтити существует и это предмет
    if (entity && entity.item) {
        if (entity.item.id == BANNED_ITEM) {
            entity.kill()
        }
    }
})

// Безопасная проверка инвентаря через tick
var tickCounter = 0

ServerEvents.tick(function(event) {
    tickCounter++
    if (tickCounter < 20) return // Раз в секунду
    tickCounter = 0
    
    if (!event.server || !event.server.players) return
    
    event.server.players.forEach(function(player) {
        if (!player || !player.inventory) return
        
        // Проверяем основной инвентарь (0-35 слоты)
        for (var i = 0; i < 36; i++) {
            var item = player.getInventory().getItem(i)
            
            if (!item || item.isEmpty()) continue
            
            // Безопасное удаление предмета
            if (item.getId() == BANNED_ITEM) {
                player.getInventory().setItem(i, Item.of('minecraft:air'))
                continue
            }
            
            // Обработка зачарований
            processEnchantments(item, player, i, 'main')
        }
        
        // Проверяем броню (слоты 36-39)
        for (var i = 0; i < 4; i++) {
            var armorItem = player.getInventory().getItem(36 + i)
            if (armorItem && !armorItem.isEmpty()) {
                processEnchantments(armorItem, player, 36 + i, 'armor')
            }
        }
        
        // Проверяем оффхенд (слот 40)
        var offhandItem = player.getInventory().getItem(40)
        if (offhandItem && !offhandItem.isEmpty()) {
            processEnchantments(offhandItem, player, 40, 'offhand')
        }
    })
})

// Функция обработки зачарований
function processEnchantments(item, player, slot, inventoryType) {
    try {
        // Получаем NBT предмета
        var nbt = item.getNbt()
        var modified = false
        
        if (nbt) {
            // Обычные зачарования на предметах
            if (nbt.Enchantments && Array.isArray(nbt.Enchantments)) {
                var originalLength = nbt.Enchantments.length
                var invalidEnchants = []
                
                var filteredEnchants = []
                for (var i = 0; i < nbt.Enchantments.length; i++) {
                    var enchant = nbt.Enchantments[i]
                    if (enchant && enchant.id) {
                        if (isEnchantAllowed(enchant.id)) {
                            filteredEnchants.push(enchant)
                        } else {
                            invalidEnchants.push(enchant.id)
                        }
                    } else {
                        filteredEnchants.push(enchant)
                    }
                }
                nbt.Enchantments = filteredEnchants
                
                if (nbt.Enchantments.length !== originalLength) {
                    modified = true
                    if (invalidEnchants.length > 0) {
                        console.info('Removed invalid enchants from ' + item.getId() + ' for ' + player.getUsername() + ': ' + invalidEnchants.join(', '))
                    }
                }
            }
            
            // Зачарования на зачарованных книгах
            if (nbt.StoredEnchantments && Array.isArray(nbt.StoredEnchantments)) {
                var originalLength = nbt.StoredEnchantments.length
                var invalidEnchants = []
                
                var filteredEnchants = []
                for (var i = 0; i < nbt.StoredEnchantments.length; i++) {
                    var enchant = nbt.StoredEnchantments[i]
                    if (enchant && enchant.id) {
                        if (isEnchantAllowed(enchant.id)) {
                            filteredEnchants.push(enchant)
                        } else {
                            invalidEnchants.push(enchant.id)
                        }
                    } else {
                        filteredEnchants.push(enchant)
                    }
                }
                nbt.StoredEnchantments = filteredEnchants
                
                if (nbt.StoredEnchantments.length !== originalLength) {
                    modified = true
                    if (invalidEnchants.length > 0) {
                        console.info('Removed invalid stored enchants from enchanted book for ' + player.getUsername() + ': ' + invalidEnchants.join(', '))
                    }
                }
            }
            
            // Если после удаления зачарований предмет остался без зачарований и это зачарованная книга - заменяем на обычную книгу
            if (item.getId() === 'minecraft:enchanted_book') {
                var hasEnchants = false
                if (nbt.StoredEnchantments && nbt.StoredEnchantments.length > 0) hasEnchants = true
                if (nbt.Enchantments && nbt.Enchantments.length > 0) hasEnchants = true
                
                if (!hasEnchants) {
                    player.getInventory().setItem(slot, Item.of('minecraft:book'))
                    console.info('Replaced empty enchanted book with regular book for ' + player.getUsername())
                    return
                }
            }
            
            if (modified) {
                item.setNbt(nbt)
                player.getInventory().setItem(slot, item)
            }
        }
    } catch (e) {
        // Игнорируем ошибки
        if (item.getId() != 'minecraft:cobblestone') {
            console.warn('Error processing item ' + item.getId() + ': ' + e)
        }
    }
}

// Дополнительная защита: удаление всех запрещённых зачарований при загрузке игрока
PlayerEvents.loggedIn(function(event) {
    var player = event.player
    var hasInvalidEnchants = false
    
    // Проверяем весь инвентарь (0-35)
    for (var i = 0; i < 36; i++) {
        var item = player.getInventory().getItem(i)
        if (item && !item.isEmpty()) {
            try {
                var nbt = item.getNbt()
                if (nbt) {
                    // Проверяем зачарования
                    if (nbt.Enchantments && Array.isArray(nbt.Enchantments)) {
                        for (var j = 0; j < nbt.Enchantments.length; j++) {
                            var enchant = nbt.Enchantments[j]
                            if (enchant && enchant.id && !isEnchantAllowed(enchant.id)) {
                                hasInvalidEnchants = true
                                console.info('Found invalid enchant ' + enchant.id + ' on ' + item.getId() + ' in inventory of ' + player.getUsername())
                                break
                            }
                        }
                    }
                    // Проверяем зачарования на книгах
                    if (nbt.StoredEnchantments && Array.isArray(nbt.StoredEnchantments)) {
                        for (var j = 0; j < nbt.StoredEnchantments.length; j++) {
                            var enchant = nbt.StoredEnchantments[j]
                            if (enchant && enchant.id && !isEnchantAllowed(enchant.id)) {
                                hasInvalidEnchants = true
                                console.info('Found invalid stored enchant ' + enchant.id + ' on enchanted book in inventory of ' + player.getUsername())
                                break
                            }
                        }
                    }
                }
            } catch (e) {
                console.warn('Error checking item on login: ' + e)
            }
        }
    }
    
    // Проверяем броню (слоты 36-39)
    for (var i = 36; i < 40; i++) {
        var item = player.getInventory().getItem(i)
        if (item && !item.isEmpty()) {
            try {
                var nbt = item.getNbt()
                if (nbt && nbt.Enchantments && Array.isArray(nbt.Enchantments)) {
                    for (var j = 0; j < nbt.Enchantments.length; j++) {
                        var enchant = nbt.Enchantments[j]
                        if (enchant && enchant.id && !isEnchantAllowed(enchant.id)) {
                            hasInvalidEnchants = true
                            console.info('Found invalid enchant ' + enchant.id + ' on ' + item.getId() + ' in armor slot of ' + player.getUsername())
                            break
                        }
                    }
                }
            } catch (e) {
                console.warn('Error checking armor on login: ' + e)
            }
        }
    }
    
    if (hasInvalidEnchants) {
        player.sendMessage(Component.literal('§cОбнаружены запрещённые зачарования в инвентаре! Они будут автоматически удалены.'))
    }
})

// Защита от получения запрещённых зачарований через наковальню
ForgeEvents.onEvent('net.minecraftforge.event.AnvilUpdateEvent', function(event) {
    var output = event.getOutput()
    
    if (output && !output.isEmpty()) {
        try {
            var nbt = output.getNbt()
            if (nbt) {
                var hasInvalidEnchant = false
                
                if (nbt.Enchantments && Array.isArray(nbt.Enchantments)) {
                    for (var i = 0; i < nbt.Enchantments.length; i++) {
                        var enchant = nbt.Enchantments[i]
                        if (enchant && enchant.id && !isEnchantAllowed(enchant.id)) {
                            hasInvalidEnchant = true
                            break
                        }
                    }
                }
                
                if (hasInvalidEnchant) {
                    event.setCanceled(true)
                    console.info('Blocked anvil operation that would create invalid enchantments')
                }
            }
        } catch (e) {
            console.warn('Error checking anvil output: ' + e)
        }
    }
})

console.info('Enchantment restrictions active. Allowed enchants: ' + ALLOWED_ENCHANTS.join(', '))