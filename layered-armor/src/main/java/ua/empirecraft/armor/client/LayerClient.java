package ua.empirecraft.armor.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ua.empirecraft.armor.*;
@Mod.EventBusSubscriber(modid=LayeredArmor.ID,value=Dist.CLIENT)
public final class LayerClient {
    public static void toggle(InventoryScreen screen,boolean expanded) {
        ((LayerInventoryAccess)screen.getMenu()).empirecraft$layers().expanded(expanded);
        if(expanded&&screen.getRecipeBookComponent().isVisible())screen.getRecipeBookComponent().toggleVisibility();
        LayeredArmor.NETWORK.sendToServer(new LayeredArmor.Toggle(expanded));
        screen.init(Minecraft.getInstance(),screen.width,screen.height);
    }
    @SubscribeEvent public static void inventory(ScreenEvent.Init.Post event) {
        if(event.getScreen() instanceof InventoryScreen screen) {
            var state=((LayerInventoryAccess)screen.getMenu()).empirecraft$layers();
            event.addListener(Button.builder(Component.literal(state.expanded()?"«":"»"),button->toggle(screen,!state.expanded()))
                .bounds(screen.getGuiLeft()+27,screen.getGuiTop()+7,12,12).tooltip(Tooltip.create(ArmorLabels.title())).build());
        }
    }
    @SubscribeEvent public static void close(ScreenEvent.Closing event) {
        if(event.getScreen() instanceof InventoryScreen screen) {
            ((LayerInventoryAccess)screen.getMenu()).empirecraft$layers().expanded(false);
            LayeredArmor.NETWORK.sendToServer(new LayeredArmor.Toggle(false));
        }
    }
    @Mod.EventBusSubscriber(modid=LayeredArmor.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->MenuScreens.register(LayeredArmor.MENU.get(),LayerScreen::new));}
    }
}
