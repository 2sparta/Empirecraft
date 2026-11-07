#!/usr/bin/env python3
"""Verify the exact overlay, linked client API and upgrade from old on-disk locks."""
import argparse,hashlib,json,os,re,shutil,subprocess,tempfile,zipfile
from pathlib import Path
import build

ROOT=build.ROOT;REPO=build.REPO;BUILD=build.BUILD;SDK=build.SDK
def run(*args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default=shutil.which('java'));parser.add_argument('--static-only',action='store_true');parser.add_argument('--baseline-world',type=Path,help='Development: reuse an old-format world from a successful baseline phase');args=parser.parse_args()
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';installed=REPO/'mods'/build.NAME
    assert installed.read_bytes()==(REPO/'server/mods'/build.NAME).read_bytes()
    with zipfile.ZipFile(build.BASE) as old,zipfile.ZipFile(build.DONOR) as donor,zipfile.ZipFile(installed) as z:
        assert z.testzip() is None
        changed={n for n in old.namelist() if n.endswith('.class') and old.read(n)!=z.read(n)}
        assert changed=={'melonslise/locks/common/init/LocksItems.class','melonslise/locks/common/init/LocksNetwork.class','melonslise/locks/common/container/LockPickingContainer.class','melonslise/locks/client/init/LocksScreens.class'},changed
        for name in ['assets/locks/models/item/steel_lock_pick.json','assets/locks/textures/item/steel_lock_pick.png','assets/locks/textures/gui/steel_lock_pick.png']:
            assert z.read(name)==donor.read(name)
        for name in old.namelist():
            if not name.endswith('/') and ('/recipes/' in name or '/textures/' in name): assert old.read(name)==z.read(name),name
        assert json.loads(z.read('data/locks/tags/items/lock_picks.json'))['values']==['locks:steel_lock_pick']
        assert json.loads(z.read('data/locks/recipes/steel_lock_pick.json'))['result']['count']==1
        for language in ['en_us','ru_ru','uk_ua']:
            data=json.loads(z.read('assets/locks/lang/'+language+'.json'))
            for key,value in json.loads((ROOT/'resources/assets/locks/lang/en_us.json').read_text()).items():
                assert key in data and len(re.findall('%s',value))==len(re.findall('%s',data[key])),(language,key)
        # These persisted types and furniture guards are byte-for-byte unchanged.
        for name in ['Lock','Lockable','Cuboid6i','Transform']:
            path='melonslise/locks/common/util/'+name+'.class';assert z.read(path)==old.read(path)
    tests=BUILD/'tests';tests.mkdir(exist_ok=True)
    run(java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',BUILD/'helper-named.jar','-d',tests,ROOT/'tests/SessionTest.java',ROOT/'tests/NativeLink.java')
    result=run(java,'-Xverify:all','-cp',os.pathsep.join(map(str,[tests,BUILD/'helper-named.jar'])),'SessionTest',capture_output=True,text=True).stdout
    print(result,end='');count=int(re.search('checks=(\\d+)',result)[1])
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    named=BUILD/'installed-named.jar';named.unlink(missing_ok=True)
    run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',installed,named,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
    cp=[named,SDK/'runtime-named.jar',SDK/'client-named.jar',REPO/'server/plugins/Towny-0.101.2.0.jar',*libs]
    run(java,'-Xverify:all','-cp',os.pathsep.join(map(str,[tests,*cp])),'NativeLink')
    compile_args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',tests,ROOT/'tests/Baseline.java',ROOT/'tests/NativeSmoke.java']
    argfile=BUILD/'test-javac.args';argfile.write_text('\n'.join('"'+str(a)+'"' for a in compile_args));run(java,'com.sun.tools.javac.Main','@'+str(argfile))
    fixture=BUILD/'fixture-named.jar';build.jar(fixture,{p.relative_to(tests).as_posix():p.read_bytes() for p in tests.rglob('*.class')})
    srg=BUILD/'fixture-srg.jar';srg.unlink(missing_ok=True)
    run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',fixture,srg,SDK/'named-srg.tiny','named','srg',*cp[:3],'--ignoreConflicts')
    if args.static_only: print('STEEL_STATIC_TEST_PASS');return
    logs=BUILD/'native';logs.mkdir(exist_ok=True)
    source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text())
    for name,expected in spec['sha256'].items():assert hashlib.sha256((source/name).read_bytes()).hexdigest()==expected,name
    scratch=REPO/'work';scratch.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='steel-locks-',dir=scratch) as temp:
        root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'plugins').mkdir();(root/'mohist-config').mkdir()
        (root/'mohist-config/mohist.yml').write_text('config-version: 1\n');(root/'eula.txt').write_text('eula=true\n')
        (root/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
        (root/'defaultconfigs').mkdir();shutil.copyfile(source/'defaultconfigs/locks-server.toml',root/'defaultconfigs/locks-server.toml')
        farmer=next(p.name for p in (source/'mods').glob('FarmersDelight-*.jar'))
        for name in [farmer,'handcrafted-forge-1.20.1-3.0.6.jar','resourcefullib-forge-1.20.1-2.1.29.jar','empirecraft-locks-compat-1.0.0.jar','empirecraft-no-end-1.0.0.jar']:
            shutil.copyfile(source/'mods'/name,root/'mods'/name)
        layers={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
        (root/'server.properties').write_text('level-name=locks_test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+'\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nallow-nether=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
        if args.baseline_world:shutil.copytree(args.baseline_world,root/'locks_test')
        boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
        tested_sha=None
        for phase in ([2] if args.baseline_world else [1,2]):
            active=root/'mods'/build.NAME;shutil.copyfile(build.BASE if phase==1 else installed,active)
            if phase==2:tested_sha=hashlib.sha256(active.read_bytes()).hexdigest()
            if phase==2:shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',root/'plugins/Towny.jar')
            with zipfile.ZipFile(srg) as z:
                data={n:z.read(n) for n in z.namelist() if n.startswith('locktest/')}
            data['plugin.yml']=('name: SteelPickingSmoke\nversion: 1.0\nmain: locktest.'+('Baseline' if phase==1 else 'NativeSmoke')+'\napi-version: 1.20\n'+('depend: [Towny]\n' if phase==2 else '')).encode()
            build.jar(root/'plugins/steel-picking-smoke.jar',data)
            launch=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH',
                    '--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler',
                    '--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
                    '-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),
                    'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
            (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch))
            log=logs/('phase-'+str(phase)+'.log')
            with log.open('w') as output: result=run(args.java,'@launch.args',cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
            text=log.read_text();print('\n'.join(s for s in text.splitlines() if 'STEEL_' in s),flush=True)
            if 'STEEL_NATIVE_FAIL' in text or ('STEEL_BASELINE_PASS' if phase==1 else 'STEEL_NATIVE_PASS') not in text:
                print(text[-14000:]);raise SystemExit('Steel lock native test failed: '+str(log))
            if phase==1:
                shutil.rmtree(logs/'baseline-world',ignore_errors=True);shutil.copytree(root/'locks_test',logs/'baseline-world')
        native=json.loads((root/'steel-picking-result.json').read_text());assert native['passed']
    assert tested_sha==hashlib.sha256(installed.read_bytes()).hexdigest(),'Installed JAR changed during native validation'
    report=dict(status='passed',installed_sha256=tested_sha,base_sha256=build.BASE_SHA,donor_sha256=build.DONOR_SHA,
                session_checks=count,native_client_classes_linked=6,native=native,mohist=spec['mohist'],actual_server_restarts=1,old_world_locks_loaded=3,
                graphical_client='not_run',production_world='not_modified')
    (REPO/'validation/steel-lockpicking.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('STEEL_LOCKPICKING_VALIDATION_PASS',count,'session checks',len(native['checks']),'native checks')

if __name__=='__main__':main()
