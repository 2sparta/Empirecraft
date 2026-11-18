#!/usr/bin/env python3
"""Reproducible build with installed SRG APIs; no Forge SDK download required."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;WORK=REPO/'work/alexs/build'
JDK17=Path(os.environ.get('WILDLIFE_JDK17',REPO.parent/'.empirecraft/jdk-17'))
AM='alexsmobs-1.22.9-empirecraft.1.jar';MOD='empirecraft-wildlife-1.0.0.jar';SPAWN='spawn-4.0.8-1.20.1-empirecraft.2.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):
    with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def pack(p,files):
    p.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(p,'w') as z:
        for n,raw in sorted(files.items()):
            i=zipfile.ZipInfo(n,(2026,11,17,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
    p.with_suffix(p.suffix+'.sha256').write_text(sha(p)+'  '+p.name+'\n')
def refs(v):
    if isinstance(v,str):return {v.removeprefix('#')}
    if isinstance(v,list):return set().union(*(refs(x) for x in v)) if v else set()
    if isinstance(v,dict):return refs(list(v.values()))
    return set()
def main():
    p=argparse.ArgumentParser();p.add_argument('--alexs',type=Path,required=True);a=p.parse_args();WORK.mkdir(parents=True,exist_ok=True)
    source=a.alexs.resolve();pins=json.loads((ROOT/'inputs.json').read_text());assert sha(source)==pins['alexsmobs_sha256']
    policy=json.loads((ROOT/'src/main/resources/wildlife-policy.json').read_text());hidden=set(policy['hidden_items'])
    libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    mc=[p for p in libs if p.name=='forge-1.20.1-47.4.23-server.jar'];cp=[*mc,*libs,*sorted((REPO/'mods').glob('*.jar'))]
    classes=WORK/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    # Share the Earth query implementation in an isolated package, avoiding duplicate runtime classes.
    geo=WORK/'generated/Geography.java';geo.parent.mkdir(exist_ok=True)
    geo.write_text((REPO/'earth-geography/src/main/java/ua/empirecraft/earth/Geography.java').read_text().replace('package ua.empirecraft.earth;','package ua.empirecraft.wildlife;'))
    compiler=['java','com.sun.tools.javac.Main','-source','17','-target','17','--system',str(JDK17),'-proc:none','-encoding','UTF-8']
    args=[*compiler,'-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(geo),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    subprocess.run(args,check=True)
    files={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    res=ROOT/'src/main/resources';files.update({f.relative_to(res).as_posix():f.read_bytes() for f in res.rglob('*') if f.is_file()})
    files['earth-defaults.json']=(REPO/'earth-geography/src/main/resources/earth-defaults.json').read_bytes()
    files['pack.mcmeta']=b'{"pack":{"pack_format":15,"description":"Empirecraft wildlife policy"}}'
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nImplementation-Version: 1.0.0\r\n\r\n'
    dist=ROOT/'dist';pack(dist/MOD,files)
    entries=read(source);changed=[];removed=[]
    for name,raw in list(entries.items()):
        if not name.startswith('data/') or not name.endswith('.json'):continue
        data=json.loads(raw)
        if '/recipes/' in name and refs(data)&hidden:
            del entries[name];removed.append(name)
        elif '/loot_tables/entities/' in name and 'alexsmobs:'+Path(name).stem in policy['removed']:
            entries[name]=b'{"type":"minecraft:entity","pools":[]}'
            changed.append(name)
    # The core mod cannot run without the policy that removes fantasy/duplicate creatures.
    entries['META-INF/mods.toml']+=b'\n[[dependencies.alexsmobs]]\nmodId="empirecraft_wildlife"\nmandatory=true\nversionRange="[1.0.0,)"\nordering="NONE"\nside="BOTH"\n'
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="1.22.9"',b'version="1.22.9-empirecraft.1"')
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    subprocess.run([*compiler,'-cp',os.pathsep.join(map(str,asm)),'-d',str(classes),str(ROOT/'PatchBiome.java')],check=True)
    for simple in ['AMMobSpawnBiomeModifier','FeatureLeafcutterAnthill']:
        key='com/github/alexthe666/alexsmobs/world/'+simple+'.class';before=WORK/(simple+'.class');after=WORK/(simple+'-patched.class');before.write_bytes(entries[key])
        subprocess.run(['java','-cp',os.pathsep.join(map(str,[classes,*asm])),'PatchBiome',str(before),str(after)],check=True);entries[key]=after.read_bytes()
    entries['empirecraft-wildlife.json']=json.dumps(dict(upstream_sha256=sha(source),removed_entities=policy['removed'],removed_recipes=removed),indent=2).encode()
    pack(dist/AM,entries)
    original=next(p for p in [REPO/'mods/spawn-4.0.8-1.20.1-empirecraft.1.jar',REPO/'disabled-mods/alexs-wildlife/mods/spawn-4.0.8-1.20.1-empirecraft.1.jar'] if p.exists());assert sha(original)==pins['spawn_sha256'];entries=read(original)
    ant_resources=[]
    for name,raw in list(entries.items()):
        if not name.startswith('data/') or not name.endswith('.json'):continue
        if name=='data/spawn/forge/biome_modifier/add_small_anthill.json' or ('/recipes/' in name or '/advancements/' in name) and refs(json.loads(raw))&hidden:
            del entries[name];ant_resources.append(name)
        elif name in ['data/spawn/worldgen/configured_feature/anthill.json','data/spawn/worldgen/configured_feature/small_anthill.json']:
            data=json.loads(raw);data['config']['anthill_chance']=0;entries[name]=json.dumps(data,indent=2).encode();ant_resources.append(name)
        elif name=='data/spawn/worldgen/biome/ant_gardens.json':
            data=json.loads(raw);data['features']=[[v for v in group if v!='spawn:anthill'] for group in data['features']];entries[name]=json.dumps(data,indent=2).encode();ant_resources.append(name)
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'4.0.8-1.20.1-empirecraft.1',b'4.0.8-1.20.1-empirecraft.2')
    entries['META-INF/mods.toml']+=b'\n[[dependencies.spawn]]\nmodId="empirecraft_wildlife"\nmandatory=true\nversionRange="[1.0.0,)"\nordering="NONE"\nside="BOTH"\n'
    entries['empirecraft-ant-replacement.json']=json.dumps(dict(replacement='alexsmobs:leafcutter_ant',removed_resources=ant_resources),indent=2).encode()
    pack(dist/SPAWN,entries)
    report=dict(status='built',kept_species=len(policy['animals']),removed_entities=len(policy['removed']),removed_recipes=removed,cleared_loot=changed,spawn_ant_resources=ant_resources,sha256={n:sha(dist/n) for n in [AM,MOD,SPAWN]})
    (ROOT/'audit/build.json').write_text(json.dumps(report,indent=2)+'\n');print('WILDLIFE_BUILD_PASS',report['sha256'])
if __name__=='__main__':main()
