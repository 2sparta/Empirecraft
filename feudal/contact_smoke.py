#!/usr/bin/env python3
"""Exercise the production Contact adapter across two disposable Mohist server boots."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile
from pathlib import Path
REPO=Path(__file__).resolve().parent.parent
from release import bridge_name

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java',default=shutil.which('java'))
    parser.add_argument('--bridge-jar',type=Path,default=REPO/'bridge/build/libs'/bridge_name())
    parser.add_argument('--fixture',type=Path,default=REPO/'bridge/build/libs/empirecraft-contact-smoke.jar')
    args=parser.parse_args();source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,expected in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==expected,name
    logs=REPO/'feudal/build/contact-native';logs.mkdir(parents=True,exist_ok=True)
    scratch=REPO/'work';scratch.mkdir(exist_ok=True);results=[]
    with tempfile.TemporaryDirectory(prefix='fair-contact-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
        for name in ['Contact-forge-1.2.3-empirecraft.1.jar','OELib-forge-1.20.1-0.2.4.1.jar','journeymap-forge-1.20.1-6.0.7.jar','empirecraft-no-end-1.0.0.jar']:
            shutil.copyfile(source/'mods'/name,root/'mods'/name)
        for artifact in [args.bridge_jar,args.fixture]:shutil.copyfile(artifact,root/'mods'/artifact.name)
        (root/'config/contact').mkdir(parents=True);shutil.copyfile(source/'config/contact/contact_common.json',root/'config/contact/contact_common.json')
        (root/'eula.txt').write_text('eula=true\n')
        layers={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+'\nserver-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        for phase in [1,2]:
            launch=['-Xmx2G','-XX:ActiveProcessorCount=2','-Dempirecraft.contact.testPhase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
                    '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
                    '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
                    '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
                    'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in launch))
            log=logs/('phase-'+str(phase)+'.log')
            with log.open('w') as out:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=240)
            text=log.read_text();lines=[s for s in text.splitlines() if 'CONTACT_FAIR_NATIVE_' in s];print('\n'.join(lines),flush=True)
            if result.returncode or 'CONTACT_FAIR_NATIVE_PASS phase='+str(phase) not in text or 'CONTACT_FAIR_NATIVE_FAIL' in text:
                print(text[-16000:]);raise SystemExit('Contact native check failed: '+str(log))
            results.extend(lines)
    report={'runtime':spec['mohist'],'phases':results,'bridge_sha256':hashlib.sha256(args.bridge_jar.read_bytes()).hexdigest(),
            'contact_sha256':hashlib.sha256((source/'mods/Contact-forge-1.2.3-empirecraft.1.jar').read_bytes()).hexdigest(),
            'actual_server_restarts':1,'graphical_client_tested':False,'production_world_tested':False}
    (logs/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('CONTACT_FAIR_NATIVE_RESTART_PASS')

if __name__=='__main__':main()
