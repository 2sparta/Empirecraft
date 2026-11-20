package ua.empirecraft.feudal;

import java.util.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Decorative controls only: never used as a transaction prototype or a reward. */
final class MenuIcons {
    private static final Map<Material, ItemStack> CACHE = new EnumMap<>(Material.class);
    static String id(Material material) {
        return switch (material) {
            case BELL -> "create:peculiar_bell";
            case PLAYER_HEAD -> "recruits:recruit_spawn_egg";
            case GRASS_BLOCK, DIRT -> "empirecraft_tfc:grass/chernozem";
            case GOLD_INGOT, GOLD_NUGGET, EMERALD -> "magistuarmory:silver_ingot";
            case IRON_HELMET, LEATHER_HELMET -> "magistuarmory:chainmail_helmet";
            case GOLDEN_HELMET -> "magistuarmory:crown_decoration";
            case WRITABLE_BOOK, BOOK, WRITTEN_BOOK, LECTERN -> "patchouli:guide_book";
            case WHITE_BANNER -> "supplementaries:flag_white";
            case RED_BANNER -> "supplementaries:flag_red";
            case BLUE_BANNER -> "supplementaries:flag_blue";
            case YELLOW_BANNER -> "supplementaries:flag_yellow";
            case RED_BED -> "tfc:thatch_bed";
            case PAPER, NAME_TAG, OAK_SIGN -> "create:clipboard";
            case BREAD -> "tfc:food/wheat_bread";
            case CLOCK -> "sereneseasons:calendar";
            case ARROW -> "tfc:metal/javelin/wrought_iron";
            case SUNFLOWER, LEVER -> "create:cogwheel";
            case BARRIER, OAK_DOOR, IRON_DOOR -> "supplementaries:key";
            case MAP, FILLED_MAP, COMPASS -> "supplementaries:globe";
            case RED_DYE, RED_CONCRETE -> "create:redstone_link";
            case LIME_DYE, LIME_CONCRETE -> "create:super_glue";
            case GRAY_DYE -> "create:clutch";
            case STONE_BRICKS -> "tfc:rock/bricks/granite";
            case FEATHER -> "create:schematic_and_quill";
            case EXPERIENCE_BOTTLE, GLASS_BOTTLE -> "farmersrespite:green_tea";
            case ANVIL -> "tfc:metal/anvil/steel";
            case BARREL -> "tfc:wood/barrel/oak";
            case CHEST -> "supplementaries:sack";
            case WHEAT -> "tfc:food/wheat";
            case ROTTEN_FLESH -> "tfc:compost";
            default -> "create:clipboard";
        };
    }
    static ItemStack item(Material material) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(material, m -> {
                ItemStack stack = resolve(id(m));
                if (stack != null) return stack;
                Bukkit.getLogger().warning("[Empirecraft] Menu icon unavailable: " + id(m));
                return new ItemStack(m);
            }).clone();
        }
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
