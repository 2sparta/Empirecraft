#!/usr/bin/env python3
"""Reviewed terminology and Empirecraft-specific localization overrides."""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent

TERMS = {
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
                if key.endswith('.author') or key == 'translator':
                    values[key] = text
                if re.search(r'itemGroup|creativetab|item_group', key) and text in BRANDS:
                    values[key] = text
                if text == "Create's Building Blocks":
                    values[key] = ['Create: будівельні блоки', 'Create: строительные блоки'][index]
                if '.usage' in key or text.startswith('/'):
                    values[key] = text.replace('Usage:', ['Використання:', 'Использование:'][index])
                if text in TERMS:
                    values[key] = TERMS[text][index]
                if ns == 'vs_sails':
                    for wood, names in WOODS.items():
                        if key == 'block.vs_sails.' + wood + '_helm':
                            values[key] = ('Основа керма з ' if index == 0 else 'Основа штурвала из ') + names[index]
                        if key == 'block.vs_sails.' + wood + '_helm_wheel':
                            values[key] = ('Штурвал з ' if index == 0 else 'Штурвал из ') + names[index]
            if ns == 'empirecraft_calendar':
                for month, name in enumerate(MONTHS[locale], 1):
                    values['calendar.empirecraft.month.' + str(month)] = name
                values['calendar.empirecraft.date'] = '%2$s • день %1$s/30'
                values['calendar.empirecraft.duration'] = ['Доба: 48 хвилин. Місяць: 30 днів.', 'Сутки: 48 минут. Месяц: 30 дней.'][index]
            if ns == 'sereneseasons':
                values['commands.sereneseasons.usage'] = ['/season <get/set> [аргументи]', '/season <get/set> [аргументы]'][index]
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
