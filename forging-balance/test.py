#!/usr/bin/env python3
"""Execute rules and native Mixin transformations on both physical sides."""
import argparse, json, os, shutil, subprocess, zipfile
from pathlib import Path
from build import ROOT, REPO, NAME, classpath, compiler

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--classpath-dir', action='append', type=Path); args = parser.parse_args()
    jars = classpath(args.classpath_dir or [REPO/'server/libraries', REPO/'server/mods'])
    tests = ROOT/'build/test-classes'
    if tests.exists(): shutil.rmtree(tests)
    tests.mkdir(parents=True)
    cp = os.pathsep.join(map(str, [ROOT/'build/classes', ROOT/'src/main/resources', *jars]))
    subprocess.run([*compiler(), '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(tests), *map(str, (ROOT/'src/test/java').rglob('*.java'))], check=True)
    services = tests/'META-INF/services'; services.mkdir(parents=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    runtime = str(tests)+os.pathsep+cp
    subprocess.run(['java', '-ea', '-cp', runtime, 'guardtest.ArmorRulesTest'], check=True)
    for side in ['server', 'client']:
        subprocess.run(['java', '-ea', '-cp', runtime, 'guardtest.ForgingMixinTest', side], check=True)
    client, server = REPO/'mods'/NAME, REPO/'server/mods'/NAME
    assert client.read_bytes() == server.read_bytes()
    with zipfile.ZipFile(client) as jar:
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == 15
        for language in ['en_us', 'uk_ua', 'ru_ru']:
            strings = json.loads(jar.read('assets/empirecraft_forging/lang/'+language+'.json'))
            assert all('calendar.empirecraft.month.'+str(month) in strings for month in range(1, 13))
            assert '%1$s' in strings['calendar.empirecraft.date'] and '%2$s' in strings['calendar.empirecraft.date']
            assert 'calendar.empirecraft.duration' in strings
            assert 'tooltype.overgeared.helmet' in strings and 'tooltype.overgeared.boots' in strings
            assert 'message.empirecraft.arquebus.no_powder' in strings
    print('PASS complete automatically loaded English, Ukrainian and Russian calendar/blueprint/ammunition resources')

if __name__ == '__main__': main()
