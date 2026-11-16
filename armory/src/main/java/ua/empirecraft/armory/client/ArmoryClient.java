package ua.empirecraft.armory.client;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ua.empirecraft.armory.*;
@Mod.EventBusSubscriber(modid=Armory.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArmoryClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{MenuScreens.register(Armory.MENU.get(),WorkshopScreen::new);var shield=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("hamzabus_ottoman_mod:ottoman_shield"));if(shield instanceof net.minecraft.world.item.ShieldItem)net.minecraft.client.renderer.item.ItemProperties.register(shield,new net.minecraft.resources.ResourceLocation("blocking"),(stack,level,user,seed)->user!=null&&user.isUsingItem()&&user.getUseItem()==stack?1:0);});}
}
