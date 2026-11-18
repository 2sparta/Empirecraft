from pathlib import Path
import shutil,zipfile,json,hashlib
R=Path(__file__).resolve().parent;bundle=R/'build/bundle'
shutil.rmtree(bundle,ignore_errors=True);(bundle/'mods').mkdir(parents=True)
for n in ['README.uk.md','release.json','install.py']:shutil.copy2(R/n,bundle/n)
for row in json.loads((R/'release.json').read_text())['mods']:
 p=R/'dist'/row['file'] if row['id']=='knockedback' else R.parent/'mods'/row['file']
 assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256']
 shutil.copy2(p,bundle/'mods'/row['file'])
p=R/'dist/Empirecraft-KnockedBack-2-install.zip'
with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(bundle.rglob('*')):
  if f.is_file():z.write(f,f.relative_to(bundle))
p.with_suffix('.zip.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
print('RELEASE_PASS',p)
