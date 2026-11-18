"""Remove unavailable Alex blocks and compact the Animal Dictionary to its retained species."""
import json
from pathlib import PurePosixPath

DISABLED_BLOCKS = {'alexsmobs:ender_residue', 'alexsmobs:sculk_boomer', 'alexsmobs:transmutation_table'}
BOOK = 'assets/alexsmobs/book/animal_dictionary/'


def strings(value):
    if isinstance(value, str):
        yield value.removeprefix('#')
    elif isinstance(value, dict):
        for child in value.values():
            yield from strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)


def encode(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()


def trim(entries, policy):
    pages = set()
    root = json.loads(entries[BOOK + 'root.json'])
    retained = set(policy['animals'])
    slots = [(row['x'], row['y'], row['page']) for row in root['entity_buttons']]
    buttons = [row for row in root['entity_buttons'] if row['entity'] in retained]
    assert {row['entity'] for row in buttons} == retained
    for button, (x, y, page) in zip(buttons, slots):
        button.update(x=x, y=y, page=page)
        pages.add(button['linked_page'])
    root['entity_buttons'] = buttons
    entries[BOOK + 'root.json'] = encode(root)
    texts = {'root.txt'} | {json.loads(entries[BOOK + page])['text'] for page in pages}
    for name in list(entries):
        path = PurePosixPath(name)
        if name.startswith(BOOK):
            if path.suffix == '.json' and path.name not in pages | {'root.json'} or path.suffix == '.txt' and path.name not in texts:
                del entries[name]
            continue
        if not name.startswith('data/alexsmobs/') or path.suffix != '.json':
            continue
        blocked = 'alexsmobs:' + path.stem in DISABLED_BLOCKS
        if '/loot_tables/blocks/' in name and blocked:
            entries[name] = encode({'type': 'minecraft:block', 'pools': []})
        elif '/loot_tables/gameplay/transmutation_table_' in name:
            entries[name] = encode({'type': 'minecraft:generic', 'pools': []})
        elif any(section in name for section in ('/recipes/', '/advancements/', '/capsid_recipes/')):
            values = set(strings(json.loads(entries[name])))
            if blocked or path.stem == 'transmute_1000_items' or values & DISABLED_BLOCKS:
                del entries[name]
    return entries
