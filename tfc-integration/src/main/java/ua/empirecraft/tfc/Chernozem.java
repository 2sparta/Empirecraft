package ua.empirecraft.tfc;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.util.registry.RegistrySoilVariant;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/** TFC soil blocks share the native tool actions, spreading grass and farmland entity. */
public final class Chernozem implements RegistrySoilVariant {
    private static final Chernozem VARIANT = new Chernozem();
    private final EnumMap<SoilBlockType, RegistryObject<Block>> blocks = new EnumMap<>(SoilBlockType.class);

    private Chernozem() {}

    static void register() {
        for (var type : List.of(SoilBlockType.DIRT, SoilBlockType.GRASS, SoilBlockType.GRASS_PATH,
                SoilBlockType.FARMLAND, SoilBlockType.ROOTED_DIRT)) {
            String path = type.name().toLowerCase(java.util.Locale.ROOT) + "/chernozem";
            var block = Integration.BLOCKS.register(path, () -> type.create(VARIANT));
            VARIANT.blocks.put(type, block);
            Integration.ITEMS.register(path, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static Block block(SoilBlockType type) {
        return VARIANT.getBlock(type).get();
    }

    public static boolean isFarmland(Block block) {
        var farmland = VARIANT.blocks.get(SoilBlockType.FARMLAND);
        return farmland != null && farmland.isPresent() && farmland.get() == block;
    }

    @Override
    public Supplier<? extends Block> getBlock(SoilBlockType type) {
        var registered = blocks.get(type);
        return registered == null ? SoilBlockType.Variant.SILTY_LOAM.getBlock(type) : registered;
    }

    @Override
    public Supplier<? extends Item> getDriedMudBrick() {
        return SoilBlockType.Variant.SILTY_LOAM.getDriedMudBrick();
    }

    static Block block(SoilBlockType type, RegionalSoilPalette.Soil soil) {
        if (soil == RegionalSoilPalette.Soil.CHERNOZEM) return block(type);
        var texture = switch (soil) {
            case SILT -> SoilBlockType.Variant.SILT;
            case SILTY_LOAM -> SoilBlockType.Variant.SILTY_LOAM;
            case SANDY_LOAM -> SoilBlockType.Variant.SANDY_LOAM;
            default -> SoilBlockType.Variant.LOAM;
        };
        return TFCBlocks.SOIL.get(type).get(texture).get();
    }
}
