package ua.empirecraft.townymap.client;

import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RenderGuiEvent;
import ua.empirecraft.townymap.*;

/** Contextual spyglass input. Server data and personal logs never cross connections. */
public final class ShipReconClient {
    private static final Gson JSON=new Gson();
    private static final ShipObservation.Pages pages=new ShipObservation.Pages();
    private static Connection connection;
    private static long focus=-1,lastKey;
    private static List<ShipObservation> contacts=List.of();
    private static int ticks,iconCursor;
    private static boolean accept(Connection source){
        var mc=Minecraft.getInstance();if(mc.getConnection()==null||mc.getConnection().getConnection()!=source)return false;
        StrategyMapUi.attachConnection(source);
        if(connection!=source){pages.clear();connection=source;focus=-1;contacts=List.of();}
        return true;
    }
    public static void contacts(String json,long aimed,long batch,int part,int total,Connection source){
        if(!accept(source))return;
        try{List<ShipObservation> list=JSON.fromJson(json,new TypeToken<List<ShipObservation>>(){}.getType());
            pages.accept(batch,part,total,list).ifPresent(rows->{contacts=rows;ShipMapLayer.contacts(rows);});
        }catch(RuntimeException invalid){System.getLogger("empirecraft_townymap").log(System.Logger.Level.WARNING,"Invalid nearby ship page");}
    }
    public static void icon(long ship,ShipIcon image,Connection source){if(accept(source)){ShipIconCache.put(ship,image);ShipMapLayer.refresh();}}
    public static long focus(){return focus;}
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getConnection()==null){focus=-1;return;}
        if(++ticks%20==0&&StrategyNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())){
            var rows=ShipMapLayer.view();int requested=0;
            for(int i=0;i<rows.size();i++){var row=rows.get((iconCursor+i)%rows.size());if(ShipIconCache.image(row.ship(),row.icon())==null){StrategyNetwork.CHANNEL.sendToServer(new StrategyNetwork.IconRequest(row.ship()));if(++requested==4)break;}}
            if(!rows.isEmpty())iconCursor=(iconCursor+4)%rows.size();
        }
        if(!mc.player.isUsingItem()||!mc.player.getUseItem().is(Items.SPYGLASS)){focus=-1;return;}
        if(ticks%2!=0)return;
        double range=16.0*Math.min(32,mc.options.getEffectiveRenderDistance());
        var eye=mc.player.getEyePosition();var hit=ShipSight.first(mc.level,mc.player,eye,eye.add(mc.player.getViewVector(1).scale(range)),true,state->false);
        focus=hit==null||hit.state()==null||hit.ship()==null?-1:hit.ship().getId();
    }
    public static boolean key(int key,int scan,int action,int modifiers){
        if(!ShipReconKeys.SAVE.matches(key,scan)||action==0||(modifiers&14)!=0)return false;
        if(!ready())return false;if(action==1)save();return true;
    }
    public static boolean mouse(int button,int action,int modifiers){if(!ShipReconKeys.SAVE.matchesMouse(button)||action==0||!ready())return false;if(action==1)save();return true;}
    private static boolean ready(){var mc=Minecraft.getInstance();return mc.screen==null&&mc.player!=null&&mc.player.isUsingItem()&&mc.player.getUseItem().is(Items.SPYGLASS)&&focus>=0&&mc.getConnection()!=null&&StrategyNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection());}
    private static void save(){long now=System.nanoTime();if(now-lastKey<400_000_000L)return;lastKey=now;StrategyNetwork.CHANNEL.sendToServer(new StrategyNetwork.Save(focus));}
    public static void hud(RenderGuiEvent.Post event){
        if(!ready())return;var mc=Minecraft.getInstance();if(mc.options.hideGui)return;
        var row=contacts.stream().filter(r->r.ship()==focus).findFirst().orElse(null);
        int width=event.getWindow().getGuiScaledWidth(),height=event.getWindow().getGuiScaledHeight(),w=Math.min(288,width-16),x=(width-w)/2,y=height-78;var g=event.getGuiGraphics();
        g.fill(x,y,x+w,y+62,0xd91a262a);g.fill(x,y,x+w,y+1,0xffbfa371);
        String name=row==null?"Корабель #"+focus:row.name();g.drawString(mc.font,mc.font.plainSubstrByWidth(name,w-16),x+8,y+8,0xffe4cd9b,false);
        g.drawString(mc.font,row==null?"Спостереження через підзорну трубу":ShipMapLayer.motion(row),x+8,y+24,0xffaacfd0,false);
        var hint=net.minecraft.network.chat.Component.literal("[").append(ShipReconKeys.SAVE.getTranslatedKeyMessage()).append("] Зафіксувати на мапі");
        g.drawString(mc.font,hint,x+8,y+43,0xfff0d58f,false);
    }
    public static void clear(){pages.clear();connection=null;focus=-1;contacts=List.of();lastKey=0;ticks=0;ShipIconCache.clear();}
    private ShipReconClient(){}
}
