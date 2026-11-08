#!/usr/bin/env python3
"""Install the combined native-tested client/server JAR and refresh its ADM entry."""
import argparse,datetime,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO;NAME='empirecraft-seasonal-leaves-'+build.VERSION+'.jar';DIST=ROOT/'dist'/build.VERSION

def digest(raw):return hashlib.sha256(raw).hexdigest()
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def install():
    candidate=DIST/NAME;raw=candidate.read_bytes();sha=digest(raw)
    perf=json.loads((build.BUILD/'performance-world-logs/result.json').read_text())
    optimized=next(r for r in perf['runs'] if r['phase']=='optimized')
    baseline=next(r for r in perf['runs'] if r['phase']=='baseline')
    assert optimized['jar_sha256']==sha and optimized['actual_tag_reloads']==2
    assert optimized['slow_update_callbacks_in_slice']==1
    assert optimized['max_sections']<=32 and optimized['max_chunk_visits']<=128 and optimized['max_positions']<=32768
    assert baseline['jar_sha256']=='5713a16e0d0b663974326af578401b22f5ff335273ccc834c1c422e02d87b3c4'
    growth=json.loads((build.BUILD/'growth-world-logs/result.json').read_text())
    assert [r['phase'] for r in growth['runs']]==['off','on'] and all(r['exit_code']==0 and all('PASS' in m for m in r['markers']) for r in growth['runs'])
    effects=json.loads((build.BUILD/'effects-world-logs/result.json').read_text())
    assert effects['addon_sha256']==sha and growth['addon_sha256']==sha
    assert [r['phase'] for r in effects['runs']]==['initial','restart'] and all(r['exit_code']==0 and all('PASS' in m for m in r['markers']) for r in effects['runs'])
    unit=(build.BUILD/'unit-result.txt').read_text();mixins=(build.BUILD/'mixin-result.txt').read_text()
    for mark in ['SEASON_CURVE_PASS','VEGETATION_POLICY_PASS','SCAN_BUDGET_PASS','PROFILE_DATA_PASS','SEASON_EFFECTS_PASS']:assert mark in unit,mark
    for target in ['ua.empirecraft.worldrules.NaturalRenewal','net.minecraft.world.level.block.SaplingBlock','net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase']:assert 'MIXIN_PASS '+target in mixins,target
    for marker in ['CLIENT_MIXIN_PASS','CLIENT_QUAD_RUNTIME_PASS','CLIENT_EARLY_REFRESH_RUNTIME_PASS']:assert marker in mixins,marker
    for directory in [REPO/'mods',REPO/'server/mods']:
        for old in directory.glob('empirecraft-seasonal-leaves-*.jar*'):
            if old.name not in (NAME,NAME+'.sha256'):old.unlink()
        shutil.copyfile(candidate,directory/NAME);(directory/(NAME+'.sha256')).write_text(sha+'  '+NAME+'\n')
    lockpath=REPO/'modpack-lock.json';lock=json.loads(lockpath.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_seasonal_leaves')
    row.update(version=build.VERSION,file=NAME,old_version='1.3.0',sides=['client','server'],source='local:seasonal-leaves',updated=True,pin_reason='Cached block-state profiles with live tag invalidation; bounded palette, chunk and time budgets for cleanup and independent regrowth queue. Combined with 1.3.0 weather, client autumn colours, Embeddium support and leaf foothold protection; paired 1.3.1 client/server and ADM.')
    write(lockpath,lock)
    # Reconciliation verifies artifact paths while refreshing hashes. Move the source pointer first.
    manifestpath=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifestpath.read_text())
    next(r for r in manifest['releases'] if r['id']==row['id'])['artifact']=str(candidate.relative_to(REPO))
    write(manifestpath,manifest)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    lock=json.loads(lockpath.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_seasonal_leaves')
    manifestpath=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifestpath.read_text())
    for i,entry in enumerate(manifest['releases']):
        if entry['id']==row['id']:manifest['releases'][i]=dict(row,artifact=str(candidate.relative_to(REPO)));break
    else:raise AssertionError('Missing installed Seasonal Leaves manifest entry')
    write(manifestpath,manifest)
    for folder, inventorypath in [('server/mods',REPO/'seasonal-calendar/installed-mods.json'),('mods',REPO/'forging-balance/installed-client-mods.json')]:
        inventory=[r for r in json.loads(inventorypath.read_text()) if not r['name'].startswith('empirecraft-seasonal-leaves-')]
        inventory.append(dict(name=NAME,path=folder+'/'+NAME,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()));write(inventorypath,sorted(inventory,key=lambda r:r['name']))
    coveragepath=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(coveragepath.read_text());coverage['jar_sha256']={n:h for n,h in coverage['jar_sha256'].items() if not n.startswith('empirecraft-seasonal-leaves-')};coverage['jar_sha256'][NAME]=sha;coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(coverage['jar_sha256']);write(coveragepath,coverage)
    retainedpath=REPO/'native-content/retained-code.json';retained={n:v for n,v in json.loads(retainedpath.read_text()).items() if not n.startswith('empirecraft-seasonal-leaves-')}
    with zipfile.ZipFile(candidate) as jar:retained[NAME]={n:digest(jar.read(n)) for n in sorted(jar.namelist()) if n.endswith('.class')}
    write(retainedpath,retained)
    report=dict(version=build.VERSION,tested_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),jar_sha256=sha,server_only=False,combined_prs=[113,114],
                unit_checks=unit.strip().splitlines(),mixin_checks=[s for s in mixins.splitlines() if s.startswith(('MIXIN_PASS','CLIENT_'))],
                performance=perf,median_handler_speedup=baseline['median_tick_ms']/optimized['median_tick_ms'],native_restart=growth,native_effects=effects,
                config=dict(simulation_distance=8,vegetation_sections=32,vegetation_chunk_visits=128,target_micros=1000,leaf_sections=16),
                limitations=['Synthetic 14k work entries reference one actual test chunk; not a whole-server TPS measurement.',
                'Uploaded Spark profile exceeds 32 MiB transfer limit and was not read directly; initial attribution comes from user analysis.',
                'Production world, graphical client and cause of the isolated 3.8-second tick were not tested.'])
    write(DIST/'validation.json',report)
    subprocess.run([sys.executable,ROOT/'package.py','--adm-list',REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'],check=True)

def check():
    source=DIST/NAME;raw=source.read_bytes();sha=digest(raw);report=json.loads((DIST/'validation.json').read_text())
    assert report['jar_sha256']==sha==next(r for r in report['performance']['runs'] if r['phase']=='optimized')['jar_sha256']
    assert raw==(REPO/'server/mods'/NAME).read_bytes() and raw==(REPO/'mods'/NAME).read_bytes()
    assert [p.name for p in (REPO/'server/mods').glob('empirecraft-seasonal-leaves-*.jar')]==[NAME]
    for p in [source,REPO/'server/mods'/NAME]:assert p.with_suffix('.jar.sha256').read_text()==sha+'  '+NAME+'\n'
    with zipfile.ZipFile(source) as jar:
        assert jar.testzip() is None and not any(n.startswith(('net/minecraft/','net/minecraftforge/','sereneseasons/','ua/empirecraft/leaves/test/')) for n in jar.namelist())
        resources=ROOT/'src/main/resources'
        for file in resources.rglob('*'):
            if file.is_file():assert jar.read(str(file.relative_to(resources)))==file.read_bytes(),file
    lock=json.loads((REPO/'modpack-lock.json').read_text());entry=next(r for r in lock['mods'] if r['id']=='empirecraft_seasonal_leaves');assert entry['sha256']==sha and entry['version']==build.VERSION and entry['sides']==['client','server']
    adm=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());assert dict(id=entry['id'],version=build.VERSION,sha256=sha,filePresent=True) in adm['modIds']
    artifacts=json.loads((DIST/'artifacts.json').read_text())
    for name,row in artifacts.items():assert row['sha256']==digest((DIST/name).read_bytes()) and row['size']==(DIST/name).stat().st_size,name
    with zipfile.ZipFile(DIST/('Empirecraft-Seasonal-Leaves-'+build.VERSION+'-install.zip')) as jar:assert jar.read('server/mods/'+NAME)==jar.read('client/mods/'+NAME)==raw
    print('SEASONAL_LEAVES_RELEASE_PASS: exact native-tested server JAR, resources, package checksums and matching client/server ADM')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');a=p.parse_args()
    if a.install:install()
    check()
