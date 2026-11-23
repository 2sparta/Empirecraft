package ua.empirecraft.feudal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ua.empirecraft.feudal.CityMarketService.Box;
import ua.empirecraft.feudal.CityMarketService.Department;
import ua.empirecraft.feudal.CityMarketService.Place;
import ua.empirecraft.feudal.FeudalService.Actor;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

public final class FeudalCoreTest {
    private static final String A = "t:" + UUID.randomUUID();
    private static final String B = "t:" + UUID.randomUUID();
    private static final Actor MAYOR_A = new Actor(UUID.randomUUID().toString(), false);
    private static final Actor MAYOR_B = new Actor(UUID.randomUUID().toString(), false);
    private static final Actor ADMIN = new Actor("administrator", true);
    private static final String WORLD = UUID.randomUUID().toString();
    private static final long DAY = FeudalService.DAY;

    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        var clock = new AdjustableClock();
        try (var db = new Database("jdbc:h2:mem:feudal;DATABASE_TO_LOWER=TRUE")) {
            var service = new FeudalService(db, clock, 7 * DAY, DAY, 20, 12, 2);
            service.debtsEnabled = false;
            service.sync(new Snapshot(List.of(
                    new Estate(A, Kind.BARONY, "North", null, MAYOR_A.id(), true),
                    new Estate(B, Kind.BARONY, "South", null, MAYOR_B.id(), true)), Set.of()));
            var politics = new PoliticsService(service, new PoliticsRules(k -> -1));
            var commerce = new CommerceService(service);
            var market = new CityMarketService(service, commerce);
            var contracts = new ContractService(service, politics);
            service.contractBuilder = contracts;
            db.execute("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES('GRO','Groschen','G',?,10000,'GOLD')", B);
            service.setBalance("p:" + MAYOR_A.id(), "GRO", 1000);
            service.transfer(MAYOR_A, "p:" + MAYOR_A.id(), "e:" + A, "GRO", 300);
            check(service.balance("e:" + A, "GRO") == 300, "treasury deposit");
            reject(() -> service.transfer(MAYOR_B, "e:" + A, "p:" + MAYOR_B.id(), "GRO", 100));

            long id = contracts.create(MAYOR_A, A, B);
            int revision = contracts.edit(MAYOR_A, id, 1, "code", "GRO");
            revision = contracts.edit(MAYOR_A, id, revision, "amount", "2.00");
            revision = contracts.edit(MAYOR_A, id, revision, "resources", "minecraft:bread=2");
            contracts.agree(MAYOR_A, id, revision);
            contracts.agree(MAYOR_B, id, revision);
            sign(market, MAYOR_A, id, revision);
            sign(market, MAYOR_B, id, revision);
            check("ACTIVE".equals(string(contracts.get(id), "status")), "both reviewed signatures activate");
            var item = new CommerceService.InventoryChange(MAYOR_A.id(), "before", "after");
            String input = contracts.stock(MAYOR_A, A, "minecraft:bread", 2, commerce, item);
            commerce.finish(input, true);
            clock.now += 7 * DAY;
            contracts.tick();
            check(service.balance("e:" + A, "GRO") == 100 && service.balance("e:" + B, "GRO") == 200, "funded tribute");
            check(number(db.one("SELECT quantity FROM vc_goods WHERE estate=? AND material='minecraft:bread'", B), "quantity") == 2,
                    "funded goods obligation");
            clock.now += 7 * DAY;
            contracts.tick();
            check(service.balance("e:" + A, "GRO") == 100 && contracts.debts(id).isEmpty()
                    && db.one("SELECT id FROM vc_obligation LIMIT 1") == null, "unfunded dues expire without debt");

            market.land = (town, world, x, z) -> A.equals(town) && WORLD.equals(world) && x == 0 && z == 0;
            var place = new Place(WORLD, 4.5, 66, 4.5);
            String bazaar = market.create(MAYOR_A, request(), A, WORLD, "Bazaar", "");
            market.claim(ADMIN, request(), bazaar, 0, 0, true, "approved");
            market.building(ADMIN, request(), bazaar, Department.GOODS, new Box(0, 64, 0, 8, 70, 8), 4, 66, 4, "minecraft:oak_planks", "counter");
            market.reviewDepartment(ADMIN, request(), bazaar, Department.GOODS, true, "inspection");
            market.submit(MAYOR_A, request(), bazaar);
            market.state(ADMIN, request(), bazaar, "OPEN", "approved");
            market.permit(MAYOR_A, request(), bazaar, "GRO", "payment", true);
            String spec = market.registerSpec("forge1:item", false);
            service.setBalance("p:" + MAYOR_B.id(), "GRO", 200);
            String sell = market.order(MAYOR_A, request(), bazaar, place, Department.GOODS, spec, "", "GRO", "SELL", 2, 50, 1, 1, item);
            commerce.finish(sell, true);
            market.order(MAYOR_B, request(), bazaar, place, Department.GOODS, spec, "", "GRO", "BUY", 2, 50, 1, 1, null);
            var asset = db.one("SELECT id FROM cm_assets WHERE owner=? AND spec=? AND quantity=2", "p:" + MAYOR_B.id(), spec);
            check(asset != null, "paid market order delivered to buyer escrow");
            check(!market.open(bazaar, Department.CREDIT), "credit disabled");
            market.land = (town, world, x, z) -> false;
            market.tick();
            check("SUSPENDED".equals(string(market.market(bazaar), "state")), "loss of claim suspends market");

            var stock = new ProvisionService(service, commerce);
            var calendar = new ProvisionClock(clock.now, 0, true, clock.now);
            check(stock.settle(A, 1, clock.now, calendar), "reserve initialized");
            String deposit = stock.transfer(MAYOR_A, A, 0, 0, "forge1:bread", 16, 5, 64, item);
            commerce.finish(deposit, true);
            check(stock.canSupport(A, 2, clock.now, calendar), "funded expansion");
            var army = new ArmyService(service, stock, politics);
            String unit = UUID.randomUUID().toString();
            army.observe(unit, MAYOR_A.id(), A, 20);
            check(army.feed(unit, 1, 10) == 10, "recruit uses city provisions");
            check(army.feed(unit, 1, 10) == 10, "receipt replay safe");

            var hull = new FleetService.Hull(7L, WORLD, List.of(),
                    0, 64, 0, A, "Test vessel", "", true);
            var fleet = new FleetService(market, new FleetService.World() {
                public FleetService.Hull hull(long key) { return key == hull.id() ? hull : null; }
                public String hometown(String player) { return MAYOR_A.id().equals(player) ? A : B; }
                public boolean person(String id) { return true; }
                public boolean allied(String first, String second) { return first.equals(second); }
                public boolean war(String first, String second) { return false; }
                public boolean republic(String town) { return false; }
                public Set<String> territories(FleetService.Hull ship) { return Set.of(A); }
                public boolean docked(FleetService.Hull ship, String town, Place counter) { return false; }
            });
            fleet.register(hull, A, MAYOR_A.id());
            check(!fleet.policy(7L, MAYOR_B.id()).allow(), "another town cannot edit ship in claim");
            fleet.edit(MAYOR_A, request(), 7L, "crew-add", MAYOR_B.id());
            check(fleet.policy(7L, MAYOR_B.id()).allow(), "authorized crew");
            System.out.println("PASS Feudal reviewed signatures, prepaid dues, treasury, market, recruit provisions, and fleet permissions");
        }
    }

    private static void sign(CityMarketService market, Actor actor, long id, int revision) throws Exception {
        var document = market.documents.vassalBuilder(id);
        var first = market.documents.view(actor, document, 1);
        String token = first.token();
        for (int i = 2; i <= first.pages(); i++) token = market.documents.view(actor, document, i).token();
        market.documents.acceptBuilder(actor, id, revision, token);
    }

    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("unauthorized transfer allowed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String request() { return UUID.randomUUID().toString(); }

    private static final class AdjustableClock extends Clock {
        long now = 1_800_000_000_000L;
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
