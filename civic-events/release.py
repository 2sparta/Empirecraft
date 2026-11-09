#!/usr/bin/env python3
"""Record executed civic checks and package the server addon and reproducible feature sources."""
import argparse,hashlib,json,re,shutil,tomllib,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;VERSION='1.0.0';NAME='empirecraft-civic-events-'+VERSION+'.jar';REPORT=REPO/'validation/civic-events-1.0.0.json'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def artifacts():
 names=[s['file'] for s in json.loads((REPO/'starcatcher-upgrade/sources.json').read_text())]+['empirecraft-towny-journeymap-1.10.0.jar']
 return [REPO/'server/plugins/empirecraft-feudal-1.19.2.jar',REPO/'server/mods'/NAME,*[REPO/side/name for side in ['mods','server/mods'] for name in names],REPO/'translations/dist/Empirecraft-Translations-uk-ru-1.1.0.zip',REPO/'server/world/datapacks/empirecraft-multiverse.zip',*[REPO/side/'data/empirecraft_mass/tags/blocks'/(name+'.json') for side in ['kubejs','server/kubejs'] for name in ['wood_furniture','wood_container','brass_machine']]]
def identities():return {p.relative_to(REPO).as_posix():sha(p) for p in artifacts()}
def record(backend,junit,native):
 text=backend.read_text();assert 'Готово:' in text and 'Exception in thread' not in text and sha(artifacts()[0]) in text
 notices=int(re.search(r'MARKET_NOTICE_TESTS_PASS scenarios=(\d+)',text)[1]);fishing=int(re.search(r'MARKET_FISHING_TESTS_PASS scenarios=(\d+)',text)[1]);assert notices>=11 and fishing>=13
 suites=[ET.parse(p).getroot() for p in junit.glob('TEST-*.xml')];assert suites and all(int(s.get(k,0))==0 for s in suites for k in ['failures','errors','skipped'])
 assert any('CivicReadStateTest' in c.get('classname','') for s in suites for c in s.iter('testcase'));client=sum(int(s.get('tests',0)) for s in suites)
 result=json.loads(native.read_text());assert result['actual_server_restarts']==1 and result['plugin_sha256']==sha(artifacts()[0])
 assert result['dimension_pack_sha256']==sha(REPO/'server/world/datapacks/empirecraft-multiverse.zip')
 assert len(result['results'])==2 and all('CIVIC_NATIVE_PASS' in line for line in result['results'])
 for name,digest in result['mods_sha256'].items():assert sha(REPO/'server/mods'/name)==digest,name
 assertions=sum(int(re.search(r'assertions=(\d+)',line)[1]) for line in result['results']);result['results']=[re.search(r'CIVIC_NATIVE_PASS[^\x1b\r\n]*',line)[0].strip() for line in result['results']]
 write(REPORT,dict(status='passed',versions=dict(feudal='1.18.0',bridge='1.10.0',civic=VERSION,starcatcher='3.3.10-FORGE-1.20.1'),
  upstream=dict(as_of='2026-11-07 Europe/Kyiv',provider='official Modrinth Forge 1.20.1 releases',pins='starcatcher-upgrade/sources.json',license_notice='starcatcher-upgrade/UPSTREAM-LICENSE.txt'),
  backend=dict(suites=32,scenarios=len(re.findall(r'^✓ ',text,re.M)),notice_cases=notices,fishing_cases=fishing,compiled_against='installed Mohist 1.20.1-47.4.23 server API'),
  native_client=dict(tests=client,civic_reader_cases=4,scope='JUnit against exact installed Bridge remapped to native client API; real reader state and packet fixtures'),
  native=dict(assertions=assertions,**result),
  dimension_compatibility=dict(world='minecraft:world_europe',native_check='actual Starcatcher cod restriction permits Europe and Overworld; rejects Nether',datapack='server/world/datapacks/empirecraft-multiverse.zip',fish_overrides=0,java_identity_proof='starcatcher-upgrade/compatibility.json'),
  entry_fee=dict(optional=True,source='personal wallet once per successful signup',destination='entire entry pool to first place',refund='every entrant on cancellation or no qualifying catches',terms_frozen='before announcement'),
  prizes=dict(ranks=[1,2,3],types=['currencies','exact item NBT'],funding='escrow before announcement; treasury authority and republic budget enforced',delivery='wallet and persistent market assets, including offline players',unoccupied_rank='refund contributor'),
  noticeboard=dict(block='vanilla sign inside approved GOODS building',fee='zero or an approved city market currency',review='optional, mayor or current mayor-appointed city herald',pending_fee='reserved until publication; rejected/expired/withdrawn/missing board refunds',automatic_events=['announced fairs','announced fishing tournaments']),
  catch_source='Starcatcher server completion; actual owned live FishingBobEntity, fish properties and percentile, both angler and bobber within host Towny city; deduplicate bobber UUID',
  invitations='existing persistent Contact postcard sample, postage and idempotent receipt outbox',
  artifacts=identities(),graphical_client='not_run',production_world='not_run',large_event_performance='not_measured'))
def validate():
 report=json.loads(REPORT.read_text());assert report['status']=='passed' and report['artifacts']==identities()
 assert not (REPO/'mods'/NAME).exists(),'The civic integration belongs only on the server'
 for source in json.loads((REPO/'starcatcher-upgrade/sources.json').read_text()):assert (REPO/'mods'/source['file']).read_bytes()==(REPO/'server/mods'/source['file']).read_bytes()
 with zipfile.ZipFile(REPO/'server/mods'/NAME) as z:
  assert z.testzip() is None and 'ua/empirecraft/civic/mixin/TournamentCatchMixin.class' in z.namelist()
  assert not any('civictest' in n or n.startswith(('net/minecraft/','org/bukkit/')) for n in z.namelist())
  doc=tomllib.loads(z.read('META-INF/mods.toml').decode());assert doc['mods'][0]['version']==VERSION
  assert z.read('META-INF/MANIFEST.MF').startswith(b'Manifest-Version:')
 return report
def archive(path,entries,check):
 import io
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w') as z:
  for name,raw in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 raw=out.getvalue()
 if check:assert path.read_bytes()==raw,('Stale civic source archive',path)
 else:path.parent.mkdir(exist_ok=True);path.write_bytes(raw);path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(raw).hexdigest()+'  '+path.name+'\n')
def package(check):
 validate();target=ROOT/'dist'/NAME;source=REPO/'server/mods'/NAME
 if check:assert target.read_bytes()==source.read_bytes()
 else:target.parent.mkdir(exist_ok=True);shutil.copyfile(source,target);target.with_suffix('.jar.sha256').write_text(sha(target)+'  '+target.name+'\n')
 files={}
 for folder in ['civic-events/src','civic-events/tests','feudal/src','bridge/src','bridge/native-inputs','starcatcher-upgrade','multiverse-compat']:
  for p in (REPO/folder).rglob('*'):
   if p.is_file() and '__pycache__' not in p.parts and 'build' not in p.relative_to(REPO).parts and 'dist' not in p.relative_to(REPO).parts:files[p.relative_to(REPO).as_posix()]=p.read_bytes()
 for name in ['civic-events/build.py','civic-events/smoke.py','civic-events/release.py','feudal/CIVIC-EVENTS.md','feudal/build.py','feudal/ci_build.py','feudal/release.py','feudal/city_release.py','bridge/overlay.py','bridge/build.gradle','validation/civic-events-1.0.0.json','modpack-lock.json']:
  files[name]=(REPO/name).read_bytes()
 files['REPRODUCE.txt']=b'Use the matching Empirecraft checkout and its pinned Minecraft/Forge API. Download the official files pinned in starcatcher-upgrade/sources.json and run its build.py --inputs DIRECTORY with JDK17. Prepare the Sails named compile SDK, restore the unchanged shipped Sails JAR, then build civic-events/build.py and bridge/overlay.py with JDK17. Build Feudal with JDK21 and feudal/ci_build.py. Run civic-events/smoke.py with a JDK21 server runtime. Sources and fixtures do not belong in the production mods directory. The complete client/server update is feudal/dist/Empirecraft-City-Markets-1.18.0-bundle.zip.\n'
 archive(ROOT/'dist/Empirecraft-Civic-Events-1.0.0-sources.zip',files,check)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--record',action='store_true');p.add_argument('--backend-log',type=Path);p.add_argument('--junit',type=Path);p.add_argument('--native-report',type=Path,default=ROOT/'build/native/result.json');p.add_argument('--check',action='store_true');args=p.parse_args()
 if args.record:record(args.backend_log,args.junit,args.native_report)
 package(args.check);print('CIVIC_RELEASE_PASS')
if __name__=='__main__':main()
