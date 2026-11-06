package ua.townyrecruits.client;

import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import ua.townyrecruits.map.ArmyMapData.*;

/** Render-thread texture cache, bounded across changing heraldry and released on disconnect/reload. */
public final class ArmyMapTextures implements JourneyMapArmyLayer.Icons {
    private final LinkedHashMap<Object,NativeImage> textures=new LinkedHashMap<>(16,0.75f,true);
    private final Map<String,Optional<BufferedImage>> masks=new HashMap<>();
    private final int[] colors=new int[16];
    private record FlagKey(Style style,Relation relation) {}
    public ArmyMapTextures(){for(int i=0;i<16;i++){float[] c=DyeColor.m_41053_(i).m_41068_();colors[i]=((int)(c[0]*255)<<16)|((int)(c[1]*255)<<8)|(int)(c[2]*255);}}
    private BufferedImage mask(String hash) {
        var known=BannerPattern.m_222700_(hash);if(known==null||known.m_203543_().isEmpty())return null;
        return masks.computeIfAbsent(hash,id->{
            try {
                var holder=BannerPattern.m_222700_(hash);if(holder==null||holder.m_203543_().isEmpty())return Optional.empty();
                ResourceLocation sprite=BannerPattern.m_222697_(holder.m_203543_().get(),true);
                ResourceLocation location=new ResourceLocation(sprite.m_135827_(),"textures/"+sprite.m_135815_()+".png");
                var resource=Minecraft.m_91087_().m_91098_().m_213713_(location);if(resource.isEmpty())return Optional.empty();
                try(var in=resource.get().m_215507_()){return Optional.ofNullable(ImageIO.read(in));}
            } catch(Exception ignored){return Optional.empty();}
        }).orElse(null);
    }
    private NativeImage texture(Object key,java.util.function.Supplier<BufferedImage> raster) {
        NativeImage cached=textures.get(key);if(cached!=null)return cached;
        BufferedImage source=raster.get();NativeImage image=new NativeImage(source.getWidth(),source.getHeight(),true);
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){int c=source.getRGB(x,y);image.m_84988_(x,y,(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16));}
        textures.put(key,image);
        while(textures.size()>192){var it=textures.entrySet().iterator();var entry=it.next();entry.getValue().close();it.remove();}
        return image;
    }
    @Override public MapImage unit(Unit u){var image=texture(List.of(u.kind(),u.relation(),u.routing()),()->ArmyMapRaster.unit(u.kind(),u.relation(),u.routing()));return new MapImage(image).setDisplayWidth(9).setDisplayHeight(9).centerAnchors().setRotation(Math.floorMod(Math.round(u.yaw())+180,360));}
    @Override public MapImage flag(Style s,Relation relation){var image=texture(new FlagKey(s,relation),()->ArmyMapRaster.flag(s.banner(),relation,colors,this::mask));return new MapImage(image).setDisplayWidth(22).setDisplayHeight(26).setAnchorX(5).setAnchorY(24);}
    @Override public void clear(){for(var image:textures.values())image.close();textures.clear();masks.clear();}
}
