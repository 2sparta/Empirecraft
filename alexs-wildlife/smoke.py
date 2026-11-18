#!/usr/bin/env python3
"""Boot only disposable worlds using the repository's exact Mohist/Forge runtime."""
import argparse,json,os,re,shutil,subprocess
from pathlib import Path
from build import ROOT,REPO,WORK,JDK17,AM,MOD,SPAWN,pack,sha

def main():
    p=argparse.ArgumentParser();p.add_argument('--citadel',type=Path,required=True);p.add_argument('--full-pack',action='store_true');a=p.parse_args()
    assert sha(a.citadel)==json.loads((ROOT/'inputs.json').read_text())['citadel_sha256']
    root=WORK/('runtime-full' if a.full_pack else 'runtime');root.mkdir(exist_ok=True);(root/'libraries').symlink_to(REPO/'server/libraries',target_is_directory=True) if not (root/'libraries').exists() else None
    (root/'mods').mkdir(exist_ok=True);(root/'mohist-config').mkdir(exist_ok=True);(root/'mohist-config/mohist.yml').write_text('config-version: 1\n')
    for name in [AM,MOD,SPAWN]:shutil.copy2(ROOT/'dist'/name,root/'mods'/name)
    shutil.copy2(a.citadel,root/'mods'/a.citadel.name)
    for pattern in ['geckolib-forge-*.jar','FarmersDelight-*.jar','wonderoussea-*.jar','naturalist-*.jar','empirecraft-no-end-*.jar']:
        source,=list((REPO/'server/mods').glob(pattern));shutil.copy2(source,root/'mods'/source.name)
    if a.full_pack:
        for source in (REPO/'server/mods').glob('*.jar'):
            if source.name.startswith('antixray-'):continue
            shutil.copy2(source,root/'mods'/source.name)
        for name in ['config','defaultconfigs','kubejs']:
            shutil.copytree(REPO/'server'/name,root/name,dirs_exist_ok=True,ignore=shutil.ignore_patterns('logs','client_scripts'))
        towny=root/'plugins/Towny/settings';towny.mkdir(parents=True,exist_ok=True)
        shutil.copy2(REPO/'server/plugins/Towny-0.101.2.0.jar',root/'plugins/Towny-0.101.2.0.jar')
        config=(REPO/'server/plugins/Towny/settings/config.yml').read_text();config=re.sub(r"(using_economy:\s*)['\"]?true['\"]?",r"\1'false'",config);(towny/'config.yml').write_text(config)
        shutil.copy2(REPO/'server/plugins/Towny/settings/townyperms.yml',towny/'townyperms.yml')
    (root/'eula.txt').write_text('eula=true\n')
    settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
    (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
    classes=WORK/'test-classes';classes.mkdir(exist_ok=True)
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));mc=[p for p in libs if p.name=='forge-1.20.1-47.4.23-server.jar'];cp=[ROOT/'dist'/MOD,*mc,*libs]
    compile=['java','com.sun.tools.javac.Main','-source','17','-target','17','--system',str(JDK17),'-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes)]
    subprocess.run([*compile,str(ROOT/'tests/NativeProbe.java')],check=True)
    entries={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_wildlife_test"\nversion="1"\n'
    pack(root/'mods/wildlife-TEST-ONLY.jar',entries)
    spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());boot=[REPO/'server'/p for p in spec['module_path']];legacy=[REPO/'server'/p for p in spec['legacy_classpath']]
    launch=['-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
    (root/'launch.args').write_text('\n'.join('"'+x+'"' for x in launch)+'\n')
    if a.full_pack:launch[0]='-Xmx6G';(root/'launch.args').write_text('\n'.join('"'+x+'"' for x in launch)+'\n')
    log=WORK/('native-full.log' if a.full_pack else 'native.log')
    with log.open('w') as out:result=subprocess.run(['java','@launch.args'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=600)
    text=log.read_text(errors='replace');lines=[v for v in text.splitlines() if 'WILDLIFE_NATIVE_' in v]
    print('\n'.join(lines))
    if result.returncode or not any('WILDLIFE_NATIVE_PASS' in v for v in lines) or any('WILDLIFE_NATIVE_FAIL' in v for v in lines):print(text[-12000:]);raise SystemExit('Native wildlife check failed: '+str(log))
    report=dict(status='passed',marker=lines,sha256={n:sha(ROOT/'dist'/n) for n in [AM,MOD,SPAWN]},citadel_sha256=sha(a.citadel),mod_count=len(list((root/'mods').glob('*.jar'))),graphical_client_tested=False,production_world_tested=False)
    if a.full_pack:report['fixture_changes']=['AntiXray packet filter omitted; no clients in this fixture','Towny economy disabled in fixture; no Vault provider']
    (ROOT/('audit/native-full.json' if a.full_pack else 'audit/native.json')).write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
