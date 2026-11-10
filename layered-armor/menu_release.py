#!/usr/bin/env python3
"""Install and package only the verified armor-menu localization fix."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
import build
from update_armor_adm import update,integrity

ROOT=build.ROOT;REPO=build.REPO;REPORT=REPO/'validation/armor-menu-1.0.1.json'
OLD=ROOT/'dist/empirecraft-layered-armor-1.0.0.jar'


def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(build.sha(path)+'  '+path.name+'\n')


def install():
    candidate=ROOT/'dist'/build.NAME
    for folder in ('mods','server/mods'):
        for old in (REPO/folder).glob('empirecraft-layered-armor-*.jar'):
            if old.name!=candidate.name:old.unlink();old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        target=REPO/folder/candidate.name;shutil.copyfile(candidate,target);checksum(target)
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text())
    row=next(row for row in lock['mods'] if row['id']=='empirecraft_armor')
    row.update(pin_reason='1.0.1: automatically enabled embedded en/uk/ru armor-menu languages, readable fallback labels and bounded row text. Server gameplay, item data and protocol are byte-identical to 1.0.0.',updated=True)
    write(path,lock)
    manifest_path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(manifest_path.read_text())
    for release in manifest['releases']:
        if release['id']=='empirecraft_armor':release['artifact']='layered-armor/dist/'+build.NAME
    write(manifest_path,manifest)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    lock=json.loads(path.read_text());row=next(row for row in lock['mods'] if row['id']=='empirecraft_armor')
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text())
    manifest['releases']=[dict(row,artifact='layered-armor/dist/'+build.NAME) if r['id']=='empirecraft_armor' else r for r in manifest['releases']];write(path,manifest)
    for folder,target in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        path=REPO/target;rows=json.loads(path.read_text());rows=[r for r in rows if not r['name'].startswith('empirecraft-layered-armor-')]
        raw=candidate.read_bytes();rows.append(dict(name=candidate.name,path=folder+'/'+candidate.name,size=len(raw),sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()));write(path,sorted(rows,key=lambda r:r['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256']={n:v for n,v in coverage['jar_sha256'].items() if not n.startswith('empirecraft-layered-armor-')};coverage['jar_sha256'][candidate.name]=build.sha(candidate);coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text());retained={n:v for n,v in retained.items() if not n.startswith('empirecraft-layered-armor-')}
    with zipfile.ZipFile(candidate) as z:retained[candidate.name]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    write(path,retained)


def verify():
    candidate=ROOT/'dist'/build.NAME
    language=(ROOT/'build/language.log').read_text();mixins=(ROOT/'build/mixins.log').read_text()
    assert 'ARMOR_LANGUAGE_PASS assertions=60' in language and mixins.count('ARMOR_MIXIN_PASS')==5
    with zipfile.ZipFile(OLD) as old,zipfile.ZipFile(candidate) as new:
        before={n:old.read(n) for n in old.namelist() if n.endswith('.class')};after={n:new.read(n) for n in new.namelist() if n.endswith('.class')}
        changed=sorted(n for n in before.keys()&after.keys() if before[n]!=after[n]);added=sorted(after.keys()-before.keys())
        assert not before.keys()-after.keys()
        assert changed==['ua/empirecraft/armor/client/LayerScreen.class']
        assert added==['ua/empirecraft/armor/client/ArmorLabels.class','ua/empirecraft/armor/client/ArmorLanguages.class']
        assert json.loads(new.read('pack.mcmeta'))['pack']['pack_format']==15
    for folder in ('mods','server/mods'):assert (REPO/folder/candidate.name).read_bytes()==candidate.read_bytes()
    # Updating an installation that already removed Caupona must not add it back.
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'adm.json';data=json.loads((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text());data['modIds']=[r for r in data['modIds'] if r['id']!='caupona'];data['integrityHash']=integrity(data);write(path,data)
        unrelated=[r for r in data['modIds'] if r['id']!='empirecraft_armor']
        # Exercise an actual version replacement, not an already-current no-op.
        next(r for r in data['modIds'] if r['id']=='empirecraft_armor')['version']='1.0.0';data['integrityHash']=integrity(data);write(path,data)
        assert update(path,candidate)
        result=json.loads(path.read_text());assert integrity(result)==result['integrityHash']
        assert unrelated==[r for r in result['modIds'] if r['id']!='empirecraft_armor'] and not update(path,candidate)
        assert path.with_name(path.name+'.before-armor-1.0.1').exists()
    report=dict(status='passed',file=candidate.name,sha256=build.sha(candidate),previous_sha256=build.sha(OLD),changed_classes=changed,added_classes=added,all_server_classes_byte_identical=True,language_assertions=60,language_fixture_sha256=build.sha(ROOT/'tests/ArmorLanguageTest.java'),language_log_sha256=build.sha(ROOT/'build/language.log'),mixin_targets=5,adm_update_preserves_other_mods=True,graphical_client_tested=False,production_world_tested=False)
    write(REPORT,report);return report


def package():
    report=verify();candidate=ROOT/'dist'/build.NAME
    files={'INSTALL-UA.md':(ROOT/'MENU-FIX-UA.md').read_bytes(),'update_armor_adm.py':(ROOT/'update_armor_adm.py').read_bytes(),'verification.json':REPORT.read_bytes()}
    for side in ('client','server'):
        for suffix in ('','.sha256'):files[side+'/mods/'+candidate.name+suffix]=(ROOT/'dist'/(candidate.name+suffix)).read_bytes()
    files['REMOVE-OLD.txt']=b'Remove empirecraft-layered-armor-1.0.0.jar and its checksum from client/server mods before copying 1.0.1. Keep only one armor addon. No other mods or world data are replaced.\n'
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=ROOT/'dist/Empirecraft-Armor-Menu-Fix-1.0.1.zip';build.archive(target,files);checksum(target)
    sources={}
    for path in (ROOT/'src').rglob('*'):
        if path.is_file():sources[str(path.relative_to(ROOT))]=path.read_bytes()
    for name in ('build.py','test.py','menu_release.py','update_armor_adm.py','LICENSE','MENU-FIX-UA.md','tests/ArmorLanguageTest.java'):sources[name]=(ROOT/name).read_bytes()
    build.archive(ROOT/'dist/Empirecraft-Armor-Menu-Fix-1.0.1-sources.zip',sources);checksum(ROOT/'dist/Empirecraft-Armor-Menu-Fix-1.0.1-sources.zip')
    audit=ROOT/'audit/menu-1.0.1';audit.mkdir(parents=True,exist_ok=True)
    for name in ('language.log','mixins.log'):
        (audit/name).write_text('\n'.join(line.rstrip() for line in (ROOT/'build'/name).read_text().splitlines())+'\n')
    print('ARMOR_MENU_RELEASE_PASS',target,report['sha256'])


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--install',action='store_true');args=parser.parse_args()
    if args.install:install()
    package()
