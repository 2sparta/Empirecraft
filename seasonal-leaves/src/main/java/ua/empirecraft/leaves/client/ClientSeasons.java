package ua.empirecraft.leaves.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;
import ua.empirecraft.leaves.*;

@Mod.EventBusSubscriber(modid=SeasonalLeaves.ID,value=Dist.CLIENT)
public final class ClientSeasons {
    public static final int UNTINTED=10077;
    private static final Map<BakedModel,BakedModel> MODELS=new ConcurrentHashMap<>();
    private static final Map<Block,Integer> SPECIES=new ConcurrentHashMap<>();
    private static final Set<Long> TOUCHED=ConcurrentHashMap.newKeySet();
    private static final Set<Long> REFRESH=new LinkedHashSet<>();
    private static volatile double phase=3;
    private static volatile boolean active;
    private static ClientLevel previous;
    private static volatile ClientContext context;
    private static int bucket=-1;
    private ClientSeasons() {}
    public static void attach(ClientContext client) {context=client;}
    public static BakedModel wrap(BakedModel model) {return MODELS.computeIfAbsent(model,SeasonalLeafModel::new);}
    public static void clearModels() {MODELS.clear();SPECIES.clear();}
    public static boolean enabled() {return active;}
    public static int color(BlockState state,BlockPos pos,int base,int tint) {
        int original=tint==UNTINTED?0xffffff:base;
        if(pos==null)return original;
        // Only meshed leaf sections are refreshed. The bound prevents wandering from growing this forever.
        if(TOUCHED.size()<8192)TOUCHED.add(SectionPos.asLong(pos));
        if(!active||!AtmosphereConfig.COLORS.get())return original;
        int species=SPECIES.computeIfAbsent(state.getBlock(),b->BuiltInRegistries.BLOCK.getKey(b).hashCode());
        return SeasonVisualPolicy.foliage(original,pos.getX(),pos.getY(),pos.getZ(),species,phase);
    }
    public static float[] sky(float r,float g,float b,float rain) {
        return enabled()&&AtmosphereConfig.SKY.get()?SeasonVisualPolicy.sky(r,g,b,phase,rain):new float[]{r,g,b};
    }
    @SubscribeEvent public static void loaded(LevelEvent.Load event) {
        if(event.getLevel() instanceof ClientLevel level) {
            previous=level;active=false;phase=3;bucket=-1;REFRESH.clear();TOUCHED.clear();
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=context;if(mc==null)return;ClientLevel level=mc.empirecraft$level();
        if(level!=previous) {previous=level;active=false;phase=3;bucket=-1;REFRESH.clear();TOUCHED.clear();}
        if(level==null)return;
        if(bucket==-1||level.getGameTime()%20==0) {
            boolean next=sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(level.dimension());
            var s=SeasonHelper.getSeasonState(level);
            double nextPhase=SeasonVisualPolicy.phase(s.getSeasonCycleTicks(),s.getSubSeasonDuration());
            int nextBucket=(next?(int)(nextPhase*64):-1)*4+(AtmosphereConfig.COLORS.get()?1:0);
            phase=nextPhase;active=next;
            if(nextBucket!=bucket) {
                // Keep unfinished refreshes if a season packet/command arrives during a previous batch.
                for(long section:TOUCHED)if(REFRESH.size()<8192)REFRESH.add(section);
                TOUCHED.clear();bucket=nextBucket;
            }
        }
        // Spread work across frames instead of invalidating every terrain mesh in one tick.
        for(int n=0;n<8&&!REFRESH.isEmpty();n++) {
            var iterator=REFRESH.iterator();long section=iterator.next();iterator.remove();
            int x=SectionPos.x(section),y=SectionPos.y(section),z=SectionPos.z(section);
            if(level.hasChunk(x,z))mc.empirecraft$renderer().setSectionDirty(x,y,z);
        }
    }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor event) {
        var mc=context;if(mc==null)return;var level=mc.empirecraft$level();
        if(level==null||!active||!AtmosphereConfig.SKY.get()||!level.getFluidState(event.getCamera().getBlockPosition()).isEmpty())return;
        var c=sky(event.getRed(),event.getGreen(),event.getBlue(),level.getRainLevel((float)event.getPartialTick()));
        event.setRed(c[0]);event.setGreen(c[1]);event.setBlue(c[2]);
    }
}
