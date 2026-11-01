#!/usr/bin/env python3
"""Run large-hull edit and radial-damage regressions in a disposable native server world."""
import argparse,hashlib,importlib.util,json,os,shlex,shutil,subprocess,tempfile,zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('sails_build',Path(__file__).resolve().parent.parent/'sails/build.py')
build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
ROOT=Path(__file__).resolve().parent

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default=shutil.which('java'));a=parser.parse_args()
    repo=build.REPO;spec=json.loads((build.ROOT/'smoke-runtime.json').read_text());source=repo/'server'
    for relative,expected in spec['sha256'].items():
        assert hashlib.sha256((source/relative).read_bytes()).hexdigest()==expected,'Changed runtime input: '+relative
    scratch=repo/'work';scratch.mkdir(exist_ok=True);logs=ROOT/'build/smoke-logs';logs.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ship-physics-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True)
        (root/'mods').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        for pattern in ['architectury-9.2.14-forge.jar','kotlinforforge-4.12.0-all*.jar','valkyrienskies-120-2.4.11-empirecraft.1.jar','vs_sails-1.20.1-'+build.VERSION+'-forge.jar','empirecraft-ship-physics-1.2.1.jar','create-1.20.1-6.0.8-medieval.4.jar','copycats-3.0.10+mc.1.20.1-forge-empirecraft-recipes.1.jar','empirecraft-no-end-1.0.0.jar']:
            path,=list((source/'mods').glob(pattern));shutil.copyfile(path,root/'mods'/path.name)
        flags=shlex.split((ROOT/'build/javac.args').read_text())
        cp=os.pathsep.join([str(ROOT/'build/classes'),str(build.ROOT/'build/classes'),flags[flags.index('-cp')+1]])
        classes=ROOT/'build/fixture-classes';classes.mkdir(exist_ok=True)
        compiler_java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
        build.run([compiler_java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',classes,ROOT/'tests/ShipPhysicsSmokeMod.java'])
        named=ROOT/'build/fixture-named.jar';build.jar(named,{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
        fixture=ROOT/'build/empirecraft-physics-smoketest.jar';fixture.unlink(missing_ok=True)
        build.run([a.java,'-cp',os.pathsep.join([str(named),cp]),'net.md_5.specialsource.SpecialSource','-i',named,'-o',fixture,'-m',build.ROOT/'build/offline-named-srg.srg','-l','--quiet'])
        with zipfile.ZipFile(fixture) as z:entries={n:z.read(n) for n in z.namelist()}
        entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_physics_smoketest"\nversion="1.0.0"\ndisplayName="Ship Physics test fixture"\n'
        build.jar(fixture,entries);shutil.copyfile(fixture,root/'mods'/fixture.name)
        (root/'config/vs_sails').mkdir(parents=True)
        (root/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nmagic-ballast-righting-force=0\n')
        (root/'eula.txt').write_text('eula=true\n')
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:water',height=68)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        args=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
            '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
            '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
            '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
            'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
        log=logs/'native-physics.log'
        with log.open('w') as output:
            result=subprocess.run([a.java,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text();lines=[line for line in text.splitlines() if 'SHIP_PHYSICS_SMOKE_' in line]
        print('\n'.join(lines))
        if result.returncode or 'SHIP_PHYSICS_SMOKE_PASS' not in text or 'SHIP_PHYSICS_SMOKE_FAIL' in text or 'Exception in thread "Physics thread"' in text:
            print(text[-16000:]);raise SystemExit('Ship Physics native test failed: '+str(log))
        (logs/'result.json').write_text(json.dumps(dict(runtime=spec['mohist'],exit_code=result.returncode,result=lines,production_world_tested=False),indent=2)+'\n')
        print('SHIP_PHYSICS_NATIVE_WORLD_PASS')
if __name__=='__main__':main()
