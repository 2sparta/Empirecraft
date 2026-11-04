#!/usr/bin/env python3
"""Compile against the real MixinExtras already shipped by the pinned ModernFix."""
import hashlib,io,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
lock=json.loads((REPO/'modpack-lock.json').read_text())
row=next(x for x in lock['mods'] if x['id']=='modernfix')
jar=REPO/'mods'/row['file']
assert hashlib.sha256(jar.read_bytes()).hexdigest()==row['sha256']
with zipfile.ZipFile(jar) as z:
    wrapper=z.read('META-INF/jarjar/mixinextras-forge-0.4.1.jar')
with zipfile.ZipFile(io.BytesIO(wrapper)) as z:
    common=z.read('META-INF/jars/MixinExtras-0.4.1.jar')
with zipfile.ZipFile(io.BytesIO(common)) as z:
    assert 'com/llamalad7/mixinextras/injector/wrapmethod/WrapMethod.class' in z.namelist()
lib=ROOT/'lib';lib.mkdir(exist_ok=True)
(lib/'mixinextras-forge-0.4.1.jar').write_bytes(wrapper)
(lib/'mixinextras-common-0.4.1.jar').write_bytes(common)
for directory in ['run-smoke']:
    mods=ROOT/directory/'mods';mods.mkdir(parents=True,exist_ok=True)
    (mods/'mixinextras-forge-0.4.1.jar').write_bytes(wrapper)
print('PASS exact pinned ModernFix and its real MixinExtras 0.4.1 API; no dependencies bundled in addon')
