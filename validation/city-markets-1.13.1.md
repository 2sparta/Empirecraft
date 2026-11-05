# Перевірка City Markets 1.13.1 / Bridge 1.8.0

Доповнення до реалізації City Markets 1.12.0: англійська та російська локалізації, особиста збережена мова команд `/language en|uk|ru` з початковою англійською та виправлення застарілої ширини вкладок ChatPlus після зміни мови або шрифту. Порядок команд, дані авторів і формат чинних договорів збережені.

Відтворювана перевірка:

1. `python3 validation/check_localization.py`.
2. `JAVA_HOME=<JDK21> python3 feudal/build.py --server-api server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar --api-libraries server/libraries --output-dir work/release` — усі 24 набори тестів, включно з новими локалізаціями, H2-перезапуском, фінансовими перевірками та порівняннями оригінальної ArmyPolicy 1.11.0.
3. На JDK21: `python3 chat-channels/build.py --feudal-jar work/release/empirecraft-feudal-1.13.1.jar`, `python3 chat-channels/test.py` — патч конкретного upstream JAR, перевірка шести змінених класів, маршрутизації та getter ширини.
4. На JDK17: `cd bridge && bash gradlew test build --no-daemon` — реальна Forge API, JUnit та reobfuscation.
5. `python3 feudal/city_release.py --plugin-jar work/release/empirecraft-feudal-1.13.1.jar --bridge-jar bridge/build/libs/empirecraft-towny-journeymap-1.8.0.jar --install --test-reports bridge/build/test-results/test --backend-log work/release/backend-tests.log --output-dir work/release` — перевірка встановлених двох сторін, ADM, lock і повного ZIP.

Машинний звіт поруч містить лише підтверджені результати конкретної збірки. Графічна перевірка Minecraft і приймання на живому світі Mohist залишаються окремими ручними перевірками, описаними в `feudal/LOCALIZATION.md`. Contact-розсилка залишається вимкненою до перевірки інтеграції доставки.

Bridge 1.8.0 додає особисту пам’ять приватів за відвіданими чанками. Перевірки та приймання: [MAP-HISTORY.md](../bridge/MAP-HISTORY.md). Чинні результати автоматичних перевірок наведені в JSON поруч; дані попереднього релізу не є підтвердженням нової збірки.

Підтверджено [повною CI-збіркою](https://github.com/2sparta/Empirecraft/actions/runs/41889302385) коду `d6f81a0468bc700e19e0afd5e5971a48a78a38d7`: 386 серверних сценаріїв у 24 наборах, 54 JUnit-тести у 8 наборах (24 нових тести пам’яті), 46 перевірок ChatPlus, 144 перевірки договорів, 3072 порівняння політики та збереження 842 публічних API. Окрема локальна перевірка зберегла й відновила 10 000 особистих чанків. Перевірено всі версії, байти спільних модів, ADM та ZIP.

[Готовий пакет](https://github.com/2sparta/Empirecraft/actions/runs/41889302385/artifacts/13352589477) містить `work/release/Empirecraft-City-Markets-1.13.1-bundle.zip`. SHA-256 внутрішнього ZIP: `ac15812c2dc48c5816e034f46f19a5c6f98e8bcdcbfaf2531078c3202bf5bb6f`. У пакеті обидві сторони мають Bridge 1.8.0; фізичні JAR моста в гілці залишаються 1.4.2, поки мережеві правила середовища не дозволяють завантаження зібраного артефакту. Встановлюйте комплект із ZIP.

Інші перевірки: два старі пакувальники Feudal очікують уже встановлений `server/mods/empirecraft-towny-journeymap-1.8.0.jar` і не збирають його самостійно; актуальний повний профіль спочатку збирає, встановлює та перевіряє обидві сторони. Окремий профіль Tools після повторного збирання Forging змінює очікуваний lock. JAR Forging у цьому завданні не змінювався. Ці три червоні профілі не позначені як успішні.
