#!/usr/bin/env python3
"""Exercise real whale damage, projectile discovery and Better Combat filters in disposable Mohist."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile
from pathlib import Path
import build

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default=shutil.which('java'));args=parser.parse_args()
    source=build.REPO/'server';spec=json.loads((build.REPO/'sails/smoke-runtime.json').read_text())
    for name,expected in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==expected,name
    logs=build.BUILD/'native-logs';logs.mkdir(exist_ok=True)
    scratch=build.REPO/'work';scratch.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='whale-hitboxes-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        for pattern in [build.NAME,'wonderoussea-1.0.4-empirecraft.2-forge-1.20.1.jar','geckolib-forge-1.20.1-4.8.4.jar','bettercombat-forge-1.9.0+1.20.1.jar',
                        'player-animation-lib-forge-1.0.2-rc1+1.20.jar','cloth-config-11.1.136-forge.jar','architectury-9.2.14-forge.jar','empirecraft-no-end-1.0.0.jar']:
            path,=(source/'mods').glob(pattern);shutil.copyfile(path,root/'mods'/path.name)
        fixture=build.BUILD/'empirecraft-whale-hitbox-test.jar';shutil.copyfile(fixture,root/'mods'/fixture.name)
        (root/'config/bettercombat').mkdir(parents=True)
        shutil.copyfile(source/'config/bettercombat/server.json5',root/'config/bettercombat/server.json5')
        shutil.copyfile(source/'config/accuratehitboxes.json',root/'config/accuratehitboxes.json')
        (root/'eula.txt').write_text('eula=true\n')
        layers={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+'\nserver-ip=127.0.0.1\nserver-port=25597\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        launch=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
                '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
                '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
                '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
                'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in launch))
        log=logs/'native-whale.log'
        with log.open('w') as out:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text();lines=[s for s in text.splitlines() if 'WHALE_HITBOX_NATIVE_' in s];print('\n'.join(lines))
        if result.returncode or 'WHALE_HITBOX_NATIVE_PASS' not in text or 'WHALE_HITBOX_NATIVE_FAIL' in text:
            print(text[-14000:]);raise SystemExit('Native whale hitbox test failed: '+str(log))
        (logs/'result.json').write_text(json.dumps({'runtime':spec['mohist'],'result':lines,'exit_code':result.returncode,'accuratehitboxes_sha256':build.sha((source/'mods'/build.NAME).read_bytes()),
                'production_model_filters_and_real_weapon_shapes_tested':True,'graphical_client_tested':False,'production_world_tested':False},indent=2)+'\n')
        print('WHALE_HITBOX_NATIVE_WORLD_PASS')
if __name__=='__main__':main()
