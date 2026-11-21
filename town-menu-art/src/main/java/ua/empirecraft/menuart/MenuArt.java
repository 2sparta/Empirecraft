package ua.empirecraft.menuart;

import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native inventory icons, registered identically on client and server. No screen replacement. */
@Mod(MenuArt.ID)
public final class MenuArt {
    public static final String ID = "empirecraft_menu_art";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final Map<String, RegistryObject<Item>> ICONS = new LinkedHashMap<>();
    static {
        for (String name : IconCatalog.NAMES) {
            ICONS.put(name, ITEMS.register(name, () -> new Item(new Item.Properties().m_41487_(1))));
        }
    }
    public MenuArt() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
