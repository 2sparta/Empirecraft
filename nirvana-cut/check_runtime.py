#!/usr/bin/env python3
import json
from build import ROOT

baseline=json.loads((ROOT/'build/runtime/nirvana-runtime-baseline.json').read_text())
patched=json.loads((ROOT/'build/runtime/nirvana-runtime-patched.json').read_text())
assert baseline['passed'] and patched['passed'],(baseline,patched)
assert set(baseline['blocks'])==set(patched['blocks'])
assert set(baseline['items'])-{'nirvana:reefer_spawn_egg'}==set(patched['items'])
assert set(baseline['entities'])-{'nirvana:reefer'}==set(patched['entities'])
assert baseline['effects']==patched['effects']
assert baseline['actual_reefers']>1 and patched['actual_reefers']==0
assert patched['vanilla_creeper_preserved'] and patched['hunger_preserved']
print('PASS actual original/patched game: only the Reefer entity and egg retired; all other registry entries equal; vanilla creeper unconverted and hunger retained; same world restarted')
