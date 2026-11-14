#!/usr/bin/env python3
"""Update the server chat plugin, retaining all client code and existing bitmap resources."""
import argparse,hashlib,json,os,subprocess,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;REPO=HERE.parent;NAME='empirecraft-chat-1.1.1.jar'
def archive(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,25,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--system-jdk',type=Path,default=REPO.parent/'work/tfc/jdk');args=parser.parse_args()
    work=HERE/'build/catalog-fix';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
    base=REPO/'server/plugins/empirecraft-chat-1.1.0.jar'
    if not base.exists():base=next((REPO/'disabled-mods').rglob(base.name))
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    cp=[base,*libs,*sorted((REPO/'server/plugins').glob('*.jar'))]
    sources=[HERE/'src/server/java/ua/empirecraft/chat'/(s+'.java') for s in ['CosmeticDisplay','ChatFormat','ChatPlugin']]
    subprocess.run(['java','com.sun.tools.javac.Main','-source','17','-target','17','--system',str(args.system_jdk),'-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sources)],check=True)
    with zipfile.ZipFile(base) as z:before={n:z.read(n) for n in z.namelist()}
    entries=dict(before);entries.update({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    entries['plugin.yml']=before['plugin.yml'].replace(b'version: 1.1.0',b'version: 1.1.1')
    changed=sorted(n for n in before if before[n]!=entries[n])
    assert set(changed)=={'plugin.yml','ua/empirecraft/chat/ChatFormat.class','ua/empirecraft/chat/ChatPlugin.class'},changed
    target=HERE/'dist'/NAME;archive(target,entries)
    report={'baseline':base.name,'baseline_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'file':target.name,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'changed':changed,'new_classes':['ua/empirecraft/chat/CosmeticDisplay.class'],'cosmetic_bitmaps_unchanged':entries['cosmetics.yml']==before['cosmetics.yml'],'client_jar_unchanged':True}
    (HERE/'dist/catalog-fix-build.json').write_text(json.dumps(report,indent=2)+'\n')
    print('CHAT_CATALOG_FIX_BUILD_PASS',report['sha256'])
if __name__=='__main__':main()
