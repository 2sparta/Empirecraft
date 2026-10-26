#!/usr/bin/env python3
"""Package the server-only quartz update deterministically."""
import hashlib
from pathlib import Path
import build

out = build.ROOT/'dist/Empirecraft-Quartz-1.1.0.zip'
readme = '''Оновлення одноразової генерації кварцу — 1.1.0

Зупиніть сервер. Видаліть старий empirecraft-ore-retrogen-1.0.0.jar
та його .sha256 із mods. Скопіюйте вміст папки server цього архіву
в серверну папку. Потрібний повний перезапуск сервера.

Якщо ви змінювали свій конфіг, збережіть його значення й додайте
quartzPerChunk = 15. Клієнтам нічого встановлювати не потрібно.

Додається до 15 блоків minecraft:quartz_block на чанк, одноразово,
у minecraft:overworld і minecraft:world_europe, Y -64…64.
Наявний кварц враховується. Жили замінюють лише придатну породу,
не ближче 8 блоків до поверхні. За нестачі породи блоків буде менше.
Уже оброблені старою версією чанки теж отримають кварц;
видобуті сірка, цинк і кварц повторно не генеруються.
Чанки обробляються під час завантаження, без примусового обходу карти.
'''
entries = {'README.txt':readme.encode('utf-8')}
for relative in ['server/mods/'+build.NAME,'server/mods/'+build.NAME+'.sha256','server/config/empirecraft_ore_retrogen-common.toml']:
    entries[relative] = (build.REPO/relative).read_bytes()
build.jar(out,entries)
out.with_suffix('.zip.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
print(out.name)
