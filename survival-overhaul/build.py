#!/usr/bin/env python3
"""Reproducible fork of the pinned uploaded Forge binary using the real Minecraft API."""
import hashlib,json,os,shlex,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build'
NAME='legendarysurvivaloverhaul-1.20.1-2.4.7-empirecraft.1.jar'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,data in sorted(files.items(),key=lambda x:(x[0]!='META-INF/MANIFEST.MF',x[0])):
            info=zipfile.ZipInfo(name,(2026,11,9,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
def main():
    BUILD.mkdir(parents=True,exist_ok=True);pin=json.loads((ROOT/'sources.json').read_text());source=ROOT/'native-inputs'/pin['input'];assert sha(source)==pin['sha256']
    java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';javac=java.with_name('javac')
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));asmcp=os.pathsep.join(map(str,asm));remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
    named=BUILD/'lso-named.jar'
    if not named.exists():subprocess.run([str(java),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(source),str(named),str(SDK/'named-srg.tiny'),'srg','named',str(SDK/'runtime-srg.jar'),'--ignoreConflicts'],check=True)
    flags=shlex.split((SDK/'javac.args').read_text());api=flags[flags.index('-cp')+1]
    cp=os.pathsep.join(map(str,[SDK/'runtime-named.jar',SDK/'client-named.jar',named,REPO/'mods/empirecraft-layered-armor-1.0.0.jar']))+os.pathsep+api
    classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    arguments=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))];argfile=BUILD/'compile.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in arguments))
    subprocess.run([str(javac),'@'+str(argfile)],check=True)
    archive(BUILD/'hooks-named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    subprocess.run([str(java),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(BUILD/'hooks-named.jar'),str(BUILD/'hooks-srg.jar'),str(SDK/'named-srg.tiny'),'named','srg',*cp.split(os.pathsep),'--ignoreConflicts'],check=True)
    tools=BUILD/'tools';tools.mkdir(exist_ok=True);subprocess.run([str(javac),'-cp',asmcp,'-d',str(tools),str(ROOT/'Patch.java')],check=True)
    patched=BUILD/'patched';shutil.rmtree(patched,ignore_errors=True);patched.mkdir()
    result=subprocess.run([str(java),'-cp',str(tools)+os.pathsep+asmcp,'Patch',str(source),str(patched)],check=True,capture_output=True,text=True);(BUILD/'patch.log').write_text(result.stdout);print(result.stdout)
    with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    with zipfile.ZipFile(BUILD/'hooks-srg.jar') as z:files.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    files.update({p.relative_to(patched).as_posix():p.read_bytes() for p in patched.rglob('*.class')})
    import resources
    files=resources.adapt(files)
    files['LICENSE-LSO.txt']=(ROOT/'LICENSE-LSO.txt').read_bytes();files['EMPIRECRAFT-SOURCE.txt']=b'Empirecraft adaptation: full patch sources, pinned input and license are in survival-overhaul/ of https://github.com/2sparta/Empirecraft. Vanilla health, physical inner linings and modpack thirst data.\n'
    target=ROOT/'dist'/NAME;archive(target,files);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+NAME+'\n')
    print('SURVIVAL_BUILD_PASS',sha(target))
if __name__=='__main__':main()
