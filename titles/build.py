#!/usr/bin/env python3
"""Reproduce the supplied title art, menus and transaction plugin for Forge/Mohist 1.20.1."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import urllib.request
import zipfile
import yaml

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
VERSION = '1.1.0'
RESOURCE_VERSION = '1.0.0'
MOD_ID = 'empirecraft_titles'
MOD_NAME = f'empirecraft-titles-resources-{RESOURCE_VERSION}.jar'
PLUGIN_NAME = f'empirecraft-titles-{VERSION}.jar'
SPIGOT_URL = ('https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/'
              '1.20.1-R0.1-SNAPSHOT/spigot-api-1.20.1-R0.1-20230921.163938-66-shaded.jar')
SPIGOT_SHA = '465c8fce2efa60da5517c312dcc57c957908c608f23f92b14216caecc0e85713'


def yaml_bytes(data):
    return yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=120).encode('utf-8')


def write_zip(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data)


def checked_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    path.with_suffix(path.suffix + '.sha256').write_text(hashlib.sha256(data).hexdigest() + '  ' + path.name + '\n')


def source():
    with zipfile.ZipFile(HERE / 'source.zip') as archive:
        return {name: archive.read(name) for name in archive.namelist()}


def glyph(name):
    # Stable, independent of file sorting and missing textures in the original archive.
    return chr(0xE800 + int(name[3:]) - 1) if re.fullmatch(r'tit\d+', name) else chr(0xE880)


def formatted(text):
    text = text.replace('§', '&')
    text = re.sub(r'&#([0-9A-Fa-f]{6})', lambda m: '&x' + ''.join('&' + c for c in m[1]), text)
    return re.sub(r'%img_(tit\d+)%', lambda m: glyph(m[1]), text)


def plain(text):
    return re.sub(r'&[0-9a-fk-orx]', '', formatted(text), flags=re.I).strip()


def original_suffix(item):
    value = next(c.split('meta setsuffix 1 ', 1)[1] for c in item['left_click_commands'] if 'meta setsuffix 1 ' in c)
    return ' ' + formatted(value.strip().strip('"').strip()) + '&r'


def catalog(files):
    def menu(name): return yaml.safe_load(files['DeluxeMenus/gui_menus/' + name + '.yml'])['items']
    result = {}
    paid = menu('tituls_plat')
    for number in range(1, 29):
        item = paid[f'Титул {number} Доступен']
        suffix = original_suffix(item)
        label = plain(suffix)
        if any(0xE800 <= ord(c) <= 0xE880 for c in label): label = f'Титул {number}'
        result[f'shop_{number}'] = dict(permission=f'titul.{number}', suffix=suffix, label=label,
            kind='achievement', group='classic')
    names = ['Діамантовий магнат', 'Досвідчений мореплавець', 'Великий визволитель',
             'Бджоляр', 'Підривник', 'Лісоруб', 'Мисливець на скелетів', 'Золотий магнат', 'Лазуритовий магнат']
    for number, label in enumerate(names, 1):
        item = menu('tituls_znamenat')[str(number)]
        result[f'achievement_{number}'] = dict(permission=f'title.{number}', suffix=original_suffix(item),
            label=label, kind='achievement', group='progress')
    for number, label in [(1, 'Травень'), (2, 'Салют')]:
        item = menu('tituls_season')[f'{number}_open']
        result[f'season_{number}'] = dict(permission=f'tituls.3_{number}', suffix=original_suffix(item),
            label=label, kind='season', group='season', requirements=[])
    acquisition = json.loads((HERE / 'acquisition.json').read_text())
    art = acquisition['artwork']
    seen = set()
    for title_id, title in result.items():
        if title['kind'] == 'season': continue
        used = re.findall(r'%img_(tit\d+)%', next(
            c for c in (paid[f"Титул {title_id[5:]} Доступен"] if title_id.startswith('shop_') else
                menu('tituls_znamenat')[title_id[12:]])['left_click_commands'] if 'meta setsuffix 1 ' in c))
        if used:
            assert len(used) == 1 and used[0] in art
            name = used[0]
            seen.add(name)
            title['artwork'] = name
            title['requirements'] = acquisition['achievements'].get(title_id, art[name]['requirements'])
            if title_id.startswith('shop_'): title['label'] = art[name]['label']
        else:
            title['requirements'] = acquisition['classic'][title_id]
    for name, profile in art.items():
        image = 'ItemsAdder/contents/icons/resourcepack/assets/icons/textures/icons/titles/' + name + '.png'
        if image not in files: raise ValueError('Missing title artwork: ' + name)
        if name in seen: continue
        result['art_' + name] = dict(permission='empirecraft.titles.art.' + name,
            suffix=' &f' + glyph(name) + '&r', label=profile['label'], kind='achievement', group='progress',
            artwork=name, requirements=profile['requirements'])
    for group in ['classic', 'progress', 'season']:
        for index, title in enumerate(t for t in result.values() if t['group'] == group):
            title['page'] = index // 36 + 1
            title['slot'] = index % 36 + 9
    return result


def button(material, slot, name, lore, commands):
    return dict(material=material, slot=slot, display_name=name, lore=lore, left_click_commands=commands)


def menu(title, command, size=54):
    return dict(menu_title='&8' + title, open_command=[command], register_command=True, size=size, items={})


def permission(node, owns=True):
    return dict(type='has permission' if owns else '!has permission', permission=node)


def menus(data):
    main = menu('Титули Empirecraft', 'titles', 18)
    main['items']['close'] = button('BARRIER', 0, '&cЗакрити', [], ['[close]'])
    main['items']['clear'] = button('MILK_BUCKET', 8, '&eЗняти титул',
        ['&7Права на отримані титули збережуться.'], ['[close]', '[player] etitle clear'])
    groups = [('progress', 'tituls_znamenat', 'Досягнення та ремесла', 'DIAMOND', 2),
              ('season', 'tituls_season', 'Пам’ятні титули', 'FIREWORK_ROCKET', 4),
              ('classic', 'tituls_plat', 'Класичні титули', 'GOLD_INGOT', 6)]
    for group, command, heading, material, slot in groups:
        count = sum(t['group'] == group for t in data.values())
        main['items'][group] = button(material, slot, '&6' + heading,
            [f'&7Титулів: &f{count}', '&eНатисніть, щоб відкрити.'], ['[openguimenu] ' + command])
    main['items']['info'] = button('BOOK', 13, '&6Про титули',
        [f'&7Усього титулів: &f{len(data)}', '&7Нагороди за статистику, ремесла й досягнення.',
         '&7Виконані умови перевіряються автоматично.', '&7Нові нагороди не змінюють обраний титул.',
         '&7Політичні титули /title існують окремо.'], [])
    result = {'titles': main}
    for group, base, heading, _, _ in groups:
        entries = [(key, title) for key, title in data.items() if title['group'] == group]
        pages = max(t['page'] for _, t in entries)
        def page_name(page): return base if page == 1 else f'{base}_{page}'
        for page in range(1, pages + 1):
            command = page_name(page)
            current = menu(f'{heading} — {page}/{pages}', command)
            info = ['&7Уже отримані титули можна обирати вільно.']
            if group == 'season': info += ['&7Пам’ятні нагороди за серверні події.', '&7Участь підтверджує адміністрація.']
            else: info += ['&7Виконайте всі вказані умови.', '&7Клік перевірить прогрес і обере нагороду.',
                          '&7Прогрес зберігається у статистиці сервера.']
            current['items']['info'] = button('BOOK', 4, '&6Інформація', info, [])
            current['items']['back'] = button('ARROW', 49, '&eДо розділів', [], ['[openguimenu] titles'])
            current['items']['clear'] = button('MILK_BUCKET', 53, '&eЗняти титул', [], ['[close]', '[player] etitle clear'])
            if page > 1:
                current['items']['previous'] = button('ARROW', 45, '&eПопередня сторінка', [], ['[openguimenu] ' + page_name(page - 1)])
            if page < pages:
                current['items']['next'] = button('ARROW', 50, '&eНаступна сторінка', [], ['[openguimenu] ' + page_name(page + 1)])
            for key, title in entries:
                if title['page'] != page: continue
                lore = ['&7Титул: &r' + title['suffix'], '']
                for r in title['requirements']:
                    lore += ['&7• ' + r['description']]
                locked = button('BARRIER' if group == 'season' else 'NAME_TAG', title['slot'], '&6' + title['label'],
                    lore + (['&cНагорода адміністрації за серверну подію.'] if group == 'season' else ['&eНатисніть: перевірити й отримати.']),
                    ['[close]', '[player] etitle unlock ' + key])
                owned = button('NAME_TAG', title['slot'], '&a' + title['label'], lore + ['&aОтримано. Натисніть, щоб обрати.'],
                    ['[close]', '[player] etitle choose ' + key])
                for item, available in [(locked, False), (owned, True)]:
                    item['priority'] = 1 if available else 2
                    item['view_requirement'] = dict(requirements=dict(ownership=permission(title['permission'], available)))
                    item['update'] = True
                owned['left_click_requirement'] = dict(requirements=dict(ownership=permission(title['permission'])))
                current['items'][key + '_locked'] = locked
                current['items'][key + '_owned'] = owned
            result[command] = current
    return result


def build_resources(files):
    providers, resources, missing = [], {}, []
    definitions = yaml.safe_load(files['ItemsAdder/contents/icons/configs/icons.yml'])['font_images']
    names = list(definitions) + ['dune']
    for name in names:
        image_path = 'ItemsAdder/contents/icons/resourcepack/assets/icons/textures/icons/titles/' + name + '.png'
        if image_path not in files:
            missing.append(name)
            continue
        png = files[image_path]
        assert png[:8] == b'\x89PNG\r\n\x1a\n'
        width, height = struct.unpack('>II', png[16:24])
        assert width > 0 and height > 0
        target = 'assets/empirecraft_titles/textures/titles/' + name + '.png'
        resources[target] = png
        providers.append(dict(type='bitmap', file='empirecraft_titles:titles/' + name + '.png',
            height=8, ascent=8, chars=[glyph(name)]))
    resources['assets/minecraft/font/default.json'] = json.dumps(dict(providers=providers), ensure_ascii=False, indent=2).encode()
    resources['pack.mcmeta'] = json.dumps(dict(pack=dict(pack_format=15, description='Empirecraft — графічні титули')), ensure_ascii=False, indent=2).encode()
    pack = HERE / 'dist/Empirecraft-Titles-1.0.0.zip'
    write_zip(pack, resources)
    checked_write(pack, pack.read_bytes())
    resources['META-INF/mods.toml'] = b'''modLoader="lowcodefml"
loaderVersion="[47,)"
license="All Rights Reserved; supplied Titles.rar artwork"
showAsResourcePack=false
[[mods]]
modId="empirecraft_titles"
version="1.0.0"
displayName="Empirecraft Titles Resources"
description="Graphical title font from the supplied Titles.rar; no executable code."
displayTest="IGNORE_ALL_VERSION"
[[dependencies.empirecraft_titles]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.20.2)"
ordering="NONE"
side="BOTH"
'''
    target = ROOT / 'mods' / MOD_NAME
    write_zip(target, resources)
    checked_write(target, target.read_bytes())
    checked_write(ROOT / 'server/mods' / MOD_NAME, target.read_bytes())
    (HERE / 'adm-pin.json').write_text(json.dumps(dict(id=MOD_ID, version=RESOURCE_VERSION,
        sha256=hashlib.sha256(target.read_bytes()).hexdigest(), filePresent=True), indent=2) + '\n')
    (HERE / 'glyphs.json').write_text(json.dumps({name: dict(character=glyph(name), codepoint=f'U+{ord(glyph(name)):04X}')
        for name in names if name not in missing}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return dict(glyph_count=len(providers), missing_source_textures=missing,
                resource_mod_sha256=hashlib.sha256(target.read_bytes()).hexdigest())


def compile_plugin(data):
    library = ROOT / 'work/titles/lib/spigot-api.jar'
    library.parent.mkdir(parents=True, exist_ok=True)
    if not library.exists():
        with urllib.request.urlopen(SPIGOT_URL, timeout=120) as response: library.write_bytes(response.read())
    if hashlib.sha256(library.read_bytes()).hexdigest() != SPIGOT_SHA:
        raise ValueError('Spigot API checksum mismatch')
    dependencies = [library, ROOT / 'server/plugins/LuckPerms-Bukkit-5.5.36.jar']
    for path in dependencies:
        if not path.is_file(): raise FileNotFoundError(path)
    classes = ROOT / 'work/titles/classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir(parents=True)
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '--release', '17', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, dependencies)),
        '-d', str(classes), *map(str, sorted((HERE / 'src').rglob('*.java')))], check=True)
    metadata = dict(name='EmpirecraftTitles', version=VERSION, main='ua.empirecraft.titles.TitlesPlugin',
        **{'api-version': '1.20'}, depend=['LuckPerms'], softdepend=['DeluxeMenus', 'TAB', 'Towny'],
        commands=dict(etitle=dict(description='Керування косметичними титулами', permission='empirecraft.titles.use')),
        permissions={'empirecraft.titles.use': dict(default=True), **{t['permission']: dict(default=False) for t in data.values()}})
    resources = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    resources.update({'plugin.yml': yaml_bytes(metadata), 'catalog.yml': yaml_bytes(data),
        'config.yml': yaml_bytes({'check-interval-seconds': 60})})
    target = ROOT / 'server/plugins' / PLUGIN_NAME
    write_zip(target, resources)
    checked_write(target, target.read_bytes())
    return hashlib.sha256(target.read_bytes()).hexdigest()


def write_guide(data):
    lines = ['# Каталог титулів Empirecraft 1.1.0', '',
        '135 титулів за прогрес і 2 пам’ятні нагороди. Умови одного титулу виконуються разом.', '',
        '| ID | Титул | Умови отримання |', '| --- | --- | --- |']
    for key, title in data.items():
        conditions = ' '.join(r['description'] for r in title['requirements']) or 'Нагорода адміністрації за серверну подію.'
        lines.append(f"| `{key}` | {title['label']} | {conditions} |")
    (HERE / 'CATALOGUE.uk.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def release_bundle():
    paths = [ROOT / 'server/plugins' / PLUGIN_NAME, ROOT / 'server/plugins' / (PLUGIN_NAME + '.sha256'),
        ROOT / 'server/plugins/EmpirecraftTitles/config.yml', ROOT / 'server/plugins/DeluxeMenus/config.yml',
        HERE / 'README.md', HERE / 'CATALOGUE.uk.md', ROOT / f'validation/titles-{VERSION}.json']
    paths += [ROOT / 'server/plugins/DeluxeMenus/gui_menus' / (name + '.yml') for name in menus(catalog(source()))]
    files = {p.relative_to(ROOT).as_posix(): p.read_bytes() for p in paths}
    files['REMOVE-OLD.txt'] = b'server/plugins/empirecraft-titles-1.0.0.jar\nserver/plugins/empirecraft-titles-1.0.0.jar.sha256\n'
    path = HERE / f'dist/Empirecraft-Titles-{VERSION}.zip'
    write_zip(path, files)
    checked_write(path, path.read_bytes())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resources-only', action='store_true', help='Generate assets and menus without compiling the plugin')
    args = parser.parse_args()
    files = source()
    data = catalog(files)
    for name, value in menus(data).items():
        target = ROOT / 'server/plugins/DeluxeMenus/gui_menus' / (name + '.yml')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(yaml_bytes(value))
    (HERE / 'catalog.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    settings = ROOT / 'server/plugins/EmpirecraftTitles/config.yml'
    settings.parent.mkdir(parents=True, exist_ok=True)
    settings.write_bytes(yaml_bytes({'check-interval-seconds': 60}))
    registration = ROOT / 'server/plugins/DeluxeMenus/config.yml'
    config = yaml.safe_load(registration.read_text())
    for name in menus(data): config['gui_menus'][name] = dict(file=name + '.yml')
    registration.write_bytes(yaml_bytes(config))
    report = build_resources(files)
    report.update(title_count=len(data), shop_titles=0, achievement_titles=sum(t['kind'] == 'achievement' for t in data.values()), seasonal_titles=2,
        source_sha256=hashlib.sha256((HERE / 'source.zip').read_bytes()).hexdigest(),
        runtime_tested=False)
    write_guide(data)
    if not args.resources_only:
        report['plugin_sha256'] = compile_plugin(data)
    target = ROOT / f'validation/titles-{VERSION}.json'
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if not args.resources_only: release_bundle()
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
