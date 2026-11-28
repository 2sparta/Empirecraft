package ua.empirecraft.provenance;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Craftsman marks and provenance written onto crafted goods. */
public final class Mark {
    public static final String TAG = "EmpirecraftProvenance";
    private static final String MAKER = "Maker";
    private static final String GUILD = "Guild";
    private static final String DAY = "Day";
    private static final String ORIGIN = "Origin";
    private static final String DIMENSION = "Dimension";

    public enum Guild {
        SMITHS("smiths"),
        ARMORERS("armorers"),
        WEAVERS("weavers"),
        FOUNDERS("founders");

        final String key;

        Guild(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "guild.empirecraft_provenance." + key;
        }
    }

    /** Which guild signs this item, or null when the item carries no mark. */
    public static Guild guildFor(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (stack.getItem() instanceof ArmorItem) {
            return Guild.ARMORERS;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem) {
            return Guild.SMITHS;
        }
        if (id.getNamespace().equals("weaversparadise") || path.contains("cloth")
                || path.contains("garment") || path.contains("garments")) {
            return Guild.WEAVERS;
        }
        if (path.endsWith("_ingot") || path.endsWith("_double_ingot") || path.contains("ingot/")
                || path.endsWith("_sheet") || path.endsWith("_plate")) {
            return Guild.FOUNDERS;
        }
        return null;
    }

    /** Cloth and metal travel in batches: they carry where they were made, not who signed them. */
    public static boolean isBatch(Guild guild) {
        return guild == Guild.WEAVERS || guild == Guild.FOUNDERS;
    }

    public static void stamp(Player player, ItemStack stack) {
        Guild guild = guildFor(stack);
        if (guild == null || stack.isEmpty() || player.level().isClientSide) {
            return;
        }
        CompoundTag mark = new CompoundTag();
        mark.putString(GUILD, guild.key);
        mark.putInt(DAY, day(player.level()));
        mark.putString(ORIGIN, origin(player.blockPosition()));
        mark.putString(DIMENSION, player.level().dimension().location().toString());
        if (!isBatch(guild)) {
            mark.putString(MAKER, player.getName().getString());
        }
        stack.getOrCreateTag().put(TAG, mark);
    }

    public static List<Component> describe(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG)) {
            return List.of();
        }
        CompoundTag mark = stack.getTag().getCompound(TAG);
        String guild = mark.getString(GUILD);
        if (guild.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new java.util.ArrayList<>();
        String maker = mark.getString(MAKER);
        if (!maker.isEmpty()) {
            lines.add(Component.translatable("tooltip.empirecraft_provenance.maker", maker)
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip.empirecraft_provenance.guild",
                Component.translatable("guild.empirecraft_provenance." + guild))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.empirecraft_provenance.day", mark.getInt(DAY))
                .withStyle(ChatFormatting.DARK_GRAY));
        String origin = mark.getString(ORIGIN);
        String dimension = mark.getString(DIMENSION);
        if (!origin.isEmpty()) {
            lines.add(Component.translatable("tooltip.empirecraft_provenance.origin", origin,
                    shortDimension(dimension)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "";
        }
        return dimension.substring(dimension.indexOf(':') + 1);
    }

    private static int day(Level level) {
        return (int) (level.getDayTime() / 24000L) + 1;
    }

    private static String origin(BlockPos pos) {
        return pos.getX() + ", " + pos.getZ();
    }
}
