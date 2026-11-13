#!/usr/bin/env python3
"""Stage Sails 19.1 on exact installed Sails 19; preserve every unrelated native class."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
VERSION='0.3.2-empirecraft.19.1'
BASE=ROOT/'native-inputs/vs_sails-1.20.1-0.3.2-empirecraft.19-forge.jar'
SHA='e58aa519c4cf16bb9217244165bbdc84b4930481f59d6e6d23498009ab2f58d1'
def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==SHA
    build=ROOT/'build/hotfix';build.mkdir(parents=True,exist_ok=True);sdk=a.sdk.resolve()
    java=a.java_home/'bin/java';javac=a.java_home/'bin/javac';libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    remapper=os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]))
    def run(args):subprocess.run(list(map(str,args)),check=True)
    deps=[BASE,next((REPO/'mods').glob('valkyrienskies*.jar'))];mapped=[]
    for dep in deps:
        target=build/(dep.stem+'-named.jar');mapped.append(target)
        if target.exists():continue
        run([java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',dep,target,sdk/'named-srg.tiny','srg','named',sdk/'runtime-srg.jar','--ignoreConflicts'])
    nested=REPO/'tfc-integration/build/nested'
    # VS embeds its own API/core; use those exact interfaces in the overlay.
    for dep in [*deps,next((REPO/'mods').glob('kotlinforforge*.jar'))]:
        with zipfile.ZipFile(dep) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    out=build/'nested'/Path(name).name;out.parent.mkdir(exist_ok=True);out.write_bytes(z.read(name))
    cp=[sdk/'runtime-named.jar',sdk/'client-named.jar',*mapped,*libs,*sorted(nested.glob('*.jar')),*sorted((build/'nested').glob('*.jar')),*sorted((REPO/'mods').glob('*.jar'))]
    classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    base=ROOT/'src/main/java/com/quintonc/vs_sails'
    sources=[base/x for x in ['ship/FloodModel.java','ship/ShipFlooding.java','config/ConfigUtils.java','mixin/compat/ShipPassengerSyncMixin.java']]
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sources)]
    (build/'javac.args').write_text('\n'.join('"'+x.replace('\\','\\\\').replace('"','\\"')+'"' for x in args))
    run([javac,'@'+str(build/'javac.args')])
    named=build/'patch-named.jar';srg=build/'patch-srg.jar';srg.unlink(missing_ok=True)
    with zipfile.ZipFile(named,'w') as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
    run([java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,sdk/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'])
    with zipfile.ZipFile(BASE) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    with zipfile.ZipFile(srg) as z:overlay={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    files.update(overlay)
    files['vs_sails-compat.mixins.json']=(ROOT/'src/main/resources/vs_sails-compat.mixins.json').read_bytes()
    files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'0.3.2-empirecraft.19',VERSION.encode())
    target=ROOT/'dist'/('vs_sails-1.20.1-'+VERSION+'-forge.jar');target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target,'w') as z:
        for n,d in sorted(files.items()):
            i=zipfile.ZipInfo(n,(2026,11,12,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,d)
    report={'version':VERSION,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'base_sha256':SHA,'overlaid_classes':sorted(overlay),'unchanged_class_count':sum(n.endswith('.class') and n not in overlay for n in files)}
    (ROOT/'passenger-puddles-build.json').write_text(json.dumps(report,indent=2)+'\n')
    target.with_suffix('.jar.sha256').write_text(report['sha256']+'  '+target.name+'\n')
    print('SAILS_HOTFIX_BUILD_PASS',report['sha256'])
if __name__=='__main__':main()
