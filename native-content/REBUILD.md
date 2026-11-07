# Повторна збірка native overlay

Порядок: `build.py --inputs .native-inputs`, `client_colors.py`, `update_metadata.py`, `test.py`.

Базовий прохід використовує `legacy/PolicyHooks.java`: точний вихідний код первинного helper, до додавання клієнтського color guard. Другий прохід використовує поточний `PolicyHooks.java` і відтворює color guard лише в JAR із Registrate builders, включно з вкладеними JarJar. Це повторює порядок випуску встановлених артефактів без зміни helper у сторонніх JAR.

`retained-code.json` фіксує переглянуті gameplay-класи. Перед зміною файлів базова збірка перевіряє ці контрольні суми у встановлених JAR і переносить класи поверх старих вхідних артефактів. Невідома зміна класу зупиняє збірку. Так повторна збірка зберігає пізніші виправлення відновлення рослин/дерев, конфігурації World Rules і природних умов сезонного календаря.

Коли всі записи JAR відтворилися без змін, зберігається також його вже опублікований ZIP-контейнер: пізніші детерміновані overlays могли мати іншу фіксовану дату архіву.


Оновлення інструментів 1.1.2 змінює лише базовий вхід `empirecraft-forging`. Спочатку виконайте `python3 forging-balance/build.py`. Він зберігає JAR до native overlay в `forging-balance/dist/native-inputs/empirecraft-forging-1.1.2.jar`. Скопіюйте цей файл у `mods/` і `server/mods/` вашого каталогу `--inputs` під цим самим ім'ям. Після `forging-balance/update_metadata.py` його digest зафіксовано в `native-content/inputs.json`, а переглянуті gameplay-класи — в `retained-code.json`; інші входи не змінюються.

Для Sails 10 використовуйте `sails/native-inputs/vs_sails-1.20.1-0.3.2-empirecraft.10-forge.jar` як вхід обох сторін. Це JAR до native overlay, а не встановлена обрізана копія. `sails/build.py --sails-only` і `sails/release_smooth.py --apply` відтворюють overlay та оновлюють його digest/переглянуті класи. Активні записи попереднього Sails вилучаються; вхід Forging 1.1.2 та інші моди зберігаються.

Для відновленої сталевої відмички спочатку виконайте `steel-lockpicking/build.py`.
Файл `steel-lockpicking/build/locks-steel-only-1.20.1-1.2.4-empirecraft.1.jar`
є новим входом Locks для обох сторін у каталозі `--inputs`; його SHA закріплено
в `inputs.json`. Початкові SteelOnly і Locks Reforged збережені окремо в
`steel-lockpicking/native-inputs/`. Не підставляйте їх безпосередньо замість
готового overlay: тільки новий вхід містить відмичку, ресурси й захищене меню.
Переглянуті класи також зафіксовані в `retained-code.json`.
