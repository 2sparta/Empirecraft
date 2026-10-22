# EmpireCraft Locks Furniture Compatibility

Серверне доповнення для Minecraft 1.20.1 / Mohist 71a84ff7 / Forge 47.4.23 та наявного LocksSteelOnly 1.20.1-1.2.4. Інструкція гравцям, таблиця меблів і встановлення: [../LOCKS-FURNITURE.md](../LOCKS-FURNITURE.md).

## Реалізація

`server/defaultconfigs/locks-server.toml` і `server/world/serverconfig/locks-server.toml` розширюють штатні регулярні вирази Locks. Перевірки `RightClickBlock`, ключів, ламання та збереження замків виконує сам Locks.

Доповнення захищає стандартні обгортки інвентарів Forge від продовження роботи після замикання: `InvWrapper`, `SidedInvWrapper` та `CompoundContainer`. Перевірка стану виконується на кожній операції; дані інвентарів не кешуються та не замінюються. Стан береться через `LocksUtil.locked` з наявної capability світу. Використовуються серверні методи й поля з SRG-іменами Minecraft 1.20.1; Mixin-цілі перевіряються з `defaultRequire: 1`.

Додаткові мережеві повідомлення не створюються. Forge display test дозволяє клієнтам обходитися без цього JAR. Оригінальний LocksSteelOnly та його клієнтські ресурси використовуються в наявному вигляді.

## Збірка

Потрібен JDK 17+ та вже встановлений Mohist 71a84ff7 із `server.jar` і `libraries/`. SHA-256 ядра перевіряється перед збіркою. Залежності не завантажуються; оригінальний LocksSteelOnly береться з `server/mods/` репозиторію.

```bash
python3 locks-compat/build.py --server-home /path/to/installed-mohist
```

Результат — `server/mods/empirecraft-locks-compat-1.0.0.jar` та SHA-256 поряд. Час ZIP-записів зафіксовано для відтворюваної збірки. MIT-ліцензія стосується власного коду доповнення; код чи ресурси Locks у JAR не включені.

## Інтеграційні перевірки

```bash
python3 locks-compat/build.py --server-home /path/to/installed-mohist --smoke-plugins
```

Це додатково створює два **тестові** Bukkit-плагіни в `locks-compat/build/`. Їх використовують тільки в окремій копії сервера зі світом `locks_test`: вони ставлять тестові блоки, працюють з інвентарями та зупиняють сервер наприкінці перевірки.

1. Підготувати окремий сервер із меблевими модами зі списку `runtime_jars` у [звіті](../validation/locks-furniture.json), Locks та цим доповненням. Використати `level-name=locks_test`, скопіювати нову конфігурацію до `defaultconfigs/`.
2. Покласти `build/LocksFurnitureSmoke.jar` у тестову `plugins/`, запустити сервер. Результати — `locks-furniture-smoke.json` та `eligible-furniture.json`; сервер зупиниться сам.
3. Прибрати перший тестовий плагін, покласти `build/LocksFurniturePersistence.jar` і повторно запустити той самий світ. Результат — `locks-furniture-persistence.json`.

Перший набір використовує Forge FakePlayer, справжній сталевий замок та ключі Locks і реальні block entity меблів. Другий перевіряє дані після читання з диска. Протокольний вхід графічного клієнта цими тестами не перевіряється.
