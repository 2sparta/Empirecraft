#!/usr/bin/env python3
"""Pure geometry regression and the disposable Minecraft fixture compiler."""
import os,shlex,subprocess,zipfile
from pathlib import Path
import build
def main():
    root=build.ROOT;folder=build.BUILD;java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    flags=shlex.split((folder/'javac.args').read_text());cp=os.pathsep.join([str(folder/'classes'),flags[flags.index('-cp')+1]])
    classes=folder/'test-classes';classes.mkdir(exist_ok=True)
    sources=[root/'tests/ModelBoxTest.java',root/'tests/WhaleHitboxSmoke.java']
    result=subprocess.run([java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],capture_output=True,text=True)
    print(result.stdout+result.stderr,end='')
    if result.returncode:raise SystemExit(result.returncode)
    result=subprocess.run([java,'-ea','-cp',os.pathsep.join([str(classes),cp]),'ModelBoxTest'],capture_output=True,text=True)
    print(result.stdout+result.stderr,end='')
    if result.returncode:raise SystemExit(result.returncode)
    named=folder/'fixture-named.jar';build.jar(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class') if 'com/empirecraft' in p.as_posix()})
    fixture=folder/'empirecraft-whale-hitbox-test.jar';fixture.unlink(missing_ok=True)
    result=subprocess.run([java,'-cp',os.pathsep.join([str(named),cp]),'net.md_5.specialsource.SpecialSource','-i',str(named),'-o',str(fixture),'-m',str(folder/'named-srg.srg'),'-l','--quiet'],capture_output=True,text=True)
    if result.returncode:print(result.stdout+result.stderr);raise SystemExit(result.returncode)
    entries=build.read(fixture)
    entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_whale_hitbox_test"\nversion="1.0.0"\ndisplayName="Whale hitbox test fixture"\n'
    entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\n\r\n'
    build.jar(fixture,entries);print('WHALE_FIXTURE_BUILD_PASS')
if __name__=='__main__':main()
