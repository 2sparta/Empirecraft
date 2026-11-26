#!/usr/bin/env python3
"""Staged build against the pinned repository runtime. Does not install or change lockfiles."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent

def pack(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,17,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--compiler',default='javac');a=p.parse_args()
    b=R/'build';b.mkdir(exist_ok=True);sdk=a.sdk.resolve();libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    api=b/'journeymap-api.jar'
    with zipfile.ZipFile(REPO/'mods/journeymap-forge-1.20.1-6.0.7.jar') as z:api.write_bytes(z.read('META-INF/jarjar/journeymap-api-forge-1.20.1-2.0.0.jar'))
    deps=[REPO/'mods'/name for name in ['TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar','empirecraft-tfc-integration-1.0.9.jar','empirecraft-calendar-1.2.4.jar']]
    cp=[sdk/'runtime-named.jar',sdk/'client-named.jar',api,*deps,*libs]
    for path in cp:assert path.is_file(),path
    classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    args=['-source','17','-target','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sorted((R/'src/main/java').rglob('*.java')))]
    argfile=b/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args)+'\n')
    subprocess.run([a.compiler,'@'+str(argfile)],check=True)
    files={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    res=R/'src/main/resources';files.update({f.relative_to(res).as_posix():f.read_bytes() for f in res.rglob('*') if f.is_file()})
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft-earth.mixins.json\r\nImplementation-Version: 0.1.3\r\n\r\n'
    named=b/'earth-named.jar';out=b/'libs/empirecraft-earth-0.1.3.jar';pack(named,files);out.parent.mkdir(exist_ok=True);out.unlink(missing_ok=True)
    remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))]
    subprocess.run(['java','-Xmx2G','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(named),str(out),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(out) as z:final={name:z.read(name) for name in z.namelist() if not name.endswith('/')}
    pack(out,final)
    out.with_suffix('.jar.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
    pins={str(path.relative_to(REPO)):hashlib.sha256(path.read_bytes()).hexdigest() for path in deps}
    (b/'inputs.json').write_text(json.dumps(pins,indent=2)+'\n');print('STAGED',out)
if __name__=='__main__':main()
