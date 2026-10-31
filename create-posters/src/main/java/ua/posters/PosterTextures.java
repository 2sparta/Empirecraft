package ua.posters;
import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
public final class PosterTextures {
    private record Texture(ResourceLocation id,int pixels){}
    private static final int MAX_PIXELS=16*1024*1024;
    private static final Map<CompoundTag,Texture> CACHE=new LinkedHashMap<>(32,.75f,true);
    private static int allocated;
    public static ResourceLocation texture(CompoundTag data){
        Texture found=CACHE.get(data);if(found!=null)return found.id;
        int w=DrawingData.textureWidth(data),h=DrawingData.textureHeight(data);int[] pixels=DrawingData.pixels(data);
        NativeImage image=new NativeImage(w,h,false);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rgb=pixels[x+y*w];image.m_84988_(x,y,(rgb&0xff00ff00)|((rgb>>16)&255)|((rgb&255)<<16));
        }
        ResourceLocation id=Minecraft.m_91087_().m_91097_().m_118490_("create_poster",new DynamicTexture(image));
        CACHE.put(data.m_6426_(),new Texture(id,w*h));allocated+=w*h;
        while(CACHE.size()>32||allocated>MAX_PIXELS){var it=CACHE.entrySet().iterator();var old=it.next();Minecraft.m_91087_().m_91097_().m_118513_(old.getValue().id);allocated-=old.getValue().pixels;it.remove();}
        return id;
    }
    public static void clear(){for(var t:CACHE.values())Minecraft.m_91087_().m_91097_().m_118513_(t.id);CACHE.clear();allocated=0;}
}
