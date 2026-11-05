#!/usr/bin/env python3
"""Place real fences/walls with server and single-player datapacks in disposable Mohist worlds."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from repair_tags import ROOT, REPO

BUILD = ROOT/'build'


def compile_probe(javac, java):
    named = REPO/'seasonal-leaves/build/runtime-named.jar'
    mapping = REPO/'seasonal-leaves/build/named-srg.srg'
    assert named.is_file() and mapping.is_file(), 'Run python3 seasonal-leaves/build.py with JDK 17 first'
    classes = BUILD/'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    deps = sorted((REPO/'server/libraries').rglob('*.jar'))
    flags = ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [named, *deps])),
             '-d', str(classes), *map(str, (ROOT/'src/test/java').rglob('*.java'))]
    argfile = BUILD/'javac.args'
    argfile.write_text('\n'.join('"'+value.replace('\\', '\\\\').replace('"', '\\"')+'"' for value in flags))
    subprocess.run([javac, '@'+str(argfile)], check=True)
    source = BUILD/'probe-named.jar'
    with zipfile.ZipFile(source, 'w') as archive:
        for path in classes.rglob('*.class'):
            archive.write(path, path.relative_to(classes).as_posix())
    target = BUILD/'probe-srg.jar'
    target.unlink(missing_ok=True)
    subprocess.run([java, '-Xmx1G', '-cp', os.pathsep.join(map(str, [source, named, *deps])),
                    'net.md_5.specialsource.SpecialSource', '-i', str(source), '-o', str(target),
                    '-m', str(mapping), '-l', '--quiet'], check=True)
    with zipfile.ZipFile(target, 'a') as archive:
        archive.writestr('META-INF/mods.toml', 'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_connection_probe"\nversion="1"\n')
    return target


def run(profile, java, probe, runtime):
    source = REPO/'server'
    with tempfile.TemporaryDirectory(prefix='connections-', dir=REPO/'work') as temp:
        root = Path(temp)
        (root/'libraries').symlink_to(source/'libraries', target_is_directory=True)
        (root/'mods').mkdir()
        # WorldEdit does not affect these block connections and its eager state
        # expansion is a separate issue documented in the modpack health audit.
        for jar in (source/'mods').glob('*.jar'):
            if not jar.name.startswith('worldedit-'):
                shutil.copy2(jar, root/'mods'/jar.name)
        shutil.copy2(probe, root/'mods/connection-test-only.jar')
        for folder in ['config', 'defaultconfigs', 'kubejs']:
            base = REPO if folder == 'kubejs' and profile == 'client' else source
            shutil.copytree(base/folder, root/folder, ignore=shutil.ignore_patterns('logs', '*.log', 'cache'))
        (root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        (root/'eula.txt').write_text('eula=true\n')
        (root/'config/voicechat').mkdir(exist_ok=True)
        (root/'config/voicechat/voicechat-server.properties').write_text('bind_address=127.0.0.1\nport=24593\n')
        layers = dict(biome='minecraft:plains', layers=[dict(block='minecraft:bedrock', height=1),
                      dict(block='minecraft:dirt', height=2), dict(block='minecraft:grass_block', height=1)],
                      features=False, lakes=False, structure_overrides=[])
        (root/'server.properties').write_text('level-name=connections\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+
            '\nserver-ip=127.0.0.1\nserver-port=25593\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot = [source/name for name in runtime['module_path']]
        legacy = [source/name for name in runtime['legacy_classpath']]
        launch = ['-Xmx4G', '-XX:ActiveProcessorCount=2', '--module-path', os.pathsep.join(map(str, boot)),
                  '--add-modules', 'ALL-MODULE-PATH', '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler',
                  '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler', '--add-exports',
                  'java.base/sun.security.util=cpw.mods.securejarhandler', '--add-exports', 'jdk.naming.dns/com.sun.jndi.dns=java.naming',
                  '-DignoreList='+','.join(path.name for path in boot), '-DlibraryDirectory=libraries',
                  '-DlegacyClassPath='+os.pathsep.join(map(str, legacy)), 'cpw.mods.bootstraplauncher.BootstrapLauncher',
                  '--launchTarget', 'forgeserver', '--fml.forgeVersion', '47.4.23', '--fml.mcVersion', '1.20.1',
                  '--fml.forgeGroup', 'net.minecraftforge', '--fml.mcpVersion', '20230612.114412', 'nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(value)+'"' for value in launch))
        log = BUILD/(profile+'.log')
        with log.open('w') as output:
            result = subprocess.run([java, '@launch.args'], cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=300)
        report = root/'connections.json'
        assert report.exists(), 'Connection probe did not finish: '+str(log)
        data = json.loads(report.read_text())
        shutil.copy2(report, BUILD/(profile+'.json'))
        assert result.returncode == 0 and not data['failures'], (profile, result.returncode, data['failures'])
        assert not any(data['missing_required_blocks'].values()), data['missing_required_blocks']
        print('PASS', profile, 'profile:', data['checks'], 'checks;', len(data['registered_fences']), 'fences;', len(data['registered_walls']), 'available walls', flush=True)
        return {key: value for key, value in data.items() if key not in ['registered_fences', 'registered_walls']} | dict(
            fences=len(data['registered_fences']), walls=len(data['registered_walls']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--javac', default=shutil.which('javac'))
    args = parser.parse_args()
    BUILD.mkdir(exist_ok=True)
    (REPO/'work').mkdir(exist_ok=True)
    runtime = json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name, digest in runtime['sha256'].items():
        assert hashlib.sha256((REPO/'server'/name).read_bytes()).hexdigest() == digest, name
    probe = compile_probe(args.javac, args.java)
    results = {profile: run(profile, args.java, probe, runtime) for profile in ['server', 'client']}
    summary = dict(runtime='Mohist 71a84ff7 / Forge 47.4.23', profiles=results,
                   worldedit_loaded=False, bukkit_plugins_loaded=False, graphical_client_tested=False,
                   production_world_loaded=False)
    (BUILD/'result.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n')
    print('PASS actual placement, neighbor updates, gates, vanilla material rules and optional-reference regression')


if __name__ == '__main__':
    main()
