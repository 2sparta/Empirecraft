#!/usr/bin/env python3
"""Apply actual Sponge Mixins to pinned JourneyMap classes without starting Minecraft/graphics."""
import hashlib,json,os,re,shlex,shutil,subprocess,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def main():
    build=ROOT/'build';flags=shlex.split((build/'javac.args').read_text());dependencies=flags[flags.index('-cp')+1].split(os.pathsep)
    pinned=json.loads((ROOT/'build-inputs.json').read_text())['test_inputs']['journeymap-6.0.7']
    journey=build/'journeymap-original.jar'
    if not journey.exists() or hashlib.sha256(journey.read_bytes()).hexdigest()!=pinned['sha256']:
        with urllib.request.urlopen(pinned['url'],timeout=60) as response:content=response.read()
        assert hashlib.sha256(content).hexdigest()==pinned['sha256'];journey.write_bytes(content)
    assert len(journey.read_bytes())==pinned['size']
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
    subprocess.run(list(map(str,[*compiler,'--release','17','-proc:none','-cp',os.pathsep.join(asm+dependencies),'-d',classes,ROOT/'tests/JourneyMapMixinTest.java'])),check=True)
    version='0.3.2-empirecraft.5'
    common=[*asm,str(classes),str(build/('vs_sails-1.20.1-'+version+'-forge.jar')),str(build/'runtime-srg.jar'),str(build/'client-named.jar'),*dependencies]
    for extra,mode in [([str(journey)],'present'),([], 'absent')]:
        command=[str(java),'-cp',os.pathsep.join(extra+common),'com.empirecraft.sails.test.JourneyMapMixinTest',str(build/'mixin-transformed'),mode]
        # Report only the JVM's useful diagnostics, never a many-kilobyte classpath traceback.
        result=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        print(result.stdout,end='')
        if result.returncode:raise SystemExit(result.returncode)

if __name__=='__main__':main()
