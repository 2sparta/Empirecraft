#!/usr/bin/env python3
"""Compile/reobfuscate against the actual installed Sails/Forge APIs, without bundling them."""
import argparse,hashlib,os,shlex,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;SDK=REPO/'sails/build';VERSION='1.1.0'
def run(args):subprocess.run(list(map(str,args)),check=True)
def jar(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--install',action='store_true');args=parser.parse_args()
    if not (SDK/'javac.args').exists():raise SystemExit('Prepare the pinned API with python3 sails/build.py --sails-only first.')
    flags=shlex.split((SDK/'javac.args').read_text());cp=flags[flags.index('-cp')+1]+os.pathsep+str(SDK/'classes')+os.pathsep+str(REPO/'server/mods/empirecraft-ship-physics-1.2.2.jar')
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac');build=ROOT/'build';classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));asmcp=os.pathsep.join(map(str,asm));tools=build/'tools';tools.mkdir(exist_ok=True)
    universal=REPO/'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-universal.jar'
    with zipfile.ZipFile(universal) as z:assert b'public net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier' in z.read('META-INF/accesstransformer.cfg')
    run([javac,'--release','17','-cp',asmcp,'-d',tools,ROOT/'tools/ForgeCompileAccess.java'])
    run([java,'-cp',os.pathsep.join([str(tools),asmcp]),'ForgeCompileAccess',SDK/'runtime-named.jar',build/'forge-access.jar'])
    cp=str(build/'forge-access.jar')+os.pathsep+cp
    sources=sorted((ROOT/'src/main/java').rglob('*.java'));params=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',classes,*sources]
    (build/'javac.args').write_text('\n'.join('"'+str(a).replace('\\','\\\\').replace('"','\\"')+'"' for a in params));run([javac,'@'+str(build/'javac.args')])
    jar(build/'named.jar',{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    run([java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'named.jar',build/'srg.jar',SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar','--ignoreConflicts'])
    with zipfile.ZipFile(build/'srg.jar') as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    resources=ROOT/'src/main/resources';entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMixinConfigs: engagement.mixins.json\n\n'
    target=ROOT/'dist'/('empirecraft-engagement-'+VERSION+'.jar');jar(target,entries);sha=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.jar.sha256').write_text(sha+'  '+target.name+'\n')
    if args.install:
        for side in ('mods','server/mods'):
            shutil.copyfile(target,REPO/side/target.name);shutil.copyfile(target.with_suffix('.jar.sha256'),REPO/side/(target.name+'.sha256'))
    print(target.name,sha)
if __name__=='__main__':main()
