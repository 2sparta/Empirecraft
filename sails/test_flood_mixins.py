#!/usr/bin/env python3
"""Transform actual Minecraft client/common fluid classes without opening a game window."""
import os,re,shlex,shutil,subprocess
from pathlib import Path
import build

def main():
    root=build.ROOT;folder=root/'build'
    flags=shlex.split((folder/'javac.args').read_text());deps=flags[flags.index('-cp')+1].split(os.pathsep)
    installed={}
    for path in deps:
        match=re.fullmatch(r'(asm(?:-tree|-commons|-util|-analysis)?)-(\d+(?:\.\d+)*)\.jar',Path(path).name)
        if match:
            version=tuple(map(int,match[2].split('.')))
            if version>installed.get(match[1],((0,),''))[0]:installed[match[1]]=(version,path)
    asm=[path for version,path in installed.values()]
    classes=folder/'flood-mixin-test';classes.mkdir(exist_ok=True)
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    javac=str(Path(java).with_name('javac'))
    cp=os.pathsep.join([*asm,*deps])
    subprocess.run([javac,'--release','17','-proc:none','-cp',cp,'-d',str(classes),str(root/'tests/JourneyMapMixinTest.java'),str(root/'tests/FloodMixinTest.java')],check=True)
    embeddium=list((build.REPO/'mods').glob('embeddium-*.jar'))
    assert len(embeddium)==1, 'Test the exact installed Embeddium client'
    cp=os.pathsep.join([*asm,str(classes),str(folder/('vs_sails-1.20.1-'+build.VERSION+'-forge.jar')),str(folder/'runtime-srg.jar'),str(embeddium[0]),*deps])
    result=subprocess.run([java,'-cp',cp,'com.empirecraft.sails.test.FloodMixinTest',str(folder/'flood-transformed')],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(result.stdout,end='')
    if result.returncode:raise SystemExit(result.returncode)

if __name__=='__main__':main()
