#!/usr/bin/env python3
"""Apply actual Sponge Mixins to pinned Supplementaries classes without starting Minecraft/graphics."""
import hashlib,json,os,re,shlex,shutil,subprocess,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent
SHARED=ROOT.parent/'sails'

def main():
    build=ROOT/'build';flags=shlex.split((SHARED/'build/javac.args').read_text());dependencies=flags[flags.index('-cp')+1].split(os.pathsep)
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
    javac=shutil.which('javac');compiler=[javac] if javac else [java,'com.sun.tools.javac.Main']
    classes=build/'mixin-test';classes.mkdir(exist_ok=True)
    # The merged runtime contains old shaded ASM; put the installed Forge ASM first, as Forge does.
    installed={}
    for p in dependencies:
        match=re.fullmatch(r'(asm(?:-tree|-commons|-util|-analysis)?)-(\d+(?:\.\d+)*)\.jar',Path(p).name)
        if match:
            version=tuple(map(int,match[2].split('.')))
            if version>installed.get(match[1],((0,),''))[0]:installed[match[1]]=(version,p)
    if installed.get('asm',((0,),''))[0]<(9,2):raise SystemExit('Installed Forge ASM >=9.2 required for Java 17')
    asm=[p for version,p in installed.values()]
    subprocess.run(list(map(str,[*compiler,'--release','17','-proc:none','-cp',os.pathsep.join(asm+[str(build/'empirecraft-ship-cannons-1.0.1.jar')]+dependencies),'-d',classes,ROOT/'tests/CannonsMixinTest.java'])),check=True)
    common=[*asm,str(classes),str(build/'empirecraft-ship-cannons-1.0.1.jar'),str(build/'mods-srg.jar'),str(build/'runtime-srg.jar') if (build/'runtime-srg.jar').exists() else str(SHARED/'build/runtime-srg.jar'),str(SHARED/'build/client-named.jar'),*dependencies]
    subprocess.run([str(java),'-cp',os.pathsep.join(common),'ua.empirecraft.shipcannons.test.CannonsMixinTest'],check=True)

if __name__=='__main__':main()
