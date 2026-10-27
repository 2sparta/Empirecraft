package ua.empirecraft.townymap.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import ua.empirecraft.townymap.CurrencyNetwork;

/** Client classes are reached only behind the Forge physical-side guard. */
public final class CurrencyClient {
    public static void receive(CurrencyNetwork.Reply packet){
        JsonObject data=JsonParser.parseString(packet.json()).getAsJsonObject();Minecraft mc=Minecraft.getInstance();
        if(packet.open())mc.setScreen(new CurrencyScreen(packet.mode(),data));
        else if(mc.screen instanceof CurrencyScreen screen && screen.session().equals(packet.screen()))screen.update(data);
    }
}
