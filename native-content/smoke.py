#!/usr/bin/env python3
import argparse,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from build import ROOT,REPO,archive
p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--toolchain',type=Path,required=True);a=p.parse_args()
runtime=a.runtime.resolve();tool=a.toolchain.resolve();build=ROOT/'build';classes=build/'smoke-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
deps=[tool/'runtime-named.jar',tool/'client-named.jar',*runtime.glob('libraries/**/*.jar')]
subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(ROOT/'Smoke.java')],check=True)
(build/'smoke-named.jar').write_bytes(archive({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}))
remapper=os.pathsep.join(map(str,[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*runtime.glob('libraries/org/ow2/asm/**/*.jar')]))
srg=build/'smoke-srg.jar';srg.unlink(missing_ok=True)
subprocess.run(['java','-cp',remapper,'dev.architectury.tinyremapper.Main',str(build/'smoke-named.jar'),str(srg),str(tool/'named-srg.tiny'),'named','srg',str(tool/'runtime-named.jar'),str(tool/'client-named.jar'),'--ignoreConflicts'],check=True)
with zipfile.ZipFile(srg) as z:files={n:z.read(n) for n in z.namelist()}
files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_native_test"\nversion="1.0.0"\n'
files['native-policy.json']=(ROOT/'audit/policy.json').read_bytes();test=archive(files)
with tempfile.TemporaryDirectory(prefix='empirecraft-native-') as d:
 root=Path(d);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True);shutil.copytree(REPO/'server/mods',root/'mods')
 (root/'mods/native-validation.jar').write_bytes(test);(root/'eula.txt').write_text('eula=true\n')
 (root/'server.properties').write_text('level-name=native-test\nlevel-type=minecraft:flat\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25594\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
 log=build/'smoke.log'
 with log.open('w') as out:r=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xms512M','-Xmx6G','@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=540)
 text=log.read_text()
 if r.returncode or 'NATIVE_CONTENT_SMOKE_PASS' not in text or 'NATIVE_CONTENT_SMOKE_FAIL' in text:raise SystemExit('FAIL native Forge launch\n'+text[-16000:])
 print(next(line for line in text.splitlines() if 'NATIVE_CONTENT_SMOKE_PASS' in line))
