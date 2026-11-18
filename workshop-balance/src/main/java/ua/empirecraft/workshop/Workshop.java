package ua.empirecraft.workshop;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(Workshop.ID)
public final class Workshop {
    public static final String ID="empirecraft_workshop";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(ID,"diameter"),()->"1","1"::equals,"1"::equals);
    public Workshop(){
        CHANNEL.registerMessage(0,Diameter.class,Diameter::encode,Diameter::decode,Diameter::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e)->{if(e.phase==TickEvent.Phase.END&&e.level instanceof ServerLevel level)CastJobs.get(level).tick(level);});
    }
    public static void diameter(int menu,int value){CHANNEL.sendToServer(new Diameter(menu,value));}
    public record Diameter(int menu,int value){
        static void encode(Diameter p,FriendlyByteBuf b){b.m_130130_(p.menu);b.m_130130_(p.value);}
        static Diameter decode(FriendlyByteBuf b){return new Diameter(b.m_130242_(),b.m_130242_());}
        static void handle(Diameter p,Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();context.enqueueWork(()->{
                var player=context.getSender();if(player==null||player.m_5833_()||!CastRules.validDiameter(p.value))return;
                var menu=player.f_36096_;if(menu.f_38840_==p.menu&&menu instanceof CastAccess cast&&menu.m_6875_(player)){cast.workshop$diameter(p.value);menu.m_38946_();}
            });context.setPacketHandled(true);
        }
    }
}
