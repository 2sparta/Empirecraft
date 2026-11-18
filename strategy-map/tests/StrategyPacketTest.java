package ua.empirecraft.townymap;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StrategyPacketTest {
    @Test void countryRulersAndBannerRoundTripWithHistoricalCard() {
        var entry=new MapHistoryNetwork.Entry("Січ","Графство · Старий правитель",
                "{\"government\":\"REPUBLIC\",\"leaders\":[{\"name\":\"Олена\"},{\"name\":\"Богдан\"}],\"flag\":{\"kind\":\"banner\"}}");
        var reply=new MapHistoryNetwork.Reply("request",MapHistoryNetwork.Status.KNOWN,List.of(entry));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{MapHistoryNetwork.Reply.encode(reply,buffer);assertEquals(reply,MapHistoryNetwork.Reply.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally{buffer.release();}
    }
    @Test void shipPagePreservesFocusBatchAndLastKnownSnapshot() {
        var page=new StrategyNetwork.Observations("[{\"name\":\"Чорний крук\",\"heading\":90,\"speed\":5}]",42,12,1,2);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{StrategyNetwork.Observations.encode(page,buffer);assertEquals(page,StrategyNetwork.Observations.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally{buffer.release();}
    }
    @Test void profileCardRejectsOversizedEntryCountBeforeReadingEntries() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{buffer.m_130070_("request");buffer.m_130068_(MapHistoryNetwork.Status.KNOWN);buffer.m_130130_(33);
            assertThrows(IllegalArgumentException.class,()->MapHistoryNetwork.Reply.decode(buffer));}
        finally{buffer.release();}
    }
}
