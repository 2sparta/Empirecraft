package ua.posters;

import java.util.*;
import net.minecraft.nbt.*;

public final class DrawingData {
    public static CompoundTag make(String title,int sx,int sy,int sz,List<Projection.View> views){
        CompoundTag tag=new CompoundTag();tag.m_128405_("Version",1);tag.m_128359_("Title",title);
        tag.m_128405_("X",sx);tag.m_128405_("Y",sy);tag.m_128405_("Z",sz);
        ListTag list=new ListTag();
        for(var v:views){CompoundTag t=new CompoundTag();t.m_128405_("Direction",v.direction());t.m_128405_("Width",v.width());t.m_128405_("Height",v.height());t.m_128382_("Ink",v.ink());list.add(t);}
        tag.m_128365_("Views",list);return tag;
    }
    public static List<Projection.View> views(CompoundTag tag){
        List<Projection.View> out=new ArrayList<>();ListTag list=tag.m_128437_("Views",10);int seen=0;
        if(list.size()>6)return out;
        for(int i=0;i<list.size();i++){
            CompoundTag t=list.m_128728_(i);int d=t.m_128451_("Direction");byte[] ink=t.m_128463_("Ink");
            if(d<0||d>5||(seen&(1<<d))!=0||ink.length!=Projection.BYTES)return List.of();seen|=1<<d;
            int w=t.m_128451_("Width"),h=t.m_128451_("Height");
            if(w<1||h<1||w>256||h>256)return List.of();out.add(new Projection.View(d,w,h,ink));
        }
        return out;
    }
    public static boolean valid(CompoundTag tag){return tag.m_128451_("Version")==1&&tag.m_128461_("Title").length()<=64&&!views(tag).isEmpty();}
    public static int[] pixels(CompoundTag tag){return Paper.compose(tag.m_128461_("Title"),tag.m_128451_("X"),tag.m_128451_("Y"),tag.m_128451_("Z"),views(tag));}
}
