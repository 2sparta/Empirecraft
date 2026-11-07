#!/usr/bin/env python3
"""Run trader AI and every installed trade pool in a disposable copy of the full mod set."""
import argparse,hashlib,json,os,re,shutil,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build'
def run(*args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def archive(path,entries):
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
def normalize(report):
    """Remove VM-generated lambda numbers/addresses from the human-readable audit."""
    clean=lambda text:re.sub(r'\$\$Lambda(?:\$\d+)?(?:/0x[0-9a-fA-F]+)?',lambda match:'$$Lambda',text)
    for result in report['results']:
        for row in result['provider_audit']:row['provider']=clean(row['provider'])
        result['provider_audit'].sort(key=lambda row:(row['pool'],row['provider'],row['allowed'],row['retired_or_invalid'],str(row.get('rejected_views_after_migration',[]))))
        result['skipped_map_generation']=sorted(clean(name) for name in result['skipped_map_generation'])
    return report
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default='/usr/bin/java');args=parser.parse_args()
    BUILD.mkdir(exist_ok=True);java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]));named=[]
    def remap(source,target,from_space,to_space,*extra):
        target.unlink(missing_ok=True);run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',source,target,SDK/'named-srg.tiny',from_space,to_space,*extra,'--ignoreConflicts')
    for stem in ['empirecraft-world-rules-1.0.2','empirecraft-forging-1.1.3']:
        output=BUILD/(stem+'-named.jar');remap(REPO/'server/mods'/(stem+'.jar'),output,'srg','named',SDK/'runtime-srg.jar');named.append(output)
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    cp=[*named,SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
    compile_args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,ROOT/'NativeTrades.java']
    argfile=BUILD/'javac.args';argfile.write_text('\n'.join('"'+str(a)+'"' for a in compile_args));run(java,'com.sun.tools.javac.Main','@'+str(argfile))
    fixture=BUILD/'fixture-named.jar';archive(fixture,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    srg=BUILD/'fixture-srg.jar';remap(fixture,srg,'named','srg',*cp[:4])
    with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_trader_test"\nversion="1.0.0"\n'
    fixture=BUILD/'empirecraft-trader-test.jar';archive(fixture,entries)
    spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());source=REPO/'server'
    for name,digest in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==digest,name
    snapshot={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((source/'mods').glob('*.jar'))}
    scratch=REPO/'work';scratch.mkdir(exist_ok=True);logs=BUILD/'native';logs.mkdir(exist_ok=True);reports=[]
    with tempfile.TemporaryDirectory(prefix='trader-items-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'home').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n');(root/'eula.txt').write_text('eula=true\n');(root/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
        for p in (source/'mods').glob('*.jar'):shutil.copyfile(p,root/'mods'/p.name)
        shutil.copyfile(fixture,root/'mods'/fixture.name)
        (root/'config').mkdir()
        configs=['overgeared-common.toml','empirecraft_world_rules-common.toml','vinery.toml','ecologics-common.toml','nirvana-common.toml','fruitsdelight-common.toml','supplementaries-common.toml']
        copied_configs={}
        for name in configs:
            if(source/'config'/name).exists():shutil.copyfile(source/'config'/name,root/'config'/name)
            if(source/'config'/name).exists():copied_configs[name]=hashlib.sha256((source/'config'/name).read_bytes()).hexdigest()
        shutil.copytree(source/'config/sereneseasons',root/'config/sereneseasons')
        flat={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=trader_test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)+'\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nallow-nether=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        for phase in [1,2]:
            launch=['-Xmx6G','-XX:ActiveProcessorCount=2','-Duser.home='+str(root/'home'),'-Dempirecraft.trader.phase='+str(phase),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
                    '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
                    '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
                    '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
                    'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch))
            log=logs/('phase-'+str(phase)+'.log')
            with log.open('w') as output:result=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=480)
            text=log.read_text();print('\n'.join(s for s in text.splitlines() if 'TRADER_NATIVE_' in s),flush=True)
            if result.returncode or 'TRADER_NATIVE_PASS phase='+str(phase) not in text or 'TRADER_NATIVE_FAIL' in text:
                print(text[-16000:]);raise SystemExit('Trader native check failed: '+str(log))
            data=json.loads((root/('trader-phase-'+str(phase)+'.json')).read_text());assert data['passed'];reports.append(data)
    assert snapshot=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((source/'mods').glob('*.jar'))},'Runtime changed during the test'
    report={'status':'passed','mohist':spec['mohist'],'runtime_jars':snapshot,'copied_game_configs':copied_configs,'actual_server_restarts':1,'results':reports,'graphical_client':'not_run','production_world':'not_modified'}
    (REPO/'validation/trader-cut-items.json').write_text(json.dumps(normalize(report),ensure_ascii=False,indent=2)+'\n');print('TRADER_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
