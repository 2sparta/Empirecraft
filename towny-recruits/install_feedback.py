"""Install the verified unified release into both repository sides without broad reconciliation."""
import hashlib, importlib.util, json, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def write(path,value):path.write_bytes(encode(value))
def install(helper):
    raw=(ROOT/'dist/2.0.1/TownyRecruits.jar').read_bytes();digest=hashlib.sha256(raw).hexdigest();name='TownyRecruits.jar'
    audit=json.loads((ROOT/'audit/feedback-2.0.1.json').read_text());assert audit['status']=='passed' and audit['sha256']==digest
    # The previous installed bytes already have a permanent, versioned backup.
    old=(ROOT/'dist/2.0.0'/name).read_bytes()
    for folder in [REPO/'mods',REPO/'server/mods']:
        assert (folder/name).read_bytes() in [old,raw],('Unexpected installed TownyRecruits',folder)
    blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    fields=dict(version='2.0.1',file=name,sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=blob,old_version='2.0.0',updated=True,
        pin_reason='Compiled and native-tested recruitment feedback, safe placement, shared provisions, durable dismissal and Towny confirmation compatibility. See towny-recruits/audit/feedback-2.0.1.json.')
    writes={}
    for folder in [REPO/'mods',REPO/'server/mods']:
        writes[folder/name]=raw;writes[folder/(name+'.sha256')]=(digest+'  '+name+'\n').encode()
    for path,key in [(REPO/'modpack-lock.json','mods'),(REPO/'validation/installed-releases-2026-11-01.json','releases')]:
        data=json.loads(path.read_text());matches=[v for v in data[key] if v['id']=='townyrecruits'];assert len(matches)==1
        matches[0].update(fields)
        if key=='releases':matches[0]['artifact']='towny-recruits/dist/2.0.1/'+name
        else:data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
        writes[path]=encode(data)
    for path,folder in [(REPO/'forging-balance/installed-client-mods.json','mods'),(REPO/'seasonal-calendar/installed-mods.json','server/mods')]:
        data=json.loads(path.read_text());matches=[v for v in data if v['name']==name];assert len(matches)==1
        matches[0].update(path=folder+'/'+name,size=len(raw),sha=blob);writes[path]=encode(data)
    path=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(path.read_text());assert name in data['jar_sha256'];data['jar_sha256'][name]=digest;writes[path]=encode(data)
    path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';before=path.read_text();original,report=helper.parse_adm(before)
    updated=helper.prepare_adm(original,[dict(id='townyrecruits',version='2.0.1',sha256=digest)],sort_ids=False)
    assert [r for r in original['modIds'] if r['id']!='townyrecruits']==[r for r in updated['modIds'] if r['id']!='townyrecruits']
    assert original['resourcePackIds']==updated['resourcePackIds']
    writes[path]=helper.render_adm(before,original,updated,report).encode()
    for path,data in writes.items():path.write_bytes(data)
    print('RECRUIT_FEEDBACK_INSTALLED_PASS',digest)
