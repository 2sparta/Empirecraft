#!/usr/bin/env python3
"""Build the pinned Overgeared native trim without rebuilding other mods."""
import argparse,copy,hashlib,importlib.util,json,os,re,shutil,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
POLICY=json.loads((ROOT/'policy.json').read_text())
REMOVED=set(POLICY['removed'])
TYPES=set(POLICY['disabled_recipe_types'])
EXPORTS=['--add-exports=java.base/jdk.internal.org.objectweb.asm'+s+'=ALL-UNNAMED' for s in ['', '.tree', '.tree.analysis']]

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def refs(value):
    if isinstance(value,str):return {value}
    if isinstance(value,list):return set().union(*(refs(v) for v in value)) if value else set()
    if isinstance(value,dict):return set().union(*(refs(v) for v in value.values())) if value else set()
    return set()

def clean(entries):
    deleted=[];updated=[];files={}
    for name,raw in entries.items():
        if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',name,re.I):deleted.append(name);continue
        parts=name.split('/')
        ns=parts[1] if len(parts)>2 else ''
        leaf=parts[-1].removesuffix('.json').removesuffix('.png').removesuffix('.png.mcmeta')
        machine_asset=ns=='overgeared' and any(leaf.startswith(t+'_') for t in ['nether_alloy_furnace','casting_furnace'])
        if (ns+':'+leaf in REMOVED or machine_asset) and any(t in name for t in ['/models/','/blockstates/','/textures/','/loot_tables/blocks/']):
            deleted.append(name);continue
        if not name.endswith('.json'):
            files[name]=raw;continue
        try:data=json.loads(raw)
        except ValueError:
            files[name]=raw;continue
        original=copy.deepcopy(data)
        if '/recipes/' in name and isinstance(data,dict):
            if data.get('type') in TYPES or refs(data)&REMOVED:deleted.append(name);continue
        if '/loot_modifiers/' in name and refs(data)&REMOVED:deleted.append(name);continue
        if '/advancements/' in name and (refs(data)&REMOVED or any(t in name for t in ['diamond_arrow','diamond_upgrade','nether_alloy','casting_furnace'])):
            deleted.append(name);continue
        if '/tags/' in name and isinstance(data,dict):
            data['values']=[v for v in data.get('values',[]) if not refs(v)&REMOVED]
        if '/lang/' in name and isinstance(data,dict):
            terms=['nether_alloy_furnace','nether_alloy_smelting','nether_alloy_smelter','diamond_upgrade','netherite_alloy','diamond_arrow','diamond_shard','nether_tool_cast','casting_furnace']
            data={k:v for k,v in data.items() if not any(t in k for t in terms)}
        if data!=original:
            raw=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();updated.append(name)
        files[name]=raw
    # Delete references to removed loot-modifier resources and recipe-book advancements.
    deleted_ids={n.split('/')[1]+':'+n.split('/loot_modifiers/')[1][:-5] for n in deleted if '/loot_modifiers/' in n}
    path='data/forge/loot_modifiers/global_loot_modifiers.json'
    data=json.loads(files[path]);data['entries']=[x for x in data['entries'] if x not in deleted_ids]
    files[path]=(json.dumps(data,indent=2)+'\n').encode();updated.append(path)
    recipes={n.split('/')[1]+':'+n.split('/recipes/')[1][:-5] for n in deleted if '/recipes/' in n}
    for name,raw in list(files.items()):
        if '/advancements/' in name and name.endswith('.json'):
            if refs(json.loads(raw))&recipes:deleted.append(name);del files[name]
    while True:
        advancements={n.split('/')[1]+':'+n.split('/advancements/')[1][:-5] for n in deleted if '/advancements/' in n}
        dependents=[n for n,raw in files.items() if '/advancements/' in n and n.endswith('.json') and refs(json.loads(raw))&advancements]
        if not dependents:break
        for name in dependents:deleted.append(name);del files[name]
    return files,sorted(deleted),sorted(set(updated))

def pack(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,27,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            z.writestr(info,raw)
    with zipfile.ZipFile(path) as z:assert z.testzip() is None
    path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')

def hashes(raw):
    return dict(size=len(raw),sha256=hashlib.sha256(raw).hexdigest(),sha512=hashlib.sha512(raw).hexdigest(),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())

def install(jar):
    raw=jar.read_bytes();digest=hashes(raw)
    for side in ['mods','server/mods']:
        destination=REPO/side/jar.name;destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(jar,destination);shutil.copyfile(jar.with_suffix('.jar.sha256'),destination.with_suffix('.jar.sha256'))
        old=REPO/side/POLICY['upstream_file'];old.unlink(missing_ok=True);old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    lockpath=REPO/'modpack-lock.json';lock=json.loads(lockpath.read_text());before=copy.deepcopy(lock)
    record=next(x for x in lock['mods'] if x['id']=='overgeared')
    record.update(file=jar.name,**digest,source='local:overgeared-trim',pin_reason='Native removal of the requested Overgeared progression branch; reproducible build and Forge registry tests in overgeared-trim/.')
    assert [x for x in lock['mods'] if x['id']!='overgeared']==[x for x in before['mods'] if x['id']!='overgeared']
    write(lockpath,lock)
    inventorypath=REPO/'seasonal-calendar/installed-mods.json';inventory=json.loads(inventorypath.read_text())
    record=next(x for x in inventory if x['name'] in [POLICY['upstream_file'],jar.name]);record.update(name=jar.name,path='server/mods/'+jar.name,size=digest['size'],sha=digest['git_sha'])
    write(inventorypath,inventory)
    coveragepath=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(coveragepath.read_text())
    coverage['jar_sha256'].pop(POLICY['upstream_file'],None);coverage['jar_sha256'][jar.name]=digest['sha256'];write(coveragepath,coverage)
    spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py');adm=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm)
    path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';data=adm.parse(path.read_text(encoding='utf-8-sig'));before=copy.deepcopy(data)
    record=next(x for x in data['modIds'] if x['id']=='overgeared');record.update(sha256=digest['sha256'],filePresent=True)
    data['integrityHash']=adm.integrity(data);adm.parse(json.dumps(data))
    assert data['resourcePackIds']==before['resourcePackIds']
    assert [x for x in data['modIds'] if x['id']!='overgeared']==[x for x in before['modIds'] if x['id']!='overgeared']
    write(path,data)

def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--classpath-dir',type=Path,action='append',required=True);p.add_argument('--install',action='store_true');a=p.parse_args()
    raw=a.input.read_bytes();assert hashlib.sha256(raw).hexdigest()==POLICY['upstream_sha256'],'Unreviewed input'
    build=ROOT/'build';build.mkdir(exist_ok=True);tool=build/'tool';tool.mkdir(exist_ok=True)
    patched=build/'patched';shutil.rmtree(patched,ignore_errors=True)
    subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',str(tool),str(ROOT/'Patch.java')],check=True)
    subprocess.run(['java',*EXPORTS,'-cp',str(tool),'Patch',str(a.input),str(patched)],check=True)
    with zipfile.ZipFile(a.input) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    files,deleted,updated=clean(original)
    files.update({p.relative_to(patched).as_posix():p.read_bytes() for p in patched.rglob('*.class')})
    cp=sorted({p.resolve() for root in a.classpath_dir for p in root.rglob('*.jar')},key=lambda p:(p.name!='runtime-srg.jar',str(p)))
    classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,(ROOT/'src/main/java').rglob('*.java'))],check=True)
    files.update({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    files['overgeared-empirecraft.mixins.json']=(ROOT/'src/main/resources/overgeared-empirecraft.mixins.json').read_bytes()
    # Preserve the original mod version for existing exact-version dependencies and mixin gates.
    manifest=files['META-INF/MANIFEST.MF'].decode();assert 'overgeared-empirecraft.mixins.json' not in manifest
    manifest,count=re.subn(r'(?m)^MixinConfigs: ([^\r\n]+)',lambda m:'MixinConfigs: '+m[1]+',overgeared-empirecraft.mixins.json',manifest)
    if count==0:manifest=manifest.rstrip()+'\r\nMixinConfigs: overgeared-empirecraft.mixins.json\r\n\r\n'
    assert count<=1;files['META-INF/MANIFEST.MF']=manifest.encode()
    files['META-INF/empirecraft-overgeared-trim.json']=(json.dumps(POLICY,indent=2)+'\n').encode()
    target=ROOT/'dist'/POLICY['file'];pack(target,files)
    untouched=[n for n,raw in original.items() if n in files and raw==files[n]]
    report=dict(**hashes(target.read_bytes()),file=target.name,upstream_sha256=POLICY['upstream_sha256'],removed=POLICY['removed'],deleted_resources=deleted,updated_resources=updated,patched_classes=sorted(p.relative_to(patched).as_posix() for p in patched.rglob('*.class')),untouched_files=len(untouched),checks=dict(bytecode='passed',live_forge='not run',graphical_client='not run'))
    write(ROOT/'audit/build.json',report)
    if a.install:install(target)
    print('PASS native Overgeared JAR:',len(deleted),'resources removed;',len(untouched),'files preserved byte-for-byte')

if __name__=='__main__':main()
