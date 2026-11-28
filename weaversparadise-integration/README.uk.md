# Weavers' Paradise — інтеграція

Мод `weaversparadise_1.6.4.1.jar` (Forge 1.20.1) додає ткацтво: бавовну, шовк, вовняне
полотно, фарбувальні станки, кроєння одягу, стібки й плюшеві іграшки.

## Що зроблено

- `patch_upstream.py` збирає `mods/weaversparadise-1.6.4.1-empirecraft.1.jar` з
  оригінального релізу, вирізаючи з нього **лише дані**: рецепти, лут блоків і
  loot-box модифікатор. Текстури й моделі предметів лишаються, щоб уже наявні в
  світі речі не ставали рожево-чорними кубами.
- `audit/removed.json` — повний перелік вирізаного з причинами та sha256 апстріму.
- `test.py` перевіряє патч і рецепти, що використовують тканину.

## Що вирізано (сучасне й фентезійне)

- Джинси, футболки, майки, худі, комбінезон, топ, панчохи, вушанка.
- Прядильна машина «spinning jenny» (промислова революція).
- Усі 20 прапорів і `dye_core` (сучасна символіка).
- Фентезійні рослини: `chromatic_bloom`, `starbloom`, `witchroot`, `chromatic_dust`.
- Loot-box модифікатор броні.

**Лишено:** уся тканина (бавовна, шовк, вовна), фарби, пігменти, кроєння, увесь
середньовічний одяг, інструменти ткача, прикраси-чокери та **плюшеві іграшки**
(зокрема `player_plushie`).

## Використання тканини в інших крафтах

У `tfc-integration` додано рецепти в `data/empirecraft_tfc/recipes/crafting/`:

| Рецепт | Що дає |
| --- | --- |
| `wool_cloth_split.json` | 1 вовняне полотно → 2 `tfc:wool_cloth` |
| `silk_cloth_split.json` | 1 шовкове полотно → 2 `tfc:silk_cloth` |
| `white_sail_from_cotton_cloth.json` | бавовняне полотно + мотузка → 2 вітрила `vs_sails:white_sail` |

## Залежності

- `PlayerMixin` у моді напряму викликає `CuriosApi.getCuriosInventory(...)`, тому без
  Curios гравець падає з `NoClassDefFoundError` при отриманні шкоди. Тому
  `curios-forge-5.14.1+1.20.1.jar` (sha256 збігається з maven.theillusivec4.top)
  доданий у `mods/` і `server/mods/`.
- `WeaversParadiseMod.<init>` створює предмети через `xox.labvorty.vortylib`, а в
  `mods.toml` ця залежність не задекларована. Без неї сервер падає з
  `NoClassDefFoundError: xox/labvorty/vortylib/items/defined/AdvancedItemOptions`
  (перевірено запуском). Тому доданий `vortylib-forge-1.20.1-1.2.5.2.jar` з
  офіційного репозиторію автора (гілка `1.20.1`, `mcmodsrepo`).

## Перевірка

```
python3 weaversparadise-integration/test.py
```

## Обмеження

- Вирізані предмети все ще існують у реєстрі мода (це Java-код, не дані), тому
  вони видимі у творчому інвентарі; у виживанні їх не отримати, а з JEI вони
  прибрані скриптом `kubejs/client_scripts/empirecraft_weaversparadise.js`.
- Серверні скрипти KubeJS видаляють рецепти цих предметів і у вже збережених світах.
