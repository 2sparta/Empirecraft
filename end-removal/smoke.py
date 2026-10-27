#!/usr/bin/env python3
"""Boot a temporary Forge world with original registries, then reopen it with End removal."""
import argparse, os, shutil, subprocess, tempfile, zipfile
from pathlib import Path
from build import ROOT, REPO, MOD, NAMES, archive

def run(command,**kwargs):subprocess.run(list(map(str,command)),check=True,**kwargs)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--toolchain',type=Path,required=True);parser.add_argument('--inputs',type=Path,required=True);parser.add_argument('--launch-args',type=Path)
    args=parser.parse_args();runtime=args.runtime.resolve();tool=args.toolchain.resolve();inputs=args.inputs.resolve();build=ROOT/'build';classes=build/'smoke-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    deps=[tool/'runtime-named.jar',tool/'client-named.jar',*runtime.glob('libraries/**/*.jar')]
    run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',classes,ROOT/'tests/EndSmoke.java'])
    archive(build/'smoke-named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    remapper=os.pathsep.join(map(str,[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*runtime.glob('libraries/org/ow2/asm/**/*.jar')]))
    srg=build/'smoke-srg.jar';srg.unlink(missing_ok=True)
    run(['java','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'smoke-named.jar',srg,tool/'named-srg.tiny','named','srg',tool/'runtime-named.jar',tool/'client-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(srg) as z:files={n:z.read(n) for n in z.namelist()}
    files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_end_test"\nversion="1.0.0"\n'
    files['native.json']=(ROOT/'audit/native.json').read_bytes();archive(build/'smoke.jar',files)
    import json
    with zipfile.ZipFile(inputs/'chipped-forge-1.20.1-3.0.7-empirecraft.2.jar') as source:
        files['legacy.json']=json.dumps(json.loads(source.read('META-INF/empirecraft-trim.json'))['removed']).encode()
    archive(build/'smoke.jar',files)
    with tempfile.TemporaryDirectory(prefix='empirecraft-no-end-') as directory:
        root=Path(directory);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'eula.txt').write_text('eula=true\n')
        (root/'server.properties').write_text('level-name=end-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25594\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
        for prefix in ['architectury-','cloth-config-','resourcefullib-','OELib-']:
            source=next((REPO/'server/mods').glob(prefix+'*.jar'));shutil.copyfile(source,root/'mods'/source.name)
        shutil.copyfile(build/'smoke.jar',root/'mods/smoke.jar')
        for baseline in [True,False]:
            for old,new in NAMES.items():
                (root/'mods'/old).unlink(missing_ok=True);shutil.copyfile(inputs/old if baseline else REPO/'mods'/new,root/'mods'/(old if baseline else new))
            if not baseline:shutil.copyfile(REPO/'mods'/MOD,root/'mods'/MOD)
            log=build/('smoke-original.log' if baseline else 'smoke-removed.log')
            with log.open('w') as output:
                launch='@'+str(args.launch_args.resolve()) if args.launch_args else '@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt'
                result=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xms512M','-Xmx4G','-Dnoend.baseline='+str(baseline).lower(),launch,'nogui'],cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=420)
            text=log.read_text();marker='NO_END_SMOKE_PASS baseline='+str(baseline).lower()
            if result.returncode or marker not in text or 'NO_END_SMOKE_FAIL' in text:raise SystemExit('FAIL '+str(log)+'\n'+text[-12000:])
            print(marker)

if __name__=='__main__':main()
