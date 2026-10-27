#!/usr/bin/env python3
"""Side-specific upgrade ZIPs. No world data or other live server configs."""
import hashlib,json
from build import ROOT,REPO,NAMES,MOD,VERSION,archive

def main():
 for side in ['client','server']:
  base=REPO/'mods' if side=='client' else REPO/'server/mods'
  files={'README.md':(ROOT/'README.md').read_bytes(),'audit/removed.json':(ROOT/'audit/removed.json').read_bytes()}
  for name in [*NAMES.values(),*([MOD] if side=='server' else [])]:
   for suffix in ['', '.sha256']:files['mods/'+name+suffix]=(base/(name+suffix)).read_bytes()
  if side=='server':files['reference-configs/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
  files['REMOVE-OLD-JARS.txt']=('\n'.join([*NAMES,*[n.replace('empirecraft.2','empirecraft.1') for n in NAMES.values() if 'empirecraft.2' in n],*(['empirecraft-content-trim-1.0.0.jar'] if side=='server' else [])])+'\n').encode()
  files['SHA256SUMS.txt']=''.join(hashlib.sha256(v).hexdigest()+'  '+k+'\n' for k,v in sorted(files.items())).encode()
  archive(ROOT/f'dist/Empirecraft-Content-Trim-{VERSION}-{side}.zip',files)
 print('PASS separate verified server/client upgrade archives')
if __name__=='__main__':main()

