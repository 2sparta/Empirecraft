#!/usr/bin/env python3
"""Inspect the exact installed Overgeared bytecode before choosing native removal points."""
from pathlib import Path
import json
import subprocess
import zipfile

jar=Path('mods/overgeared-forge-1.20.1-1.6.36.jar')
terms=['nether_alloy_furnace','diamond_upgrade','netherite_alloy','diamond_arrow','diamond_shard','nether_tool_cast','casting_furnace']
with zipfile.ZipFile(jar) as z:
    print('=== LANG ===')
    lang=json.loads(z.read('assets/overgeared/lang/en_us.json'))
    print(json.dumps({k:v for k,v in lang.items() if any(t in k for t in terms)},indent=2))
    classes=[n for n in z.namelist() if n.endswith('.class')]
    print('=== CLASSES ===\n'+'\n'.join(classes))
    selected=[]
    print('=== REFERENCES ===')
    for n in classes:
        raw=z.read(n)
        hits=[t for t in terms if t.encode() in raw or t.upper().encode() in raw]
        if hits:
            print(n,','.join(hits))
            selected.append(n)
    for n in selected:
        print('=== BYTECODE '+n+' ===',flush=True)
        subprocess.run(['javap','-p','-c','-classpath',str(jar),n[:-6].replace('/','.')],check=True)
    print('=== RELATED RESOURCES ===')
    for n in z.namelist():
        if n.endswith('.json') and '/lang/' not in n and any(t.encode() in z.read(n) or t in n for t in terms):
            print(n,z.read(n).decode(errors='replace'))
