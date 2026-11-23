# Towny і Feudal на Forge

Серверний мод `empirecraft-towny-forge-0.5.0.jar` працює з Minecraft 1.20.1 і Forge 47.4.23. У чистому світі він сам створює `world/empirecraft/towny-state.json` та `world/empirecraft/feudal/feudal.mv.db`. Клієнтам залишається звичайна збірка Empirecraft. [Forge 47.4.23](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)

## Підготовка нового світу

Потрібні JDK 17 або 21, Python із PyYAML, бібліотеки Forge для збірки й моди чинного пакета. Для накладання інтеграції JourneyMap потрібен той самий Forge SDK із Tiny Remapper. H2 2.2.224 входить у зібраний JAR. [Вихідний код і ліцензія H2](https://github.com/h2database/h2database/tree/version-2.2.224)

```sh
curl -fL -o /tmp/h2-2.2.224.jar \
  https://repo.maven.apache.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar
python3 forge-migration/build.py \
  --h2 /tmp/h2-2.2.224.jar \
  --libraries /шлях/до/forge/libraries \
  --pack-mods /шлях/до/пакета/mods --sdk /шлях/до/forge-sdk --test
curl -fL -o work/forge-migration/serverlogin-1.0.3.jar \
  https://github.com/iwosw/server-login/releases/download/v1.0.3/serverlogin-1.0.3.jar
python3 forge-migration/migrate.py \
  --server /шлях/до/пакета --stage work/forge-migration/forge-fresh --fresh-world \
  --login-jar work/forge-migration/serverlogin-1.0.3.jar \
  --companion-jar work/forge-migration/empirecraft-forge-migration-0.1.0.jar \
  --forge-runtime /шлях/до/чистого/forge
python3 forge-migration/verify.py work/forge-migration/forge-fresh
python3 forge-migration/start.py work/forge-migration/forge-fresh
```

`--fresh-world` створює окрему копію збірки на `127.0.0.1:25629` із білим списком. Зі старої папки світу бере лише `datapacks` і `serverconfig`. Регіони, інвентарі, привати, економіка й логіни стартують порожніми. Можна також підставити підготовлену нову карту до першого запуску та згенерувати дані Towny й Feudal поверх неї. За потреби можна передати `--installer` з офіційним інсталятором Forge замість готового ядра. [Server Login 1.0.3](https://github.com/iwosw/server-login/releases/tag/v1.0.3)

## Команди

| Система | Початкова команда | Дії |
| --- | --- | --- |
| Towny | `/town new <назва>` | Створення міст і націй, привати, аванпости, ранги й ділянки. |
| Податки | `/towny taxcycle on` | Адміністратор запускає щоденний цикл. Ставки задаються через `/town set taxes`, `/nation set taxes` і `/town set plottax`. Адмін задає початковий баланс командою `/towny setbalance <resident\|town\|nation> <назва> <сума>`. |
| Договори | `/town treaty help` | Проєкт, редакції, голосування, перегляд умов, підписи, склад договірних ресурсів і вихід. |
| Скарбниці | `/treasury help`, `/currency help` | Міські й особисті рахунки Feudal, банкіри, видатки за рішенням ради, валюти та золотий резерв. Прогрес володіння й перевірка замку доступні через `/town state help` і `/town castle help`. |
| Ринки | `/market help` | Проєкти та перевірка будівель, купівля й продаж, золотовалютне забезпечення, обмін, аукціони, торгові договори, робота й постачання. |
| Провізія | `/provisions help` | Міські запаси, сезонне споживання, резерв на 24 години для розширення міста та харчування TownyRecruits. |
| Флот | `/fleet help`, `/shiprequest help`, `/shipadmin help` | Заявки на збирання, призначення міста-власника, адмірали, екіпаж, прапори, захист суден, продаж та аукціони. |

Договірні платежі та поставки виконуються в день розрахунку з доступного балансу чи запасу. Кредити, штрафи за прострочення й арешти прибрано. Звільнення привату чи нестача провізії не запускають руйнування блоків. Щоденні податки початково вимкнені, як у поточній конфігурації Empirecraft.

JourneyMap читає поточні межі, володіння й ієрархію Feudal. Claim Guard, Siegeworks і прохід ґрунтів TFC звертаються до Forge Towny. TownyRecruits зберігає облік людського ресурсу і використовує провізію рідного міста рекрута. Валюти Feudal і базові баланси податків Towny ведуть окремий облік.

Порт використовує команди Forge для нових операцій. Меню Bukkit Feudal, його пошта, ярмарки, Discord-розгляд і розширення на кшталт угод про найм полків та інтегрованих рибальських торгів потребують окремого входу у Forge. Архів старої бази Feudal, який робить імпортер без `--fresh-world`, не підключається до нової економіки.

## Перевірки

`build.py --test` перевіряє чисту ініціалізацію, права приватів і правила податків, підписання договорів, передплачені платежі та поставки, скарбниці, ринок, резерви рекрутів і дозволи екіпажу. `--native-probe` збирає тестовий мод для ізольованого сервера. Для порожнього світу додайте `-Dempirecraft.test.freshFeudal=true` та `-Dempirecraft.test.nativeBridges=true` до параметрів JVM. Проба створює місто і ринок, перевіряє Forge-інтеграції та зупиняє сервер.
