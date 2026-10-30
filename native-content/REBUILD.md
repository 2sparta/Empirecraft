# Повторна збірка native overlay

Порядок: `build.py --inputs .native-inputs`, `client_colors.py`, `update_metadata.py`, `test.py`.

Базовий прохід використовує `legacy/PolicyHooks.java`: точний вихідний код первинного helper, до додавання клієнтського color guard. Другий прохід використовує поточний `PolicyHooks.java` і відтворює color guard лише в JAR із Registrate builders, включно з вкладеними JarJar. Це повторює порядок випуску встановлених артефактів без зміни helper у сторонніх JAR.

`retained-code.json` фіксує переглянуті gameplay-класи. Перед зміною файлів базова збірка перевіряє ці контрольні суми у встановлених JAR і переносить класи поверх старих вхідних артефактів. Невідома зміна класу зупиняє збірку. Так повторна збірка зберігає пізніші виправлення відновлення рослин/дерев, конфігурації World Rules і природних умов сезонного календаря.

Коли всі записи JAR відтворилися без змін, зберігається також його вже опублікований ZIP-контейнер: пізніші детерміновані overlays могли мати іншу фіксовану дату архіву.


Оновлення інструментів 1.1.2 змінює лише базовий вхід `empirecraft-forging`. Спочатку виконайте `python3 forging-balance/build.py`. Він зберігає JAR до native overlay в `forging-balance/dist/native-inputs/empirecraft-forging-1.1.2.jar`. Скопіюйте цей файл у `mods/` і `server/mods/` вашого каталогу `--inputs` під цим самим ім'ям. Після `forging-balance/update_metadata.py` його digest зафіксовано в `native-content/inputs.json`, а переглянуті gameplay-класи — в `retained-code.json`; інші входи не змінюються.
