package ua.empirecraft.townymap.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.townymap.*;

/** Kept client-only so a dedicated server never loads Minecraft's screen classes. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID,value=Dist.CLIENT)
public final class MapModeButton {
    private static MapMode selected=MapMode.KINGDOMS;
    private static Button button;
    public static void selected(MapMode mode){selected=mode;if(button!=null)button.setMessage(Component.literal("Мапа: "+mode.label));}
    @SubscribeEvent public static void opened(ScreenEvent.Init.Post event){
        String type=event.getScreen().getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if(!type.startsWith("journeymap.") || !type.contains("fullscreen"))return;
        var connection=net.minecraft.client.Minecraft.getInstance().getConnection();
        if(connection==null || !CurrencyNetwork.CHANNEL.isRemotePresent(connection.getConnection()))return;
        button=Button.builder(Component.literal("Мапа: "+selected.label),clicked->{
            selected(selected.next());
            CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.MapSelection(selected));
        }).bounds(8,32,174,20).build();
        event.addListener(button);
        CurrencyNetwork.CHANNEL.sendToServer(new CurrencyNetwork.MapQuery());
    }
}
