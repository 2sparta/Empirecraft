#!/usr/bin/env python3
"""Build the Sails 15 overlay using the pinned Sails 10 input and installed Mohist tools.

Unchanged client rendering classes retain the exact tested Sails 10 bytecode.
No downloads, generated game classes, or redistributed Minecraft mappings are needed.
"""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import build

ROOT=build.ROOT;REPO=build.REPO;BUILD=ROOT/'build'
BASE=ROOT/'native-inputs/vs_sails-1.20.1-0.3.2-empirecraft.10-forge.jar'
BASE_SHA='cdab5866bbe878a05f253666a27dc99abc5bc86c69d3664d532171b4e8a78f60'
def run(args):subprocess.run(list(map(str,args)),check=True)
def main():
    BUILD.mkdir(exist_ok=True)
    # The reviewed native-content manifest pins this complete pre-trim Sails 10 input.
    expected=BASE_SHA
    assert build.digest(BASE)==expected,'Changed Sails 10 overlay input'
    libs=REPO/'server/libraries'
    def need(pattern,folder=libs):
        found=list(folder.rglob(pattern));assert len(found)==1,(pattern,len(found));return found[0]
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    compiler=[java,'com.sun.tools.javac.Main']
    tools=BUILD/'offline-tools';tools.mkdir(exist_ok=True)
    srgutils=need('srgutils-0.5.4.jar')
    run([*compiler,'--release','17','-cp',srgutils,'-d',tools,ROOT/'tools/OfflineMappings.java'])
    with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:(BUILD/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    mapping=BUILD/'offline-named-srg.srg'
    run([java,'-cp',os.pathsep.join(map(str,[tools,srgutils])),'OfflineMappings',need('server-1.20.1-20230612.114412-mappings.txt'),BUILD/'joined.tsrg',mapping])
    mods=[BASE,need('valkyrienskies-120-2.4.11-empirecraft.1.jar',REPO/'server/mods'),need('architectury-9.2.14-forge.jar',REPO/'server/mods'),need('kotlinforforge-4.12.0-all*.jar',REPO/'server/mods')]
    nested=BUILD/'nested';nested.mkdir(exist_ok=True)
    for path in mods:
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.endswith('.jar'):(nested/Path(n).name).write_bytes(z.read(n))
    for path in list(nested.glob('*mixinextras*.jar')):
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.endswith('.jar'):(nested/Path(n).name).write_bytes(z.read(n))
    inputs=[need('server-1.20.1-20230612.114412-srg.jar'),need('forge-1.20.1-47.4.23-server.jar'),need('forge-1.20.1-47.4.23-universal.jar'),*mods,*sorted(nested.glob('*.jar'))]
    cp=list(sorted(libs.rglob('*.jar')))
    entries={}
    for path in inputs:
        with zipfile.ZipFile(path) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    runtime=BUILD/'runtime-srg.jar';build.jar(runtime,entries)
    remapper=os.pathsep.join(map(str,[runtime,*cp]))
    named=BUILD/'runtime-named.jar';stamp=BUILD/'offline-runtime.sha256';runtime_hash=build.digest(runtime)+build.digest(mapping)
    if not named.exists() or not stamp.exists() or stamp.read_text()!=runtime_hash:
        named.unlink(missing_ok=True)
        run([java,'-Xmx2G','-cp',remapper,'net.md_5.specialsource.SpecialSource','-i',runtime,'-o',named,'-m',mapping,'-r','-l','--quiet'])
        stamp.write_text(runtime_hash)
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    base=ROOT/'src/main/java/com/quintonc/vs_sails'
    changed=['blocks/entity/BaseHelmBlockEntity.java','items/DedicationBottle.java','ship/BuoyMassMigration.java','ship/SailsShipControl.java','ship/ShipBlockScan.java','ship/ShipMotionSafety.java','util/ShipIsolationCommands.java']
    changed+=['ship/ArcadeSailing.java','ship/SailDrive.java','ship/HullStability.java','ship/WaterContact.java','config/ConfigUtils.java']
    changed+=['ship/WaterSupport.java','ship/SeaMotion.java','ship/ShipSounds.java','ship/SealedCabins.java','mixin/compat/SealedCabinEntityMixin.java']
    changed+=['ship/CabinAirSpace.java','mixin/compat/SealedCabinQueryMixin.java']
    changed+=['ship/AnchorMechanics.java','ship/AnchorTether.java']
    changed+=['ship/ShipFlooding.java','ship/DoorWater.java','ship/FiniteBuckets.java']
    changed+=['mixin/compat/WaterloggedDoorMixin.java','mixin/compat/FiniteTrapdoorMixin.java','mixin/compat/FiniteWaterTickMixin.java','mixin/compat/FiniteWaterloggedMixin.java']
    changed+=[str(p.relative_to(base)) for p in sorted((base/'ship').glob('Flood*.java'))]
    changed+=[str(p.relative_to(base)) for p in sorted((base/'mixin/compat').glob('Ship*Fluid*Mixin.java'))]
    sources=[base/path for path in changed]+sorted((base/'protection').glob('*.java'))+sorted((base/'mixin/compat').glob('*ProtectionMixin.java'))+[base/'mixin/compat/CannonAccessPacketMixin.java',base/'mixin/compat/ShipContainerPacketMixin.java']
    annotations=BUILD/'annotations.jar'
    assert build.digest(annotations)==json.loads((ROOT/'build-inputs.json').read_text())['downloads']['annotations.jar']['sha256']
    classpath=os.pathsep.join(map(str,[named,annotations,*cp,*sorted(nested.glob('*.jar'))]))
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',classes,*sources]
    (BUILD/'javac.args').write_text('\n'.join('"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"' for s in args))
    run([*compiler,'@'+str(BUILD/'javac.args')])
    build.jar(BUILD/'patch-named.jar',{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    patch=BUILD/'patch-srg.jar';patch.unlink(missing_ok=True)
    run([java,'-Xmx2G','-cp',os.pathsep.join(map(str,[BUILD/'patch-named.jar',named,*cp])),'net.md_5.specialsource.SpecialSource','-i',BUILD/'patch-named.jar','-o',patch,'-m',mapping,'-l','--quiet'])
    with zipfile.ZipFile(BASE) as z:entries={n:z.read(n) for n in z.namelist()}
    with zipfile.ZipFile(patch) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    resources=ROOT/'src/main/resources';entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'0.3.2-empirecraft.10',build.VERSION.encode())
    output=BUILD/('vs_sails-1.20.1-'+build.VERSION+'-forge.jar');build.jar(output,entries)
    from native_overlay import apply_native
    output.write_bytes(apply_native(output.read_bytes()))
    for folder in [REPO/'mods',REPO/'server/mods']:
        shutil.copyfile(output,folder/output.name);(folder/(output.name+'.sha256')).write_text(build.digest(output)+'  '+output.name+'\n')
    (BUILD/'offline-build.json').write_text(json.dumps(dict(base_sha256=expected,sources=[str(s.relative_to(REPO)) for s in sources],sha256=build.digest(output)),indent=2)+'\n')
    print('SAILS_OFFLINE_BUILD_PASS',build.digest(output))
if __name__=='__main__':main()
