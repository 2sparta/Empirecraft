#!/usr/bin/env python3
"""Build the pinned Accurate Hitboxes overlay using installed Mohist libraries, without downloads."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build'
BASE=ROOT/'native-inputs/accuratehitboxes-neoforge-2.4.2-1.20.1.jar'
BASE_SHA='3463181daf0c64d4de531ef821bfad9215c9e67bef659a85fd4db804f479915f'
VERSION='2.4.2-empirecraft.1';NAME='accuratehitboxes-neoforge-'+VERSION+'-1.20.1.jar'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def jar(path,entries):
    with zipfile.ZipFile(path,'w') as out:
        for name in sorted(entries,key=lambda n:(n!='META-INF/MANIFEST.MF',n)):
            info=zipfile.ZipInfo(name,(2026,11,4,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,entries[name])
def read(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
    assert sha(BASE.read_bytes())==BASE_SHA,'Unreviewed Accurate Hitboxes input'
    BUILD.mkdir(exist_ok=True);libs=REPO/'server/libraries';java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'
    def run(args):subprocess.run(list(map(str,args)),check=True)
    def need(pattern,folder=libs):
        found=list(folder.rglob(pattern));assert len(found)==1,(pattern,len(found));return found[0]
    tools=BUILD/'tools';tools.mkdir(exist_ok=True);srgutils=need('srgutils-0.5.4.jar')
    run([java,'com.sun.tools.javac.Main','--release','17','-cp',srgutils,'-d',tools,REPO/'sails/tools/OfflineMappings.java'])
    with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:(BUILD/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    mapping=BUILD/'named-srg.srg'
    run([java,'-cp',os.pathsep.join(map(str,[tools,srgutils])),'OfflineMappings',need('server-1.20.1-20230612.114412-mappings.txt'),BUILD/'joined.tsrg',mapping])
    dependencies=sorted(libs.rglob('*.jar'));runtime=BUILD/'runtime-srg.jar'
    entries={}
    for path in [need('server-1.20.1-20230612.114412-srg.jar'),need('forge-1.20.1-47.4.23-server.jar'),need('forge-1.20.1-47.4.23-universal.jar')]:
        entries.update({n:v for n,v in read(path).items() if n.endswith('.class')})
    jar(runtime,entries);stamp=BUILD/'runtime.sha256';named=BUILD/'runtime-named.jar';key=sha(runtime.read_bytes())+sha(mapping.read_bytes())
    remapper=os.pathsep.join(map(str,[runtime,*dependencies]))
    if not named.exists() or not stamp.exists() or stamp.read_text()!=key:
        named.unlink(missing_ok=True);run([java,'-Xmx2G','-cp',remapper,'net.md_5.specialsource.SpecialSource','-i',runtime,'-o',named,'-m',mapping,'-r','-l','--quiet']);stamp.write_text(key)
    inputs=[BASE,need('bettercombat-forge-1.9.0+1.20.1.jar',REPO/'mods')]
    compiled=[]
    for source in inputs:
        target=BUILD/(source.stem+'-named.jar');stamp=target.with_suffix('.sha256');key=sha(source.read_bytes())+sha(mapping.read_bytes())
        if not target.exists() or not stamp.exists() or stamp.read_text()!=key:
            target.unlink(missing_ok=True);run([java,'-cp',remapper,'net.md_5.specialsource.SpecialSource','-i',source,'-o',target,'-m',mapping,'-r','-l','--quiet']);stamp.write_text(key)
        compiled.append(target)
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    classpath=os.pathsep.join(map(str,[named,*compiled,*dependencies]))
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',classes,*sorted((ROOT/'src/main/java').rglob('*.java'))]
    (BUILD/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args))
    run([java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args')])
    patch=BUILD/'patch-named.jar';jar(patch,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    srg=BUILD/'patch-srg.jar';srg.unlink(missing_ok=True)
    run([java,'-cp',os.pathsep.join(map(str,[patch,*compiled,named,*dependencies])),'net.md_5.specialsource.SpecialSource','-i',patch,'-o',srg,'-m',mapping,'-l','--quiet'])
    asm=[p for p in dependencies if '/org/ow2/asm/' in str(p)];run([java,'com.sun.tools.javac.Main','--release','17','-cp',os.pathsep.join(map(str,asm)),'-d',tools,ROOT/'tools/PatchQueries.java'])
    original=read(BASE);entries=dict(original)
    for target in ['net/devra/accuratehitboxes/mixin/ProjectileUtilMixin.class','net/devra/accuratehitboxes/mixin/client/GameRendererMixin.class']:
        before=BUILD/'before.class';after=BUILD/'after.class';before.write_bytes(entries[target])
        run([java,'-cp',os.pathsep.join(map(str,[tools,*asm])),'PatchQueries',before,after]);entries[target]=after.read_bytes()
    entries.update(read(srg));entries.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="2.4.2"',('version="'+VERSION+'"').encode(),1)
    manifest=entries['META-INF/MANIFEST.MF'].decode().replace('\r\n ','').replace('\n ','')
    lines=manifest.splitlines();updated=[]
    for line in lines:
        if line.startswith('MixinConfigs:'):line+=',accuratehitboxes.empirecraft.mixins.json'
        while len(line)>70:updated.append(line[:70]);line=' '+line[70:]
        updated.append(line)
    entries['META-INF/MANIFEST.MF']=('\r\n'.join(updated).rstrip()+'\r\n\r\n').encode()
    entries['META-INF/empirecraft-whale-hitboxes.json']=(json.dumps({'upstream_sha256':BASE_SHA,'version':VERSION},sort_keys=True)+'\n').encode()
    output=BUILD/NAME;jar(output,entries);raw=output.read_bytes()
    assert not any('Smoke' in n or n.startswith(('net/minecraft/','net/bettercombat/')) for n in entries)
    for folder in [REPO/'mods',REPO/'server/mods']:
        (folder/NAME).write_bytes(raw);(folder/(NAME+'.sha256')).write_text(sha(raw)+'  '+NAME+'\n')
    (BUILD/'build.json').write_text(json.dumps({'version':VERSION,'file':NAME,'base_sha256':BASE_SHA,'sha256':sha(raw),'size':len(raw),
        'changed_upstream_entries':[n for n in original if original[n]!=entries[n]],'added_entries':sorted(entries.keys()-original.keys())},indent=2)+'\n')
    print('WHALE_HITBOX_BUILD_PASS',sha(raw))
if __name__=='__main__':main()
