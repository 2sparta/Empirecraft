"""Check local metadata migrations with synthetic data only."""
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import release
rows=json.loads(release.MANIFEST.read_text())
original=dict(osName='test',cpuInfo='test',ramInfo='test',gpuInfo='test',anticheatVersion='1',lastUpdated=1,
    resourcePackIds=[dict(filename='example.zip',sha256='example')],modIds=[dict(id='unrelated',version='1',sha256=None,filePresent=False)])
original['integrityHash']=release.integrity(original)
updated=release.prepare_adm(original,rows)
assert updated['integrityHash']==release.integrity(updated)
assert release.prepare_adm(updated,rows)==updated
for key in original:
    if key not in {'modIds','lastUpdated','integrityHash'}:assert updated[key]==original[key]
assert next(x for x in updated['modIds'] if x['id']=='unrelated')==original['modIds'][0]
lock=dict(mods=[dict(id='unrelated',version='1')],client_jar_count=1,server_jar_count=1)
merged=release.prepare_lock(lock,rows)
assert merged['mods'][0]==lock['mods'][0]
assert merged['client_jar_count']==1 and merged['server_jar_count']==1
assert release.prepare_lock(merged,rows)==merged
print('SHIP_RELEASE_MIGRATION_PASS: one cannon entry, integrity, preservation, idempotence')


existing=dict(mods=[dict(id='empirecraft_ship_cannons',version='1.1.0',custom='keep'),dict(id='other',version='4')],client_jar_count=2,server_jar_count=2)
patched=release.prepare_lock(existing,rows)
assert patched['client_jar_count']==2 and patched['server_jar_count']==2
assert patched['mods'][0]['custom']=='keep' and patched['mods'][1]==existing['mods'][1]
bad=dict(original,integrityHash='invalid')
try:release.prepare_adm(bad,rows)
except ValueError:pass
else:raise AssertionError('Invalid ADM must not be rewritten')
assert len(rows)==1 and rows[0]['id']=='empirecraft_ship_cannons'
print('SHIP_RELEASE_UPDATE_PASS: existing counts, custom metadata, invalid integrity rejected')

# The repository's .json ADM file is a text report; preserve its unrelated lines and order.
report='[System Info]\n'+''.join('  '+label+': '+str(original[key])+'\n' for label,key in [('OS','osName'),('CPU','cpuInfo'),('RAM','ramInfo'),('GPU','gpuInfo'),('Anticheat Version','anticheatVersion'),('Last Updated','lastUpdated'),('Integrity Hash','integrityHash')])+'[Mod IDs]\n  - unrelated                      @ 1               [] {nofile}\n[Resource Pack IDs]\n  - example.zip [example]\n'
# Report hashes use hexadecimal SHA-256; use a proper synthetic resource hash.
original['resourcePackIds'][0]['sha256']='a'*64;original['integrityHash']=release.integrity(original)
report=report.replace('[example]','['+'a'*64+']').replace(next(line.split(': ',1)[1] for line in report.splitlines() if 'Integrity Hash:' in line),original['integrityHash'])
parsed,kind=release.parse_adm(report);assert kind and parsed==original
new=release.prepare_adm(parsed,rows,sort_ids=False);text=release.render_adm(report,parsed,new,True)
assert release.parse_adm(text)[0]==new and '  - unrelated                      @ 1               [] {nofile}' in text
assert release.render_adm(text,new,new,True)==text
print('SHIP_ADM_REPORT_PASS: native report format, hash, unchanged rows, round-trip, idempotence')

# Apply only inside a temporary fixture, including the real paired JAR and obsolete-version removal.
import tempfile,shutil,contextlib,io
with tempfile.TemporaryDirectory() as temporary:
    base=Path(temporary);(base/'ship-cannons').mkdir();name=rows[0]['file']
    for folder in ['mods','server/mods']:
        target=base/folder;target.mkdir(parents=True)
        for suffix in ['', '.sha256']:shutil.copyfile(release.REPO/folder/(name+suffix),target/(name+suffix))
        (target/'empirecraft-ship-cannons-1.1.0.jar').write_bytes(b'old fixture')
    adm=base/'server/config/admanticheat/idfile/admanticheat_id_list.json';adm.parent.mkdir(parents=True);adm.write_text(report)
    (base/'modpack-lock.json').write_text(json.dumps(existing))
    real_repo,real_manifest=release.REPO,release.MANIFEST
    release.REPO=base;release.MANIFEST=base/'ship-cannons/release.json';release.MANIFEST.write_text(json.dumps(rows))
    try:
        sys.argv=['release.py','--apply','--server-home',str(base/'server')]
        with contextlib.redirect_stdout(io.StringIO()):release.main()
        first=adm.read_bytes();assert release.parse_adm(adm.read_text())[0]['integrityHash']==release.integrity(release.parse_adm(adm.read_text())[0])
        with contextlib.redirect_stdout(io.StringIO()):release.main()
        assert adm.read_bytes()==first and adm.with_suffix('.json.before-ship-cannons-1.2.0').read_text()==report
        for folder in ['mods','server/mods']:
            assert not (base/folder/'empirecraft-ship-cannons-1.1.0.jar').exists()
            assert (base/folder/'empirecraft-ship-cannons-1.1.0.jar.disabled').read_bytes()==b'old fixture'
        sys.argv=['release.py','--apply','--check','--server-home',str(base/'server')]
        with contextlib.redirect_stdout(io.StringIO()):release.main()
    finally:release.REPO,release.MANIFEST=real_repo,real_manifest
print('SHIP_INSTALLER_FIXTURE_PASS: native ADM format, backups, duplicate removal, second-run stability')
