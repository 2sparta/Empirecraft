#!/usr/bin/env python3
"""Verify admin permissions, growth cancellation and config persistence across two real server runs."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile,re
from pathlib import Path
import build

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server-home',type=Path,default=build.REPO/'server');p.add_argument('--effects',action='store_true');p.add_argument('--java',default='java');a=p.parse_args()
    server=a.server_home.resolve();spec=json.loads((build.ROOT/'smoke-runtime.json').read_text())
    for path,digest in spec['sha256'].items():assert hashlib.sha256((server/path).read_bytes()).hexdigest()==digest,'Changed runtime: '+path
    scratch=build.REPO/'work';scratch.mkdir(exist_ok=True);logs=build.BUILD/('effects-world-logs' if a.effects else 'growth-world-logs');logs.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='leaves-growth-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(server/'libraries',target_is_directory=True);(root/'mods').mkdir()
        for pattern in ['SereneSeasons-forge-1.20.1-9.1.0.3.jar','GlitchCore-forge-1.20.1-0.0.1.1*.jar','empirecraft-no-end-1.0.0.jar','empirecraft-world-rules-1.0.2.jar','empirecraft-calendar-*.jar','combatlog-2.8.4-forge+1.20.1.jar']:
            source,=list((server/'mods').glob(pattern));shutil.copyfile(source,root/'mods'/source.name)
        for source in [build.ROOT/'dist'/build.VERSION/('empirecraft-seasonal-leaves-'+build.VERSION+'.jar'),build.BUILD/('empirecraft-effects-test.jar' if a.effects else 'empirecraft-growth-test.jar')]:shutil.copyfile(source,root/'mods'/source.name)
        (root/'eula.txt').write_text('eula=true\n');(root/'mohist-config').mkdir();(root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        (root/'config').mkdir();config=root/'config/empirecraft_seasonal_leaves-common.toml'
        (root/'config/sereneseasons').mkdir()
        (root/'config/sereneseasons/seasons.toml').write_text('[time_settings]\nday_duration=57600\nsub_season_duration=30\nstarting_sub_season=1\nprogress_season_while_offline=true\n')
        # Start from a complete profile: this fixture verifies command persistence,
        # while schema correction separately rewrites the file watched by Forge.
        shutil.copyfile(build.ROOT/'tests/growth-config.toml',config)
        settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=62),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
        (root/'server.properties').write_text('level-name=growth-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port='+('25597' if a.effects else '25596')+'\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[server/path for path in spec['module_path']];legacy=[server/path for path in spec['legacy_classpath']]
        args=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(path.name for path in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
        results=[]
        phases=[('initial',[]),('restart',['-Deffects.resume=true'])] if a.effects else [('off',[]),('on',['-Dgrowth.resume=true'])]
        marker='EFFECTS_SMOKE_' if a.effects else 'GROWTH_SMOKE_'
        for phase,flag in phases:
            log=logs/(phase+'.log')
            with log.open('w') as output:result=subprocess.run([a.java,*flag,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
            text=log.read_text();marks=re.findall(marker+r'(?:PASS|FAIL)[^\r\n]*',text);print(phase,marks,flush=True)
            shutil.copyfile(config,logs/(phase+'-config.toml'))
            if result.returncode or marker+'PASS phase='+phase not in text or marker+'FAIL' in text:
                print(text[-14000:]);raise SystemExit('Native growth test failed: '+str(log))
            expected='false' if phase=='off' else 'true';assert re.search(r'growthEnabled\s*=\s*'+expected,config.read_text()),'Command did not persist '+expected
            results.append(dict(phase=phase,exit_code=result.returncode,markers=marks))
        (logs/'result.json').write_text(json.dumps(dict(runtime='Mohist 71a84ff7 / Forge 47.4.23',addon_sha256=build.sha(root/'mods'/('empirecraft-seasonal-leaves-'+build.VERSION+'.jar')),runs=results),indent=2)+'\n')
    print('EFFECTS_NATIVE_RESTART_PASS' if a.effects else 'GROWTH_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
