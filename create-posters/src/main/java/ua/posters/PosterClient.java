package ua.posters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CreatePosters.ID,value=Dist.CLIENT)
public final class PosterClient {
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){
        if(e.getScreen() instanceof SchematicTableScreen screen){
            int x=screen.getGuiLeft(),y=screen.getGuiTop()-23;
            if(y<2){x=Math.min(screen.f_96543_-87,screen.getGuiLeft()+screen.getXSize()-85);y=2;}
            e.addListener(Button.m_253074_(Component.m_237115_("gui.create_posters.tab"),b->
                Minecraft.m_91087_().m_91152_(new PosterScreen(screen))).m_252987_(x,y,85,20).m_253136_());
        }
    }
    public static void result(PosterNetwork.Result r){if(Minecraft.m_91087_().f_91080_ instanceof PosterScreen s)s.result(r);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){PosterTextures.clear();}
    @Mod.EventBusSubscriber(modid=CreatePosters.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(CreatePosters.POSTER.get(),PosterRenderer::new);}
    }
}
