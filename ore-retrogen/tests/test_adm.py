"""Check the local ADM migration without publishing a machine's inventory."""
import copy
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import pin
original=dict(osName='test',cpuInfo='test',ramInfo='test',gpuInfo='test',anticheatVersion='1.9.3',lastUpdated=1,
    modIds=[dict(id='existing',version='1.0',sha256=None,filePresent=False)],resourcePackIds=[dict(filename='example.zip',sha256='example')])
original['integrityHash']=pin.integrity(original)
entry=json.loads(pin.ENTRY.read_text())
updated=pin.prepare_adm(original,entry)
assert updated['integrityHash']==pin.integrity(updated)
assert pin.prepare_adm(updated,entry)==updated
assert original['modIds']==[dict(id='existing',version='1.0',sha256=None,filePresent=False)]
assert [e for e in updated['modIds'] if e['id']!=entry['id']]==original['modIds']
for key in original:
    if key not in {'modIds','lastUpdated','integrityHash'}:assert updated[key]==original[key]
invalid=copy.deepcopy(original);invalid['integrityHash']='invalid'
try:pin.prepare_adm(invalid,entry)
except ValueError:pass
else:raise AssertionError('invalid integrity accepted')
duplicate=copy.deepcopy(original);duplicate['modIds'] += [entry.copy(),entry.copy()];duplicate['integrityHash']=pin.integrity(duplicate)
try:pin.prepare_adm(duplicate,entry)
except ValueError:pass
else:raise AssertionError('duplicate mod IDs accepted')
print('LOCAL_ADM_MIGRATION_PASS: integrity, preservation, idempotence and invalid inputs')
additions=json.loads(pin.MODS.read_text())
lock=dict(client_jar_count=1,server_jar_count=1,mods=[dict(id='existing',sides=['client','server'])],other='preserve')
merged=pin.prepare_lock(lock,additions)
assert merged['client_jar_count']==2 and merged['server_jar_count']==3
assert merged['mods'][0]==lock['mods'][0] and merged['other']=='preserve'
assert pin.prepare_lock(merged,additions)==merged
assert len(lock['mods'])==1
print('LOCAL_LOCK_MIGRATION_PASS: only ore entries, counts and idempotence')
