package ua.empirecraft.townyforge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.townyforge.TownyGovernance.Here;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyTravel {
    enum Kind { TOWN, NATION, OUTPOST }
    record Destination(Here point, long cooldownSeconds, boolean safe) {}
    private record Cooldown(UUID player, Kind kind) {}

    private final Supplier<TownyStore> store;
    private final Map<Cooldown, Long> cooldowns = new HashMap<>();

    TownyTravel(Supplier<TownyStore> store) {
        this.store = store;
    }

    String arrive(CommandSourceStack command, Kind kind, int number) throws CommandSyntaxException {
        var player = command.m_81375_();
        var snapshot = store.get().snapshot();
        if (!snapshot.editable()) throw new TownyProblem("Потрібно оновити імпорт Towny.");
        Destination destination = resolve(snapshot.editableCopy(), snapshot.residentOf(player.m_20148_()),
                TownyCommands.position(command), kind, number);
        Here here = destination.point();
        var level = command.m_81377_().m_129880_(ResourceKey.m_135785_(Registries.f_256858_,
                ResourceLocation.m_135820_(here.dimension())));
        BlockPos pos = BlockPos.m_274561_(here.x(), here.y(), here.z());
        if (level == null || !level.m_6857_().m_61937_(pos)
                || here.y() < level.m_141937_() || here.y() + 2 >= level.m_151558_()) {
            throw new TownyProblem("Точка спавну поза межами доступного світу.");
        }
        Cooldown cooldown = new Cooldown(player.m_20148_(), kind);
        long now = System.currentTimeMillis();
        long remaining = cooldowns.getOrDefault(cooldown, 0L) - now;
        if (remaining > 0) throw new TownyProblem("Зачекайте ще " + (remaining + 999) / 1000 + " с.");
        if (destination.safe() && (!level.m_45756_(player, player.m_20191_().m_82386_(
                here.x() - player.m_20185_(), here.y() - player.m_20186_(), here.z() - player.m_20189_()))
                || !level.m_8055_(pos.m_7495_()).m_280296_() || !level.m_6425_(pos).m_76178_())) {
            throw new TownyProblem("Точка спавну небезпечна.");
        }
        player.m_8999_(level, here.x(), here.y(), here.z(), here.yaw(), here.pitch());
        cooldowns.put(cooldown, now + destination.cooldownSeconds() * 1000L);
        return "Телепортовано до точки спавну Towny.";
    }

    static Destination resolve(Source data, String member, Here origin, Kind kind, int number) {
        Map<String, String> settings = data.settings();
        String group = kind == Kind.NATION ? "nation_spawn" : "town_spawn";
        String prefix = "spawning." + group + ".";
        if (!Boolean.parseBoolean(settings.getOrDefault(prefix + "allow_" + group, "false"))
                || !settings.getOrDefault("spawning.spawning_warmups.teleport_warmup_time", "0").equals("0")
                || kind != Kind.NATION && new BigDecimal(settings.getOrDefault(
                        "economy.spawn_travel.price_town_spawn_travel", "0")).signum() != 0) {
            throw new TownyProblem("Цей режим телепортації Towny вимкнено.");
        }
        Map<String, String> resident = data.residents().getOrDefault(member, Map.of());
        String townName = resident.getOrDefault("town", "");
        Map<String, String> town = data.towns().get(townName);
        if (town == null) throw new TownyProblem("Спочатку вступіть до міста.");
        String nationName = town.getOrDefault("nation", "");
        String restricted = settings.get("spawning.town_spawn.prevent_town_spawn_in");
        var originClaim = claim(data, origin);
        if (originClaim == null && TownySnapshot.listed(restricted, "unclaimed")) {
            throw new TownyProblem("Телепорт із дикої території заборонено.");
        }
        if (originClaim != null && !originClaim.town().equals(townName)) {
            var owner = data.towns().get(originClaim.town());
            String ownerNation = owner.getOrDefault("nation", "");
            if (TownySnapshot.listed(restricted, "outlaw") && TownySnapshot.listed(owner.get("outlaws"), member)
                    || TownySnapshot.listed(restricted, "neutral") && TownySnapshot.flag(owner, "neutral")
                    || TownySnapshot.listed(restricted, "enemy") && TownySnapshot.listed(
                            data.nations().getOrDefault(ownerNation, Map.of()).get("enemies"), nationName)) {
                throw new TownyProblem("Телепорт із цієї території заборонено.");
            }
        }

        String destinationTown = townName;
        String raw = town.get("spawn");
        String cooldown = "town_spawn_cooldown_time";
        TownySnapshot.Claim selectedOutpost = null;
        if (kind == Kind.NATION) {
            var nation = data.nations().get(nationName);
            if (nation == null) throw new TownyProblem("Ваше місто не входить до нації.");
            destinationTown = nation.get("capital");
            raw = nation.get("spawn");
            if (raw == null || raw.isBlank()) raw = data.towns().get(destinationTown).get("spawn");
            cooldown = "nation_member_nation_spawn_cooldown_time";
        } else if (kind == Kind.OUTPOST) {
            var outposts = data.claims().stream()
                    .filter(plot -> plot.town().equals(townName) && TownySnapshot.flag(plot.data(), "outpost"))
                    .sorted(Comparator.comparingLong(plot -> Long.parseLong(plot.data().getOrDefault("claimedAt", "0"))))
                    .toList();
            if (number < 1 || number > outposts.size()) throw new TownyProblem("Аванпост не знайдено.");
            selectedOutpost = outposts.get(number - 1);
            raw = selectedOutpost.data().get("spawn");
            if (raw == null || raw.isBlank()) raw = importedOutpost(data, town, selectedOutpost);
            cooldown = "outpost_cooldown_time";
        }
        Here target = parse(data.dimensions(), raw);
        var plot = claim(data, target);
        if (plot == null || !plot.town().equals(destinationTown)
                || selectedOutpost != null && !selectedOutpost.equals(plot)) {
            throw new TownyProblem("Точка спавну не належить потрібній території.");
        }
        long seconds = Long.parseLong(settings.getOrDefault(prefix + "spawning_cooldowns." + cooldown, "30"));
        return new Destination(target, Math.max(0, seconds), Boolean.parseBoolean(settings.get("spawning.safe_teleport")));
    }

    private static String importedOutpost(Source data, Map<String, String> town, TownySnapshot.Claim outpost) {
        for (String raw : town.getOrDefault("outpostspawns", "").split(";")) {
            if (raw.isBlank()) continue;
            Here point = parse(data.dimensions(), raw);
            if (point.cx() == outpost.x() && point.cz() == outpost.z()
                    && point.dimension().equals(data.dimensions().get(outpost.world()))) return raw;
        }
        return null;
    }

    private static TownySnapshot.Claim claim(Source data, Here point) {
        return data.claims().stream().filter(plot -> point.dimension().equals(data.dimensions().get(plot.world()))
                && plot.x() == point.cx() && plot.z() == point.cz()).findFirst().orElse(null);
    }

    static Here parse(Map<String, String> dimensions, String raw) {
        if (raw == null) throw new TownyProblem("Точку спавну не встановлено.");
        String[] fields = raw.split(",", -1);
        if (fields.length != 6) throw new TownyProblem("Точка спавну пошкоджена.");
        String dimension = dimensions.get(fields[0]);
        try {
            double x = Double.parseDouble(fields[1]), y = Double.parseDouble(fields[2]), z = Double.parseDouble(fields[3]);
            float pitch = Float.parseFloat(fields[4]), yaw = Float.parseFloat(fields[5]);
            if (dimension != null && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Float.isFinite(yaw) && Float.isFinite(pitch)) return new Here(dimension, x, y, z, yaw, pitch);
        } catch (NumberFormatException malformed) {
            throw new TownyProblem("Точка спавну пошкоджена.");
        }
        throw new TownyProblem("Точка спавну пошкоджена або її вимір недоступний.");
    }
}
