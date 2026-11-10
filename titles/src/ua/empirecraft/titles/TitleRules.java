package ua.empirecraft.titles;

import java.util.List;

/** Read persisted server progress; claiming a cosmetic reward never spends it. */
public final class TitleRules {
    public record Requirement(String type, String key, List<String> targets, long amount, String description) {
        public Requirement {
            targets = List.copyOf(targets);
            if (!List.of("statistic", "advancement", "towny").contains(type) || key == null || key.isBlank()
                    || amount <= 0 || description == null || description.isBlank())
                throw new IllegalArgumentException("Invalid title requirement");
            if (!type.equals("statistic") && (amount != 1 || !targets.isEmpty()))
                throw new IllegalArgumentException("Boolean requirements must have amount 1 and no targets");
        }
    }
    public record Title(String id, String permission, String suffix, String label,
                        String kind, List<Requirement> requirements) {
        public Title {
            requirements = List.copyOf(requirements);
            if (!List.of("achievement", "season").contains(kind)
                    || kind.equals("achievement") && requirements.isEmpty()
                    || kind.equals("season") && !requirements.isEmpty())
                throw new IllegalArgumentException("Invalid acquisition rules: " + id);
        }
    }
    public enum Result { SELECTED, UNLOCKED, OWNED, LOCKED, INCOMPLETE, UNAVAILABLE, FAILED }
    public interface PlayerPort {
        boolean owns(String permission);
        /** -1 means the progress source is unavailable, never an earned reward. */
        long progress(Requirement requirement);
        boolean grant(String permission);
        void select(String suffix);
    }
    private TitleRules() {}

    public static Result choose(PlayerPort player, Title title) {
        if (!player.owns(title.permission())) return Result.LOCKED;
        player.select(title.suffix());
        return Result.SELECTED;
    }

    /** All requirements must be met. Missing integrations fail closed. */
    public static Result eligible(PlayerPort player, Title title) {
        if (title.kind().equals("season")) return Result.LOCKED;
        boolean incomplete = false;
        for (Requirement requirement : title.requirements()) {
            long value = player.progress(requirement);
            if (value < 0) return Result.UNAVAILABLE;
            if (value < requirement.amount()) incomplete = true;
        }
        return incomplete ? Result.INCOMPLETE : Result.UNLOCKED;
    }

    /** Automatic awards do not change the equipped title. Old entitlements survive migration. */
    public static Result claim(PlayerPort player, Title title) {
        if (player.owns(title.permission())) return Result.OWNED;
        Result result = eligible(player, title);
        if (result != Result.UNLOCKED) return result;
        return player.grant(title.permission()) ? Result.UNLOCKED : Result.FAILED;
    }

    public static Result unlock(PlayerPort player, Title title) {
        Result result = claim(player, title);
        if (result == Result.OWNED) return choose(player, title);
        if (result == Result.UNLOCKED) player.select(title.suffix());
        return result;
    }
}
