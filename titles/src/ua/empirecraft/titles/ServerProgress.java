package ua.empirecraft.titles;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** One command/check snapshot. Bukkit reads vanilla history and Forge advancement keys. */
final class ServerProgress {
    private final Player player;
    private final Consumer<String> unavailable;
    private final Map<String, Long> cache = new HashMap<>();

    ServerProgress(Player player, Consumer<String> unavailable) {
        this.player = player;
        this.unavailable = unavailable;
    }

    static void validate(TitleRules.Requirement r) {
        switch (r.type()) {
            case "advancement" -> {
                if (NamespacedKey.fromString(r.key()) == null) throw new IllegalArgumentException("Invalid advancement key");
            }
            case "towny" -> {
                if (!Set.of("member", "nation", "mayor").contains(r.key())) throw new IllegalArgumentException("Invalid Towny role");
            }
            case "statistic" -> {
                Statistic statistic = Statistic.valueOf(r.key());
                if (new HashSet<>(r.targets()).size() != r.targets().size()) throw new IllegalArgumentException("Duplicate stat targets");
                if ((statistic.getType() == Statistic.Type.UNTYPED) != r.targets().isEmpty())
                    throw new IllegalArgumentException("Wrong stat target count: " + r.key());
                for (String target : r.targets()) {
                    if (statistic.getType() == Statistic.Type.ENTITY) EntityType.valueOf(target);
                    else {
                        Material material = Material.matchMaterial(target);
                        if (material == null || statistic.getType() == Statistic.Type.BLOCK && !material.isBlock())
                            throw new IllegalArgumentException("Invalid material: " + target);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Invalid source type");
        }
    }

    long read(TitleRules.Requirement r) {
        String source = r.type() + ":" + r.key() + ":" + String.join(",", r.targets());
        return cache.computeIfAbsent(source, key -> {
            try {
                long value = switch (r.type()) {
                    case "statistic" -> statistic(r);
                    case "advancement" -> {
                        var advancement = player.getServer().getAdvancement(NamespacedKey.fromString(r.key()));
                        yield advancement == null ? -1 : player.getAdvancementProgress(advancement).isDone() ? 1 : 0;
                    }
                    case "towny" -> towny(r.key());
                    default -> -1;
                };
                if (value < 0) unavailable.accept(source);
                return value;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                unavailable.accept(source);
                return -1L;
            }
        });
    }

    private long statistic(TitleRules.Requirement r) {
        Statistic statistic = Statistic.valueOf(r.key());
        if (statistic.getType() == Statistic.Type.UNTYPED) return Math.max(0, player.getStatistic(statistic));
        long sum = 0;
        for (String target : r.targets()) {
            int value = statistic.getType() == Statistic.Type.ENTITY
                ? player.getStatistic(statistic, EntityType.valueOf(target))
                : player.getStatistic(statistic, Objects.requireNonNull(Material.matchMaterial(target)));
            sum += Math.max(0, value);
        }
        return sum;
    }

    /** Public Towny API only; optional integration, no database or permission shortcuts. */
    private long towny(String role) throws ReflectiveOperationException {
        if (!player.getServer().getPluginManager().isPluginEnabled("Towny")) return -1;
        Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        Object resident = apiClass.getMethod("getResident", UUID.class).invoke(api, player.getUniqueId());
        if (resident == null) return 0;
        if (!(boolean) call(resident, "hasTown")) return 0;
        return switch (role) {
            case "member" -> 1;
            case "mayor" -> (boolean) call(resident, "isMayor") ? 1 : 0;
            case "nation" -> (boolean) call(call(resident, "getTown"), "hasNation") ? 1 : 0;
            default -> -1;
        };
    }

    private static Object call(Object object, String method) throws ReflectiveOperationException {
        Method accessor = object.getClass().getMethod(method);
        return accessor.invoke(object);
    }
}
