package ua.empirecraft.chat;

import java.util.*;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.network.chat.Component;

public final class ChannelTests {
    private static int count;
    private static final UUID CITY=UUID.randomUUID(), OTHER=UUID.randomUUID(), KINGDOM=UUID.randomUUID(), WORLD=UUID.randomUUID();
    static void eq(Object a,Object b){count++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    static ChatRules.Member at(UUID city,UUID kingdom,UUID world,double x,double y,double z){return new ChatRules.Member(UUID.randomUUID(),city,kingdom,world,x,y,z);}
    static void bad(String value){try{ChatRules.body(value);throw new AssertionError("Accepted invalid input");}catch(IllegalArgumentException expected){count++;}}
    public static void main(String[] args) throws Exception {
        var sender=at(CITY,KINGDOM,WORLD,0,0,0);
        var distant=at(CITY,KINGDOM,WORLD,5000,0,0);
        var stranger=at(OTHER,OTHER,WORLD,0,0,0);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(OTHER,OTHER,WORLD,50,0,0)),true);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,WORLD,50.001,0,0)),false);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,WORLD,0,50.001,0)),false);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,OTHER,0,0,0)),false);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,WORLD,30,40,0)),true);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,WORLD,30,40,0.01)),false);
        eq(ChatRules.receives(Channel.LOCAL,sender,at(CITY,KINGDOM,WORLD,Double.NaN,0,0)),false);
        eq(ChatRules.receives(Channel.TOWN,sender,distant),true);
        eq(ChatRules.receives(Channel.TOWN,sender,stranger),false);
        eq(ChatRules.receives(Channel.KINGDOM,sender,at(OTHER,KINGDOM,OTHER,0,0,0)),true);
        eq(ChatRules.receives(Channel.KINGDOM,sender,stranger),false);
        eq(ChatRules.receives(Channel.TOWN,at(null,null,WORLD,0,0,0),at(null,null,WORLD,0,0,0)),false);
        eq(ChatRules.receives(Channel.KINGDOM,at(null,null,WORLD,0,0,0),at(null,null,WORLD,0,0,0)),false);
        eq(ChatRules.receives(Channel.GLOBAL,sender,stranger),true);
        eq(ChatRules.receives(Channel.SYSTEM,sender,sender),false);
        eq(ChatRules.body(" Привіт "),"Привіт");bad("");bad("\n");bad("line\nbreak");bad("§cFake");bad("\ue900");bad("x".repeat(257));
        for(Channel channel:Channel.values()){
            var wire=ChatFormat.message(channel,"Київ","Русь","Player","Граф","\ue900","Привіт [local] &c test");
            String json=ComponentSerializer.toString(wire);
            Component message=Component.Serializer.m_130691_(com.google.gson.JsonParser.parseString(json));
            eq(IncomingChannel.read(message),channel);
            eq(message.getString(),"[Київ|Русь] Player Граф \ue900: Привіт [local] &c test");
            eq(IncomingChannel.read(Component.m_237119_().m_7220_(message)),channel);
            eq(wire.getExtra().get(4).getFont(),"empirecraft_chat:titles");
        }
        eq(IncomingChannel.read(Component.m_237113_("empirecraft:chat/v1/town [місто] fake")),Channel.SYSTEM);
        eq(IncomingChannel.read(Component.m_237119_()),Channel.SYSTEM);
        eq(Channel.fromMarker("empirecraft:chat/v1/not-a-channel"),Channel.SYSTEM);
        var noTitle=ChatFormat.message(Channel.GLOBAL,"—","—","Guest","","","Текст");
        eq(noTitle.toPlainText(),"[—|—] Guest: Текст");
        System.out.println("PASS routing/privacy, 3D 50-block boundary, world separation, literal input, actual Bungee → Minecraft JSON, title/font order: "+count+" assertions");
    }
}
