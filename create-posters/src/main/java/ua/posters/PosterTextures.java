package ua.posters;

import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PosterTextures {
    private static final Map<CompoundTag,ResourceLocation> CACHE=new LinkedHashMap<>(32,.75f,true);
    public static ResourceLocation texture(CompoundTag data){
        ResourceLocation found=CACHE.get(data);if(found!=null)return found;
        int[] pixels=DrawingData.pixels(data);NativeImage image=new NativeImage(Paper.W,Paper.H,false);
        for(int y=0;y<Paper.H;y++)for(int x=0;x<Paper.W;x++){
            int rgb=pixels[x+y*Paper.W];image.m_84988_(x,y,(rgb&0xff00ff00)|((rgb>>16)&255)|((rgb&255)<<16));
        }
        ResourceLocation id=Minecraft.m_91087_().m_91097_().m_118490_("create_poster",new DynamicTexture(image));CACHE.put(data.m_6426_(),id);
        if(CACHE.size()>32){var it=CACHE.entrySet().iterator();var old=it.next();Minecraft.m_91087_().m_91097_().m_118513_(old.getValue());it.remove();}
        return id;
    }
    public static void clear(){for(var id:CACHE.values())Minecraft.m_91087_().m_91097_().m_118513_(id);CACHE.clear();}
}
