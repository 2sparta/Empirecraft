package ua.empirecraft.armory;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
/** Vanilla menu buttons encode a signed byte; the armor catalog is much larger than 127. */
public final class ArmoryNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(Armory.ID,"selection"),()->"1","1"::equals,"1"::equals);
    public static void register(){CHANNEL.registerMessage(0,Selection.class,Selection::encode,Selection::decode,Selection::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));}
    public static void select(int menu,int recipe){CHANNEL.sendToServer(new Selection(menu,recipe));}
    public record Selection(int menu,int recipe){
        static void encode(Selection s,FriendlyByteBuf b){b.writeVarInt(s.menu);b.writeVarInt(s.recipe);}
        static Selection decode(FriendlyByteBuf b){return new Selection(b.readVarInt(),b.readVarInt());}
        static void handle(Selection s,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player==null||player.isSpectator()||s.recipe<0||s.recipe>32700)return;if(player.containerMenu instanceof WorkshopMenu menu&&menu.containerId==s.menu&&menu.stillValid(player))menu.clickMenuButton(player,100+s.recipe);});context.setPacketHandled(true);}
    }
}
