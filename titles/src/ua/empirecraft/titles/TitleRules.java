package ua.empirecraft.titles;

/** Transactions run on the server thread; the port supplies the actual player state. */
public final class TitleRules {
    public record Title(String id, String permission, String suffix, String label,
                        String kind, String material, int amount) {}
    public enum Result { SELECTED, UNLOCKED, LOCKED, NO_MONEY, NO_ITEMS, UNAVAILABLE, FAILED }
    public interface PlayerPort {
        boolean owns(String permission);
        boolean withdraw(double price);
        boolean consume(String material, int amount);
        void refundMoney(double price);
        void refundItems(String material, int amount);
        boolean grant(String permission);
        void select(String suffix);
    }
    private TitleRules() {}

    public static Result choose(PlayerPort player, Title title) {
        if (!player.owns(title.permission())) return Result.LOCKED;
        player.select(title.suffix());
        return Result.SELECTED;
    }

    public static Result unlock(PlayerPort player, Title title, double price) {
        // Reopening a stale menu must not charge an already owned title again.
        if (player.owns(title.permission())) return choose(player, title);
        if (title.kind().equals("season")) return Result.LOCKED;
        if (title.kind().equals("shop")) {
            if (!Double.isFinite(price) || price <= 0) return Result.UNAVAILABLE;
            if (!player.withdraw(price)) return Result.NO_MONEY;
        } else if (title.kind().equals("achievement")) {
            if (title.amount() <= 0 || !player.consume(title.material(), title.amount()))
                return Result.NO_ITEMS;
        } else return Result.UNAVAILABLE;
        if (!player.grant(title.permission())) {
            if (title.kind().equals("shop")) player.refundMoney(price);
            else player.refundItems(title.material(), title.amount());
            return Result.FAILED;
        }
        player.select(title.suffix());
        return Result.UNLOCKED;
    }

    /** Return a removal plan without mutating inventory; null means insufficient items. */
    public static int[] plan(int[] stacks, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        int[] result = new int[stacks.length];
        int left = amount;
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] < 0) throw new IllegalArgumentException("negative stack");
            result[i] = Math.min(left, stacks[i]);
            left -= result[i];
        }
        return left == 0 ? result : null;
    }
}
