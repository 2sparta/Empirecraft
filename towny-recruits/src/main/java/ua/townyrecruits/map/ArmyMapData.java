package ua.townyrecruits.map;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.RegimentalBanners;

/** A bounded current view, never the server's military registry or a list of unloaded positions. */
public final class ArmyMapData {
    public static final int MAX_UNITS=2048, MAX_STYLES=128;
    public enum Relation { ALLY(0x43d66f), NEUTRAL(0xf2ce4b), ENEMY(0xef5350); public final int color; Relation(int color){this.color=color;} }
    public enum Kind { INFANTRY, PIKE, RANGED, CAVALRY, MOUNTED_RANGED }
    public record Style(UUID regiment,String name,RegimentalBanners.Design banner) {}
    public record Unit(UUID id,Vec3 position,float yaw,Relation relation,Kind kind,UUID regiment,String nation,String group,String commander,boolean bearer,boolean routing) {}
    public record Snapshot(ResourceLocation dimension,long sequence,List<Style> styles,List<Unit> units) {}
    private ArmyMapData() {}
    public static String text(String input) {
        if(input==null)return "";
        String clean=input.replaceAll("§.","").replaceAll("[\\p{Cntrl}\\p{Cf}]","");
        if(clean.length()<=64)return clean;
        return clean.substring(0,Character.isHighSurrogate(clean.charAt(63))?63:64);
    }
    public static boolean visible(Relation relation,boolean sameDimension,boolean tracked,boolean invisible) {
        return sameDimension && (relation==Relation.ALLY || tracked && !invisible);
    }
    public static final class Codec {
        private Codec() {}
        public static void write(Snapshot p,FriendlyByteBuf b) {
            b.m_130072_(p.dimension().toString(),128);b.writeLong(p.sequence());b.writeShort(p.styles().size());
            for(Style s:p.styles()) { b.m_130077_(s.regiment());b.m_130072_(s.name(),64);b.writeByte(s.banner().base());b.writeByte(s.banner().layers().size());
                for(var layer:s.banner().layers()){b.m_130072_(layer.pattern(),48);b.writeByte(layer.color());} }
            b.writeShort(p.units().size());
            for(Unit u:p.units()) {
                b.m_130077_(u.id());b.writeDouble(u.position().f_82479_);b.writeDouble(u.position().f_82480_);b.writeDouble(u.position().f_82481_);b.writeFloat(u.yaw());
                b.writeByte(u.relation().ordinal());b.writeByte(u.kind().ordinal());b.writeBoolean(u.regiment()!=null);if(u.regiment()!=null)b.m_130077_(u.regiment());
                b.m_130072_(u.nation(),64);b.m_130072_(u.group(),64);b.m_130072_(u.commander(),64);b.writeBoolean(u.bearer());b.writeBoolean(u.routing());
            }
        }
        public static Snapshot read(FriendlyByteBuf b) {
            ResourceLocation dim=new ResourceLocation(b.m_130136_(128));long sequence=b.readLong();
            int count=bound(b.readUnsignedShort(),MAX_STYLES);List<Style> styles=new ArrayList<>();Set<UUID> ids=new HashSet<>();
            for(int i=0;i<count;i++) {
                UUID id=b.m_130259_();if(!ids.add(id))throw new IllegalArgumentException("Duplicate regiment style");String name=b.m_130136_(64);
                int base=bound(b.readUnsignedByte(),15), size=bound(b.readUnsignedByte(),RegimentalBanners.MAX_LAYERS);List<RegimentalBanners.Layer> layers=new ArrayList<>();
                for(int j=0;j<size;j++){var layer=new RegimentalBanners.Layer(b.m_130136_(48),bound(b.readUnsignedByte(),15));if(!layer.sane())throw new IllegalArgumentException("Invalid banner layer");layers.add(layer);}
                styles.add(new Style(id,name,new RegimentalBanners.Design(base,List.copyOf(layers))));
            }
            count=bound(b.readUnsignedShort(),MAX_UNITS);List<Unit> units=new ArrayList<>();Set<UUID> soldiers=new HashSet<>();
            for(int i=0;i<count;i++) {
                UUID id=b.m_130259_();if(!soldiers.add(id))throw new IllegalArgumentException("Duplicate soldier");
                double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();float yaw=b.readFloat();
                if(!Double.isFinite(x+y+z)||Math.abs(x)>30000000||Math.abs(z)>30000000||Math.abs(y)>4096||!Float.isFinite(yaw))throw new IllegalArgumentException("Invalid map location");
                Relation relation=Relation.values()[bound(b.readUnsignedByte(),Relation.values().length-1)];Kind kind=Kind.values()[bound(b.readUnsignedByte(),Kind.values().length-1)];
                UUID regiment=b.readBoolean()?b.m_130259_():null;if(regiment!=null&&!ids.contains(regiment))throw new IllegalArgumentException("Missing regiment style");
                units.add(new Unit(id,new Vec3(x,y,z),yaw,relation,kind,regiment,b.m_130136_(64),b.m_130136_(64),b.m_130136_(64),b.readBoolean(),b.readBoolean()));
            }
            return new Snapshot(dim,sequence,List.copyOf(styles),List.copyOf(units));
        }
        private static int bound(int value,int max){if(value<0||value>max)throw new IllegalArgumentException("Oversized army map packet");return value;}
    }
    /** Lifecycle independent of JourneyMap: delayed dimensions, TTLs and reconnects cannot leave ghost armies. */
    public static final class View {
        private Snapshot snapshot;private long received;private long sequence=-1;
        public boolean accept(Snapshot next,ResourceLocation dimension,long tick) {
            if(!next.dimension().equals(dimension)||next.sequence()<=sequence)return false;
            snapshot=next;sequence=next.sequence();received=tick;return true;
        }
        public Snapshot current(ResourceLocation dimension,long tick){return snapshot!=null&&snapshot.dimension().equals(dimension)&&tick-received<=100?snapshot:null;}
        public void clear(){snapshot=null;sequence=-1;received=0;}
    }
}
