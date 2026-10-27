#!/usr/bin/env python3
"""Execute rules and native Mixin transformations on both physical sides."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
from build import ROOT, REPO, NAME, classpath, compiler

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--classpath-dir', action='append', type=Path); args = parser.parse_args()
    jars = classpath(args.classpath_dir or [REPO/'server/libraries', REPO/'server/mods', REPO/'mods'])
    tests = ROOT/'build/test-classes'
    if tests.exists(): shutil.rmtree(tests)
    tests.mkdir(parents=True)
    cp = os.pathsep.join(map(str, [ROOT/'build/classes', ROOT/'src/main/resources', *jars]))
    subprocess.run([*compiler(), '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(tests), *map(str, (ROOT/'src/test/java').rglob('*.java'))], check=True)
    services = tests/'META-INF/services'; services.mkdir(parents=True, exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    runtime = str(tests)+os.pathsep+cp
    subprocess.run(['java', '-ea', '-cp', runtime, 'guardtest.ArmorRulesTest'], check=True)
    knights = REPO/'server/mods/epic-knights-all-in-one-1_20_1-forge-empirecraft.1.jar'
    assert hashlib.sha256(knights.read_bytes()).hexdigest() == 'b182dbca3df68e541444f40e4b2822837765a84c68758b61badab0f6d6d1e410'
    subprocess.run(['java', '-ea', '-cp', runtime, 'guardtest.EquipmentRulesTest', str(knights)], check=True)
    for side in ['server', 'client']:
        subprocess.run(['java', '-ea', '-cp', runtime, 'guardtest.ForgingMixinTest', side], check=True)
    old = ROOT/'tests/fixtures/empirecraft-forging-1.0.0.jar'
    assert hashlib.sha256(old.read_bytes()).hexdigest() == '0c5806671023a4499309a1dc7d928e93c4504fc5da892422a074ff3122c04eb4'
    old_runtime = str(tests)+os.pathsep+str(old)+os.pathsep+cp
    for target, forbidden in [('net.stirdrem.overgeared.recipe.ForgingRecipe', 'ArmorBlueprintMixin$1'), ('net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket', 'ArmorCounterMixin')]:
        failure = subprocess.run(['java', '-ea', '-cp', old_runtime, 'guardtest.ForgingMixinTest', 'server', target], capture_output=True, text=True)
        assert failure.returncode and 'Illegal runtime Mixin reference' in failure.stderr and forbidden in failure.stderr, failure.stdout+failure.stderr
    failure = subprocess.run(['java', '-ea', '-cp', old_runtime, 'guardtest.ForgingMixinTest', 'server', 'net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket', 'linkage'], capture_output=True, text=True)
    assert failure.returncode and 'NoClassDefFoundError' in failure.stderr and 'ArmorCounterMixin' in failure.stderr, failure.stdout+failure.stderr
    print('PASS original 1.0.0 reproduces the reported NoClassDefFoundError and both forbidden references; current addon passes')
    client, server = REPO/'mods'/NAME, REPO/'server/mods'/NAME
    assert client.read_bytes() == server.read_bytes()
    with zipfile.ZipFile(client) as jar:
        assert not any('/mixin/' in name and '$' in name for name in jar.namelist()), 'Compiler-generated helper in forbidden Mixin package'
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == 15
        for language in ['en_us', 'uk_ua', 'ru_ru']:
            strings = json.loads(jar.read('assets/empirecraft_forging/lang/'+language+'.json'))
            assert all('calendar.empirecraft.month.'+str(month) in strings for month in range(1, 13))
            assert '%1$s' in strings['calendar.empirecraft.date'] and '%2$s' in strings['calendar.empirecraft.date']
            assert 'calendar.empirecraft.duration' in strings
            assert 'tooltype.overgeared.helmet' in strings and 'tooltype.overgeared.boots' in strings
            assert 'message.empirecraft.arquebus.no_powder' in strings
    print('PASS complete automatically loaded English, Ukrainian and Russian calendar/blueprint/ammunition resources')
    lock = json.loads((REPO/'modpack-lock.json').read_text())
    calendar = next(row for row in lock['mods'] if row['id'] == 'empirecraft_calendar')
    raw = (REPO/'mods'/calendar['file']).read_bytes()
    git_sha = hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    for manifest in [ROOT/'installed-client-mods.json', REPO/'seasonal-calendar/installed-mods.json']:
        row = next(row for row in json.loads(manifest.read_text()) if row['name'] == calendar['file'])
        assert row['sha'] == git_sha and row['size'] == len(raw), str(manifest)
    with zipfile.ZipFile(REPO/'mods'/calendar['file']) as jar:
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == 15
        for language in ['en_us', 'uk_ua', 'ru_ru']:
            strings = json.loads(jar.read('assets/empirecraft_calendar/lang/'+language+'.json'))
            assert len([key for key in strings if key.startswith('calendar.empirecraft.month.')]) == 12
            assert 'calendar.empirecraft.date' in strings and 'calendar.empirecraft.duration' in strings
        # Regression: auditing the calendar's own output used to erase these
        # compatibility tags on alternating builds. The deployed JAR must keep them.
        for season in ['spring', 'summer', 'autumn', 'winter']:
            for kind in ['blocks', 'items']:
                tag = json.loads(jar.read('data/sereneseasons/tags/'+kind+'/'+season+'_crops.json'))
                assert tag['replace'] is False and tag['values'], (season, kind)
    print('PASS native calendar JAR pack metadata and complete en_us/uk_ua/ru_ru resources')

if __name__ == '__main__': main()

