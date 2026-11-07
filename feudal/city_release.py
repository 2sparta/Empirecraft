#!/usr/bin/env python3
"""Verify/install descriptor versions and produce a deterministic city-markets update."""
import argparse
import hashlib
import io
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from release import REPO, PLUGIN, PLUGIN_VERSION, bridge_name, bridge_version

BRIDGE = bridge_name()
RECRUITS_ROW = next(row for row in json.loads((REPO/'modpack-lock.json').read_text())['mods'] if row['id']=='townyrecruits')
RECRUITS = RECRUITS_ROW['file']
CHATPLUS = 'chatplus-forge-2.8.1-empirecraft.2.jar'
CHAT_PLUGIN = 'empirecraft-chat-1.1.0.jar'
REPORT = 'city-markets-' + PLUGIN_VERSION
TRANSLATIONS = 'Empirecraft-Translations-uk-ru-1.1.0.zip'

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def checksum(path):
    path.with_suffix(path.suffix + '.sha256').write_text(sha(path.read_bytes()) + '  ' + path.name + '\n')

def pin_translation_pack(pack, adm_path=None):
    """Register our language pack while preserving every unrelated ADM entry."""
    adm_path = adm_path or REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json'
    spec = importlib.util.spec_from_file_location('city_release_adm', REPO / 'ship-cannons/release.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    data = json.loads(adm_path.read_text())
    assert helper.integrity(data) == data['integrityHash'], 'Existing ADM integrity does not match'
    previous = [dict(row) for row in data['resourcePackIds']]
    own = lambda row: row['filename'].startswith('Empirecraft-Translations-uk-ru-')
    desired = dict(filename=TRANSLATIONS, sha256=sha(pack.read_bytes()))
    refreshed = []
    for row in previous:
        if own(row):
            if desired not in refreshed:
                refreshed.append(desired)
        else:
            refreshed.append(row)
    if desired not in refreshed:
        refreshed.append(desired)
    assert [row for row in previous if not own(row)] == [row for row in refreshed if not own(row)]
    if refreshed != previous:
        data['resourcePackIds'] = refreshed
        data['lastUpdated'] += 1
        data['integrityHash'] = helper.integrity(data)
        write(adm_path, data)

def refresh_translations():
    for script in ('scan.py', 'review.py', 'build.py'):
        subprocess.run([sys.executable, str(REPO / 'translations' / script)], check=True)
    pack = REPO / 'translations/dist' / TRANSLATIONS
    pin_translation_pack(pack)
    coverage = json.loads((REPO / 'translations/audit/coverage.json').read_text())
    assert coverage['missing'] == dict(uk_ua=0, ru_ru=0)
    path = REPO / 'validation' / (REPORT + '.json')
    report = json.loads(path.read_text())
    report['resource_pack'] = dict(file=TRANSLATIONS, sha256=sha(pack.read_bytes()),
        english_keys=coverage['english_keys'], namespaces=coverage['namespaces_with_english'], missing=coverage['missing'])
    write(path, report)

def verify(plugin, bridge):
    for path in (plugin, bridge):
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None, path
    with zipfile.ZipFile(plugin) as archive:
        assert 'version: ' + PLUGIN_VERSION + '\n' in archive.read('plugin.yml').decode()
        for name in ('MarketNotices', 'MarketFishing', 'MarketCivic', 'MarketAuctions', 'MarketDocuments', 'MarketOfficialExchange', 'MarketScreens', 'TownTravelGuard',
                     'TownMenu', 'TownMenuData', 'ArmyService', 'ArmyController', 'ArmyPolicy', 'ContractService', 'ContractMenus','EngagementController','EngagementTownyGuard','DiscordBot','DiscordNotifications','DiscordMessage','RegistrationOutbox','RegistrationNotices','ShipRegistrationService','ShipRegistrationController'):
            assert 'ua/empirecraft/feudal/' + name + '.class' in archive.namelist()
        for language in ('en', 'ru'):
            assert len(json.loads(archive.read('empirecraft-feudal/lang/' + language + '.json'))) > 2700
        assert 'ua/empirecraft/feudal/CommandPreferences.class' in archive.namelist()
        assert 'org/h2/Driver.class' in archive.namelist()
        assert not any(name.endswith('Tests.class') for name in archive.namelist())
    with zipfile.ZipFile(bridge) as archive:
        assert 'version="' + bridge_version() + '"' in archive.read('META-INF/mods.toml').decode()
        for name in ('MarketScreen', 'MapMemoryScreen'):
            assert 'ua/empirecraft/townymap/client/' + name + '.class' in archive.namelist()
        for name in ('ClaimKnowledge', 'ClaimIndex', 'KnowledgeStore', 'MapHistoryNetwork'):
            assert 'ua/empirecraft/townymap/' + name + '.class' in archive.namelist()
        for language in ('en', 'ru'):
            assert len(json.loads(archive.read('assets/empirecraft_townymap/locales/' + language + '.json'))) > 100
        for name in ('InteractiveChatMixin', 'InteractiveTabMixin'):
            assert 'ua/empirecraft/townymap/client/mixin/' + name + '.class' in archive.namelist()
        assert 'MixinConfigs: empirecraft-client.mixins.json' in archive.read('META-INF/MANIFEST.MF').decode()
        assert b'm_6375_' in archive.read('ua/empirecraft/townymap/client/mixin/InteractiveChatMixin.class')
        assert 'empirecraft-contact.mixins.json' in archive.namelist()
        assert 'ua/empirecraft/townymap/contact/ContactMail.class' in archive.namelist()
        assert not any('ContactSmoke' in name for name in archive.namelist())
        # Mixin reserves the entire configured package. Ordinary screens or the
        # mailbox interface there fail class loading before their first use.
        for config in ('empirecraft-client.mixins.json','empirecraft-contact.mixins.json'):
            data=json.loads(archive.read(config));prefix=data['package'].replace('.','/')+'/'
            declared={prefix+name.replace('.','/')+'.class' for side in ('mixins','client','server') for name in data.get(side,[])}
            assert {name for name in archive.namelist() if name.startswith(prefix) and name.endswith('.class')}==declared, 'Ordinary classes in a reserved Mixin package'

def record_tests(plugin, bridge, reports, backend_log, native_contact, chat_log=None):
    xml_files = sorted(reports.glob('TEST-*.xml'))
    assert xml_files, 'Native JUnit reports are required'
    tests = 0
    for path in xml_files:
        suite = ET.parse(path).getroot()
        assert int(suite.get('failures', '0')) == int(suite.get('errors', '0')) == 0, path
        assert int(suite.get('skipped', '0')) == 0, path
        tests += int(suite.get('tests', '0'))
    print('PASS native JUnit:', tests, 'scenarios across', len(xml_files), 'suites')
    log = backend_log.read_text()
    assert 'Extended market scenarios: 36' in log and 'Готово:' in log and 'Exception in thread' not in log
    path = REPO / ('validation/' + REPORT + '.json')
    report = dict(plugin_version=PLUGIN_VERSION,bridge_version=bridge_version(),townyrecruits_version=RECRUITS_ROW['version'],
        previous_validation='city-markets-1.17.0.md',graphical_client='not_run',production_world='not_run',backend={},map_memory={})
    assert 'Runtime compatibility scenarios: 4; installed policy comparisons: 3072' in log
    contract_checks = int(re.search(r'PASS ContractTests: (\d+)', log)[1])
    assert 'Contact fair mail scenarios: 9' in log
    assert native_contact, '--native-contact is required to record fair delivery validation'
    native=json.loads(native_contact.read_text())
    assert native['bridge_sha256']==sha(bridge.read_bytes()) and len(native['phases'])==2 and all('CONTACT_FAIR_NATIVE_PASS' in line for line in native['phases'])
    native['phases']=[re.search(r'CONTACT_FAIR_NATIVE_PASS phase=\d+ assertions=\d+',line)[0] for line in native['phases']]
    report['contact_mail']=dict(status='passed',backend_scenarios=9,native=native)
    report['backend'].update(suites=32, scenarios=len(re.findall(r'^✓ ', log, re.M)), contract_checks=contract_checks,
        installed_policy_comparisons=3072, plugin_sha256=sha(plugin.read_bytes()), status='passed')
    report['backend'].update(command_language=int(re.search(r'Command locale scenarios: (\d+)', log)[1]), localization=int(re.search(r'Localization scenarios: (\d+)', log)[1]))
    if chat_log:
        chat = chat_log.read_text()
        assert 'native font measurement, padding and xEnd hitbox code preserved exactly' in chat
        assert 'PASS exact upstream class allowlist' in chat
        report['chatplus'] = dict(status='passed', native_width_and_hitbox_preserved=True, routing_and_privacy='passed',
            chatplus_sha256=sha((REPO / 'mods' / CHATPLUS).read_bytes()), plugin_sha256=sha((REPO / 'server/plugins' / CHAT_PLUGIN).read_bytes()))
    map_tests = sum(int(ET.parse(path).getroot().get('tests', '0')) for path in xml_files if path.stem.endswith(('ClaimKnowledgeTest', 'KnowledgeStoreTest')))
    assert map_tests >= 24, 'Personal claim history regression suites must run'
    report['map_memory'].update(status='passed', scenarios=map_tests, persistence='world-scoped per-player UUID files', discovery='occupied Minecraft chunk only')
    report['native_client'] = dict(status='passed', scenarios=tests, suites=len(xml_files),
        bridge_sha256=sha(bridge.read_bytes()), forge_compile_and_reobfuscation='passed',
        workflow='City markets and native client',
        build_url=(os.environ.get('GITHUB_SERVER_URL', 'https://github.com') + '/' + os.environ.get('GITHUB_REPOSITORY', '2sparta/Empirecraft') + '/actions/runs/' + os.environ['GITHUB_RUN_ID']) if os.environ.get('GITHUB_RUN_ID') else None)
    write(path, report)

def refresh_inventory():
    lock_path = REPO / 'modpack-lock.json'
    lock = json.loads(lock_path.read_text())
    row = next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')
    row['pin_reason'] = 'Identical client/server '+bridge_version()+': personal visited-chunk history, localized native forms, Contact fair postcards, durable mailbox receipts and ChatPlus interactive components.'
    write(lock_path, lock)
    subprocess.run([sys.executable, str(REPO / 'validation/reconcile_modpack.py'), '--apply'], check=True)
    for folder, target in [('server/mods', 'seasonal-calendar/installed-mods.json'),
                           ('mods', 'forging-balance/installed-client-mods.json')]:
        rows = []
        for jar in sorted((REPO / folder).glob('*.jar')):
            raw = jar.read_bytes()
            rows.append(dict(name=jar.name, path=folder + '/' + jar.name, size=len(raw),
                sha=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()))
        write(REPO / target, rows)
    path = REPO / 'seasonal-calendar/audit/coverage.json'
    coverage = json.loads(path.read_text())
    current = {jar.name: sha(jar.read_bytes()) for jar in sorted((REPO / 'server/mods').glob('*.jar'))}
    coverage['jar_sha256'] = {name:current[name] for name in coverage['jar_sha256'] if name in current}
    coverage['jar_sha256'].update(current)
    coverage['jar_count'] = len(coverage['jar_sha256'])
    write(path, coverage)
    # The native-content pass excludes no bridge resources. Pin the complete
    # reviewed input and its code so later resource rebuilds retain this adapter.
    path=REPO/'native-content/inputs.json'
    if path.exists():
        inputs=json.loads(path.read_text());inputs={name:value for name,value in inputs.items() if not name.startswith(('mods/empirecraft-towny-journeymap-','server/mods/empirecraft-towny-journeymap-'))}
        for side in ('mods','server/mods'):inputs[side+'/'+BRIDGE]=sha((REPO/side/BRIDGE).read_bytes())
        write(path,inputs)
        path=REPO/'native-content/retained-code.json';pins=json.loads(path.read_text());pins={name:value for name,value in pins.items() if not name.startswith('empirecraft-towny-journeymap-')}
        with zipfile.ZipFile(REPO/'mods'/BRIDGE) as archive:pins[BRIDGE]={name:sha(archive.read(name)) for name in archive.namelist() if name.endswith('.class')}
        write(path,pins)

def install(plugin, bridge):
    for folder, source, name, pattern in [('server/plugins', plugin, PLUGIN, 'empirecraft-feudal-*.jar'),
        ('mods', bridge, BRIDGE, 'empirecraft-towny-journeymap-*.jar'),
        ('server/mods', bridge, BRIDGE, 'empirecraft-towny-journeymap-*.jar')]:
        target = REPO / folder / name
        target.parent.mkdir(parents=True, exist_ok=True)
        for old in target.parent.glob(pattern):
            if old.name != name:
                old.unlink()
                old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        for old in target.parent.glob(pattern + '.sha256'):
            if old.name != name + '.sha256':
                old.unlink()
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        checksum(target)
    refresh_inventory()
    for checker in ('reconcile_modpack.py', 'check_installed_releases.py'):
        subprocess.run([sys.executable, str(REPO / 'validation' / checker)], check=True)
    refresh_translations()
    subprocess.run([sys.executable, str(REPO / 'validation/reconcile_modpack.py')], check=True)

def bundle(destination, plugin, bridge):
    assert (REPO / 'mods' / RECRUITS).read_bytes() == (REPO / 'server/mods' / RECRUITS).read_bytes()
    # A packaged ADM/lock must describe the exact included binary.
    lock = json.loads((REPO / 'modpack-lock.json').read_text())
    row = next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')
    assert row['sha256'] == sha(bridge.read_bytes()) and row['version'] == bridge_version()
    chat_row = next(row for row in lock['mods'] if row['id'] == 'chatplus')
    assert chat_row['file'] == CHATPLUS and chat_row['sha256'] == sha((REPO / 'mods' / CHATPLUS).read_bytes())
    files = {}
    # The bundled ADM requires every installed client mod it declares. Carry the
    # optional death-screen overlay when producing an update from this checkout.
    death = next((r for r in lock['mods'] if r['id'] == 'epicdeathscreen'), None)
    if death:
        path = REPO / 'mods' / death['file']
        assert death['sides'] == ['client'] and sha(path.read_bytes()) == death['sha256']
        files['client/mods/' + path.name] = path.read_bytes()
        files['client/mods/' + path.name + '.sha256'] = path.with_suffix('.jar.sha256').read_bytes()
        files['EPIC-DEATH-SCREEN.md'] = (REPO / 'death-screen/README.md').read_bytes()
    for source in json.loads((REPO/'starcatcher-upgrade/sources.json').read_text()):
        name=source['file']
        for side,folder in [('client','mods'),('server','server/mods')]:
            path=REPO/folder/name
            files[side+'/mods/'+name]=path.read_bytes()
            files[side+'/mods/'+name+'.sha256']=(sha(path.read_bytes())+'  '+name+'\n').encode()
    dimension_pack=REPO/'server/world/datapacks/empirecraft-multiverse.zip'
    files['server/world/datapacks/'+dimension_pack.name]=dimension_pack.read_bytes()
    files['server/world/datapacks/'+dimension_pack.name+'.sha256']=(sha(dimension_pack.read_bytes())+'  '+dimension_pack.name+'\n').encode()
    for side,folder in [('client','kubejs'),('server','server/kubejs')]:
        for material in ['wood_furniture','wood_container','brass_machine']:
            relative='data/empirecraft_mass/tags/blocks/'+material+'.json'
            files[side+'/kubejs/'+relative]=(REPO/folder/relative).read_bytes()
    civic=REPO/'server/mods/empirecraft-civic-events-1.0.0.jar'
    files['server/mods/'+civic.name]=civic.read_bytes()
    files['server/mods/'+civic.name+'.sha256']=(sha(civic.read_bytes())+'  '+civic.name+'\n').encode()
    files['CIVIC-EVENTS.md']=(REPO/'feudal/CIVIC-EVENTS.md').read_bytes()
    files['validation/civic-events-1.0.0.json']=(REPO/'validation/civic-events-1.0.0.json').read_bytes()
    language_pack = REPO / 'translations/dist' / TRANSLATIONS
    language_bytes = language_pack.read_bytes()
    adm = json.loads((REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
    assert dict(filename=TRANSLATIONS, sha256=sha(language_bytes)) in adm['resourcePackIds']
    files['client/resourcepacks/' + TRANSLATIONS] = language_bytes
    files['client/resourcepacks/' + TRANSLATIONS + '.sha256'] = (sha(language_bytes) + '  ' + TRANSLATIONS + '\n').encode()
    files['RESOURCEPACK-INSTALL.md'] = (REPO / 'translations/README.md').read_bytes()
    for side in ('client', 'server'):
        for name, raw in ((BRIDGE, bridge.read_bytes()), (RECRUITS, (REPO / 'mods' / RECRUITS).read_bytes()), ('empirecraft-engagement-1.1.0.jar',(REPO/'mods/empirecraft-engagement-1.1.0.jar').read_bytes())):
            files[side + '/mods/' + name] = raw
            files[side + '/mods/' + name + '.sha256'] = (sha(raw) + '  ' + name + '\n').encode()
    for folder, name in [('client/mods/', CHATPLUS), ('server/plugins/', CHAT_PLUGIN)]:
        path = REPO / ('mods' if folder.startswith('client') else 'server/plugins') / name
        files[folder + name] = path.read_bytes()
        files[folder + name + '.sha256'] = (sha(path.read_bytes()) + '  ' + name + '\n').encode()
    files['LOCALIZATION.md'] = (REPO / 'feudal/LOCALIZATION.md').read_bytes()
    files['ALLIED-COMMAND-DISCORD.md'] = (REPO / 'feudal/ALLIED-COMMAND-DISCORD.md').read_bytes()
    files['server/discord-bot.env.example'] = (REPO / 'server/discord-bot.env.example').read_bytes()
    files['MAP-HISTORY.md'] = (REPO / 'bridge/MAP-HISTORY.md').read_bytes()
    files['NAVAL-COMBAT.md'] = (REPO / 'engagement/README.md').read_bytes()
    # Keep the exact patch sources, upstream ChatPlus source and license with the update.
    sources = {}
    for relative in ('feudal/src', 'engagement/src','engagement/tests','engagement/tools','bridge/src', 'chat-channels/src', 'chat-channels/patch',
                     'chat-channels/tests', 'chat-channels/font', 'chat-channels/frozen-assets', 'bridge/gradle',
                     'translations/pack', 'translations/licenses', 'civic-events/src', 'civic-events/tests', 'starcatcher-upgrade', 'bridge/native-inputs', 'multiverse-compat', 'death-screen'):
        for path in sorted((REPO / relative).rglob('*')):
            if path.is_file() and not {'__pycache__', 'build', 'dist'}.intersection(path.relative_to(REPO).parts):
                sources[path.relative_to(REPO).as_posix()] = path.read_bytes()
    for relative in ('feudal/build.py', 'feudal/release.py', 'feudal/city_release.py', 'feudal/LOCALIZATION.md',
                     'bridge/README.md', 'bridge/MAP-HISTORY.md', 'bridge/build.gradle', 'bridge/settings.gradle', 'bridge/gradle.properties', 'bridge/gradlew',
                     'chat-channels/build.py', 'chat-channels/assets.py', 'chat-channels/test.py',
                     'chat-channels/README.md', 'chat-channels/LICENSE-ChatPlus.txt',
                     'chat-channels/ChatPlus-2.8.1-upstream-source.zip', 'validation/check_localization.py',
                     'translations/build.py', 'translations/scan.py', 'translations/review.py',
                     'translations/README.md', 'translations/CREDITS.md', 'ship-cannons/release.py',
                     'engagement/build.py','engagement/test.py','engagement/smoke.py','engagement/README.md',
                     'feudal/ALLIED-COMMAND-DISCORD.md','server/discord-bot.env.example','feudal/CIVIC-EVENTS.md',
                     'civic-events/build.py','civic-events/smoke.py','civic-events/release.py','bridge/overlay.py'):
        sources[relative] = (REPO / relative).read_bytes()
    source_zip = io.BytesIO()
    with zipfile.ZipFile(source_zip, 'w') as archive:
        for name, raw in sorted(sources.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    files['Empirecraft-localization-sources.zip'] = source_zip.getvalue()
    files['LICENSE-ChatPlus.txt'] = (REPO / 'chat-channels/LICENSE-ChatPlus.txt').read_bytes()

    files['server/plugins/' + PLUGIN] = plugin.read_bytes()
    files['server/plugins/' + PLUGIN + '.sha256'] = (sha(plugin.read_bytes()) + '  ' + PLUGIN + '\n').encode()
    for name in ('CITY-MARKETS.md', 'INSTALL-CITY-MARKETS.md', 'CONTACT-FAIRS.md'):
        files[name] = (REPO / 'feudal' / name).read_bytes()
    for name in ('config.yml', 'townyperms.yml'):
        files['reference-configs/Towny/settings/' + name] = (REPO / 'server/plugins/Towny/settings' / name).read_bytes()
    files['reference-configs/admanticheat_id_list.json'] = (REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    for name in (REPORT+'.md', REPORT+'.json', 'installed-releases-2026-11-01.json'):
        files['validation/' + name] = (REPO / 'validation' / name).read_bytes()
    files['metadata/modpack-lock.json'] = (REPO / 'modpack-lock.json').read_bytes()
    files['apply_towny_settings.py'] = (REPO / 'feudal/apply_towny_settings.py').read_bytes()
    files['REMOVE-OLD-JARS.txt'] = ('Remove superseded empirecraft-feudal-*.jar from server/plugins and their checksums.\n'
        'Remove superseded empirecraft-towny-journeymap-*.jar and townyrecruits-*.jar from both mods directories.\n'
        'Remove Starcatcher 2.3.19 and its checksums from both mods directories; use included Starcatcher 3.3.10 and all three included required libraries.\n'
        'Remove superseded chatplus-forge-*.jar from client mods and empirecraft-chat-*.jar from server/plugins, with checksums.\n'
        'Keep exactly the versions present in this bundle. Preserve worlds, playerdata and the Feudal database.\n').encode()
    files['SHA256SUMS.txt'] = ''.join(sha(raw) + '  ' + name + '\n' for name, raw in sorted(files.items())).encode()
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / ('Empirecraft-City-Markets-' + PLUGIN_VERSION + '-bundle.zip')
    with zipfile.ZipFile(path, 'w') as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
    checksum(path)
    print(path, '\nSHA-256:', sha(path.read_bytes()))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plugin-jar', type=Path, default=REPO / 'server/plugins' / PLUGIN)
    parser.add_argument('--bridge-jar', type=Path, default=REPO / 'bridge/build/libs' / BRIDGE)
    parser.add_argument('--install', action='store_true', help='Install into this checkout and refresh inventories')
    parser.add_argument('--test-reports', type=Path, help='Record actual native JUnit XML results')
    parser.add_argument('--backend-log', type=Path)
    parser.add_argument('--native-contact', type=Path, help='Actual two-boot Contact result JSON')
    parser.add_argument('--chat-log', type=Path)
    parser.add_argument('--output-dir', type=Path, default=REPO / 'work/city-markets-release')
    args = parser.parse_args()
    verify(args.plugin_jar, args.bridge_jar)
    if args.test_reports:
        assert args.backend_log, '--backend-log is required with --test-reports'
        record_tests(args.plugin_jar, args.bridge_jar, args.test_reports, args.backend_log, args.native_contact, args.chat_log)
    subprocess.run([sys.executable, str(REPO / 'validation/check_feudal_runtime.py'), '--jar', str(args.plugin_jar)], check=True)
    if args.install:
        install(args.plugin_jar, args.bridge_jar)
    bundle(args.output_dir, args.plugin_jar, args.bridge_jar)

if __name__ == '__main__':
    main()
