package ua.empirecraft.townyforge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ua.empirecraft.townyforge.TownySnapshot.Claim;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyJail {
    private TownyJail() {}

    static boolean confined(Map<String, String> resident) {
        return resident != null && Long.parseLong(resident.getOrDefault("forgeJailUntil", "0")) > Instant.now().toEpochMilli();
    }

    static void sentence(Source source, Map<String, String> resident, Claim plot, int hours) {
        int limit = Integer.parseInt(source.settings().getOrDefault("jail.maximum_jail_hours", "5"));
        if (hours < 1 || limit > 0 && hours > limit || hours > 8760) throw new TownyProblem("Строк ув’язнення поза дозволеними межами.");
        if (confined(resident)) throw new TownyProblem("Гравець уже ув’язнений.");
        if (!plot.type().equals("jail") || !plot.data().containsKey("jailSpawn")) {
            throw new TownyProblem("Потрібна ділянка в’язниці з точкою камери.");
        }
        int maximum = Integer.parseInt(source.settings().getOrDefault("jail.max_jailed_count", "-1"));
        long count = source.residents().values().stream().filter(TownyJail::confined)
                .filter(r -> plot.town().equals(r.get("forgeJailTown"))).count();
        if (maximum >= 0 && count >= maximum) throw new TownyProblem("В’язниця досягла ліміту ув’язнених.");
        var bank = new TownyEconomy(source);
        String account = TownyEconomy.account("town", source.towns().get(plot.town()));
        bank.fee(account, new BigDecimal(source.settings().getOrDefault("jail.fee_initial_amount", "0")));
        resident.put("forgeJailTown", plot.town());
        resident.put("forgeJailSpawn", plot.data().get("jailSpawn"));
        resident.put("forgeJailUntil", Long.toString(Instant.now().plusSeconds(hours * 3600L).toEpochMilli()));
        resident.remove("forgeJailRelease");
    }

    static void release(Map<String, String> resident) {
        for (String key : List.of("forgeJailTown", "forgeJailSpawn", "forgeJailUntil", "forgeJailBail")) resident.remove(key);
        resident.put("forgeJailRelease", "true");
    }

    static void removeCell(Source source, Claim plot) {
        source.residents().values().stream().filter(record -> matches(source, record.get("forgeJailSpawn"), plot))
                .forEach(TownyJail::release);
        plot.data().remove("jailSpawn");
    }

    private static boolean matches(Source source, String raw, Claim plot) {
        if (raw == null) return false;
        var point = TownyTravel.parse(source.dimensions(), raw);
        return point.cx() == plot.x() && point.cz() == plot.z() && point.dimension().equals(source.dimensions().get(plot.world()));
    }

    static void bail(Source source, Map<String, String> resident) {
        if (!confined(resident) || !TownySnapshot.flag(source.settings(), "jail.bail.is_allowing_bail")) {
            throw new TownyProblem("Застава недоступна.");
        }
        var captor = source.towns().get(resident.get("forgeJailTown"));
        if (captor == null) throw new TownyProblem("Місто в’язниці відсутнє.");
        var bank = new TownyEconomy(source);
        BigDecimal price = new BigDecimal(resident.getOrDefault("forgeJailBail", source.settings().getOrDefault("jail.bail.bail_amount", "0")));
        bank.transfer(TownyEconomy.account("resident", resident), TownyEconomy.account("town", captor), price);
        release(resident);
    }

    static void enforce(TownyStore store, ServerPlayer player) throws java.io.IOException {
        var snapshot = store.snapshot();
        String member = snapshot.residentOf(player.m_20148_());
        var record = snapshot.resident(member);
        if (record == null || !record.containsKey("forgeJailUntil") && !record.containsKey("forgeJailRelease")) return;
        if (confined(record)) {
            var point = TownyTravel.parse(snapshot.dimensions(), record.get("forgeJailSpawn"));
            Claim destination = snapshot.claim(point.dimension(), (int)Math.floor(point.x()), (int)Math.floor(point.z()));
            if (destination != null && destination.type().equals("jail") && destination.town().equals(record.get("forgeJailTown"))) {
                var position = player.m_20183_();
                if (!player.m_9236_().m_46472_().m_135782_().toString().equals(point.dimension())
                        || position.m_123341_() >> 4 != point.cx() || position.m_123343_() >> 4 != point.cz()) {
                    if (!move(player, point)) throw new TownyProblem("Вимір в’язниці недоступний.");
                }
                return;
            }
        }
        var town = snapshot.town(record.getOrDefault("town", ""));
        boolean moved = town != null && town.get("spawn") != null && move(player, TownyTravel.parse(snapshot.dimensions(), town.get("spawn")));
        if (!moved) {
            var level = player.m_20194_().m_129783_();
            var pos = level.m_220360_();
            player.m_8999_(level, pos.m_123341_() + 0.5, pos.m_123342_(), pos.m_123343_() + 0.5, 0, 0);
        }
        store.change(data -> {
            var current = data.residents().get(member);
            release(current);
            current.remove("forgeJailRelease");
            return null;
        });
    }

    private static boolean move(ServerPlayer player, TownyGovernance.Here point) {
        var level = player.m_20194_().m_129880_(ResourceKey.m_135785_(Registries.f_256858_,
                ResourceLocation.m_135820_(point.dimension())));
        if (level == null || point.y() < level.m_141937_() || point.y() + 2 >= level.m_151558_()
                || !level.m_6857_().m_61937_(net.minecraft.core.BlockPos.m_274561_(point.x(), point.y(), point.z()))) return false;
        player.m_8999_(level, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        return true;
    }
}
