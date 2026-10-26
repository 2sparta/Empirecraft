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
assert merged['client_jar_count']==3 and merged['server_jar_count']==3
assert release.prepare_lock(merged,rows)==merged
print('SHIP_RELEASE_MIGRATION_PASS: exact two entries, integrity, preservation, idempotence')
