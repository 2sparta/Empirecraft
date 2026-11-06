#!/usr/bin/env python3
"""Install the paired release, record executed checks and make deterministic update/source archives."""
import argparse,hashlib,json,re,shutil,subprocess,sys,tomllib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
NAME='empirecraft-engagement-1.0.0.jar';PLUGIN='empirecraft-feudal-1.16.0.jar'
REPORT=REPO/'validation/engagement-1.0.0.json'
COMPONENTS=[NAME,'townyrecruits-1.4.0.jar','vs_sails-1.20.1-0.3.2-empirecraft.17-forge.jar','empirecraft-ship-physics-1.2.2.jar','empirecraft-towny-journeymap-1.9.0.jar']
def sha(raw):return hashlib.sha256(raw).hexdigest()
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(sha(path.read_bytes())+'  '+path.name+'\n')
def run(*args):subprocess.run([sys.executable,*map(str,args)],check=True)
def identities():return {str(p.relative_to(REPO)):sha(p.read_bytes()) for p in [REPO/'server/plugins'/PLUGIN,*[REPO/side/name for side in ['mods','server/mods'] for name in COMPONENTS]]}
def install(plugin):
    for side in ['mods','server/mods']:
        shutil.copyfile(ROOT/'dist'/NAME,REPO/side/NAME);checksum(REPO/side/NAME)
    for old in (REPO/'server/plugins').glob('empirecraft-feudal-*.jar'):
        if old.name!=PLUGIN:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    target=REPO/'server/plugins'/PLUGIN
    if target.resolve()!=plugin.resolve():shutil.copyfile(plugin,target)
    checksum(target)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());raw=(ROOT/'dist'/NAME).read_bytes()
    row=dict(id='empirecraft_engagement',file=NAME,version='1.0.0',sides=['client','server'],source='local:engagement',added=True,updated=True,
        sha256=sha(raw),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),
        pin_reason='Towny recruit/ship PvP, actual deck and modded-seat crews, immutable cannon/TNT provenance, permitted block-breaking rams and visible scaled national ensigns. Paired with Feudal 1.16.0; source and executed checks in engagement/.')
    lock['mods']=[x for x in lock['mods'] if x['id']!=row['id']]+[row];write(path,lock)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text());manifest['releases']=[x for x in manifest['releases'] if x['id']!=row['id']]+[dict(row,artifact='mods/'+NAME)];write(path,manifest)
    run(REPO/'validation/reconcile_modpack.py','--apply')
    for relative,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
        path=REPO/relative;values=json.loads(path.read_text());values=[x for x in values if x['name']!=NAME];values.append(dict(name=NAME,path=side+'/'+NAME,size=len(raw),sha=row['git_sha']));write(path,sorted(values,key=lambda x:x['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256'][NAME]=row['sha256'];coverage['jar_count']=len(json.loads((REPO/'seasonal-calendar/installed-mods.json').read_text()));write(path,coverage)
def record(backend,native):
    text=backend.read_text();assert 'Готово:' in text and 'Exception in thread' not in text
    checks={}
    for name,pattern in [('policy',r'COMBAT_POLICY_PASS checks=(\d+)'),('nbt',r'COMBAT_LEDGER_NBT_PASS scenarios=(\d+)'),('motion',r'RAM_MOTION_PASS scenarios=(\d+)'),('mixins',r'ENGAGEMENT_MIXINS_PASS targets=(\d+)')]:
        log='mixins.log' if name=='mixins' else {'policy':'CombatPolicyTest','nbt':'CombatLedgerTest','motion':'RamMotionTest'}[name]
        path=ROOT/'build'/(log if name=='mixins' else 'ua.empirecraft.engagement.'+log+'.log');checks[name]=int(re.search(pattern,path.read_text())[1])
    result=json.loads(native.read_text());assert result['server_restarts']==1 and len(result['results'])==2
    assert all('ENGAGEMENT_NATIVE_PASS' in line for line in result['results'])
    assert result['plugin_sha256']==sha((REPO/'server/plugins'/PLUGIN).read_bytes())
    assert result['mod_sha256'][NAME]==sha((ROOT/'dist'/NAME).read_bytes())
    result['results']=[re.search(r'ENGAGEMENT_NATIVE_PASS[^\x1b\r\n]*',line)[0].strip() for line in result['results']]
    write(REPORT,dict(status='passed',versions=dict(engagement='1.0.0',feudal='1.16.0',recruits='1.4.0'),automatic_checks=checks,
        backend=dict(suites=28,scenarios=len(re.findall(r'^✓ ',text,re.M)),fleet_cases=int(re.search(r'FLEET_SERVICE_PASS cases=(\d+)',text)[1])),
        native=result,artifacts=identities(),graphical_client='not_run',production_world='not_run',natural_physics_contacts='not_run; controlled contacts exercise the real collision work queue',large_battle_performance='not_measured'))
def archive(path,files,check):
    from io import BytesIO
    output=BytesIO()
    with zipfile.ZipFile(output,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    raw=output.getvalue()
    if check:assert path.read_bytes()==raw;assert path.with_suffix(path.suffix+'.sha256').read_text()==sha(raw)+'  '+path.name+'\n'
    else:path.write_bytes(raw);checksum(path)
def package(check):
    report=json.loads(REPORT.read_text());assert report['status']=='passed' and report['artifacts']==identities()
    for name in COMPONENTS:
        assert (REPO/'mods'/name).read_bytes()==(REPO/'server/mods'/name).read_bytes()
    assert (ROOT/'dist'/NAME).read_bytes()==(REPO/'mods'/NAME).read_bytes()
    for relative in identities():
        path=REPO/relative;assert path.with_suffix('.jar.sha256').read_text()==sha(path.read_bytes())+'  '+path.name+'\n'
        with zipfile.ZipFile(path) as z:assert z.testzip() is None and not any('SmokeMod' in n or n.startswith(('net/minecraft/','org/bukkit/')) for n in z.namelist())
    files={'INSTALL-UA.md':(ROOT/'README.md').read_bytes(),'validation.json':REPORT.read_bytes(),'modpack-lock.json':(REPO/'modpack-lock.json').read_bytes(),
        'server/config/admanticheat/idfile/admanticheat_id_list.json':(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes(),
        'REMOVE-OLD-JARS.txt':b'Replace superseded Feudal JARs and checksums. Install the matching JARs in both mods folders. Keep only one version of every mod. Preserve worlds and the Feudal database.\n'}
    for relative in identities():
        path=REPO/relative;name='client/'+relative if relative.startswith('mods/') else relative;files[name]=path.read_bytes();files[name+'.sha256']=path.with_suffix('.jar.sha256').read_bytes()
    files['SHA256SUMS.txt']=''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode();archive(ROOT/'dist/Empirecraft-Engagement-1.0.0-update.zip',files,check)
    sources={}
    for folder in ['engagement/src','engagement/tests','engagement/tools','feudal/src']:
        for path in (REPO/folder).rglob('*'):
            if path.is_file():sources[str(path.relative_to(REPO))]=path.read_bytes()
    for relative in ['engagement/README.md','engagement/build.py','engagement/test.py','engagement/smoke.py','engagement/release.py','feudal/build.py','feudal/ci_build.py','feudal/release.py','validation/check_localization.py','modpack-lock.json']:
        sources[relative]=(REPO/relative).read_bytes()
    sources['REPRODUCE.txt']=b'Apply these sources to the matching Empirecraft checkout. Use JDK 17 for engagement and JDK 21 for Feudal. Prepare the pinned Sails compile SDK with python3 sails/build.py --sails-only, then restore the committed Sails JAR. Run engagement/build.py, engagement/test.py, feudal/ci_build.py and engagement/smoke.py. API and Minecraft binaries are supplied by the matching repository, not this source archive.\n'
    archive(ROOT/'dist/Empirecraft-Engagement-1.0.0-sources.zip',sources,check)
    print('ENGAGEMENT_RELEASE_PASS',sha((ROOT/'dist'/NAME).read_bytes()))
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--install',action='store_true');parser.add_argument('--check',action='store_true');parser.add_argument('--plugin',type=Path,default=REPO/'server/plugins'/PLUGIN);parser.add_argument('--backend-log',type=Path);parser.add_argument('--native-report',type=Path,default=ROOT/'build/logs/result.json');args=parser.parse_args()
    if args.install:install(args.plugin)
    if args.backend_log:record(args.backend_log,args.native_report)
    package(args.check)
if __name__=='__main__':main()
