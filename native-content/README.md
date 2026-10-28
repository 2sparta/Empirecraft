# Нативне вилучення матеріалів та вмісту Енду/Незеру

Це зміни самих JAR для обох сторін Minecraft 1.20.1 / Forge 47.4.23. Вилучені ідентифікатори не передаються в реєстри предметів і блоків. Політика вбудована також у спільні провайдери реєстрації та їхні вкладені копії JarJar; приховування в JEI не є механізмом вилучення.

- `magistuarmory:steel_ingot` та `magistuarmory:steel_nugget` вилучено. Старі збереження переносяться на відповідні форми Overgeared через наявний мод міграції.
- `antiquelegacy:bronze_ingot` та `antiquelegacy:bronze_nugget` вилучено. Канонічні форми — `createbigcannons:bronze_ingot` та `createbigcannons:bronze_scrap`. Бронзові обладунки, зброя, пластини і проміжна суміш залишаються: для них у Big Cannons немає відповідних замін.
- Збережено точний рецепт Epic Knights: вісім мідних самородків навколо олов’яного → бронзова суміш; переплавлення суміші за 200 тіків з досвідом 0.7 → злиток Big Cannons. Альтернативний рецепт `createbigcannons:medieval/bronze` вирізано. Зворотні перетворення блока/злитка/самородків Big Cannons залишаються.
- Двері Create та `framed_glass_trapdoor` не реєструються. Залежні рецепти, теги, моделі та переклади прибрано з JAR.
- Аудит охоплює всі встановлені клієнтські й серверні JAR. Вилучається End-/Nether-декор, незеритове спорядження, душевні/магмові предмети, Nethersteel, їжа з хорусом та інший тематичний вміст. `naturalist:crimson_froglass` є червоним забарвленням звичайного скла і залишається; звичайні меблі `end_table` також залишаються.
- Старий `server/kubejs/server_scripts/1_steel.js` видалено. Залишкова сумісність пластин і заборона продажу сирої сталі збережені в окремому `empirecraft_plate_compat.js`; заміни двох вирізаних сталевих форм у KubeJS більше немає.

Нативні поля, на які ще посилається сторонній код, отримують ліниві посилання на наявні аналоги без повторної реєстрації вилученого ID. Вхід до обох вимірів заборонений; вихід у звичайний світ дозволений. Наявні світи та каталоги вимірів не видаляються. Зі старих блоків залишається звичайний камінь; форма і властивості вилученого декору не зберігаються.

## Відтворення

`inputs.json` фіксує SHA-256 кожного вхідного JAR з коміту `7cb7eb03c33b5a4f4da64f6ece6a6f16acb333e2`. Збірка приймає лише ці оригінали, а не вже змінені результати.

```sh
python3 native-content/build.py --inputs ORIGINAL_CHECKOUT
python3 native-content/update_metadata.py
python3 native-content/update_metadata.py --check
python3 native-content/test.py
python3 seasonal-leaves/prepare_runtime.py native-content/build/runtime
python3 seasonal-leaves/toolchain.py native-content/build/toolchain native-content/build/runtime
python3 native-content/smoke.py --runtime native-content/build/runtime --toolchain native-content/build/toolchain
```

Змінені методи проходять ASM BasicVerifier. `test.py` перевіряє ресурси, початкові пропорції бронзи, відсутність альтернативного легування та однакові JAR клієнта/сервера. `Smoke.java` перевіряє фактичні Forge-реєстри, творчі вкладки, бронзові рецепти та переходи між вимірами в окремому тимчасовому світі. Це не запуск графічного клієнта або чинного Mohist-світу з плагінами.

Після встановлення змінених JAR на сервер і клієнти потрібен повний перезапуск. Список хешів, lock-файл та ADM оновлюються автоматично; ресурс-паки й сторонні записи ADM зберігаються.
