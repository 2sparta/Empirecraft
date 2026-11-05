#!/usr/bin/env python3
"""Run fauna against real Towny, Calendar, Naturalist and Livestock across two disposable Mohist boots."""
import argparse,hashlib,json,os,re,shutil,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default=shutil.which('java'));args=parser.parse_args()
    source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,digest in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    logs=ROOT/'build/native';logs.mkdir(parents=True,exist_ok=True);(logs/'result.json').unlink(missing_ok=True);results=[];jar=ROOT/'build/libs/empirecraft-fauna-1.0.1.jar'
    (REPO/'work').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='fauna-',dir=REPO/'work') as scratch:
        root=Path(scratch);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'mohist-config').mkdir();(root/'plugins/Towny/settings').mkdir(parents=True)
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        names=[next((source/'mods').glob('empirecraft-calendar-*.jar')).name,'SereneSeasons-forge-1.20.1-9.1.0.3.jar','GlitchCore-forge-1.20.1-0.0.1.1 (1).jar','naturalist-5.0pre5+forge-1.20.1.jar','DragNs_Livestock_Overhaul-1.20.1-3.9.4.jar','geckolib-forge-1.20.1-4.8.4.jar','Patchouli-1.20.1-85-FORGE.jar','empirecraft-no-end-1.0.0.jar']
        for name in names:shutil.copyfile(source/'mods'/name,root/'mods'/name)
        for path in [jar,ROOT/'build/libs/empirecraft-fauna-smoke.jar']:shutil.copyfile(path,root/'mods'/path.name)
        shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',root/'plugins/Towny-0.101.2.0.jar')
        text=(source/'plugins/Towny/settings/config.yml').read_text();text=re.sub(r"(using_economy:\s*)['\"]?true['\"]?",r"\1'false'",text);(root/'plugins/Towny/settings/config.yml').write_text(text)
        shutil.copyfile(source/'plugins/Towny/settings/townyperms.yml',root/'plugins/Towny/settings/townyperms.yml')
        (root/'config').mkdir();(root/'config/empirecraft-fauna.json').write_text(json.dumps({'townLevelCapacity':{'0':3,'4':6},'capitalCapacity':6,'wildGlobalLimit':6,'wildRegionLimit':30,'biomeRegionLimits':{},'positionRefreshTicks':1},indent=2))
        shutil.copytree(source/'config/sereneseasons',root/'config/sereneseasons')
        (root/'eula.txt').write_text('eula=true\n')
        layers={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+'\nserver-ip=127.0.0.1\nserver-port=25599\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        for phase in [1,2]:
            launch=['-Xmx3G','-XX:ActiveProcessorCount=2','-Dempirecraft.fauna.phase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch));log=logs/f'phase-{phase}.log'
            with log.open('w') as out:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=240)
            text=log.read_text();lines=re.findall(r'FAUNA_NATIVE_(?:PASS[^\n]*|FAIL[^\n]*)',text);print('\n'.join(lines),flush=True)
            if result.returncode or f'FAUNA_NATIVE_PASS phase={phase}' not in text or 'FAUNA_NATIVE_FAIL' in text:
                print(text[-20000:]);raise SystemExit('Native fauna check failed: '+str(log))
            results.extend(lines)
    report={'runtime':spec['mohist'],'fauna_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'phases':results,'actual_towny':True,'actual_naturalist_and_livestock':True,'actual_restarts':1,'graphical_client_tested':False,'production_world_tested':False}
    (logs/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('FAUNA_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
