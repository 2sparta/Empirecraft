package ua.empirecraft.forging;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class RangedQuality {
    public static final String KEY = "empirecraft_forging_damage";
    private RangedQuality() {}
    public static void mark(Entity projectile, ItemStack weapon) {
        // Snapshot the exact firing stack, including offhand weapons; switching gear later cannot change a shot.
        double bonus = QualityStats.bonus(EquipmentQuality.quality(weapon), true);
        if (bonus != 0) projectile.getPersistentData().m_128347_(KEY, bonus);
    }
    public static float damage(float base, double bonus) { return (float)Math.max(0, base + bonus); }
}
