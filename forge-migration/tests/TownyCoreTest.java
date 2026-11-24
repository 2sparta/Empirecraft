package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ua.empirecraft.townyforge.TownyGovernance.Actor;
import ua.empirecraft.townyforge.TownyGovernance.Here;
import ua.empirecraft.townyforge.TownySnapshot.Action;
import ua.empirecraft.townyforge.TownySnapshot.Claim;
import ua.empirecraft.townyforge.TownySnapshot.Source;

public final class TownyCoreTest {
    private static final String WORLD = "world_europe", DIMENSION = "minecraft:overworld";
    private static final UUID MAYOR = UUID.randomUUID(), RESIDENT = UUID.randomUUID(), OTHER = UUID.randomUUID();
    private static final Here CELL = new Here(DIMENSION, 19, 65, 1, 0, 0);

    public static void main(String[] args) throws Exception {
        Path world = Path.of(args[0]);
        Files.createDirectories(world.resolve("empirecraft"));
        var configured = mutable(Map.ofEntries(Map.entry("economy.using_economy", "true"),
                Map.entry("economy.daily_taxes.enabled", "true"), Map.entry("economy.daily_taxes.price_town_upkeep", "10"),
                Map.entry("economy.daily_taxes.price_nation_upkeep", "2"), Map.entry("plots.farm.allowedBlocks", "WHEAT"),
                Map.entry("jail.bail.is_allowing_bail", "true"), Map.entry("jail.bail.bail_amount", "7")));
        String north = "town:" + UUID.randomUUID(), south = "town:" + UUID.randomUUID(), region = "nation:" + UUID.randomUUID();
        var nations = new HashMap<String,Map<String,String>>();
        nations.put("Region", mutable(Map.of("uuid", region.substring(7), "capital", "North", "taxes", "5")));
        var towns = new HashMap<String,Map<String,String>>();
        towns.put("North", mutable(Map.of("uuid", north.substring(5), "mayor", "Mayor", "nation", "Region",
                "taxes", "10", "taxpercent", "true", "plotTax", "5", "homeBlock", WORLD + ",0,0")));
        towns.put("South", mutable(Map.of("uuid", south.substring(5), "mayor", "Other", "nation", "Region", "hasUpkeep", "false")));
        var residents = new HashMap<String,Map<String,String>>();
        residents.put("Mayor", mutable(Map.of("uuid", MAYOR.toString(), "town", "North")));
        residents.put("Resident", mutable(Map.of("uuid", RESIDENT.toString(), "town", "North", "forgeJailUntil", "999999")));
        residents.put("Other", mutable(Map.of("uuid", OTHER.toString(), "town", "South")));
        var claims = new ArrayList<>(List.of(
                new Claim(WORLD, 0, 0, mutable(Map.of("town", "North", "type", "default"))),
                new Claim(WORLD, 1, 0, mutable(Map.of("town", "North", "type", "farm", "resident", "Resident",
                        "permissions", "nationDestroy")))));
        claims.add(new Claim(WORLD, 2, 0, mutable(Map.of("town", "South", "type", "jail", "jailSpawn", "legacy"))));
        var prefs = mutable(Map.of(north, "20", south, "1", region, "4", "resident:" + RESIDENT, "100", "resident:" + OTHER, "-12"));
        var data = new Source(1, Map.of(WORLD, DIMENSION), Map.of(WORLD, Map.of("usingTowny", "true")),
                towns, nations, residents, claims, List.of(), List.of(), configured, Map.of(), prefs, new ArrayList<>());
        Files.writeString(world.resolve("empirecraft/towny-import.json"), TownySnapshot.JSON.toJson(data));

        var store = new TownyStore(world);
        check(store.snapshot().allows(DIMENSION, 19, 1, OTHER, Action.DESTROY, "WHEAT"), "farm crop");
        check(!store.snapshot().allows(DIMENSION, 19, 1, OTHER, Action.DESTROY, "STONE"), "farm restriction");
        store.collectDaily(86400);
        check(balance(store, north, "20"), "first startup establishes the daily marker");
        store.collectDaily(172800);
        check(balance(store, north, "25") && balance(store, region, "2"), "town and nation upkeep");
        check(balance(store, "resident:" + RESIDENT, "85"), "resident tax and plot tax");
        check(store.snapshot().town("South").get("nation").isEmpty(), "unpaid nation tax");
        store = new TownyStore(world);
        store.collectDaily(172808);
        check(balance(store, north, "25"), "restart cannot charge twice");
        store.change(source -> {
            var town = new TownyGovernance(source, new Actor(MAYOR, "Mayor"));
            town.setPlotType(CELL, "arena");
            return null;
        });
        check(store.snapshot().environmental(DIMENSION, 19, 1, "pvp", "forcepvp", "pvp"), "arena PvP");
        check(store.snapshot().view().at(DIMENSION, 33, 1).type().equals("default"), "old jail is an ordinary plot");
        check(!store.snapshot().resident("Resident").containsKey("forgeJailUntil"), "old confinement discarded");
        check(balance(store, "resident:" + OTHER, "0"), "old overdraft discarded");
        try {
            store.change(source -> {
                new TownyGovernance(source, new Actor(MAYOR, "Mayor")).setPlotType(CELL, "jail");
                return null;
            });
            throw new AssertionError("jail plot creation succeeded");
        } catch (TownyProblem expected) {
            check(store.snapshot().view().at(DIMENSION, 19, 1).type().equals("arena"), "rejected update");
        }
        System.out.println("PASS Towny daily charges, restart, farm, arena and removal of legacy jails and debt");
        Path fresh = world.resolve("fresh-world");
        var initialized = new TownyStore(fresh);
        check(initialized.snapshot().towns().isEmpty() && initialized.snapshot().nations().isEmpty(), "fresh world starts empty");
        check(initialized.snapshot().allows(DIMENSION, 501, 1, OTHER, Action.BUILD, "STONE"), "wilderness is usable");
        String worldId = initialized.snapshot().legacyWorld(DIMENSION);
        initialized.change(source -> new TownyGovernance(source, new Actor(MAYOR, "Mayor")).foundTown("NewCity", CELL));
        var reopened = new TownyStore(fresh);
        check(worldId.equals(reopened.snapshot().legacyWorld(DIMENSION)), "world ID persists");
        check(reopened.snapshot().isClaimed(DIMENSION, 19, 1), "new town after fresh startup");
        System.out.println("PASS fresh Towny state, wilderness, town founding and restart");
    }

    private static HashMap<String,String> mutable(Map<String,String> map) { return new HashMap<>(map); }

    private static boolean balance(TownyStore store, String account, String expected) {
        return new TownyEconomy(store.snapshot().editableCopy()).balance(account).compareTo(new BigDecimal(expected)) == 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
