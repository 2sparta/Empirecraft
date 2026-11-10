#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
import unittest
import zipfile
import yaml
import build
import pin

OPTIONS = argparse.Namespace(server_api=None, api_libraries=None)


class TitleIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = build.catalog(build.source())
        cls.menus = {name: yaml.safe_load((build.ROOT / 'server/plugins/DeluxeMenus/gui_menus' / (name + '.yml')).read_text())
            for name in build.menus(cls.data)}

    def test_every_entitlement_has_one_slot_and_two_guarded_states(self):
        self.assertEqual(137, len(self.data))
        seen = set()
        for name, menu in self.menus.items():
            for title_id, title in self.data.items():
                key = title_id + '_owned'
                if key not in menu['items']: continue
                identity = (name, title['slot'])
                self.assertNotIn(identity, seen)
                seen.add(identity)
                owned = menu['items'][key]
                locked = menu['items'][title_id + '_locked']
                self.assertEqual(title['slot'], owned['slot'])
                self.assertEqual(title['slot'], locked['slot'])
                self.assertEqual(title['permission'], owned['view_requirement']['requirements']['ownership']['permission'])
                self.assertEqual('!has permission', locked['view_requirement']['requirements']['ownership']['type'])
                self.assertEqual(title['permission'], owned['left_click_requirement']['requirements']['ownership']['permission'])
                self.assertEqual('[player] etitle choose ' + title_id, owned['left_click_commands'][-1])
        self.assertEqual(137, len(seen))

    def test_all_artwork_is_selectable_exactly_once(self):
        artwork = [t['artwork'] for t in self.data.values() if 'artwork' in t]
        actual = {Path(n).stem for n in build.source() if n.endswith('.png')}
        self.assertEqual(actual, set(artwork))
        self.assertEqual(115, len(artwork))
        self.assertEqual(115, len(set(artwork)))

    def test_legacy_permissions_suffixes_and_seasons_survive(self):
        source = build.source()
        for number in range(1, 29):
            t = self.data[f'shop_{number}']
            original = yaml.safe_load(source['DeluxeMenus/gui_menus/tituls_plat.yml'])['items'][f'Титул {number} Доступен']
            self.assertEqual(f'titul.{number}', t['permission'])
            self.assertEqual(build.original_suffix(original), t['suffix'])
        for number in range(1, 10):
            t = self.data[f'achievement_{number}']
            original = yaml.safe_load(source['DeluxeMenus/gui_menus/tituls_znamenat.yml'])['items'][str(number)]
            self.assertEqual(f'title.{number}', t['permission'])
            self.assertEqual(build.original_suffix(original), t['suffix'])
        for number in range(1, 3):
            t = self.data[f'season_{number}']
            original = yaml.safe_load(source['DeluxeMenus/gui_menus/tituls_season.yml'])['items'][f'{number}_open']
            self.assertEqual(f'tituls.3_{number}', t['permission'])
            self.assertEqual(build.original_suffix(original), t['suffix'])
            self.assertEqual('season', t['kind'])
            self.assertEqual([], t['requirements'])

    def test_pages_registration_and_generated_outputs(self):
        self.assertEqual(build.menus(self.data), self.menus)
        self.assertEqual(self.data, json.loads((build.HERE / 'catalog.json').read_text()))
        config = yaml.safe_load((build.ROOT / 'server/plugins/DeluxeMenus/config.yml').read_text())
        for name, menu in self.menus.items():
            self.assertEqual(name + '.yml', config['gui_menus'][name]['file'])
            positions = {}
            for key, item in menu['items'].items():
                positions.setdefault(item['slot'], []).append(key)
            for keys in positions.values():
                if len(keys) == 1: continue
                self.assertEqual(2, len(keys))
                self.assertEqual({k.rsplit('_', 1)[0] for k in keys}, {keys[0].rsplit('_', 1)[0]})
        self.assertEqual(6, len(self.menus))
        self.assertIn('tituls_znamenat_3', self.menus)

    def test_acquisition_matches_installed_server_mechanics(self):
        mod_advancements = {}
        for pattern in ['overgeared-*.jar', 'FarmersDelight-*.jar', 'create-1.20.1-*.jar']:
            matches = list((build.ROOT / 'mods').glob(pattern))
            self.assertEqual(1, len(matches), pattern)
            with zipfile.ZipFile(matches[0]) as jar:
                for path in jar.namelist():
                    match = re.fullmatch(r'data/([^/]+)/advancements/(.+)\.json', path)
                    if match: mod_advancements[match[1] + ':' + match[2]] = json.loads(jar.read(path))
        vanilla = {'minecraft:adventure/shoot_arrow', 'minecraft:adventure/hero_of_the_village',
            'minecraft:adventure/sniper_duel', 'minecraft:adventure/ol_betsy',
            'minecraft:husbandry/safely_harvest_honey'}
        allowed_create = {'water_wheel', 'windmill', 'mechanical_press', 'millstone', 'saw_processing', 'andesite_alloy'}
        for t in self.data.values():
            self.assertIn(t['kind'], {'achievement', 'season'})
            self.assertTrue(bool(t['requirements']) == (t['kind'] == 'achievement'))
            for r in t['requirements']:
                self.assertGreater(r['amount'], 0)
                self.assertIn(r['type'], {'advancement', 'statistic', 'towny'})
                text = json.dumps(r).lower()
                for disabled in ['dragon', 'the_end', 'nether', 'enchant', 'elytra', 'shulker', 'plant_all_crops']:
                    self.assertNotIn(disabled, text)
                if r['type'] == 'advancement':
                    self.assertEqual(1, r['amount'])
                    self.assertIn(r['key'], vanilla | set(mod_advancements))
                    self.assertNotIn('/recipes/', r['key'])
                    if r['key'].startswith('create:'): self.assertIn(r['key'][7:], allowed_create)
                if r['type'] == 'towny': self.assertIn(r['key'], {'member', 'nation', 'mayor'})

    def test_no_missing_dependencies_or_privileged_menu_commands(self):
        for menu in self.menus.values():
            text = yaml.safe_dump(menu, allow_unicode=True)
            for missing in ['%player_name%', '%img_', '%coinsengine_', ':offset_', ':titles:', '[console]', 'meta clear', 'view_requirements:']:
                self.assertNotIn(missing, text)
            for item in menu['items'].values():
                self.assertLess(item['slot'], menu['size'])
                for command in item.get('left_click_commands', []):
                    if command.startswith('[openguimenu] '): self.assertIn(command.split(' ', 1)[1], self.menus)

    def test_used_glyphs_resolve_and_original_pngs_are_unchanged(self):
        mapping = json.loads((build.HERE / 'glyphs.json').read_text())
        chars = {value['character'] for value in mapping.values()}
        self.assertEqual(115, len(chars))
        for title in self.data.values():
            self.assertTrue({c for c in title['suffix'] if 0xE800 <= ord(c) <= 0xE880} <= chars)
        original = build.source()
        with zipfile.ZipFile(build.ROOT / 'mods' / build.MOD_NAME) as jar:
            font = json.loads(jar.read('assets/minecraft/font/default.json'))
            self.assertEqual(115, len(font['providers']))
            for provider in font['providers']:
                name = provider['file'].split('/')[-1]
                self.assertEqual(original['ItemsAdder/contents/icons/resourcepack/assets/icons/textures/icons/titles/' + name],
                    jar.read('assets/empirecraft_titles/textures/titles/' + name))
            toml = tomllib.loads(jar.read('META-INF/mods.toml').decode())
            self.assertEqual('lowcodefml', toml['modLoader'])
            self.assertFalse(any(n.endswith('.class') for n in jar.namelist()))
            self.assertEqual(15, json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'])
        self.assertEqual((build.ROOT / 'mods' / build.MOD_NAME).read_bytes(),
            (build.ROOT / 'server/mods' / build.MOD_NAME).read_bytes())

    def test_java_progression_rules(self):
        classes = build.ROOT / 'work/titles/tests'
        classes.mkdir(parents=True, exist_ok=True)
        compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
        subprocess.run([*compiler, '--release', '17', '-d', str(classes),
            str(build.HERE / 'src/ua/empirecraft/titles/TitleRules.java'),
            str(build.HERE / 'tests/TitleRulesTest.java')], check=True)
        subprocess.run(['java', '-cp', str(classes), 'ua.empirecraft.titles.TitleRulesTest'], check=True)

    def test_adm_pin_keeps_unrelated_state_and_detects_tampering(self):
        data = json.loads((build.ROOT / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text(encoding='utf-8-sig'))
        sha = hashlib.sha256((build.ROOT / 'mods' / build.MOD_NAME).read_bytes()).hexdigest()
        changed = pin.prepare_adm(data, sha)
        self.assertEqual(data['resourcePackIds'], changed['resourcePackIds'])
        self.assertEqual([e for e in data['modIds'] if e['id'] != build.MOD_ID],
            [e for e in changed['modIds'] if e['id'] != build.MOD_ID])
        self.assertEqual(pin.integrity(changed), changed['integrityHash'])
        self.assertEqual(changed, pin.prepare_adm(changed, sha))
        changed['integrityHash'] = 'invalid'
        with self.assertRaises(ValueError): pin.prepare_adm(changed, sha)

    def test_plugin_catalog_and_permissions_if_built(self):
        target = build.ROOT / 'server/plugins' / build.PLUGIN_NAME
        if not target.exists():
            if '--require-plugin' in sys.argv: self.fail('The compiled server plugin is missing')
            self.skipTest('Full plugin compilation runs in GitHub Actions with the installed API jars')
        with zipfile.ZipFile(target) as jar:
            self.assertIsNone(jar.testzip())
            metadata = yaml.safe_load(jar.read('plugin.yml'))
            self.assertEqual(self.data, yaml.safe_load(jar.read('catalog.yml')))
            self.assertEqual(['LuckPerms'], metadata['depend'])
            self.assertIn('Towny', metadata['softdepend'])
            for title in self.data.values():
                self.assertIs(False, metadata['permissions'][title['permission']]['default'])
            self.assertIn('ua/empirecraft/titles/TitlesPlugin.class', jar.namelist())
            self.assertIn('ua/empirecraft/titles/ServerProgress.class', jar.namelist())
            self.assertFalse(any(n.startswith(('org/bukkit/', 'net/luckperms/', 'net/milkbowl/')) for n in jar.namelist()))
    def test_server_progress_adapter(self):
        library = Path(OPTIONS.server_api).resolve() if OPTIONS.server_api else build.ROOT / 'work/titles/lib/spigot-api.jar'
        if not library.exists():
            if '--require-plugin' in sys.argv: self.fail('Real Spigot API required for adapter tests')
            self.skipTest('The API download is not yet available')
        classes = build.ROOT / 'work/titles/adapter-tests'
        classes.mkdir(parents=True, exist_ok=True)
        compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
        sources = [build.HERE / 'src/ua/empirecraft/titles' / n for n in ['TitleRules.java', 'ServerProgress.java']]
        sources += [build.HERE / 'tests/ServerProgressTest.java']
        import os
        dependencies = [library]
        if OPTIONS.api_libraries: dependencies += sorted(Path(OPTIONS.api_libraries).resolve().glob('*.jar'))
        cp = os.pathsep.join(map(str, dependencies))
        subprocess.run([*compiler, '--release', '17', '-encoding', 'UTF-8', '-cp', cp,
            '-d', str(classes), *map(str, sources)], check=True)
        subprocess.run(['java', '-cp', os.pathsep.join([str(classes), cp]),
            'ua.empirecraft.titles.ServerProgressTest', str(build.ROOT / 'server/plugins' / build.PLUGIN_NAME)], check=True)



if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--require-plugin', action='store_true')
    parser.add_argument('--server-api')
    parser.add_argument('--api-libraries')
    OPTIONS = parser.parse_args()
    unittest.main(argv=[sys.argv[0]], verbosity=2)
