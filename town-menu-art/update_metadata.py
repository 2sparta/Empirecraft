#!/usr/bin/env python3
"""Record only the new paired icon module; keep all unrelated inventory records intact."""
from pathlib import Path
import hashlib,json
from build import MOD_NAME,PLUGIN_NAME
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    raw=(REPO/'mods'/MOD_NAME).read_bytes()
    assert raw==(REPO/'server/mods'/MOD_NAME).read_bytes()==(ROOT/'dist'/MOD_NAME).read_bytes()
    assert (REPO/'server/plugins'/PLUGIN_NAME).read_bytes()==(ROOT/'dist'/PLUGIN_NAME).read_bytes()
    path=REPO/'modpack-lock.json';lock=json.loads(path.read_text());mid='empirecraft_menu_art'
    previous=next((m for m in lock['mods'] if m['id']==mid),None)
    old_sides=set(previous['sides']) if previous else set()
    row={'id':mid,'file':MOD_NAME,'sides':['client','server'],'version':'1.1.0','added':previous.get('added',True) if previous else True,'updated':(previous.get('updated',False) if previous and previous['version']=='1.1.0' else bool(previous)),'source':'local:town-menu-art','sha256':hashlib.sha256(raw).hexdigest(),'sha512':hashlib.sha512(raw).hexdigest(),'size':len(raw),'git_sha':hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),'pin_reason':'46 native Towny control items with Ukrainian labels; same 1.1.0 JAR on both sides, Feudal 1.20.3, standard chest screen, recruitment click metadata preserved.'}
    if previous and previous['version']!='1.1.0':row['old_version']=previous['version']
    elif previous and 'old_version' in previous:row['old_version']=previous['old_version']
    lock['mods']=[m for m in lock['mods'] if m['id']!=mid]+[row];lock['mods'].sort(key=lambda m:m['id'])
    for side in ['client','server']:
        if side not in old_sides:lock[side+'_jar_count']+=1
    lock['updated_mod_count']=sum(bool(m.get('updated')) for m in lock['mods']);lock['town_menu_art_notes']='town-menu-art/README-UA.md'
    encode=lambda v:json.dumps(v,ensure_ascii=False,indent=2)+'\n'
    path.write_text(encode(lock))
    installed=REPO/'validation/installed-releases-2026-11-01.json'
    if installed.exists():
        data=json.loads(installed.read_text());data['releases']=[m for m in data['releases'] if m['id']!=mid]+[dict(row,artifact='town-menu-art/dist/'+MOD_NAME)]
        for key in ['client_jar_count','server_jar_count']:data[key]=lock[key]
        installed.write_text(encode(data))
    audit=json.loads((ROOT/'audit/build.json').read_text());audit['installer']=json.loads((ROOT/'audit/installer-1.1.json').read_text())
    audit['installed_client_mod']='mods/'+MOD_NAME;audit['installed_server_mod']='server/mods/'+MOD_NAME;audit['installed_server_plugin']='server/plugins/'+PLUGIN_NAME
    (REPO/'validation/town-menu-icons-1.1.json').write_text(encode(audit))
    print('NATIVE_TOWN_ICONS_METADATA_PASS')
if __name__=='__main__':main()
