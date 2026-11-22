"""Patch only the reviewed naval guide entries; preserve every unrelated entry from the installed handbook."""
from build import *
import importlib.util,re
raw=subprocess.check_output(['git','show',BASE+':mods/empirecraft-handbook-1.0.6.jar'],cwd=REPO)
with zipfile.ZipFile(io.BytesIO(raw)) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
ns='empirecraft_guidebook';spec=importlib.util.spec_from_file_location('book_builder',REPO/'handbook/build.py');book=importlib.util.module_from_spec(spec);spec.loader.exec_module(book)
text=(REPO/'handbook/guide.uk.md').read_text();chapter=text.split('## ship_contacts | Кораблі на мапі | minecraft:spyglass\n',1)[1].split('\n# leisure |',1)[0]
entry=dict(name='Кораблі на мапі',icon='minecraft:spyglass',category=ns+':map',sortnum=115,read_by_default=True,pages=book.pages(chapter))
spec=importlib.util.spec_from_file_location('book_layout',REPO/'handbook/layout.py');layout=importlib.util.module_from_spec(spec);spec.loader.exec_module(layout);layout.fit({'map/ship_contacts':entry})
replaced=[]
for locale in ['uk_ua','en_us']:
 prefix='assets/'+ns+'/patchouli_books/handbook/'+locale+'/entries/'
 files[prefix+'map/ship_contacts.json']=book.encode(entry)
 for name,raw in list(files.items()):
  if not name.startswith(prefix+'ships/') or not name.endswith('.json'):continue
  old=json.loads(raw);title=old.get('name','')
  if title=='Мітки JourneyMap' or title.startswith('Мапа та останнє'):
   old['pages']=book.pages(chapter);layout.fit({'legacy':old});files[name]=book.encode(old);replaced.append(name)
files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'version="1.0.6"',b'version="1.0.7"')
catalog=json.loads(files['empirecraft-handbook-catalog.json']);catalog['entries'].append(dict(id='map/ship_contacts',icon='minecraft:spyglass',category=ns+':map',recipes=[]));files['empirecraft-handbook-catalog.json']=book.encode(catalog)
name='empirecraft-handbook-1.0.7.jar';archive(R/'dist'/name,files)
(R/'book.json').write_text(json.dumps(dict(status='passed',pages=len(entry['pages']),replaced_legacy_entries=replaced,sha256=hashlib.sha256((R/'dist'/name).read_bytes()).hexdigest(),unchanged_other_entries=True),indent=2)+'\n')
print('SHIP_MAP_BOOK_PASS',len(entry['pages']))
