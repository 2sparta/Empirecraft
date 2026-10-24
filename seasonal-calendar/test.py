#!/usr/bin/env python3
"""Test calendar mathematics, native APIs, exact installed Mixin targets and generated data."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile
from build import classpath, ROOT, REPO, NAME
from client_inputs import prepare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-dir', type=Path, action='append')
    args = parser.parse_args()
    jars = classpath(args.classpath_dir or [REPO/'server/libraries', REPO/'server/mods', REPO/'mods'])
    classes = ROOT/'build/classes'
    tests = ROOT/'build/test-classes'
    if tests.exists(): shutil.rmtree(tests)
    tests.mkdir(parents=True)
    cp = os.pathsep.join(map(str, [classes, ROOT/'src/main/resources', *jars]))
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home)/'bin/java') if java_home else 'java'
    compiler = [str(Path(java_home)/'bin/javac')] if java_home else ([shutil.which('javac')] if shutil.which('javac') else [java, 'com.sun.tools.javac.Main'])
    flags = ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(tests), *map(str, sorted((ROOT/'src/test/java').rglob('*.java')))]
    argfile = ROOT/'build/test-javac.args'
    argfile.write_text('\n'.join('"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"' for s in flags) + '\n')
    subprocess.run([*compiler, '@'+str(argfile)], check=True)
    services = tests/'META-INF/services'
    services.mkdir(parents=True, exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    fixtures = prepare()
    client = ROOT/'build/client-level-srg.jar'
    test_cp = str(tests)+os.pathsep+cp
    subprocess.run([java, '-cp', test_cp, 'guardtest.ClientClassRemapper', str(fixtures/'client.jar'), str(fixtures/'client.txt'), str(fixtures/'joined.tsrg'), str(client)], check=True)
    for test, arguments in [('CalendarPolicyTest', []), ('CalendarDataTest', []), ('CalendarMixinTest', ['SERVER']), ('CalendarMixinTest', ['CLIENT'])]:
        result = subprocess.run([java, '-ea', '-cp', str(client)+os.pathsep+test_cp, 'guardtest.'+test, *arguments], capture_output=True, text=True)
        (ROOT/'build'/(test+''.join(arguments)+'.log')).write_text(result.stdout+result.stderr)
        print(result.stdout, end='')
        if result.returncode:
            print(result.stderr)
            raise SystemExit(result.returncode)
    for name in ['seasons.toml', 'fertility.toml']:
        server = tomllib.loads((REPO/'server/config/sereneseasons'/name).read_text())
        client = tomllib.loads((REPO/'config/sereneseasons'/name).read_text())
        assert server == client
        if name == 'seasons.toml':
            assert server['time_settings']['day_duration'] == 57_600
            assert server['time_settings']['sub_season_duration'] == 30
            assert server['time_settings']['progress_season_while_offline']
        else: assert server['general']['out_of_season_crop_behavior'] == 1
    with zipfile.ZipFile(REPO/'mods'/NAME) as archive:
        assert archive.testzip() is None
        assert 'empirecraft-fauna.json' in archive.namelist()
        assert len([n for n in archive.namelist() if n.startswith('data/sereneseasons/tags/')]) == 8
        for locale in ['en_us', 'uk_ua']:
            lang = json.loads(archive.read(f'assets/empirecraft_calendar/lang/{locale}.json'))
            assert len([key for key in lang if key.startswith('calendar.empirecraft.month.')]) == 12
    for directory in [REPO/'mods', REPO/'server/mods']:
        assert sorted(p.name for p in directory.glob('empirecraft-calendar-*.jar')) == [NAME], 'Remove superseded calendar JARs'
    assert (REPO/'mods'/NAME).read_bytes() == (REPO/'server/mods'/NAME).read_bytes()
    print('PASS client/server configuration, 12 translated months, fertility tags and identical packaged JARs')


if __name__ == '__main__':
    main()
