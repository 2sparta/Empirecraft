#!/usr/bin/env python3
"""Two real Mohist/Forge launches in a generated test world; never reads a production world."""
import argparse, hashlib, json, os, re, shutil, subprocess, tomllib, zipfile, time
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--runtime',type=Path,required=True);p.add_argument('--full-pack',action='store_true');a=p.parse_args()
    target=a.runtime.resolve()
    assert str(target).startswith('/workspace/work/'), 'Tests must use generated scratch storage'
    if target.exists():
        assert (target/'TFC-INTEGRATION-TEST-ONLY').exists(),'Refusing to remove an unrelated directory'
        shutil.rmtree(target)
    target.mkdir(parents=True);(target/'TFC-INTEGRATION-TEST-ONLY').write_text('Generated test world, never a production world.\n')
    source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,digest in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    (target/'libraries').symlink_to(source/'libraries',target_is_directory=True);(target/'mods').mkdir()
    by_id={};meta={}
    for jar in (source/'mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            d=tomllib.loads(z.read('META-INF/mods.toml').decode());meta[jar]=d
            for mod in d.get('mods',[]):by_id[mod['modId']]=jar
    selected=set();pending=['create','naturalist','dragnlivestock','overgeared','legendarysurvivaloverhaul',
        'sereneseasons','starcatcher','magistuarmory','createbigcannons','medieval_boomsticks','musketmod',
        'brewinandchewin','vinery','sulfur_gunpowder','ecologics','empirecraft_forging','empirecraft_calendar','empirecraft_no_end']
    builtins={'minecraft','forge','javafml','tfc','empirecraft_tfc','empirecraft_tfc_test','ponder','flywheel','registrate','mixinextras'}
    while pending:
        name=pending.pop()
        if name in builtins:continue
        if name not in by_id:raise SystemExit('Missing test dependency '+name)
        jar=by_id[name]
        if jar in selected:continue
        selected.add(jar)
        for dependencies in meta[jar].get('dependencies',{}).values():
            for dep in dependencies:
                if dep.get('mandatory'):pending.append(dep['modId'])
    selected.add(by_id['patchouli'])
    if a.full_pack:selected=set((source/'mods').glob('*.jar'))
    selected={jar for jar in selected if not jar.name.startswith(('TerraFirmaCraft-','empirecraft-tfc-'))}
    for jar in selected:(target/'mods'/jar.name).symlink_to(jar)
    for pattern in ['TerraFirmaCraft-*-empirecraft.1.jar','empirecraft-tfc-integration-*.jar','empirecraft-tfc-test.jar']:
        jar=next((ROOT/'build/libs').glob(pattern));(target/'mods'/jar.name).symlink_to(jar)
    (target/'plugins/Towny/settings').mkdir(parents=True)
    shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',target/'plugins/Towny-0.101.2.0.jar')
    text=(source/'plugins/Towny/settings/config.yml').read_text();text=re.sub(r"(using_economy:\s*)['\"]?true['\"]?",r"\1'false'",text)
    (target/'plugins/Towny/settings/config.yml').write_text(text)
    shutil.copyfile(source/'plugins/Towny/settings/townyperms.yml',target/'plugins/Towny/settings/townyperms.yml')
    (target/'mohist-config').mkdir();(target/'mohist-config/mohist.yml').write_text('config-version: 1\n')
    if a.full_pack:shutil.copytree(source/'config',target/'config')
    else:
        (target/'config').mkdir();shutil.copyfile(source/'config/overgeared-common.toml',target/'config/overgeared-common.toml')
        shutil.copytree(source/'config/sereneseasons',target/'config/sereneseasons')
    if (source/'defaultconfigs').exists():shutil.copytree(source/'defaultconfigs',target/'defaultconfigs')
    # Run the existing recipe policy scripts too; they used to reject every new TFC steel output.
    if 'kubejs' in by_id:
        for dependency in ['kubejs','rhino','architectury']:
            if dependency in by_id:(target/'mods'/by_id[dependency].name).symlink_to(by_id[dependency]) if not (target/'mods'/by_id[dependency].name).exists() else None
        (target/'kubejs').mkdir()
        for subdir in ['server_scripts','startup_scripts']+(['data'] if a.full_pack else []):
            if (source/'kubejs'/subdir).exists():shutil.copytree(source/'kubejs'/subdir,target/'kubejs'/subdir)
    (target/'eula.txt').write_text('eula=true\n')
    settings={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],
              'features':False,'lakes':False,'structure_overrides':[]}
    (target/'server.properties').write_text('level-name=test\nlevel-seed=4411\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+
        '\nserver-ip=127.0.0.1\nserver-port=25597\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
    suite='full-pack' if a.full_pack else 'smoke'
    logs=ROOT/'build'/suite;logs.mkdir(exist_ok=True);results=[]
    for phase in [1,2]:
        args=['-Xmx6G','-XX:ActiveProcessorCount=2','-Dempirecraft.tfc.phase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),
              '--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler',
              '--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler',
              '--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),
              '-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
              'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23',
              '--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
        (target/'launch.args').write_text('\n'.join('"'+str(v)+'"' for v in args))
        log=logs/f'phase-{phase}.log';print('Launching test phase',phase,flush=True)
        with log.open('w') as out:
            process=subprocess.Popen([str(a.java_home/'bin/java'),'@launch.args'],cwd=target,stdout=out,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+600
            while process.poll() is None:
                try:process.wait(timeout=2)
                except subprocess.TimeoutExpired:pass
                current=log.read_text(errors='replace')
                if 'TFC_NATIVE_FAIL' in current or 'Failed to load datapacks' in current or 'Loading errors encountered' in current or time.monotonic()>deadline:
                    process.terminate()
                    try:process.wait(timeout=20)
                    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
                    break
        text=log.read_text();markers=re.findall('TFC_NATIVE_(?:PASS|FAIL)[^\n]*',text);print('\n'.join(markers),flush=True)
        if process.returncode or f'TFC_NATIVE_PASS phase={phase}' not in text or 'TFC_NATIVE_FAIL' in text:
            print(text[-16000:]);raise SystemExit('Integration test failed: '+str(log))
        results.extend(markers)
    report={'runtime':spec['mohist'],'forge':spec['forge'],'actual_mods':[p.name for p in selected],
            'phases':results,'production_world_opened':False,'graphical_client_tested':False,
            'test_world':str(target),'jars':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'build/libs').glob('*.jar')}}
    (ROOT/('audit/'+suite+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':main()
