package ua.empirecraft.titles;

import java.util.*;

public final class TitleRulesTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static final TitleRules.Title SHOP = new TitleRules.Title("shop_1", "titul.1", "suffix", "Title", "shop", "AIR", 0);
    private static final TitleRules.Title ACHIEVEMENT = new TitleRules.Title("achievement_1", "title.1", "icon", "Achievement", "achievement", "DIAMOND", 64);
    private static final TitleRules.Title SEASON = new TitleRules.Title("season_1", "tituls.3_1", "season", "Season", "season", "AIR", 0);
    private static final class Port implements TitleRules.PlayerPort {
        final Set<String> permissions = new HashSet<>();
        double money = 100;
        int items = 64, withdrawals, removals, selections;
        boolean economyWorks = true, grantWorks = true;
        String selected;
        public boolean owns(String permission) { return permissions.contains(permission); }
        public boolean withdraw(double price) {
            withdrawals++;
            if (!economyWorks || money < price) return false;
            money -= price; return true;
        }
        public boolean consume(String material, int amount) {
            if (items < amount) return false;
            items -= amount; removals++; return true;
        }
        public void refundMoney(double price) { money += price; }
        public void refundItems(String material, int amount) { items += amount; }
        public boolean grant(String permission) { if (!grantWorks) return false; permissions.add(permission); return true; }
        public void select(String suffix) { selected = suffix; selections++; }
    }
    public static void main(String[] args) {
        Port p = new Port();
        check(TitleRules.choose(p, SHOP) == TitleRules.Result.LOCKED);
        check(p.selections == 0 && p.money == 100);
        check(TitleRules.unlock(p, SHOP, 30) == TitleRules.Result.UNLOCKED);
        check(p.money == 70 && p.withdrawals == 1 && p.owns("titul.1") && p.selected.equals("suffix"));
        for (int i = 0; i < 100; i++) check(TitleRules.unlock(p, SHOP, 30) == TitleRules.Result.SELECTED);
        check(p.money == 70 && p.withdrawals == 1);
        p.permissions.clear();
        check(TitleRules.choose(p, SHOP) == TitleRules.Result.LOCKED); // revoked while menu is open
        p = new Port(); p.economyWorks = false;
        check(TitleRules.unlock(p, SHOP, 30) == TitleRules.Result.NO_MONEY);
        check(p.money == 100 && p.permissions.isEmpty() && p.selections == 0);
        p = new Port(); p.money = 29.99;
        check(TitleRules.unlock(p, SHOP, 30) == TitleRules.Result.NO_MONEY);
        check(p.money == 29.99 && p.permissions.isEmpty());
        p = new Port(); p.grantWorks = false;
        check(TitleRules.unlock(p, SHOP, 30) == TitleRules.Result.FAILED);
        check(p.money == 100 && p.selections == 0);
        check(TitleRules.unlock(p, ACHIEVEMENT, 30) == TitleRules.Result.FAILED);
        check(p.items == 64 && p.permissions.isEmpty());
        p = new Port(); p.items = 63;
        check(TitleRules.unlock(p, ACHIEVEMENT, 30) == TitleRules.Result.NO_ITEMS);
        check(p.items == 63 && p.removals == 0 && p.permissions.isEmpty());
        p.items = 64;
        check(TitleRules.unlock(p, ACHIEVEMENT, 30) == TitleRules.Result.UNLOCKED);
        check(p.items == 0 && p.removals == 1 && p.owns("title.1"));
        check(TitleRules.unlock(p, ACHIEVEMENT, 30) == TitleRules.Result.SELECTED);
        check(p.removals == 1 && p.money == 100);
        p = new Port();
        check(TitleRules.unlock(p, SEASON, 30) == TitleRules.Result.LOCKED);
        check(p.money == 100 && p.items == 64 && p.permissions.isEmpty());
        p.permissions.add(SEASON.permission());
        check(TitleRules.choose(p, SEASON) == TitleRules.Result.SELECTED);
        for (double price : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            p = new Port(); check(TitleRules.unlock(p, SHOP, price) == TitleRules.Result.UNAVAILABLE);
            check(p.money == 100 && p.withdrawals == 0);
        }
        int[] stacks = {64, 64, 64, 57, 1};
        check(Arrays.equals(TitleRules.plan(stacks, 250), stacks));
        check(TitleRules.plan(stacks, 251) == null);
        check(Arrays.equals(stacks, new int[]{64,64,64,57,1})); // failed planning never mutates inventory
        check(Arrays.equals(TitleRules.plan(new int[]{63, 0, 1}, 64), new int[]{63,0,1}));
        check(Arrays.equals(TitleRules.plan(new int[]{64,64}, 65), new int[]{64,1}));
        check(TitleRules.plan(new int[]{}, 1) == null);
        System.out.println("Title transaction checks passed: " + checks);
    }
}
