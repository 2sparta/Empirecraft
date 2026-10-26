#!/usr/bin/env python3
"""Rebuild the pinned Spawn JAR with native registry and data removals."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
VERSION='4.0.8-1.20.1-empirecraft.1'
NAME='spawn-'+VERSION+'.jar'
ORIGINAL='spawn-4.0.8-1.20.1.jar'
INPUT_SHA256='8cd18f3c029fddfeec28314ab9bb4845ff9061131e9f1dce149778e5ee731d23'
BLOCKS=set('sunstone moonstone heart_of_the_sun heliostone heliostone_wall heliostone_slab heliostone_stairs unstable_heliostone magmatic_splatter blasted_sand blasted_sandstone chiseled_blasted_sandstone blasted_sandstone_wall blasted_sandstone_slab blasted_sandstone_stairs cut_blasted_sandstone cut_blasted_sandstone_slab smooth_blasted_sandstone smooth_blasted_sandstone_slab smooth_blasted_sandstone_stairs zombified_flower barnacle barnacle_cluster'.split())
ITEMS=BLOCKS|set('blue_footed_boots firekeeper_spawn_egg stranded_spawn_egg flukeshroom_spawn_egg barbed_spawn_egg stickbug_spawn_egg baby_flukeshroom_bucket suspicious_stick'.split())
ENTITIES=set('stranded flukeshroom barbed barb stickbug firekeeper'.split())
EXTRA=set('volcanic_island volcanic_island_piece add_barbed add_flukeshroom volcanic_island_boulder flower_volcanic_island patch_zombified_flower stranded_spawns discover_stickbug'.split())
BANNED={'spawn:'+i for i in ITEMS|ENTITIES|EXTRA}
def run(args):subprocess.run(list(map(str,args)),check=True)
def compiler():return [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
def jar(path,entries):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as out:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,10,26,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,data)
def dump(data):return (json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
DROP=object()
def scrub(value):
    if isinstance(value,str):return DROP if value.removeprefix('#') in BANNED else value
    if isinstance(value,list):return [changed for v in value if (changed:=scrub(v)) is not DROP]
    if isinstance(value,dict):
        # Named entries/biome spawns/block states are indivisible; remove the whole entry.
        if any(isinstance(v,str) and v.removeprefix('#') in BANNED for v in value.values()):return DROP
        return {k:changed for k,v in value.items() if (changed:=scrub(v)) is not DROP}
    return value
def references(data):
    if isinstance(data,str):return {data.removeprefix('#')}
    if isinstance(data,list):return set().union(*(references(v) for v in data)) if data else set()
    if isinstance(data,dict):return references(list(data.values()))
    return set()
def main():
    build=ROOT/'build';build.mkdir(exist_ok=True)
    original=build/ORIGINAL
    if not original.exists():
        source=REPO/'server/mods'/ORIGINAL
        if not source.exists():raise SystemExit('Restore the pinned upstream Spawn JAR to spawn-native/build/'+ORIGINAL)
        shutil.copyfile(source,original)
    assert hashlib.sha256(original.read_bytes()).hexdigest()==INPUT_SHA256,'Unexpected Spawn input'
    libraries=REPO/'server/libraries';asm=sorted((libraries/'org/ow2/asm').rglob('*.jar'))
    tools=build/'tools';tools.mkdir(exist_ok=True)
    cp=os.pathsep.join(map(str,asm));run([*compiler(),'-proc:none','--release','17','-cp',cp,'-d',tools,ROOT/'tools/PatchSpawn.java'])
    hierarchy=[original,*sorted(libraries.rglob('*.jar')),*sorted((REPO/'server/mods').glob('*.jar'))]
    client=REPO/'sails/build/client-named.jar'
    if client.exists() and not os.environ.get('SPAWN_SKIP_CLIENT'):hierarchy.append(client)
    paths=build/'hierarchy.txt';paths.write_text('\n'.join(map(str,hierarchy)))
    patched=build/'patched';shutil.rmtree(patched,ignore_errors=True);patched.mkdir()
    run(['java','-Xmx2G','-cp',str(tools)+os.pathsep+cp,'PatchSpawn',original,patched,paths])
    with zipfile.ZipFile(original) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    removed=[];changed=[]
    for name,raw in list(entries.items()):
        if not name.startswith('data/') or not name.endswith('.json'):continue
        parts=name.split('/');own='spawn:'+Path(name).stem
        retired=own in BANNED or any('/animal_variants/'+e+'/' in name for e in ENTITIES) or name=='data/spawn/animal_variants/dodo/magmatic.json' or name.startswith('data/spawn/hullback_dirt/barnacle_')
        data=json.loads(raw);refs=references(data)&BANNED
        if refs and any('/'+t+'/' in name for t in ('recipes','advancements','configured_feature','placed_feature')):retired=True
        if retired:del entries[name];removed.append(name);continue
        clean=scrub(data)
        if clean is DROP:del entries[name];removed.append(name)
        elif clean!=data:entries[name]=dump(clean);changed.append(name)
    # Whole mixins implement only removed mechanics; other mixed-purpose hooks are patched above.
    mixins=json.loads(entries['spawn.mixins.json'])
    removed_mixins={'SkeletonMixin','TurtleMixin','ItemEntityMixin','client.TurtleRendererMixin','client.CapeLayerMixin','client.ElytraLayerMixin','client.CustomHeadLayerMixin'}
    for side in ['mixins','client']:mixins[side]=[m for m in mixins[side] if m not in removed_mixins]
    entries['spawn.mixins.json']=dump(mixins)
    entries.update({p.relative_to(patched).as_posix():p.read_bytes() for p in patched.rglob('*.class')})
    native=build/'native';native.mkdir(exist_ok=True)
    jars=sorted(libraries.rglob('*.jar'),key=lambda p:('forge-1.20.1-47.4.23-server' not in p.name,'server-1.20.1-' not in p.name,str(p)))
    fullcp=os.pathsep.join(map(str,[original,*jars]))
    run([*compiler(),'--release','17','-proc:none','-cp',fullcp,'-d',native,*sorted((ROOT/'src/main/java').rglob('*.java'))])
    entries.update({p.relative_to(native).as_posix():p.read_bytes() for p in native.rglob('*.class')})
    tests=build/'test-classes';tests.mkdir(exist_ok=True)
    run([*compiler(),'--release','17','-proc:none','-cp',str(native)+os.pathsep+fullcp,'-d',tests,ROOT/'tests/SpawnSmokeMod.java'])
    fixture={p.relative_to(tests).as_posix():p.read_bytes() for p in tests.rglob('*.class')}
    fixture['fixture-ids.txt']='\n'.join(sorted(BANNED)).encode()
    fixture['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_spawn_smoketest"\nversion="1"\n'
    jar(build/'empirecraft-spawn-smoketest.jar',fixture)
    entries['META-INF/mods.toml']=entries['META-INF/mods.toml'].replace(b'version="4.0.8-1.20.1"',('version="'+VERSION+'"').encode())
    manifest=dict(version=VERSION,input_sha256=INPUT_SHA256,removed_items=sorted('spawn:'+x for x in ITEMS),removed_blocks=sorted('spawn:'+x for x in BLOCKS),removed_entities=sorted('spawn:'+x for x in ENTITIES),removed_resources=sorted(removed),edited_resources=sorted(changed),removed_mixins=sorted(removed_mixins))
    entries['empirecraft-spawn-removal.json']=dump(manifest)
    (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/removal.json').write_bytes(dump(manifest));shutil.copyfile(patched/'patch-report.txt',ROOT/'audit/bytecode.txt')
    output=build/NAME;jar(output,entries)
    for folder in [REPO/'mods',REPO/'server/mods']:
        shutil.copyfile(output,folder/NAME);(folder/(NAME+'.sha256')).write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+NAME+'\n')
    print('PASS native Spawn:',len(ITEMS),'items,',len(BLOCKS),'blocks,',len(ENTITIES),'entities removed;',len(removed),'data files removed')
if __name__=='__main__':main()
