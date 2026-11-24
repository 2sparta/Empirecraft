package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;
import ua.empirecraft.townyforge.TownyGovernance.Actor;
import ua.empirecraft.townyforge.TownyGovernance.Here;

public final class TownyTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final Actor MARIA = new Actor(UUID.fromString("755a0bb1-e909-4c65-9bd4-5427cfb057f9"), "MariaTest");
    private static final Actor MARKO = new Actor(UUID.fromString("73845ad2-eb2b-4755-a409-5c029eacaefb"), "MarkoTest");
    private static final Actor NINA = new Actor(UUID.fromString("30609be3-9aa8-4843-8f2b-23c3328ff3c1"), "NinaTest");
    private static final Actor OSTAP = new Actor(UUID.fromString("2cb1e362-bd3c-48d0-a916-2574b3697360"), "OstapTest");
    private static final Actor OKSANA = new Actor(UUID.fromString("92f73861-6f98-4d69-bbbe-b2735493346c"), "OksanaTest");
    private static final Here HOME = position(-8001, -8017);
    private static final Here PLOT = position(-7985, -8017);
    private static final Here OTHER = position(-7953, -8017);
    private static final Here THIRD = position(-7000, -7000);
    private static final Here OUTPOST = position(-9000, -9000);

    private static Here position(int x, int z) {
        return new Here(OVERWORLD, x, 72, z, 0, 0);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] arguments) throws Exception {
        Path original = Path.of(arguments[0]);
        Path world = Path.of(arguments[1]);
        territory(TownySnapshot.load(original));
        Files.createDirectories(world.resolve("empirecraft"));
        Files.copy(original.resolve("empirecraft/towny-import.json"), world.resolve("empirecraft/towny-import.json"));
        var store = new TownyStore(world);
        check(!store.snapshot().editableCopy().balances().isEmpty(), "EssentialsX economy imported");
        store.change(data -> {
            for (Actor actor : new Actor[]{MARIA, MARKO, NINA, OSTAP, OKSANA}) new TownyGovernance(data, actor);
            data.balances().put("resident:" + MARIA.id(), "100");
            data.balances().put("resident:" + MARKO.id(), "20");
            return null;
        });
        edit(store, MARIA, g -> g.foundTown("Тестове місто", HOME));
        check(store.snapshot().townOf(MARIA.id()).equals("Тестове_місто"), "Unicode town founding");
        check(store.snapshot().claim(OVERWORLD, -8001, -8017).x() == -501, "home claim with negative coordinates");
        reject(store, MARIA, g -> g.claimTown(OTHER));
        edit(store, MARIA, g -> g.claimTown(PLOT));
        reject(store, MARIA, g -> g.unclaimTown(HOME));
        edit(store, MARIA, g -> g.inviteResident("markotest"));
        reject(store, MARIA, g -> g.inviteResident("MarkoTest"));
        check(new TownyGovernance(store.snapshot().editableCopy(), MARKO).inbox().contains("Тестове_місто"), "invitation inbox");
        edit(store, MARKO, g -> g.joinTown("тестове_місто", true));
        reject(store, NINA, g -> g.joinTown("Тестове_місто", false));
        reject(store, MARKO, g -> g.inviteResident("NinaTest"));
        edit(store, MARIA, g -> g.rank("town", "MarkoTest", "assistant", true));
        edit(store, MARKO, g -> g.inviteResident("NinaTest"));
        reject(store, MARIA, g -> g.claimTown(OUTPOST, true));
        edit(store, MARKO, g -> g.inviteResident("OksanaTest"));
        edit(store, OKSANA, g -> g.joinTown("Тестове_місто", true));
        edit(store, MARIA, g -> g.claimTown(OUTPOST, true));
        check(TownySnapshot.flag(store.snapshot().claim(OVERWORLD, -9000, -9000).data(), "outpost"), "outpost created at configured limit");
        edit(store, MARIA, g -> g.unclaimTown(OUTPOST));

        edit(store, MARIA, g -> g.bank("town", "25", false, HOME));
        reject(store, MARIA, g -> g.bank("town", "30", true, HOME));
        reject(store, MARKO, g -> g.bank("town", "1", true, HOME));
        edit(store, MARIA, g -> g.sellPlot(PLOT, "7.50"));
        edit(store, MARKO, g -> g.claimPlot(PLOT));
        check(balance(store, "resident:" + MARKO.id()).compareTo(new BigDecimal("12.50")) == 0, "plot debit");
        String townAccount = TownyEconomy.account("town", store.snapshot().town("Тестове_місто"));
        check(balance(store, townAccount).compareTo(new BigDecimal("32.50")) == 0, "atomic town balance and plot transfer");
        edit(store, MARKO, g -> g.setPermissions("plot", PLOT, "outsider", "switch", true));
        check(store.snapshot().allows(OVERWORLD, -7985, -8017, NINA.id(), TownySnapshot.Action.SWITCH, ""), "applied plot permissions");
        reject(store, MARIA, g -> g.unclaimTown(PLOT));

        edit(store, NINA, g -> g.foundTown("Вежа", OTHER));
        edit(store, OSTAP, g -> g.foundTown("Степ", THIRD));
        edit(store, MARIA, g -> g.foundNation("Союз"));
        edit(store, OSTAP, g -> g.foundNation("Рада"));
        edit(store, MARIA, g -> g.inviteTown("Вежа"));
        edit(store, NINA, g -> g.joinNation("Союз"));
        reject(store, MARIA, TownyGovernance::leaveNation);
        edit(store, MARIA, g -> g.diplomacy("allyAdd", "Рада"));
        edit(store, OSTAP, g -> g.diplomacy("allyAccept", "Союз"));
        check(store.snapshot().nation("Союз").get("allies").equals("Рада"), "alliance persisted on both sides");
        edit(store, MARIA, g -> g.diplomacy("enemyAdd", "Рада"));
        check(store.snapshot().nation("Рада").get("allies").isEmpty(), "hostility removes reciprocal alliance");
        edit(store, MARIA, g -> g.transferCapital("Вежа"));
        edit(store, MARIA, TownyGovernance::leaveNation);
        var reopened = new TownyStore(world);
        check(reopened.snapshot().nation("Союз").get("capital").equals("Вежа"), "capital after restart");
        check(reopened.snapshot().claim(OVERWORLD, -7985, -8017).data().get("resident").equals(MARKO.name()), "plot after restart");
        check(balance(reopened, townAccount).compareTo(new BigDecimal("32.50")) == 0, "bank after restart");
        edit(reopened, MARKO, g -> g.unclaimPlot(PLOT));
        edit(reopened, MARIA, g -> g.unclaimTown(PLOT));
        edit(reopened, MARIA, TownyGovernance::deleteTown);
        check(reopened.snapshot().town("Тестове_місто") == null && reopened.snapshot().townOf(MARKO.id()).isEmpty(), "town cleanup");
        check(balance(reopened, "resident:" + MARIA.id()).compareTo(new BigDecimal("107.50")) == 0, "deleted bank settles once");
        System.out.println("PASS Towny governance, balances, rollback, diplomacy, plots and restart");
    }

    private static void territory(TownySnapshot snapshot) {
        check(snapshot.towns().size() == 3 && snapshot.nations().size() == 3, "town/nation count");
        int x = -2433, z = -2001;
        var claim = snapshot.claim(OVERWORLD, x, z);
        check(claim != null && claim.x() == -153 && claim.z() == -126, "negative claim positions");
        check(snapshot.claim("minecraft:world_europe", x, z) == null, "correct Forge dimension");
        check(snapshot.claim(OVERWORLD, 61 * 16, 42 * 16) == null, "deleted plot");
        check(snapshot.sameClaim(OVERWORLD, x, z, x - 16, z), "adjacent town plots");
        check(!snapshot.sameClaim(OVERWORLD, x, z, x + 16, z), "piston boundary");
        check(!snapshot.allows(OVERWORLD, x, z, NINA.id(), TownySnapshot.Action.DESTROY, "STONE"), "outsider protection");
        check(!snapshot.allows(OVERWORLD, 0, 0, NINA.id(), TownySnapshot.Action.BUILD, "STONE"), "wilderness setting");
        check(snapshot.allows(OVERWORLD, 0, 0, NINA.id(), TownySnapshot.Action.BUILD, "LADDER"), "wilderness exception");
        check(!snapshot.environmental(OVERWORLD, x, z, "explosions", "forceexplosions", "explosion"), "explosions");
        check(snapshot.restrictedSwitch("SPRUCE_DOOR") && !snapshot.restrictedSwitch("STONE"), "switch rules");
        check(snapshot.restrictedItem("OAK_BOAT") && !snapshot.restrictedItem("BREAD"), "item rules");
        var town = snapshot.town(claim.town());
        var mayor = snapshot.editableCopy().residents().get(town.get("mayor"));
        check(snapshot.allows(OVERWORLD, x, z, UUID.fromString(mayor.get("uuid")), TownySnapshot.Action.BUILD, "STONE"),
                "mayor permission by UUID");
        System.out.println("PASS imported Towny protections and mapped dimensions");
    }

    private static BigDecimal balance(TownyStore store, String account) {
        return new TownyEconomy(store.snapshot().editableCopy()).balance(account);
    }

    private static String edit(TownyStore store, Actor actor, Function<TownyGovernance, String> action) throws Exception {
        return store.change(data -> action.apply(new TownyGovernance(data, actor)));
    }

    private static void reject(TownyStore store, Actor actor, Function<TownyGovernance, String> action) throws Exception {
        TownySnapshot before = store.snapshot();
        try {
            edit(store, actor, action);
            throw new AssertionError("Expected Towny rejection");
        } catch (TownyProblem expected) {
            check(before == store.snapshot(), "rejected operation never replaces committed state");
        }
    }
}
