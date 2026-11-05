# Додаткова оптимізація EmpireCraft

Новий необов’язковий клієнтський мод: [режим «картоплі»](potato-client/README.md). Автоматично спрощує лише шість графічних параметрів на комп’ютері гравця, зберігає його налаштування та не додається до обов’язкового набору.

Minecraft **1.20.1**, Mohist **71a84ff7**, Forge **47.4.23**. Додано 22.10.2026.

## Що додано

| Мод | Версія | Призначення |
|---|---|---|
| [FastFurnace](https://www.curseforge.com/minecraft/mc-mods/fastfurnace) | 8.0.2 | Кешує перевірку рецептів звичайних і плавильних печей та коптилень. Зменшує роботу сервера; швидкість переплавлення не змінює. |
| [Structure Layout Optimizer](https://modrinth.com/mod/structure-layout-optimizer) | 1.0.11 | Оптимізує перевірку перетинів частин jigsaw-споруд і обробку NBT під час генерації нових чанків. |
| [Placebo](https://modrinth.com/mod/placebo) | 8.6.3 | Потрібна бібліотека FastFurnace. |
| [Resourceful Config](https://modrinth.com/mod/resourceful-config) | 2.1.3 | Потрібна бібліотека Structure Layout Optimizer. |

Усі чотири JAR додані **лише до `server/mods/`**. Клієнтський набір `mods/` залишається з 89 JAR; серверний набір містить 91 JAR з урахуванням [доповнення Locks](LOCKS-FURNITURE.md). Для цих доповнень клієнтам додаткові файли не потрібні. ADM перевіряє попередній клієнтський набір; його список не змінювався.

Нові файли взяті з офіційних проєктів Modrinth. Версії, посилання на файли та SHA-512 зафіксовані в [modpack-lock.json](modpack-lock.json), ліцензії — у [licenses/optimization/](licenses/optimization/).

Налаштування за замовчуванням. Після зміни рецептів KubeJS, датапаків або плагінів слід повністю перезапустити сервер: FastFurnace зберігає об’єкт останнього рецепта в печі, тому гаряча заміна рецептів може залишити старий кеш.

## Що вже було у збірці

ModernFix 5.27.89, FerriteCore 6.0.1, Memory Leak Fix 1.1.5, AI Improvements 0.5.2, Alternate Current 1.7.0, Clumps 12.0.0.4, Noisium 2.3.0 та профайлер Spark 1.10.53. Повторні копії цих модів не додаються. Embeddium, ImmediatelyFast, Entity Culling і Dynamic FPS уже є на клієнтському боці.

## Чому не додано інші кандидати

- **FastWorkbench:** його `MixinCraftingMenu` / `MixinInventoryMenu` замінюють обробку крафту через `FastBenchUtil`. У цьому шляху відсутній виклик Bukkit `PrepareItemCraftEvent`. Для сервера з плагінами та зміненими рецептами це ризик обходу правил крафту.
- **FastSuite 5.x:** `AuxRecipeManager.getRecipeFor` для великих наборів рецептів обходить метод Mohist, який викликає `Container.setCurrentRecipe`. Тому сумісність із Bukkit-рецептами потребує окремого виправлення. Це висновок зі зіставлення вихідного коду, а не зафіксований у грі збій.
- **Ksyxis:** у 1.20.1 вимикає постійне завантаження спавнових чанків; це змінює роботу ферм і механізмів у цих чанках.
- **Radium/Canary/ServerCore та асинхронні оптимізатори чанків:** зачіпають ширші ділянки логіки гібридного ядра. До цього невеликого доповнення не включені.

Джерела перевірки: [FastFurnace](https://github.com/Shadows-of-Fire/FastFurnace/tree/1.20), [FastWorkbench](https://github.com/Shadows-of-Fire/FastWorkbench/tree/1.20), [FastSuite](https://github.com/Shadows-of-Fire/FastSuite/tree/1.20), [патч RecipeManager точного Mohist](https://github.com/TiangongCraft/Tenet/blob/71a84ff74bf6b12c6efbb7ff81b3d0cc5fc42662/patches/minecraft/net/minecraft/world/item/crafting/RecipeManager.java.patch), [Ksyxis](https://modrinth.com/mod/ksyxis).

## Встановлення

1. Зупинити сервер.
2. Додати **тільки чотири нові JAR** із `server/mods/` до чинної серверної папки `mods/`. Наявний набір модів залишити на місці; не створювати дублікати бібліотек чи версій.
3. Запустити сервер. Конфігурації нових модів створяться автоматично.
4. Для порівняння під однаковим навантаженням використати наявний Spark: `/spark tps`, `/spark healthreport`, `/spark profiler start --timeout 120`. Посилання профілю показує сам Spark.

Очікувана користь — менша вартість роботи великої кількості печей та генерації споруд. Відсоток приросту TPS або економії RAM на робочому сервері не вимірювався.

## Перевірка

Живий тест проведено на точному JAR Mohist `71a84ff7` (SHA-256 `ade8962d51108f70a9facb8a7680990e50768cab83a2559d8bb64889f043471c`) з Java 21. Окремий тестовий світ містив 11 JAR: чотири доповнення та сім наявних модів оптимізації, перелічених у [validation/optimization-mods.json](validation/optimization-mods.json).

Перевірено запуск, збереження й завершення сервера, три типи печей, події `FurnaceBurnEvent`, `FurnaceStartSmeltEvent`, `FurnaceSmeltEvent` та скасування переплавлення через Bukkit-плагін. Результати та перевірка генерації споруди записані у звіті.

Це не тест повної збірки з усіма контентними модами, робочим світом, Towny, ADM і входом графічного клієнта. Наявні файли світу, KubeJS та налаштування сервера цим доповненням не змінюються.
