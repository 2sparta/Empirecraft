package ua.empirecraft.townymap.client;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Native Minecraft item rendering preserves resource-pack banner patterns and player skull skins. */
final class HeraldryIcon {
    private static final Map<String,ItemStack> CACHE=new LinkedHashMap<>();
    static boolean flag(Object graphics,JsonObject flag,int x,int y,int scale) {
        if(flag!=null&&flag.has("kind")&&"painting".equals(flag.get("kind").getAsString()))return painting(graphics,flag,x,y,scale);
        if(flag==null || !flag.has("kind") || !"banner".equals(flag.get("kind").getAsString()))return false;
        try {
            String payload=flag.get("payload").getAsString();
            ItemStack item=CACHE.computeIfAbsent(payload,key->{
                try {
                    var source=TagParser.m_129359_(key);int base=source.m_128451_("Base");
                    var dye=DyeColor.m_41053_(Math.max(0,Math.min(15,base)));
                    var result=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft",dye.m_41065_()+"_banner")));
                    var layers=source.m_128437_("Layers",10);var patterns=new ListTag();
                    for(int i=0;i<Math.min(32,layers.size());i++) {
                        var layer=layers.m_128728_(i);var pattern=BuiltInRegistries.f_256878_.m_7745_(new ResourceLocation(layer.m_128461_("Pattern")));
                        if(pattern==null)continue;var row=new CompoundTag();row.m_128359_("Pattern",pattern.m_58579_());row.m_128405_("Color",layer.m_128451_("Color"));patterns.add(row);
                    }
                    var block=new CompoundTag();block.m_128365_("Patterns",patterns);var tag=new CompoundTag();tag.m_128365_("BlockEntityTag",block);result.m_41751_(tag);return result;
                }catch(Exception invalid){throw new IllegalArgumentException("Invalid observed banner",invalid);}
            });
            draw(graphics,item,x,y,scale);trim();return true;
        }catch(RuntimeException invalid){return false;}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static boolean painting(Object graphics,JsonObject flag,int x,int y,int scale) {
        try {
            var id=new ResourceLocation(flag.get("payload").getAsString());
            Object type=Enum.valueOf((Class)ClientAccess.type("immersive_paintings.resources.Painting$Type"),"THUMBNAIL");
            Object texture=ClientAccess.call(ClientAccess.type("immersive_paintings.resources.ClientPaintingManager"),"getPaintingTexture",id,type);
            Object image=ClientAccess.field(texture,"image"),location=ClientAccess.field(texture,"textureIdentifier");
            if(image==null||location==null)return false;
            int width=((Number)ClientAccess.call(image,"getWidth")).intValue(),height=((Number)ClientAccess.call(image,"getHeight")).intValue();
            if(width<1||height<1)return false;
            float factor=Math.min(16f*scale/width,16f*scale/height);
            Object pose=ClientAccess.call(graphics,"m_280168_");ClientAccess.call(pose,"m_85836_");
            try{ClientAccess.call(pose,"m_85837_",(double)x,(double)y,0d);ClientAccess.call(pose,"m_85841_",factor,factor,1f);
                ClientAccess.call(graphics,"m_280163_",location,0,0,0f,0f,width,height,width,height);
            }finally{ClientAccess.call(pose,"m_85849_");}
            return true;
        }catch(RuntimeException unavailable){return false;}
    }
    static void head(Object graphics,String uuid,String name,int x,int y) {
        try {
            String key="head/"+uuid;ItemStack head=CACHE.computeIfAbsent(key,k->{
                var item=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft","player_head")));
                var owner=new CompoundTag();owner.m_128362_("Id",UUID.fromString(uuid));owner.m_128359_("Name",name);
                var tag=new CompoundTag();tag.m_128365_("SkullOwner",owner);item.m_41751_(tag);return item;
            });
            draw(graphics,head,x,y,1);trim();
        }catch(RuntimeException unavailable){ClientAccess.text(graphics,name.isEmpty()?"?":name.substring(0,1),x+4,y+4,0xe9d8b3);}
    }
    private static void draw(Object graphics,ItemStack item,int x,int y,int scale) {
        Object pose=ClientAccess.call(graphics,"m_280168_");ClientAccess.call(pose,"m_85836_");
        try{ClientAccess.call(pose,"m_85837_",(double)x,(double)y,0d);ClientAccess.call(pose,"m_85841_",(float)scale,(float)scale,1f);
            ClientAccess.call(graphics,"m_280480_",item,0,0);
        }finally{ClientAccess.call(pose,"m_85849_");}
    }
    private static void trim(){while(CACHE.size()>128)CACHE.remove(CACHE.keySet().iterator().next());}
    static void clear(){CACHE.clear();}
}
