package ua.empirecraft.feudal;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Stable wire IDs for decorative controls only. Names, lore and food NBT are never selectors. */
final class MenuSkin {
    static final int BASE = 742100;
    static final NamespacedKey KEY = new NamespacedKey("empirecraft", "menu_skin");

    static int model(Material material, int slot, String section) {
        if (material == Material.ARROW) return BASE + (slot == 52 ? 13 : 12);
        if (section.equals("provision") && material == Material.PAPER) return BASE + 14;
        return BASE + switch (material) {
            case BELL -> 0;
            case PLAYER_HEAD -> 1;
            case GRASS_BLOCK, DIRT -> 2;
            case GOLD_INGOT -> 3;
            case IRON_HELMET, LEATHER_HELMET -> 4;
            case WRITABLE_BOOK, BOOK -> 5;
            case WHITE_BANNER -> 6;
            case GOLDEN_HELMET -> 7;
            case RED_BED -> 8;
            case BREAD -> 39;
            case CLOCK -> 11;
            case SUNFLOWER -> 14;
            case BARRIER -> 15;
            case MAP -> 16;
            case PAPER -> 17;
            case EMERALD -> 18;
            case RED_DYE, RED_CONCRETE -> 19;
            case LIME_DYE, LIME_CONCRETE -> 20;
            case STONE_BRICKS -> 21;
            case FEATHER -> 22;
            case NAME_TAG -> 23;
            case COMPASS -> 25;
            case LEVER -> 26;
            case LECTERN -> 27;
            case EXPERIENCE_BOTTLE -> 28;
            case GOLD_NUGGET -> 30;
            case OAK_DOOR -> 31;
            case GRAY_DYE -> 32;
            case RED_BANNER -> 33;
            case BLUE_BANNER -> 34;
            case YELLOW_BANNER -> 35;
            case CHEST -> 36;
            case WHEAT -> 37;
            case ROTTEN_FLESH -> 38;
            case OAK_SIGN -> 40;
            case GLASS_BOTTLE -> 43;
            default -> 17;
        };
    }

    static void apply(ItemMeta meta, Material material, int slot, String section) {
        meta.setCustomModelData(model(material, slot, section));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, section + "/1");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }
}
