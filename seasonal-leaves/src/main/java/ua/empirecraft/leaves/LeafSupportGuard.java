package ua.empirecraft.leaves;

import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded player-footprint cache; no entity scan for each leaf removal. */
@Mod.EventBusSubscriber(modid=SeasonalLeaves.ID)
public final class LeafSupportGuard {
    private static final Map<ServerLevel,Long2LongOpenHashMap> WORLDS=new IdentityHashMap<>();
    private static final Map<net.minecraft.world.entity.player.Player,Footprint> FEET=new WeakHashMap<>();
    private record Footprint(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,int minX,int maxX,int minZ,int maxZ,int feet) {}
    private LeafSupportGuard() {}
    public static boolean protectedAt(ServerLevel level,BlockPos pos) {
        if(!AtmosphereConfig.PROTECT.get())return false;
        var map=WORLDS.get(level);return map!=null&&map.get(pos.asLong())>level.getGameTime();
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if(event.getEntity() instanceof net.minecraft.world.entity.player.Player player&&player.level() instanceof ServerLevel level&&AtmosphereConfig.PROTECT.get())remember(level,player);
    }
    @SubscribeEvent public static void moved(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player.level() instanceof ServerLevel level))return;
        track(level,event.player,false);
    }
    private static void track(ServerLevel level,net.minecraft.world.entity.player.Player player,boolean refresh) {
        if(player.isSpectator()||!AtmosphereConfig.PROTECT.get()) {FEET.remove(player);return;}
        var box=player.getBoundingBox();int minX=(int)Math.floor(box.minX-0.08),maxX=(int)Math.floor(box.maxX+0.08);
        int minZ=(int)Math.floor(box.minZ-0.08),maxZ=(int)Math.floor(box.maxZ+0.08),feet=(int)Math.floor(player.getY());
        var old=FEET.get(player);
        if(refresh||old==null||!old.dimension.equals(level.dimension())||old.minX!=minX||old.maxX!=maxX||old.minZ!=minZ||old.maxZ!=maxZ||old.feet!=feet) {
            FEET.put(player,new Footprint(level.dimension(),minX,maxX,minZ,maxZ,feet));
            remember(level,player);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.START||!(event.level instanceof ServerLevel level))return;
        var map=WORLDS.computeIfAbsent(level,k->new Long2LongOpenHashMap());
        long now=level.getGameTime();boolean refresh=now%10==0;
        if(refresh)map.long2LongEntrySet().removeIf(e->e.getLongValue()<=now);
        if(!AtmosphereConfig.PROTECT.get())return;
        // Observe movement before the level can shed leaves, even when player END ticks run later.
        for(var player:level.players())track(level,player,refresh);
    }
    private static void remember(ServerLevel level,net.minecraft.world.entity.player.Player player) {
            if(player.isSpectator())return;
            var map=WORLDS.computeIfAbsent(level,k->new Long2LongOpenHashMap());long now=level.getGameTime();
            var box=player.getBoundingBox();int feet=(int)Math.floor(player.getY());
            for(int x=(int)Math.floor(box.minX-0.08);x<=(int)Math.floor(box.maxX+0.08);x++)
                for(int z=(int)Math.floor(box.minZ-0.08);z<=(int)Math.floor(box.maxZ+0.08);z++)
                    for(int y=feet-3;y<=feet;y++) {
                        BlockPos pos=new BlockPos(x,y,z);if(!level.hasChunkAt(pos))continue;
                        BlockState state=level.getBlockState(pos);
                        if(SeasonalLeaves.isLeaves(state))map.put(pos.asLong(),now+60);
                    }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {WORLDS.clear();FEET.clear();}
}
