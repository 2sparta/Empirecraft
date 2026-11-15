"""Package both sides, preserving unrelated inventory and anticheat entries."""
import argparse, copy, hashlib, json, zipfile
from pathlib import Path
import update_installation as u
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
IDS=['handson_delight','diegesis','farmersdelight','farmersrespite']
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(u.encode(v))
def rows():
    import tomllib
    result=[]
    for p in sorted((REPO/'mods').glob('*.jar')):
        with zipfile.ZipFile(p) as z:
            data=tomllib.loads(z.read('META-INF/mods.toml').decode()) if 'META-INF/mods.toml' in z.namelist() else {}
        for mod in data.get('mods',[]):
            if mod['modId'] in IDS:
                raw=p.read_bytes();result.append(dict(id=mod['modId'],file=p.name,version=mod['version'],sha256=u.sha(raw),sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),sides=['client','server'],source='local:tea-integration',updated=True,pin_reason='Hands-On kitchen with Farmer\'s Respite, TFC ingredients and Serene Seasons; see tea-integration/audit.'))
    assert sorted(r['id'] for r in result)==sorted(IDS)
    return result
def package():
    mods=rows();main=next(r for r in mods if r['id']=='handson_delight');release={k:main[k] for k in ['id','version','file','sha256']};release.update(additional_mods=[{k:r[k] for k in ['id','version','file','sha256']} for r in mods if r!=main],files={})
    generated=json.loads((ROOT/'audit/generated.json').read_text());files={}
    for side,root in [('client',REPO),('server',REPO/'server')]:
        payload={}
        for row in mods:
            for name in ['mods/'+row['file'],'mods/'+row['file']+'.sha256']:payload[name]=(root/name).read_bytes()
        for name,digest in generated['files'].items():
            raw=(root/name).read_bytes();assert u.sha(raw)==digest,name;payload[name]=raw
        release['files'][side]={n:u.sha(v) for n,v in payload.items()}
        files.update({side+'/'+n:v for n,v in payload.items()})
    return release,files
def install_metadata(release):
    mods=rows();changed={r['id'] for r in mods if r['id']!='farmersrespite'}
    # Farmer's Respite is deliberately byte-for-byte unchanged; keep its original provenance.
    for name,key in [('modpack-lock.json','mods'),('validation/installed-releases-2026-11-01.json','releases')]:
        p=REPO/name;data=json.loads(p.read_text());order=[r['id'] for r in data[key]];old={r['id']:r for r in data[key]}
        for r in mods:
            if r['id'] not in changed:continue
            row=dict(old.get(r['id'],{}));row.update(r)
            if key=='releases':row['artifact']='mods/'+r['file']
            old[r['id']]=row
        data[key]=sorted(old.values(),key=lambda r:r['id']) if key=='mods' else [old[id] for id in order]+[old[id] for id in old if id not in order]
        if key=='mods':data['updated_mod_count']=sum(bool(v.get('updated')) for v in data[key])
        else:
            for old in (REPO/'disabled-mods/tea-2026-11-15/client/mods').glob('*.jar'):
                for folder in ['mods/','server/mods/']:
                    path=folder+old.name
                    if path not in data.setdefault('retired',[]):data['retired'].append(path)
        for side,folder in [('client','mods'),('server','server/mods')]:data[side+'_jar_count']=len(list((REPO/folder).glob('*.jar')))
        write(p,data)
    prefixes=('FarmersDelight-','diegesis-','handson_delight-')
    for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
        p=REPO/name;data=json.loads(p.read_text());data=[v for v in data if not v['name'].startswith(prefixes)]
        data.extend(dict(name=r['file'],path=folder+'/'+r['file'],size=r['size'],sha=r['git_sha']) for r in mods if r['id'] in changed);write(p,sorted(data,key=lambda v:v['name']))
    p=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(p.read_text());data['jar_sha256']={k:v for k,v in data['jar_sha256'].items() if not k.startswith(prefixes)};data['jar_sha256'].update({r['file']:r['sha256'] for r in mods if r['id'] in changed});data['jar_count']=len(data['jar_sha256']);write(p,data)
    p=REPO/'native-content/retained-code.json';data=json.loads(p.read_text());data={k:v for k,v in data.items() if not k.startswith(prefixes)}
    for row in mods:
        if row['id'] not in changed:continue
        with zipfile.ZipFile(REPO/'mods'/row['file']) as z:data[row['file']]={n:u.sha(z.read(n)) for n in z.namelist() if n.endswith('.class')}
    write(p,data)
    p=REPO/'native-content/inputs.json';data=json.loads(p.read_text());data={k:v for k,v in data.items() if not Path(k).name.startswith('FarmersDelight-')}
    fd=next(r for r in mods if r['id']=='farmersdelight');baseline=ROOT/'dist/native-inputs'/fd['file'];digest=u.sha(baseline.read_bytes())
    for prefix in ['mods/','server/mods/']:data[prefix+fd['file']]=digest
    write(p,data)
    adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';before=adm.read_text(encoding='utf-8-sig');original,report=u.parse_adm(before);updated=u.adm_update(original,release);adm.write_text(u.render_adm(before,original,updated,report))
    assert [v for v in original['modIds'] if v['id'] not in IDS]==[v for v in updated['modIds'] if v['id'] not in IDS]
    assert original['resourcePackIds']==updated['resourcePackIds']
    write(ROOT/'audit/installed.json',dict(status='passed',mods={r['id']:r['sha256'] for r in mods},unrelated_adm_rows_preserved=True))
def main():
    p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');a=p.parse_args();release,files=package()
    runtime=json.loads((ROOT/'audit/runtime.json').read_text());assert runtime['status']=='passed' and runtime['probe_sha256']==u.sha((ROOT/'Probe.java').read_bytes()) and runtime['generated_sha256']==u.sha((ROOT/'audit/generated.json').read_bytes())
    for row in rows():assert runtime['mod_sha256'][row['file']]==row['sha256']
    if a.install:install_metadata(release)
    write(ROOT/'release.json',release)
    for name in ['release.json','update_installation.py','README.uk.md']:files[name]=(ROOT/name).read_bytes()
    for f in (ROOT/'audit').glob('*.json'):files['verification/'+f.name]=f.read_bytes()
    files['SHA256SUMS.txt']=''.join(u.sha(v)+'  '+n+'\n' for n,v in sorted(files.items())).encode()
    target=ROOT/'dist/Empirecraft-Tea-and-HandsOn-1-install.zip';target.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
    target.with_suffix('.zip.sha256').write_text(u.sha(target.read_bytes())+'  '+target.name+'\n');print('TEA_RELEASE_PASS',u.sha(target.read_bytes()))
if __name__=='__main__':main()
