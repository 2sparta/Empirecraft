#!/usr/bin/env python3
"""Run a real Forge server fixture for native whale harvesting and death loot."""
import argparse,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from build import ROOT,REPO,pack

def run(cmd,**kw):subprocess.run(list(map(str,cmd)),check=True,env=dict(os.environ,JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2'),**kw)
def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--toolchain',type=Path,required=True);a=p.parse_args()
    runtime=a.runtime.resolve();tool=a.toolchain.resolve();build=ROOT/'build';classes=build/'test-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    deps=[tool/'runtime-named.jar',tool/'client-named.jar',*runtime.glob('libraries/**/*.jar')]
    run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',classes,ROOT/'tests/WhaleSmoke.java'])
    named=build/'fixture-named.jar';pack(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    remapper=os.pathsep.join(map(str,[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*runtime.glob('libraries/org/ow2/asm/**/*.jar')]))
    srg=build/'fixture-srg.jar';srg.unlink(missing_ok=True)
    run(['java','-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,tool/'named-srg.tiny','named','srg',tool/'runtime-named.jar',tool/'client-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(srg) as z:files={n:z.read(n) for n in z.namelist()}
    files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_whale_test"\nversion="1.0.0"\n'
    fixture=build/'fixture.jar';pack(fixture,files)
    with tempfile.TemporaryDirectory(prefix='empirecraft-whales-') as tmp:
        root=Path(tmp);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True);(root/'mods').mkdir()
        shutil.copyfile(fixture,root/'mods/fixture.jar')
        shutil.copyfile(ROOT/'dist/wonderoussea-1.0.4-empirecraft.1-forge-1.20.1.jar',root/'mods/wonderoussea.jar')
        shutil.copyfile(next((REPO/'mods').glob('geckolib-*.jar')),root/'mods/geckolib.jar')
        shutil.copyfile(ROOT/'whale-loot.json',root/'whale-loot.json')
        (root/'eula.txt').write_text('eula=true\n')
        (root/'server.properties').write_text('level-name=whale-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25593\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
        log=build/'smoke.log'
        with log.open('w') as out:
            proc=subprocess.Popen(['java','-XX:ActiveProcessorCount=2','-Xmx1500M','@'+str(runtime/'libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt'),'nogui'],cwd=root,stdout=out,stderr=subprocess.STDOUT)
            try:proc.wait(timeout=180)
            except subprocess.TimeoutExpired:proc.kill();proc.wait();raise
        if not (root/'smoke-pass.txt').exists():raise AssertionError('Whale smoke failed; inspect '+str(log))
        result=(root/'smoke-pass.txt').read_text();(ROOT/'audit/forge-smoke.txt').write_text(result);print(result)
if __name__=='__main__':main()
