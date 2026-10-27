#!/usr/bin/env python3
"""Start isolated real Forge servers for the upstream and trimmed native JARs."""
import argparse,json,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from build import ROOT,REPO,POLICY,pack,write

ENV=dict(os.environ,JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2')
def run(args,**kwargs):subprocess.run(list(map(str,args)),check=True,env=ENV,**kwargs)

def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--toolchain',type=Path,required=True);p.add_argument('--input',type=Path,required=True);a=p.parse_args()
    runtime=a.runtime.resolve();tool=a.toolchain.resolve();classes=ROOT/'build/fixture-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    deps=[tool/'runtime-named.jar',tool/'client-named.jar',*runtime.glob('libraries/**/*.jar')]
    run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',classes,ROOT/'tests/Smoke.java'])
    named=ROOT/'build/fixture-named.jar';pack(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    srg=ROOT/'build/fixture-srg.jar';srg.unlink(missing_ok=True)
    remapper=os.pathsep.join(map(str,[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*runtime.glob('libraries/org/ow2/asm/**/*.jar')]))
    run(['java','-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,tool/'named-srg.tiny','named','srg',tool/'runtime-named.jar',tool/'client-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(srg) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_overgeared_trim_test"\nversion="1.0.0"\n'
    files['pack.mcmeta']=b'{"pack":{"pack_format":15,"description":"Overgeared native removal integration fixture"}}\n'
    files['data/empirecraft_overgeared_trim_test/recipes/forbidden_external.json']=json.dumps(dict(type='minecraft:crafting_shapeless',ingredients=[dict(item='minecraft:stick')],result=dict(item='overgeared:diamond_shard'))).encode()
    files['data/empirecraft_overgeared_trim_test/recipes/retained_external.json']=json.dumps(dict(type='minecraft:crafting_shapeless',ingredients=[dict(item='minecraft:stick')],result=dict(item='overgeared:steel_ingot'))).encode()
    fixture=ROOT/'build/fixture.jar';pack(fixture,files)
    with tempfile.TemporaryDirectory(prefix='overgeared-trim-') as temporary:
        root=Path(temporary);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True);(root/'mods').mkdir()
        for baseline in [True,False]:
            shutil.copyfile(fixture,root/'mods/fixture.jar')
            shutil.copyfile(a.input if baseline else ROOT/'dist'/POLICY['file'],root/'mods/overgeared.jar')
            for pattern in ['cloth-config-*.jar','architectury-*.jar']:
                for jar in (REPO/'mods').glob(pattern):shutil.copyfile(jar,root/'mods'/jar.name)
            (root/'eula.txt').write_text('eula=true\n')
            (root/'server.properties').write_text('level-name=smoke\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25594\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
            log=ROOT/'build'/('forge-original.log' if baseline else 'forge-trimmed.log')
            with log.open('w') as out:
                result=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xmx3G','-Dtrim.baseline='+str(baseline).lower(),'@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,env=ENV,stdout=out,stderr=subprocess.STDOUT,timeout=240)
            text=log.read_text();marker='OVERGEARED_SMOKE_PASS baseline='+str(baseline).lower()
            if result.returncode or marker not in text or 'OVERGEARED_SMOKE_FAIL' in text:
                details='\n'.join(line for line in text.splitlines() if any(term in line for term in ['external','metadata','pack.mcmeta','OVERGEARED_','AssertionError']))
                raise SystemExit('FAIL '+str(log)+'\n'+details+'\n'+text[-18000:])
            print(marker)
    report=json.loads((ROOT/'audit/build.json').read_text());report['checks']['live_forge']='passed: upstream and trimmed registries, creative tabs, external recipes, retained clay casting and saved-world migration';write(ROOT/'audit/build.json',report)

if __name__=='__main__':main()
