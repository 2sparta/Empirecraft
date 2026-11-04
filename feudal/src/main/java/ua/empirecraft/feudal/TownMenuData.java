/* Recovered from the installed Feudal 1.11.0 (SHA-256 b1715e0dbebb4c3d6dbeb5e08f461a5e87acc7dcd982f0aad22fd0a9b91c8ab2) with Vineflower 1.11.1; missing generic local types restored. */
package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class TownMenuData {
   static final int PAGE_SIZE = 36;
   final FeudalService s;

   TownMenuData(FeudalService var1) {
      this.s = var1;
   }

   List<Model.Estate> estates(FeudalService.Actor var1, String var2) throws SQLException {
      return this.s
         .estates()
         .stream()
         .filter(Model.Estate::active)
         .filter(var3 -> this.s.controls(var1, var3) || Objects.equals(var3.id(), var2) || this.s.citizen(var1.id(), var3.nation()))
         .sorted(Comparator.comparing(Model.Estate::name).thenComparing(Model.Estate::id))
         .toList();
   }

   void access(FeudalService.Actor var1, String var2, String var3) throws SQLException {
      Model.require(this.estates(var1, var2).stream().anyMatch(var1x -> var1x.id().equals(var3)), "Володіння недоступне. Відкрийте /t menu знову.");
   }

   List<Model.Estate> vassals(String var1) throws SQLException {
      String var2 = this.s.authority(var1);
      ArrayList var3 = new ArrayList();

      for (Model.Estate var5 : this.s.children(var2)) {
         if (!var2.equals(this.s.authority(var5.id()))) {
            var3.add(var5);
         }
      }

      return List.copyOf(var3);
   }

   List<Map<String, Object>> offers(FeudalService.Actor var1) throws SQLException {
      return this.s
         .db
         .query("SELECT * FROM proposals WHERE expires>? ORDER BY expires,id", this.s.now())
         .stream()
         .filter(var1x -> var1.admin() || csv(Database.string((Map<String, Object>)var1x, "required")).contains(var1.id()))
         .toList();
   }

   static Set<String> csv(String var0) {
      return (Set<String>)(var0 != null && !var0.isBlank() ? new HashSet<>(Arrays.asList(var0.split(","))) : Set.of());
   }

   static int page(int var0, int var1) {
      return Math.max(0, Math.min(var0, Math.max(0, (var1 - 1) / 36)));
   }

   static <T> List<T> slice(List<T> var0, int var1) {
      int var2 = page(var1, var0.size()) * 36;
      return List.copyOf(var0.subList(var2, Math.min(var0.size(), var2 + 36)));
   }

   static String token(String var0) {
      Model.require(var0 != null && var0.matches("[\\p{L}\\p{N}_:.-]{1,80}"), "Укажіть одне ім’я, ID або назву з підкресленнями замість пробілів.");
      return var0;
   }

   static String input(String var0) {
      Model.require(
         var0 != null && !var0.isBlank() && var0.length() <= 500 && !var0.matches("(?s).*[\\p{Cntrl}§/].*"), "Введіть 1–500 знаків без команд і форматування."
      );
      return var0.trim();
   }

   static int days(String var0) {
      Model.require(var0.matches("[0-9]{1,3}"), "Строк: ціле число 1–365.");
      int var1 = Integer.parseInt(var0);
      Model.require(var1 >= 1 && var1 <= 365, "Строк: 1–365 діб.");
      return var1;
   }
}
