package ua.empirecraft.engagement;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.*;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Only existing public server-managed paintings or the actual held banner can become a state ensign. */
public final class PaintingDesign {
    private PaintingDesign(){}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    public static Map<String,Object> inspect(ServerPlayer player,String source){
        if(source.equals("banner")){
            var item=player.getMainHandItem();if(!(item.getItem() instanceof BannerItem banner))throw new IllegalArgumentException("Візьміть державне знамено в головну руку.");
            var data=new CompoundTag();data.putInt("Base",banner.getColor().getId());var layers=new ListTag();
            for(var pair:BannerBlockEntity.createPatterns(banner.getColor(),BannerBlockEntity.getItemPatterns(item))){
                if(layers.size()>=32)throw new IllegalArgumentException("Підтримується щонайбільше 32 шари знамена.");
                var key=pair.getFirst().unwrapKey().orElseThrow();if(key.location().getPath().equals("base"))continue;
                var layer=new CompoundTag();layer.putString("Pattern",key.location().toString());layer.putInt("Color",pair.getSecond().getId());layers.add(layer);
            }
            data.put("Layers",layers);String payload=data.toString();return Map.of("kind","banner","payload",payload,"hash",hash("banner|"+payload));
        }
        if(!source.equals("painting"))throw new IllegalArgumentException("Виберіть знамено або картину Immersive Paintings.");
        EntityChoice choice=looking(player);if(choice==null)throw new IllegalArgumentException("Наведіться на картину Immersive Paintings у межах восьми блоків.");
        try {
            var id=(ResourceLocation)choice.entity.getClass().getMethod("getMotive").invoke(choice.entity);Object painting=painting(id);
            checkImage(id,painting);String checksum=painting.getClass().getField("texture").get(painting).getClass().getField("hash").get(painting.getClass().getField("texture").get(painting)).toString();
            return Map.of("kind","painting","payload",id.toString(),"hash",hash("painting|"+id+"|"+checksum));
        }catch(ReflectiveOperationException|LinkageError e){throw new IllegalArgumentException("Ця картина недоступна.",e);}
    }
    private record EntityChoice(net.minecraft.world.entity.Entity entity,double distance){}
    private static EntityChoice looking(ServerPlayer player){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(8));EntityChoice best=null;
        for(var e:player.serverLevel().getAllEntities())if(e.getClass().getName().startsWith("immersive_paintings.entity.Immersive")&&hasMotive(e)){
            AABB box=e.getBoundingBox();var ship=VSGameUtilsKt.getLoadedShipManagingPos(e.level(),e.getX(),e.getY(),e.getZ());
            if(ship!=null){AABB world=null;for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ}){
                Vec3 p=CombatRaycast.worldPoint(ship,new Vec3(x,y,z));world=world==null?new AABB(p,p):world.minmax(new AABB(p,p));}box=world;
            }
            var hit=box.inflate(.05).clip(eye,end);if(hit.isEmpty())continue;double distance=eye.distanceToSqr(hit.get());if(best==null||distance<best.distance)best=new EntityChoice(e,distance);
        }return best;
    }
    private static boolean hasMotive(Object object){try{object.getClass().getMethod("getMotive");return true;}catch(NoSuchMethodException e){return false;}}
    private static Object painting(ResourceLocation id)throws ReflectiveOperationException{return Class.forName("immersive_paintings.resources.ServerPaintingManager").getMethod("getPainting",ResourceLocation.class).invoke(null,id);}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void checkImage(ResourceLocation id,Object painting)throws ReflectiveOperationException {
        if(painting==null||painting.getClass().getField("hidden").getBoolean(painting)||painting.getClass().getField("graffiti").getBoolean(painting))throw new IllegalArgumentException("Використайте публічну картину без графіті.");
        var type=(Class<? extends Enum>)Class.forName("immersive_paintings.resources.Painting$Type");Object full=Enum.valueOf(type,"FULL");
        var image=(Optional<?>)Class.forName("immersive_paintings.resources.ServerPaintingManager").getMethod("getImage",ResourceLocation.class,type).invoke(null,id,full);
        if(image.isEmpty())throw new IllegalArgumentException("Зображення картини відсутнє.");Object bytes=image.get();var c=bytes.getClass();
        int width=((Number)c.getMethod("getWidth").invoke(bytes)).intValue(),height=((Number)c.getMethod("getHeight").invoke(bytes)).intValue();
        if(width<1||height<1||(long)width*height>16777216)throw new IllegalArgumentException("Неприпустимі розміри зображення.");
        var pixel=c.getMethod("getARGB",int.class,int.class);int opaque=0,total=0;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){int argb=((Number)pixel.invoke(bytes,Math.min(width-1,x*width/16),Math.min(height-1,y*height/16))).intValue();total++;if((argb>>>24)>=192)opaque++;}
        if(opaque*4<total*3)throw new IllegalArgumentException("Полотно морського прапора має бути видимим і непрозорим.");
    }
    public static boolean valid(String kind,String payload,String checksum){
        if(payload.length()>8192||checksum.length()!=64)return false;
        try {
            if(kind.equals("banner")){var tag=TagParser.parseTag(payload);if(tag.getInt("Base")<0||tag.getInt("Base")>15||tag.getList("Layers",10).size()>32)return false;return hash("banner|"+payload).equals(checksum);}
            if(!kind.equals("painting"))return false;var id=ResourceLocation.tryParse(payload);if(id==null)return false;var painting=painting(id);if(painting==null||painting.getClass().getField("hidden").getBoolean(painting))return false;
            Object texture=painting.getClass().getField("texture").get(painting);String actual=texture.getClass().getField("hash").get(texture).toString();return hash("painting|"+id+"|"+actual).equals(checksum);
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
