#!/usr/bin/env python3
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


class TitleIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = build.catalog(build.source())
        cls.menus = {name: yaml.safe_load((build.ROOT / 'server/plugins/DeluxeMenus/gui_menus' / (name + '.yml')).read_text())
            for name in ['titles', 'tituls_plat', 'tituls_znamenat', 'tituls_season']}

    def test_every_entitlement_has_one_slot_and_two_guarded_states(self):
        self.assertEqual(39, len(self.data))
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
        self.assertEqual(39, len(seen))

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

    def test_java_transactions(self):
        classes = build.HERE / 'build/tests'
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
            self.assertEqual(['LuckPerms', 'Vault'], metadata['depend'])
            for title in self.data.values():
                self.assertIs(False, metadata['permissions'][title['permission']]['default'])
            self.assertIn('ua/empirecraft/titles/TitlesPlugin.class', jar.namelist())
            self.assertFalse(any(n.startswith(('org/bukkit/', 'net/luckperms/', 'net/milkbowl/')) for n in jar.namelist()))


if __name__ == '__main__':
    unittest.main(argv=[sys.argv[0]], verbosity=2)
