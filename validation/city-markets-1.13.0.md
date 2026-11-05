# Перевірка City Markets 1.13.0 / Bridge 1.7.0

Доповнення до реалізації City Markets 1.12.0: англійська та російська локалізації, особиста збережена мова команд `/language en|uk|ru` з початковою англійською та виправлення застарілої ширини вкладок ChatPlus після зміни мови або шрифту. Порядок команд, дані авторів і формат чинних договорів збережені.

Відтворювана перевірка:

1. `python3 validation/check_localization.py`.
2. `JAVA_HOME=<JDK21> python3 feudal/build.py --server-api server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar --api-libraries server/libraries --output-dir work/release` — усі 24 набори тестів, включно з новими локалізаціями, H2-перезапуском, фінансовими перевірками та порівняннями оригінальної ArmyPolicy 1.11.0.
3. На JDK21: `python3 chat-channels/build.py --feudal-jar work/release/empirecraft-feudal-1.13.0.jar`, `python3 chat-channels/test.py` — патч конкретного upstream JAR, перевірка шести змінених класів, маршрутизації та getter ширини.
4. На JDK17: `cd bridge && bash gradlew test build --no-daemon` — реальна Forge API, JUnit та reobfuscation.
5. `python3 feudal/city_release.py --plugin-jar work/release/empirecraft-feudal-1.13.0.jar --bridge-jar bridge/build/libs/empirecraft-towny-journeymap-1.7.0.jar --install --test-reports bridge/build/test-results/test --backend-log work/release/backend-tests.log --output-dir work/release` — перевірка встановлених двох сторін, ADM, lock і повного ZIP.

Машинний звіт поруч містить лише підтверджені результати конкретної збірки. Графічна перевірка Minecraft і приймання на живому світі Mohist залишаються окремими ручними перевірками, описаними в `feudal/LOCALIZATION.md`. Contact-розсилка залишається вимкненою до перевірки інтеграції доставки.
