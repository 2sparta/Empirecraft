from pathlib import Path
import os,subprocess,shutil
from build_remembered import jar,read
R=Path(__file__).resolve().parent;b=R/'build/remembered';sdk=Path('/workspace/work/tfc/toolchain');jdk=Path('/workspace/work/tfc/jdk/bin')
libs=sorted(Path('/workspace/Empirecraft/server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
cp=[b/'named.jar',sdk/'runtime-named.jar',sdk/'client-named.jar',b/'base-named.jar',*libs]
c=b/'tests';shutil.rmtree(c,ignore_errors=True);c.mkdir()
subprocess.run([str(jdk/'javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(c),str(R/'tests/remembered/SoilProbe.java')],check=True)
jar(b/'test-named.jar',{p.relative_to(c).as_posix():p.read_bytes() for p in c.rglob('*.class')});out=b/'test-srg.jar';out.unlink(missing_ok=True)
subprocess.run([str(jdk/'java'),'-cp',os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(b/'test-named.jar'),str(out),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
f=read(out);f['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_trmt_test"\nversion="2"\ndisplayName="TEST ONLY Soil memory"\n';jar(b/'empirecraft-trmt-test.jar',f)
