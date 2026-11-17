package ua.empirecraft.earth;
import java.util.*;
import journeymap.api.v2.common.*;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.*;
import journeymap.api.v2.server.overlay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Server-authoritative overlay, bounded to loaded terrain. No client plugin or remote chunk generation. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class EarthMap implements IServerPlugin {
    private final Map<UUID,String> modes=new HashMap<>();
    private final Map<UUID,String> revisions=new HashMap<>();
    private IServerOverlayAPI api;private int ticks,cursor;
    public String getModId(){return EarthMod.ID;}
    public void initialize(IServerAPI serverApi){
        api=serverApi.getOverlayApi();var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::commands);bus.addListener(this::tick);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{modes.remove(e.getEntity().getUUID());revisions.remove(e.getEntity().getUUID());});
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{if(e.getEntity() instanceof ServerPlayer p){api.clearAll(p,EarthMod.ID);revisions.remove(p.getUUID());}});
        bus.addListener((ServerStoppedEvent e)->{modes.clear();revisions.clear();ticks=0;cursor=0;});
    }
    private void commands(RegisterCommandsEvent e){
        var root=net.minecraft.commands.Commands.literal("empireearthmap");
        for(String mode:List.of("climate","ecology","marine","off"))root.then(net.minecraft.commands.Commands.literal(mode).executes(ctx->{
            var p=ctx.getSource().getPlayerOrException();modes.put(p.getUUID(),mode);revisions.remove(p.getUUID());api.clearAll(p,EarthMod.ID);
            ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Earth map: "+mode+". Loaded terrain only; regional approximations."),false);return 1;
        }));
        e.getDispatcher().register(root);
    }
    private void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||++ticks%20!=0)return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;
        var p=players.get(Math.floorMod(cursor++,players.size()));String mode=modes.getOrDefault(p.getUUID(),"off");
        if(mode.equals("off")||!EarthMod.enabled(p.serverLevel()))return;
        String revision=p.serverLevel().dimension().location()+":"+p.chunkPosition()+":"+mode+":"+System.identityHashCode(EarthMod.data)+":"+(ticks/200);
        if(revision.equals(revisions.get(p.getUUID())))return;
        try{render(p,mode);revisions.put(p.getUUID(),revision);}catch(RuntimeException ex){EarthMod.LOG.warn("Earth JourneyMap overlay failed; retrying",ex);}
    }
    private void render(ServerPlayer player,String layer){
        api.clearAll(player,EarthMod.ID);var l=player.serverLevel();var d=EarthMod.data;
        int cx=player.chunkPosition().x,cz=player.chunkPosition().z;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            var chunk=l.getChunkSource().getChunkNow(cx+dx,cz+dz);if(chunk==null)continue;
            Map<String,List<OverlayPolygon>> groups=new TreeMap<>();Map<String,String> labels=new HashMap<>();
            int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
            for(int x=0;x<16;x+=4)for(int z=0;z<16;z+=4){
                int y=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x+2,z+2);
                boolean water=chunk.getFluidState(new BlockPos(ox+x+2,y+1,oz+z+2)).is(FluidTags.WATER);
                if(layer.equals("marine")&&!water||layer.equals("ecology")&&water)continue;
                var point=d.coordinates(ox+x+2,oz+z+2);var f=d.at(layer,point);
                String key=f!=null?f.id():layer.equals("climate")?d.climate(point).id():layer.equals("ecology")?d.zone(point):"unmapped_water";
                String label=f!=null?f.label():key;
                if(layer.equals("marine")){
                    // River masks override broad ocean polygons.
                    String habitat=EarthMod.habitat(l,new BlockPos(ox+x+2,y+1,oz+z+2));
                    if(!habitat.equals("marine")){key="freshwater";label="Прісна вода";}
                }
                labels.put(key,label);
                var points=new OverlayPoints(List.of(BlockPos.asLong(ox+x,64,oz+z),BlockPos.asLong(ox+x+4,64,oz+z),BlockPos.asLong(ox+x+4,64,oz+z+4),BlockPos.asLong(ox+x,64,oz+z+4)));
                groups.computeIfAbsent(key,k->new ArrayList<>()).add(new OverlayPolygon(points,List.of()));
            }
            for(var entry:groups.entrySet()){
                int color=0x404040|(entry.getKey().hashCode()&0xbfbfbf);
                var props=new OverlayShapeProps(color,.28f,color,0,.65f,900,UIState.FULLSCREEN_ZOOM_MIN,UIState.ZOOM_IN_MAX,
                    EnumSet.of(Context.UI.Fullscreen),EnumSet.allOf(Context.MapType.class),labels.get(entry.getKey()),"Regional approximation • "+layer+" • coastline sampled every 4 blocks");
                api.show(player,EarthMod.ID,new ServerPolygon("earth/"+chunk.getPos()+"/"+entry.getKey(),l.dimension(),entry.getValue(),props));
            }
        }
    }
}
