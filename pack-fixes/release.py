#!/usr/bin/env python3
"""Install the tested compatibility mod, update inventories/ADM and package the paired update."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
import build
from package_support import add_updates

ROOT=build.ROOT;REPO=build.REPO;NAME=build.NAME;DIST=ROOT/'dist'
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def checksum(path):path.with_suffix(path.suffix+'.sha256').write_text(build.sha(path)+'  '+path.name+'\n')
def evidence():
    candidate=DIST/NAME;digest=build.sha(candidate);runs=[]
    for profile in ['server','client']:
        data=json.loads((build.BUILD/(profile+'.json')).read_text());assert data['addon_sha256']==digest and data['assertions']>=6000
        assert all(data[k] for k in ['stone_pickaxe_tag','andesite_pickaxe_tag','oak_axe_tag'])
        runs.append({k:v for k,v in data.items() if k not in ['blocks','items']})
    unit=(build.BUILD/'FlagWindTest.log').read_text();languages=(build.BUILD/'TranslationPackTest.log').read_text();mixins=(build.BUILD/'mixins.log').read_text()
    assert 'FLAG_WIND_PASS' in unit and 'BUILTIN_TRANSLATIONS_PASS' in languages
    for target in ['FlagRenderer','NavalFlags','ServerPlayerGameMode','ServerGamePacketListenerImpl']:assert 'PACK_MIXIN_PASS' in mixins and target in mixins
    coverage=[]
    lock=json.loads((REPO/'modpack-lock.json').read_text())
    with zipfile.ZipFile(candidate) as overlay:
        for ns in ['overgeared','vs_sails','betsyross','empirecraft_engagement']:
            entry=next(r for r in lock['mods'] if r['id']==ns)
            with zipfile.ZipFile(REPO/'mods'/entry['file']) as original:
                english=json.loads(original.read('assets/'+ns+'/lang/en_us.json'));missing={}
                for language in ['uk_ua','ru_ru']:
                    name='assets/'+ns+'/lang/'+language+'.json';values=json.loads(original.read(name)) if name in original.namelist() else {}
                    name='builtin/translations/'+name
                    if name in overlay.namelist():values.update(json.loads(overlay.read(name)))
                    missing[language]=len(set(english)-set(values));assert not missing[language],(ns,language)
                coverage.append(dict(namespace=ns,english_keys=len(english),missing=missing))
    return dict(status='passed',version='1.0.0',jar_sha256=digest,native_profiles=runs,translation_coverage=coverage,
        checks=[unit.strip(),languages.strip(),*[s for s in mixins.splitlines() if s.startswith('PACK_MIXIN_PASS')]],
        original_mod_binaries_modified=False,
        limitations=['The original unexplained steel-arrow producer was not reproduced with an empty off hand. The regression installs a controlled misbehaving Forge callback and verifies suppression through actual useItemOn calls, including chests and both hands.',
        'Arrow suppression is scoped to synchronous bone-meal interaction callbacks; it does not delete existing arrows or unrelated delayed actions.',
        'Client tag overlays are tested in a disposable server harness; full graphical client and production world were not launched.'])
def install():
    report=evidence();candidate=DIST/NAME;raw=candidate.read_bytes();digest=report['jar_sha256']
    for folder in [REPO/'mods',REPO/'server/mods']:
        for old in folder.glob('empirecraft-pack-fixes-*.jar*'):
            if old.name not in [NAME,NAME+'.sha256']:old.unlink()
        shutil.copyfile(candidate,folder/NAME);checksum(folder/NAME)
    lockpath=REPO/'modpack-lock.json';lock=json.loads(lockpath.read_text());row=dict(id='empirecraft_pack_fixes',file=NAME,version='1.0.0',sides=['client','server'],source='local:pack-fixes',added=True,updated=True,
        pin_reason='Built-in reviewed Ukrainian/Russian translations, scoped bone-meal ammunition suppression, wind-aligned naval cloth and matching client/server tag repairs; source and exact native proof in pack-fixes/.')
    lock['mods']=[r for r in lock['mods'] if r['id']!=row['id']]+[row];write(lockpath,lock)
    adm_path=REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json';adm=json.loads(adm_path.read_text())
    adm['resourcePackIds']=[r for r in adm['resourcePackIds'] if r['filename']!='Empirecraft-Translations-uk-ru-1.1.0.zip']
    import importlib.util
    spec=importlib.util.spec_from_file_location('pack_adm',REPO/'ship-cannons/release.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    adm['integrityHash']=helper.integrity(adm);write(adm_path,adm)
    subprocess.run([sys.executable,REPO/'validation/reconcile_modpack.py','--apply'],check=True)
    lock=json.loads(lockpath.read_text());row=next(r for r in lock['mods'] if r['id']=='empirecraft_pack_fixes')
    path=REPO/'validation/installed-releases-2026-11-01.json';manifest=json.loads(path.read_text());manifest['releases']=[r for r in manifest['releases'] if r['id']!=row['id']]+[dict(row,artifact='pack-fixes/dist/'+NAME)];write(path,manifest)
    for relative,folder in [('seasonal-calendar/installed-mods.json','server/mods'),('forging-balance/installed-client-mods.json','mods')]:
        path=REPO/relative;values=[r for r in json.loads(path.read_text()) if not r['name'].startswith('empirecraft-pack-fixes-')]
        values.append(dict(name=NAME,path=folder+'/'+NAME,size=len(raw),sha=row['git_sha']));write(path,sorted(values,key=lambda r:r['name']))
    path=REPO/'seasonal-calendar/audit/coverage.json';coverage=json.loads(path.read_text());coverage['jar_sha256'][NAME]=digest;coverage['jar_count']=len(coverage['jar_sha256']);write(path,coverage)
    path=REPO/'native-content/retained-code.json';retained=json.loads(path.read_text())
    with zipfile.ZipFile(candidate) as z:retained[NAME]={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
    write(path,retained);write(DIST/'validation.json',report);write(REPO/'validation/pack-fixes-1.json',report)
def package():
    files={};add_updates(files,REPO)
    assert files,'Install the tested mod first'
    for relative,name in [('server/config/admanticheat/idfile/admanticheat_id_list.json','server/config/admanticheat/idfile/admanticheat_id_list.json'),('pack-fixes/dist/validation.json','validation.json')]:files[name]=(REPO/relative).read_bytes()
    files['SHA256SUMS.txt']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(files.items())).encode()
    target=DIST/'Empirecraft-Pack-Fixes-1.0.0-update.zip';build.archive(target,files);checksum(target)
    source={}
    for path in ROOT.rglob('*'):
        if path.is_file() and not {'build','dist','__pycache__'}.intersection(path.relative_to(ROOT).parts):source['pack-fixes/'+str(path.relative_to(ROOT))]=path.read_bytes()
    target=DIST/'Empirecraft-Pack-Fixes-1.0.0-source.zip';build.archive(target,source);checksum(target)
    print('PACK_FIXES_RELEASE_PASS',build.sha(DIST/NAME))
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');args=p.parse_args()
    if args.install:install()
    package()
if __name__=='__main__':main()
