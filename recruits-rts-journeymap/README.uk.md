# Recruits RTS Command у JourneyMap

Відкрийте повноекранну JourneyMap і натисніть **RTS** у правому верхньому куті. Лівий клік по позначці обирає загін, **Shift + перетягування** обирає бійців у рамці, правий клік задає ціль або місце руху, а **Shift + правий клік** додає точку до маршруту. **F1** показує решту клавіш, зокрема команди для шикування, повороту, збережених груп і зон вогню. Повторне натискання **RTS** повертає звичайне керування картою.

Клієнт і сервер використовують однаковий `recruitsrtscommand-0.1.0-beta.3-empirecraft.1.jar` зі своїх папок `mods`. Якщо ви вже додали оригінальний `recruitsrtscommand-0.1.0-beta.3.jar`, замініть його, бо обидва JAR мають один Forge ID. Збірка розрахована на Minecraft 1.20.1, Forge 47, JourneyMap 6.0.7, міст Empirecraft 1.14.0 та Villager Recruits 1.15.2-empirecraft.2.

## Відтворення

Завантажте оригінальний JAR зі сторінки [Recruits RTS Command на CurseForge](https://www.curseforge.com/minecraft/mc-mods/recruits-rts-command). Далі з кореня репозиторію запустіть команду з JDK 17.

```sh
python3 recruits-rts-journeymap/build.py \
  /path/to/recruitsrtscommand-0.1.0-beta.3.jar \
  /path/to/recruitsrtscommand-0.1.0-beta.3-empirecraft.1.jar
```

Версія Empirecraft використовує наявний RTS-протокол, перевірки наказів і API Siegeworks. Клієнтський код у цьому каталозі замінює прив’язку до старої тактичної карти на події екрана JourneyMap. Для клієнтських доповнень `WorldMapView.screen()` тепер повертає Minecraft `Screen`. Початкові джерела й ліцензія LGPL-3.0-only доступні у [тегу 0.1.0-beta.3](https://github.com/mess1re/recruitsrtscommand/tree/0.1.0-beta.3). Зміни для Empirecraft поширюються під тією ж ліцензією.
