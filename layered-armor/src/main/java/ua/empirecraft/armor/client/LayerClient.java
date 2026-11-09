package ua.empirecraft.armor.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ua.empirecraft.armor.LayeredArmor;
@Mod.EventBusSubscriber(modid=LayeredArmor.ID,value=Dist.CLIENT)
public final class LayerClient {
    @SubscribeEvent public static void inventory(ScreenEvent.Init.Post event) {
        if(event.getScreen() instanceof InventoryScreen screen)
            event.addListener(Button.builder(Component.empty(),button->LayeredArmor.NETWORK.sendToServer(new LayeredArmor.Open())).bounds(screen.getGuiLeft()+27,screen.getGuiTop()+7,8,8).build());
    }
    @Mod.EventBusSubscriber(modid=LayeredArmor.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->MenuScreens.register(LayeredArmor.MENU.get(),LayerScreen::new));}
    }
}
