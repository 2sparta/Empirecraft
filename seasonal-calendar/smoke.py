#!/usr/bin/env python3
"""Verify calendar commands and persistent UTC offsets across two disposable Mohist boots."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
from build import ROOT, REPO, NAME, classpath


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java'))
    args = parser.parse_args()
    source = REPO/'server'
    spec = json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name, digest in spec['sha256'].items():
        assert hashlib.sha256((source/name).read_bytes()).hexdigest() == digest, name
    logs = ROOT/'build/native'; logs.mkdir(parents=True, exist_ok=True)
    (logs/'result.json').unlink(missing_ok=True)
    classes = ROOT/'build/smoke-classes'
    shutil.rmtree(classes, ignore_errors=True); classes.mkdir(parents=True)
    compiler = str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
    cp = os.pathsep.join(map(str, [ROOT/'build/classes', *classpath([source/'libraries', source/'mods', REPO/'mods'])]))
    subprocess.run([compiler, '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                    *map(str, (ROOT/'src/smoke/java').rglob('*.java'))], check=True)
    fixture = ROOT/'build/empirecraft-calendar-smoke.jar'
    with zipfile.ZipFile(fixture, 'w') as jar:
        for path in classes.rglob('*.class'): jar.write(path, path.relative_to(classes).as_posix())
        jar.writestr('META-INF/mods.toml', 'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_calendar_smoke"\nversion="1"\ndisplayName="Disposable calendar tests"\n')
    jar = REPO/'server/mods'/NAME
    results = []
    scratch = REPO/'work'; scratch.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='calendar-', dir=scratch) as temp:
        root = Path(temp)
        (root/'libraries').symlink_to(source/'libraries', target_is_directory=True)
        (root/'mods').mkdir(); (root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        for name in ['SereneSeasons-forge-1.20.1-9.1.0.3.jar', 'GlitchCore-forge-1.20.1-0.0.1.1 (1).jar',
                     'empirecraft-world-rules-1.0.2.jar', 'combatlog-2.8.4-forge+1.20.1.jar', 'empirecraft-no-end-1.0.0.jar']:
            shutil.copyfile(source/'mods'/name, root/'mods'/name)
        for path in [jar, fixture]: shutil.copyfile(path, root/'mods'/path.name)
        shutil.copytree(source/'config/sereneseasons', root/'config/sereneseasons')
        (root/'eula.txt').write_text('eula=true\n')
        layers = dict(biome='minecraft:plains', layers=[dict(block='minecraft:bedrock', height=1),
                      dict(block='minecraft:dirt', height=2), dict(block='minecraft:grass_block', height=1)],
                      features=False, lakes=False, structure_overrides=[])
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+
            '\nserver-ip=127.0.0.1\nserver-port=25597\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot = [source/p for p in spec['module_path']]; legacy = [source/p for p in spec['legacy_classpath']]
        for phase in [1, 2]:
            launch = ['-Xmx2G', '-XX:ActiveProcessorCount=2', '-Dempirecraft.calendar.testPhase='+str(phase),
                '--module-path', os.pathsep.join(map(str, boot)), '--add-modules', 'ALL-MODULE-PATH',
                '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler',
                '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler',
                '--add-exports', 'java.base/sun.security.util=cpw.mods.securejarhandler',
                '--add-exports', 'jdk.naming.dns/com.sun.jndi.dns=java.naming',
                '-DignoreList='+','.join(p.name for p in boot), '-DlibraryDirectory=libraries',
                '-DlegacyClassPath='+os.pathsep.join(map(str, legacy)), 'cpw.mods.bootstraplauncher.BootstrapLauncher',
                '--launchTarget', 'forgeserver', '--fml.forgeVersion', '47.4.23', '--fml.mcVersion', '1.20.1',
                '--fml.forgeGroup', 'net.minecraftforge', '--fml.mcpVersion', '20230612.114412', 'nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(value)+'"' for value in launch))
            log = logs/f'phase-{phase}.log'
            with log.open('w') as output:
                result = subprocess.run([args.java, '@launch.args'], cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=240)
            text = log.read_text()
            lines = re.findall(r'CALENDAR_NATIVE_(?:PASS[^\n]*|FAIL[^\n]*)', text)
            print('\n'.join(lines), flush=True)
            if result.returncode or f'CALENDAR_NATIVE_PASS phase={phase}' not in text or 'CALENDAR_NATIVE_FAIL' in text:
                print(text[-18000:]); raise SystemExit('Native calendar check failed: '+str(log))
            results.extend(lines)
    report = dict(runtime=spec['mohist'], calendar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                  phases=results, actual_restarts=1, graphical_client_tested=False, production_world_tested=False)
    (logs/'result.json').write_text(json.dumps(report, indent=2)+'\n')
    print('CALENDAR_NATIVE_RESTART_PASS')


if __name__ == '__main__': main()
