Патч Fancy Tab Sections (виправлення вильоту креативного інвентаря)
====================================================================

`FTSInternal.applyItems` заповнював порожні слоти секцій стеком `ItemStack.EMPTY`
(count = 0) і клав їх у `CreativeModeTab.displayItems`. Ванільний
`CreativeModeTab.ItemDisplayBuilder.accept` вимагає `count == 1`, тому клієнт падав з
`IllegalArgumentException: Stack size must be exactly 1`, коли вкладки перебудовувалися
при відкритті креативного інвентаря.

`PatchFts.java` замінює кожен `getstatic ItemStack.f_41583_` у `applyItems` на
`new ItemStack(Items.f_41852_, 1)` — стек повітря з count = 1. Такий слот і далі
рендериться порожнім (`ItemStack.isEmpty()` істинне для `Items.AIR`), пошукова вкладка
його відфільтровує, але ванільна перевірка count проходить.

Застосування (виконується поверх вже зібраного native overlay, після `build.py`):

```
javac --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
      --add-exports=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
      --add-exports=java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED \
      -proc:none -d native-content/fts-padding/out native-content/fts-padding/PatchFts.java
java --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
     --add-exports=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
     -cp native-content/fts-padding/out PatchFts \
     mods/fancytabsections-6.2.3-FORGE-1.20.1.jar <новий jar>
```

Патчер друкує `OK rewrote 2 padding site(s)` і зберігає всі інші записи JAR байт у байт,
включно з `ua/empirecraft/nativecut/*/PolicyHooks.class`. Після заміни JAR у `mods/` і
`server/mods/` оновіть `.sha256`, `modpack-lock.json` і ADM-список:
`python3 validation/reconcile_modpack.py --apply`.
