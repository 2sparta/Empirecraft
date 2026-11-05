package ua.empirecraft.townymap;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FleetPacketTest {
    @Test void fleetTermsRoundTripWithoutPlayerIdentity(){
        var request=new FleetNetwork.Request(UUID.randomUUID().toString(),"prepare","market","sell",List.of("42","ринок","100.50","KRN","24","Судно з вітрилами"),"",1,"screen");
        var buffer=new FriendlyByteBuf(Unpooled.buffer());try{FleetNetwork.Request.encode(request,buffer);assertEquals(request,FleetNetwork.Request.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
    }
    @Test void oversizedFieldCountIsRejectedBeforeReadingValues(){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());try{buffer.writeUtf("");buffer.writeUtf("prepare");buffer.writeUtf("market");buffer.writeUtf("sell");buffer.writeVarInt(11);assertThrows(IllegalArgumentException.class,()->FleetNetwork.Request.decode(buffer));}finally{buffer.release();}
    }
    @Test void oversizedTotalTextIsRejected(){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());try{buffer.writeUtf("");buffer.writeUtf("prepare");buffer.writeUtf("market");buffer.writeUtf("sell");buffer.writeVarInt(5);for(int i=0;i<5;i++)buffer.writeUtf("a".repeat(2000));assertThrows(IllegalArgumentException.class,()->FleetNetwork.Request.decode(buffer));}finally{buffer.release();}
    }
}
