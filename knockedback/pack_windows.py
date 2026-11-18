"""Refresh the application inside the already verified portable Windows runtime."""
import hashlib,shutil,zipfile,json
from pathlib import Path
R=Path(__file__).resolve().parent;L=R.parent/'launcher';folder=L/'build/windows-portable/Empirecraft'
assert (folder/'Empirecraft.exe').read_bytes()[:2]==b'MZ'
assert json.loads((folder/'python-runtime-source.json').read_text())['sha256']=='355ee61b8207b91574491b1c6dc9b0ebb4408fc56bfafe498f3ef439314b52dc'
for name in ['empirecraft_launcher','resources']:
 target=folder/'application'/name
 if target.exists():shutil.rmtree(target)
 shutil.copytree(L/name,target,ignore=shutil.ignore_patterns('__pycache__'))
shutil.copy2(L/'README.md',folder/'README.md')
p=L/'dist/Empirecraft-Launcher-0.1.3-Windows-x64.zip'
with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for f in sorted(folder.rglob('*')):
  if f.is_file():z.write(f,'Empirecraft/'+f.relative_to(folder).as_posix())
with zipfile.ZipFile(p) as z:
 assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
 for name in ['profiles.py','app.py','__init__.py']:
  assert z.read('Empirecraft/application/empirecraft_launcher/'+name)==(L/'empirecraft_launcher'/name).read_bytes()
 assert z.read('Empirecraft/application/resources/bootstrap.json')==(L/'resources/bootstrap.json').read_bytes()
sha=hashlib.sha256(p.read_bytes()).hexdigest();p.with_suffix('.zip.sha256').write_text(sha+'  '+p.name+'\n')
(R/'audit/windows-package.json').write_text(json.dumps(dict(sha256=sha,size=p.stat().st_size,source_and_bootstrap_match=True,windows_runtime_execution_tested=False),indent=2)+'\n')
print('WINDOWS_PACKAGE_PASS',p)
