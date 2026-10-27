#!/usr/bin/env python3
"""Update only the three replaced native jars and the new End guard in existing inventories."""
import argparse, copy, hashlib, importlib.util, json, tomllib, zipfile
from build import ROOT, REPO, NAMES, MOD, VERSION
from audit import write

def digest(path):
    raw=path.read_bytes()
    return {'size':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'sha512':hashlib.sha512(raw).hexdigest(),'git_sha':hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    paths=[REPO/'modpack-lock.json',REPO/'seasonal-calendar/installed-mods.json',REPO/'forging-balance/installed-client-mods.json',REPO/'seasonal-calendar/audit/coverage.json',REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json']
    original=[json.loads(path.read_text()) for path in paths];lock,server,client,coverage,adm=copy.deepcopy(original)
    spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    assert helper.integrity(adm)==adm['integrityHash'],'The existing integrity list must already be valid'
    changed=set();new_guard=False;changed_names=set(NAMES)|set(NAMES.values())|{MOD}
    for old,new in [*NAMES.items(),(MOD,MOD)]:
        path=REPO/'mods'/new;hashes=digest(path);assert path.read_bytes()==(REPO/'server/mods'/new).read_bytes()
        with zipfile.ZipFile(path) as archive:mod=tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods'][0]
        identity=mod['modId'];changed.add(identity)
        record=next((r for r in lock['mods'] if r['id']==identity),None)
        if record is None:
            assert identity=='empirecraft_no_end';record={'id':identity,'old_version':None,'added':True,'updated':False};lock['mods'].append(record);new_guard=True
        record.update(file=new,sides=['client','server'],source='local:end-removal',pin_reason='No End content, native End decor/mail and diamond/netherite knife exclusions; pinned rebuild and migration tests in end-removal/.',**hashes)
        if identity=='empirecraft_no_end':record['version']=VERSION
        for records,prefix in [(server,'server/mods/'),(client,'mods/')]:
            row=next((r for r in records if r['name'] in {old,new}),None)
            if row is None and identity!='empirecraft_no_end':
                family=old.split('-')[0]+'-';candidates=[r for r in records if r['name'].startswith(family)]
                assert len(candidates)==1,('Ambiguous native inventory entry',identity,candidates)
                row=candidates[0];changed_names.add(row['name'])
            if row is None:assert identity=='empirecraft_no_end';row={};records.append(row)
            row.update(name=new,path=prefix+new,size=hashes['size'],sha=hashes['git_sha'])
        coverage['jar_sha256'].pop(old,None);coverage['jar_sha256'][new]=hashes['sha256']
        row=next((r for r in adm['modIds'] if r['id']==identity),None)
        if row is None:assert identity=='empirecraft_no_end';row={'id':identity,'version':VERSION};adm['modIds'].append(row)
        row.update(sha256=hashes['sha256'],filePresent=True)
    if new_guard:lock['client_jar_count']+=1;lock['server_jar_count']+=1
    server.sort(key=lambda r:r['name']);client.sort(key=lambda r:r['name']);coverage['jar_count']=len(server);adm['integrityHash']=helper.integrity(adm)
    assert adm['resourcePackIds']==original[4]['resourcePackIds']
    assert all(row in adm['modIds'] for row in original[4]['modIds'] if row['id'] not in changed)
    assert all(row in lock['mods'] for row in original[0]['mods'] if row['id'] not in changed)
    for before,after in [(original[1],server),(original[2],client)]:assert all(row in after for row in before if row['name'] not in changed_names)
    assert {key:value for key,value in coverage['jar_sha256'].items() if key not in changed_names}=={key:value for key,value in original[3]['jar_sha256'].items() if key not in changed_names}
    for path,before,after in zip(paths,original,[lock,server,client,coverage,adm]):
        if args.check:assert before==after,'Stale End metadata: '+str(path)
        else:write(path,after)
    print('PASS End jar pins, client/server inventories and ADM; unrelated mod and resource-pack records preserved')

if __name__=='__main__':main()
