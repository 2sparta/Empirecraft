#!/usr/bin/env python3
"""Prepare the pinned named Minecraft/Forge API without rebuilding or installing Sails."""
import argparse,hashlib,io,json,os,shutil,subprocess,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent/'sails'
REPO=ROOT.parent
VERSION='0.3.2-empirecraft.19'

def run(args):subprocess.run(list(map(str,args)),check=True)
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def jar(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as out:
        for name,data in sorted(entries.items()):
            entry=zipfile.ZipInfo(name,(2026,10,24,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;out.writestr(entry,data)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-home',type=Path,default=REPO/'server')
    parser.add_argument('--check-inputs',action='store_true')
    parser.add_argument('--sails-only',action='store_true',help='Keep the existing, separately tested VS compatibility JAR')
    args=parser.parse_args();server=args.server_home.resolve()
    build=REPO/'work/armor/sdk';build.mkdir(parents=True,exist_ok=True)
    pinned=json.loads((ROOT/'build-inputs.json').read_text())
    for name,item in pinned['downloads'].items():
        path=build/name
        if not path.exists() or digest(path)!=item['sha256']:
            if args.check_inputs:raise SystemExit('Missing or changed build input: '+name)
            installed=list((server/'libraries').rglob('mcp_config-1.20.1-20230612.114412.zip')) if name=='mcp-config.zip' else []
            if len(installed)==1 and digest(installed[0])==item['sha256']:
                shutil.copyfile(installed[0],path);continue
            temporary=path.with_suffix(path.suffix+'.download')
            try:
                with urllib.request.urlopen(item['url'],timeout=60) as response:temporary.write_bytes(response.read())
            except Exception:
                if not shutil.which('curl'):raise
                run(['curl','-fsSL','--max-time','90','-o',temporary,item['url']])
            if digest(temporary)!=item['sha256']:raise SystemExit('Checksum mismatch: '+name)
            os.replace(temporary,path)
    if args.check_inputs:print('Pinned downloads verified');return
    libraries=server/'libraries';mods=server/'mods'
    def need(name,base=libraries):
        found=list(base.rglob(name))
        if len(found)!=1:raise SystemExit('Expected exactly one installed runtime file: '+name)
        return found[0]
    mc=need('server-1.20.1-20230612.114412-srg.jar')
    forge=need('forge-1.20.1-47.4.23-server.jar')
    universal=need('forge-1.20.1-47.4.23-universal.jar')
    architectury=need('architectury-9.2.14-forge.jar',mods)
    kotlin=need('kotlinforforge-4.12.0-all*.jar',mods)
    srgutils=need('srgutils-0.5.4.jar');asm=list((libraries/'org/ow2/asm').rglob('*.jar'))
    if not asm:raise SystemExit('ASM runtime libraries are missing')
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java')
    javac=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else shutil.which('javac')
    compiler=[javac] if javac else [java,'com.sun.tools.javac.Main']
    tools=build/'tools';tools.mkdir(exist_ok=True)
    run([*compiler,'--release','17','-cp',srgutils,'-d',tools,ROOT/'tools/CreateMappings.java'])
    with zipfile.ZipFile(build/'mcp-config.zip') as z:(build/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
    mapping=build/'named-srg.tiny'
    run([java,'-cp',os.pathsep.join(map(str,[tools,srgutils])),'CreateMappings',build/'client-mappings.txt',build/'joined.tsrg',mapping,build/'client-named.tiny'])
    nested=build/'nested';nested.mkdir(exist_ok=True)
    def unpack(path):
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.endswith('.jar'):
                    dest=nested/Path(name).name;dest.write_bytes(archive.read(name))
    unpack(build/'vs-original.jar');unpack(kotlin)
    for nested_input in list(nested.glob('*mixinextras*.jar')):unpack(nested_input)
    inputs=[mc,forge,universal,build/'vs-original.jar',build/'sails-original.jar',architectury,*sorted(nested.glob('*.jar'))]
    entries={}
    for path in inputs:
        with zipfile.ZipFile(path) as archive:entries.update({n:archive.read(n) for n in archive.namelist() if n.endswith('.class')})
    jar(build/'runtime-srg.jar',entries)
    remapper=os.pathsep.join(map(str,[build/'tiny-remapper.jar',build/'mapping-io.jar',*asm]))
    run([java,'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'runtime-srg.jar',build/'runtime-named.jar',mapping,'srg','named','--ignoreConflicts'])
    client_named=build/'client-named.jar'
    run([java,'-Xmx2G','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'client-original.jar',client_named,build/'client-named.tiny','official','named','--ignoreConflicts'])

    classpath=[build/'runtime-named.jar',build/'client-named.jar',build/'annotations.jar',kotlin,*sorted(libraries.rglob('*.jar')),*sorted(nested.glob('*.jar'))]
    (build/'javac.args').write_text('-cp\n'+os.pathsep.join(map(str,classpath)))
    print('ARMOR_SDK_READY', build)

if __name__=='__main__':main()
