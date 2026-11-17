import hashlib,json,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME,archive,sha
import update_installation as u
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def main():
 raw=(ROOT/'dist'/NAME).read_bytes();digest=sha(ROOT/'dist'/NAME);audit=json.loads((ROOT/'audit/runtime.json').read_text());assert audit['status']=='passed' and audit['mod_sha256']==digest
 lock=json.loads((REPO/'modpack-lock.json').read_text());old=next((r for r in lock['mods'] if r['id']=='chineseweapons'),{});row=dict(old,id='chineseweapons',file=NAME,version='1.2.1.6-empirecraft.1',sides=['client','server'],added=True,sha256=digest,sha512=hashlib.sha512(raw).hexdigest(),size=len(raw),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),source='local:chinese-weapons',updated=True,pin_reason='User-supplied Chinese Weapons: native component armor assembly, TFC head forging, PR149 profiles, no magical effects/enchantments or high-tier armor; original art and attribution preserved.')
 lock['mods']=[row if r['id']=='chineseweapons' else r for r in lock['mods']];lock['mods']=[r for r in lock['mods'] if r['id']!='chineseweapons']+[row];lock['mods'].sort(key=lambda r:r['id']);lock['updated_mod_count']=sum(bool(r.get('updated')) for r in lock['mods']);lock['client_jar_count']=len(list((REPO/'mods').glob('*.jar')));lock['server_jar_count']=len(list((REPO/'server/mods').glob('*.jar')));write(REPO/'modpack-lock.json',lock)
 p=REPO/'validation/installed-releases-2026-11-01.json';d=json.loads(p.read_text());d['releases']=[r for r in d['releases'] if r['id']!='chineseweapons']+[dict(row,artifact='mods/'+NAME)];d['client_jar_count']=lock['client_jar_count'];d['server_jar_count']=lock['server_jar_count']
 write(p,d)
 for folder,name in [('mods','forging-balance/installed-client-mods.json'),('server/mods','seasonal-calendar/installed-mods.json')]:
  p=REPO/name;d=[r for r in json.loads(p.read_text()) if not r['name'].startswith('chineseweapons-')];d.append(dict(name=NAME,path=folder+'/'+NAME,size=len(raw),sha=row['git_sha']));write(p,sorted(d,key=lambda r:r['name']))
 p=REPO/'seasonal-calendar/audit/coverage.json';d=json.loads(p.read_text());d['jar_sha256']={k:v for k,v in d['jar_sha256'].items() if not k.startswith('chineseweapons-')};d['jar_sha256'][NAME]=digest;d['jar_count']=len(d['jar_sha256']);write(p,d)
 p=REPO/'native-content/retained-code.json';d={k:v for k,v in json.loads(p.read_text()).items() if not k.startswith('chineseweapons-')}
 with zipfile.ZipFile(ROOT/'dist'/NAME) as z:d[NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
 write(p,d)
 release=dict(id='chineseweapons',file=NAME,version='1.2.1.6-empirecraft.1',sha256=digest,files={});files={}
 for side in ['client','server']:
  payload={'mods/'+NAME:raw,'mods/'+NAME+'.sha256':(ROOT/'dist'/(NAME+'.sha256')).read_bytes()};release['files'][side]={n:hashlib.sha256(v).hexdigest() for n,v in payload.items()};files.update({side+'/'+n:v for n,v in payload.items()})
 adm=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=adm.read_text(encoding='utf-8-sig');before,report=u.parse_adm(text);after=u.adm_update(before,release);assert [r for r in before['modIds'] if r['id']!='chineseweapons']==[r for r in after['modIds'] if r['id']!='chineseweapons'];adm.write_text(u.render_adm(text,before,after,report))
 write(ROOT/'release.json',release)
 for n in ['release.json','update_installation.py','README.uk.md','NOTICE.txt']:files[n]=(ROOT/n).read_bytes()
 for p in (ROOT/'audit').glob('*.json'):
  if p.name not in ['bundle.json','launcher.json']:files['verification/'+p.name]=p.read_bytes()
 files['SHA256SUMS.txt']=''.join(hashlib.sha256(v).hexdigest()+'  '+n+'\n' for n,v in sorted(files.items())).encode();p=ROOT/'dist/Empirecraft-Chinese-Weapons-1-install.zip';archive(p,files);p.with_suffix('.zip.sha256').write_text(sha(p)+'  '+p.name+'\n');print('CHINESE_RELEASE_PASS',sha(p),p.stat().st_size)
if __name__=='__main__':main()
