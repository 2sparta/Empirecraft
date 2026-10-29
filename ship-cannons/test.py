#!/usr/bin/env python3
"""Build the pinned mod and exercise recoil, ship-frame pellet rays, and Sponge hooks."""
import argparse,hashlib,json,os,subprocess,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def run(*args):
    result=subprocess.run(list(map(str,args)))
    if result.returncode:raise SystemExit("Verification failed (exit "+str(result.returncode)+")")
def main():
    p=argparse.ArgumentParser();p.add_argument('--no-build',action='store_true');a=p.parse_args()
    java=Path(os.environ.get('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64'))/'bin/java';b=ROOT/'build'
    if not a.no_build:run('python3',ROOT/'build.py')
    libs=[]
    for item in json.loads((ROOT/'tests/adapter-inputs.json').read_text()):
        path=b/'test-tools'/item['file'];path.parent.mkdir(exist_ok=True)
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:
            data=urllib.request.urlopen(item['url'],timeout=30).read()
            if hashlib.sha256(data).hexdigest()!=item['sha256']:raise SystemExit('Changed test library: '+item['file'])
            path.write_bytes(data)
        libs.append(str(path))
    asm=list(map(str,sorted((ROOT.parent/'server/libraries/org/ow2/asm').rglob('*.jar'))))
    linkage=b/'linkage-classes';linkage.mkdir(exist_ok=True)
    run(java,'com.sun.tools.javac.Main','--release','17','-cp',os.pathsep.join(asm),'-d',linkage,ROOT/'tests/RuntimeLinkageTest.java')
    variants=sorted((ROOT.parent/'mods').glob('createbigcannons-5.11.4-mc.1.20.1-forge-medieval.*.jar'))
    assert variants,'Missing real CBC runtime'
    for variant in variants:
        for mode,production in [('broken',b/'regression-1.2.0.jar'),('fixed',b/'empirecraft-ship-cannons-1.2.1.jar')]:
            if production.exists():
                run(java,'-ea','-cp',os.pathsep.join([str(linkage),*asm]),'ua.empirecraft.shipcannons.test.RuntimeLinkageTest',mode,production,variant,ROOT.parent/'server/libraries',ROOT.parent/'mods')
    out=b/'adapter-classes';out.mkdir(exist_ok=True)
    run(java,'com.sun.tools.javac.Main','--release','17','-cp',os.pathsep.join(asm),'-d',out,ROOT/'tests/TestEntityOverlay.java')
    run(java,'-cp',os.pathsep.join([str(out),*asm]),'ua.empirecraft.shipcannons.test.TestEntityOverlay',b/'client-named.jar',b/'test-entity-overlay.jar')
    cp=os.pathsep.join([*asm,str(b/'classes'),str(b/'test-entity-overlay.jar'),str(b/'client-named.jar'),(b/'classpath.txt').read_text(),*libs])
    sources=[ROOT/'tests/RecoilMathTest.java',ROOT/'tests/CannonPhysicsAdapterTest.java',ROOT/'tests/fixtures/FMLEnvironment.java']
    run(java,'com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',out,*sources)
    for test in ['RecoilMathTest','CannonPhysicsAdapterTest']:
        run(java,'-javaagent:'+str(b/'test-tools/byte-buddy-agent-1.12.19.jar'),'-Xmx2G','-ea','-cp',str(out)+os.pathsep+cp,'ua.empirecraft.shipcannons.test.'+test)
    run('python3',ROOT/'test_client.py')
if __name__=='__main__':main()
