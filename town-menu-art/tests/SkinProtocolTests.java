package ua.empirecraft.menuart;

import java.io.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Exercise genuine Minecraft NBT, including malformed tags and a binary wire round-trip. */
public final class SkinProtocolTests {
    static CompoundTag button(String section,int model){
        var tag=new CompoundTag();var pdc=new CompoundTag();
        pdc.m_128359_(SkinProtocol.KEY,section);tag.m_128365_("PublicBukkitValues",pdc);tag.m_128405_("CustomModelData",model);return tag;
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(SkinProtocol.icon(null)==-1);
        check(SkinProtocol.icon(new CompoundTag())==-1);
        var ordinary=new CompoundTag();ordinary.m_128405_("CustomModelData",742115);
        check(SkinProtocol.icon(ordinary)==-1);
        var renamed=new CompoundTag();renamed.m_128359_("display","Місто · City · Город");
        check(SkinProtocol.icon(renamed)==-1);
        for(int n=0;n<44;n++){
            var original=button("town/1",742100+n);var before=original.m_6426_();
            var out=new ByteArrayOutputStream();NbtIo.m_128947_(original,out);
            var received=NbtIo.m_128939_(new ByteArrayInputStream(out.toByteArray()));
            check(SkinProtocol.icon(received)==n);check(original.equals(before));
        }
        check(SkinProtocol.anchor(button("town/1",742115)));
        check(SkinProtocol.anchor(button("provision/1",742114)));
        check(!SkinProtocol.anchor(button("town/1",742114)));
        check(!SkinProtocol.anchor(button("provision/1",742115)));
        for(String bad:new String[]{"town/2","provision/0","Town/1","","chest/1"})check(SkinProtocol.icon(button(bad,742115))==-1);
        for(int bad:new int[]{0,742099,742144,Integer.MAX_VALUE,Integer.MIN_VALUE})check(SkinProtocol.icon(button("town/1",bad))==-1);
        var malformed=button("town/1",742115);malformed.m_128359_("CustomModelData","742115");check(SkinProtocol.icon(malformed)==-1);
        malformed=new CompoundTag();malformed.m_128359_("PublicBukkitValues","town/1");check(SkinProtocol.icon(malformed)==-1);
        System.out.println("SKIN_PROTOCOL_PASS: 44 binary NBT round-trips, anchors, version isolation, ordinary items, malformed tags, no mutations");
    }
}
