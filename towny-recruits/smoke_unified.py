#!/usr/bin/env python3
"""Boot a disposable Mohist world with real dependencies; never starts the production world."""
import argparse,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent
def main():
    p=argparse.ArgumentParser();p.add_argument('--prepare-only',action='store_true');p.add_argument('--reset-world',action='store_true');p.add_argument('--server-home',type=Path,default=R.parent/'server');p.add_argument('--boomsticks',type=Path);p.add_argument('--feudal',type=Path);p.add_argument('--java',default='java');a=p.parse_args()
    server=a.server_home.resolve();out=R/'build/native-test';out.mkdir(exist_ok=True)
    cp=(R/'build/unified-classpath.txt').read_text();classes=R/'build/smoke-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(exist_ok=True)
    compiler=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'javac')
    subprocess.run([str(compiler),'--release','17','-proc:none','-cp',cp,'-d',str(classes),str(R/'tests/RecruitmentSmokeMod.java')],check=True)
    fixture=R/'build/townyrecruits-test.jar'
    with zipfile.ZipFile(fixture,'w') as z:
        z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="townyrecruits_test"\nversion="1"\n[[dependencies.townyrecruits_test]]\nmodId="townyrecruits"\nmandatory=true\nversionRange="[2.0.1]"\nordering="AFTER"\nside="SERVER"\n')
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
    root=out/'world';
    if a.reset_world:shutil.rmtree(root,ignore_errors=True)
    root.mkdir(exist_ok=True);(root/'mods').mkdir(exist_ok=True);(root/'plugins').mkdir(exist_ok=True)
    if not (root/'libraries').exists():(root/'libraries').symlink_to(server/'libraries',target_is_directory=True)
    for pattern in ['recruits-1.20.1-1.15.0.jar','musketmod-1.20.1-forge-1.5.4.jar','medieval_boomsticks-no_stars*.jar','supplementaries-*.jar','moonlight-*.jar','empirecraft-no-end-1.0.0.jar','geckolib-forge-*.jar']:
        source,=list((server/'mods').glob(pattern));shutil.copyfile(source,root/'mods'/source.name)

    if a.boomsticks:shutil.copyfile(a.boomsticks,root/'mods/medieval_boomsticks-no_stars (8).jar')
    shutil.copyfile(fixture,root/'mods'/fixture.name);shutil.copyfile(R/'dist/2.0.1/TownyRecruits.jar',root/'mods/TownyRecruits.jar')
    for name in ['Towny-0.101.2.0.jar','Vault.jar']:shutil.copyfile(server/'plugins'/name,root/'plugins'/name)
    feudal=a.feudal
    if feudal is None:feudal,=list((server/'plugins').glob('empirecraft-feudal-*.jar'))
    shutil.copyfile(feudal,root/'plugins'/feudal.name)
    (root/'eula.txt').write_text('eula=true\n');(root/'mohist-config').mkdir(exist_ok=True);(root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
    settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=62),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
    (root/'server.properties').write_text('level-name=recruitment-test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25595\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    spec=json.loads((R/'tests/native-runtime.json').read_text());boot=[server/path for path in spec['module_path']];legacy=[server/path for path in spec['legacy_classpath']]
    args=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(path.name for path in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
    (root/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
    if a.prepare_only:print(root);return
    for phase,flag in [('initial',[]),('restart',['-Drecruitment.resume=true'])]:
        if phase=='restart':(root/'plugins'/feudal.name).rename(root/'plugins'/(feudal.name+'.disabled'))
        log=out/(phase+'.log')
        with log.open('w') as output:result=subprocess.run([a.java,*flag,'@launch.args'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text();print(phase,result.returncode,flush=True)
        if result.returncode or 'RECRUIT_SMOKE_PASS phase='+phase not in text or 'RECRUIT_SMOKE_FAIL' in text:print(text[-14000:]);raise SystemExit('Native test failed: '+str(log))
    print('RECRUIT_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
