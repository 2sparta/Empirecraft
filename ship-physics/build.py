#!/usr/bin/env python3
"""Compile the collision overlay against pinned Ship Physics 1.2.0 and the installed runtime.

Run sails/build_offline.py first. Unchanged Ship Physics classes retain their original bytecode.
"""
import hashlib, os, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
BUILD=ROOT/'build'
BASE=ROOT/'native-inputs/empirecraft-ship-physics-1.2.0.jar'
BASE_SHA='0a864ef88e62651804c428f5c5190d93bd03186a6808f94a7ec9eb070413e979'
VERSION='1.2.2'
sys.path.insert(0,str(REPO/'sails'))
from build import jar, digest

def run(args): subprocess.run(list(map(str,args)),check=True)
def main():
    assert digest(BASE)==BASE_SHA, 'Changed Ship Physics overlay input'
    BUILD.mkdir(exist_ok=True)
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    sails=REPO/'sails/build'
    libraries=sorted((REPO/'server/libraries').rglob('*.jar'))
    mapping=sails/'offline-named-srg.srg'
    cp=[BASE,sails/'runtime-srg.jar',*libraries,*sorted((sails/'nested').glob('*.jar'))]
    named=BUILD/'physics-named.jar'; named.unlink(missing_ok=True)
    run([java,'-cp',os.pathsep.join(map(str,cp)),'net.md_5.specialsource.SpecialSource',
         '-i',BASE,'-o',named,'-m',mapping,'-r','-l','--quiet'])
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    cp=[named,sails/'runtime-named.jar',sails/'annotations.jar',*libraries,*sorted((sails/'nested').glob('*.jar'))]
    sources=sorted((ROOT/'src/main/java').rglob('*.java'))
    flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sources]
    (BUILD/'javac.args').write_text('\n'.join('"'+str(x)+'"' for x in flags))
    run([java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args')])
    patch=BUILD/'patch-named.jar'
    jar(patch,{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    srg=BUILD/'patch-srg.jar';srg.unlink(missing_ok=True)
    run([java,'-cp',os.pathsep.join(map(str,[patch,*cp])),'net.md_5.specialsource.SpecialSource',
         '-i',patch,'-o',srg,'-m',mapping,'-l','--quiet'])
    with zipfile.ZipFile(BASE) as z:entries={n:z.read(n) for n in z.namelist()}
    with zipfile.ZipFile(srg) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    # VS AABB maxima are exclusive: migration must not read the next chunk or ship.
    asm=[p for p in libraries if '/org/ow2/asm/' in str(p)]
    tools=BUILD/'tools';tools.mkdir(exist_ok=True)
    run([java,'com.sun.tools.javac.Main','--release','17','-cp',os.pathsep.join(map(str,asm)),
         '-d',tools,ROOT/'tools/PatchMassBounds.java'])
    job='ua/empirecraft/shipphysics/MassSync$Job.class'
    original_job=BUILD/'mass-job-original.class';original_job.write_bytes(entries[job])
    patched_job=BUILD/'mass-job-patched.class'
    run([java,'-cp',os.pathsep.join(map(str,[tools,*asm])),'PatchMassBounds',original_job,patched_job])
    entries[job]=patched_job.read_bytes()
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="1.2.0"',('version="'+VERSION+'"').encode())
    # Keep the public version constant consistent without changing unrelated code.
    entries['ua/empirecraft/shipphysics/ShipPhysics.class']=entries['ua/empirecraft/shipphysics/ShipPhysics.class'].replace(b'1.2.0',b'1.2.2')
    output=BUILD/('empirecraft-ship-physics-'+VERSION+'.jar');jar(output,entries)
    for folder in [REPO/'mods',REPO/'server/mods']:
        shutil.copyfile(output,folder/output.name)
        (folder/(output.name+'.sha256')).write_text(digest(output)+'  '+output.name+'\n')
    print('SHIP_PHYSICS_BUILD_PASS',digest(output))

if __name__=='__main__': main()
