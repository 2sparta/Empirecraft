#!/usr/bin/env python3
"""Run new ecology behavior with every installed server mod in a disposable world."""
import argparse, hashlib, json, os, re, shutil, subprocess, time
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
UPDATES=[REPO/'fauna/build/libs/empirecraft-fauna-1.2.0.jar',
         REPO/'seasonal-leaves/dist/1.3.3/empirecraft-seasonal-leaves-1.3.3.jar',
         REPO/'seasonal-calendar/build/libs/empirecraft-calendar-1.2.2.jar',
         REPO/'layered-armor/dist/empirecraft-layered-armor-1.0.3.jar',
         REPO/'world-rules/build/libs/empirecraft-world-rules-1.0.3.jar']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--runtime',type=Path,required=True);a=p.parse_args();target=a.runtime.resolve()
    assert str(target).startswith('/workspace/work/'),'Use generated scratch storage'
    marker=target/'ECOLOGY-TEST-ONLY'
    if target.exists():
        assert marker.is_file(),'Refusing to remove an unrelated directory'
        shutil.rmtree(target)
    target.mkdir(parents=True);marker.write_text('Disposable generated test world.\n')
    source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,digest in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    (target/'libraries').symlink_to(source/'libraries',target_is_directory=True);(target/'mods').mkdir()
    for jar in (source/'mods').glob('*.jar'):
        if not jar.name.startswith(('empirecraft-fauna-','empirecraft-seasonal-leaves-','empirecraft-calendar-','empirecraft-layered-armor-','empirecraft-world-rules-','antixray-')):
            (target/'mods'/jar.name).symlink_to(jar)
    for jar in [*UPDATES,ROOT/'build/empirecraft-ecology-test.jar']:
        assert jar.is_file(),jar
        shutil.copyfile(jar,target/'mods'/jar.name)
    (target/'plugins/Towny/settings').mkdir(parents=True)
    shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',target/'plugins/Towny-0.101.2.0.jar')
    text=(source/'plugins/Towny/settings/config.yml').read_text();text=re.sub(r"(using_economy:\s*)['\"]?true['\"]?",r"\1'false'",text)
    (target/'plugins/Towny/settings/config.yml').write_text(text)
    shutil.copyfile(source/'plugins/Towny/settings/townyperms.yml',target/'plugins/Towny/settings/townyperms.yml')
    (target/'mohist-config').mkdir();(target/'mohist-config/mohist.yml').write_text('config-version: 1\n')
    shutil.copytree(source/'config',target/'config')
    # AntiXray initializes every TFC collision shape even when configured off. There are no clients
    # or chunk packets in this behavior fixture, so only that packet filter is excluded here.
    for name in ['defaultconfigs','kubejs']:
        if (source/name).exists():shutil.copytree(source/name,target/name,ignore=shutil.ignore_patterns('logs','client_scripts'))
    (target/'eula.txt').write_text('eula=true\n')
    settings={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':62},{'block':'minecraft:grass_block','height':1}],
              'features':False,'lakes':False,'structure_overrides':[]}
    (target/'server.properties').write_text('level-name=world\nlevel-seed=4411\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+
        '\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
    logs=ROOT/'build/native';logs.mkdir(exist_ok=True);results=[]
    for phase in [1,2]:
        launch=['-Xmx6G','-XX:ActiveProcessorCount=2','-Decology.phase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),
              '--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler',
              '--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler',
              '--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),
              '-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
              'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23',
              '--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (target/'launch.args').write_text('\n'.join('"'+str(v)+'"' for v in launch))
        log=logs/f'phase-{phase}.log';print('Launching ecology phase',phase,flush=True)
        with log.open('w') as out:
            process=subprocess.Popen([str(a.java_home/'bin/java'),'@launch.args'],cwd=target,stdout=out,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+1200
            while process.poll() is None:
                try:process.wait(timeout=2)
                except subprocess.TimeoutExpired:pass
                current=log.read_text(errors='replace')
                if any(word in current for word in ['ECOLOGY_NATIVE_FAIL','Failed to load datapacks','Loading errors encountered']) or time.monotonic()>deadline:
                    process.terminate()
                    try:process.wait(timeout=20)
                    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
                    break
        text=log.read_text(errors='replace');markers=re.findall('ECOLOGY_NATIVE_(?:PASS|FAIL)[^\n]*',text);print('\n'.join(markers),flush=True)
        if process.returncode or f'ECOLOGY_NATIVE_PASS phase={phase}' not in text or 'ECOLOGY_NATIVE_FAIL' in text:
            print(text[-22000:]);raise SystemExit('Ecology test failed: '+str(log))
        results.extend(markers)
    for name in ['ecology-plants.json','ecology-flight.json']:shutil.copyfile(target/name,logs/name)
    report={'runtime':spec['mohist'],'forge':spec['forge'],'mods':len(list((target/'mods').glob('*.jar'))),'phases':results,
            'test_world':str(target),'production_world_opened':False,'graphical_client_tested':False,
            'fixture_overrides':['Towny economy disabled; no Vault/economy plugin in fixture','AntiXray excluded from generated fixture; no clients or chunk packets'],
            'updates':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in UPDATES}}
    (logs/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('ECOLOGY_NATIVE_RESTART_PASS')

if __name__=='__main__':main()
