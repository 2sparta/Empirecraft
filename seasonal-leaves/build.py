#!/usr/bin/env python3
"""Build common/client seasonal code against actual installed Mohist and Embeddium classes."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
VERSION='1.3.3'
BUILD=REPO/'work/seasonal-leaves'
def run(args):subprocess.run(list(map(str,args)),check=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def jar(path,entries,executable=()):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,31,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(0o100755 if name in executable else 0o100644)<<16;z.writestr(info,data)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--server-home',type=Path,default=REPO/'server')
    p.add_argument('--prepare-fixture',action='store_true')
    p.add_argument('--prepare-effects-fixture',action='store_true')
    p.add_argument('--prepare-evergreen-fixture',action='store_true')
    p.add_argument('--embeddium',type=Path)
    a=p.parse_args();server=a.server_home.resolve();libs=server/'libraries';BUILD.mkdir(parents=True,exist_ok=True)
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java')
    compiler=[java,'com.sun.tools.javac.Main']
    def need(pattern,base=libs):
        found=list(base.rglob(pattern))
        if len(found)!=1:raise SystemExit('Expected one '+pattern+' in '+str(base))
        return found[0]
    srgutils=need('srgutils-0.5.4.jar');tools=BUILD/'tools';tools.mkdir(exist_ok=True)
    run([*compiler,'--release','17','-cp',srgutils,'-d',tools,ROOT/'tools/OfflineMappings.java'])
    with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:(BUILD/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    mapping=BUILD/'named-srg.srg'
    run([java,'-cp',os.pathsep.join(map(str,[tools,srgutils])),'OfflineMappings',need('server-1.20.1-20230612.114412-mappings.txt'),BUILD/'joined.tsrg',mapping])
    # The hybrid patched runtime also contains the client classes used here.
    # Supplement the server mapping with these explicit, verified client API names.
    with mapping.open('a') as out:out.write('\n'+(ROOT/'tools/client-api.srg').read_text())
    inputs=[need('server-1.20.1-20230612.114412-srg.jar'),need('forge-1.20.1-47.4.23-server.jar'),need('forge-1.20.1-47.4.23-universal.jar')]
    for row in json.loads((ROOT/'build-inputs.json').read_text()).values():
        file=need(row['file'],server/'mods');assert sha(file)==row['sha256'] and file.stat().st_size==row['size'],'Changed dependency: '+file.name;inputs.append(file)
    runtime_hash=sha(mapping)+''.join(sha(file) for file in inputs)
    deps=sorted(libs.rglob('*.jar'));runtime=BUILD/'runtime-srg.jar';named=BUILD/'runtime-named.jar';stamp=BUILD/'runtime.sha256'
    if not named.exists() or not stamp.exists() or stamp.read_text()!=runtime_hash:
        entries={}
        for file in inputs:
            with zipfile.ZipFile(file) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
        jar(runtime,entries);named.unlink(missing_ok=True)
        run([java,'-Xmx2G','-cp',os.pathsep.join(map(str,[runtime,*deps])),'net.md_5.specialsource.SpecialSource','-i',runtime,'-o',named,'-m',mapping,'-r','-l','--quiet'])
        stamp.write_text(runtime_hash)
    fixture=a.prepare_fixture or a.prepare_effects_fixture or a.prepare_evergreen_fixture
    classes=BUILD/('evergreen-fixture-classes' if a.prepare_evergreen_fixture else 'effects-fixture-classes' if a.prepare_effects_fixture else 'fixture-classes' if fixture else 'classes');shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    embeddium=a.embeddium or need('embeddium-0.3.31+mc1.20.1.jar',server.parent/'mods')
    client_pin=json.loads((ROOT/'client-inputs.json').read_text())['embeddium']
    assert sha(embeddium)==client_pin['sha256'] and embeddium.stat().st_size==client_pin['size'],'Changed Embeddium compile input'
    cp=os.pathsep.join(map(str,[BUILD/'addon-named.jar',named,embeddium,*deps]))
    sources=[ROOT/'tests/EvergreenSmokeMod.java'] if a.prepare_evergreen_fixture else [ROOT/'tests/SeasonEffectsSmokeMod.java'] if a.prepare_effects_fixture else [ROOT/'tests/GrowthSmokeMod.java',ROOT/'tests/PerformanceScenario.java'] if fixture else sorted((ROOT/'src/main/java').rglob('*.java'))
    flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',classes,*sources]
    argfile=BUILD/('fixture-javac.args' if fixture else 'javac.args')
    argfile.write_text('\n'.join('"'+str(x).replace('\\','\\\\').replace('"','\\"')+'"' for x in flags));run([*compiler,'@'+str(argfile)])
    patch_named=BUILD/('evergreen-fixture-named.jar' if a.prepare_evergreen_fixture else 'effects-fixture-named.jar' if a.prepare_effects_fixture else 'fixture-named.jar' if fixture else 'addon-named.jar');patch_srg=BUILD/('evergreen-fixture-srg.jar' if a.prepare_evergreen_fixture else 'effects-fixture-srg.jar' if a.prepare_effects_fixture else 'fixture-srg.jar' if fixture else 'addon-srg.jar')
    jar(patch_named,{str(f.relative_to(classes)):f.read_bytes() for f in classes.rglob('*.class')});patch_srg.unlink(missing_ok=True)
    run([java,'-Xmx1G','-cp',os.pathsep.join(map(str,[patch_named,BUILD/'addon-named.jar',named,*deps])),'net.md_5.specialsource.SpecialSource','-i',patch_named,'-o',patch_srg,'-m',mapping,'-l','--quiet'])
    with zipfile.ZipFile(patch_srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    if fixture:
        entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_growth_test"\nversion="1.0.0"\ndisplayName="TEST ONLY plant growth"\n'
        entries['pack.mcmeta']=b'{"pack":{"pack_format":15,"description":"TEST ONLY plant growth"}}'
        output=BUILD/'empirecraft-growth-test.jar'
        if a.prepare_evergreen_fixture:
            entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'empirecraft_growth_test',b'empirecraft_evergreen_test')
            entries['evergreen-fixture.json']=(ROOT/'src/main/resources/data/empirecraft_seasonal_leaves/tags/blocks/evergreen.json').read_bytes()
            output=BUILD/'empirecraft-evergreen-test.jar'
        if a.prepare_effects_fixture:
            entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'empirecraft_growth_test',b'empirecraft_effects_test')
            output=BUILD/'empirecraft-effects-test.jar'
    else:
        resources=ROOT/'src/main/resources';entries.update({str(f.relative_to(resources)):f.read_bytes() for f in resources.rglob('*') if f.is_file()})
        entries['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
        entries['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_seasonal_vegetation.mixins.json\r\nImplementation-Version: '+VERSION+'\r\n\r\n').encode()
        output=ROOT/'dist'/VERSION/('empirecraft-seasonal-leaves-'+VERSION+'.jar')
    jar(output,entries);output.with_suffix('.jar.sha256').write_text(sha(output)+'  '+output.name+'\n')
    (BUILD/'classpath.txt').write_text(cp)
    print('BUILD_PASS',output.name,sha(output))
if __name__=='__main__':main()
