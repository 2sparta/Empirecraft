#!/usr/bin/env python3
"""Record this one client addon without rewriting unrelated lock or ADM rows."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    name='empirecraft-menu-art-1.0.0.jar';raw=(REPO/'mods'/name).read_bytes()
    assert raw==(ROOT/'dist'/name).read_bytes()
    p=REPO/'modpack-lock.json';lock=json.loads(p.read_text())
    row={'id':'empirecraft_menu_art','file':name,'sides':['client'],'version':'1.0.0','added':True,'updated':False,'source':'local:town-menu-art','sha256':hashlib.sha256(raw).hexdigest(),'sha512':hashlib.sha512(raw).hexdigest(),'size':len(raw),'git_sha':hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),'pin_reason':'Town Hall controls and background for /t menu and /t provision; client-only Forge render events. Requires Feudal 1.19.4 menu markers.'}
    lock['mods']=[m for m in lock['mods'] if m['id']!=row['id']]+[row];lock['mods'].sort(key=lambda m:m['id'])
    lock['client_jar_count']=len(list((REPO/'mods').glob('*.jar')))
    lock['server_jar_count']=len(list((REPO/'server/mods').glob('*.jar')))
    lock['updated_mod_count']=sum(bool(m.get('updated')) for m in lock['mods'])
    lock['town_menu_art_notes']='town-menu-art/README-UA.md'
    p.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+'\n')
    audit=json.loads((ROOT/'audit/build.json').read_text());audit['installer']=json.loads((ROOT/'audit/installer.json').read_text())
    audit['installed_client_mod']='mods/'+name;audit['installed_server_plugin']='server/plugins/empirecraft-feudal-1.19.4.jar'
    (REPO/'validation/town-menu-art-1.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
    print('TOWN_HALL_METADATA_PASS')
if __name__=='__main__':main()
