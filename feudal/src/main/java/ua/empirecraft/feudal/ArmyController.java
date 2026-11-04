/* Recovered from the installed Feudal 1.11.0 (SHA-256 b1715e0dbebb4c3d6dbeb5e08f461a5e87acc7dcd982f0aad22fd0a9b91c8ab2) with Vineflower 1.11.1; missing generic local types restored. */
package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.NationAcceptAllyRequestEvent;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.TownAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
import com.palmergames.bukkit.towny.event.nation.NationPreAddAllyEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import com.palmergames.bukkit.towny.event.town.toggle.TownTogglePVPEvent;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ArmyController implements Listener, CommandExecutor, TabCompleter {
   final FeudalPlugin plugin;
   final FeudalService s;
   final ArmyService army;
   final ProvisionController provisions;
   final TownyBridge towny;
   volatile ArmyPolicy.View view = ArmyPolicy.empty();
   volatile Map<String, Boolean> claims = Map.of();
   volatile Map<String, Boolean> worlds = Map.of();
   volatile int cell = 16;
   volatile Map<String, String> factionEstates = Map.of();
   volatile List<Map<String, Object>> factions = List.of();
   final Map<String, Long> retaliation = new ConcurrentHashMap<>();
   boolean refreshQueued;

   public ArmyController(FeudalPlugin var1, ArmyService var2, ProvisionController var3, TownyBridge var4) {
      this.plugin = var1;
      this.army = var2;
      this.s = var2.s;
      this.provisions = var3;
      this.towny = var4;
      if (this.s.contractBuilder != null) {
         this.s.contractBuilder.changed = this::invalidate;
      }

      var2.canDeclare = (var1x, var2x) -> System.currentTimeMillis() - this.view.updated() < 5000L
         && !this.view.friendly(var1x, var2x)
         && !this.view.related(this.view.pacts(), var1x, var2x);
   }

   void invalidate() {
      this.view = ArmyPolicy.empty();
      if (!this.refreshQueued) {
         this.refreshQueued = true;
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
               this.towny.sync();
               this.army.tick();
               this.refresh();
            } catch (Exception var5) {
               this.plugin.failure("Оновлення дипломатії рекрутів", var5);
            } finally {
               this.refreshQueued = false;
            }
         });
      }
   }

   double hungerRate() {
      double var1 = this.plugin.getConfig().getDouble("army.hunger-per-minute", 2.0);
      return Double.isFinite(var1) ? Math.max(0.01, Math.min(100.0, var1)) : 2.0;
   }

   void main() {
      Model.require(Bukkit.isPrimaryThread(), "Зміна армії дозволена лише в серверному потоці.");
   }

   public void start() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         try {
            this.army.tick();
            this.refresh();
         } catch (Exception var2) {
            this.plugin.failure("Облік рекрутів", var2);
         }
      }, 1L, 20L);
   }

   void access(Player var1, String var2) throws Exception {
      Model.require(var1.hasPermission("empirecraft.feudal.use"), "Немає доступу.");
      Model.require(
         var1.hasPermission("empirecraft.feudal.admin")
            || this.army.citizen(var1.getUniqueId().toString(), var2)
            || this.s.controls(new FeudalService.Actor(var1.getUniqueId().toString(), false), this.s.estate(var2)),
         "Переглядати армію можуть громадяни володіння."
      );
   }

   static String text(Map<String, Object> var0, String var1) {
      Object var2 = var0.get(var1);
      return var2 == null ? null : var2.toString();
   }

   static boolean flag(Map<String, Object> var0, String var1) {
      return Boolean.TRUE.equals(var0.get(var1));
   }

   static long num(Map<String, Object> var0, String var1) {
      return ((Number)var0.get(var1)).longValue();
   }

   String home(String var1) {
      if (var1 == null) {
         return null;
      } else {
         Resident var2 = TownyAPI.getInstance().getResident(UUID.fromString(var1));
         return var2 != null && var2.getTownOrNull() != null ? TownyBridge.townId(var2.getTownOrNull()) : null;
      }
   }

   public Map<String, Object> request(String var1, Map<String, Object> var2) throws Exception {
      if (var1.equals("protocol")) {
         return Map.of(
            "protocol",
            2,
            "hungerPerMinute",
            this.hungerRate(),
            "starveEverySeconds",
            Math.max(1, this.plugin.getConfig().getInt("army.starve-every-seconds", 10)),
            "starvation",
            true
         );
      } else if (var1.equals("policy")) {
         ArmyPolicy.View var13 = this.view;
         String var15 = text(var2, "source");
         String var20 = text(var2, "target");
         String var25 = var13.estate(var15, text(var2, "ownerA"), flag(var2, "recruitA"));
         boolean var27 = this.retaliation.getOrDefault(var25 + "|" + var20, 0L) > System.currentTimeMillis();
         boolean var28 = this.territory(var2, "worldA", "xA", "zA") && this.territory(var2, "worldB", "xB", "zB");
         ArmyPolicy.Result var29 = var13.decide(
            var15,
            text(var2, "ownerA"),
            flag(var2, "recruitA"),
            var20,
            text(var2, "ownerB"),
            flag(var2, "recruitB"),
            flag(var2, "civilian"),
            flag(var2, "monster"),
            var28,
            var27,
            System.currentTimeMillis()
         );
         HashMap var30 = new HashMap();
         var30.put("allow", var29.allow());
         var30.put("reason", var29.reason());
         var30.put("a", var29.attacker());
         var30.put("b", var29.target());
         return var30;
      } else if (var1.equals("canCommand")) {
         return Map.of("allow", System.currentTimeMillis() - this.view.updated() < 5000L && this.view.commands(text(var2, "actor"), text(var2, "unit")));
      } else if (var1.equals("factions")) {
         return Map.of("factions", this.factions, "updated", this.view.updated());
      } else if (var1.equals("relation")) {
         ArmyPolicy.View var3 = this.view;
         String var14 = this.factionEstates.get(text(var2, "a"));
         String var19 = this.factionEstates.get(text(var2, "b"));
         boolean var24 = var14 != null || var19 != null;
         return Map.of(
            "managed",
            var24,
            "relation",
            var14 == null || var19 == null || !var3.friendly(var14, var19) && !var3.related(var3.pacts(), var14, var19)
               ? (var14 != null && var19 != null && var3.related(var3.wars(), var14, var19) ? "ENEMY" : "NEUTRAL")
               : "ALLY"
         );
      } else {
         this.main();
         switch (var1) {
            case "observe":
               String var18 = text(var2, "unit");
               String var23 = text(var2, "owner");
               Map var26 = this.army.unit(var18);
               String var8 = var26 != null && Database.string(var26, "pending_owner") != null ? Database.string(var26, "pending_owner") : var23;
               this.army.observe(var18, var23, this.home(var8), ((Number)var2.get("hunger")).doubleValue());
               Map var9 = this.army.unit(var18);
               HashMap var10 = new HashMap();
               var10.put("owner", Database.string(var9, "owner"));
               var10.put("estate", Database.string(var9, "estate"));
               var10.put("home", Database.string(var9, "home"));
               String var11 = Database.string(var9, "estate") == null ? Database.string(var9, "home") : Database.string(var9, "estate");
               var10.put("faction", var11 == null ? null : this.army.faction(var11));
               var10.put("active", var11 != null && this.s.estate(var11).active());
               var10.put("alive", Database.bool(var9, "alive"));
               var10.put("feedSeq", Database.number(var9, "feed_seq"));
               Map var12 = Database.string(var9, "estate") == null ? null : this.army.settings(Database.string(var9, "estate"));
               var10.put(
                  "unpaid", var12 != null && Database.number(var12, "unpaid_since") > 0L && this.s.now() - Database.number(var12, "unpaid_since") >= 172800000L
               );
               return var10;
            case "feed":
               String var17 = text(var2, "unit");
               Map var22 = this.army.unit(var17);
               String var7 = var22 == null ? null : Database.string(var22, "home");
               if (var7 == null) {
                  return Map.of("amount", 0, "reason", "Немає міста постачання");
               } else {
                  if (!this.provisions.settleArmy(var7)) {
                     return Map.of("amount", 0, "paused", true, "reason", "Облік провізії або передача ще не готові");
                  }

                  return Map.of("amount", this.army.feed(var17, num(var2, "seq"), 10));
               }
            case "retire":
               this.army.retire(text(var2, "unit"));
               return Map.of();
            case "lost":
            case "dead":
               this.army.presence(text(var2, "unit"), var1.equals("dead"));
               return Map.of();
            case "incident":
               String var16 = this.view.estate(text(var2, "target"), text(var2, "ownerB"), flag(var2, "recruitB"));
               String var21 = text(var2, "source");
               if (var16 != null && var21 != null) {
                  this.retaliation.put(var16 + "|" + var21, System.currentTimeMillis() + 120000L);
                  this.retaliation.entrySet().removeIf(var0 -> var0.getValue() < System.currentTimeMillis());
               }

               return Map.of();
            case "canHire":
               String var5 = this.home(text(var2, "actor"));
               boolean var6 = var5 != null && this.army.count("home", var5, false) < Database.number(this.army.settings(var5), "unit_limit");
               return Map.of("allow", var6);
            default:
               throw new IllegalArgumentException("Невідомий запит до армії.");
         }
      }
   }

   boolean territory(Map<String, Object> var1, String var2, String var3, String var4) {
      String var5 = text(var1, var2);
      if (var5 != null && var1.containsKey(var3) && var1.containsKey(var4)) {
         if (Bukkit.isPrimaryThread()) {
            for (World var7 : Bukkit.getWorlds()) {
               if (var7.getKey().toString().equals(var5)) {
                  return var7.getPVP()
                     && (
                        !TownyAPI.getInstance().isTownyWorld(var7)
                           || TownyAPI.getInstance()
                              .isPVP(new Location(var7, ((Number)var1.get(var3)).doubleValue(), 64.0, ((Number)var1.get(var4)).doubleValue()))
                     );
               }
            }
         }

         int var8 = (int)Math.floor(((Number)var1.get(var3)).doubleValue() / this.cell);
         int var9 = (int)Math.floor(((Number)var1.get(var4)).doubleValue() / this.cell);
         return this.claims.getOrDefault(var5 + "|" + var8 + "|" + var9, this.worlds.getOrDefault(var5, false));
      } else {
         return false;
      }
   }

   public void refresh() throws Exception {
      this.main();
      TownyAPI var1 = TownyAPI.getInstance();
      HashMap<String,ArmyPolicy.Land> var2 = new HashMap<>();
      HashMap<String,String> var3 = new HashMap<>();
      HashMap<String,ArmyPolicy.Soldier> var4 = new HashMap<>();
      HashSet var5 = new HashSet();
      HashSet var6 = new HashSet();
      HashSet var7 = new HashSet();
      HashSet var8 = new HashSet();

      for (Model.Estate var10 : this.s.estates()) {
         if (var10.active()) {
            String var11 = var10.id();
            HashSet var12 = new HashSet();

            for (String var13 = var10.id(); var13 != null && var12.add(var13); var13 = this.s.parent(var13)) {
               var11 = var13;
            }

            var2.put(var10.id(), new ArmyPolicy.Land(var10.id(), var10.nation(), var11, var10.lord()));
         }
      }

      for (Town var26 : var1.getTowns()) {
         for (Resident var39 : var26.getResidents()) {
            var3.put(var39.getUUID().toString(), TownyBridge.townId(var26));
         }
      }

      for (Nation var27 : TownyUniverse.getInstance().getNations()) {
         for (Nation var40 : var27.getAllies()) {
            if (var40.hasAlly(var27)) {
               var5.add(ArmyPolicy.pair(TownyBridge.nationId(var27), TownyBridge.nationId(var40)));
            }
         }
      }

      for (Map var28 : this.s.db.query("SELECT * FROM army_units")) {
         var4.put(
            Database.string(var28, "id"),
            new ArmyPolicy.Soldier(
               Database.string(var28, "owner"), Database.string(var28, "estate"), Database.string(var28, "home"), Database.bool(var28, "alive")
            )
         );
      }

      for (Map var29 : this.s.db.query("SELECT * FROM army_officers")) {
         if (var2.containsKey(Database.string(var29, "estate")) && this.army.citizen(Database.string(var29, "person"), Database.string(var29, "estate"))) {
            var8.add(Database.string(var29, "estate") + "|" + Database.string(var29, "person"));
         }
      }

      for (Map var30 : this.s.db.query("SELECT * FROM diplomacy WHERE status='ACTIVE' AND ends>?", this.s.now())) {
         String var35 = Database.string(var30, "a");
         String var41 = Database.string(var30, "b");
         if ("ALLIANCE".equals(Database.string(var30, "kind"))) {
            var5.add(ArmyPolicy.pair(var35, var41));
         }

         if ("NONAGGRESSION".equals(Database.string(var30, "kind"))) {
            var6.add(ArmyPolicy.pair(var35, var41));
         }
      }

      for (Map var31 : this.s.db.query("SELECT * FROM army_wars WHERE status='ACTIVE'")) {
         String var36 = Database.string(var31, "attacker");
         String var42 = Database.string(var31, "defender");
         if (var2.containsKey(var36) && var2.containsKey(var42)) {
            Set<String> var45 = this.participants(var36, false, var2);
            Set<String> var14 = this.participants(var42, true, var2);

            for (String var16 : var45) {
               for (String var18 : var14) {
                  var7.add(ArmyPolicy.pair(var16, var18));
               }
            }
         }
      }

      ArrayList var25 = new ArrayList();
      HashMap var32 = new HashMap();

      for (ArmyPolicy.Land var43 : var2.values()) {
         if (var43.id().startsWith("t:") || !var4.values().stream().noneMatch(var1x -> var43.id().equals(var1x.estate()))) {
            String var46 = this.army.faction(var43.id());
            var32.put(var46, var43.id());
            ArrayList var48 = new ArrayList();
            var3.forEach((var3x, var4x) -> {
               if (var4x.equals(var43.id())) {
                  var48.add(Map.of("uuid", var3x, "name", this.towny.playerName(var3x)));
               }
            });
            HashMap var51 = new HashMap();
            var51.put("id", var46);
            var51.put("estate", var43.id());
            var51.put("name", this.s.estate(var43.id()).name());
            var51.put("leader", var43.ruler());
            var51.put("leaderName", this.towny.playerName(var43.ruler()));
            var51.put("members", List.copyOf(var48));
            var25.add(Collections.unmodifiableMap(var51));
         }
      }

      HashMap var38 = new HashMap();
      HashMap var44 = new HashMap();
      int var47 = Coord.getCellSize();

      for (World var52 : Bukkit.getWorlds()) {
         String var54 = var52.getKey().toString();
         boolean var56 = var52.getPVP();
         if (var1.isTownyWorld(var52)) {
            var56 &= CombatUtil.isWorldPvP(var1.getTownyWorld(var52));
         }

         var44.put(var54, var56);
      }

      for (TownBlock var53 : var1.getTownBlocks()) {
         World var55 = Bukkit.getWorld(var53.getWorldCoord().getWorldName());
         if (var55 != null) {
            String var57 = var55.getKey().toString();
            Location var58 = new Location(var55, var53.getX() * var47 + 0.5, 64.0, var53.getZ() * var47 + 0.5);
            var38.put(var57 + "|" + var53.getX() + "|" + var53.getZ(), var55.getPVP() && var1.isPVP(var58));
         }
      }

      this.cell = var47;
      this.claims = Map.copyOf(var38);
      this.worlds = Map.copyOf(var44);
      this.factions = List.copyOf(var25);
      this.factionEstates = Map.copyOf(var32);
      this.view = new ArmyPolicy.View(System.currentTimeMillis(), var2, var3, var4, var5, var6, var7, var8);
   }

   Set<String> participants(String var1, boolean var2, Map<String, ArmyPolicy.Land> var3) throws Exception {
      HashSet var4 = new HashSet();
      var4.add(var1);
      String var5 = var1.startsWith("n:") ? this.s.estate(var1).nation() : null;

      for (ArmyPolicy.Land var7 : var3.values()) {
         if (Objects.equals(var7.nation(), var5) && var5 != null) {
            var4.add(var7.id());
         }
      }

      boolean var12;
      do {
         var12 = false;

         for (Map var8 : this.s.db.query("SELECT * FROM vc_contract WHERE active_rev>0 AND status IN('ACTIVE','NOTICE')")) {
            ContractTerms var9 = this.s.contractBuilder.terms(Database.number(var8, "id"), (int)Database.number(var8, "active_rev"));
            String var10 = Database.string(var8, "child");
            String var11 = Database.string(var8, "parent");
            if (var4.contains(var11) && (var9.get("military").equals("ALL") || var2 && var9.get("military").equals("DEFENSIVE"))) {
               var12 |= var4.add(var10);
            }

            if (var2 && var4.contains(var10) && var9.yes("defend")) {
               var12 |= var4.add(var11);
            }
         }
      } while (var12);

      return var4;
   }

   List<Map<String, Object>> roster(String var1) throws Exception {
      Set var2 = this.army.politics == null ? Set.of(var1) : this.army.politics.lands(var1);
      ArrayList var3 = new ArrayList();

      for (Map var5 : this.s.db.query("SELECT * FROM army_units WHERE alive=TRUE ORDER BY id")) {
         if (var1.equals(Database.string(var5, "estate")) || var2.contains(Database.string(var5, "home"))) {
            var3.add(var5);
         }
      }

      return var3;
   }

   String estateLabel(String var1) throws Exception {
      return var1 == null ? "Не призначено" : this.s.estate(var1).name();
   }

   String debtDate(long var1) {
      return var1 <= 0L ? "Боргу немає" : Instant.ofEpochMilli(var1).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + " UTC";
   }

   void say(CommandSender var1, String var2) {
      this.plugin.message(var1, var2);
   }

   FeudalService.Actor actor(Player var1) {
      return new FeudalService.Actor(var1.getUniqueId().toString(), false);
   }

   String selected(Player var1, String[] var2, int var3) throws Exception {
      return var2.length > var3 ? this.s.resolve(var2[var3]).id() : Objects.requireNonNull(this.home(var1.getUniqueId().toString()), "Ви не житель міста.");
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      try {
         Model.require(var1 instanceof Player, "Команда доступна гравцю.");
         Player var5 = (Player)var1;
         this.towny.sync();
         String var6 = var4.length == 0 ? "menu" : var4[0];
         switch (var6) {
            case "menu":
            case "status":
               this.open(var5, this.selected(var5, var4, 1), 0);
               break;
            case "assign":
               Model.require(var4.length == 4, "/army assign UUID володіння місто_постачання");
               this.army.assign(this.actor(var5), var4[1], this.s.resolve(var4[2]).id(), this.s.resolve(var4[3]).id());
               this.say(var5, "Рекрута передано володінню. Командування успадковується посадою.");
               break;
            case "supply":
               Model.require(var4.length == 3, "/army supply володіння власне_місто_постачання");
               this.army.supply(this.actor(var5), this.s.resolve(var4[1]).id(), this.s.resolve(var4[2]).id());
               this.say(var5, "Місто постачання гарнізону оновлено.");
               break;
            case "release":
               Model.require(var4.length == 2, "/army release UUID");
               this.army.release(this.actor(var5), var4[1]);
               this.say(var5, "Рекрута повернуто первісному власнику.");
               break;
            case "officer":
            case "revoke":
               Model.require(var4.length == 3, "/army officer|revoke володіння гравець");
               this.army.officer(this.actor(var5), this.s.resolve(var4[1]).id(), this.towny.residentId(var4[2]), var6.equals("officer"));
               this.say(var5, "Склад командування оновлено.");
               break;
            case "upkeep":
               Model.require(var4.length == 5, "/army upkeep володіння сума_за_рекрута_на_добу валюта ліміт");
               this.say(
                  var5,
                  "Рішення: "
                     + this.army
                        .propose(this.actor(var5), this.s.resolve(var4[1]).id(), "WAGE", var4[2] + " " + var4[3].toUpperCase(Locale.ROOT) + " " + var4[4])
               );
               break;
            case "war":
               Model.require(var4.length == 3, "/army war ваше_володіння вороже_володіння");
               this.say(var5, "Рішення: " + this.army.propose(this.actor(var5), this.s.resolve(var4[1]).id(), "WAR", this.s.resolve(var4[2]).id()));
               break;
            case "peace":
               Model.require(var4.length == 3, "/army peace ваше_володіння UUID_війни");
               this.say(var5, "Рішення: " + this.army.propose(this.actor(var5), this.s.resolve(var4[1]).id(), "PEACE", var4[2]));
               break;
            case "vote":
               Model.require(var4.length == 3 && Set.of("yes", "no").contains(var4[2]), "/army vote UUID_рішення yes|no");
               this.army.vote(this.actor(var5), var4[1], var4[2].equals("yes"));
               this.say(var5, "Голос збережено.");
               break;
            case "wars":
               for (Map var20 : this.s.db.query("SELECT * FROM army_wars WHERE status='ACTIVE'")) {
                  this.say(
                     var5,
                     Database.string(var20, "id")
                        + ": "
                        + this.s.estate(Database.string(var20, "attacker")).name()
                        + " → "
                        + this.s.estate(Database.string(var20, "defender")).name()
                  );
               }
               break;
            case "history":
               String var16 = this.selected(var5, var4, 1);
               this.access(var5, var16);

               for (Map var11 : this.army.history(var16, var4.length > 2 ? Integer.parseInt(var4[2]) - 1 : 0)) {
                  this.say(var5, Database.string(var11, "event") + ": " + Database.string(var11, "detail") + " [" + Database.number(var11, "amount") + "]");
               }
               break;
            case "why":
               Model.require(var4.length == 3, "/army why UUID_рекрута UUID_цілі");
               Map var15 = this.army.unit(var4[1]);
               Model.require(
                  var5.hasPermission("empirecraft.feudal.admin") || this.army.commands(var5.getUniqueId().toString(), var15),
                  "Діагностика доступна командиру свого загону."
               );
               Map var18 = (Map)Class.forName("ua.empirecraft.recruits.ArmyHooks")
                  .getMethod("diagnose", String.class, String.class)
                  .invoke(null, var4[1], var4[2]);
               this.say(var5, "Атака: " + var18.get("allow") + "; причина: " + var18.get("reason") + "; сторони: " + var18.get("a") + " → " + var18.get("b"));
               break;
            case "command":
               Model.require(var4.length >= 3 && var4.length <= 4, "/army command володіння follow|hold|defend|attack [UUID_цілі]");
               String var14 = this.s.resolve(var4[1]).id();
               Object var10 = Class.forName("ua.empirecraft.recruits.ArmyHooks")
                  .getMethod("orders", String.class, String.class, String.class, String.class)
                  .invoke(null, var5.getUniqueId().toString(), var14, var4[2], var4.length == 4 ? var4[3] : null);
               this.say(var5, "Наказ отримали рекрути: " + var10 + " (завантажені, у межах 128 блоків).");
               break;
            case "inspect":
               Model.require(var5.hasPermission("empirecraft.feudal.admin") && var4.length == 2, "/army inspect UUID — для адміністратора");
               Map var9 = this.army.unit(var4[1]);
               Model.require(var9 != null, "Рекрут ще не зареєстрований.");
               this.say(
                  var5,
                  "Власник: "
                     + Database.string(var9, "owner")
                     + "; володіння: "
                     + Database.string(var9, "estate")
                     + "; провізія: "
                     + Database.string(var9, "home")
                     + "; завантажений: "
                     + Database.bool(var9, "loaded")
                     + "; голод: "
                     + var9.get("hunger")
                     + "; спожито: "
                     + Database.number(var9, "feed_total")
               );
               break;
            default:
               this.say(
                  var5,
                  "/army menu [місто]; assign, supply, release, officer, revoke, command, upkeep, war, peace, vote, wars, history, why, inspect. Передавання в гарнізон: лише свого рекрута, UUID видно у вкладці «Рекрути»."
               );
         }

         this.refresh();
      } catch (NullPointerException | IllegalArgumentException var12) {
         this.say(var1, "§c" + var12.getMessage());
      } catch (Exception var13) {
         this.plugin.failure("Армія", var13);
         this.say(var1, "§cОперацію не завершено; дивіться журнал сервера.");
      }

      return true;
   }

   public void open(Player var1, String var2, int var3) throws Exception {
      this.access(var1, var2);
      List<Map<String,Object>> var4 = this.roster(var2);
      ArmyController.Menu var5 = new ArmyController.Menu(var1, "Рекрути · " + this.s.estate(var2).name());
      long var6 = var4.stream().filter(var0 -> Database.bool((Map<String, Object>)var0, "loaded")).count();
      long var8 = var4.stream().filter(var0 -> ((Number)var0.get("hunger")).doubleValue() <= 1.0).count();
      double var10 = var6 * this.hungerRate() * 1440.0 / 5.0;
      this.button(
         var5,
         4,
         Material.BREAD,
         "Забезпечення рекрутів",
         null,
         "Відомих живих: " + var4.size() + "; завантажено: " + var6,
         "Голодують: " + var8,
         "Оцінка при поточній активності: " + String.format(Locale.ROOT, "%.2f", var10) + " од. поживності / реальну добу",
         "Джерело: міська провізія /t provision",
         "Незавантажені рекрути не їдять; загальна кількість поповнюється при першому завантаженні старих загонів."
      );
      if (var2.startsWith("t:") && this.army.food.town(var2) != null) {
         Map var12 = this.s
            .db
            .one("SELECT COALESCE(SUM(amount),0) AS n FROM army_log WHERE estate=? AND event='FOOD' AND at_time>?", var2, this.s.now() - 86400000L);
         String var13 = new BigDecimal(this.army.food.reserve(var2)).divide(new BigDecimal(ProvisionClock.UNIT), 2, RoundingMode.DOWN).toPlainString();
         this.button(
            var5,
            5,
            Material.CHEST,
            "Запас і фактичні витрати",
            var0 -> var0.performCommand("t provision"),
            "Спільний залишок: " + var13 + " од.",
            "Рекрути з'їли за 24 год: " + Database.number(var12, "n") + " од.",
            "Запас також витрачається на територію міста."
         );
      }

      int var16 = var3 * 27;

      for (int var17 = var16; var17 < Math.min(var16 + 27, var4.size()); var17++) {
         Map var14 = (Map)var4.get(var17);
         String var15 = Database.string(var14, "id");
         this.button(
            var5,
            9 + var17 - var16,
            Material.IRON_HELMET,
            var15,
            var2x -> this.say(var2x, "UUID: " + var15 + "; передавання: /army assign " + var15 + " <володіння> <місто>"),
            "Власник: " + this.towny.playerName(Database.string(var14, "owner")),
            "Володіння: " + (Database.string(var14, "estate") == null ? "Особистий рекрут" : this.estateLabel(Database.string(var14, "estate"))),
            "Постачання: " + this.estateLabel(Database.string(var14, "home")),
            "Голод: "
               + String.format(Locale.ROOT, "%.1f", ((Number)var14.get("hunger")).doubleValue())
               + "/100; "
               + (Database.bool(var14, "loaded") ? "завантажений" : "поза завантаженими чанками")
         );
      }

      Map var18 = this.army.settings(var2);
      this.button(
         var5,
         40,
         Material.GOLD_INGOT,
         "Утримання гарнізону",
         null,
         "На рекрута / добу: " + Model.format(Database.number(var18, "wage")) + " " + Database.string(var18, "code"),
         "Ліміт: " + Database.number(var18, "unit_limit"),
         "Утримання: " + this.debtDate(Database.number(var18, "unpaid_since")),
         "Зміна: /army upkeep володіння сума валюта ліміт"
      );
      this.button(var5, 41, Material.PAPER, "Останні події", var2x -> {
         for (Map var4x : this.army.history(var2, 0)) {
            this.say(var2x, Database.string(var4x, "event") + ": " + Database.string(var4x, "detail") + " [" + Database.number(var4x, "amount") + "]");
         }
      });
      this.button(
         var5,
         42,
         Material.LECTERN,
         "Рішення ради",
         var2x -> {
            for (Map var4x : this.s.db.query("SELECT * FROM army_motions WHERE estate=? AND status='OPEN'", var2)) {
               this.say(
                  var2x,
                  Database.string(var4x, "id") + " " + Database.string(var4x, "kind") + " " + Database.string(var4x, "args") + "; /army vote UUID yes|no"
               );
            }
         }
      );
      if (var3 > 0) {
         this.button(var5, 45, Material.ARROW, "Назад", var3x -> this.open(var3x, var2, var3 - 1));
      }

      if (var16 + 27 < var4.size()) {
         this.button(var5, 53, Material.ARROW, "Далі", var3x -> this.open(var3x, var2, var3 + 1));
      }

      this.button(var5, 49, Material.SUNFLOWER, "Оновити", var3x -> this.open(var3x, var2, var3));
      var1.openInventory(var5.inv);
   }

   void button(ArmyController.Menu var1, int var2, Material var3, String var4, ArmyController.Action var5, String... var6) {
      ItemStack var7 = new ItemStack(var3);
      ItemMeta var8 = var7.getItemMeta();
      var8.setDisplayName("§e" + var4);
      ArrayList var9 = new ArrayList();

      for (String var13 : var6) {
         for (byte var14 = 0; var14 < var13.length(); var14 += 48) {
            var9.add("§7" + var13.substring(var14, Math.min(var14 + 48, var13.length())));
         }
      }

      var8.setLore(var9);
      var7.setItemMeta(var8);
      var1.inv.setItem(var2, var7);
      if (var5 != null) {
         var1.actions.put(var2, var5);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change0(NationKingChangeEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change1(TownMayorChangeEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change2(NationAcceptAllyRequestEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change3(NationPreAddAllyEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change4(TownAddResidentEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change5(TownRemoveResidentEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change6(NationAddTownEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change7(NationRemoveTownEvent var1) {
      this.invalidate();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void change8(TownTogglePVPEvent var1) {
      this.invalidate();
   }

   @EventHandler
   public void townMenu(InventoryOpenEvent var1) {
      if (var1.getInventory().getHolder() instanceof TownMenu.View var2 && var1.getInventory().getItem(51) == null) {
         ItemStack var5 = new ItemStack(Material.IRON_HELMET);
         ItemMeta var4 = var5.getItemMeta();
         var4.setDisplayName("§6Рекрути");
         var4.setLore(List.of("§7Кількість, провізія, витрати та гарнізони"));
         var4.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "army_menu"), PersistentDataType.BYTE, (byte)1);
         var5.setItemMeta(var4);
         var1.getInventory().setItem(51, var5);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void click(InventoryClickEvent var1) {
      if (var1.getWhoClicked() instanceof Player var2) {
         if (var1.getView().getTopInventory().getHolder() instanceof TownMenu.View var7 && var1.getRawSlot() == 51) {
            ItemStack var11 = var1.getCurrentItem();
            if (var11 != null
               && var11.hasItemMeta()
               && var11.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this.plugin, "army_menu"), PersistentDataType.BYTE)) {
               var1.setCancelled(true);
               Bukkit.getScheduler().runTask(this.plugin, () -> {
                  try {
                     this.open(var2, var7.estate, 0);
                  } catch (Exception var4) {
                     this.say(var2, "§c" + var4.getMessage());
                  }
               });
            }
         } else if (var1.getView().getTopInventory().getHolder() instanceof ArmyController.Menu var8) {
            var1.setCancelled(true);
            if (var8.owner.equals(var2.getUniqueId())) {
               ArmyController.Action var10 = var8.actions.get(var1.getRawSlot());
               if (var10 != null) {
                  try {
                     var10.run(var2);
                  } catch (Exception var6) {
                     this.say(var2, "§c" + var6.getMessage());
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void drag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof ArmyController.Menu) {
         var1.setCancelled(true);
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      return var4.length == 1
         ? List.of(
            "menu",
            "status",
            "assign",
            "release",
            "officer",
            "revoke",
            "upkeep",
            "war",
            "peace",
            "vote",
            "wars",
            "history",
            "inspect",
            "why",
            "command",
            "supply"
         )
         : List.of();
   }

   @FunctionalInterface
   interface Action {
      void run(Player var1) throws Exception;
   }

   static final class Menu implements InventoryHolder {
      final UUID owner;
      final Inventory inv;
      final Map<Integer, ArmyController.Action> actions = new HashMap<>();

      Menu(Player var1, String var2) {
         this.owner = var1.getUniqueId();
         this.inv = Bukkit.createInventory(this, 54, var2);
      }

      public Inventory getInventory() {
         return this.inv;
      }
   }
}
