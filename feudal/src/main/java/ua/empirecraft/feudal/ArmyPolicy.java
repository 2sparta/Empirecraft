/* Recovered from the installed Feudal 1.11.0 (SHA-256 b1715e0dbebb4c3d6dbeb5e08f461a5e87acc7dcd982f0aad22fd0a9b91c8ab2) with Vineflower 1.11.1; missing generic local types restored. */
package ua.empirecraft.feudal;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ArmyPolicy {
   private ArmyPolicy() {
   }

   public static String pair(String var0, String var1) {
      return var0.compareTo(var1) <= 0 ? var0 + "|" + var1 : var1 + "|" + var0;
   }

   public static ArmyPolicy.View empty() {
      return new ArmyPolicy.View(0L, Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
   }

   public record Land(String id, String nation, String root, String ruler) {
   }

   public record Result(boolean allow, String reason, String attacker, String target) {
   }

   public record Soldier(String owner, String estate, String home, boolean alive) {
   }

   public record View(
      long updated,
      Map<String, ArmyPolicy.Land> lands,
      Map<String, String> citizens,
      Map<String, ArmyPolicy.Soldier> units,
      Set<String> allies,
      Set<String> pacts,
      Set<String> wars,
      Set<String> officers
   ) {
      public View(
         long updated,
         Map<String, ArmyPolicy.Land> lands,
         Map<String, String> citizens,
         Map<String, ArmyPolicy.Soldier> units,
         Set<String> allies,
         Set<String> pacts,
         Set<String> wars,
         Set<String> officers
      ) {
         lands = Map.copyOf(lands);
         citizens = Map.copyOf(citizens);
         units = Map.copyOf(units);
         allies = Set.copyOf(allies);
         pacts = Set.copyOf(pacts);
         wars = Set.copyOf(wars);
         officers = Set.copyOf(officers);
         this.updated = updated;
         this.lands = lands;
         this.citizens = citizens;
         this.units = units;
         this.allies = allies;
         this.pacts = pacts;
         this.wars = wars;
         this.officers = officers;
      }

      public String estate(String var1, String var2, boolean var3) {
         if (var3) {
            ArmyPolicy.Soldier var4 = this.units.get(var1);
            return var4 != null && var4.estate() != null ? var4.estate() : this.citizens.get(var2 == null ? "" : var2);
         } else {
            return this.citizens.get(var1 == null ? "" : var1);
         }
      }

      public boolean commands(String var1, String var2) {
         ArmyPolicy.Soldier var3 = this.units.get(var2);
         if (var3 == null || !var3.alive()) {
            return false;
         } else if (var3.estate() == null) {
            return Objects.equals(var1, var3.owner());
         } else {
            ArmyPolicy.Land var4 = this.lands.get(var3.estate());
            return var4 != null && (Objects.equals(var1, var4.ruler()) || this.officers.contains(var3.estate() + "|" + var1));
         }
      }

      public boolean friendly(String var1, String var2) {
         if (var1 != null && var2 != null) {
            if (!var1.equals(var2) && !this.allies.contains(ArmyPolicy.pair(var1, var2))) {
               ArmyPolicy.Land var3 = this.lands.get(var1);
               ArmyPolicy.Land var4 = this.lands.get(var2);
               return var3 != null && var4 != null
                  ? Objects.equals(var3.root(), var4.root())
                     || var3.nation() != null && var3.nation().equals(var4.nation())
                     || var3.nation() != null && var4.nation() != null && this.allies.contains(ArmyPolicy.pair(var3.nation(), var4.nation()))
                  : false;
            } else {
               return true;
            }
         } else {
            return false;
         }
      }

      boolean related(Set<String> var1, String var2, String var3) {
         if (var2 == null || var3 == null) {
            return false;
         } else if (var1.contains(ArmyPolicy.pair(var2, var3))) {
            return true;
         } else {
            ArmyPolicy.Land var4 = this.lands.get(var2);
            ArmyPolicy.Land var5 = this.lands.get(var3);
            return var4 != null
               && var5 != null
               && var4.nation() != null
               && var5.nation() != null
               && var1.contains(ArmyPolicy.pair(var4.nation(), var5.nation()));
         }
      }

      public ArmyPolicy.Result decide(
         String var1, String var2, boolean var3, String var4, String var5, boolean var6, boolean var7, boolean var8, boolean var9, boolean var10, long var11
      ) {
         String var13 = this.estate(var1, var2, var3);
         String var14 = this.estate(var4, var5, var6);
         if (var11 - this.updated > 5000L || var11 < this.updated) {
            return new ArmyPolicy.Result(false, "Дані Towny ще не готові або застаріли", var13, var14);
         } else if (!var3 && !var6) {
            return new ArmyPolicy.Result(true, "Поза правилами рекрутів", var13, var14);
         } else {
            String var15;
            if (!Objects.equals(var1, var4)
               && (var2 == null || !var2.equals(var4))
               && (var5 == null || !var5.equals(var1))
               && (var2 == null || !var2.equals(var5))) {
               if (var3 && var8 && !var6) {
                  return new ArmyPolicy.Result(true, "Ворожий монстр", var13, var14);
               }

               if (var3 && var7 && !var6) {
                  var15 = "Цивільний мешканець або тварина";
               } else if (!var9) {
                  var15 = "На території заборонено PvP";
               } else if (this.friendly(var13, var14)) {
                  var15 = "Спільне володіння, васальний дім або прямий союз";
               } else if (this.related(this.pacts, var13, var14)) {
                  var15 = "Діє договір про ненапад";
               } else {
                  if (var3 && var10) {
                     return new ArmyPolicy.Result(true, "Тимчасова самооборона проти фактичного нападника", var13, var14);
                  }

                  if (!var3 && var14 != null) {
                     return new ArmyPolicy.Result(true, "Напад на нейтрального рекрута дозволений територіальними правилами; можлива самооборона", var13, var14);
                  }

                  if (var13 != null && var14 != null) {
                     if (this.related(this.wars, var13, var14)) {
                        return new ArmyPolicy.Result(true, "Офіційна війна", var13, var14);
                     }

                     if (var10) {
                        return new ArmyPolicy.Result(true, "Тимчасова самооборона проти фактичного нападника", var13, var14);
                     }

                     var15 = "Нейтральна сторона: напад першими заборонено";
                  } else {
                     var15 = "Не визначено місто або володіння сторони";
                  }
               }
            } else {
               var15 = "Власник або його власний загін";
            }

            return new ArmyPolicy.Result(false, var15, var13, var14);
         }
      }
   }
}
