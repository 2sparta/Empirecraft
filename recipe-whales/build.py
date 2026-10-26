#!/usr/bin/env python3
"""Remove unusable recipes from their own JARs; patch native JEI registration and whale harvesting."""
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
spec = importlib.util.spec_from_file_location('medieval_policy', REPO/'medieval-create/policy.py')
policy = importlib.util.module_from_spec(spec); spec.loader.exec_module(policy)
EXPORTS = ['--add-exports=java.base/jdk.internal.org.objectweb.asm'+s+'=ALL-UNNAMED' for s in ['', '.tree', '.tree.analysis']]
VERSIONS = {'create': ('6.0.8-medieval.2', '6.0.8-medieval.3'), 'wonderoussea': ('1.0.0', '1.0.0-empirecraft.1')}

def sha(raw): return hashlib.sha256(raw).hexdigest()
def blob(raw): return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
def read_jar(path,scan=False):
    with zipfile.ZipFile(path) as z:
        return {n:z.read(n) for n in z.namelist() if not n.endswith('/') and
                (not scan or n.endswith('.json') and any(t in n for t in ['/recipes/','/tags/items/','/advancements/']))}
def pack(path, files):
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_name(path.name+'.tmp')
    with zipfile.ZipFile(temporary,'w') as z:
        for n,raw in sorted(files.items()):
            i=zipfile.ZipInfo(n,(2026,10,26,0,0,0)); i.compress_type=zipfile.ZIP_DEFLATED; i.external_attr=0o100644<<16
            z.writestr(i,raw)
    with zipfile.ZipFile(temporary) as z: assert z.testzip() is None
    with temporary.open('rb') as stream:os.fsync(stream.fileno())
    os.replace(temporary,path)
def write(path, value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def run(args): subprocess.run(list(map(str,args)),check=True)

def profiles(): return json.loads((ROOT/'whale-loot.json').read_text())

def data_json(raw):
    # Minecraft's Gson reader accepts comments/trailing commas used by some installed mods.
    text=raw.decode('utf-8-sig')
    text=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*.*?\*/',lambda m:m[1] or '',text,flags=re.S)
    text=re.sub(r'("(?:\\.|[^"\\])*")|,\s*(?=[}\]])',lambda m:m[1] or '',text)
    return json.loads(text)

def tags_from(archives):
    tags={}
    for files in archives:
        for n,raw in files.items():
            m=re.fullmatch(r'data/([^/]+)/tags/items/(.+)\.json',n)
            if not m: continue
            d=data_json(raw); key=m[1]+':'+m[2]
            if d.get('replace'): tags[key]=[]
            tags.setdefault(key,[]).extend(d.get('values',[]))
    return tags

def forbidden_tag(key,tags,seen=frozenset()):
    if key in seen or key not in tags or not tags[key]: return False
    values=[v if isinstance(v,str) else v.get('id','') for v in tags[key]]
    return all(forbidden_tag(v[1:],tags,seen|{key}) if v.startswith('#') else v.startswith('create:') and not policy.allowed(v) for v in values)

def recipe_reason(value,tags):
    if isinstance(value,list): return next((r for v in value if (r:=recipe_reason(v,tags))),None)
    if not isinstance(value,dict): return None
    typ=value.get('type')
    if isinstance(typ,str) and typ in policy.DISABLED_TYPES: return 'disabled processing type '+typ
    for key,v in value.items():
        if key in {'item','result'} and isinstance(v,str) and v.startswith('create:') and not policy.allowed(v):
            return 'disabled item '+v
        if key=='tag' and isinstance(v,str) and forbidden_tag(v,tags): return 'disabled ingredient tag #'+v
        if isinstance(v,(dict,list)) and (r:=recipe_reason(v,tags)): return r
    if typ=='create:milling':
        for output in value.get('results',[]):
            item=output.get('item','')
            if policy.METAL_OUTPUT.search(item.split(':')[-1]):return 'disabled metal/experience milling'
    return None

def recipe_id(path):
    m=re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json',path)
    return m[1]+':'+m[2] if m else None

def clean(files,tags):
    removed={}
    for n,raw in list(files.items()):
        if not recipe_id(n):continue
        try:d=data_json(raw)
        except ValueError:continue  # Existing malformed, unrelated recipes are audited separately.
        if why:=recipe_reason(d,tags):removed[recipe_id(n)]=why;del files[n]
    return removed

def loot_table(profile):
    # set_count clamps each result to its stack limit. Emit several legal stacks instead.
    pools=[]
    def pool(item,rolls,count):
        pools.append({'rolls':rolls,'entries':[{'type':'minecraft:item','name':item,'functions':[
            {'function':'minecraft:set_count','count':count}]}]})
    for item,(low,high) in profile['loot'].items():
        if low//64:pool(item,low//64,64)
        if low%64:pool(item,1,low%64)
        extra=high-low
        while extra:
            chunk=min(extra,64);pool(item,1,{'type':'minecraft:uniform','min':0,'max':chunk});extra-=chunk
    return {'type':'minecraft:entity','pools':pools}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpath-dir',type=Path,action='append',required=True)
    p.add_argument('--install',action='store_true')
    a=p.parse_args();build=ROOT/'build';shutil.rmtree(build,ignore_errors=True);build.mkdir()
    native_inputs=json.loads((ROOT/'inputs.json').read_text())
    jars={}
    for side in ['mods','server/mods']:
        for path in sorted((REPO/side).glob('*.jar')):
            raw=path.read_bytes();digest=sha(raw)
            if digest not in jars:jars[digest]=dict(paths=[],files=read_jar(path,scan=True),input=path)
            row=jars[digest]
            row['paths'].append(path.relative_to(REPO).as_posix())
    tags=tags_from(j['files'] for j in jars.values())
    cp_jars=[]
    for directory in a.classpath_dir:cp_jars.extend(sorted(directory.rglob('*.jar')))
    cp_jars.sort(key=lambda x:(x.name!='runtime-srg.jar',str(x)))
    classes=build/'classes';tool=build/'tool';classes.mkdir();tool.mkdir()
    run(['java','com.sun.tools.javac.Main',*EXPORTS,'-proc:none','-d',tool,ROOT/'NativePatch.java'])
    run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp_jars)),
         '-d',classes,ROOT/'JeiCategories.java',ROOT/'WhaleHarvest.java'])
    report={'jars':[],'removed_recipes':{},'whale_profiles':profiles(),
            'checks':{'asm_basic_verifier':'passed','graphical_jei':'not run','live_server':'not run'}}
    all_removed=set()
    for row in jars.values():
        files=row['files'];row['removed']=clean(files,tags);all_removed.update(row['removed'])
    # Remove recipe-book advancements only when their recipe has actually been removed.
    for row in jars.values():
        files=row['files'];adv=[]
        for n,raw in list(files.items()):
            if '/advancements/' not in n or not n.endswith('.json'): continue
            d=data_json(raw);refs=set(d.get('rewards',{}).get('recipes',[]))
            for c in d.get('criteria',{}).values():
                v=c.get('conditions',{}).get('recipe');refs.update([v] if isinstance(v,str) else [])
            if refs & all_removed:adv.append(n);del files[n]
        row['removed_advancements']=adv
    for digest,row in jars.items():
        path=row['input'];files=row['files'];name=path.name;kind=None
        if name not in native_inputs and not row['removed'] and not row['removed_advancements']:continue
        original=read_jar(path)
        removed_paths={n for n in original if recipe_id(n) in row['removed']}|set(row['removed_advancements'])
        files={n:raw for n,raw in original.items() if n not in removed_paths}
        if name in native_inputs:
            if digest!=native_inputs[name]:raise ValueError('Unreviewed native input '+name)
            kind='create' if name.startswith('create-') else 'wonderoussea'
            patched=build/(kind+'-patched');patched.mkdir()
            run(['java',*EXPORTS,'-cp',tool,'NativePatch',path,patched])
            files.update({n.relative_to(patched).as_posix():n.read_bytes() for n in patched.rglob('*.class')})
            helper='JeiCategories' if kind=='create' else 'WhaleHarvest'
            helperpath='ua/empirecraft/nativepatch/'+helper+'.class'
            files[helperpath]=(classes/helperpath).read_bytes()
            old,new=VERSIONS[kind]
            meta=files['META-INF/mods.toml'].decode()
            # MCreator stores its version without spaces around '='.
            meta,count=re.subn(r'(?m)^(\s*version\s*=\s*[\"\'])'+re.escape(old)+r'([\"\'])',lambda m:m[1]+new+m[2],meta,count=1)
            if count!=1:raise ValueError('Missing native version '+kind)
            files['META-INF/mods.toml']=meta.encode()
            if kind=='create':name='create-1.20.1-'+new+'.jar'
            else:
                name='wonderoussea-1.0.4-empirecraft.1-forge-1.20.1.jar'
                files['empirecraft/whale-loot.json']=(ROOT/'whale-loot.json').read_bytes()
                for species,profile in profiles().items():
                    if not profile['corpse']:
                        for entity in [species,*profile.get('variants',[])]:
                            files['data/wonderoussea/loot_tables/entities/'+entity+'.json']=json.dumps(loot_table(profile),indent=2).encode()
            if 'META-INF/MANIFEST.MF' in files:
                files['META-INF/MANIFEST.MF']=re.sub(rb'(?m)^Implementation-Version:[^\r\n]*',b'Implementation-Version: '+new.encode(),files['META-INF/MANIFEST.MF'])
        elif not row['removed'] and not row['removed_advancements']:continue
        else:name=path.stem+'-empirecraft-recipes.1.jar'
        for n in list(files):
            if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',n,re.I):del files[n]
        files['META-INF/empirecraft-recipes-whales.json']=json.dumps({'build':1,'upstream_sha256':digest,
            'removed_recipes':row['removed'],'native_patch':kind},indent=2).encode()
        target=ROOT/'dist'/name;pack(target,files);raw=target.read_bytes()
        target.with_suffix('.jar.sha256').write_text(sha(raw)+'  '+name+'\n')
        result={'old_file':path.name,'file':name,'paths':row['paths'],'sha256':sha(raw),'sha512':hashlib.sha512(raw).hexdigest(),
                'git_sha':blob(raw),'size':len(raw),'native_patch':kind,'removed_recipes':row['removed'],'removed_advancements':row['removed_advancements']}
        report['jars'].append(result);report['removed_recipes'].update(row['removed'])
        print('PASS patched',path.name,'recipes removed:',len(row['removed']))
    assert {r['native_patch'] for r in report['jars']} >= {'create','wonderoussea'},'Both native mods must be present'
    write(ROOT/'audit/build.json',report)
    if a.install:install(report)

def install(report):
    spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py')
    adm=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm)
    lock_path=REPO/'modpack-lock.json';lock=json.loads(lock_path.read_text())
    changes={}
    for row in report['jars']:
        jar=ROOT/'dist'/row['file'];raw=jar.read_bytes()
        for rel in row['paths']:
            old=REPO/rel;target=old.with_name(row['file']);target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(jar,target);target.with_suffix('.jar.sha256').write_text(sha(raw)+'  '+target.name+'\n')
            if old!=target:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        with zipfile.ZipFile(jar) as z:
            import tomllib
            meta=tomllib.loads(z.read('META-INF/mods.toml').decode())
            manifest=z.read('META-INF/MANIFEST.MF').decode() if 'META-INF/MANIFEST.MF' in z.namelist() else ''
            impl=re.search(r'(?m)^Implementation-Version:\s*([^\r\n]+)',manifest)
            for mod in meta.get('mods',[]):
                version=mod['version']
                if version=='${file.jarVersion}':
                    if not impl:raise ValueError('No resolved mod version '+mod['modId'])
                    version=impl[1]
                changes[mod['modId']]={'id':mod['modId'],'version':version,'sha256':row['sha256'],'filePresent':True}
                for entry in lock['mods']:
                    if entry['id']==mod['modId']:
                        entry.update({k:row[k] for k in ['file','sha256','sha512','size','git_sha']});entry['version']=version
                        entry['pin_reason']='Native recipe cleanup / whale loot; reproducible sources in recipe-whales/'
    write(lock_path,lock)
    adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    original=adm.parse(adm_path.read_text(encoding='utf-8-sig'))
    new=json.loads(json.dumps(original))
    for row in new['modIds']:
        if row['id'] in changes:row.update(changes[row['id']])
    assert all(row in new['modIds'] for row in original['modIds'] if row['id'] not in changes)
    assert new['resourcePackIds']==original['resourcePackIds']
    new['integrityHash']=adm.integrity(new);adm.parse(json.dumps(new));write(adm_path,new)
    print('PASS installed both sides and preserved ADM entries/resource packs')

if __name__=='__main__':main()
