package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ua.empirecraft.townyforge.TownySnapshot.Claim;
import ua.empirecraft.townyforge.TownySnapshot.Invitation;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyGovernance {
    private static final Set<String> PLOT_TYPES = Set.of("default", "shop", "arena", "embassy", "wilds", "inn", "jail", "farm", "bank");
    record Actor(UUID id, String name) {}
    record Here(String dimension, double x, double y, double z, float yaw, float pitch) {
        int cx() { return (int) Math.floor(x) >> 4; }
        int cz() { return (int) Math.floor(z) >> 4; }
        String positions(String world) { return world + "," + x + "," + y + "," + z + "," + pitch + "," + yaw; }
    }

    private final Source source;
    private final TownyEconomy bank;
    private final String name;
    private final Map<String, String> resident;

    TownyGovernance(Source source, Actor actor) {
        this.source = source;
        bank = new TownyEconomy(source);
        name = identify(actor);
        resident = source.residents().computeIfAbsent(name, ignored -> new HashMap<>(Map.of(
                "uuid", actor.id.toString(), "registered", now(), "lastOnline", now())));
    }

    private String identify(Actor actor) {
        for (var entry : source.residents().entrySet()) {
            if (actor.id.toString().equalsIgnoreCase(entry.getValue().get("uuid"))) return entry.getKey();
        }
        if (find(source.residents(), actor.name) != null) throw new TownyProblem("Це ім’я вже прив’язане до іншого UUID.");
        return actor.name;
    }

    static String find(Map<String, ?> map, String query) {
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(query)) return key;
        }
        return null;
    }

    private static String lookup(Map<String, ?> map, String query, String error) {
        String name = find(map, query);
        if (name == null) throw new TownyProblem(error);
        return name;
    }

    private static String now() {
        return Long.toString(Instant.now().toEpochMilli());
    }

    private String setting(String key, String fallback) {
        return source.settings().getOrDefault(key, fallback);
    }

    private int number(String key) {
        return Integer.parseInt(setting(key, "0"));
    }

    private boolean enabled(String key, boolean fallback) {
        return Boolean.parseBoolean(setting(key, Boolean.toString(fallback)));
    }

    private String townName() {
        return resident.getOrDefault("town", "");
    }

    private Map<String, String> town() {
        var town = source.towns().get(townName());
        if (town == null) throw new TownyProblem("Спочатку вступіть до міста.");
        return town;
    }

    private String nationName() {
        return town().getOrDefault("nation", "");
    }

    private Map<String, String> nation() {
        var nation = source.nations().get(nationName());
        if (nation == null) throw new TownyProblem("Ваше місто не входить до нації.");
        return nation;
    }

    private boolean mayor() {
        return name.equals(town().get("mayor"));
    }

    private boolean king() {
        return mayor() && townName().equals(nation().get("capital"));
    }

    private void requireTown(String node) {
        town();
        if (!mayor() && !hasRankPermission("towns", "town-ranks", node)) {
            throw new TownyProblem("Ваш ранг у місті не дозволяє цю дію.");
        }
    }

    private void requireNation(String node) {
        nation();
        if (!king() && !hasRankPermission("nations", "nation-ranks", node)) {
            throw new TownyProblem("Ваш ранг у нації не дозволяє цю дію.");
        }
    }

    private boolean hasRankPermission(String scope, String field, String node) {
        for (String rank : entries(resident.get(field))) {
            for (String value : source.permissions().getOrDefault(scope + ".ranks." + rank, List.of())) {
                if (value.startsWith("-") && matches(value.substring(1), node)) return false;
            }
        }
        for (String rank : entries(resident.get(field))) {
            for (String value : source.permissions().getOrDefault(scope + ".ranks." + rank, List.of())) {
                if (matches(value, node)) return true;
            }
        }
        return false;
    }

    private static boolean matches(String permission, String node) {
        return permission.equals(node) || permission.endsWith(".*")
                && node.startsWith(permission.substring(0, permission.length() - 1));
    }

    private static Set<String> entries(String csv) {
        var values = new LinkedHashSet<String>();
        if (csv != null) {
            for (String value : csv.split(",")) {
                if (!value.isBlank()) values.add(value.strip());
            }
        }
        return values;
    }

    private String organizationName(String raw, Map<String, ?> used) {
        String value = Normalizer.normalize(raw.strip().replace(' ', '_'), Normalizer.Form.NFC);
        if (!value.matches("[\\p{L}0-9_\\-]{1,32}") || value.matches("[0-9_-]+")) {
            throw new TownyProblem("Вкажіть назву з букв, цифр, дефіса або підкреслення.");
        }
        if (find(used, value) != null) throw new TownyProblem("Ця назва вже зайнята.");
        return value;
    }

    private String world(Here here, boolean forClaim) {
        for (var entry : source.dimensions().entrySet()) {
            if (entry.getValue().equals(here.dimension)) {
                var rules = source.worlds().get(entry.getKey());
                if (TownySnapshot.flag(rules, "usingTowny") && (!forClaim || TownySnapshot.flag(rules, "claimable"))) {
                    return entry.getKey();
                }
            }
        }
        throw new TownyProblem("У цьому вимірі Towny не дозволяє дію.");
    }

    private Claim claim(Here here) {
        String world = world(here, false);
        return source.claims().stream().filter(c -> c.world().equals(world) && c.x() == here.cx() && c.z() == here.cz())
                .findFirst().orElse(null);
    }

    private void addClaim(Here here, String world, String owner, boolean outpost) {
        var data = new HashMap<>(Map.of("town", owner, "type", "Default", "outpost", Boolean.toString(outpost),
                "changed", "false", "taxed", "true", "claimedAt", now(), "price", "-1", "customPermissionData", "{}"));
        if (outpost) data.put("spawn", here.positions(world));
        source.claims().add(new Claim(world, here.cx(), here.cz(), data));
    }

    String foundTown(String raw, Here here) {
        if (!townName().isEmpty()) throw new TownyProblem("Ви вже в місті.");
        String world = world(here, true);
        String town = organizationName(raw, source.towns());
        if (claim(here) != null) throw new TownyProblem("Цей чанк уже зайнятий.");
        checkDistance(here, world, "");
        bank.fee(account("resident", resident), bank.configured("new_expand.price_new_town"));
        var city = new HashMap<String, String>();
        city.put("uuid", UUID.randomUUID().toString());
        city.put("name", town);
        city.put("mayor", name);
        city.put("founder", name);
        city.put("registered", now());
        city.put("nation", "");
        city.put("bonusBlocks", setting("claiming.new_town_bonus_claims", "0"));
        city.put("homeBlock", world + "," + here.cx() + "," + here.cz());
        city.put("spawn", here.positions(world));
        city.put("protectionStatus", "residentBuild,residentDestroy,residentSwitch,residentItemUse");
        city.put("open", setting("town.default_open", "false"));
        city.put("public", setting("town.default_public", "false"));
        city.put("taxes", setting("town.default_taxes.tax", "0"));
        city.put("taxpercent", setting("town.default_taxes.taxpercentage", "false"));
        city.put("plotTax", setting("town.default_taxes.plot_tax", "0"));
        city.put("commercialPlotTax", setting("town.default_taxes.shop_tax", "0"));
        city.put("embassyPlotTax", setting("town.default_taxes.embassy_tax", "0"));
        city.put("hasUpkeep", "true");
        source.towns().put(town, city);
        assignTown(resident, town);
        addClaim(here, world, town, false);
        return "Місто " + town + " створено.";
    }

    private void checkDistance(Here here, String world, String owner) {
        int min = number("claiming.distance_rules.min_plot_distance_from_town_plot");
        if (owner.isEmpty()) {
            int founding = Integer.parseInt(setting("claiming.distance_rules.new_town_min_distance_from_town_plot", "-1"));
            if (founding >= 0) min = founding;
        }
        if (min <= 0) return;
        for (Claim other : source.claims()) {
            if (other.world().equals(world) && !other.town().equals(owner)
                    && Math.max(Math.abs((long) other.x() - here.cx()), Math.abs((long) other.z() - here.cz())) < min) {
                throw new TownyProblem("Чанк надто близько до іншого міста.");
            }
        }
    }

    String claimTown(Here here) {
        return claimTown(here, false);
    }

    String claimTown(Here here, boolean outpost) {
        requireTown(outpost ? "towny.command.town.claim.outpost" : "towny.command.town.claim.town");
        if (TownySnapshot.flag(town(), "bankrupt")) throw new TownyProblem("Місто має прострочене утримання.");
        String world = world(here, true);
        if (claim(here) != null) throw new TownyProblem("Цей чанк уже зайнятий.");
        List<Claim> existing = source.claims().stream().filter(c -> townName().equals(c.town())).toList();
        boolean adjacent = existing.stream().anyMatch(c -> c.world().equals(world)
                && Math.abs((long) c.x() - here.cx()) + Math.abs((long) c.z() - here.cz()) == 1);
        long members = memberCount(townName());
        if (outpost) checkOutpost(here, world, existing, members);
        else if (!adjacent) throw new TownyProblem("Виберіть суміжний чанк вашого міста.");
        checkDistance(here, world, townName());
        if (!TownySnapshot.flag(town(), "hasUnlimitedClaims")) {
            long limit = members * number("claiming.town_block_ratio") + storedNumber(town(), "bonusBlocks")
                    + storedNumber(town(), "purchasedBlocks");
            int maximum = number("claiming.town_block_limit");
            if (maximum > 0) limit = Math.min(limit, maximum);
            if (limit > 0 && existing.size() >= limit) throw new TownyProblem("Місто досягло ліміту приватів.");
        }
        BigDecimal fee = bank.configured(outpost ? "new_expand.price_outpost" : "new_expand.price_claim_townblock");
        if (fee.signum() > 0 && !outpost) {
            BigDecimal increment = bank.configured("new_expand.price_claim_townblock_increase");
            fee = fee.multiply(increment.signum() == 0 ? BigDecimal.ONE : increment.pow(existing.size(), java.math.MathContext.DECIMAL64));
            BigDecimal cap = bank.configured("new_expand.max_price_claim_townblock");
            if (cap.signum() >= 0) fee = fee.min(cap);
        }
        bank.fee(account("town", town()), fee);
        addClaim(here, world, townName(), outpost);
        return outpost ? "Аванпост створено." : "Чанк приєднано до " + townName() + ".";
    }

    private long memberCount(String city) {
        return source.residents().values().stream().filter(r -> city.equals(r.get("town"))).count();
    }

    private void checkOutpost(Here here, String world, List<Claim> existing, long members) {
        if (!enabled("claiming.outposts.allow_outposts", false)) throw new TownyProblem("Аванпости вимкнено.");
        if (members < number("claiming.outposts.minimum_amount_of_residents_in_town_for_outpost")) {
            throw new TownyProblem("У місті замало жителів для аванпосту.");
        }
        if (enabled("claiming.outposts.limit_outposts_using_town_and_nation_levels", true)) {
            int limit = levelValue("town", members, "townOutpostLimit");
            String nation = nationName();
            if (!nation.isEmpty()) {
                long population = source.towns().entrySet().stream()
                        .filter(e -> nation.equals(e.getValue().get("nation")))
                        .mapToLong(e -> memberCount(e.getKey())).sum();
                limit += levelValue("nation", population, "nationBonusOutpostLimit");
            }
            long used = existing.stream().filter(c -> TownySnapshot.flag(c.data(), "outpost")).count();
            if (used >= limit) throw new TownyProblem("Місто досягло ліміту аванпостів.");
        }
        long minDistance = source.claims().stream().filter(c -> c.world().equals(world))
                .mapToLong(c -> distance(c, here)).min().orElse(Long.MAX_VALUE);
        if (minDistance <= 1 || minDistance < number("claiming.distance_rules.min_distance_for_outpost_from_plot")) {
            throw new TownyProblem("Аванпост надто близько до наявної ділянки.");
        }
        int maxDistance = number("claiming.distance_rules.max_distance_for_outpost_from_town_plot");
        if (maxDistance > 0 && existing.stream().filter(c -> c.world().equals(world))
                .noneMatch(c -> distance(c, here) <= maxDistance)) {
            throw new TownyProblem("Аванпост надто далеко від міста.");
        }
    }

    private static long distance(Claim claim, Here here) {
        return Math.max(Math.abs((long) claim.x() - here.cx()), Math.abs((long) claim.z() - here.cz()));
    }

    private int levelValue(String type, long members, String field) {
        int result = 0;
        for (int index = 0; ; index++) {
            String prefix = "levels." + type + "." + index + ".";
            String minimum = source.settings().get(prefix + "numResidents");
            if (minimum == null || members < Integer.parseInt(minimum)) return result;
            result = number(prefix + field);
        }
    }

    private static int storedNumber(Map<String, String> record, String field) {
        return Integer.parseInt(record.getOrDefault(field, "0"));
    }

    String unclaimTown(Here here) {
        requireTown("towny.command.town.unclaim");
        Claim claim = townPlot(here);
        if ((claim.world() + "," + claim.x() + "," + claim.z()).equals(town().get("homeBlock"))) {
            throw new TownyProblem("Столичний чанк міста не можна звільнити.");
        }
        if (!claim.data().getOrDefault("resident", "").isEmpty()) {
            throw new TownyProblem("Спочатку звільніть ділянку її власника.");
        }
        if (!bank.enabled() || bank.configured("new_expand.price_claim_townblock_refund").signum() == 0) {
            if (claim.type().equals("jail")) TownyJail.removeCell(source, claim);
            source.claims().remove(claim);
            return "Чанк звільнено.";
        }
        throw new TownyProblem("У цій конфігурації звільнення чанку потребує повернення коштів.");
    }

    String inviteResident(String query) {
        requireTown("towny.command.town.invite.add");
        if (TownySnapshot.flag(town(), "bankrupt")) throw new TownyProblem("Місто має прострочене утримання.");
        String target = lookup(source.residents(), query, "Гравця ще немає в реєстрі Towny.");
        if (!source.residents().get(target).getOrDefault("town", "").isEmpty()) {
            throw new TownyProblem("Гравець уже належить до міста.");
        }
        invite("town", townName(), target);
        return "Запрошення до міста надіслано.";
    }

    String joinTown(String query, boolean invitationRequired) {
        if (!townName().isEmpty()) throw new TownyProblem("Ви вже в місті.");
        String target = lookup(source.towns(), query, "Місто не знайдено.");
        var invitation = new Invitation("town", target, name);
        if (!source.invitations().contains(invitation)
                && (invitationRequired || !TownySnapshot.flag(source.towns().get(target), "open"))) {
            throw new TownyProblem("Потрібне запрошення до міста.");
        }
        source.invitations().removeIf(entry -> entry.kind().equals("town") && entry.recipient().equals(name));
        assignTown(resident, target);
        return "Ви вступили до " + target + ".";
    }

    String leaveTown() {
        if (enabled("jail.jail_denies_town_leave", false) && TownyJail.confined(resident)) {
            throw new TownyProblem("Ви не можете вийти з міста під час ув’язнення.");
        }
        if (mayor()) throw new TownyProblem("Спочатку призначте іншого мера.");
        removeResident(name, resident);
        return "Ви залишили місто.";
    }

    String kickResident(String query) {
        requireTown("towny.command.town.kick");
        String target = member(query);
        if (target.equals(town().get("mayor"))) throw new TownyProblem("Мера не можна виключити.");
        removeResident(target, source.residents().get(target));
        return "Гравця виключено з міста.";
    }

    String transferMayor(String query) {
        if (!mayor()) throw new TownyProblem("Лише мер може передати посаду.");
        town().put("mayor", member(query));
        return "Мера змінено.";
    }

    private String member(String query) {
        String target = lookup(source.residents(), query, "Гравця не знайдено.");
        if (!townName().equals(source.residents().get(target).get("town"))) {
            throw new TownyProblem("Гравець не належить до вашого міста.");
        }
        return target;
    }

    private void removeResident(String target, Map<String, String> record) {
        removeResident(source, target, record);
    }

    static void removeResident(Source source, String target, Map<String, String> record) {
        String oldTown = record.get("town");
        for (Claim plot : source.claims()) {
            if (plot.town().equals(oldTown) && target.equals(plot.data().get("resident"))) resetPlot(plot);
        }
        assignTown(record, "");
    }

    private static void assignTown(Map<String, String> record, String name) {
        record.put("town", name);
        record.put("town-ranks", "");
        record.put("nation-ranks", "");
        record.put("joinedTownAt", name.isEmpty() ? "0" : now());
    }

    String setTownSpawn(Here here) {
        requireTown("towny.command.town.set.spawn");
        Claim plot = townPlot(here);
        if (!(plot.world() + "," + plot.x() + "," + plot.z()).equals(town().get("homeBlock"))) {
            throw new TownyProblem("Спавн міста має бути в його домашньому чанку.");
        }
        town().put("spawn", here.positions(plot.world()));
        return "Спавн міста збережено.";
    }

    String toggle(String scope, String flag) {
        Map<String, String> owner;
        if (scope.equals("town")) {
            requireTown("towny.command.town.toggle." + flag);
            owner = town();
        } else {
            requireNation("towny.command.nation.toggle." + flag);
            owner = nation();
        }
        if (flag.equals("open") || flag.equals("public")) {
            String key = scope.equals("town") ? flag : flag.equals("open") ? "isOpen" : "isPublic";
            boolean next = !TownySnapshot.flag(owner, key);
            owner.put(key, Boolean.toString(next));
            return (next ? "Увімкнено " : "Вимкнено ") + flag + ".";
        }
        if (flag.equals("taxpercent")) {
            boolean next = !TownySnapshot.flag(owner, "taxpercent");
            owner.put("taxpercent", Boolean.toString(next));
            setTax(scope, "taxes", owner.getOrDefault("taxes", "0"));
            return next ? "Податок тепер відсотковий." : "Податок тепер фіксований.";
        }
        if (scope.equals("town") && Set.of("pvp", "fire", "explosion", "mobs").contains(flag)) {
            return flipPermission(owner, "protectionStatus", flag);
        }
        throw new TownyProblem("Невідомий параметр.");
    }

    String rank(String scope, String targetName, String rank, boolean add) {
        Map<String, String> record;
        if (scope.equals("town")) {
            requireTown("towny.command.town.rank." + rank);
            record = source.residents().get(member(targetName));
        } else {
            requireNation("towny.command.nation.rank." + rank);
            String target = lookup(source.residents(), targetName, "Гравця не знайдено.");
            record = source.residents().get(target);
            var targetTown = source.towns().get(record.get("town"));
            if (targetTown == null || !nationName().equals(targetTown.get("nation"))) {
                throw new TownyProblem("Гравець не належить до вашої нації.");
            }
        }
        String key = (scope.equals("town") ? "towns" : "nations") + ".ranks." + rank;
        if (!source.permissions().containsKey(key)) throw new TownyProblem("Цей ранг не налаштовано.");
        if (source.permissions().get(key).stream().anyMatch(p -> p.contains("_level_requirement."))) {
            throw new TownyProblem("Цей ранг має вимогу до рівня міста або нації.");
        }
        String field = scope + "-ranks";
        var ranks = entries(record.get(field));
        if (add) ranks.add(rank);
        else ranks.remove(rank);
        record.put(field, String.join(",", ranks));
        return "Ранги оновлено.";
    }

    String foundNation(String raw) {
        if (!mayor()) throw new TownyProblem("Заснувати націю може мер.");
        if (!nationName().isEmpty()) throw new TownyProblem("Ваше місто вже в нації.");
        String name = organizationName(raw, source.nations());
        bank.fee(account("town", town()), bank.configured("new_expand.price_new_nation"));
        var nation = new HashMap<>(Map.of("uuid", UUID.randomUUID().toString(), "capital", townName(),
                "registered", now(), "isOpen", setting("nation.default_open", "false"),
                "isPublic", setting("nation.default_public", "false"), "allies", "", "enemies", ""));
        nation.put("taxes", setting("nation.default_taxes.tax", "0"));
        nation.put("taxpercent", setting("nation.default_taxes.taxpercentage", "false"));
        source.nations().put(name, nation);
        assignNation(townName(), name);
        return "Націю " + name + " створено.";
    }

    String inviteTown(String query) {
        requireNation("towny.command.nation.invite.add");
        String target = lookup(source.towns(), query, "Місто не знайдено.");
        if (!source.towns().get(target).getOrDefault("nation", "").isEmpty()) {
            throw new TownyProblem("Місто вже належить до нації.");
        }
        invite("nation", nationName(), target);
        return "Запрошення для міста надіслано.";
    }

    private void invite(String type, String owner, String recipient) {
        var invitation = new Invitation(type, owner, recipient);
        if (source.invitations().contains(invitation)) throw new TownyProblem("Запрошення вже надіслано.");
        source.invitations().add(invitation);
    }

    String inbox() {
        var received = source.invitations().stream()
                .filter(i -> switch (i.kind()) {
                    case "town" -> i.recipient().equals(name);
                    case "nation" -> !townName().isEmpty() && mayor() && i.recipient().equals(townName());
                    case "ally" -> !townName().isEmpty() && !nationName().isEmpty() && king() && i.recipient().equals(nationName());
                    default -> false;
                })
                .map(i -> i.kind() + " " + i.owner()).toList();
        return received.isEmpty() ? "Нових запрошень немає." : String.join(", ", received);
    }

    String joinNation(String query) {
        if (!mayor()) throw new TownyProblem("Рішення про вступ приймає мер.");
        if (!nationName().isEmpty()) throw new TownyProblem("Ваше місто вже в нації.");
        String target = lookup(source.nations(), query, "Націю не знайдено.");
        if (!source.invitations().contains(new Invitation("nation", target, townName()))
                && !TownySnapshot.flag(source.nations().get(target), "isOpen")) {
            throw new TownyProblem("Потрібне запрошення до нації.");
        }
        source.invitations().removeIf(entry -> entry.kind().equals("nation") && entry.recipient().equals(townName()));
        assignNation(townName(), target);
        return "Ваше місто вступило до " + target + ".";
    }

    String leaveNation() {
        if (!mayor()) throw new TownyProblem("Рішення про вихід приймає мер.");
        String current = nationName();
        nation();
        if (king()) {
            boolean members = source.towns().entrySet().stream()
                    .anyMatch(e -> !e.getKey().equals(townName()) && current.equals(e.getValue().get("nation")));
            if (members) throw new TownyProblem("Спочатку передайте статус столиці іншому місту.");
            throw new TownyProblem("Скористайтеся /nation delete confirm для нації з одним містом.");
        }
        assignNation(townName(), "");
        return "Ваше місто залишило націю.";
    }

    String kickTown(String query) {
        requireNation("towny.command.nation.kick");
        String target = nationalTown(query);
        if (target.equals(nation().get("capital"))) throw new TownyProblem("Столицю не можна виключити.");
        assignNation(target, "");
        return "Місто виключено з нації.";
    }

    String transferCapital(String query) {
        if (!king()) throw new TownyProblem("Столицю обирає лідер нації.");
        nation().put("capital", nationalTown(query));
        return "Столицю змінено.";
    }

    private String nationalTown(String query) {
        String target = lookup(source.towns(), query, "Місто не знайдено.");
        if (!nationName().equals(source.towns().get(target).get("nation"))) {
            throw new TownyProblem("Місто не належить до вашої нації.");
        }
        return target;
    }

    private void assignNation(String city, String nation) {
        assignNation(source, city, nation);
    }

    static void assignNation(Source source, String city, String nation) {
        var record = source.towns().get(city);
        record.put("nation", nation);
        record.put("joinedNationAt", nation.isEmpty() ? "0" : now());
        for (var person : source.residents().values()) {
            if (city.equals(person.get("town"))) person.put("nation-ranks", "");
        }
    }

    String diplomacy(String operation, String query) {
        String node = operation.startsWith("ally") ? "ally." + operation.substring(4).toLowerCase(Locale.ROOT) : "enemy";
        requireNation("towny.command.nation." + node);
        String current = nationName();
        String target = lookup(source.nations(), query, "Націю не знайдено.");
        if (current.equals(target)) throw new TownyProblem("Вкажіть іншу націю.");
        Map<String, String> other = source.nations().get(target);
        switch (operation) {
            case "allyAdd" -> {
                if (relation(other, "enemies", current) || relation(nation(), "enemies", target)
                        || relation(nation(), "allies", target)) throw new TownyProblem("Союз зараз неможливий.");
                invite("ally", current, target);
                return "Запит на союз надіслано.";
            }
            case "allyAccept" -> {
                if (!source.invitations().remove(new Invitation("ally", target, current))) {
                    throw new TownyProblem("Запиту на союз немає.");
                }
                if (relation(other, "enemies", current) || relation(nation(), "enemies", target)) {
                    throw new TownyProblem("Спочатку припиніть ворожнечу.");
                }
                setRelation(nation(), "allies", target, true);
                setRelation(other, "allies", current, true);
            }
            case "allyRemove" -> {
                setRelation(nation(), "allies", target, false);
                setRelation(other, "allies", current, false);
            }
            case "enemyAdd" -> {
                setRelation(nation(), "allies", target, false);
                setRelation(other, "allies", current, false);
                setRelation(nation(), "enemies", target, true);
                source.invitations().removeIf(i -> i.kind().equals("ally")
                        && (i.owner().equals(current) && i.recipient().equals(target)
                        || i.owner().equals(target) && i.recipient().equals(current)));
            }
            case "enemyRemove" -> setRelation(nation(), "enemies", target, false);
            default -> throw new IllegalArgumentException(operation);
        }
        return "Дипломатичні відносини оновлено.";
    }

    private static boolean relation(Map<String, String> nation, String field, String name) {
        return TownySnapshot.listed(nation.get(field), name);
    }

    private static void setRelation(Map<String, String> record, String field, String name, boolean present) {
        var names = entries(record.get(field));
        names.removeIf(entry -> entry.equalsIgnoreCase(name));
        if (present) names.add(name);
        record.put(field, String.join(",", names));
    }

    String deleteTown() {
        if (!mayor()) throw new TownyProblem("Видалити місто може лише мер.");
        String current = townName();
        if (!nationName().isEmpty()) throw new TownyProblem("Спочатку виведіть місто з нації.");
        settle("town", town());
        removeTown(source, current);
        return "Місто видалено.";
    }

    static void removeTown(Source source, String current) {
        source.residents().values().stream().filter(r -> current.equals(r.get("forgeJailTown"))).forEach(TownyJail::release);
        source.towns().remove(current);
        source.claims().removeIf(claim -> claim.town().equals(current));
        source.residents().values().stream().filter(r -> current.equals(r.get("town"))).forEach(r -> assignTown(r, ""));
        source.invitations().removeIf(i -> i.kind().equals("town") && i.owner().equals(current)
                || i.kind().equals("nation") && i.recipient().equals(current));
        for (var town : source.towns().values()) setRelation(town, "trustedTowns", current, false);
    }

    String deleteNation() {
        if (!king()) throw new TownyProblem("Видалити націю може лише її лідер.");
        String current = nationName();
        settle("nation", nation());
        removeNation(source, current);
        return "Націю видалено.";
    }

    static void removeNation(Source source, String current) {
        source.nations().remove(current);
        for (var entry : source.towns().entrySet()) {
            if (current.equals(entry.getValue().get("nation"))) assignNation(source, entry.getKey(), "");
        }
        for (var record : source.nations().values()) {
            setRelation(record, "allies", current, false);
            setRelation(record, "enemies", current, false);
        }
        source.invitations().removeIf(i -> i.kind().equals("nation") && i.owner().equals(current)
                || i.kind().equals("ally") && (i.owner().equals(current) || i.recipient().equals(current)));
    }

    private void settle(String type, Map<String, String> record) {
        String from = account(type, record);
        BigDecimal amount = bank.balance(from);
        if (bank.enabled() && amount.signum() > 0 && enabled("economy.banks.is_deleted_town_and_nation_bank_balances_paid_to_owner", false)) {
            bank.transfer(from, account("resident", resident), amount);
        }
        source.balances().remove(from);
    }

    String balance(String scope) {
        var owner = scope.equals("town") ? town() : nation();
        if (!bank.enabled()) throw new TownyProblem("Економіку Towny вимкнено.");
        return "Баланс " + bank.balance(account(scope, owner)).stripTrailingZeros().toPlainString() + ".";
    }

    String setTax(String scope, String field, String text) {
        var owner = scope.equals("nation") ? nation() : town();
        if (scope.equals("nation")) requireNation("towny.command.nation.set.taxes");
        else requireTown("towny.command.town.set." + field);
        if (!text.matches("-?[0-9]{1,12}(?:\\.[0-9]{1,2})?")) throw new TownyProblem("Вкажіть податок числом.");
        BigDecimal amount = new BigDecimal(text);
        boolean percent = field.equals("taxes") && TownySnapshot.flag(owner, "taxpercent");
        String type = field.equals("taxes") ? scope : "plot";
        BigDecimal maximum = bank.configured("daily_taxes.max_" + type + (percent ? "_tax_percent" : "_tax_amount"));
        if (amount.signum() < 0 && (percent || !enabled("economy.daily_taxes.allow_negative_" + type + "_tax", false))
                || maximum.signum() > 0 && amount.abs().compareTo(maximum) > 0) {
            throw new TownyProblem("Сума поза дозволеними межами податку.");
        }
        owner.put(switch (field) {
            case "plottax" -> "plotTax";
            case "shoptax" -> "commercialPlotTax";
            case "embassytax" -> "embassyPlotTax";
            default -> "taxes";
        }, amount.toPlainString());
        return "Податок збережено.";
    }

    String bank(String scope, String value, boolean withdraw, Here here) {
        var owner = scope.equals("town") ? town() : nation();
        if (withdraw) {
            if (scope.equals("town")) requireTown("towny.command.town.withdraw");
            else requireNation("towny.command.nation.withdraw");
        }
        if (enabled("bank.is_banking_limited_to_bank_plots", false)) {
            String location = scope.equals("town") ? townName() : owner.get("capital");
            Claim at = claim(here);
            boolean bankPlot = at != null && at.town().equals(location) && at.type().equals("bank");
            boolean home = at != null && at.town().equals(location)
                    && (at.world() + "," + at.x() + "," + at.z()).equals(source.towns().get(location).get("homeBlock"))
                    && (!enabled("bank.do_homeblocks_not_work_when_a_town_has_bank_plots", false)
                    || source.claims().stream().noneMatch(p -> p.town().equals(location) && p.type().equals("bank")));
            if (!bankPlot && !home) throw new TownyProblem("Скористайтеся банківською ділянкою.");
        } else if (enabled("economy.banks.disallow_bank_actions_outside_town", false)) townPlot(here);
        bank.bank(scope, account("resident", resident), account(scope, owner), TownyEconomy.payment(value), withdraw);
        if (!withdraw && scope.equals("town") && bank.balance(account(scope, owner)).signum() >= 0) {
            owner.remove("bankrupt");
            owner.remove("forgeDebtDays");
        }
        return balance(scope);
    }

    private Claim townPlot(Here here) {
        Claim plot = claim(here);
        if (plot == null || !townName().equals(plot.town())) throw new TownyProblem("Ця ділянка не належить вашому місту.");
        return plot;
    }

    private Claim ownedPlot(Here here) {
        Claim plot = claim(here);
        if (plot == null) throw new TownyProblem("Тут немає ділянки Towny.");
        if (!name.equals(plot.data().get("resident"))) {
            townPlot(here);
            requireTown("towny.command.plot.set.perm");
        }
        return plot;
    }

    String setPlotType(Here here, String type) {
        if (!PLOT_TYPES.contains(type) && !source.settings().containsKey("plots." + type + ".cost")) {
            throw new TownyProblem("Невідомий тип ділянки.");
        }
        Claim plot = townPlot(here);
        requireTown("towny.command.plot.set." + type);
        if (plot.type().equals(type)) return "Ділянка вже має цей тип.";
        if (plot.type().equals("jail")) TownyJail.removeCell(source, plot);
        BigDecimal cost = new BigDecimal(setting("plots." + type + ".cost", "0"));
        bank.fee(account("resident", resident), cost);
        plot.data().put("type", type);
        var tenant = source.residents().get(plot.data().getOrDefault("resident", ""));
        if (!type.equals("embassy") && tenant != null && !plot.town().equals(tenant.get("town"))) resetPlot(plot);
        if (type.equals("jail")) {
            resetPlot(plot);
            plot.data().put("jailSpawn", here.positions(plot.world()));
        }
        return "Тип ділянки змінено на " + type + ".";
    }

    String sentence(String query, Here here, int hours) {
        requireTown("towny.command.town.toggle.jail");
        String target = lookup(source.residents(), query, "Гравця не знайдено.");
        var inmate = source.residents().get(target);
        if (target.equals(name) || target.equals(town().get("mayor"))
                || !townName().equals(inmate.get("town")) && !TownySnapshot.listed(town().get("outlaws"), target)) {
            throw new TownyProblem("Ви не маєте права ув’язнити цього гравця.");
        }
        TownyJail.sentence(source, inmate, townPlot(here), hours);
        return "Гравця ув’язнено.";
    }

    String releasePrisoner(String query) {
        requireTown("towny.command.town.toggle.jail");
        String target = lookup(source.residents(), query, "Гравця не знайдено.");
        var inmate = source.residents().get(target);
        if (!townName().equals(inmate.get("forgeJailTown"))) throw new TownyProblem("Гравця немає у в’язниці вашого міста.");
        TownyJail.release(inmate);
        return "Гравця звільнено.";
    }

    String bail() {
        TownyJail.bail(source, resident);
        return "Заставу сплачено.";
    }

    String sellPlot(Here here, String value) {
        Claim plot = ownedPlot(here);
        BigDecimal price = value.equals("0") ? BigDecimal.ZERO : TownyEconomy.payment(value);
        if (!bank.enabled() && price.signum() > 0) throw new TownyProblem("Економіку Towny вимкнено.");
        plot.data().put("price", price.toPlainString());
        return "Ділянку виставлено на продаж.";
    }

    String claimPlot(Here here) {
        Claim plot = claim(here);
        if (plot == null || !plot.town().equals(townName()) && !plot.type().equals("embassy")) {
            throw new TownyProblem("Це не ділянка вашого міста або посольства.");
        }
        if (plot.type().equals("jail")) throw new TownyProblem("В’язниця належить місту.");
        BigDecimal price = new BigDecimal(plot.data().getOrDefault("price", "-1"));
        if (price.signum() < 0) throw new TownyProblem("Ця ділянка не продається.");
        String seller = plot.data().getOrDefault("resident", "");
        if (name.equals(seller)) throw new TownyProblem("Ця ділянка вже ваша.");
        Map<String, String> sellerRecord = source.residents().get(seller);
        if (!seller.isEmpty() && sellerRecord == null) throw new TownyProblem("Не знайдено попереднього власника.");
        String receiver = seller.isEmpty() ? account("town", source.towns().get(plot.town())) : account("resident", sellerRecord);
        bank.transfer(account("resident", resident), receiver, price);
        resetPlot(plot);
        plot.data().put("resident", name);
        return "Ділянка тепер ваша.";
    }

    String unclaimPlot(Here here) {
        Claim plot = claim(here);
        if (plot == null || !name.equals(plot.data().get("resident"))) throw new TownyProblem("Це не ваша особиста ділянка.");
        resetPlot(plot);
        return "Ділянку повернуто місту.";
    }

    String notForSale(Here here) {
        ownedPlot(here).data().put("price", "-1");
        return "Продаж ділянки скасовано.";
    }

    static void resetPlot(Claim plot) {
        for (String key : List.of("resident", "permissions", "protectionStatus", "trustedResidents", "groupID", "districtID")) {
            plot.data().remove(key);
        }
        plot.data().put("customPermissionData", "{}");
        plot.data().put("changed", "false");
        plot.data().put("price", "-1");
    }

    String setPermissions(String scope, Here here, String relation, String action, boolean allow) {
        if (!Set.of("resident", "nation", "ally", "outsider").contains(relation)
                || !Set.of("build", "destroy", "switch", "itemuse").contains(action)) {
            throw new TownyProblem("Невідома група або дія.");
        }
        String suffix = switch (action) {
            case "build" -> "Build";
            case "destroy" -> "Destroy";
            case "switch" -> "Switch";
            default -> "ItemUse";
        };
        Map<String, String> record;
        String key;
        if (scope.equals("plot")) {
            Claim plot = ownedPlot(here);
            record = plot.data();
            key = "permissions";
            record.putIfAbsent(key, record.getOrDefault("protectionStatus", town().getOrDefault("protectionStatus", "")));
            record.put("changed", "true");
        } else {
            requireTown("towny.command.town.set.perm");
            record = town();
            key = "protectionStatus";
        }
        setRelation(record, key, relation + suffix, allow);
        return "Права оновлено.";
    }

    private static String flipPermission(Map<String, String> record, String key, String flag) {
        boolean allow = !relation(record, key, flag);
        setRelation(record, key, flag, allow);
        return (allow ? "Увімкнено " : "Вимкнено ") + flag + ".";
    }

    private static String account(String scope, Map<String, String> record) {
        return TownyEconomy.account(scope, record);
    }
}
