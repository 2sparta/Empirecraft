package ua.empirecraft.townymap.client;

import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import ua.empirecraft.townymap.ShipIcon;

/** A bounded image bank shared by the minimap and full map; no per-frame image allocation. */
public final class ShipIconCache {
    private static final Map<String,NativeImage> images=new LinkedHashMap<>(32,.75f,true);
    private static final Map<Long,String> aliases=new LinkedHashMap<>(32,.75f,true);
    public static void put(long ship,ShipIcon data){
        if(!images.containsKey(data.key())){
            if(images.size()>=512){
                var protectedKeys=new HashSet<String>();for(var row:ShipMapLayer.view())protectedKeys.add(aliases.getOrDefault(row.ship(),row.icon()));
                for(var it=images.entrySet().iterator();it.hasNext();){var old=it.next();if(!protectedKeys.contains(old.getKey())){old.getValue().close();it.remove();break;}}
                if(images.size()>=512)return;
            }
            var image=new NativeImage(ShipIcon.SIDE,ShipIcon.SIDE,false);
            for(int z=0;z<ShipIcon.SIDE;z++)for(int x=0;x<ShipIcon.SIDE;x++){
                int p=data.pixel(x,z);image.setPixelRGBA(x,z,(p&0xff00ff00)|((p>>>16)&255)|((p&255)<<16));
            }
            images.put(data.key(),image);
        }
        aliases.put(ship,data.key());while(aliases.size()>2048)aliases.remove(aliases.keySet().iterator().next());
    }
    public static NativeImage image(long ship,String key){var latest=images.get(aliases.get(ship));return latest!=null?latest:images.get(key);}
    public static void clear(){images.values().forEach(NativeImage::close);images.clear();aliases.clear();}
    private ShipIconCache(){}
}
