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
compat=lib/'compat';compat.mkdir(exist_ok=True)
for mid in ['embeddium','dynamic_fps']:
    pinned=next(x for x in lock['mods'] if x['id']==mid)
    source=REPO/'mods'/pinned['file'];raw=source.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==pinned['sha256']
    refmap='potato-development-'+mid+'.refmap.json'
    # Production SRG mixins without a refmap need an identity refmap to activate Sponge's
    # standard SRG -> official-name development adapter. No class or original file is edited.
    output=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(raw)) as original,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
        for name in original.namelist():
            data=original.read(name)
            if name.endswith('.mixins.json') or name.endswith('.mixin.json'):
                config=json.loads(data)
                if not config.get('refmap'):config['refmap']=refmap;data=json.dumps(config).encode()
            target.writestr(name,data)
        target.writestr(refmap,json.dumps({'mappings':{},'data':{}}))
    with zipfile.ZipFile(io.BytesIO(output.getvalue())) as transformed,zipfile.ZipFile(io.BytesIO(raw)) as original:
        for name in original.namelist():
            if name.endswith('.class'):assert transformed.read(name)==original.read(name)
    (compat/pinned['file']).write_bytes(output.getvalue())
print('PASS exact pinned ModernFix and its real MixinExtras 0.4.1 API; no dependencies bundled in addon')
