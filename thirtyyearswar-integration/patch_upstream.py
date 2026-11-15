"""Metadata-only compatibility patch, justified by audit/base-api.txt and runtime tests."""
import zipfile,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
ORIGINAL='epic-knights-thirty-years-war-1.20.1-forge-1.3.jar'
JAR='epic-knights-thirty-years-war-1.20.1-forge-1.3-empirecraft.1.jar'
def main():
 source=ROOT/ORIGINAL
 with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 before=dict(files);text=files['META-INF/mods.toml'].decode();assert 'versionRange="[10.11,)"' in text
 files['META-INF/mods.toml']=text.replace('version="1.3"','version="1.3-empirecraft.1"').replace('versionRange="[10.11,)"','versionRange="[10.10,)"').encode()
 target=REPO/'mods'/JAR
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
 assert [n for n in files if files[n]!=before[n]]==['META-INF/mods.toml']
 raw=target.read_bytes();digest=hashlib.sha256(raw).hexdigest()
 for folder in [REPO/'mods',REPO/'server/mods']:
  (folder/JAR).write_bytes(raw);(folder/(JAR+'.sha256')).write_text(digest+'  '+JAR+'\n')
  (folder/ORIGINAL).unlink(missing_ok=True);(folder/(ORIGINAL+'.sha256')).unlink(missing_ok=True)
 (ROOT/'audit/upstream.json').write_text(json.dumps(dict(original_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),patched_sha256=digest,changed_entries=['META-INF/mods.toml'],base_epic_knights='10.10',referenced_api_members=144,classes_changed=False),indent=2)+'\n')
if __name__=='__main__':main()
