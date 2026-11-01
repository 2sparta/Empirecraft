# Current paired package: Ship Physics 1.2.1 and Sails 13

[Sails 13 wind and hull stability guide](../sails/WIND-STABILITY.md). Current bundle: `dist/Empirecraft-Ship-Safety-13.zip`. Sails 13 replaces Sails 12 on both sides; the Ship Physics JAR is byte-identical. The notes below describe the retained collision and hull support fixes.

# Ship Physics 1.2.1 та Sails 12

Додавання одного блока до великого корабля скидало `hullReady` у Sails:
фізичний такт установлював нульову плавучість, поки багатотактовий скан не
завершиться. Додатково зміна меж корпусу замінювала порахований об'єм на
об'єм усієї обмежувальної коробки, що могло вимкнути захистом підтримку води.
Тепер попередній завершений знімок використовується до готовності нового.
Маса нового блока й надалі змінюється звичайним інкрементальним шляхом VS.

При встановленому Ship Physics його `MassSync` є єдиним механізмом міграції
інерції. Sails більше не очищає фізичні вокселі повторно після свого сканування.
Без Ship Physics автономна міграція старих буїв зберігається.

Ударні пошкодження тепер поширюються сферою по блоках, з'єднаних гранями.
Повітря, рідина, порожня collision shape, чужий корабель, незавантажений чанк
та неруйнівний блок зупиняють відповідну гілку. Енергія ділиться між знайденими
блоками з вагою `1 / (1 + відстань²)`; невикористана частка не переходить
променем до наступної палуби. Радіус залежить від енергії та міцності, обмежений
чотирма блоками та наявним `maximumPenetrationBlocks - 1` (значення 1 залишає
лише місце контакту). Один контакт перевіряє не більше 257 позицій.

Збережено загальний бюджет енергії, поріг удару, часткові пошкодження,
ліміт зруйнованих блоків за такт, строк життя черги, перевірки станів блоків,
власника корабля, світових меж, spawn protection і Forge break events.
У native-обробнику контакту також прибрано виклик `PhysShip.getShipAABB()`:
у встановленій VS 2.4.11 цей метод викидає `NotImplementedError`.

Додатково виправлено верхні межі курсора `MassSync`: VS передає виключні
максимуми, а старий код обходив їх як включні. Корабель на межі чанка міг
очікувати сторонній незавантажений чанк або врахувати блок за межами корпусу.
Шість операцій у закріпленому bytecode виправляються зі строгою перевіркою
очікуваних інструкцій; решта механізму інерції залишається без змін.

Зупиніть сервер і клієнт, замініть Sails 11 та Ship Physics 1.2.0 двома новими
JAR у `mods/` і `server/mods/`, перенесіть оновлений ADM ID-файл та перезапустіть.
Спільний пакет: `dist/Empirecraft-Ship-Safety-12.zip`.
Конфіг матеріалів із `block-mass` не потребує зміни.

Компіляція з JDK 17 та встановленими бібліотеками Mohist; тест зі справжнім
Towny запускається на Java 21 (його JAR містить класи Java 21):

```sh
python sails/build_offline.py
python ship-physics/build.py
python sails/test.py
python ship-physics/test.py
python ship-physics/smoke.py
python sails/test.py --prepare-safety-fixture
python sails/safety_smoke.py --java /path/to/java21/bin/java
python ship-physics/release.py --apply
python sails/release_smooth.py --apply
python ship-physics/release.py --check
python sails/release_smooth.py --check
```

`sails/build/annotations.jar` — JetBrains annotations 24.1.0 із SHA-256,
закріпленою в `sails/build-inputs.json`. Інші build-входи наявні в репозиторії.
`smoke.py` створює й видаляє окремий світ; робочі світи не запускаються.
Тест пошкоджень вимикає лише у своїй пам'яті Towny-захист, оскільки окремий
світ не містить Towny, і окремо перевіряє скасування Forge-події.
`sails/safety_smoke.py` перевіряє справжній Towny та попередні захисні механіки.
Графічний клієнт, тривале навантаження флотом та робочий світ потребують
окремої ігрової перевірки. Запис фактичних результатів: `validation/ship-physics-1.2.1.json`.
