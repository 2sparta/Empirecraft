#!/usr/bin/env python3
"""Package a verified client mod and its matching server ADM without changing server gameplay."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=build.REPO
def sha(raw):return hashlib.sha256(raw).hexdigest()
def archive(path,files,check):
 stream=io.BytesIO()
 with zipfile.ZipFile(stream,'w') as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 raw=stream.getvalue();checksum=sha(raw)+'  '+path.name+'\n'
 if check:assert path.read_bytes()==raw and path.with_suffix('.zip.sha256').read_text()==checksum,path
 else:path.parent.mkdir(exist_ok=True);path.write_bytes(raw);path.with_suffix('.zip.sha256').write_text(checksum)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args();report=REPO/'validation/epic-death-screen-1.json';result=json.loads(report.read_text());installed=REPO/'mods'/build.NAME
 assert result['status']=='passed' and result['installed_sha256']==sha(installed.read_bytes())
 assert result['core_checks']>=561 and result['sounds']==16 and result['native_client_classes_linked']==5
 lock=json.loads((REPO/'modpack-lock.json').read_text());row=next(r for r in lock['mods'] if r['id']=='epicdeathscreen');assert row['sides']==['client'] and row['sha256']==result['installed_sha256']
 files={'client/mods/'+build.NAME:installed.read_bytes(),'client/mods/'+build.NAME+'.sha256':installed.with_suffix('.jar.sha256').read_bytes(),'INSTALL-UA.md':(ROOT/'README.md').read_bytes(),'AUDIO-CREDITS.md':(ROOT/'AUDIO-CREDITS.md').read_bytes(),'LICENSE-upstream.txt':(ROOT/'LICENSE-upstream.txt').read_bytes(),'validation/epic-death-screen-1.json':report.read_bytes(),'metadata/modpack-lock.json':(REPO/'modpack-lock.json').read_bytes(),'server/config/admanticheat/idfile/admanticheat_id_list.json':(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()}
 pack='Empirecraft-Translations-uk-ru-1.1.0.zip';translation=REPO/'translations/dist'/pack
 adm=json.loads(files['server/config/admanticheat/idfile/admanticheat_id_list.json'])
 if not any(r.get('id')=='empirecraft_pack_fixes' and r.get('filePresent') for r in adm['modIds']):assert dict(filename=pack,sha256=sha(translation.read_bytes())) in adm['resourcePackIds']
 if not any(r.get('id')=='empirecraft_pack_fixes' and r.get('filePresent') for r in adm['modIds']):files['client/resourcepacks/'+pack]=translation.read_bytes();files['client/resourcepacks/'+pack+'.sha256']=translation.with_suffix('.zip.sha256').read_bytes()
 files['REMOVE-OLD.txt']=b'Remove any previous epicdeathscreen JAR and its checksum from client mods. Keep exactly the included version. Install no Epic Death Screen JAR on the server. Preserve config/epicdeathscreen and user phrase files. Apply the included server ADM list together with the client update.\n'
 # The bundled ADM also declares the combined seasonal client mod.
 seasonal=list((REPO/'mods').glob('empirecraft-seasonal-leaves-*.jar'))
 if seasonal:
     assert len(seasonal)==1
     leaf=seasonal[0]
     for side in ['client','server']:
         files[side+'/mods/'+leaf.name]=leaf.read_bytes()
         files[side+'/mods/'+leaf.name+'.sha256']=leaf.with_suffix('.jar.sha256').read_bytes()
     files['REMOVE-OLD-SEASONAL-LEAVES.txt']=b'Remove previous empirecraft-seasonal-leaves JARs and checksums from both mods folders. Install the same included version on client and server; preserve worlds and configs.\n'
 import importlib.util as packfix_importlib
 packfix_spec=packfix_importlib.spec_from_file_location('packfix_package_support',REPO/'pack-fixes/package_support.py')
 packfix_helper=packfix_importlib.module_from_spec(packfix_spec);packfix_spec.loader.exec_module(packfix_helper)
 packfix_helper.add_updates(files,REPO)
 files['SHA256SUMS.txt']=''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(files.items())).encode();archive(ROOT/'dist/Empirecraft-Epic-Death-Screen-1.zip',files,args.check)
 sources={p.relative_to(REPO).as_posix():p.read_bytes() for p in ROOT.rglob('*') if p.is_file() and not {'build','dist','__pycache__'}.intersection(p.relative_to(ROOT).parts)}
 sources['validation/epic-death-screen-1.json']=report.read_bytes();sources['.github/workflows/death-screen.yml']=(REPO/'.github/workflows/death-screen.yml').read_bytes();archive(ROOT/'dist/Empirecraft-Epic-Death-Screen-1-sources.zip',sources,args.check)
 print('DEATH_SCREEN_RELEASE_PASS',result['installed_sha256'])
if __name__=='__main__':main()
