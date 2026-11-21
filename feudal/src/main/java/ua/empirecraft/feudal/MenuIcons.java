package ua.empirecraft.feudal;

import java.util.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Decorative controls only: never used as a transaction prototype, storage item or reward. */
final class MenuIcons {
    static final String NAMESPACE = "empirecraft_menu_art:";
    private static final Map<String, ItemStack> CACHE = new HashMap<>();
    static String id(Material material) { return id(material, -1, "town"); }
    static String id(Material material, int slot, String section) {
        if (material == Material.ARROW) return NAMESPACE + (slot == 52 || slot == 53 ? "next" : "back");
        if (section.equals("provision")) {
            if (material == Material.BREAD) return NAMESPACE + "satisfied";
            if (material == Material.PAPER) return NAMESPACE + "refresh";
        }
        if (material == Material.PAPER && slot == 30) return NAMESPACE + "offers";
        return NAMESPACE + switch (material) {
            case BELL -> "town";
            case PLAYER_HEAD -> "people";
            case GRASS_BLOCK, DIRT -> "land";
            case GOLD_INGOT -> "treasury";
            case GOLD_NUGGET -> "coin";
            case EMERALD -> "invite";
            case IRON_HELMET, LEATHER_HELMET -> "vassals";
            case GOLDEN_HELMET -> "crown";
            case WRITABLE_BOOK, BOOK, WRITTEN_BOOK -> "treaty";
            case LECTERN -> "council";
            case WHITE_BANNER -> "union";
            case RED_BANNER -> "red_flag";
            case BLUE_BANNER -> "blue_flag";
            case YELLOW_BANNER -> "yellow_flag";
            case RED_BED -> "dynasty";
            case PAPER -> "parchment";
            case NAME_TAG -> "name";
            case OAK_SIGN -> "help";
            case BREAD -> "provisions";
            case CLOCK -> "history";
            case SUNFLOWER -> "refresh";
            case LEVER, ANVIL -> "settings";
            case BARRIER -> "close";
            case OAK_DOOR, IRON_DOOR -> "depart";
            case MAP, FILLED_MAP -> "map";
            case COMPASS -> "compass";
            case RED_DYE, RED_CONCRETE -> "reject";
            case LIME_DYE, LIME_CONCRETE -> "confirm";
            case GRAY_DYE -> "neutral";
            case STONE_BRICKS -> "castle";
            case FEATHER -> "quill";
            case EXPERIENCE_BOTTLE -> "ascend";
            case GLASS_BOTTLE -> "bottle";
            case BARREL, CHEST -> "storage";
            case WHEAT -> "wheat";
            case ROTTEN_FLESH -> "hunger";
            case IRON_SWORD, SHIELD -> "recruitment";
            default -> "parchment";
        };
    }
    static ItemStack item(Material material) { return item(material, -1, "town"); }
    static ItemStack item(Material material, int slot, String section) {
        return item(id(material, slot, section), material);
    }
    private static ItemStack item(String id, Material fallback) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(id, key -> {
                ItemStack stack = resolve(key);
                if (stack != null) return stack;
                Bukkit.getLogger().warning("[Empirecraft] Відсутня іконка " + key
                    + ". Установіть empirecraft-menu-art-1.1.0.jar на сервер і клієнти.");
                return new ItemStack(fallback);
            }).clone();
        }
    }
    /** Called after InventoryOpen listeners add their tagged recruitment shortcuts. */
    static void finishTownMenu(Inventory inventory) {
        finishTownMenu(inventory, MenuIcons::item);
    }
    static void finishTownMenu(Inventory inventory, java.util.function.BiFunction<String,Material,ItemStack> icons) {
        for (int slot = 45; slot < Math.min(54, inventory.getSize()); slot++) {
            ItemStack original = inventory.getItem(slot);
            if (original == null) continue;
            String icon = footerIcon(original.getType());
            if (icon == null) continue;
            ItemStack replacement = icons.apply(NAMESPACE + icon, original.getType());
            // Bukkit converts the generic display metadata while retaining both plugins' PDC click keys.
            if (replacement.setItemMeta(original.getItemMeta())) {
                replacement.setAmount(original.getAmount());
                inventory.setItem(slot, replacement);
            }
        }
    }
    static String footerIcon(Material material) {
        return switch (material) {
            case IRON_SWORD -> "recruitment";
            case IRON_HELMET -> "garrison";
            default -> null;
        };
    }
    // Mohist exposes mod items as Bukkit materials. The bridge also supports registries
    // whose generated enum spelling differs; no NMS class is linked on vanilla Bukkit.
    private static ItemStack resolve(String id) {
        String enumName = id.replace(':', '_').replace('/', '_').toUpperCase(Locale.ROOT);
        Material material = Arrays.stream(Material.values()).filter(m -> m.name().equals(enumName)).findFirst().orElse(null);
        if (material != null && !material.isAir()) return new ItemStack(material);
        try {
            var lookup = MethodHandles.publicLookup();
            Class<?> registries = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Class<?> registryType = Class.forName("net.minecraftforge.registries.IForgeRegistry");
            Object registry = lookup.unreflectGetter(registries.getField("ITEMS")).invoke();
            Class<?> location = Class.forName("net.minecraft.resources.ResourceLocation");
            Object key = lookup.findConstructor(location, MethodType.methodType(void.class, String.class)).invoke(id);
            Object item = lookup.findVirtual(registryType, "getValue", MethodType.methodType(Object.class, location)).invoke(registry, key);
            Object actual = lookup.findVirtual(registryType, "getKey", MethodType.methodType(location, Object.class)).invoke(registry, item);
            if (!key.equals(actual)) return null;
            Class<?> itemLike = Class.forName("net.minecraft.world.level.ItemLike");
            Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
            Object nativeStack = lookup.findConstructor(stack, MethodType.methodType(void.class, itemLike)).invoke(item);
            String craft = Bukkit.getServer().getClass().getPackageName() + ".inventory.CraftItemStack";
            return (ItemStack)lookup.findStatic(Class.forName(craft), "asBukkitCopy", MethodType.methodType(ItemStack.class, stack)).invoke(nativeStack);
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError fatal) throw fatal;
            Bukkit.getLogger().warning("[Empirecraft] Cannot resolve menu item " + id + ": " + e);
            return null;
        }
    }
}
