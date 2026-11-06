package guardtest;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.RegimentalBanners.*;
import ua.townyrecruits.map.ArmyMapData;
import ua.townyrecruits.map.ArmyMapData.*;

public final class ArmyMapCodecTest {
    static final ResourceLocation DIM=new ResourceLocation("minecraft:overworld");
    static Snapshot roundtrip(Snapshot p){var b=new FriendlyByteBuf(Unpooled.buffer());try{Codec.write(p,b);assert b.readableBytes()<524288;Snapshot copy=Codec.read(b);assert !b.isReadable();return copy;}finally{b.release();}}
    static void rejects(Snapshot p){var b=new FriendlyByteBuf(Unpooled.buffer());try{Codec.write(p,b);try{Codec.read(b);throw new AssertionError("Invalid army snapshot accepted");}catch(IllegalArgumentException expected){}}finally{b.release();}}
    public static void main(String[] args) {
        List<Style> styles=new ArrayList<>();
        for(int i=0;i<128;i++)styles.add(new Style(UUID.randomUUID(),"Полк_"+i,new Design(i%16,List.of(new Layer("bs",2),new Layer("cre",15)))));
        List<Unit> units=new ArrayList<>();
        for(int i=0;i<2048;i++)units.add(new Unit(UUID.randomUUID(),new Vec3(i,64.5,-i),i%360,Relation.values()[i%3],Kind.values()[i%5],styles.get(i%128).regiment(),"Нація","Загін","Commander",i%20==0,i%7==0));
        Snapshot p=new Snapshot(DIM,42,List.copyOf(styles),List.copyOf(units));assert roundtrip(p).equals(p);
        assert roundtrip(new Snapshot(DIM,43,List.of(),List.of())).units().isEmpty();
        rejects(new Snapshot(DIM,44,Collections.nCopies(129,styles.get(0)),List.of()));
        rejects(new Snapshot(DIM,44,List.of(styles.get(0),styles.get(0)),List.of()));
        rejects(new Snapshot(DIM,44,List.of(),List.of(units.get(0))));
        var noReg=new Unit(UUID.randomUUID(),Vec3.f_82478_,0,Relation.NEUTRAL,Kind.INFANTRY,null,"","","",false,false);
        rejects(new Snapshot(DIM,44,List.of(),Collections.nCopies(2049,noReg)));
        rejects(new Snapshot(DIM,44,List.of(),List.of(noReg,noReg)));
        rejects(new Snapshot(DIM,44,List.of(),List.of(new Unit(UUID.randomUUID(),new Vec3(Double.NaN,0,0),0,Relation.ALLY,Kind.PIKE,null,"","","",false,false))));
        rejects(new Snapshot(DIM,44,List.of(new Style(UUID.randomUUID(),"X",new Design(16,List.of()))),List.of()));
        rejects(new Snapshot(DIM,44,List.of(new Style(UUID.randomUUID(),"X",new Design(0,Collections.nCopies(17,new Layer("bs",0))))),List.of()));
        for(Relation relation:Relation.values())for(boolean same:new boolean[]{false,true})for(boolean tracked:new boolean[]{false,true})for(boolean invisible:new boolean[]{false,true})
            assert ArmyMapData.visible(relation,same,tracked,invisible)==(same&&(relation==Relation.ALLY||tracked&&!invisible));
        View view=new View();assert view.accept(p,DIM,100);assert !view.accept(p,DIM,101);
        assert !view.accept(new Snapshot(new ResourceLocation("minecraft:the_nether"),99,List.of(),List.of()),DIM,102);
        assert view.current(DIM,200)==p && view.current(DIM,201)==null;
        assert view.current(new ResourceLocation("minecraft:the_nether"),150)==null;
        view.clear();assert view.accept(new Snapshot(DIM,1,List.of(),List.of()),DIM,1000);
        String emoji=ArmyMapData.text("§a"+"😀".repeat(80)+"\n");assert emoji.length()<=64&&!Character.isHighSurrogate(emoji.charAt(emoji.length()-1));
        assert ArmyMapData.text("§cEnemies\n\u200b").equals("Enemies");
        System.out.println("PASS full 2048-unit/128-regiment real buffer roundtrip, bounded heraldry, invalid/missing/duplicate records, observed enemy/neutral visibility, allied sharing, dimensions, out-of-order updates, stale/reconnect cleanup and safe labels");
    }
}
