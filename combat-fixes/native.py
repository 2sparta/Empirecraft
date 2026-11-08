#!/usr/bin/env python3
"""Exercise armor, wounds, recruit eggs and repeated player deaths on a full disposable Mohist."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build'
def run(*args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def archive(path,entries):
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='/usr/bin/java');args=p.parse_args()
    BUILD.mkdir(exist_ok=True);java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]));classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
    compile_args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,ROOT/'NativeCombat.java']
    argfile=BUILD/'javac.args';argfile.write_text('\n'.join('"'+str(a)+'"' for a in compile_args));run(java,'com.sun.tools.javac.Main','@'+str(argfile))
    fixture=BUILD/'fixture-named.jar';archive(fixture,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    srg=BUILD/'fixture-srg.jar';srg.unlink(missing_ok=True)
    run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',fixture,srg,SDK/'named-srg.tiny','named','srg',*cp[:2],'--ignoreConflicts')
    with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_combat_test"\nversion="1.0.0"\n'
    entries['pack.mcmeta']=b'{"pack":{"pack_format":15,"description":"Isolated combat regression"}}'
    fixture=BUILD/'empirecraft-combat-test.jar';archive(fixture,entries)
    spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());source=REPO/'server'
    for name,digest in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    snapshot={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((source/'mods').glob('*.jar'))}
    scratch=REPO/'work';scratch.mkdir(exist_ok=True);logs=BUILD/'native';logs.mkdir(exist_ok=True);reports=[]
    plugins=['Towny-0.101.2.0.jar','empirecraft-feudal-1.18.0.jar','EssentialsX-2.21.2.jar','EssentialsXSpawn-2.21.2.jar']
    with tempfile.TemporaryDirectory(prefix='combat-fixes-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'home').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n');(root/'eula.txt').write_text('eula=true\n');(root/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
        for path in (source/'mods').glob('*.jar'):shutil.copyfile(path,root/'mods'/path.name)
        shutil.copyfile(fixture,root/'mods'/fixture.name);(root/'plugins/Towny/settings').mkdir(parents=True)
        for name in plugins:shutil.copyfile(source/'plugins'/name,root/'plugins'/name)
        # Simulate an already-installed world made with the old false default.
        (root/'plugins/Towny/settings/config.yml').write_text("economy:\n  using_economy: 'false'\nnew_world_settings:\n  pvp:\n    world_pvp: 'false'\n")
        (root/'plugins/Essentials').mkdir();shutil.copyfile(source/'plugins/Essentials/config.yml',root/'plugins/Essentials/config.yml')
        (root/'config').mkdir();copied={}
        for name in ['overgeared-common.toml','empirecraft_world_rules-common.toml','vinery.toml','combatlog-common.json5','recruits-server.toml']:
            shutil.copyfile(source/'config'/name,root/'config'/name);copied[name]=hashlib.sha256((source/'config'/name).read_bytes()).hexdigest()
        for name in ['sereneseasons','epicknights','epicknightsaddon','darkages','slavicarmory','antiquelegacy']:shutil.copytree(source/'config'/name,root/'config'/name)
        flat={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=world\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)+'\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\npvp=true\nallow-nether=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        for phase in [1,2]:
            launch=['-Xmx6G','-XX:ActiveProcessorCount=2','-Duser.home='+str(root/'home'),'-Dempirecraft.combat.phase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch));log=logs/('phase-'+str(phase)+'.log')
            with log.open('w') as output:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=600)
            text=log.read_text();print('\n'.join(s for s in text.splitlines() if 'COMBAT_NATIVE_' in s),flush=True)
            if result.returncode or 'COMBAT_NATIVE_PASS phase='+str(phase) not in text or 'COMBAT_NATIVE_FAIL' in text:
                print(text[-18000:]);raise SystemExit('Combat native check failed: '+str(log))
            data=json.loads((root/('combat-phase-'+str(phase)+'.json')).read_text());assert data['passed'];reports.append(data)
    assert snapshot=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((source/'mods').glob('*.jar'))},'Runtime changed during the test'
    report={'status':'passed','mohist':spec['mohist'],'runtime_jars':snapshot,'plugins':{name:hashlib.sha256((source/'plugins'/name).read_bytes()).hexdigest() for name in plugins},'copied_game_configs':copied,'legacy_towny_default_pvp':False,'actual_server_restarts':1,'results':reports,'graphical_client':'not_run','production_world':'not_modified'}
    from audit import verify
    report['armor_balance_comparison']=verify(report)
    (REPO/'validation/combat-armor-recruits.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('COMBAT_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
