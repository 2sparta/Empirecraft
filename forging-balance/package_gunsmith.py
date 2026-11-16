"""Package the paired gunsmith update with checksums and installation notes."""
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAME='empirecraft-forging-1.1.5.jar'
def main():
 entries={}
 for side,base in [('client',REPO),('server',REPO/'server')]:
  for relative in ['mods/'+NAME,'mods/'+NAME+'.sha256','kubejs/server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js']:
   entries[side+'/'+relative]=(base/relative).read_bytes()
 assert entries['client/mods/'+NAME]==entries['server/mods/'+NAME]
 entries['server/config/admanticheat/idfile/admanticheat_id_list.json']=(REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
 entries['GUNSMITH.uk.md']=(ROOT/'GUNSMITH.uk.md').read_bytes()
 entries['INSTALL.uk.txt']='''Оновлення зброярства 1.1.5 для поточної Empirecraft.
Основа пакета: main 14464a1e8e596602d9101d318e964ce6a4e91415 (PR #148).
Це доповнення до повної збірки, не самостійний модпак.

1. Закрий гру та зупини сервер.
2. Зроби резервну копію папок mods, kubejs та серверного файла ADM.
3. Видали empirecraft-forging-1.1.4.jar та його .sha256 з обох папок mods.
4. Вміст client/ скопіюй у папку клієнта, server/ — у папку сервера.
   Залиш тільки один empirecraft-forging-*.jar на кожній стороні.
5. Серверний файл ADM відповідає поточному main. Якщо твій набір модів
   відрізняється, узгодь його записи зі своєю збіркою перед заміною.
6. Повністю перезапусти клієнт і сервер. Світи видаляти не потрібно.

Рецепт столу й повна механіка: GUNSMITH.uk.md та внутрішньоігрова книжка.
Серверні перевірки: 86 успішних перевірок на ізольованому тестовому світі.
Графічний клієнт для цього оновлення не перевірявся.
'''.encode()
 entries['manifest-sha256.json']=(json.dumps({k:hashlib.sha256(v).hexdigest() for k,v in sorted(entries.items())},indent=2,ensure_ascii=False)+'\n').encode()
 target=ROOT/'dist/Empirecraft-Gunsmith-1.1.5-install.zip';target.parent.mkdir(exist_ok=True)
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for name,raw in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,16,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 target.with_suffix('.zip.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
 print('PASS install ZIP:',target.name,'bytes='+str(target.stat().st_size))
if __name__=='__main__':main()
