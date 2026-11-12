#!/usr/bin/env python3
"""Reproduce 1.3.1 winter loss and verify 1.3.2 recovery in a disposable full-pack world."""
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile
from pathlib import Path
import build

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-home',type=Path,default=build.REPO/'server')
    p.add_argument('--java',default=shutil.which('java'))
    a=p.parse_args();server=a.server_home.resolve()
    spec=json.loads((build.ROOT/'smoke-runtime.json').read_text())
    for path,digest in spec['sha256'].items():
        assert build.sha(server/path)==digest,'Changed runtime: '+path
    logs=build.BUILD/'evergreen-world-logs';logs.mkdir(parents=True,exist_ok=True)
    candidate=build.ROOT/'dist'/build.VERSION/('empirecraft-seasonal-leaves-'+build.VERSION+'.jar')
    legacy=build.ROOT/'dist/1.3.1/empirecraft-seasonal-leaves-1.3.1.jar'
    assert build.sha(legacy)=='e53e65ba697023a7e38fde389b35c6c7e5a1b89a10496ef1c58fd15d864a1051'
    results=[]
    with tempfile.TemporaryDirectory(prefix='evergreen-native-',dir=build.REPO/'work') as temp:
        root=Path(temp);(root/'libraries').symlink_to(server/'libraries',target_is_directory=True)
        (root/'mods').mkdir();(root/'config').mkdir()
        # All installed tree families and their actual companion mods, without production world data.
        for source in sorted((server/'mods').glob('*.jar')):
            if not source.name.startswith('empirecraft-seasonal-leaves-'):
                (root/'mods'/source.name).symlink_to(source)
        fixture=build.BUILD/'empirecraft-evergreen-test.jar'
        shutil.copyfile(fixture,root/'mods'/fixture.name)
        (root/'eula.txt').write_text('eula=true\n');(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        config=(build.ROOT/'tests/growth-config.toml').read_text().replace('includePersistentLeaves = false','includePersistentLeaves = true')
        (root/'config/empirecraft_seasonal_leaves-common.toml').write_text(config)
        (root/'config/sereneseasons').mkdir()
        (root/'config/sereneseasons/seasons.toml').write_text('[time_settings]\nday_duration=57600\nsub_season_duration=30\nstarting_sub_season=1\nprogress_season_while_offline=false\n')
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=62),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=evergreen-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[server/path for path in spec['module_path']];legacycp=[server/path for path in spec['legacy_classpath']]
        args=['-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(path.name for path in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacycp)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
        for phase,jar,flags in [('legacy',legacy,['-Devergreen.legacy=true']),('fixed',candidate,[])]:
            for old in (root/'mods').glob('empirecraft-seasonal-leaves-*.jar'):old.unlink()
            shutil.copyfile(jar,root/'mods'/jar.name)
            log=logs/(phase+'.log')
            with log.open('w') as output:
                result=subprocess.run([a.java,*flags,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=480)
            text=log.read_text();marks=re.findall(r'EVERGREEN_NATIVE_(?:PASS|FAIL)[^\r\n]*',text)
            print(phase,marks,flush=True)
            if result.returncode or 'EVERGREEN_NATIVE_PASS phase='+phase not in text or 'EVERGREEN_NATIVE_FAIL' in text:
                print(text[-14000:]);raise SystemExit('Evergreen native check failed: '+str(log))
            shutil.copyfile(root/'evergreen-registry.json',logs/(phase+'-registry.json'))
            results.append(dict(phase=phase,jar_sha256=build.sha(jar),exit_code=result.returncode,markers=marks))
        report=dict(runtime='Mohist 71a84ff7 / Forge 47.4.23',addon_sha256=build.sha(candidate),fixture_sha256=build.sha(fixture),full_installed_modpack=True,production_world_modified=False,runs=results)
        (logs/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    print('EVERGREEN_NATIVE_RESTART_PASS')

if __name__=='__main__':main()
