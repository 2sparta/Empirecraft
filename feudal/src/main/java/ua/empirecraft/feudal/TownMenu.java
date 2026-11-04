/* Recovered from the installed Feudal 1.11.0 (SHA-256 b1715e0dbebb4c3d6dbeb5e08f461a5e87acc7dcd982f0aad22fd0a9b91c8ab2) with Vineflower 1.11.1; missing generic local types restored. */
package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class TownMenu implements CommandExecutor, TabCompleter, Listener, AutoCloseable {
   private final FeudalPlugin plugin;
   private final FeudalService s;
   private final TownyBridge towny;
   private final PoliticalCommands political;
   private final RealmCommands realms;
   private final TownMenuData data;
   private final Map<UUID, TownMenu.View> views = new HashMap<>();
   private final Map<UUID, TownMenu.Form> forms = new ConcurrentHashMap<>();
   private final Map<UUID, Long> generations = new HashMap<>();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Kyiv"));

   TownMenu(FeudalPlugin var1, FeudalService var2, TownyBridge var3, PoliticalCommands var4, RealmCommands var5) {
      this.plugin = var1;
      this.s = var2;
      this.towny = var3;
      this.political = var4;
      this.realms = var5;
      this.data = new TownMenuData(var2);
   }

   private FeudalService.Actor actor(Player var1) {
      return new FeudalService.Actor(var1.getUniqueId().toString(), var1.hasPermission("empirecraft.feudal.admin"));
   }

   private String home(Player var1) {
      Resident var2 = TownyAPI.getInstance().getResident(var1);
      return var2 != null && var2.getTownOrNull() != null ? TownyBridge.townId(var2.getTownOrNull()) : null;
   }

   private Town town(String var1) {
      return var1.startsWith("t:") ? TownyAPI.getInstance().getTown(UUID.fromString(var1.substring(2))) : null;
   }

   private long generation(Player var1) {
      return this.generations.merge(var1.getUniqueId(), 1L, Long::sum);
   }

   private void check(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      Model.require(var1.isOnline() && var1.hasPermission("empirecraft.feudal.use"), LocalText.tr("Немає доступу до меню."));
      this.towny.sync();
      this.s.tick();
      this.political.refresh();
      this.realms.realm.tick();
      this.data.access(this.actor(var1), this.home(var1), var2);

 }
}

   private boolean controls(Player var1, String var2) throws Exception {
      return this.s.controls(this.actor(var1), this.s.estate(var2));
   }

   private void ruler(Player var1, String var2) throws Exception {
      this.s.control(this.actor(var1), this.s.estate(var2));
   }

   private String name(String var1) throws Exception {
      return var1 == null ? LocalText.tr("Незалежне") : this.s.estate(var1).name();
   }

   private String when(long var1) {
      return var1 == 0L ? "—" : DATE.format(Instant.ofEpochMilli(var1)) + LocalText.tr(" (Київ)");
   }

   private void say(Player var1, String var2) {
      this.plugin.message(var1, var2);
   }

   private void safe(Player var1, TownMenu.Action var2) {
 try(var languageScope=LocalText.use(var1)){

      try {
         var2.run(var1);
      } catch (IllegalArgumentException var4) {
         this.say(var1, "§c" + var4.getMessage());
      } catch (Exception var5) {
         this.plugin.failure(LocalText.tr("Меню міста"), var5);
         this.say(var1, LocalText.tr("§cНе вдалося завершити дію. Подробиці є в журналі сервера."));
      }

 }
}

   private TownMenu.View view(Player var1, String var2, String var3, TownMenu.Action var4) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      this.check(var1, var2);
      this.forms.remove(var1.getUniqueId());
      TownMenu.View var5 = new TownMenu.View(var1.getUniqueId(), var2, this.generation(var1), this.s.now() + 300000L);
      var5.inventory = Bukkit.createInventory(var5, 54, var3);
      var5.refresh = var4;
      this.button(var5, 45, Material.ARROW, LocalText.tr("Назад до міста"), List.of(), var2x -> this.dashboard(var2x, var2));
      this.button(var5, 48, Material.MAP, LocalText.tr("Змінити володіння"), List.of(), var2x -> this.estates(var2x, var2, 0));
      this.button(var5, 49, Material.SUNFLOWER, LocalText.tr("Оновити"), List.of(), var4);
      this.button(var5, 53, Material.BARRIER, LocalText.tr("Закрити"), List.of(), HumanEntity::closeInventory);
      return var5;

 }
}

   private void show(Player var1, TownMenu.View var2) {
      this.views.put(var1.getUniqueId(), var2);
      var1.openInventory(var2.inventory);
   }

   private void button(TownMenu.View var1, int var2, Material var3, String var4, List<String> var5, TownMenu.Action var6) {
      ItemStack var7 = new ItemStack(var3);
      ItemMeta var8 = var7.getItemMeta();
      var8.setDisplayName("§6" + var4);
      ArrayList var9 = new ArrayList();

      for (String var11 : var5) {
         for (byte var12 = 0; var12 < Math.max(1, var11.length()); var12 += 58) {
            var9.add("§7" + var11.substring(var12, Math.min(var11.length(), var12 + 58)));
         }
      }

      var8.setLore(var9);
      var7.setItemMeta(var8);
      var1.inventory.setItem(var2, var7);
      if (var6 != null) {
         var1.actions.put(var2, var6);
      }
   }

   private void button(TownMenu.View var1, int var2, Material var3, String var4, TownMenu.Action var5, String... var6) {
      this.button(var1, var2, var3, var4, List.of(var6), var5);
   }

   private void pages(TownMenu.View var1, int var2, int var3, IntFunction<TownMenu.Action> var4) {
      int var5 = TownMenuData.page(var2, var3);
      if (var5 > 0) {
         this.button(var1, 46, Material.ARROW, LocalText.tr("Попередня сторінка"), (TownMenu.Action)var4.apply(var5 - 1));
      }

      this.button(
         var1,
         50,
         Material.PAPER,
         LocalText.tr("Сторінка ") + (var5 + 1) + " / " + (TownMenuData.page(Integer.MAX_VALUE, var3) + 1),
         (TownMenu.Action)null,
         LocalText.tr("Записів: ") + var3
      );
      if ((var5 + 1) * 36 < var3) {
         this.button(var1, 52, Material.ARROW, LocalText.tr("Наступна сторінка"), (TownMenu.Action)var4.apply(var5 + 1));
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
 try(var languageScope=LocalText.use(var1)){

      if (var1 instanceof Player var5) {
         this.safe(var5, var2x -> {
            Model.require(var4.length <= 1, LocalText.tr("/t menu [володіння]"));
            this.towny.sync();
            String var3x = var4.length == 1 ? this.s.resolve(var4[0]).id() : this.home(var2x);
            if (var3x == null) {
               var3x = this.towny.defaultEstate(var2x);
            }

            this.dashboard(var2x, var3x);
         });
         return true;
      } else {
         this.plugin.message(var1, LocalText.tr("Меню міста відкривається у грі."));
         return true;
      }

 }
}

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
 try(var languageScope=LocalText.use(var1)){

      if (var1 instanceof Player var5 && var5.hasPermission("empirecraft.feudal.use") && var4.length <= 1) {
         try {
            String var6 = var4.length == 0 ? "" : var4[0].toLowerCase(Locale.ROOT);
            return this.data
               .estates(this.actor(var5), this.home(var5))
               .stream()
               .map(Model.Estate::id)
               .filter(var1x -> var1x.toLowerCase(Locale.ROOT).startsWith(var6))
               .toList();
         } catch (Exception var7) {
            return List.of();
         }
      } else {
         return List.of();
      }

 }
}

   void open(Player var1) throws Exception {
      this.onCommand(var1, null, "t menu", new String[0]);
   }

   private void dashboard(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Місто · ") + this.name(var2), var2x -> this.dashboard(var2x, var2));
      Model.Estate var4 = this.s.estate(var2);
      PoliticalModel.State var5 = this.political.p.state(var2);
      Town var6 = this.town(var2);
      this.button(
         var3,
         4,
         Material.BELL,
         var4.name(),
         (TownMenu.Action)null,
         LocalText.tr("Статус: ") + LocalText.tr(var5.stage().label),
         LocalText.tr("Правитель: ") + this.towny.playerName(var4.lord()),
         LocalText.tr("Сюзерен: ") + this.name(this.s.parent(this.s.authority(var2))),
         LocalText.tr("Прямі васали: ") + this.data.vassals(var2).size(),
         var6 == null ? LocalText.tr("Державне / регіональне володіння") : LocalText.tr("Жителів: ") + var6.getResidents().size(),
         this.controls(var1, var2) ? LocalText.tr("Ви керуєте цим володінням") : LocalText.tr("Перегляд; дії перевіряють ваші права")
      );
      this.button(var3, 10, Material.PLAYER_HEAD, LocalText.tr("Жителі"), var2x -> this.residents(var2x, var2, 0), LocalText.tr("Запрошення, виключення, помічники"));
      this.button(var3, 11, Material.GRASS_BLOCK, LocalText.tr("Земля та налаштування"), var2x -> this.settings(var2x, var2), LocalText.tr("Приват, точка появи, назва, опис"));
      this.button(var3, 12, Material.GOLD_INGOT, LocalText.tr("Скарбниця"), var2x -> this.treasury(var2x, var2), LocalText.tr("Баланс, внесення, виплата, банкіри"));
      this.button(var3, 13, Material.IRON_HELMET, LocalText.tr("Васали та сюзерен"), var2x -> this.vassals(var2x, var2, 0), LocalText.tr("Договори, данина, підписи, незалежність"));
      this.button(var3, 14, Material.WRITABLE_BOOK, LocalText.tr("Дипломатичні договори"), var2x -> this.diplomacy(var2x, var2, 0), LocalText.tr("Союз, ненапад, торгівля, прохід"));
      this.button(var3, 15, Material.WHITE_BANNER, LocalText.tr("Унії та об’єднання"), var2x -> this.unions(var2x, var2, 0), LocalText.tr("Статут, запрошення, підписи, рада, вихід"));
      this.button(var3, 16, Material.GOLDEN_HELMET, LocalText.tr("Розвиток та закони"), var2x -> this.state(var2x, var2), LocalText.tr("Статус, гілки розвитку, устрій, голосування"));
      this.button(var3, 28, Material.RED_BED, LocalText.tr("Династія та спадкоємець"), var2x -> this.dynasty(var2x, var2), LocalText.tr("Правлячий дім і передача влади"));
      this.button(var3, 30, Material.PAPER, LocalText.tr("Пропозиції для вас"), var2x -> this.offers(var2x, var2, 0), LocalText.tr("Договори та призначення, що очікують підпису"));
      this.button(var3, 32, Material.BREAD, LocalText.tr("Провізія міста"), var2x -> {
         this.nativeTown(var2x, var2);
         this.run(var2x, "t provision status");
      }, LocalText.tr("Склад їжі та споживання"));
      this.button(var3, 34, Material.CLOCK, LocalText.tr("Історія володіння"), var2x -> this.execute(var2x, this.political, "state", "history", var2));
      this.show(var1, var3);

 }
}

   private void estates(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Ваші володіння"), var3x -> this.estates(var3x, var2, var3));
      List var5 = this.data.estates(this.actor(var1), this.home(var1));
      int var6 = 0;

      for (Model.Estate var8 : TownMenuData.<Model.Estate>slice((List<Model.Estate>)var5, var3)) {
         this.button(
            var4,
            var6++,
            Material.MAP,
            var8.name(),
            var2x -> this.dashboard(var2x, var8.id()),
            this.plugin.politicalLabel(var8),
            LocalText.tr("Правитель: ") + this.towny.playerName(var8.lord())
         );
      }

      this.pages(var4, var3, var5.size(), var2x -> var3x -> this.estates(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void nativeTown(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      Model.require(this.town(var2) != null && var2.equals(this.home(var1)), LocalText.tr("Ця дія доступна лише для вашого міста. Оберіть його через «Змінити володіння»."));

 }
}

   private void runTown(Player var1, String var2, String var3) throws Exception {
      this.nativeTown(var1, var2);
      this.ruler(var1, var2);
      this.run(var1, var3);
   }

   private void run(Player var1, String var2) {
 try(var languageScope=LocalText.use(var1)){

      var1.closeInventory();
      var1.performCommand(var2);

 }
}

   private void execute(Player var1, CommandExecutor var2, String var3, String... var4) {
      var1.closeInventory();
      var2.onCommand(var1, new Command(var3) {
         public boolean execute(CommandSender var1, String var2x, String[] var3x) {
            return false;
         }
      }, "t " + var3, var4);
   }

   private void residents(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Жителі міста"), var3x -> this.residents(var3x, var2, var3));
      Town var5 = this.town(var2);
      Model.require(var5 != null, LocalText.tr("Оберіть міське володіння."));
      List var6 = var5.getResidents().stream().sorted(Comparator.comparing(var0 -> var0.getName())).toList();
      int var7 = 0;

      for (Resident var9 : TownMenuData.<Resident>slice((List<Resident>)var6, var3)) {
         String var10 = var9.getName();
         this.button(
            var4,
            var7++,
            Material.PLAYER_HEAD,
            var10,
            var3x -> this.resident(var3x, var2, var10),
            var9 == var5.getMayor() ? LocalText.tr("Мер") : LocalText.tr("Житель"),
            LocalText.tr("Натисніть для керування")
         );
      }

      this.button(var4, 40, Material.EMERALD, LocalText.tr("Запросити жителя"), var3x -> this.input(var3x, var4, List.of(LocalText.tr("Ім’я гравця")), (var2xx, var3xx) -> {
         this.nativeTown(var2xx, var2);
         this.ruler(var2xx, var2);
         this.runTown(var2xx, var2, "t add " + TownMenuData.token(var3xx.get(0)));
      }));
      this.pages(var4, var3, var6.size(), var2x -> var3x -> this.residents(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void resident(Player var1, String var2, String var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Житель · ") + var3, var3x -> this.resident(var3x, var2, var3));
      this.button(var4, 11, Material.IRON_HELMET, LocalText.tr("Призначити помічником"), var4x -> {
         this.nativeTown(var4x, var2);
         this.ruler(var4x, var2);
         this.confirm(var4x, var4, LocalText.tr("Помічник: ") + var3, var3xx -> this.runTown(var3xx, var2, "t rank add " + TownMenuData.token(var3) + " assistant"));
      });
      this.button(var4, 13, Material.LEATHER_HELMET, LocalText.tr("Зняти роль помічника"), var4x -> {
         this.nativeTown(var4x, var2);
         this.ruler(var4x, var2);
         this.confirm(var4x, var4, LocalText.tr("Зняти роль: ") + var3, var3xx -> this.runTown(var3xx, var2, "t rank remove " + TownMenuData.token(var3) + " assistant"));
      });
      this.button(var4, 15, Material.BARRIER, LocalText.tr("Виключити з міста"), var4x -> {
         this.nativeTown(var4x, var2);
         this.ruler(var4x, var2);
         this.confirm(var4x, var4, LocalText.tr("Виключити: ") + var3, var3xx -> this.runTown(var3xx, var2, "t kick " + TownMenuData.token(var3)));
      });
      this.show(var1, var4);

 }
}

   private void settings(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Земля та налаштування"), var2x -> this.settings(var2x, var2));
      Town var4 = this.town(var2);
      Model.require(var4 != null, LocalText.tr("Оберіть міське володіння."));
      this.button(
         var3,
         4,
         Material.MAP,
         LocalText.tr("Територія міста"),
         (TownMenu.Action)null,
         LocalText.tr("Жителів: ") + var4.getResidents().size(),
         LocalText.tr("Зайнято чанків: ") + var4.getNumTownBlocks(),
         LocalText.tr("Ліміт чанків: ") + var4.getMaxTownBlocks()
      );
      this.button(var3, 10, Material.GRASS_BLOCK, LocalText.tr("Приєднати поточний чанк"), var3x -> {
         this.nativeTown(var3x, var2);
         this.ruler(var3x, var2);
         this.confirm(var3x, var3, LocalText.tr("Приєднати чанк"), var2xx -> this.runTown(var2xx, var2, "t claim"));
      }, LocalText.tr("Застосовуються ціна й ліміти Towny"));
      this.button(var3, 11, Material.DIRT, LocalText.tr("Відмовитися від чанка"), var3x -> {
         this.nativeTown(var3x, var2);
         this.ruler(var3x, var2);
         this.confirm(var3x, var3, LocalText.tr("Зняти приват чанка"), var2xx -> this.runTown(var2xx, var2, "t unclaim"));
      });
      this.button(var3, 12, Material.COMPASS, LocalText.tr("Точка появи тут"), var3x -> {
         this.nativeTown(var3x, var2);
         this.ruler(var3x, var2);
         this.confirm(var3x, var3, LocalText.tr("Змінити точку появи"), var2xx -> this.runTown(var2xx, var2, "t set spawn"));
      });
      this.button(
         var3, 14, Material.NAME_TAG, LocalText.tr("Змінити назву"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Нова назва міста (без пробілів)")), (var3xx, var4x) -> {
            this.nativeTown(var3xx, var2);
            this.ruler(var3xx, var2);
            String var5x = TownMenuData.token(var4x.get(0));
            this.confirm(var3xx, var3, LocalText.tr("Нова назва: ") + var5x, var3xxx -> this.runTown(var3xxx, var2, "t set name " + var5x));
         })
      );
      this.button(var3, 15, Material.OAK_SIGN, LocalText.tr("Оголошення міста"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Текст оголошення")), (var2xx, var3xx) -> {
         this.nativeTown(var2xx, var2);
         this.ruler(var2xx, var2);
         this.runTown(var2xx, var2, "t set board " + var3xx.get(0));
      }));
      this.button(var3, 16, Material.PAPER, LocalText.tr("Податок Towny"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Сума податку Towny")), (var3xx, var4x) -> {
         this.nativeTown(var3xx, var2);
         this.ruler(var3xx, var2);
         String var5x = Model.format(Model.money(var4x.get(0)));
         this.confirm(var3xx, var3, LocalText.tr("Податок: ") + var5x, var3xxx -> this.runTown(var3xxx, var2, "t set taxes " + var5x));
      }), LocalText.tr("Використовує економіку й обмеження Towny"));
      boolean[] var5 = new boolean[]{var4.isPVP(), var4.isPublic(), var4.isOpen(), var4.hasMobs(), var4.isFire(), var4.isExplosion()};
      String[] var6 = new String[]{"pvp", "public", "open", "mobs", "fire", "explosion"};
      String[] var7 = new String[]{"PvP", LocalText.tr("Публічна точка появи"), LocalText.tr("Вільний вступ"), LocalText.tr("Поява мобів"), LocalText.tr("Поширення вогню"), LocalText.tr("Вибухи")};

      for (int var8 = 0; var8 < var6.length; var8++) {
         String var9 = var6[var8];
         String var10 = var7[var8];
         this.button(
            var3,
            28 + var8,
            var5[var8] ? Material.LIME_DYE : Material.GRAY_DYE,
            var10 + (var5[var8] ? LocalText.tr(": увімкнено") : LocalText.tr(": вимкнено")),
            var4x -> {
               this.nativeTown(var4x, var2);
               this.ruler(var4x, var2);
               TownMenu.View var5x = this.view(var4x, var2, var10, var2xx -> this.settings(var2xx, var2));
               this.button(
                  var5x,
                  11,
                  Material.LIME_DYE,
                  LocalText.tr("Увімкнути"),
                  var5xx -> this.confirm(var5xx, var5x, var10 + LocalText.tr(": увімкнути"), var3xxx -> this.runTown(var3xxx, var2, "t toggle " + var9 + " on"))
               );
               this.button(
                  var5x,
                  15,
                  Material.RED_DYE,
                  LocalText.tr("Вимкнути"),
                  var5xx -> this.confirm(var5xx, var5x, var10 + LocalText.tr(": вимкнути"), var3xxx -> this.runTown(var3xxx, var2, "t toggle " + var9 + " off"))
               );
               this.show(var4x, var5x);
            }
         );
      }

      this.show(var1, var3);

 }
}

   private void treasury(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Скарбниця"), var2x -> this.treasury(var2x, var2));
      if (this.s.controlsTreasury(this.actor(var1), this.s.estate(var2))) {
         ArrayList var4 = new ArrayList();

         for (Map var6 : this.s.db.query("SELECT code,balance FROM accounts WHERE owner=? ORDER BY code", "e:" + var2)) {
            var4.add(Model.format(Database.number(var6, "balance")) + " " + Database.string(var6, "code"));
         }

         this.button(var3, 4, Material.GOLD_INGOT, LocalText.tr("Баланс казни"), var4, null);
      }

      this.button(var3, 10, Material.EMERALD, LocalText.tr("Внести валюту"), var3x -> this.treasuryInput(var3x, var3, var2, true));
      this.button(var3, 12, Material.GOLD_NUGGET, LocalText.tr("Виплатити собі"), var3x -> this.treasuryInput(var3x, var3, var2, false));
      this.button(var3, 14, Material.PLAYER_HEAD, LocalText.tr("Призначити Банкіра"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Ім’я Банкіра")), (var2xx, var3xx) -> {
         this.ruler(var2xx, var2);
         this.s.market.banker(this.actor(var2xx), var2, this.towny.residentId(TownMenuData.token(var3xx.get(0))), true);
         this.say(var2xx, LocalText.tr("Банкіра призначено."));
         this.treasury(var2xx, var2);
      }));
      this.button(var3, 15, Material.BARRIER, LocalText.tr("Зняти Банкіра"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Ім’я Банкіра")), (var3xx, var4x) -> {
         this.ruler(var3xx, var2);
         String var5 = this.towny.residentId(TownMenuData.token(var4x.get(0)));
         this.confirm(var3xx, var3, LocalText.tr("Зняти Банкіра"), var3xxx -> {
            this.s.market.banker(this.actor(var3xxx), var2, var5, false);
            this.treasury(var3xxx, var2);
         });
      }));
      this.button(var3, 16, Material.MAP, LocalText.tr("Валютний ринок і графік"), var1x -> {
         var1x.closeInventory();
         this.plugin.currencyOpen(var1x, "market", "@GOLD", "");
      });
      this.button(
         var3,
         30,
         Material.WRITABLE_BOOK,
         LocalText.tr("Бюджетне рішення"),
         var3x -> this.input(
            var3x,
            var3,
            List.of(LocalText.tr("Одержувач p:UUID або e:ID"), LocalText.tr("Код валюти"), LocalText.tr("Сума")),
            (var2xx, var3xx) -> this.execute(
               var2xx,
               this.political,
               "state",
               "budget",
               var2,
               TownMenuData.token(var3xx.get(0)),
               TownMenuData.token(var3xx.get(1)),
               Model.format(Model.money(var3xx.get(2)))
            )
         ),
         LocalText.tr("Для республіканських витрат потрібні голоси")
      );
      this.button(var3, 32, Material.PAPER, LocalText.tr("Друк валюти"), var1x -> this.run(var1x, "cu print"), LocalText.tr("Відкриває чинне меню емісії"));
      this.show(var1, var3);

 }
}

   private void treasuryInput(Player var1, TownMenu.View var2, String var3, boolean var4) {
 try(var languageScope=LocalText.use(var1)){

      this.input(var1, var2, List.of(LocalText.tr("Сума"), LocalText.tr("Код валюти")), (var4x, var5) -> {
         long var6 = Model.money(var5.get(0));
         Model.require(var6 > 0L, LocalText.tr("Сума має бути додатною."));
         String var8 = TownMenuData.token(var5.get(1)).toUpperCase(Locale.ROOT);
         this.confirm(var4x, var2, (var4 ? LocalText.tr("Внести ") : LocalText.tr("Виплатити ")) + Model.format(var6) + " " + var8, var6x -> {
            this.s.transfer(this.actor(var6x), var4 ? "p:" + var6x.getUniqueId() : "e:" + var3, var4 ? "e:" + var3 : "p:" + var6x.getUniqueId(), var8, var6);
            this.say(var6x, LocalText.tr("Переказ виконано."));
            this.treasury(var6x, var3);
         });
      });

 }
}

   private void vassals(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Васали та сюзерен"), var3x -> this.vassals(var3x, var2, var3));
      String var5 = this.s.authority(var2);
      List var6 = this.data.vassals(var2);
      int var7 = 0;

      for (Model.Estate var9 : TownMenuData.<Model.Estate>slice((List<Model.Estate>)var6, var3)) {
         this.button(
            var4,
            var7++,
            Material.IRON_HELMET,
            var9.name(),
            var3x -> this.vassal(var3x, var2, var9.id()),
            this.plugin.politicalLabel(var9),
            LocalText.tr("Правитель: ") + this.towny.playerName(var9.lord()),
            this.s.contract(var9.id()) == null ? LocalText.tr("За належністю / грамотою") : LocalText.tr("Є васальний договір")
         );
      }

      this.button(
         var4,
         38,
         Material.WRITABLE_BOOK,
         LocalText.tr("Запропонувати васалітет"),
         var4x -> this.input(
            var4x,
            var4,
            List.of(
               LocalText.tr("ID або назва васала"),
               LocalText.tr("Данина за 7 діб (0 — без данини)"),
               LocalText.tr("Код валюти (або -)"),
               LocalText.tr("Автономія: висока / обмежена"),
               LocalText.tr("Обов’язок: добровільно / оборона / повний"),
               LocalText.tr("Строк: 0 або 7–365 діб")
            ),
            (var4xx, var5x) -> {
               this.ruler(var4xx, var5);
               String var6x = this.s.resolve(TownMenuData.token(var5x.get(0))).id();
               long var7x = Model.money(var5x.get(1));
               String var9x = var5x.get(2).equals("-") ? "" : TownMenuData.token(var5x.get(2)).toUpperCase(Locale.ROOT);
               Model.require(var5x.get(5).matches("[0-9]{1,3}"), LocalText.tr("Строк має бути цілим числом."));
               int var10 = Integer.parseInt(var5x.get(5));
               this.confirm(
                  var4xx,
                  var4,
                  LocalText.tr("Запропонувати договір"),
                  var9xx -> {
                     String var10x = this.s.offer(this.actor(var9xx), var6x, var5, var7x, var9x, (String)var5x.get(3), (String)var5x.get(4), var10);
                     this.say(var9xx, LocalText.tr("Пропозиція: ") + var10x);
                     this.vassals(var9xx, var2, 0);
                  },
                  LocalText.tr("Васал: ") + this.name(var6x),
                  LocalText.tr("Сюзерен: ") + this.name(var5),
                  LocalText.tr("Данина / 7 діб: ") + Model.format(var7x) + " " + var9x,
                  LocalText.tr("Автономія: ") + var5x.get(3),
                  LocalText.tr("Обов’язок: ") + var5x.get(4),
                  LocalText.tr("Строк: ") + var10 + LocalText.tr(" діб (0 — безстроково)")
               );
            }
         )
      );
      this.button(var4, 39, Material.PAPER, LocalText.tr("Мої пропозиції"), var2x -> this.offers(var2x, var2, 0));
      this.button(
         var4, 40, Material.GOLDEN_HELMET, LocalText.tr("Сюзерен: ") + this.name(this.s.parent(var5)), var2x -> this.execute(var2x, this.political, "fief", "contract", var5)
      );
      this.button(var4, 41, Material.FEATHER, LocalText.tr("Просити незалежність"), var4x -> this.confirm(var4x, var4, LocalText.tr("Мирна незалежність"), var3xx -> {
         String var4xx = this.s.independence(this.actor(var3xx), var5);
         this.say(var3xx, LocalText.tr("Пропозиція незалежності: ") + var4xx);
         this.vassals(var3xx, var2, 0);
      }), LocalText.tr("Потрібна згода сторін"));
      this.button(
         var4,
         42,
         Material.OAK_SIGN,
         LocalText.tr("Створити регіон"),
         var4x -> this.input(var4x, var4, List.of(LocalText.tr("Тип: графство / герцогство"), LocalText.tr("Назва регіону")), (var3xx, var4xx) -> {
            String var5x = this.s.create(this.actor(var3xx), var5, Model.Kind.parse(var4xx.get(0)), var4xx.get(1));
            this.say(var3xx, LocalText.tr("Створено: ") + var5x);
            this.vassals(var3xx, var2, 0);
         })
      );
      this.pages(var4, var3, var6.size(), var2x -> var3x -> this.vassals(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void vassal(Player var1, String var2, String var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Васал · ") + this.name(var3), var3x -> this.vassal(var3x, var2, var3));
      Map var5 = this.s.contract(var3);
      ArrayList var6 = new ArrayList<>(List.of(LocalText.tr("Правитель: ") + this.towny.playerName(this.s.estate(var3).lord()), LocalText.tr("Сюзерен: ") + this.name(this.s.parent(var3))));
      if (var5 != null) {
         var6.addAll(
            List.of(
               LocalText.tr("Данина: ") + Model.format(Database.number(var5, "amount")) + " " + Database.string(var5, "code"),
               LocalText.tr("Автономія: ") + LocalText.tr(Database.string(var5, "autonomy")),
               LocalText.tr("Обов’язок: ") + LocalText.tr(Database.string(var5, "military")),
               LocalText.tr("Завершення: ") + this.when(Database.number(var5, "ends"))
            )
         );
      }

      this.button(var4, 4, Material.BOOK, this.name(var3), var6, null);
      this.button(var4, 11, Material.WRITABLE_BOOK, LocalText.tr("Переглянути договір"), var2x -> this.execute(var2x, this.political, "fief", "contract", var3));
      if (this.s.estate(var3).kind() == Model.Kind.COUNTY || this.s.estate(var3).kind() == Model.Kind.DUCHY) {
         this.button(
            var4,
            13,
            Material.PLAYER_HEAD,
            LocalText.tr("Призначити правителя регіону"),
            var4x -> this.input(var4x, var4, List.of(LocalText.tr("Ім’я нового правителя")), (var4xx, var5x) -> {
               String var6x = this.towny.residentId(TownMenuData.token(var5x.get(0)));
               this.confirm(var4xx, var4, LocalText.tr("Запропонувати призначення"), var4xxx -> {
                  this.say(var4xxx, LocalText.tr("Пропозиція: ") + this.s.nominate(this.actor(var4xxx), var3, var6x));
                  this.vassals(var4xxx, var2, 0);
               });
            })
         );
      }

      this.button(var4, 15, Material.FEATHER, LocalText.tr("Звільнити васала"), var4x -> this.confirm(var4x, var4, LocalText.tr("Звільнити ") + this.name(var3), var3xx -> {
         Model.require(Objects.equals(this.s.parent(this.s.authority(var3)), this.s.authority(var2)), LocalText.tr("Васал уже змінив сюзерена."));
         this.s.release(this.actor(var3xx), var3);
         this.say(var3xx, LocalText.tr("Васала звільнено. Попередні борги збережено."));
         this.vassals(var3xx, var2, 0);
      }), LocalText.tr("Дію підтверджує чинний сюзерен"));
      this.show(var1, var4);

 }
}

   private void offers(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Пропозиції для вас"), var3x -> this.offers(var3x, var2, var3));
      List var5 = this.data.offers(this.actor(var1));
      int var6 = 0;

      for (Map var8 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var5, var3)) {
         String var9 = Database.string(var8, "id");
         this.button(
            var4,
            var6++,
            Material.PAPER,
            this.name(Database.string(var8, "child")),
            var3x -> this.offer(var3x, var2, var9),
            LocalText.tr("Дія: ") + Database.string(var8, "action"),
            LocalText.tr("До: ") + this.when(Database.number(var8, "expires")),
            LocalText.tr("Данина: ") + Model.format(Database.number(var8, "amount")) + " " + Database.string(var8, "code")
         );
      }

      if (var5.isEmpty()) {
         this.button(var4, 4, Material.GLASS_BOTTLE, LocalText.tr("Пропозицій немає"), (TownMenu.Action)null);
      }

      this.pages(var4, var3, var5.size(), var2x -> var3x -> this.offers(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void offer(Player var1, String var2, String var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Васальна пропозиція"), var3x -> this.offer(var3x, var2, var3));
      Map var5 = this.data
         .offers(this.actor(var1))
         .stream()
         .filter(var1x -> var3.equals(Database.string((Map<String, Object>)var1x, "id")))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException(LocalText.tr("Пропозиція недоступна або завершена.")));
      this.button(
         var4,
         4,
         Material.BOOK,
         this.name(Database.string(var5, "child")),
         (TownMenu.Action)null,
         LocalText.tr("Дія: ") + Database.string(var5, "action"),
         LocalText.tr("Сюзерен: ") + this.name(Database.string(var5, "parent")),
         LocalText.tr("Данина / 7 діб: ") + Model.format(Database.number(var5, "amount")) + " " + Database.string(var5, "code"),
         LocalText.tr("Автономія: ") + LocalText.tr(Database.string(var5, "autonomy")),
         LocalText.tr("Обов’язок: ") + LocalText.tr(Database.string(var5, "military")),
         "ID: " + var3,
         LocalText.tr("Строк: ") + Database.number(var5, "duration_days") + LocalText.tr(" діб (0 — безстроково)"),
         LocalText.tr("До: ") + this.when(Database.number(var5, "expires"))
      );
      this.button(var4, 11, Material.LIME_DYE, LocalText.tr("Підписати"), var4x -> this.confirm(var4x, var4, LocalText.tr("Підписати пропозицію"), var3xx -> {
         if(this.s.cityMarkets==null)this.s.accept(this.actor(var3xx),var3);
         else {var3xx.closeInventory();this.plugin.cityMarketCommands().extensions.showDocument(var3xx,this.s.cityMarkets.documents.view(this.actor(var3xx),this.s.cityMarkets.documents.vassal(var3),1),"/t vassal",var3);}
         this.say(var3xx, LocalText.tr("Підпис збережено."));
         this.offers(var3xx, var2, 0);
      }));
      this.button(var4, 15, Material.RED_DYE, LocalText.tr("Відхилити"), var4x -> this.confirm(var4x, var4, LocalText.tr("Відхилити пропозицію"), var3xx -> {
         this.s.reject(this.actor(var3xx), var3);
         this.offers(var3xx, var2, 0);
      }));
      this.button(
         var4,
         31,
         Material.LECTERN,
         LocalText.tr("Винести договір на голосування"),
         var3x -> this.execute(var3x, this.political, "state", "vassalvote", this.s.authority(var2), var3),
         LocalText.tr("Для республіки: схвалення точного договору")
      );
      this.show(var1, var4);

 }
}

   private String nation(String var1) throws Exception {
      String var2 = this.s.estate(var1).nation();
      Model.require(var2 != null, LocalText.tr("Володіння ще не належить державі."));
      return var2;
   }

   private void diplomacy(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Дипломатичні договори"), var3x -> this.diplomacy(var3x, var2, var3));
      String var5 = this.nation(var2);
      List var6 = this.realms.realm.treaties(var5);
      int var7 = 0;

      for (Map var9 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var6, var3)) {
         String var10 = Database.string(var9, "id");
         String var11 = var5.equals(Database.string(var9, "a")) ? Database.string(var9, "b") : Database.string(var9, "a");
         this.button(
            var4,
            var7++,
            Material.WRITABLE_BOOK,
            this.name(var11),
            var3x -> this.treaty(var3x, var2, var10),
            this.treatyType(Database.string(var9, "kind")),
            this.status(Database.string(var9, "status")),
            LocalText.tr("До: ") + this.when(Database.number(var9, "ends"))
         );
      }

      this.button(var4, 40, Material.EMERALD, LocalText.tr("Новий договір"), var2x -> this.treatyKinds(var2x, var2));
      this.pages(var4, var3, var6.size(), var2x -> var3x -> this.diplomacy(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private String treatyType(String var1) {
      return switch (var1) {
         case "ALLIANCE" -> LocalText.tr("Союз");
         case "NONAGGRESSION" -> LocalText.tr("Ненапад");
         case "TRADE" -> LocalText.tr("Торгівля");
         case "PASSAGE" -> LocalText.tr("Прохід");
         default -> var1;
      };
   }

   private String status(String var1) {
      return switch (var1) {
         case "DRAFT" -> LocalText.tr("Очікує підписів");
         case "ACTIVE" -> LocalText.tr("Чинний");
         case "SUSPENDED" -> LocalText.tr("Призупинений");
         case "BROKEN" -> LocalText.tr("Розірваний");
         case "REJECTED" -> LocalText.tr("Відхилений");
         case "EXPIRED" -> LocalText.tr("Строк завершився");
         case "OPEN" -> LocalText.tr("Голосування відкрите");
         default -> var1;
      };
   }

   private void treatyKinds(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Тип нового договору"), var2x -> this.treatyKinds(var2x, var2));
      String[] var4 = new String[]{"ALLIANCE", "NONAGGRESSION", "TRADE", "PASSAGE"};

      for (int var5 = 0; var5 < var4.length; var5++) {
         String var6 = var4[var5];
         this.button(
            var3,
            10 + var5 * 2,
            Material.PAPER,
            this.treatyType(var6),
            var4x -> this.input(var4x, var3, List.of(LocalText.tr("Інша держава: ID або держава:Назва"), LocalText.tr("Строк у добах: 1–365"), LocalText.tr("Умови договору")), (var4xx, var5x) -> {
               String var6x = this.s.resolve(TownMenuData.token(var5x.get(0))).id();
               int var7 = TownMenuData.days(var5x.get(1));
               this.confirm(var4xx, var3, this.treatyType(var6) + ": " + this.name(var6x), var6xx -> {
                  this.say(var6xx, LocalText.tr("Договір: ") + this.realms.realm.offer(this.actor(var6xx), this.nation(var2), var6x, var6, var7, var5x.get(2)));
                  this.diplomacy(var6xx, var2, 0);
               }, LocalText.tr("Строк: ") + var7 + LocalText.tr(" діб"), LocalText.tr("Умови: ") + var5x.get(2));
            })
         );
      }

      this.show(var1, var3);

 }
}

   private void treaty(Player var1, String var2, String var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Дипломатичний договір"), var3x -> this.treaty(var3x, var2, var3));
      Map var5 = this.realms.realm.treaty(var3);
      String var6 = this.nation(var2);
      Model.require(var6.equals(Database.string(var5, "a")) || var6.equals(Database.string(var5, "b")), LocalText.tr("Це чужий договір."));
      this.button(
         var4,
         4,
         Material.BOOK,
         this.treatyType(Database.string(var5, "kind")),
         (TownMenu.Action)null,
         this.name(Database.string(var5, "a")) + " ↔ " + this.name(Database.string(var5, "b")),
         this.status(Database.string(var5, "status")),
         LocalText.tr("Строк: ") + Database.number(var5, "days") + LocalText.tr(" діб"),
         LocalText.tr("До підпису: ") + this.when(Database.number(var5, "deadline")),
         LocalText.tr("Кінець: ") + this.when(Database.number(var5, "ends")),
         LocalText.tr("Умови: ") + Database.string(var5, "terms")
      );
      if ("DRAFT".equals(Database.string(var5, "status"))) {
         this.button(var4, 11, Material.LIME_DYE, LocalText.tr("Підписати"), var4x -> this.confirm(var4x, var4, LocalText.tr("Підписати договір"), var3xx -> {
            this.realms.realm.sign(this.actor(var3xx), var3);
            this.diplomacy(var3xx, var2, 0);
         }));
      }

      if (Set.of("DRAFT", "ACTIVE").contains(Database.string(var5, "status"))) {
         this.button(
            var4,
            15,
            Material.RED_DYE,
            LocalText.tr("Відхилити / розірвати"),
            var4x -> this.input(
               var4x, var4, List.of(LocalText.tr("Причина завершення договору")), (var4xx, var5x) -> this.confirm(var4xx, var4, LocalText.tr("Завершити договір"), var4xxx -> {
                  this.realms.realm.end(this.actor(var4xxx), var3, (String)var5x.get(0));
                  this.diplomacy(var4xxx, var2, 0);
               })
            )
         );
      }

      this.show(var1, var4);

 }
}

   private void unions(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Унії та об’єднання"), var3x -> this.unions(var3x, var2, var3));
      String var5 = this.s.authority(var2);
      List var6 = this.political.p.unions.forState(var5);
      int var7 = 0;

      for (Map var9 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var6, var3)) {
         String var10 = Database.string(var9, "id");
         this.button(
            var4,
            var7++,
            Material.WHITE_BANNER,
            Database.string(var9, "name"),
            var3x -> this.union(var3x, var2, var10),
            this.unionType(Database.string(var9, "kind")),
            this.status(Database.string(var9, "status"))
         );
      }

      this.button(var4, 39, Material.BLUE_BANNER, LocalText.tr("Створити конфедерацію"), var3x -> this.createUnion(var3x, var4, var2, PoliticalModel.UnionType.CONFEDERATION));
      this.button(var4, 41, Material.RED_BANNER, LocalText.tr("Створити федерацію"), var3x -> this.createUnion(var3x, var4, var2, PoliticalModel.UnionType.FEDERATION));
      this.button(
         var4, 43, Material.YELLOW_BANNER, LocalText.tr("Створити торговельну лігу"), var3x -> this.createUnion(var3x, var4, var2, PoliticalModel.UnionType.TRADE_LEAGUE)
      );
      this.button(
         var4, 37, Material.WRITABLE_BOOK, LocalText.tr("Повний план унії або об’єднання"), var2x -> this.unionKinds(var2x, var2), LocalText.tr("Учасники, статут, повноваження та внески")
      );
      this.pages(var4, var3, var6.size(), var2x -> var3x -> this.unions(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void createUnion(Player var1, TownMenu.View var2, String var3, PoliticalModel.UnionType var4) {
 try(var languageScope=LocalText.use(var1)){

      this.input(var1, var2, List.of(LocalText.tr("Назва об’єднання")), (var3x, var4x) -> {
         this.say(var3x, LocalText.tr("Проєкт союзу: ") + this.political.p.unions.create(this.actor(var3x), this.s.authority(var3), var4, var4x.get(0)));
         this.unions(var3x, var3, 0);
      });

 }
}

   private void unionKinds(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Тип плану об’єднання"), var2x -> this.unionKinds(var2x, var2));
      int var4 = 10;

      for (PoliticalModel.UnionType var8 : PoliticalModel.UnionType.values()) {
         this.button(
            var3,
            var4++,
            Material.WHITE_BANNER,
            this.unionType(var8.name()),
            var4x -> this.input(
               var4x,
               var3,
               List.of(
                  LocalText.tr("Назва об’єднання"),
                  LocalText.tr("Інші держави: ID або держава:Назва через кому"),
                  LocalText.tr("Письмовий статут (не менше 20 знаків)"),
                  LocalText.tr("Повноваження, наприклад defense,trade"),
                  LocalText.tr("Внесок на добу (0 — без внесків)"),
                  LocalText.tr("Код валюти або -")
               ),
               (var4xx, var5) -> {
                  String var6 = this.s.authority(var2);
                  LinkedHashSet var7 = new LinkedHashSet();

                  for (String var11 : var5.get(1).split(",", -1)) {
                     var7.add(this.s.resolve(TownMenuData.token(var11.trim())).id());
                  }

                  long var12 = Model.money(var5.get(4));
                  String var13 = var5.get(5).equals("-") ? "" : TownMenuData.token(var5.get(5)).toUpperCase(Locale.ROOT);
                  this.confirm(
                     var4xx,
                     var3,
                     LocalText.tr("Запропонувати ") + this.unionType(var8.name()),
                     var9 -> {
                        this.say(
                           var9,
                           LocalText.tr("План союзу: ")
                              + this.political
                                 .p
                                 .unions
                                 .propose(this.actor(var9), var6, var7, var8, (String)var5.get(0), (String)var5.get(2), (String)var5.get(3), var12, var13)
                        );
                        this.unions(var9, var2, 0);
                     },
                     LocalText.tr("Учасники: ") + var7,
                     LocalText.tr("Статут: ") + var5.get(2),
                     LocalText.tr("Повноваження: ") + var5.get(3),
                     LocalText.tr("Внесок / доба: ") + Model.format(var12) + " " + var13
                  );
               }
            ),
            LocalText.tr("Умови набувають чинності після потрібних підписів")
         );
      }

      this.show(var1, var3);

 }
}

   private void union(Player var1, String var2, String var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Керування об’єднанням"), var3x -> this.union(var3x, var2, var3));
      String var5 = this.s.authority(var2);
      Map var6 = this.political.p.unions.get(var3);
      Model.require(
         this.political.p.unions.members(var3).stream().anyMatch(var1x -> var5.equals(Database.string((Map<String, Object>)var1x, "state"))),
         LocalText.tr("Володіння не входить до цього союзу.")
      );
      ArrayList var7 = new ArrayList<>(
         List.of(
            "ID: " + var3,
            this.unionType(Database.string(var6, "kind")),
            this.status(Database.string(var6, "status")),
            LocalText.tr("Внесок / доба: ") + Model.format(Database.number(var6, "contribution")) + " " + Database.string(var6, "code"),
            LocalText.tr("Статут: ") + Database.string(var6, "charter"),
            LocalText.tr("Повноваження: ") + Database.string(var6, "powers")
         )
      );

      for (Map var9 : this.political.p.unions.members(var3)) {
         var7.add(
            this.name(Database.string(var9, "state"))
               + ": "
               + (Database.bool(var9, "signed") ? LocalText.tr("підписано") : LocalText.tr("очікується підпис"))
               + (Database.number(var9, "exit_at") > 0L ? LocalText.tr("; вихід: ") + this.when(Database.number(var9, "exit_at")) : "")
         );
      }

      this.button(var4, 4, Material.BOOK, Database.string(var6, "name"), var7, null);
      this.button(var4, 10, Material.LIME_DYE, LocalText.tr("Підписати план"), var5x -> this.confirm(var5x, var4, LocalText.tr("Підписати план союзу"), var4xx -> {
         this.political.p.unions.sign(this.actor(var4xx), var3, var5);
         this.union(var4xx, var2, var3);
      }));
      this.button(var4, 11, Material.EMERALD, LocalText.tr("Прийняти запрошення"), var5x -> this.confirm(var5x, var4, LocalText.tr("Вступити до союзу"), var4xx -> {
         this.political.p.unions.acceptInvite(this.actor(var4xx), var3, var5);
         this.union(var4xx, var2, var3);
      }));
      this.button(var4, 12, Material.PLAYER_HEAD, LocalText.tr("Запросити державу"), var5x -> this.input(var5x, var4, List.of(LocalText.tr("ID або назва держави")), (var5xx, var6x) -> {
         String var7x = this.s.resolve(TownMenuData.token(var6x.get(0))).id();
         this.confirm(var5xx, var4, LocalText.tr("Запросити ") + this.name(var7x), var5xxx -> {
            Map var6xx = this.political.p.unions.get(var3);
            if ("DRAFT".equals(Database.string(var6xx, "status"))) {
               this.political.p.unions.inviteDraft(this.actor(var5xxx), var3, var5, var7x);
            } else {
               this.say(var5xxx, LocalText.tr("Голосування: ") + this.political.p.unions.proposeVote(this.actor(var5xxx), var3, var5, "INVITE", var7x, "", 0L));
            }

            this.union(var5xxx, var2, var3);
         });
      }));
      this.button(var4, 13, Material.WRITABLE_BOOK, LocalText.tr("Змінити статут"), var5x -> this.input(var5x, var4, List.of(LocalText.tr("Новий статут")), (var4xx, var5xx) -> {
         if ("DRAFT".equals(Database.string(this.political.p.unions.get(var3), "status"))) {
            this.political.p.unions.amendDraft(this.actor(var4xx), var3, var5, var5xx.get(0));
         } else {
            this.say(var4xx, LocalText.tr("Голосування: ") + this.political.p.unions.proposeVote(this.actor(var4xx), var3, var5, "CHARTER", var5xx.get(0), "", 0L));
         }

         this.union(var4xx, var2, var3);
      }));
      this.button(var4, 14, Material.GOLD_INGOT, LocalText.tr("Сплатити внесок"), var5x -> this.input(var5x, var4, List.of(LocalText.tr("Сума внеску")), (var5xx, var6x) -> {
         long var7x = Model.money(var6x.get(0));
         this.confirm(var5xx, var4, LocalText.tr("Внесок: ") + Model.format(var7x), var6xx -> {
            this.political.p.unions.contribute(this.actor(var6xx), var3, var5, var7x);
            this.union(var6xx, var2, var3);
         });
      }));
      this.button(var4, 15, Material.PAPER, LocalText.tr("Голосування ради"), var3x -> this.council(var3x, var2, var3, 0));
      this.button(var4, 16, Material.OAK_DOOR, LocalText.tr("Повідомити про вихід"), var5x -> this.confirm(var5x, var4, LocalText.tr("Вийти із союзу"), var4xx -> {
         this.political.p.unions.leave(this.actor(var4xx), var3, var5);
         this.unions(var4xx, var2, 0);
      }), LocalText.tr("Діє строк виходу та сплата боргів"));
      this.button(var4, 29, Material.RED_DYE, LocalText.tr("Відхилити план"), var5x -> this.confirm(var5x, var4, LocalText.tr("Відхилити план союзу"), var4xx -> {
         this.political.p.unions.reject(this.actor(var4xx), var3, var5);
         this.unions(var4xx, var2, 0);
      }));
      this.button(var4, 31, Material.CHEST, LocalText.tr("Погасити борг за внесками"), var5x -> this.confirm(var5x, var4, LocalText.tr("Сплатити борги союзу"), var4xx -> {
         this.political.p.unions.payDebt(this.actor(var4xx), var3, var5);
         this.union(var4xx, var2, var3);
      }));
      this.button(var4, 33, Material.BARRIER, LocalText.tr("Запропонувати завершення"), var5x -> this.confirm(var5x, var4, LocalText.tr("Голосування про завершення союзу"), var4xx -> {
         this.say(var4xx, LocalText.tr("Голосування: ") + this.political.p.unions.proposeVote(this.actor(var4xx), var3, var5, "TERMINATE", "", "", 0L));
         this.union(var4xx, var2, var3);
      }));
      this.button(var4, 37, Material.LECTERN, LocalText.tr("Голосування про вступ"), var3x -> this.execute(var3x, this.political, "state", "unionvote", var5, var3));
      this.button(var4, 39, Material.OAK_DOOR, LocalText.tr("Голосування про вихід"), var3x -> this.execute(var3x, this.political, "state", "exitvote", var5, var3));
      this.button(
         var4,
         41,
         Material.CHEST,
         LocalText.tr("Бюджет союзу"),
         var5x -> this.input(
            var5x,
            var4,
            List.of(LocalText.tr("Одержувач p:UUID або e:ID"), LocalText.tr("Код валюти"), LocalText.tr("Сума")),
            (var4xx, var5xx) -> {
               this.say(
                  var4xx,
                  LocalText.tr("Голосування: ")
                     + this.political
                        .p
                        .unions
                        .proposeVote(
                           this.actor(var4xx),
                           var3,
                           var5,
                           "BUDGET",
                           TownMenuData.token(var5xx.get(0)),
                           TownMenuData.token(var5xx.get(1)),
                           Model.money(var5xx.get(2))
                        )
               );
               this.union(var4xx, var2, var3);
            }
         )
      );
      if ("MERGE".equals(Database.string(var6, "kind"))) {
         this.button(
            var4, 43, Material.GOLDEN_HELMET, LocalText.tr("Підтвердити об’єднання держав"), var5x -> this.confirm(var5x, var4, LocalText.tr("Виконати підписаний план"), var4xx -> {
               this.political.p.unions.confirmMerge(this.actor(var4xx), var3, var5);
               this.unions(var4xx, var2, 0);
            }), LocalText.tr("Окреме підтвердження після підписів усіх сторін")
         );
      }

      this.show(var1, var4);

 }
}

   private void council(Player var1, String var2, String var3, int var4) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var5 = this.view(var1, var2, LocalText.tr("Рада об’єднання"), var4x -> this.council(var4x, var2, var3, var4));
      String var6 = this.s.authority(var2);
      List var7 = this.s
         .db
         .query("SELECT * FROM political_union_votes WHERE union_id=? AND status='OPEN' AND deadline>? ORDER BY deadline,id", var3, this.s.now());
      int var8 = 0;

      for (Map var10 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var7, var4)) {
         String var11 = Database.string(var10, "id");
         this.button(
            var5,
            var8++,
            Material.PAPER,
            Database.string(var10, "kind"),
            var7x -> {
               TownMenu.View var8x = this.view(var7x, var2, LocalText.tr("Голос держави"), var4xx -> this.council(var4xx, var2, var3, var4));
               this.button(
                  var8x,
                  4,
                  Material.BOOK,
                  Database.string(var10, "kind"),
                  (TownMenu.Action)null,
                  LocalText.tr("Значення: ") + Database.string(var10, "arg1"),
                  LocalText.tr("Валюта: ") + Database.string(var10, "arg2"),
                  LocalText.tr("Сума: ") + Model.format(Database.number(var10, "amount"))
               );
               this.button(var8x, 11, Material.LIME_DYE, LocalText.tr("За"), var7xx -> this.confirm(var7xx, var8x, LocalText.tr("Голосувати за"), var6xxx -> {
                  this.political.p.unions.vote(this.actor(var6xxx), var11, var6, true);
                  this.council(var6xxx, var2, var3, var4);
               }));
               this.button(var8x, 15, Material.RED_DYE, LocalText.tr("Проти"), var7xx -> this.confirm(var7xx, var8x, LocalText.tr("Голосувати проти"), var6xxx -> {
                  this.political.p.unions.vote(this.actor(var6xxx), var11, var6, false);
                  this.council(var6xxx, var2, var3, var4);
               }));
               this.button(
                  var8x,
                  29,
                  Material.LECTERN,
                  LocalText.tr("Рішення держави: за"),
                  var3xx -> this.execute(var3xx, this.political, "state", "councilvote", var6, var11, "yes")
               );
               this.button(
                  var8x,
                  33,
                  Material.LECTERN,
                  LocalText.tr("Рішення держави: проти"),
                  var3xx -> this.execute(var3xx, this.political, "state", "councilvote", var6, var11, "no")
               );
               this.show(var7x, var8x);
            },
            LocalText.tr("Значення: ") + Database.string(var10, "arg1"),
            LocalText.tr("До: ") + this.when(Database.number(var10, "deadline"))
         );
      }

      this.pages(var5, var4, var7.size(), var3x -> var4x -> this.council(var4x, var2, var3, var3x));
      this.show(var1, var5);

 }
}

   private void state(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Розвиток та закони"), var2x -> this.state(var2x, var2));
      PoliticalModel.State var4 = this.political.p.state(var2);
      this.button(
         var3,
         4,
         Material.GOLDEN_HELMET,
         LocalText.tr(var4.stage().label),
         (TownMenu.Action)null,
         LocalText.tr("Правління: ") + this.government(var4.government()),
         LocalText.tr("Устрій: ") + this.territory(var4.territory()),
         LocalText.tr("Столиця: ") + this.name(var4.capital()),
         LocalText.tr("Грамота: ") + var4.charter()
      );
      int var5 = 10;

      for (PoliticalModel.Stage var9 : PoliticalModel.Stage.values()) {
         if (this.political.p.rules.transition(var4.stage(), var9)) {
            PoliticalModel.Progress var10 = this.political.p.progress(var2, var9);
            ArrayList var11 = new ArrayList();

            for (PoliticalModel.Check var13 : var10.checks()) {
               var11.add((var13.met() ? "✓ " : "✗ ") + var13.name() + ": " + var13.detail());
            }

            var11.add(LocalText.tr("Строк стабільності: ") + var10.holdMillis() / 3600000L + LocalText.tr(" год"));
            var11.add(LocalText.tr("Натисніть, щоб подати заявку"));
            this.button(var3, var5++, Material.EXPERIENCE_BOTTLE, LocalText.tr(var9.label), var11, var3x -> this.advance(var3x, var2, var9));
         }
      }

      this.button(
         var3,
         19,
         Material.LIME_DYE,
         LocalText.tr("Підтвердити подану заявку"),
         var3x -> this.confirm(var3x, var3, LocalText.tr("Підтвердити розвиток"), var2xx -> this.execute(var2xx, this.political, "state", "advance", "confirm", var2))
      );
      this.button(
         var3,
         21,
         Material.LECTERN,
         LocalText.tr("Форма правління"),
         var2x -> {
            TownMenu.View var3x = this.view(var2x, var2, LocalText.tr("Форма правління"), var2xx -> this.state(var2xx, var2));
            int var4x = 0;

            for (PoliticalModel.Government var8 : PoliticalModel.Government.values()) {
               this.button(
                  var3x,
                  var4x++,
                  Material.PAPER,
                  this.government(var8),
                  var4xx -> this.confirm(
                     var4xx,
                     var3x,
                     LocalText.tr("Правління: ") + this.government(var8),
                     var3xxx -> this.execute(var3xxx, this.political, "state", "government", var2, var8.name())
                  )
               );
            }

            this.show(var2x, var3x);
         }
      );
      this.button(
         var3,
         23,
         Material.MAP,
         LocalText.tr("Територіальний устрій"),
         var2x -> {
            TownMenu.View var3x = this.view(var2x, var2, LocalText.tr("Територіальний устрій"), var2xx -> this.state(var2xx, var2));
            int var4x = 0;

            for (PoliticalModel.Territory var8 : PoliticalModel.Territory.values()) {
               this.button(
                  var3x,
                  var4x++,
                  Material.MAP,
                  this.territory(var8),
                  var4xx -> this.confirm(
                     var4xx,
                     var3x,
                     LocalText.tr("Устрій: ") + this.territory(var8),
                     var3xxx -> this.execute(var3xxx, this.political, "state", "territory", var2, var8.name())
                  )
               );
            }

            this.show(var2x, var3x);
         }
      );
      this.button(var3, 25, Material.PAPER, LocalText.tr("Рішення та голосування"), var2x -> this.decisions(var2x, var2, 0));
      this.button(
         var3,
         28,
         Material.WRITABLE_BOOK,
         LocalText.tr("Змінити грамоту"),
         var3x -> this.input(
            var3x, var3, List.of(LocalText.tr("Текст грамоти")), (var2xx, var3xx) -> this.execute(var2xx, this.political, "state", "charter", var2, var3xx.get(0))
         )
      );
      this.button(
         var3,
         30,
         Material.COMPASS,
         LocalText.tr("Змінити столицю"),
         var3x -> this.input(
            var3x,
            var3,
            List.of(LocalText.tr("ID міста або місто:Назва")),
            (var2xx, var3xx) -> this.execute(var2xx, this.political, "state", "capital", var2, this.s.resolve(TownMenuData.token(var3xx.get(0))).id())
         )
      );
      this.button(var3, 32, Material.STONE_BRICKS, LocalText.tr("Перевірити замок"), var2x -> this.execute(var2x, this.political, "castle", "inspect", var2));
      this.button(var3, 34, Material.BELL, LocalText.tr("Вибори"), var2x -> this.elections(var2x, var2, 0));
      this.show(var1, var3);

 }
}

   private void decisions(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Державні рішення"), var3x -> this.decisions(var3x, var2, var3));
      List var5 = this.s.db.query("SELECT * FROM political_decisions WHERE state=? AND status='OPEN' AND deadline>? ORDER BY deadline,id", var2, this.s.now());
      int var6 = 0;

      for (Map var8 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var5, var3)) {
         String var9 = Database.string(var8, "id");
         this.button(
            var4,
            var6++,
            Material.PAPER,
            Database.string(var8, "action"),
            var5x -> {
               TownMenu.View var6x = this.view(var5x, var2, LocalText.tr("Голосування"), var3xx -> this.decisions(var3xx, var2, var3));
               this.button(
                  var6x,
                  4,
                  Material.BOOK,
                  Database.string(var8, "action"),
                  (TownMenu.Action)null,
                  LocalText.tr("Значення: ") + Database.string(var8, "arg1"),
                  LocalText.tr("Додатково: ") + Database.string(var8, "arg2"),
                  LocalText.tr("Сума: ") + Model.format(Database.number(var8, "amount")),
                  LocalText.tr("До: ") + this.when(Database.number(var8, "deadline"))
               );
               this.button(var6x, 11, Material.LIME_DYE, LocalText.tr("За"), var5xx -> this.confirm(var5xx, var6x, LocalText.tr("Голосувати за"), var4xxx -> {
                  this.political.p.vote(this.actor(var4xxx), var9, true);
                  this.decisions(var4xxx, var2, var3);
               }));
               this.button(var6x, 13, Material.RED_DYE, LocalText.tr("Проти"), var5xx -> this.confirm(var5xx, var6x, LocalText.tr("Голосувати проти"), var4xxx -> {
                  this.political.p.vote(this.actor(var4xxx), var9, false);
                  this.decisions(var4xxx, var2, var3);
               }));
               this.button(
                  var6x,
                  15,
                  Material.LEVER,
                  LocalText.tr("Застосувати рішення"),
                  var3xx -> this.confirm(var3xx, var6x, LocalText.tr("Застосувати рішення"), var2xxx -> this.execute(var2xxx, this.political, "state", "execute", var9))
               );
               this.show(var5x, var6x);
            },
            LocalText.tr("Значення: ") + Database.string(var8, "arg1"),
            LocalText.tr("До: ") + this.when(Database.number(var8, "deadline"))
         );
      }

      this.pages(var4, var3, var5.size(), var2x -> var3x -> this.decisions(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private String government(PoliticalModel.Government var1) {
      return switch (var1) {
         case MONARCHY -> LocalText.tr("Монархія");
         case TRIBAL_COUNCIL -> LocalText.tr("Племінна рада");
         case REPUBLIC -> LocalText.tr("Республіка");
         case ABSOLUTE_MONARCHY -> LocalText.tr("Абсолютна монархія");
         case SULTANATE -> LocalText.tr("Султанат");
         case MILITARY_ORDER -> LocalText.tr("Військовий орден");
      };
   }

   private String territory(PoliticalModel.Territory var1) {
      return switch (var1) {
         case FEUDAL -> LocalText.tr("Феодальний");
         case DIRECT -> LocalText.tr("Пряме управління");
         case AUTONOMOUS -> LocalText.tr("Автономний");
         case FEDERAL -> LocalText.tr("Федеральний");
      };
   }

   private String unionType(String var1) {
      return switch (var1) {
         case "CONFEDERATION" -> LocalText.tr("Конфедерація");
         case "FEDERATION" -> LocalText.tr("Федерація");
         case "TRADE_LEAGUE" -> LocalText.tr("Торговельна ліга");
         case "PERSONAL" -> LocalText.tr("Особиста унія");
         case "REAL" -> LocalText.tr("Реальна унія");
         case "MERGE" -> LocalText.tr("Об’єднання держав");
         default -> var1;
      };
   }

   private void advance(Player var1, String var2, PoliticalModel.Stage var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Розвиток · ") + LocalText.tr(var3.label), var3x -> this.advance(var3x, var2, var3));
      PoliticalModel.Progress var5 = this.political.p.progress(var2, var3);
      ArrayList var6 = new ArrayList();

      for (PoliticalModel.Check var8 : var5.checks()) {
         var6.add((var8.met() ? "✓ " : "✗ ") + var8.name() + ": " + var8.detail());
      }

      var6.add(LocalText.tr("Готовність: ") + (var5.ready(this.s.now()) ? LocalText.tr("можна подати заявку") : LocalText.tr("вимоги / строк ще не виконані")));
      this.button(var4, 4, Material.BOOK, LocalText.tr(var3.label), var6, null);
      this.button(
         var4,
         11,
         Material.EXPERIENCE_BOTTLE,
         LocalText.tr("Подати заявку"),
         var4x -> this.confirm(
            var4x, var4, LocalText.tr("Розвиток: ") + LocalText.tr(var3.label), var3xx -> this.execute(var3xx, this.political, "state", "advance", "submit", var2, var3.name())
         )
      );
      this.button(
         var4,
         15,
         Material.LECTERN,
         LocalText.tr("Голосування про розвиток"),
         var3x -> this.execute(var3x, this.political, "state", "advancevote", var2, var3.name()),
         LocalText.tr("Для республіканського схвалення")
      );
      this.show(var1, var4);

 }
}

   private void elections(Player var1, String var2, int var3) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var4 = this.view(var1, var2, LocalText.tr("Вибори"), var3x -> this.elections(var3x, var2, var3));
      List var5 = this.s.db.query("SELECT * FROM political_elections WHERE state=? AND status IN('OPEN','PREPARED') ORDER BY deadline,id", var2);
      int var6 = 0;

      for (Map var8 : TownMenuData.<Map<String,Object>>slice((List<Map<String,Object>>)var5, var3)) {
         String var9 = Database.string(var8, "id");
         this.button(
            var4,
            var6++,
            Material.PAPER,
            LocalText.tr("Вибори"),
            var5x -> {
               TownMenu.View var6x = this.view(var5x, var2, LocalText.tr("Участь у виборах"), var3xx -> this.elections(var3xx, var2, var3));
               this.button(
                  var6x,
                  4,
                  Material.BOOK,
                  LocalText.tr("Вибори"),
                  (TownMenu.Action)null,
                  "ID: " + var9,
                  LocalText.tr("До: ") + this.when(Database.number(var8, "deadline")),
                  LocalText.tr("Стан: ") + this.status(Database.string(var8, "status"))
               );
               this.button(
                  var6x, 10, Material.PLAYER_HEAD, LocalText.tr("Висунутися"), var2xx -> this.execute(var2xx, this.political, "state", "election", "candidate", var9)
               );
               this.button(
                  var6x,
                  12,
                  Material.PAPER,
                  LocalText.tr("Голосувати"),
                  var3xx -> this.input(
                     var3xx,
                     var6x,
                     List.of(LocalText.tr("Ім’я кандидата")),
                     (var2xxx, var3xxx) -> this.execute(var2xxx, this.political, "state", "election", "vote", var9, TownMenuData.token(var3xxx.get(0)))
                  )
               );
               this.button(
                  var6x,
                  14,
                  Material.BELL,
                  LocalText.tr("Завершити підрахунок"),
                  var3xx -> this.confirm(
                     var3xx, var6x, LocalText.tr("Завершити вибори"), var2xxx -> this.execute(var2xxx, this.political, "state", "election", "close", var9)
                  )
               );
               this.button(
                  var6x,
                  16,
                  Material.RED_DYE,
                  LocalText.tr("Скасувати вибори"),
                  var3xx -> this.confirm(
                     var3xx, var6x, LocalText.tr("Скасувати вибори"), var2xxx -> this.execute(var2xxx, this.political, "state", "election", "cancel", var9)
                  )
               );
               this.button(
                  var6x,
                  31,
                  Material.LEVER,
                  LocalText.tr("Продовжити передачу влади"),
                  var3xx -> this.confirm(
                     var3xx, var6x, LocalText.tr("Продовжити передачу"), var2xxx -> this.execute(var2xxx, this.political, "state", "election", "resume", var9)
                  )
               );
               this.show(var5x, var6x);
            },
            LocalText.tr("До: ") + this.when(Database.number(var8, "deadline")),
            this.status(Database.string(var8, "status"))
         );
      }

      this.button(
         var4,
         40,
         Material.BELL,
         LocalText.tr("Відкрити вибори"),
         var3x -> this.confirm(var3x, var4, LocalText.tr("Відкрити вибори"), var2xx -> this.execute(var2xx, this.political, "state", "election", "open", var2))
      );
      this.pages(var4, var3, var5.size(), var2x -> var3x -> this.elections(var3x, var2, var2x));
      this.show(var1, var4);

 }
}

   private void dynasty(Player var1, String var2) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var3 = this.view(var1, var2, LocalText.tr("Династія та спадкоємець"), var2x -> this.dynasty(var2x, var2));
      Map var4 = this.realms.realm.heir(var2);
      Map var5 = this.s.db.one("SELECT name FROM dynasties WHERE estate=?", var2);
      this.button(
         var3,
         4,
         Material.RED_BED,
         var5 == null ? LocalText.tr("Дім ще не названо") : Database.string(var5, "name"),
         (TownMenu.Action)null,
         var4 == null ? LocalText.tr("Спадкоємця немає") : LocalText.tr("Спадкоємець: ") + this.towny.playerName(Database.string(var4, "heir")),
         var4 == null ? "" : (Database.bool(var4, "accepted") ? LocalText.tr("Згоду отримано") : LocalText.tr("Очікується згода"))
      );
      this.button(var3, 10, Material.NAME_TAG, LocalText.tr("Назвати правлячий дім"), var3x -> this.input(var3x, var3, List.of(LocalText.tr("Назва дому")), (var2xx, var3xx) -> {
         this.realms.realm.house(this.actor(var2xx), var2, var3xx.get(0));
         this.dynasty(var2xx, var2);
      }));
      this.button(
         var3,
         12,
         Material.PLAYER_HEAD,
         LocalText.tr("Призначити спадкоємця"),
         var3x -> this.input(
            var3x,
            var3,
            List.of(LocalText.tr("Ім’я спадкоємця")),
            (var2xx, var3xx) -> this.execute(var2xx, this.realms, "dynasty", "nominate", var2, TownMenuData.token(var3xx.get(0)))
         )
      );
      this.button(var3, 14, Material.LIME_DYE, LocalText.tr("Прийняти спадкоємство"), var3x -> this.confirm(var3x, var3, LocalText.tr("Прийняти спадкоємство"), var2xx -> {
         this.realms.realm.acceptHeir(this.actor(var2xx), var2);
         this.dynasty(var2xx, var2);
      }));
      this.button(var3, 16, Material.RED_DYE, LocalText.tr("Відкликати призначення"), var3x -> this.confirm(var3x, var3, LocalText.tr("Відкликати спадкоємця"), var2xx -> {
         this.realms.realm.revoke(this.actor(var2xx), var2);
         this.dynasty(var2xx, var2);
      }));
      this.button(
         var3,
         31,
         Material.GOLDEN_HELMET,
         LocalText.tr("Передати владу спадкоємцю"),
         var3x -> this.confirm(var3x, var3, LocalText.tr("Зректися влади"), var2xx -> this.execute(var2xx, this.realms, "dynasty", "abdicate", var2, "confirm")),
         LocalText.tr("Лише за підтвердженої згоди спадкоємця")
      );
      this.show(var1, var3);

 }
}

   private void confirm(Player var1, TownMenu.View var2, String var3, TownMenu.Action var4, String... var5) throws Exception {
 try(var languageScope=LocalText.use(var1)){

      TownMenu.View var6 = this.view(var1, var2.estate, LocalText.tr("Підтвердження"), var1x -> var2.refresh.run(var1x));
      ArrayList var7 = new ArrayList<>(List.of(LocalText.tr("Перевірте дію перед підтвердженням")));
      var7.addAll(List.of(var5));
      this.button(var6, 4, Material.PAPER, var3, var7, null);
      this.button(var6, 11, Material.LIME_CONCRETE, LocalText.tr("Підтвердити"), var3x -> {
         this.check(var3x, var2.estate);
         var4.run(var3x);
      });
      this.button(var6, 15, Material.RED_CONCRETE, LocalText.tr("Скасувати"), var2.refresh);
      this.show(var1, var6);

 }
}

   private void input(Player var1, TownMenu.View var2, List<String> var3, TownMenu.Answer var4) {
 try(var languageScope=LocalText.use(var1)){

      var1.closeInventory();
      TownMenu.Form var5 = new TownMenu.Form(var2, List.copyOf(var3), List.of(), var4, this.s.now() + 120000L);
      this.forms.put(var1.getUniqueId(), var5);
      this.prompt(var1, var5);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         TownMenu.Form var3x = this.forms.get(var1.getUniqueId());
         if (var3x != null && var3x.back == var5.back && this.forms.remove(var1.getUniqueId(), var3x) && var1.isOnline()) {
            this.say(var1, LocalText.tr("Час введення минув. Відкрийте /t menu знову."));
         }
      }, 2400L);

 }
}

   private void prompt(Player var1, TownMenu.Form var2) {
 try(var languageScope=LocalText.use(var1)){

      this.say(
         var1,
         "§e"
            + var2.prompts.get(var2.values.size())
            + " §7("
            + (var2.values.size() + 1)
            + "/"
            + var2.prompts.size()
            + LocalText.tr("). Напишіть у чат; «скасувати» — вихід.")
      );

 }
}

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void chat(AsyncPlayerChatEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      TownMenu.Form var3 = this.forms.get(var2);
      if (var3 != null) {
         var1.setCancelled(true);
         String var4 = var1.getMessage();
         Bukkit.getScheduler()
            .runTask(
               this.plugin,
               () -> {
                  if (this.forms.remove(var2, var3)) {
                     this.safe(
                        var1.getPlayer(),
                        var4xx -> {
                           Model.require(
                              Objects.equals(this.generations.get(var2), var3.back.generation) && var3.expires > this.s.now(),
                              LocalText.tr("Введення застаріло. Відкрийте /t menu знову.")
                           );
                           this.check(var4xx, var3.back.estate);
                           if (LocalText.cancel(var4)) {
                              var3.back.refresh.run(var4xx);
                           } else {
                              String var5;
                              try {
                                 var5 = TownMenuData.input(var4);
                              } catch (IllegalArgumentException var8) {
                                 this.forms.put(var2, var3);
                                 this.prompt(var4xx, var3);
                                 throw var8;
                              }

                              ArrayList var6 = new ArrayList<>(var3.values);
                              var6.add(var5);
                              if (var6.size() < var3.prompts.size()) {
                                 TownMenu.Form var7 = new TownMenu.Form(var3.back, var3.prompts, List.copyOf(var6), var3.answer, var3.expires);
                                 this.forms.put(var2, var7);
                                 this.prompt(var4xx, var7);
                              } else {
                                 var3.answer.run(var4xx, List.copyOf(var6));
                              }
                           }
                        }
                     );
                  }
               }
            );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void command(PlayerCommandPreprocessEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.forms.remove(var2);
      if (this.generations.containsKey(var2)) {
         this.generation(var1.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void click(InventoryClickEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof TownMenu.View var2) {
         var1.setCancelled(true);
         if (var1.getWhoClicked() instanceof Player var5
            && this.views.get(var5.getUniqueId()) == var2
            && var2.owner.equals(var5.getUniqueId())
            && var1.getRawSlot() >= 0
            && var1.getRawSlot() < 54
            && var1.getClick() == ClickType.LEFT) {
            TownMenu.Action var6 = var2.actions.get(var1.getRawSlot());
            if (var6 != null) {
               this.views.remove(var5.getUniqueId());
               Bukkit.getScheduler()
                  .runTask(
                     this.plugin,
                     () -> {
                        if (var5.isOnline()
                           && var5.getOpenInventory().getTopInventory().getHolder() == var2
                           && Objects.equals(this.generations.get(var2.owner), var2.generation)) {
                           this.safe(
                              var5,
                              var3x -> {
                                 Model.require(var2.expires > this.s.now(), LocalText.tr("Меню застаріло. Відкрийте /t menu знову."));
                                 this.check(var3x, var2.estate);

                                 try {
                                    var6.run(var3x);
                                 } finally {
                                    if (var3x.isOnline()
                                       && !this.views.containsKey(var2.owner)
                                       && !this.forms.containsKey(var2.owner)
                                       && var3x.getOpenInventory().getTopInventory().getHolder() == var2) {
                                       var2.refresh.run(var3x);
                                    }
                                 }
                              }
                           );
                        }
                     }
                  );
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void drag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof TownMenu.View) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void closed(InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof TownMenu.View var2) {
         this.views.remove(var1.getPlayer().getUniqueId(), var2);
      }
   }

   @EventHandler
   public void quit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.views.remove(var2);
      this.forms.remove(var2);
      this.generations.remove(var2);
   }

   @Override
   public void close() {
      this.forms.clear();

      for (Player var2 : Bukkit.getOnlinePlayers()) {
         if (var2.getOpenInventory().getTopInventory().getHolder() instanceof TownMenu.View) {
            var2.closeInventory();
         }
      }

      this.views.clear();
      this.generations.clear();
   }

   @FunctionalInterface
   interface Action {
      void run(Player var1) throws Exception;
   }

   @FunctionalInterface
   interface Answer {
      void run(Player var1, List<String> var2) throws Exception;
   }

   record Form(TownMenu.View back, List<String> prompts, List<String> values, TownMenu.Answer answer, long expires) {
   }

   static final class View implements InventoryHolder {
      final UUID owner;
      final String estate;
      final long generation;
      final long expires;
      final Map<Integer, TownMenu.Action> actions = new HashMap<>();
      Inventory inventory;
      TownMenu.Action refresh;

      View(UUID var1, String var2, long var3, long var5) {
         this.owner = var1;
         this.estate = var2;
         this.generation = var3;
         this.expires = var5;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
