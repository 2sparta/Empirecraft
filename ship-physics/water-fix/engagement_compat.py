"""Metadata-only compatibility update; retain the reviewed engagement implementation."""
from pathlib import Path
import hashlib,json,zipfile
from build import jar
r=Path(__file__).resolve().parent
pin=json.loads((r/'engagement.json').read_text());base=r/'inputs/empirecraft-engagement-1.1.1.jar'
assert hashlib.sha256(base.read_bytes()).hexdigest()==pin['base_sha256']
with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
old=files['META-INF/mods.toml'];files['META-INF/mods.toml']=old.replace(b'version="1.1.1"',b'version="1.1.2"').replace(b'versionRange="[1.2.2]"',b'versionRange="[1.2.3,1.3)"')
assert old!=files['META-INF/mods.toml']
output=r/'dist/empirecraft-engagement-1.1.2.jar';jar(output,files)
assert hashlib.sha256(output.read_bytes()).hexdigest()==pin['sha256']
print('ENGAGEMENT_METADATA_ONLY_PASS')
