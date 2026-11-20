package ua.empirecraft.feudal;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

/** The chest uses the standard inventory screen; controls use real mod item models. */
final class MenuSkin {
    static final NamespacedKey KEY = new NamespacedKey("empirecraft", "menu_skin");
    static void apply(ItemMeta meta, Material material, int slot, String section) {
        meta.getPersistentDataContainer().remove(KEY);
        // Do not replace a mod item's own custom model data.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }
}
