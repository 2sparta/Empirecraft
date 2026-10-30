package ua.empirecraft.claimguard;

import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** One player cooldown for every Supplementaries bomb, shared across stacks and hands. */
public final class BombCooldown {
    public static final int TICKS = 40 * 20;
    private BombCooldown() {}

    public static boolean active(Player player) {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof BombItem && player.m_36335_().m_41519_(item)) return true;
        }
        return false;
    }

    public static void start(Player player) {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof BombItem) player.m_36335_().m_41524_(item, TICKS);
        }
    }
}
