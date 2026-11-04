#!/usr/bin/env python3
"""Install only the whale-hitbox overlay and preserve unrelated lock/ADM rows."""
import argparse,hashlib,importlib.util,io,json,tomllib,zipfile
import build
ROOT=build.ROOT;REPO=build.REPO;NAME=build.NAME
OLD='accuratehitboxes-neoforge-2.4.2-1.20.1.jar'
def encode(d):return json.dumps(d,ensure_ascii=False,indent=2)+'\n'
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');parser.add_argument('--check',action='store_true');args=parser.parse_args()
    raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes();sha=build.sha(raw)
    assert raw==(build.BUILD/NAME).read_bytes() if args.apply else True
    assert build.sha(build.BASE.read_bytes())==build.BASE_SHA
    base=build.read(build.BASE);installed=build.read(REPO/'mods'/NAME)
    changed={'META-INF/MANIFEST.MF','META-INF/mods.toml','net/devra/accuratehitboxes/mixin/ProjectileUtilMixin.class','net/devra/accuratehitboxes/mixin/client/GameRendererMixin.class'}
    assert {n for n in base if base[n]!=installed[n]}==changed,'Unreviewed upstream changes'
    assert all(not n.startswith(('net/minecraft/','net/bettercombat/')) and 'Smoke' not in n for n in installed)
    assert any(m['modId']=='accuratehitboxes' and m['version']==build.VERSION for m in tomllib.loads(installed['META-INF/mods.toml'].decode())['mods'])
    assert 'accuratehitboxes.empirecraft.mixins.json' in installed['META-INF/MANIFEST.MF'].decode().replace('\r\n ','')
    git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    row={'id':'accuratehitboxes','version':build.VERSION,'file':NAME,'sides':['client','server'],'sha256':sha,'sha512':hashlib.sha512(raw).hexdigest(),'size':len(raw),'git_sha':git_sha}
    lockpath=REPO/'modpack-lock.json';original=json.loads(lockpath.read_text());planned=json.loads(lockpath.read_text())
    entry,=[r for r in planned['mods'] if r['id']=='accuratehitboxes'];entry.update(row,updated=True,source='local:accurate-hitboxes',pin_reason='Model-aware whale candidate discovery, Better Combat OBB contact/range checks and server Forge/Mohist model reach; ordinary damage events and weapon range retained.')
    planned['updated_mod_count']=sum(bool(r.get('updated')) for r in planned['mods'])
    spec=importlib.util.spec_from_file_location('ship_release',REPO/'ship-cannons/release.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';text=adm_path.read_text();adm,report=helper.parse_adm(text);updated=helper.prepare_adm(adm,[row],sort_ids=False)
    assert adm['resourcePackIds']==updated['resourcePackIds']
    assert [r for r in adm['modIds'] if r['id']!='accuratehitboxes']==[r for r in updated['modIds'] if r['id']!='accuratehitboxes']
    if args.apply:
        (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/build.json').write_bytes((build.BUILD/'build.json').read_bytes())
        lockpath.write_text(encode(planned));adm_path.write_text(helper.render_adm(text,adm,updated,report))
        (ROOT/'release.json').write_text(encode(row))
        for rel,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
            path=REPO/rel;data=json.loads(path.read_text());found,=[r for r in data if r['name'].startswith('accuratehitboxes-')]
            found.update(name=NAME,path=side+'/'+NAME,size=len(raw),sha=git_sha);path.write_text(encode(data))
        path=REPO/'seasonal-calendar/audit/coverage.json';data=json.loads(path.read_text());data['jar_sha256'].pop(OLD,None);data['jar_sha256'][NAME]=sha;path.write_text(encode(data))
        # No resources in this mod are excluded by the native-content pass. Its
        # new input is the complete reviewed overlay; trimming leaves it identical.
        path=REPO/'native-content/inputs.json';data=json.loads(path.read_text())
        for side in ['mods','server/mods']:data.pop(side+'/'+OLD,None);data[side+'/'+NAME]=sha
        path.write_text(encode(data))
        path=REPO/'native-content/retained-code.json';data=json.loads(path.read_text());data.pop(OLD,None)
        with zipfile.ZipFile(io.BytesIO(raw)) as z:data[NAME]={n:build.sha(z.read(n)) for n in z.namelist() if n.endswith('.class')}
        path.write_text(encode(data))
        for side in ['mods','server/mods']:(REPO/side/OLD).unlink(missing_ok=True);(REPO/side/(OLD+'.sha256')).unlink(missing_ok=True)
    if args.check:
        assert json.loads((ROOT/'audit/build.json').read_text())['sha256']==sha
        assert planned==original and adm==updated,'Stale lock or ADM'
        assert json.loads((ROOT/'release.json').read_text())==row
        for rel,side in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
            found,=[r for r in json.loads((REPO/rel).read_text()) if r['name'].startswith('accuratehitboxes-')]
            assert found==dict(name=NAME,path=side+'/'+NAME,size=len(raw),sha=git_sha)
        assert json.loads((REPO/'seasonal-calendar/audit/coverage.json').read_text())['jar_sha256'][NAME]==sha
        for side in ['mods','server/mods']:assert json.loads((REPO/'native-content/inputs.json').read_text())[side+'/'+NAME]==sha
    for side in ['mods','server/mods']:
        assert [p.name for p in (REPO/side).glob('accuratehitboxes-*.jar')]==[NAME]
        assert (REPO/side/(NAME+'.sha256')).read_text()==sha+'  '+NAME+'\n'
    path=ROOT/'dist/Empirecraft-Whale-Hitboxes-1.zip';path.parent.mkdir(exist_ok=True)
    entries={'INSTALL-UA.md':(ROOT/'README.md').read_bytes(),'server/config/admanticheat/idfile/admanticheat_id_list.json':adm_path.read_bytes()}
    for side in ['client','server']:
        entries[side+'/mods/'+NAME]=raw;entries[side+'/mods/'+NAME+'.sha256']=(sha+'  '+NAME+'\n').encode()
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,4,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    package=out.getvalue();checksum=build.sha(package)+'  '+path.name+'\n'
    if args.check:assert path.read_bytes()==package and path.with_suffix('.zip.sha256').read_text()==checksum
    else:path.write_bytes(package);path.with_suffix('.zip.sha256').write_text(checksum)
    print('WHALE_HITBOX_RELEASE_PASS',sha)
if __name__=='__main__':main()
