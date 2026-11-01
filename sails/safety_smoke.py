#!/usr/bin/env python3
"""Launch the destructive ship/Towny/cannon fixture in a fresh disposable Mohist 71a84ff7 world.

Uses checked, already installed runtime libraries. No production world or server process is used.
"""
import argparse,hashlib,json,os,shlex,shutil,subprocess,tempfile
from pathlib import Path
import build

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default=shutil.which('java'));a=parser.parse_args()
    repo=build.REPO;spec=json.loads((build.ROOT/'smoke-runtime.json').read_text());source=repo/'server'
    for relative,expected in spec['sha256'].items():
        assert hashlib.sha256((source/relative).read_bytes()).hexdigest()==expected,'Changed runtime input: '+relative
    scratch=repo/'work';scratch.mkdir(exist_ok=True);logs=build.ROOT/'build/safety-world-logs';logs.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sails-towny-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True)
        (root/'mods').mkdir();(root/'plugins/Towny/settings').mkdir(parents=True);(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        (root/'plugins/Towny/settings/config.yml').write_text("economy:\n  using_economy: 'false'\n")
        shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',root/'plugins/Towny-0.101.2.0.jar')
        for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','vs_sails-1.20.1-'+build.VERSION+'-forge.jar','supplementaries-1.20-3.1.44-forge-empirecraft-recipes.1.jar','moonlight-1.20-2.16.37-forge.jar','empirecraft-no-end-1.0.0.jar']:
            path,=list((source/'mods').glob(pattern));shutil.copyfile(path,root/'mods'/path.name)
        fixture=build.ROOT/'build/empirecraft-ship-safety-smoketest.jar';shutil.copyfile(fixture,root/'mods'/fixture.name)
        (root/'config/vs_sails').mkdir(parents=True)
        (root/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
        (root/'eula.txt').write_text('eula=true\n')
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        args=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
            '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
            '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
            '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
            'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
        log=logs/'mohist-towny-supplementaries.log'
        with log.open('w') as output:
            result=subprocess.run([a.java,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text();lines=[line for line in text.splitlines() if 'SHIP_SAFETY_SMOKE_' in line]
        print('\n'.join(lines))
        if result.returncode or 'SHIP_SAFETY_SMOKE_PASS' not in text or 'SHIP_SAFETY_SMOKE_FAIL' in text or 'Exception in thread "Physics thread"' in text:
            print(text[-16000:]);raise SystemExit('Ship protection native test failed: '+str(log))
        (logs/'result.json').write_text(json.dumps(dict(runtime=spec['mohist'],exit_code=result.returncode,result=lines,production_world_tested=False),indent=2)+'\n')
        print('SHIP_SAFETY_NATIVE_WORLD_PASS')
if __name__=='__main__':main()
