package ua.empirecraft.titles;

import java.util.*;

public final class TitleRulesTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static final TitleRules.Requirement MINING = new TitleRules.Requirement("statistic", "MINE_BLOCK", List.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"), 64, "Видобути 64 блоки руди.");
    private static final TitleRules.Requirement FORGING = new TitleRules.Requirement("advancement", "overgeared:steel_pickaxe", List.of(), 1, "Отримати сталеву кірку.");
    private static final TitleRules.Title LEGACY = new TitleRules.Title("shop_1", "titul.1", "suffix", "Title", "achievement", List.of(MINING));
    private static final TitleRules.Title COMBINED = new TitleRules.Title("art_dune", "empirecraft.titles.art.dune", "icon", "Achievement", "achievement", List.of(MINING, FORGING));
    private static final TitleRules.Title SEASON = new TitleRules.Title("season_1", "tituls.3_1", "season", "Season", "season", List.of());
    private static final class Port implements TitleRules.PlayerPort {
        final Set<String> permissions = new HashSet<>();
        final Map<String, Long> progress = new HashMap<>();
        int grants, reads, selections;
        boolean grantWorks = true;
        String selected = "previous title";
        public boolean owns(String permission) { return permissions.contains(permission); }
        public long progress(TitleRules.Requirement requirement) { reads++; return progress.getOrDefault(requirement.key(), 0L); }
        public boolean grant(String permission) { grants++; if (!grantWorks) return false; permissions.add(permission); return true; }
        public void select(String suffix) { selected = suffix; selections++; }
    }
    private static void invalid(Runnable task) {
        try { task.run(); throw new AssertionError("Expected invalid requirement"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        Port p = new Port();
        check(TitleRules.choose(p, LEGACY) == TitleRules.Result.LOCKED);
        check(TitleRules.unlock(p, LEGACY) == TitleRules.Result.INCOMPLETE);
        check(p.grants == 0 && p.selections == 0);
        p.progress.put("MINE_BLOCK", 63L);
        check(TitleRules.unlock(p, LEGACY) == TitleRules.Result.INCOMPLETE);
        check(p.permissions.isEmpty());
        p.progress.put("MINE_BLOCK", 64L);
        check(TitleRules.unlock(p, LEGACY) == TitleRules.Result.UNLOCKED);
        check(p.grants == 1 && p.owns("titul.1") && p.selected.equals("suffix"));
        check(p.progress.get("MINE_BLOCK") == 64L); // Claimed progress is never spent.
        p.progress.clear(); // Old entitlements do not depend on replaying a milestone.
        int reads = p.reads;
        for (int i = 0; i < 100; i++) check(TitleRules.unlock(p, LEGACY) == TitleRules.Result.SELECTED);
        check(p.grants == 1 && p.reads == reads);
        p.permissions.clear();
        check(TitleRules.choose(p, LEGACY) == TitleRules.Result.LOCKED); // Stale menu after revocation.
        p = new Port(); p.progress.put("MINE_BLOCK", 64L);
        check(TitleRules.claim(p, COMBINED) == TitleRules.Result.INCOMPLETE);
        check(p.grants == 0);
        p.progress.put("overgeared:steel_pickaxe", -1L);
        check(TitleRules.claim(p, COMBINED) == TitleRules.Result.UNAVAILABLE);
        check(p.permissions.isEmpty() && p.selected.equals("previous title"));
        p.progress.put("overgeared:steel_pickaxe", 1L);
        p.grantWorks = false;
        check(TitleRules.claim(p, COMBINED) == TitleRules.Result.FAILED);
        check(p.permissions.isEmpty() && p.selections == 0);
        p.grantWorks = true;
        Map<String, Long> before = Map.copyOf(p.progress);
        check(TitleRules.claim(p, COMBINED) == TitleRules.Result.UNLOCKED);
        check(p.selections == 0 && p.selected.equals("previous title")); // Auto-award never equips.
        check(p.progress.equals(before));
        int grants = p.grants;
        for (int i = 0; i < 100; i++) check(TitleRules.claim(p, COMBINED) == TitleRules.Result.OWNED);
        check(p.grants == grants && p.selections == 0);
        check(TitleRules.choose(p, COMBINED) == TitleRules.Result.SELECTED);
        p = new Port();
        check(TitleRules.unlock(p, SEASON) == TitleRules.Result.LOCKED);
        check(p.grants == 0 && p.reads == 0);
        p.permissions.add(SEASON.permission());
        check(TitleRules.unlock(p, SEASON) == TitleRules.Result.SELECTED);
        // Above-threshold history and integer limits must qualify normally.
        p = new Port(); p.progress.put("MINE_BLOCK", (long) Integer.MAX_VALUE * 2);
        check(TitleRules.unlock(p, LEGACY) == TitleRules.Result.UNLOCKED);
        invalid(() -> new TitleRules.Requirement("statistic", "MINE_BLOCK", List.of(), 0, "Description"));
        invalid(() -> new TitleRules.Requirement("unknown", "key", List.of(), 1, "Description"));
        invalid(() -> new TitleRules.Requirement("advancement", "key", List.of(), 2, "Description"));
        invalid(() -> new TitleRules.Requirement("towny", "member", List.of("ITEM"), 1, "Description"));
        invalid(() -> new TitleRules.Title("bad", "node", "suffix", "Title", "achievement", List.of()));
        invalid(() -> new TitleRules.Title("bad", "node", "suffix", "Title", "season", List.of(MINING)));
        System.out.println("Title progression checks passed: " + checks);
    }
}
