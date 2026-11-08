#!/usr/bin/env python3
"""Build the water overlay from pinned Sails 17 and the installed full Mohist runtime.

Mohist's Forge server JAR contains the real client rendering classes. The small
supplementary member map covers the client APIs used by this overlay; unchanged
Sails classes retain the reviewed Sails 17 bytecode.
"""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import build

ROOT=build.ROOT;REPO=build.REPO;BUILD=ROOT/'build'
BASE=ROOT/'native-inputs/vs_sails-1.20.1-0.3.2-empirecraft.17-forge.jar'
BASE_SHA='2c9c0a688728eb0c89569c895075bb518618a8e324e723625c5a10c5ef664978'

def run(args):subprocess.run(list(map(str,args)),check=True)
def main():
    assert build.digest(BASE)==BASE_SHA,'Changed Sails 17 overlay input'
    BUILD.mkdir(exist_ok=True);libs=REPO/'server/libraries'
    def need(pattern,folder=libs):
        found=list(folder.rglob(pattern));assert len(found)==1,(pattern,len(found));return found[0]
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java'))
    compiler=[java,'com.sun.tools.javac.Main'];tools=BUILD/'offline-tools';tools.mkdir(exist_ok=True)
    srgutils=need('srgutils-0.5.4.jar')
    run([*compiler,'--release','17','-cp',srgutils,'-d',tools,ROOT/'tools/OfflineMappings.java'])
    with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:(BUILD/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    mapping=BUILD/'offline-named-srg.srg'
    run([java,'-cp',os.pathsep.join(map(str,[tools,srgutils])),'OfflineMappings',need('server-1.20.1-20230612.114412-mappings.txt'),BUILD/'joined.tsrg',mapping])
    with mapping.open('a') as out:out.write((ROOT/'tools/water-client-members.srg').read_text())
    mods=[BASE,need('valkyrienskies-120-2.4.11-empirecraft.1.jar',REPO/'mods'),need('architectury-9.2.14-forge.jar',REPO/'mods'),need('kotlinforforge-4.12.0-all*.jar',REPO/'mods')]
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
    entries={}
    for path in inputs:
        with zipfile.ZipFile(path) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    runtime=BUILD/'runtime-srg.jar';build.jar(runtime,entries)
    cp=sorted(libs.rglob('*.jar'));remapper=os.pathsep.join(map(str,[runtime,*cp]))
    named=BUILD/'runtime-named.jar';stamp=BUILD/'offline-runtime.sha256';key=build.digest(runtime)+build.digest(mapping)
    if not named.exists() or not stamp.exists() or stamp.read_text()!=key:
        named.unlink(missing_ok=True)
        run([java,'-Xmx2G','-cp',remapper,'net.md_5.specialsource.SpecialSource','-i',runtime,'-o',named,'-m',mapping,'-r','-l','--quiet'])
        stamp.write_text(key)
    compile_api=BUILD/'compile-client-api.jar'
    asm=os.pathsep.join(map(str,sorted((libs/'org/ow2/asm').rglob('*.jar'))))
    run([*compiler,'--release','17','-cp',asm,'-d',tools,ROOT/'tools/CompileClientApi.java'])
    run([java,'-cp',os.pathsep.join([str(tools),asm]),'CompileClientApi',named,compile_api])
    annotations=BUILD/'annotations.jar';pin=json.loads((ROOT/'build-inputs.json').read_text())['downloads']['annotations.jar']
    if not annotations.exists():run(['curl','-fsSL','--max-time','60','-o',annotations,pin['url']])
    assert build.digest(annotations)==pin['sha256'],'Changed annotations input'
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    base=ROOT/'src/main/java/com/quintonc/vs_sails'
    sources=[base/p for p in ['ship/FloodTopology.java','ship/WaterOcclusion.java','client/OceanWaterClip.java','client/FloodWaterRenderer.java','mixin/compat/ShipWaterRenderMixin.java','mixin/compat/ShipOceanGeometryMixin.java','mixin/compat/EmbeddiumShipWaterMixin.java']]
    classpath=os.pathsep.join(map(str,[compile_api,named,annotations,*cp,*sorted(nested.glob('*.jar'))]))
    flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',classes,*sources]
    (BUILD/'javac.args').write_text('\n'.join('"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"' for s in flags))
    run([*compiler,'@'+str(BUILD/'javac.args')])
    build.jar(BUILD/'patch-named.jar',{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    patch=BUILD/'patch-srg.jar';patch.unlink(missing_ok=True)
    run([java,'-Xmx2G','-cp',os.pathsep.join(map(str,[BUILD/'patch-named.jar',named,*cp])),'net.md_5.specialsource.SpecialSource','-i',BUILD/'patch-named.jar','-o',patch,'-m',mapping,'-l','--quiet'])
    with zipfile.ZipFile(BASE) as z:entries={n:z.read(n) for n in z.namelist()}
    with zipfile.ZipFile(patch) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    resources=ROOT/'src/main/resources';entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'0.3.2-empirecraft.17',build.VERSION.encode())
    output=BUILD/('vs_sails-1.20.1-'+build.VERSION+'-forge.jar');build.jar(output,entries)
    from native_overlay import apply_native
    output.write_bytes(apply_native(output.read_bytes()))
    for folder in [REPO/'mods',REPO/'server/mods']:
        shutil.copyfile(output,folder/output.name);(folder/(output.name+'.sha256')).write_text(build.digest(output)+'  '+output.name+'\n')
    (BUILD/'offline-build.json').write_text(json.dumps(dict(base_sha256=BASE_SHA,sources=[str(p.relative_to(REPO)) for p in sources],sha256=build.digest(output)),indent=2)+'\n')
    print('SAILS_OFFLINE_BUILD_PASS',build.digest(output))

if __name__=='__main__':main()
