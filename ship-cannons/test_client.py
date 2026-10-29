#!/usr/bin/env python3
"""Apply production Mixins and the pinned VS CBC recoil Mixin on both physical sides."""
import os,subprocess,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def run(*args):
    result=subprocess.run(list(map(str,args)))
    if result.returncode:raise SystemExit("Mixin verification failed (exit "+str(result.returncode)+")")
def main():
    b=ROOT/'build';java=Path(os.environ.get('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64'))/'bin/java'
    asm=list(map(str,sorted((ROOT.parent/'server/libraries/org/ow2/asm').rglob('*.jar'))))
    deps=(b/'classpath.txt').read_text().split(os.pathsep)
    output=b/'mixin-test';output.mkdir(exist_ok=True)
    cp=os.pathsep.join([*asm,str(b/'classes'),*deps])
    run(java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',output,ROOT/'tests/CannonsMixinTest.java')
    (output/'vs-cbc-smoke.mixins.json').write_text(json.dumps(dict(required=True,minVersion='0.8',**{'package':'org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons'},compatibilityLevel='JAVA_17',mixins=['MixinCannonContraption'])))
    # Original SRG runtime is tested, matching the shipped JAR rather than the compiler's named view.
    runtime=[str(b/'empirecraft-ship-cannons-1.2.0.jar'),str(b/'compat-srg.jar'),str(b/'runtime-srg.jar'),str(b/'client-srg.jar')]
    tail=[p for p in deps if not Path(p).name.endswith('-named.jar')]
    stripped=b/'compat-without-cbc.jar'
    with zipfile.ZipFile(b/'compat-srg.jar') as source,zipfile.ZipFile(stripped,'w') as target:
        for name in source.namelist():
            if not name.startswith(('rbasamoyai/','ua/empirecraft/cannons/')):target.writestr(name,source.read(name))
    for cbc in [True,False]:
        jars=runtime if cbc else [str(stripped) if p==str(b/'compat-srg.jar') else p for p in runtime]
        for side in ['SERVER','CLIENT']:
            run(java,'-Xmx2G','-cp',os.pathsep.join([*asm,str(output),*jars,*tail]),'ua.empirecraft.shipcannons.test.CannonsMixinTest',side,*([] if cbc else ['without-cbc']))
if __name__=='__main__':main()
