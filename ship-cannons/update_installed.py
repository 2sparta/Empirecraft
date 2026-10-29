#!/usr/bin/env python3
"""Refresh the two inventories and seasonal hashes after the release installer updates lock/ADM."""
import hashlib,json
from pathlib import Path
from release import ROOT,REPO,MODS

def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
for mid,version,name,old in MODS:
 raw=(REPO/'mods'/name).read_bytes();assert raw==(REPO/'server/mods'/name).read_bytes()
 git=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest();sha=hashlib.sha256(raw).hexdigest()
 for path in [REPO/'seasonal-calendar/installed-mods.json',REPO/'forging-balance/installed-client-mods.json']:
  if not path.exists():continue
  rows=json.loads(path.read_text());matches=[r for r in rows if r['name'] in {name,old}];assert len(matches)<=1
  if matches:
   row=matches[0];row.update(name=name,sha=git,size=len(raw))
   if 'path' in row:row['path']=str(Path(row['path']).with_name(name))
   write(path,rows)
 path=REPO/'seasonal-calendar/audit/coverage.json'
 if path.exists():
  data=json.loads(path.read_text());hashes=data['jar_sha256']
  if old in hashes or name in hashes:
   hashes.pop(old,None);hashes[name]=sha;data['jar_count']=len(hashes);write(path,data)
 # Disabled installer backups stay outside the release; the previous tracked JARs are retired.
 for side in ['mods','server/mods']:
  (REPO/side/(old+'.disabled')).unlink(missing_ok=True)
print('Installed Ship Cannons inventories and seasonal hashes verified')
