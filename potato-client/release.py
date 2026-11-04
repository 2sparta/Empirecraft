#!/usr/bin/env python3
"""Package only after real compile, unit, client, uninstall and multiplayer checks pass."""
import hashlib,io,json,re,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
import smoke
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAME='empirecraft-potato-1.0.0.jar'

def digest(raw):return hashlib.sha256(raw).hexdigest()
def archive_bytes(files):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        for name,raw in sorted(files.items()):
            entry=zipfile.ZipInfo(name,(2026,11,4,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw)
    return out.getvalue()

def main():
    jar=ROOT/'build/libs'/NAME
    raw=jar.read_bytes()
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        assert z.testzip() is None
        names=z.namelist();assert not any('/smoke/' in n or n.endswith('Test.class') or n.endswith('.jar') for n in names)
        assert 'version="1.0.0"' in z.read('META-INF/mods.toml').decode()
        assert 'displayTest="IGNORE_ALL_VERSION"' in z.read('META-INF/mods.toml').decode()
        mixins=json.loads(z.read('empirecraft-potato.mixins.json'))
        assert mixins['client']==['OptionInstanceMixin','OptionsSaveMixin'] and not mixins.get('mixins')
        assert b'm_231551_' in z.read('ua/empirecraft/potato/mixin/OptionInstanceMixin.class')
        assert b'm_92169_' in z.read('ua/empirecraft/potato/mixin/OptionsSaveMixin.class')
        for n in names:
            if n.endswith('.class'):
                code=z.read(n);assert int.from_bytes(code[6:8],'big')==61
                for forbidden in [b'net/minecraft/world/entity/',b'net/minecraft/network/protocol/',b'net/minecraft/server/level/',b'net/minecraftforge/network/']:
                    assert forbidden not in code,(n,forbidden)
            if n.startswith('assets/'):assert n=='assets/' or n.startswith('assets/empirecraft_potato/'),n
        catalogs=[json.loads(z.read('assets/empirecraft_potato/lang/'+lang+'.json')) for lang in ['en_us','uk_ua','ru_ru']]
        assert all(c.keys()==catalogs[0].keys() for c in catalogs)
    suites=list((ROOT/'build/test-results/test').glob('TEST-*.xml'));assert len(suites)>=2
    count=0
    for p in suites:
        s=ET.parse(p).getroot();assert int(s.get('failures',0))==int(s.get('errors',0))==int(s.get('skipped',0))==0;count+=int(s.get('tests',0))
    assert count>=10
    phases={phase:smoke.verify(phase) for phase in ['enabled','off','absent','multiplayer','compat']}
    lock=json.loads((REPO/'modpack-lock.json').read_text());assert not any(m['id']=='empirecraft_potato' for m in lock['mods'])
    assert not any((REPO/folder/NAME).exists() for folder in ['mods','server/mods'])
    adm_raw=(REPO/'server/config/admanticheat/admanticheat_config.json').read_text()
    adm=json.loads(re.sub(r'(?m)^\s*//.*$','',re.sub(r'/\*.*?\*/','',adm_raw,flags=re.S)))
    report={'version':'1.0.0','client_only':True,'optional':True,'default_enabled':True,'jar_sha256':digest(raw),
        'unit_tests':count,'real_client':phases,'render_only_six_options':True,'stored_preferences_preserved':True,
        'new_entity_culling':False,'simulation_or_network_changes':False,'existing_optimizers_duplicated':False,
        'adm_repository_id_validation':adm['enableIdValidation'],'adm_repository_code_validation':adm['enableExperimentalClientCodeValidation'],
        'full_modpack_graphical_gameplay':'manual_acceptance_required','weak_hardware_fps_benchmark':'not_run'}
    files={'client/mods/'+NAME:raw,'client/mods/'+NAME+'.sha256':(digest(raw)+'  '+NAME+'\n').encode(),
        'README.md':(ROOT/'README.md').read_bytes(),'validation.json':(json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode()}
    sources={}
    for p in sorted(ROOT.rglob('*')):
        if p.is_file() and not any(part in ['build','.gradle','lib','run-smoke','run-server','dist','__pycache__'] for part in p.relative_to(ROOT).parts):
            sources[p.relative_to(REPO).as_posix()]=p.read_bytes()
    sources['modpack-lock.json']=(REPO/'modpack-lock.json').read_bytes()
    files['Empirecraft-Potato-1.0.0-sources.zip']=archive_bytes(sources)
    files['SHA256SUMS.txt']=''.join(digest(v)+'  '+k+'\n' for k,v in sorted(files.items())).encode()
    output=ROOT/'dist';output.mkdir(exist_ok=True)
    for name,data in [(NAME,raw),('validation.json',files['validation.json']),('Empirecraft-Potato-1.0.0-client.zip',archive_bytes(files))]:
        p=output/name;p.write_bytes(data);p.with_suffix(p.suffix+'.sha256').write_text(digest(data)+'  '+name+'\n')
    print('PASS client-only optional addon:',count,'unit tests; all real-client phases; dedicated server without addon')
    print('Client ZIP SHA-256:',digest((output/'Empirecraft-Potato-1.0.0-client.zip').read_bytes()))
if __name__=='__main__':main()
