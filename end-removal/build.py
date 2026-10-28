#!/usr/bin/env python3
"""Patch the installed native mods and compile the small Forge guard with JDK 17."""
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
from audit import ROOT, REPO, strings, write
from policy import CHIPPED_FAMILIES, CONTACT, KNIVES

VERSION='1.0.0'
MOD='empirecraft-no-end-'+VERSION+'.jar'
NAMES={
 'chipped-forge-1.20.1-3.0.7-empirecraft.2.jar':'chipped-forge-1.20.1-3.0.7-empirecraft.3.jar',
 'Contact-forge-1.2.3.jar':'Contact-forge-1.2.3-empirecraft.1.jar',
 'FarmersDelight-1.20.1-1.2.11a-empirecraft-recipes.1.jar':'FarmersDelight-1.20.1-1.2.11a-empirecraft-no-end.1.jar',
}
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm'+suffix+'=ALL-UNNAMED' for suffix in ['', '.tree', '.tree.analysis']]

def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,27,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    with zipfile.ZipFile(path) as z:assert z.testzip() is None
    path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')

def trim(entries,removed,tags):
    result={};deleted=[]
    for name,raw in entries.items():
        if name.endswith('/') or re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',name,re.I):continue
        parts=name.split('/');identity=parts[1]+':'+parts[-1].removesuffix('.json') if len(parts)>2 else ''
        if name.endswith('.json') and identity in removed and any(segment in name for segment in ['/models/item/','/models/block/','/blockstates/','/loot_tables/blocks/']):deleted.append(name);continue
        if name.endswith('.json'):
            try:data=json.loads(raw)
            except ValueError:result[name]=raw;continue
            original=data
            if '/recipes/' in name or '/advancements/' in name:
                if strings(data)&removed:deleted.append(name);continue
                if isinstance(data,dict) and isinstance(data.get('tags'),list):data=dict(data,tags=[t for t in data['tags'] if t not in tags])
            if '/tags/' in name and isinstance(data,dict):data=dict(data,values=[v for v in data.get('values',[]) if not strings(v)&(removed|{'#'+t for t in tags})])
            if '/lang/' in name and isinstance(data,dict):data={k:v for k,v in data.items() if k not in {kind+'.'+i.replace(':','.') for i in removed for kind in ['item','block']}}
            if data!=original:raw=(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()
        result[name]=raw
    return result,deleted

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--inputs',type=Path,required=True);parser.add_argument('--classpath-dir',action='append',type=Path,required=True)
    args=parser.parse_args();build=ROOT/'build';build.mkdir(exist_ok=True);pins=json.loads((ROOT/'inputs.json').read_text())
    policy=json.loads((ROOT/'src/main/resources/no-end.json').read_text());blocked=set(policy['replacements'])
    native_removed={};reports={}
    subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(build),str(ROOT/'NativePatch.java')],check=True)
    # --release forbids exports of JDK internals; the patcher itself is only a build-time JDK tool.
    for old,new in NAMES.items():
        source=args.inputs/old;raw=source.read_bytes();assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==pins['native'][old]
        with zipfile.ZipFile(source) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
        if old.startswith('chipped-forge'):
            removed={i for i in blocked if i.startswith('chipped:')};families=set()
            for family in CHIPPED_FAMILIES:families.update(json.loads(entries['data/chipped/tags/blocks/'+family+'.json'])['values'])
            mixed=sorted(i.split(':',1)[1] for i in removed-families);(build/'mixed.txt').write_text('\n'.join(mixed)+'\n')
            target='earth/terrarium/chipped/common/registry/ModBlocks.class';kind='chipped'
        elif old.startswith('Contact'):
            removed=set(CONTACT);target='com/flechazo/contact/common/registry/ItemRegistry.class';kind='contact'
        elif old.startswith('Farmers'):
            removed=set(KNIVES);target='vectorwing/farmersdelight/common/registry/ModItems.class';kind='farmer'
        else:
            path='assets/chippedexttras/generated/registry.json';rows=json.loads(entries[path]);kept=[];removed=set()
            for row in rows:
                if row['base'] in blocked:removed.update(v for k,v in row.items() if k!='base' and v)
                else:kept.append(row)
            entries[path]=(json.dumps(kept,ensure_ascii=False,separators=(',',':'))+'\n').encode();target=None
        if target:
            output=build/(kind+'.class');subprocess.run(['java',*EXPORTS,'-cp',str(build),'NativePatch',str(source),target,kind,str(output),str(build/'mixed.txt')],check=True);entries[target]=output.read_bytes()
        if kind=='contact':
            target='com/flechazo/contact/common/registry/ModCreativeTabRegistry.class';output=build/'contact-tab.class'
            subprocess.run(['java',*EXPORTS,'-cp',str(build),'NativePatch',str(source),target,'contact-tab',str(output),str(build/'mixed.txt')],check=True);entries[target]=output.read_bytes()
        entries,deleted=trim(entries,removed,{'chipped:'+f for f in CHIPPED_FAMILIES})
        entries['META-INF/empirecraft-no-end.json']=(json.dumps({'input_sha256':hashlib.sha256(raw).hexdigest(),'removed':sorted(removed)},sort_keys=True)+'\n').encode()
        for side in ['mods','server/mods']:
            archive(REPO/side/new,entries)
            for name in [old]:
                (REPO/side/name).unlink(missing_ok=True);(REPO/side/(name+'.sha256')).unlink(missing_ok=True)
        native_removed[new]=sorted(removed);reports[new]={'sha256':hashlib.sha256((REPO/'mods'/new).read_bytes()).hexdigest(),'removed_ids':len(removed),'deleted_resources':len(deleted)}
    jars=sorted({p.resolve() for root in args.classpath_dir for p in root.rglob('*.jar')},key=lambda p:(p.name!='runtime-srg.jar',str(p)))
    classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*map(str,(ROOT/'src/main/java').rglob('*.java'))],check=True)
    files={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    files.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_no_end.mixins.json\r\n\r\n'
    for side in ['mods','server/mods']:archive(REPO/side/MOD,files)
    write(ROOT/'audit/native.json',native_removed);write(ROOT/'audit/build.json',{'version':VERSION,'native':reports,'guard_sha256':hashlib.sha256((REPO/'mods'/MOD).read_bytes()).hexdigest()})
    print('PASS three native exclusions and identical client/server End guard')

if __name__=='__main__':main()
