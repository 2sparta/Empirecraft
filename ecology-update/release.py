#!/usr/bin/env python3
"""Install only verified ecology artifacts and package the matching client/server update."""
import argparse,csv,hashlib,io,json,shutil,subprocess,sys,tomllib,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
from smoke import UPDATES

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;DIST=ROOT/'dist'
PREFIXES=('empirecraft-fauna-','empirecraft-seasonal-leaves-','empirecraft-calendar-','empirecraft-layered-armor-','empirecraft-world-rules-')
OLD=('empirecraft-fauna-1.1.1.jar','empirecraft-seasonal-leaves-1.3.2.jar','empirecraft-calendar-1.2.1.jar','empirecraft-layered-armor-1.0.2.jar','empirecraft-world-rules-1.0.2.jar')

def digest(raw):return hashlib.sha256(raw).hexdigest()
def sha(path):return digest(path.read_bytes())
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def archive(path,files):
    path.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(path,'w') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,12,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
def mods():
    rows=[]
    for p in UPDATES:
        with zipfile.ZipFile(p) as z:meta=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods'][0]
        rows.append(dict(id=meta['modId'],version=meta['version'],file=p.name,sha256=sha(p),sides=['server'] if meta['modId'] in ['empirecraft_fauna','empirecraft_world_rules'] else ['client','server']))
    return rows

def verification():
    rows=mods();native=json.loads((ROOT/'build/native/result.json').read_text());fauna=json.loads((REPO/'fauna/build/native/result.json').read_text())
    assert native['updates']=={p.name:sha(p) for p in UPDATES},'Native ecology checks used different JARs'
    assert len(native['phases'])==2 and all('PASS' in marker for marker in native['phases'])
    assert native['mods']>100 and not native['production_world_opened']
    assert fauna['fauna_sha256']==rows[0]['sha256'] and len(fauna['phases'])==2 and all('PASS' in p for p in fauna['phases'])
    suites=[ET.parse(p).getroot() for p in (REPO/'fauna/build/test-results/test').glob('TEST-*.xml')]
    assert suites and all(int(s.attrib['failures'])==int(s.attrib['errors'])==0 for s in suites)
    unit=(REPO/'work/seasonal-leaves/unit-result.txt').read_text()
    for marker in ['SEASON_CURVE_PASS','VEGETATION_POLICY_PASS','SCAN_BUDGET_PASS','PROFILE_DATA_PASS','SEASON_EFFECTS_PASS']:assert marker in unit,marker
    armor=(REPO/'work/armor/build/mixins.log').read_text();language=(REPO/'work/armor/build/language.log').read_text()
    assert 'OUTER_ARMOR_RENDER_PASS' in armor and 'ARMOR_LANGUAGE_PASS assertions=60' in language
    calendar={name:(REPO/'seasonal-calendar/build'/name).read_text().splitlines() for name in ['CalendarPolicyTest.log','CalendarYearsTest.log','CalendarDataTest.log','CalendarMixinTestSERVER.log','CalendarMixinTestCLIENT.log']}
    assert all(lines and all(line.startswith('PASS') for line in lines) for lines in calendar.values())
    world_rules={name:[line for line in (REPO/'world-rules/build'/name).read_text().splitlines() if line.startswith('PASS')] for name in ['RulesPolicyTest.log','WildernessTest.log','RulesMixinTest.log']}
    assert all(lines for lines in world_rules.values())
    plants=json.loads((ROOT/'build/native/ecology-plants.json').read_text());assert len(plants)==33 and all(p['appeared'] for p in plants)
    previous=REPO/'mods/empirecraft-layered-armor-1.0.2.jar'
    if previous.exists():
        with zipfile.ZipFile(previous) as old,zipfile.ZipFile(UPDATES[3]) as new:
            before={n:old.read(n) for n in old.namelist() if n.endswith('.class')};after={n:new.read(n) for n in new.namelist() if n.endswith('.class')}
            assert all(before[n]==after[n] for n in after),'Armor logic changed outside rendering'
            removed=sorted(set(before)-set(after));assert removed==['ua/empirecraft/armor/mixin/client/InnerArmorRenderMixin.class']
            armor_identity=dict(previous_sha256=sha(previous),unchanged_classes=len(after),removed=removed)
    else:armor_identity=json.loads((ROOT/'verification.json').read_text())['armor_binary_identity']
    previous_leaf=REPO/'mods/empirecraft-seasonal-leaves-1.3.2.jar'
    if previous_leaf.exists():
        with zipfile.ZipFile(previous_leaf) as before,zipfile.ZipFile(UPDATES[1]) as after:
            names={n for n in before.namelist() if n.endswith('.class')}
            assert names=={n for n in after.namelist() if n.endswith('.class')} and all(before.read(n)==after.read(n) for n in names)
            leaf_identity=dict(previous_sha256=sha(previous_leaf),unchanged_classes=len(names),previous_native_report='seasonal-leaves/dist/1.3.2/validation.json')
    else:leaf_identity=json.loads((ROOT/'verification.json').read_text())['leaf_binary_identity']
    return dict(status='passed',mods=rows,full_pack=native,fauna_population_restart=fauna,
                junit_tests=sum(int(s.attrib['tests']) for s in suites),plant_count=len(plants),
                plant_report_sha256=sha(ROOT/'build/native/ecology-plants.json'),flight=json.loads((ROOT/'build/native/ecology-flight.json').read_text()),
                leaf_unit_markers=unit.splitlines(),leaf_binary_identity=leaf_identity,armor_mixin_markers=armor.splitlines(),armor_binary_identity=armor_identity,
                armor_language_assertions=60,calendar_checks=calendar,world_rules_checks=world_rules,graphical_client_tested=False,production_world_tested=False)

def install(report):
    rows=report['mods']
    for side,folder in [('client','mods'),('server','server/mods')]:
        directory=REPO/folder
        for old in directory.glob('*.jar'):
            if old.name.startswith(PREFIXES):old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        for row,p in zip(rows,UPDATES):
            if side in row['sides']:shutil.copyfile(p,directory/p.name);checksum(directory/p.name)
    path=REPO/'server/config/empirecraft-fauna.json';configured=json.loads(path.read_text())
    defaults=json.loads((REPO/'fauna/build/defaults/empirecraft-fauna.json').read_text())
    for key,value in defaults.items():configured.setdefault(key,value)
    write(path,configured)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text())
    sources=['fauna','seasonal-leaves','seasonal-calendar','layered-armor','world-rules']
    for new,source in zip(rows,sources):
        row=next(r for r in lock['mods'] if r['id']==new['id']);row.update(source='local:'+source,updated=True,pin_reason='Ecology update: spring–autumn wild plants, forward wildlife, flock flight/noise response and outermost armor rendering; native validation and source in ecology-update/.')
    write(path,lock)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    for new in rows:
        entry=next((r for r in manifest['releases'] if r['id']==new['id']),None)
        if entry is None:entry={'id':new['id']};manifest['releases'].append(entry)
        entry['artifact']=('server/mods/' if new['sides']==['server'] else 'mods/')+new['file']
    write(path,manifest)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    for side,folder,name in [('client','mods','forging-balance/installed-client-mods.json'),('server','server/mods','seasonal-calendar/installed-mods.json')]:
        path=REPO/name;inventory=[r for r in json.loads(path.read_text()) if not r['name'].startswith(PREFIXES)]
        for row,p in zip(rows,UPDATES):
            if side not in row['sides']:continue
            raw=p.read_bytes();inventory.append(dict(name=p.name,path=folder+'/'+p.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(path,sorted(inventory,key=lambda r:r['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text())
    coverage['jar_sha256']={n:v for n,v in coverage['jar_sha256'].items() if not n.startswith(PREFIXES)}
    coverage['jar_sha256'].update({p.name:sha(p) for p in UPDATES});coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained={n:v for n,v in json.loads(path.read_text()).items() if not n.startswith(PREFIXES)}
    for p in UPDATES:
        with zipfile.ZipFile(p) as z:retained[p.name]={n:digest(z.read(n)) for n in sorted(z.namelist()) if n.endswith('.class')}
    write(path,retained)

def package(report):
    release=dict(mods=report['mods'],requires='Empirecraft TFC integration PR #125')
    write(ROOT/'release.json',release);write(ROOT/'verification.json',report)
    plants=json.loads((ROOT/'build/native/ecology-plants.json').read_text());table=io.StringIO()
    writer=csv.writer(table,delimiter=';');writer.writerow(['Рослина (ID)','Клімат','Появу перевірено','Сезони дикої появи','Отриманий дроп без інструментів'])
    for row in plants:writer.writerow([row['id'],row['climate'],'так' if row['appeared'] else 'ні','весна, літо, осінь',', '.join(row['loot'])])
    (ROOT/'plants.csv').write_text('\ufeff'+table.getvalue(),encoding='utf-8')
    files={'INSTALL-UA.md':(ROOT/'README.uk.md').read_bytes(),'update_adm.py':(ROOT/'update_adm.py').read_bytes(),
           'release.json':(ROOT/'release.json').read_bytes(),'verification.json':(ROOT/'verification.json').read_bytes(),
           'plants.json':(ROOT/'build/native/ecology-plants.json').read_bytes(),'plants.csv':(ROOT/'plants.csv').read_bytes()}
    for row,p in zip(report['mods'],UPDATES):
        for side in row['sides']:
            files[side+'/mods/'+p.name]=p.read_bytes();files[side+'/mods/'+p.name+'.sha256']=(sha(p)+'  '+p.name+'\n').encode()
    files['REMOVE-OLD.txt']=('Remove these JARs and their .sha256 files from BOTH sides where present:\n'+'\n'.join(OLD)+'\nFauna belongs only on the server. Keep the rest of the TFC integration installed.\n').encode()
    sources={}
    for name in ['ecology-update','fauna','seasonal-leaves','seasonal-calendar','layered-armor','world-rules']:
        for p in (REPO/name).rglob('*'):
            if p.is_file() and not any(part in ['build','dist','audit','__pycache__','.gradle'] for part in p.relative_to(REPO/name).parts):sources[str(p.relative_to(REPO))]=p.read_bytes()
    sourcezip=DIST/'Empirecraft-Ecology-Update-1-sources.zip';archive(sourcezip,sources);checksum(sourcezip);files[sourcezip.name]=sourcezip.read_bytes()
    files['SHA256SUMS.txt']=''.join(digest(raw)+'  '+n+'\n' for n,raw in sorted(files.items())).encode()
    target=DIST/'Empirecraft-Ecology-Update-1-install.zip';archive(target,files);checksum(target)

def check():
    report=json.loads((ROOT/'verification.json').read_text());assert report['mods']==mods()
    for row,p in zip(report['mods'],UPDATES):
        for side,folder in [('client','mods'),('server','server/mods')]:
            installed=REPO/folder/p.name
            assert installed.exists()==(side in row['sides']),str(installed)
            if installed.exists():assert installed.read_bytes()==p.read_bytes()
    with zipfile.ZipFile(DIST/'Empirecraft-Ecology-Update-1-install.zip') as z:
        assert z.testzip() is None and not any('ecology-test.jar' in n or 'fauna-smoke.jar' in n for n in z.namelist())
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            wanted,name=line.split('  ',1);assert digest(z.read(name))==wanted,name
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py'],check=True)
    print('ECOLOGY_RELEASE_PASS: verified matching artifacts, sources, client/server installation, ADM and reproducible installer')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');p.add_argument('--check',action='store_true');a=p.parse_args()
    if not a.check:
        report=verification();write(ROOT/'verification.json',report)
        if a.install:install(report)
        package(report)
    check()

if __name__=='__main__':main()
