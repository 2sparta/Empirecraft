#!/usr/bin/env python3
"""Compile and run city fleet/ship/Towny integration across two disposable Mohist boots."""
import argparse,hashlib,json,os,shlex,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(REPO/'sails'))
import build as sails

def run(args):subprocess.run(list(map(str,args)),check=True)
def compile_fixture():
    folder=ROOT/'build';folder.mkdir(exist_ok=True);classes=folder/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    flags=shlex.split((REPO/'sails/build/javac.args').read_text());deps=flags[flags.index('-cp')+1]
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac')
    cp=os.pathsep.join([str(REPO/'sails/build/classes'),deps])
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',classes,ROOT/'tests/FleetSmokeMod.java']
    (folder/'javac.args').write_text('\n'.join('"'+str(a).replace('\\','\\\\').replace('"','\\"')+'"' for a in args))
    run([javac,'@'+str(folder/'javac.args')])
    sails.jar(folder/'fixture-named.jar',{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    asm=list((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    remapper=os.pathsep.join(map(str,[REPO/'sails/build/tiny-remapper.jar',REPO/'sails/build/mapping-io.jar',*asm]))
    run([java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',folder/'fixture-named.jar',folder/'fixture-srg.jar',REPO/'sails/build/named-srg.tiny','named','srg',REPO/'sails/build/runtime-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(folder/'fixture-srg.jar') as archive:entries={n:archive.read(n) for n in archive.namelist() if n.endswith('.class')}
    entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_fleet_smoke"\nversion="1.0.0"\ndisplayName="Disposable fleet fixture"\n'
    target=folder/'empirecraft-fleet-smoke.jar';sails.jar(target,entries);return target

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',default=os.environ.get('FLEET_RUNTIME_JAVA',str(REPO/'work/toolchains/java21/bin/java')))
    parser.add_argument('--plugin',type=Path,default=REPO/'server/plugins/empirecraft-feudal-1.15.0.jar')
    parser.add_argument('--bridge',type=Path,default=REPO/'bridge/build/libs/empirecraft-towny-journeymap-1.9.0.jar')
    args=parser.parse_args();fixture=compile_fixture();source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,expected in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==expected,name
    logs=ROOT/'build/logs';logs.mkdir(parents=True,exist_ok=True);results=[]
    (REPO/'work').mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='city-fleet-',dir=REPO/'work') as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'plugins/Towny/settings').mkdir(parents=True);(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n');(root/'plugins/Towny/settings/config.yml').write_text("economy:\n  using_economy: 'false'\n")
        shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',root/'plugins/Towny-0.101.2.0.jar');shutil.copyfile(args.plugin,root/'plugins'/args.plugin.name)
        for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','vs_sails-1.20.1-'+sails.VERSION+'-forge.jar','journeymap-forge-1.20.1-6.0.7.jar','empirecraft-claim-guard-1.0.1.jar','supplementaries-1.20-3.1.44-forge-empirecraft-recipes.1.jar','moonlight-1.20-2.16.37-forge.jar','siege-machines-1.20.1-forge-1.39*.jar','geckolib-forge-1.20.1-4.8.4.jar','empirecraft-no-end-1.0.0.jar']:
            path,=(source/'mods').glob(pattern);shutil.copyfile(path,root/'mods'/path.name)
        for path in [args.bridge,fixture]:shutil.copyfile(path,root/'mods'/path.name)
        tested_plugin=hashlib.sha256((root/'plugins'/args.plugin.name).read_bytes()).hexdigest()
        tested_bridge=hashlib.sha256((root/'mods'/args.bridge.name).read_bytes()).hexdigest()
        tested_sails=hashlib.sha256((root/'mods'/('vs_sails-1.20.1-'+sails.VERSION+'-forge.jar')).read_bytes()).hexdigest()
        dependencies={path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted((root/'mods').glob('*.jar')) if path.name!=fixture.name}
        (root/'config/vs_sails').mkdir(parents=True);(root/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
        shutil.copytree(source/'defaultconfigs',root/'defaultconfigs');(root/'eula.txt').write_text('eula=true\n')
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:stone',height=62),dict(block='minecraft:water',height=16)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nview-distance=4\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        launch=['-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch))
        for phase in ['first','restart']:
            log=logs/(phase+'.log')
            with log.open('w') as output:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=300)
            text=log.read_text();lines=[line for line in text.splitlines() if 'FLEET_NATIVE_' in line];print('\n'.join(lines),flush=True)
            if result.returncode or 'FLEET_NATIVE_PASS' not in text or 'FLEET_NATIVE_FAIL' in text:
                print(text[-22000:]);raise SystemExit('Native fleet check failed: '+str(log))
            results += [line for line in lines if 'FLEET_NATIVE_PASS' in line]
    report=dict(runtime=spec['mohist'],results=results,server_restarts=1,plugin_sha256=tested_plugin,bridge_sha256=tested_bridge,sails_sha256=tested_sails,mod_sha256=dependencies,synthetic_approved_market=True,synthetic_treasury_funding=True,graphical_client_tested=False,production_world_tested=False)
    (logs/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('FLEET_NATIVE_RESTART_PASS')

if __name__=='__main__':main()
