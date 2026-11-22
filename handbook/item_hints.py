"""Generate item links in TFC's Field Guide using Patchouli's extra recipe mappings."""

import json
import re
import zipfile

from layout import WRAPPED_TEMPLATE, fit

# TFC queries this book for tooltip links even when the player's hotbar has no book.
FIELD_GUIDE = 'assets/tfc/patchouli_books/field_guide/'
CATEGORY = 'empirecraft'
CATEGORIES = {
    'growing': ('Рослини й ґрунти', 'farmersdelight:tomato_seeds', 'Дикі оселища, посадковий матеріал і живлення культур.'),
    'kitchen': ('Кухня й напої', 'farmersdelight:cooking_pot', 'Нарізання, готування, заготівлі й бродіння.'),
    'workshop': ('Майстерня', 'create:cogwheel', 'Обертання, верстати, креслення та оздоблення.'),
    'travel': ('Подорожі', 'astikorcarts:supply_cart', 'Віз, вітрила, питна вода та рибальські снасті.'),
}
CLIMATES = {
    'any': 'придатна природна місцевість',
    'temperate': 'помірний клімат',
    'warm': 'теплий клімат',
    'hot': 'спекотний клімат',
    'cold': 'холодний клімат',
    'wet': 'вологе місце біля води',
}
SOILS = {
    'CHERNOZEM': 'чорнозем', 'CLAY': 'глина', 'LOAM': 'суглинок', 'PEAT': 'торф',
    'SAND': 'пісок', 'SANDY_LOAM': 'супісок', 'SILTY_LOAM': 'мулистий суглинок',
    'SILT': 'мул', 'WOOD': 'деревина',
}
NUTRIENTS = {'NITROGEN': 'азот', 'PHOSPHOROUS': 'фосфор', 'POTASSIUM': 'калій'}
ITEM_MODEL = re.compile(r'assets/([^/]+)/models/item/(.+)\.json')


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def scan_pack(repo):
    item_models = set()
    native_items = set()
    native_files = set()
    for path in sorted((repo / 'mods').glob('*.jar')):
        if path.name.startswith('empirecraft-handbook-'):
            continue
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                model = ITEM_MODEL.fullmatch(name)
                if model:
                    item_models.add(f'{model[1]}:{model[2]}')
                if not name.startswith(FIELD_GUIDE) or not name.endswith('.json'):
                    continue
                native_files.add(name)
                if '/entries/' in name:
                    entry = json.loads(jar.read(name))
                    for items in entry.get('extra_recipe_mappings', {}):
                        native_items.update(items.split(','))
    return item_models, native_items, native_files


def build_item_hints(repo, entries, pages):
    root = repo / 'handbook'
    crops = read_json(root / 'item-hint-crops.json')
    topics = read_json(root / 'item-hint-topics.json')
    vegetation = read_json(
        repo / 'seasonal-leaves/src/main/resources/vegetation-profiles.json'
    )['renewal_plants']
    climate = {row['id']: CLIMATES[row['climate']] for row in vegetation}
    profiles = read_json(
        repo / 'tfc-integration/src/main/resources/empirecraft-crop-profiles.json'
    )
    soils = {block: profile for profile in profiles for block in profile['blocks']}
    if difference := crops.keys() ^ climate.keys():
        raise ValueError(f'Wild plant hints and vegetation profiles differ: {sorted(difference)}')

    item_models, native_items, native_files = scan_pack(repo)
    field_entries = {}
    mapped_items = {}

    def add(key, category, title, items, content):
        if not items or not content:
            raise ValueError(f'Field Guide entry {key} needs an item and a page')
        for item in items:
            if not item.startswith('minecraft:') and item not in item_models:
                raise ValueError(f'Field Guide entry {key} references unknown item {item}')
            if item in native_items:
                raise ValueError(f'Field Guide entry {key} would replace the native link for {item}')
            if item in mapped_items:
                raise ValueError(f'{item} maps to both {mapped_items[item]} and {key}')
            mapped_items[item] = key
        field_entries[key] = {
            'name': title,
            'icon': items[0],
            'category': f'tfc:{CATEGORY}/{category}',
            'sortnum': len(field_entries),
            'extra_recipe_mappings': dict.fromkeys(items, 0),
            'pages': [dict(page) for page in content],
        }

    for plant, crop in crops.items():
        if 'guide' in crop:
            content = list(entries[crop['guide']]['pages'])
        else:
            content = pages(crop['tip'])
        profile = soils.get(crop.get('profile', plant))
        if profile:
            preferred = ', '.join(SOILS[name] for name in profile['preferred'])
            tolerated = ', '.join(SOILS[name] for name in profile['tolerated'])
            content += pages(
                f'Бажані ґрунти — {preferred}. Допустимі — {tolerated}. '
                f'Основна поживна речовина — {NUTRIENTS[profile["nutrient"]]}. '
                'Для перевірки в грі затисни Shift і клацни порожньою рукою рослину або ґрунт.'
            )
        content += pages(
            f'Дика поява — {climate[plant]}. Шукай навесні, влітку й восени. '
            'Для посівів діють окремі умови росту.'
        )
        add('plants/' + plant.replace(':', '/'), 'growing', crop['title'], crop['items'], content)

    for key, topic in topics.items():
        items = list(topic.get('items', []))
        for family in topic.get('families', []):
            family_items = sorted(item for item in item_models if re.fullmatch(family, item))
            if not family_items:
                raise ValueError(f'No installed items match {family}')
            items.extend(family_items)
        content = pages(topic.get('text', ''))
        if 'guide' in topic:
            content.extend(entries[topic['guide']]['pages'])
        add(key, topic['category'], topic['title'], items, content)

    category_files = {
        CATEGORY: {
            'name': 'Довідка Empirecraft',
            'description': 'Наведи курсор на предмет і затисни клавішу, показану біля значка книжки.',
            'icon': 'minecraft:book',
            'sortnum': 90,
        },
        **{
            f'{CATEGORY}/{key}': {
                'name': title,
                'description': description,
                'icon': icon,
                'parent': f'tfc:{CATEGORY}',
                'sortnum': order,
            }
            for order, (key, (title, icon, description)) in enumerate(CATEGORIES.items())
        },
    }
    fit(field_entries, template=f'tfc:{CATEGORY}/wrapped_text')
    files = {}
    for locale in ('en_us', 'uk_ua'):
        prefix = FIELD_GUIDE + locale + '/'
        files[prefix + f'templates/{CATEGORY}/wrapped_text.json'] = WRAPPED_TEMPLATE
        for key, category in category_files.items():
            files[prefix + f'categories/{key}.json'] = category
        for key, entry in field_entries.items():
            files[prefix + f'entries/{CATEGORY}/{key}.json'] = entry
    if collisions := native_files & files.keys():
        raise ValueError(f'Field Guide resources already exist: {sorted(collisions)}')

    return files, {
        'book': 'tfc:field_guide',
        'entries': len(field_entries),
        'wild_plants': len(crops),
        'items': len(mapped_items),
        'namespaces': sorted({item.split(':', 1)[0] for item in mapped_items}),
    }
