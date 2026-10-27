#!/usr/bin/env python3
"""Package the two native mods, matching metadata, build sources and install instructions."""
from build import ROOT,REPO,pack,sha
import json
def main():
 rows=json.loads((ROOT/'audit/build.json').read_text());files={}
 for r in rows:
  for side in ['mods','server/mods']:
   for name in [r['file'],r['file']+'.sha256']:
    p=REPO/side/name;files[p.relative_to(REPO).as_posix()]=p.read_bytes()
 for rel in ['modpack-lock.json','server/config/admanticheat/idfile/admanticheat_id_list.json','seasonal-calendar/installed-mods.json','seasonal-calendar/audit/coverage.json','.github/workflows/drinks-balance.yml']:
  files[rel]=(REPO/rel).read_bytes()
 for glob in ['*.py','NativePatch.java','README.md','.gitignore','src/*.java','tests/*.java','audit/*.json','release.json']:
  for p in ROOT.glob(glob):files[p.relative_to(REPO).as_posix()]=p.read_bytes()
 old=[side+'/'+r['old_file'] for r in rows for side in ['mods','server/mods']]
 files['REMOVE-OLD-JARS.txt']=('\n'.join(old)+'\n').encode()
 files['INSTALL-UA.txt']=('Empirecraft — напої та витримка вина\n\n'
 '1. Зупини сервер і гру.\n'
 '2. Видали старі JAR, перелічені в REMOVE-OLD-JARS.txt, та їхні .sha256, якщо є.\n'
 '3. Для клієнта скопіюй вміст mods/ у папку mods клієнта.\n'
 '4. Для сервера скопіюй вміст server/mods/ у папку mods сервера.\n'
 '5. Оновлений ADM міститься в server/config/admanticheat/idfile/. Він відповідає поточному Empirecraft; якщо власний список відрізняється, перенеси тільки SHA-256 brewinandchewin та vinery й перерахуй integrityHash за скриптом у вихідному коді.\n'
 '6. Перезапусти клієнт і сервер. Не залишай дві версії того самого моду.\n\n'
 'drinks-balance/README.md містить точні рецепти, правила витримки, перевірки та інструкції відтворення.\n'
 'Місяць — 30 реальних діб; максимум II рівень і 12 хв. Враховується час непрогружених чанків і вимкненого сервера. Старі пляшки без реальної дати починають відлік при першому серверному доступі.\n'
 'Решта папок пакета — готові зміни для репозиторію Empirecraft.\n').encode()
 out=ROOT/'dist/Empirecraft-drinks-update.zip';pack(out,files)
 out.with_suffix('.zip.sha256').write_text(sha(out.read_bytes())+'  '+out.name+'\n')
 print(out, out.stat().st_size)
if __name__=='__main__':main()
