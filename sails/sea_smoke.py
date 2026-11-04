#!/usr/bin/env python3
"""Exercise real waves, buoy draft, cabin breach/repair and sound events in a disposable Mohist world."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java'))
    args = parser.parse_args()
    repo = build.REPO
    source = repo / 'server'
    spec = json.loads((build.ROOT / 'smoke-runtime.json').read_text())
    for relative, expected in spec['sha256'].items():
        assert hashlib.sha256((source / relative).read_bytes()).hexdigest() == expected, relative
    scratch = repo / 'work'
    scratch.mkdir(exist_ok=True)
    logs = build.ROOT / 'build/sea-world-logs'
    logs.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sails-sea-', dir=scratch) as temp:
        root = Path(temp)
        (root / 'libraries').symlink_to(source / 'libraries', target_is_directory=True)
        (root / 'mods').mkdir()
        (root / 'mohist-config').mkdir()
        (root / 'mohist-config/mohist.yml').write_text('config-version: 1\n')
        for pattern in ['architectury-9.2.14-forge.jar', 'kotlinforforge-4.12.0-all*.jar',
                        'valkyrienskies-120-2.4.11-empirecraft.1.jar',
                        'vs_sails-1.20.1-' + build.VERSION + '-forge.jar', 'empirecraft-no-end-1.0.0.jar']:
            path, = (source / 'mods').glob(pattern)
            shutil.copyfile(path, root / 'mods' / path.name)
        fixture = build.ROOT / 'build/empirecraft-sea-smoketest.jar'
        shutil.copyfile(fixture, root / 'mods' / fixture.name)
        (root / 'config/vs_sails').mkdir(parents=True)
        (root / 'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
        shutil.copytree(source / 'defaultconfigs', root / 'defaultconfigs')
        (root / 'eula.txt').write_text('eula=true\n')
        settings = dict(biome='minecraft:plains', layers=[dict(block='minecraft:bedrock', height=1),
                        dict(block='minecraft:stone', height=2), dict(block='minecraft:water', height=132)],
                        features=False, lakes=False, structure_overrides=[])
        (root / 'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='
            + json.dumps(settings) + '\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nview-distance=2\n'
            'simulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot = [source / p for p in spec['module_path']]
        legacy = [source / p for p in spec['legacy_classpath']]
        launch = ['-Xmx2G', '-XX:ActiveProcessorCount=2', '--module-path', os.pathsep.join(map(str, boot)),
            '--add-modules', 'ALL-MODULE-PATH', '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler',
            '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler',
            '--add-exports', 'java.base/sun.security.util=cpw.mods.securejarhandler',
            '--add-exports', 'jdk.naming.dns/com.sun.jndi.dns=java.naming', '-DignoreList=' + ','.join(p.name for p in boot),
            '-DlibraryDirectory=libraries', '-DlegacyClassPath=' + os.pathsep.join(map(str, legacy)),
            'cpw.mods.bootstraplauncher.BootstrapLauncher', '--launchTarget', 'forgeserver', '--fml.forgeVersion', '47.4.23',
            '--fml.mcVersion', '1.20.1', '--fml.forgeGroup', 'net.minecraftforge', '--fml.mcpVersion', '20230612.114412', 'nogui']
        (root / 'launch.args').write_text('\n'.join('"' + str(x) + '"' for x in launch))
        log = logs / 'native-sea.log'
        with log.open('w') as output:
            result = subprocess.run([args.java, '@launch.args'], cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=240)
        text = log.read_text()
        shutil.copyfile(root / 'test/serverconfig/valkyrienskies/vs-core-server.toml', logs / 'generated-vs-core-server.toml')
        lines = [line for line in text.splitlines() if 'SEA_SMOKE_' in line]
        print('\n'.join(lines))
        if result.returncode or 'SEA_SMOKE_PASS' not in text or 'SEA_SMOKE_FAIL' in text or 'Exception in thread "Physics thread"' in text:
            print(text[-18000:])
            raise SystemExit('Native sea test failed: ' + str(log))
        (logs / 'result.json').write_text(json.dumps(dict(runtime=spec['mohist'], exit_code=result.returncode,
            result=lines, production_world_tested=False), indent=2) + '\n')
        print('SEA_NATIVE_WORLD_PASS')


if __name__ == '__main__':
    main()
