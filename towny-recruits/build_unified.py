#!/usr/bin/env python3
"""Rebuild changed server/common classes; preserve the verified 1.5 client/tactics bytecode.

No Minecraft/Towny/Recruits API copies or stubs are included. The ordinary build.py
can build the complete source when the documented real client dependencies exist.
"""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
CHANGED=['TownyNames','TownyAccess','CommandRouting','TownyCommandCompat','RecruitProvisionView','CommandWords','RecruitmentConfig','RecruitmentState','Recruitment','Militia','RecruitmentCommands','TownyRecruitMenu','Policy','Bridge','Supplies','TownyRecruits','ArmyData','mixin/UnitLimitMixin','mixin/ProvisionOnlyMixin']
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server-home',type=Path,required=True);p.add_argument('--extra-cp',type=Path,action='append',default=[]);a=p.parse_args()
    base=ROOT/'vendor/townyrecruits-1.5.0.jar'
    if hashlib.sha256(base.read_bytes()).hexdigest()!='f62a19efc055e49cbcb0d8b1baed1d71ed0e860f4fc208284510ec7ebbc2f2f9':raise SystemExit('Unexpected 1.5 base JAR')
    jars=sorted(a.server_home.joinpath('libraries').rglob('*.jar'))+sorted(a.server_home.joinpath('mods').glob('*.jar'))
    jars=[x for x in jars if not x.name.startswith(('townyrecruits-','TownyRecruits'))]
    jars+=a.extra_cp
    jars.sort(key=lambda x:(0 if x.name.startswith('forge-1.20.1-') and x.name.endswith('-server.jar') else 1,str(x)))
    jars.insert(0,base)
    classes=ROOT/'build/unified-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
    javac=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else 'javac'
    source=ROOT/'src/main/java/ua/townyrecruits'
    flags=['--release','17','-proc:none','-implicit:none','-sourcepath',str(ROOT/'build/no-source'),'-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*[str(source/(s+'.java')) for s in CHANGED]]
    args=ROOT/'build/unified-javac.args';args.write_text('\n'.join('"'+v.replace('\\','\\\\').replace('"','\\"')+'"' for v in flags))
    subprocess.run([javac,'@'+str(args)],check=True)
    with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    for name in CHANGED:
        prefix='ua/townyrecruits/'+name
        for old in list(files):
            if old==prefix+'.class' or old.startswith(prefix+'$'):del files[old]
    for file in classes.rglob('*.class'):files[file.relative_to(classes).as_posix()]=file.read_bytes()
    resources=ROOT/'src/main/resources'
    for file in resources.rglob('*'):
        if file.is_file():files[file.relative_to(resources).as_posix()]=file.read_bytes()
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: townyrecruits.mixins.json\r\nImplementation-Version: 2.0.1\r\n\r\n'
    dist=ROOT/'dist/2.0.1';dist.mkdir(parents=True,exist_ok=True);out=dist/'TownyRecruits.jar'
    with zipfile.ZipFile(out,'w') as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    out.with_suffix('.jar.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
    (ROOT/'build/unified-classpath.txt').write_text(os.pathsep.join(map(str,[out,*jars])))
    print(out)
if __name__=='__main__':main()
