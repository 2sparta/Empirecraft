#!/usr/bin/env python3
"""Reviewed terminology and Empirecraft-specific localization overrides."""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent

TERMS = {
    'Global chat': ['Глобальний чат', 'Глобальный чат'],
    'Local chat': ['Локальний чат', 'Локальный чат'],
    'System messages': ['Системні повідомлення', 'Системные сообщения'],
    'Town chat': ['Чат міста', 'Чат города'],
    'Kingdom chat': ['Чат королівства', 'Чат королевства'],
    'System messages are read-only. Choose a chat channel.': ['Системні повідомлення доступні лише для читання. Обери іншу вкладку.', 'Системные сообщения доступны только для чтения. Выбери другую вкладку.'],
    'The message is too long for this channel (maximum 240 characters).': ['Повідомлення задовге для цього каналу (максимум 240 символів).', 'Сообщение слишком длинное для этого канала (максимум 240 символов).'],
    'Alloy Furnace': ['Піч для сплавів', 'Печь для сплавов'],
    'Casting Furnace': ['Ливарна піч', 'Литейная печь'],
    'Drafting Table': ['Креслярський стіл', 'Чертёжный стол'],
    'Smithing Anvil': ['Ковальське ковадло', 'Кузнечная наковальня'],
    'Stone Smithing Anvil': ["Кам’яне ковальське ковадло", 'Каменная кузнечная наковальня'],
    'Block of Steel': ['Блок сталі', 'Блок стали'],
    'Steel': ['Сталь', 'Сталь'],
    'Steel Ingot': ['Сталевий зливок', 'Стальной слиток'],
    'Steel Nugget': ['Сталевий самородок', 'Стальной самородок'],
    'Smithing Blueprints': ['Ковальські креслення', 'Кузнечные чертежи'],
    'Fletching': ['Виготовлення стріл', 'Изготовление стрел'],
    'Fletching and Arrows': ['Виготовлення стріл і стріли', 'Изготовление стрел и стрелы'],
    'Drafting': ['Креслення', 'Черчение'],
    'Forging': ['Кування', 'Ковка'],
    'Alloying': ['Виплавка сплавів', 'Выплавка сплавов'],
    'Nether Alloying': ['Незерська виплавка сплавів', 'Незерская выплавка сплавов'],
    'Nether Alloy Furnace': ['Незерська піч для сплавів', 'Незерская печь для сплавов'],
    'Overworld': ['Звичайний світ', 'Верхний мир'],
    'The End': ['Енд', 'Энд'],
    'The Nether': ['Незер', 'Нижний мир'],
    'Casting': ['Лиття', 'Литьё'],
    'Water Cooling': ['Гартування у воді', 'Закалка в воде'],
    'Grinding': ['Шліфування', 'Шлифовка'],
    'Knapping': ['Оббивання каменю', 'Оббивка камня'],
    'Blueprint': ['Креслення', 'Чертёж'],
    '[Create]: use /killtps stop to bring back server tick to regular speed': ['[Create]: скористайтеся /killtps stop, щоб повернути звичайну швидкість тіків сервера', '[Create]: используйте /killtps stop, чтобы вернуть обычную скорость тиков сервера'],
    '[Create]: use /killtps start <tickTime> to artificially slow down the server tick': ['[Create]: скористайтеся /killtps start <tickTime>, щоб штучно сповільнити тіки сервера', '[Create]: используйте /killtps start <tickTime>, чтобы искусственно замедлить тики сервера'],
    'yyyy/MM/dd HH:mm:ss': ['dd.MM.yyyy HH:mm:ss', 'dd.MM.yyyy HH:mm:ss'],
    'X%s Y%s Z%s ': ['X%s Y%s Z%s ', 'X%s Y%s Z%s '],
    'x, z': ['x, z', 'x, z'],
    '%1$s was slain': ['%1$s було вбито', '%1$s был убит'],
    '%1$s was slain using %2$s': ['%1$s було вбито за допомогою %2$s', '%1$s был убит с помощью %2$s'],
    ' by mods [%s]': [' модами [%s]', ' модами [%s]'],
    'Increases Bad Omen By 1': ['Підвищує рівень лиховісного знамення на 1', 'Повышает уровень дурного знамения на 1'],
    'Diamond': ['Діамант', 'Алмаз'],
    'Netherite': ['Незерит', 'Незерит'],
    'Shift': ['Shift', 'Shift'],
    'Left Shift': ['Лівий Shift', 'Левый Shift'],
    'Right Shift': ['Правий Shift', 'Правый Shift'],
    'Arbalest': ['Арбалет', 'Арбалет'],
    'Arquebus': ['Аркебуза', 'Аркебуза'],
    'Handgonne': ['Ручниця', 'Ручница'],
    'Javelin': ['Метальний спис', 'Метательное копьё'],
    'Heavy Bolt': ['Важкий арбалетний болт', 'Тяжёлый арбалетный болт'],
    'Round Ball': ['Кругла куля', 'Круглая пуля'],
    'Bulat Bar': ['Зливок булатної сталі', 'Слиток булатной стали'],
    'Dziwer Bar': ['Зливок зварної дамаської сталі', 'Слиток сварной дамасской стали'],
    'Throwing Axe': ['Метальна сокира', 'Метательный топор'],
    'Throwing Knife': ['Метальний ніж', 'Метательный нож'],
    'War Dart': ['Бойовий дротик', 'Боевой дротик'],
    'Recurve Bow': ['Вигнутий лук', 'Изогнутый лук'],
    'Finished Shaft': ['Оброблене древко', 'Обработанное древко'],
    'Finished Leather': ['Вичинена шкіра', 'Выделанная кожа'],
    'Finished Oil': ['Очищена олія', 'Очищенное масло'],
    'Finished Oil Bucket': ['Відро очищеної олії', 'Ведро очищенного масла'],
    'Canvas': ['Полотно', 'Холст'],
    'Padded Canvas': ['Ущільнене полотно', 'Уплотнённый холст'],
    'Quilted Canvas': ['Стьобане полотно', 'Стёганый холст'],
    'Small Rock': ['Малий камінь', 'Маленький камень'],
    'Large Rock': ['Великий камінь', 'Большой камень'],
    'Upgrade Kit': ['Набір для поліпшення', 'Набор улучшения'],
    'Comfrey': ['Живокіст', 'Окопник'],
    'Lemon Balm': ['Меліса', 'Мелисса'],
    'Sage': ['Шавлія', 'Шалфей'],
    'Yarrow': ['Деревій', 'Тысячелистник'],
    'Yellow Wood Sorrel': ['Жовта кислиця', 'Жёлтая кислица'],
    'Arnica': ['Арніка', 'Арника'],
    'Anchor': ['Якір', 'Якорь'],
    'Ballast Block': ['Баластний блок', 'Балластный блок'],
    'Capstan': ['Кабестан', 'Кабестан'],
    'Buoy Block': ['Буй', 'Буй'],
    'Rabbit Hutch': ['Кролятник', 'Крольчатник'],
    'Fishing Rod': ['Вудка', 'Удочка'],
    'Tackle Box': ['Скринька для снастей', 'Ящик для снастей'],
    'Fisherman': ['Рибалка', 'Рыбак'],
    'Acacia Rabbit Hutch': ['Акацієвий кролятник', 'Акациевый крольчатник'],
    'Blue Footed Boots': ['Чоботи блакитноногої олуші', 'Сапоги голубоногой олуши'],
    'Blue-Footed Booby': ['Блакитнонога олуша', 'Голубоногая олуша'],
    'Blue Footed Booby': ['Блакитнонога олуша', 'Голубоногая олуша'],
    'Casting Net': ['Закидна сітка', 'Закидная сеть'],
    'Sunfish': ['Риба-місяць', 'Рыба-луна'],
    'Angler Fish': ['Морський вудильник', 'Морской удильщик'],
    'Seal': ['Тюлень', 'Тюлень'],
    'Booby': ['Олуша', 'Олуша'],
    'Booby Egg': ['Яйце олуші', 'Яйцо олуши'],
    'Booby Egg Shard': ['Шкаралупа яйця олуші', 'Скорлупа яйца олуши'],
    'Bucket of Baby Seal': ['Відро з тюленям', 'Ведро с тюленёнком'],
    'A bucket with a baby Seal inside.': ['Відро з маленьким тюленям усередині.', 'Ведро с маленьким тюленёнком внутри.'],
    'A crafting ingredient that sometimes drops from a thrown Booby Egg.': ['Інгредієнт для виготовлення, який іноді випадає з кинутого яйця олуші.', 'Ингредиент для изготовления, который иногда выпадает из брошенного яйца олуши.'],
    'Seal Cub captured': ['Тюленя спіймано', 'Тюленёнок пойман'],
    'Robin chirps': ['Вільшанка цвірінькає', 'Зарянка щебечет'],
    'Turkey': ['Індик', 'Индейка'],
    'Crane': ['Журавель', 'Журавль'],
    'Cricket': ['Цвіркун', 'Сверчок'],
    'Swallow': ['Ластівка', 'Ласточка'],
    'Robin': ['Вільшанка', 'Зарянка'],
    'Cock': ['Півень', 'Петух'],
    'Rooster': ['Півень', 'Петух'],
    'Hen': ['Курка', 'Курица'],
    'Kid': ['Козеня', 'Козлёнок'],
    'Calf': ['Теля', 'Телёнок'],
    'Lamb': ['Ягня', 'Ягнёнок'],
    'Ewe': ['Вівця', 'Овца'],
    'Ram': ['Баран', 'Баран'],
    'Buck': ['Самець', 'Самец'],
    'Doe': ['Самиця', 'Самка'],
    'Fawn': ['Оленя', 'Оленёнок'],
    'Clam': ['Двостулковий молюск', 'Двустворчатый моллюск'],
    'Conch': ['Стромбус', 'Стромбус'],
    'Snail': ['Равлик', 'Улитка'],
    'Snail Shell': ['Мушля равлика', 'Раковина улитки'],
    'Disable On Focus': ['Вимикати за активного чату', 'Отключать при активном чате'],
    'Gain an advancement near a seal': ['Здобудьте досягнення поруч із тюленем', 'Получите достижение рядом с тюленем'],
    '50% chance of glowing or wither': ['50% імовірності отримати світіння або висушення', '50% шанс получить свечение или иссушение'],
    '50% chance of glowing and night vision or wither': ['50% імовірності отримати світіння й нічний зір або висушення', '50% шанс получить свечение и ночное зрение или иссушение'],
    '§7Sneak right-click in air:': ['§7Крадіться й клацніть правою кнопкою в повітрі:', '§7Крадитесь и нажмите ПКМ в воздухе:'],
    'Allows you to gain a 5% attack speed boost upon dealing melee damage to entities. This stacks up to 4 times.': ['Дає 5% приросту швидкості атаки після завдання істотам шкоди в ближньому бою. Ефект складається до 4 разів.', 'Даёт 5% прироста скорости атаки после нанесения существам урона в ближнем бою. Эффект складывается до 4 раз.'],
    'Get a 100% readout from a Stressometer\n§7(Hidden Advancement)': ['Досягніть показника 100% на вимірювачі навантаження\n§7(Приховане досягнення)', 'Добейтесь показания 100% на измерителе нагрузки\n§7(Скрытое достижение)'],
    'Sandy Island': ['Піщаний острів', 'Песчаный остров'],
    'Season Sensor': ['Датчик сезону', 'Датчик сезона'],
    'Fertile Seasons': ['Сезони росту', 'Сезоны роста'],
    'Year-Round': ['Увесь рік', 'Круглый год'],
    'Calendar': ['Календар', 'Календарь'],
    'Medieval Paintings Community': ['Спільнота Medieval Paintings', 'Сообщество Medieval Paintings'],
}

MONTHS = {
    'uk_ua': ['Просинець', 'Сніжень', 'Березозіл', 'Цвітень', 'Травник', 'Кресень', 'Липець', 'Жнивець', 'Руєн', 'Листобій', 'Падолист', 'Студень'],
    'ru_ru': ['Просинец', 'Снежень', 'Березозол', 'Цветень', 'Травник', 'Кресень', 'Липец', 'Жнивец', 'Руен', 'Листобой', 'Падолист', 'Студень'],
}

WOODS = {
    'acacia': ['акації', 'акации'], 'bamboo': ['бамбука', 'бамбука'],
    'birch': ['берези', 'берёзы'], 'cherry': ['вишні', 'вишни'],
    'crimson': ['багряного дерева', 'багрового дерева'],
    'dark_oak': ['темного дуба', 'тёмного дуба'], 'jungle': ['тропічного дерева', 'тропического дерева'],
    'mangrove': ['мангрового дерева', 'мангрового дерева'], 'oak': ['дуба', 'дуба'],
    'spruce': ['смереки', 'ели'], 'warped': ['химерного дерева', 'искажённого дерева'],
}

BRANDS = {
    'AstikorCarts', 'Betsy Ross', "Brewin' and Chewin'", 'Caupona',
    'Chipped', 'Chipped Extras', 'Contact', 'Create', 'Dawn of Time',
    'Decorative Blocks', "Dumpling's Delight", 'Ecologics', "Farmer's Delight",
    "Farmer's Respite", 'Fruits Delight', 'Hearth & Home', 'Immersive Melodies',
    'Immersive Paintings', 'Locks', 'Naturalist', 'OELib', 'Overgeared',
    'Rustic Delight', 'Serene Seasons', 'Spawn', 'Starcatcher', 'Supplementaries',
    'Table Top Craft', 'Valkyrien Skies', "[Let's Do] Vinery", 'RK Medieval Boomsticks',
    'Medieval Deco', 'Realistic Horse Genetics',
}

for line in (HERE / 'final_terms.tsv').read_text().splitlines():
    if line:
        english, ukrainian, russian = line.split('\t')
        TERMS[english] = [ukrainian, russian]

COLORS = {
    'white': ['Білий', 'Біла', 'Біле', 'Белый'], 'orange': ['Помаранчевий', 'Помаранчева', 'Помаранчеве', 'Оранжевый'],
    'magenta': ['Пурпуровий', 'Пурпурова', 'Пурпурове', 'Пурпурный'], 'light_blue': ['Блакитний', 'Блакитна', 'Блакитне', 'Голубой'],
    'yellow': ['Жовтий', 'Жовта', 'Жовте', 'Жёлтый'], 'lime': ['Лаймовий', 'Лаймова', 'Лаймове', 'Лаймовый'],
    'pink': ['Рожевий', 'Рожева', 'Рожеве', 'Розовый'], 'gray': ['Сірий', 'Сіра', 'Сіре', 'Серый'],
    'light_gray': ['Світло-сірий', 'Світло-сіра', 'Світло-сіре', 'Светло-серый'],
    'cyan': ['Бірюзовий', 'Бірюзова', 'Бірюзове', 'Бирюзовый'], 'purple': ['Фіолетовий', 'Фіолетова', 'Фіолетове', 'Фиолетовый'],
    'blue': ['Синій', 'Синя', 'Синє', 'Синий'], 'brown': ['Коричневий', 'Коричнева', 'Коричневе', 'Коричневый'],
    'green': ['Зелений', 'Зелена', 'Зелене', 'Зелёный'], 'red': ['Червоний', 'Червона', 'Червоне', 'Красный'],
    'black': ['Чорний', 'Чорна', 'Чорне', 'Чёрный'],
}


def generated_terms(text, index):
    from chipped_terms import gender
    if text.endswith(' Connect Four'):
        material = text.removesuffix(' Connect Four').lower().replace(' ', '_')
        if material in WOODS:
            return ['Гра «Чотири в ряд» з ', 'Игра «Четыре в ряд» из '][index] + WOODS[material][index]
        for suffix, nouns in [('_concrete', ['бетон', 'бетон']), ('_terracotta', ['кераміка', 'керамика'])]:
            if material.endswith(suffix):
                color = material.removesuffix(suffix)
                if color in COLORS:
                    form = 'f' if suffix == '_terracotta' else 'm'
                    adjective = COLORS[color][1 if form == 'f' else 0] if index == 0 else gender(COLORS[color][3], form)
                    return ['Гра «Чотири в ряд»', 'Игра «Четыре в ряд»'][index] + ' (' + adjective.lower() + ' ' + nouns[index] + ')'
    for suffix, names, form in [
        (' Full Caparison', ['повна попона', 'полная попона'], 'f'),
        (' Half Caparison', ['часткова попона', 'частичная попона'], 'f'),
        (' Shoulder Caparison', ['плечова попона', 'плечевая попона'], 'f'),
        (' Western Blanket', ['ковбойська попона', 'ковбойская попона'], 'f'),
        (' Rich Eastern Caftan', ['розкішний східний каптан', 'богатый восточный кафтан'], 'm'),
        (' Eastern Rich Kaftan', ['розкішний східний каптан', 'богатый восточный кафтан'], 'm'),
    ]:
        if text.endswith(suffix):
            color = text.removesuffix(suffix).lower().replace(' ', '_')
            if color in COLORS:
                adjective = COLORS[color][1 if form == 'f' else 0] if index == 0 else gender(COLORS[color][3], form)
                return adjective + ' ' + names[index]
    return None


def main():
    changed = 0
    for source in sorted((HERE / 'upstream').glob('*.json')):
        if source.name.startswith('_'):
            continue
        english = json.loads(source.read_text()).get('en_us', {})
        ns = source.stem
        for index, locale in enumerate(['uk_ua', 'ru_ru']):
            path = HERE / 'pack' / 'assets' / ns / 'lang' / (locale + '.json')
            if not path.exists():
                continue
            values = json.loads(path.read_text())
            before = dict(values)
            for key, text in english.items():
                generated = generated_terms(text, index)
                if generated:
                    values[key] = generated
                if key.endswith('.author') or key == 'translator':
                    values[key] = text
                if re.search(r'itemGroup|creativetab|item_group', key) and text in BRANDS:
                    values[key] = text
                if text == "Create's Building Blocks":
                    values[key] = ['Create: будівельні блоки', 'Create: строительные блоки'][index]
                if ('.usage' in key and key.startswith(('commands.', 'command.', 'create.command.'))) or text.startswith('/'):
                    values[key] = text.replace('Usage:', ['Використання:', 'Использование:'][index])
                if text in TERMS:
                    values[key] = TERMS[text][index]
                if text in TERMS:
                    values[key] = TERMS[text][index]
                if ns == 'vs_sails':
                    for wood, names in WOODS.items():
                        if key == 'block.vs_sails.' + wood + '_helm':
                            values[key] = ('Основа керма з ' if index == 0 else 'Основа штурвала из ') + names[index]
                        if key == 'block.vs_sails.' + wood + '_helm_wheel':
                            values[key] = ('Штурвал з ' if index == 0 else 'Штурвал из ') + names[index]
                    from chipped_terms import gender
                    for color, adjectives in COLORS.items():
                        for suffix, names, form in [('_sail', ['вітрило', 'парус'], 'n'), ('_magma_sail', ['магматичне вітрило', 'магматический парус'], 'n'), ('_wind_flag', ['вітровказівник', 'ветроуказатель'], 'm'), ('_buoy', ['буй', 'буй'], 'm')]:
                            if key == 'block.vs_sails.' + color + suffix:
                                adjective = adjectives[2 if form == 'n' else 0] if index == 0 else gender(adjectives[3], 'm')
                                values[key] = adjective + ' ' + names[index]
            if ns == 'empirecraft_calendar':
                for month, name in enumerate(MONTHS[locale], 1):
                    values['calendar.empirecraft.month.' + str(month)] = name
                values['calendar.empirecraft.date'] = '%2$s • день %1$s/30'
                values['calendar.empirecraft.duration'] = ['Доба: 48 хвилин. Місяць: 30 днів.', 'Сутки: 48 минут. Месяц: 30 дней.'][index]
            if ns == 'sereneseasons':
                values['commands.sereneseasons.usage'] = ['/season <get/set> [аргументи]', '/season <get/set> [аргументы]'][index]
            if ns == 'starcatcher':
                values['tooltip.tackle.starcatcher.clear'] = 'Ясно'
                values['gui.guide.seasons.all'] = ['Усі сезони', 'Все сезоны'][index]
            if ns == 'spawn':
                from chipped_terms import gender
                for color, names in COLORS.items():
                    key = 'entity.spawn.clam.color.' + color
                    if key in english:
                        values[key] = names[0] if index == 0 else gender(names[3], 'm')
            if ns == 'genshinstrument':
                for key, text in english.items():
                    if '.genshin_notation.' in key and re.fullmatch(r'[A-Ga-g]', text):
                        values[key] = text
            if ns == 'chatplus':
                group_help = ['Використовуйте %GROUP_X%, де X — номер групи, щоб послатися на групу, знайдену шаблоном автоматичної вкладки.', 'Используйте %GROUP_X%, где X — номер группы, чтобы сослаться на группу, найденную шаблоном автоматической вкладки.'][index]
                player_help = ['Використовуйте %PLAYER%, щоб послатися на ім’я гравця.', 'Используйте %PLAYER%, чтобы сослаться на имя игрока.'][index]
                starts = {
                    'The auto prefix of the newly created tab.': ['Автоматичний префікс нової вкладки.', 'Автоматический префикс новой вкладки.'],
                    'The auto send of the newly created tab.': ['Текст, який автоматично надсилає нова вкладка.', 'Текст, который автоматически отправляет новая вкладка.'],
                    'The name of the newly created tab.': ['Назва нової вкладки.', 'Название новой вкладки.'],
                    'The regex pattern of the newly created tab.': ['Регулярний вираз нової вкладки.', 'Регулярное выражение новой вкладки.'],
                    'Regex Pattern. Can match any portion of the message.': ['Регулярний вираз. Може збігатися з будь-якою частиною повідомлення.', 'Регулярное выражение. Может совпадать с любой частью сообщения.'],
                }
                for key, text in english.items():
                    if '%PLAYER%' in text:
                        first = text.split('\n')[0]
                        prefix = starts[first][index] if first in starts else values[key].split('\n')[0]
                        values[key] = prefix + '\n \n' + (group_help + '\n \n' if '%GROUP_X%' in text else '') + player_help
                    if '%VALUE%' in text:
                        values[key] = ['Формат лічильника об’єднаних повідомлень. Використовуйте %VALUE% для кількості повторень повідомлення, а & — для кодів кольорів.', 'Формат счётчика объединённых сообщений. Используйте %VALUE% для количества повторений сообщения, а & — для кодов цветов.'][index]
            for key in english:
                if key.startswith('item.') and key.endswith('_spawn_egg'):
                    entity_key = key.removesuffix('_spawn_egg').replace('item.', 'entity.', 1)
                    if entity_key in values:
                        values[key] = ['Яйце виклику: ', 'Яйцо призывания: '][index] + values[entity_key]
            if ns == 'spawn':
                for animal in ['booby', 'seal']:
                    name = TERMS[{'booby': 'Booby', 'seal': 'Seal'}[animal]][index]
                    actions = {
                        'death': ['гине', 'погибает'], 'eat': ['їсть', 'ест'],
                        'hurt': ['дістає поранення', 'получает ранение'],
                        'idle': ['подає голос', 'подаёт голос'],
                        'flap': ['махає крилами', 'машет крыльями'],
                        'mating_call': ['подає шлюбний свист', 'подаёт брачный свист'],
                        'spit': ['плюється', 'плюётся'], 'step': ['пересувається', 'передвигается'],
                        'jiggle': ['погойдується', 'покачивается'],
                        'jump': ['вистрибує з води', 'выпрыгивает из воды'],
                        'trick': ['виконує трюк', 'выполняет трюк'],
                    }
                    for action, verbs in actions.items():
                        key = 'subtitles.entity.spawn.' + animal + '.' + action
                        if key in english:
                            values[key] = name + ' ' + verbs[index]
            changed += sum(value != before.get(key) for key, value in values.items())
            path.write_text(json.dumps(values, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    from chipped_terms import reviewed_names
    chipped_path = HERE / 'pack' / 'assets' / 'chipped' / 'lang' / 'ru_ru.json'
    chipped_values = json.loads(chipped_path.read_text())
    chipped_source = json.loads((HERE / 'upstream' / 'chipped.json').read_text())['en_us']
    print('Reviewed Russian Chipped material/texture names:', reviewed_names(chipped_source, chipped_values))
    chipped_path.write_text(json.dumps(chipped_values, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    # The Extras blocks are shapes of exact Chipped base blocks. Keep the
    # translated material/variant name identical rather than retranslating it.
    derived = 0
    extras_source = json.loads((HERE / 'upstream' / 'chippedexttras.json').read_text())['en_us']
    for index, locale in enumerate(['uk_ua', 'ru_ru']):
        chipped = json.loads((HERE / 'pack' / 'assets' / 'chipped' / 'lang' / (locale + '.json')).read_text())
        extras_path = HERE / 'pack' / 'assets' / 'chippedexttras' / 'lang' / (locale + '.json')
        extras = json.loads(extras_path.read_text())
        shapes = {'slab': ['Плита', 'Плита'], 'stairs': ['Сходи', 'Ступеньки'], 'wall': ['Огорожа', 'Ограда']}
        for key in extras_source:
            for suffix, labels in shapes.items():
                if key.endswith('_' + suffix):
                    base = key.removesuffix('_' + suffix).replace('block.chippedexttras.', 'block.chipped.')
                    if base in chipped:
                        extras[key] = labels[index] + ' (' + chipped[base] + ')'
                        derived += 1
                    break
        extras_path.write_text(json.dumps(extras, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    books = json.loads((HERE / 'upstream' / '_books.json').read_text())
    from build import formatting, TEXT_FIELDS
    kept = 0
    def preserve(source, target, original, field=''):
        nonlocal kept
        if isinstance(source, dict) and isinstance(original, dict):
            anchors = {'type', 'recipe', 'recipe2', 'icon', 'entry', 'category', 'item', 'image', 'entity'}
            if any(key in source and key in original and source[key] != original[key] for key in anchors):
                return target
            return {key: preserve(value, target[key], original.get(key), key) for key, value in source.items()}
        if isinstance(source, list) and isinstance(original, list):
            return [preserve(value, target[i], original[i] if i < len(original) else None, field) for i, value in enumerate(source)]
        if isinstance(source, str) and field in TEXT_FIELDS and isinstance(original, str) and original.strip() and original != source:
            if formatting(source) == formatting(original):
                kept += 1
                return original
        return target
    for path, source in books.items():
        if '/en_us/' not in path:
            continue
        for locale in ['uk_ua', 'ru_ru']:
            name = path.replace('/en_us/', '/' + locale + '/')
            target = HERE / 'pack' / name
            if name in books and target.exists():
                translated = preserve(source, json.loads(target.read_text()), books[name])
                target.write_text(json.dumps(translated, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    print('Reviewed terminology overrides applied:', changed)
    print('Consistent Chipped Extras shape names:', derived)
    print('Preserved original localized book passages:', kept)


if __name__ == '__main__':
    main()

