#!/usr/bin/env python3
"""Install the freshly tested medieval Create into both repository mod directories."""
import hashlib
import json
from pathlib import Path
import re
import shutil
from build import NAME, VERSION
from update_adm import expected, parse, prepare

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def main():
    jar=ROOT/'dist'/NAME
    raw=jar.read_bytes()
    digest=hashlib.sha256(raw).hexdigest()
    git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    for folder in ['mods','server/mods']:
        target=REPO/folder;target.mkdir(parents=True,exist_ok=True)
        for old in ['create-1.20.1-6.0.8-medieval.1.jar','create-1.20.1-6.0.8-medieval.1.jar.sha256']:
            (target/old).unlink(missing_ok=True)
        shutil.copyfile(jar,target/NAME)
        (target/(NAME+'.sha256')).write_text(digest+'  '+NAME+'\n')
    (REPO/'mods/vs_sails-1.20.1-0.3.2-forge.jar').unlink(missing_ok=True)
    lock_path=REPO/'modpack-lock.json'
    lock=json.loads(lock_path.read_text())
    entry=next((x for x in lock['mods'] if x['id']=='create'),None)
    if entry is None:
        entry={'id':'create'};lock['mods'].append(entry)
    entry.update(file=NAME,sides=['client','server'],version=VERSION,old_version='6.0.8-medieval.1',updated=False,source='create',git_sha=git_sha,sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),pin_reason='Empirecraft medieval profile; preserves Valkyrien Skies mixin anchors, source and tests in medieval-create/')
    lock['client_jar_count']=93;lock['server_jar_count']=99
    lock_path.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+'\n')
    adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    current=parse(adm_path.read_text(encoding='utf-8-sig'))
    adm_path.write_text(json.dumps(prepare(current,expected(jar)),ensure_ascii=False,indent=2)+'\n')
    report_path=REPO/'validation/medieval-create-6.0.8-medieval.2.json'
    report=json.loads(report_path.read_text())
    report.update(sha256=digest,version=VERSION)
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    evidence=REPO/'validation/medieval-create-mixin-fix.md'
    evidence.write_text(re.sub(r'JAR SHA-256: `[a-f0-9]{64}`', 'JAR SHA-256: `'+digest+'`',evidence.read_text()))
    assert (REPO/'mods'/NAME).read_bytes()==(REPO/'server/mods'/NAME).read_bytes()
    print('Installed identical Create copies:',digest)

if __name__=='__main__':main()
