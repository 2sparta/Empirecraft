#!/usr/bin/env python3
"""Apply production client/common mixins to real classes, with and without Better Combat."""
import os,re,shlex,subprocess
from pathlib import Path
import build
def main():
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';folder=build.BUILD
    flags=shlex.split((folder/'javac.args').read_text());deps=flags[flags.index('-cp')+1].split(os.pathsep)
    installed={}
    for path in deps:
        match=re.fullmatch(r'(asm(?:-tree|-commons|-util|-analysis)?)-(\d+(?:\.\d+)*)\.jar',Path(path).name)
        if match:
            version=tuple(map(int,match[2].split('.')))
            if version>installed.get(match[1],((0,),''))[0]:installed[match[1]]=(version,path)
    asm=[path for version,path in installed.values()];classes=folder/'mixin-test';classes.mkdir(exist_ok=True)
    result=subprocess.run([java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join([*asm,*deps]),'-d',str(classes),str(build.REPO/'sails/tests/JourneyMapMixinTest.java'),str(build.ROOT/'tests/WhaleMixinTest.java')],capture_output=True,text=True)
    print(result.stdout+result.stderr,end='')
    if result.returncode:raise SystemExit(result.returncode)
    libraries=[p for p in deps if '/server/libraries/' in p]
    base=[*asm,str(classes),str(folder/build.NAME),str(folder/'runtime-srg.jar'),*libraries]
    better,=(build.REPO/'mods').glob('bettercombat-forge-1.9.0+1.20.1.jar')
    for mode,extra in [('present',[str(better)]),('absent',[])]:
        result=subprocess.run([java,'-cp',os.pathsep.join([*extra,*base]),'com.empirecraft.whalehitboxes.test.WhaleMixinTest',mode],capture_output=True,text=True)
        print(result.stdout+result.stderr,end='')
        if result.returncode:raise SystemExit(result.returncode)
if __name__=='__main__':main()
