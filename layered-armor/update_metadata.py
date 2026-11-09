#!/usr/bin/env python3
"""Install reviewed local candidates and pin the exact paired release without changing world/config data."""
import hashlib,importlib.util,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO

def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(sha(path)+'  '+path.name+'\n')
def main():
    candidates=[('empirecraft_armor',ROOT/'dist'/build.NAME,'empirecraft-layered-armor-', ['mods','server/mods']),
                ('empirecraft_fauna',REPO/'fauna/build/libs/empirecraft-fauna-1.1.1.jar','empirecraft-fauna-',['server/mods'])]
    for mid,candidate,prefix,sides in candidates:
        for side in sides:
            for old in (REPO/side).glob(prefix+'*.jar'):
                if old.name!=candidate.name:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
            shutil.copyfile(candidate,REPO/side/candidate.name);checksum(REPO/side/candidate.name)
    lock_path=REPO/'modpack-lock.json';lock=json.loads(lock_path.read_text())
    reasons={
      'empirecraft_armor':('local:layered-armor','Padded/mail/outer armor with sequential inventory operations, expanded native menu, independent wear and original item models. Full sources and native checks in layered-armor/.'),
      'empirecraft_forging':('local:forging-balance','Previous flat set-defense additions removed; original defense, forging quality, quality durability and progression rules retained.'),
      'empirecraft_calendar':('local:seasonal-calendar','Per-world real-time calendar anchors for Towny provisions; native calendar dates, pauses, downtime and year persistence retained.'),
      'empirecraft_fauna':('local:fauna','Zero-byte region placeholders accepted, damaged chunks quarantined locally, existing animals retained and bounded background census retries.'),
      'vs_sails':('local:sails','Canonical fleet captain and crew grants no longer require a second global permission; legacy explicit grants and transfer revocation tested.'),
      'empirecraft_engagement':('local:engagement','Sails 18/19 compatibility metadata; all other JAR entries are byte-identical to the reviewed 1.1.0 release.'),
      'empirecraft_pack_fixes':('local:pack-fixes','Sails 18/19 compatibility metadata; scoped bone-meal fix, native tool tags, wind flags and embedded translations remain byte-identical.')}
    rows={r['id']:r for r in lock['mods']}
    if 'empirecraft_armor' not in rows:
        row=dict(id='empirecraft_armor',file=build.NAME,version='1.0.0',sides=['client','server'],added=True,updated=True);lock['mods'].append(row);rows[row['id']]=row
    for mid,(source,reason) in reasons.items():rows[mid].update(source=source,pin_reason=reason,updated=True)
    write(lock_path,lock)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    lock=json.loads(lock_path.read_text());rows={r['id']:r for r in lock['mods']}
    for side,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        inventory=[]
        for jar in sorted((REPO/side).glob('*.jar')):
            raw=jar.read_bytes();inventory.append(dict(name=jar.name,path=str(jar.relative_to(REPO)),size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        write(REPO/name,inventory)
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text())
    coverage['jar_sha256']={p.name:sha(p) for p in sorted((REPO/'server/mods').glob('*.jar'))};coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    manifest['releases']=[r for r in manifest['releases'] if r['id']!='empirecraft_armor']+[dict(rows['empirecraft_armor'],artifact='layered-armor/dist/'+build.NAME)];write(path,manifest)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text())
    for mid in reasons:
        row=rows[mid];prefix={'vs_sails':'vs_sails-','empirecraft_fauna':'empirecraft-fauna-','empirecraft_armor':'empirecraft-layered-armor-','empirecraft_calendar':'empirecraft-calendar-','empirecraft_forging':'empirecraft-forging-','empirecraft_engagement':'empirecraft-engagement-','empirecraft_pack_fixes':'empirecraft-pack-fixes-'}[mid]
        retained={k:v for k,v in retained.items() if not k.startswith(prefix)}
        jar=REPO/('mods' if 'client' in row['sides'] else 'server/mods')/row['file']
        with zipfile.ZipFile(jar) as z:retained[jar.name]={name:hashlib.sha256(z.read(name)).hexdigest() for name in z.namelist() if name.endswith('.class')}
    write(path,retained)
    # Native resource rebuilds start from the new untrimmed, reproducible inputs.
    sails=REPO/'sails/dist/native-inputs'/rows['vs_sails']['file'];sails.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(REPO/'sails/build/sails-untrimmed.jar',sails);checksum(sails)
    baselines={mid:REPO/folder/rows[mid]['file'] for mid,folder in [('vs_sails','sails/dist/native-inputs'),('empirecraft_forging','forging-balance/dist/native-inputs'),('empirecraft_calendar','seasonal-calendar/dist/native-inputs')]}
    path=REPO/'native-content/inputs.json';inputs=json.loads(path.read_text())
    for mid,baseline in baselines.items():
        prefix={'vs_sails':'vs_sails-','empirecraft_forging':'empirecraft-forging-','empirecraft_calendar':'empirecraft-calendar-'}[mid]
        inputs={k:v for k,v in inputs.items() if not Path(k).name.startswith(prefix)}
        for side in rows[mid]['sides']:inputs[('mods/' if side=='client' else 'server/mods/')+rows[mid]['file']]=sha(baseline)
    write(path,inputs)
    path=REPO/'native-content/audit/build.json';audit=json.loads(path.read_text())
    for mid,folder in [('vs_sails','sails'),('empirecraft_forging','forging-balance'),('empirecraft_calendar','seasonal-calendar')]:
        prefix={'vs_sails':'vs_sails-','empirecraft_forging':'empirecraft-forging-','empirecraft_calendar':'empirecraft-calendar-'}[mid]
        audit={k:v for k,v in audit.items() if not Path(k).name.startswith(prefix)}
        evidence=json.loads((REPO/folder/('build/native-overlay.json' if mid=='vs_sails' else 'build/native-release.json')).read_text())
        proof=evidence if mid=='vs_sails' else evidence['report']
        if mid!='vs_sails':proof=dict(proof,input_sha256=evidence['input_sha256'])
        assert proof['sha256']==rows[mid]['sha256']
        for side in rows[mid]['sides']:audit[('mods/' if side=='client' else 'server/mods/')+rows[mid]['file']]=proof
        checksum(baselines[mid])
    write(path,audit)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py'],check=True)
    subprocess.run([sys.executable,REPO/'validation/check_installed_releases.py'],check=True)
    print('LAYERED_HOTFIX_METADATA_PASS')
if __name__=='__main__':main()
