#!/usr/bin/env python3
"""Exercise the staged update on a disposable Mohist world."""
import argparse
import json
import os
import re
import shutil
import subprocess
import tomllib
import zipfile
from pathlib import Path

from build import REPO, ROOT, compile_classes, pack


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--overlay', type=Path, help='Staged JARs that replace installed mods by identity')
    parser.add_argument('--seasonal', action='store_true', help='Include wildlife, winds, seasonal map and provenance checks')
    args = parser.parse_args()
    target = args.runtime.resolve()
    target.mkdir(parents=True, exist_ok=False)
    source = REPO / 'server'
    libs = source / 'libraries'
    (target / 'libraries').symlink_to(libs, target_is_directory=True)
    (target / 'mods').mkdir()
    def identity(path):
        with zipfile.ZipFile(path) as jar:
            if 'META-INF/mods.toml' not in jar.namelist():
                return None
            mods=tomllib.loads(jar.read('META-INF/mods.toml').decode()).get('mods',[])
            return mods[0]['modId'] if mods else None

    staged = list(args.overlay.resolve().glob('*.jar')) if args.overlay else []
    replaced = {identity(jar) for jar in staged}
    for jar in (source / 'mods').glob('*.jar'):
        if not jar.name.startswith('antixray-') and identity(jar) not in replaced:
            (target / 'mods' / jar.name).symlink_to(jar)
    for jar in staged:
        (target / 'mods' / jar.name).symlink_to(jar)

    for name in ['config', 'defaultconfigs', 'kubejs']:
        shutil.copytree(source / name, target / name, ignore=shutil.ignore_patterns('logs', 'client_scripts'))
    towny = target / 'plugins/Towny/settings'
    towny.mkdir(parents=True)
    for jar in ['Towny-0.101.2.0.jar', 'Vault.jar']:
        shutil.copyfile(source / 'plugins' / jar, target / 'plugins' / jar)
    feudal, = (source / 'plugins').glob('empirecraft-feudal-*.jar')
    shutil.copyfile(feudal, target / 'plugins' / feudal.name)
    config = (source / 'plugins/Towny/settings/config.yml').read_text()
    (towny / 'config.yml').write_text(re.sub(r"(using_economy:\s*)['\"]?true['\"]?",r"\1'false'",config))
    shutil.copyfile(source / 'plugins/Towny/settings/townyperms.yml', towny / 'townyperms.yml')
    (target / 'mohist-config').mkdir()
    (target / 'mohist-config/mohist.yml').write_text('config-version: 1\n')
    (target / 'eula.txt').write_text('eula=true\n')
    settings = dict(biome='minecraft:plains', layers=[
        dict(block='minecraft:bedrock',height=1), dict(block='minecraft:dirt',height=2),
        dict(block='minecraft:grass_block',height=1)], features=False, lakes=False, structure_overrides=[])
    (target / 'server.properties').write_text(
        'level-name=fixture\nlevel-seed=4411\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+
        '\nserver-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n'
        'max-tick-time=120000\nspawn-protection=0\n')

    work = target / 'probe'
    work.mkdir()
    cp = [args.sdk / 'runtime-named.jar', *sorted(libs.rglob('*.jar')), *sorted((target / 'mods').glob('*.jar'))]
    sources = [ROOT / 'tests/GameplayProbe.java']
    if args.seasonal:
        sources.append(REPO / 'seasonal-world/tests/SeasonalProbe.java')
        tabletop, = (target/'mods').glob('blockandboard-*.jar')
        with zipfile.ZipFile(tabletop) as jar:
            core, = [name for name in jar.namelist() if name.endswith('.jar') and 'mctabletop-core' in name]
            path=work/'tabletop-core.jar'
            path.write_bytes(jar.read(core))
            cp.append(path)
    probe = compile_classes(work,args.sdk,libs,cp,sources)
    probe['META-INF/mods.toml'] = b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="gameplayprobe"\nversion="1"\n'
    pack(target / 'mods/gameplayprobe.jar', probe)

    spec = json.loads((REPO / 'sails/smoke-runtime.json').read_text())
    boot = [source / p for p in spec['module_path']]
    legacy = [source / p for p in spec['legacy_classpath']]
    launch = ['java','-Xmx6G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),
              '--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler',
              '--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
              '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler',
              '--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
              '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries',
              '-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
              'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver',
              '--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge',
              '--fml.mcpVersion','20230612.114412','nogui']
    log = target / 'native.log'
    with log.open('w') as stream:
        result = subprocess.run(launch, cwd=target, stdout=stream, stderr=subprocess.STDOUT, timeout=360)
    text = log.read_text(errors='replace')
    markers = re.findall(r'GAMEPLAY_NATIVE_(?:PASS|FAIL)[^\n\r]*',text)
    print('\n'.join(markers))
    assert result.returncode == 0 and any('PASS' in marker for marker in markers) and not any('FAIL' in marker for marker in markers), log


if __name__ == '__main__':
    main()
