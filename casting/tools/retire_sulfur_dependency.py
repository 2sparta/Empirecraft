#!/usr/bin/env python3
"""Change the server ore addon's dependency metadata without changing its code."""
import argparse,zipfile,tomllib,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('original',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
with zipfile.ZipFile(a.original) as z:files={n:z.read(n) for n in z.namelist()}
metadata=files['META-INF/mods.toml'].decode().replace('version="1.1.0"','version="1.1.1"');parts=metadata.split('[[dependencies.')
metadata=parts[0]+''.join('[[dependencies.'+v for v in parts[1:] if 'modId="sulfur_gunpowder"' not in v and 'modId = "sulfur_gunpowder"' not in v)
metadata+='\n[[dependencies.empirecraft_ore_retrogen]]\nmodId="empirecraft_casting"\nmandatory=true\nversionRange="[1.3.0,)"\nordering="AFTER"\nside="SERVER"\n';files['META-INF/mods.toml']=metadata.encode()
with zipfile.ZipFile(a.output,'w') as z:
 for name,raw in sorted(files.items()):
  entry=zipfile.ZipInfo(name,(2026, 11, 15, 6, 42, 10));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,raw)
with zipfile.ZipFile(a.output) as z:
 assert all(z.read(n)==raw for n,raw in files.items() if n.endswith('.class'))
 print(hashlib.sha256(a.output.read_bytes()).hexdigest())
