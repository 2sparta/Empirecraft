package guardtest;

import java.lang.reflect.*;
import java.util.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import ua.empirecraft.leaves.*;
import ua.empirecraft.leaves.client.*;

/** Real Minecraft states, live tag reloads, production colour helper. */
public final class EvergreenColorRuntimeTest {
    static int checks;
    static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.m_142977_();
        // Initialize the real registries in this offline renderer harness, without Forge's
        // network/event bootstrap (which requires ModLauncher). Forge's official AT is applied.
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("f_135867_");boot.setAccessible(true);boot.setBoolean(null,true);
        BuiltInRegistries.m_257498_();
        var spec=SeasonalLeaves.class.getDeclaredField("SPEC");spec.setAccessible(true);
        ((ForgeConfigSpec)spec.get(null)).setConfig(CommentedConfig.inMemory());
        var active=ClientSeasons.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(null,true);
        var phase=ClientSeasons.class.getDeclaredField("phase");phase.setAccessible(true);
        var registry=BuiltInRegistries.f_256975_;
        var keep=TagKey.m_203882_(Registries.f_256747_,new ResourceLocation(SeasonalLeaves.ID,"evergreen"));
        List<Holder<Block>> members=new ArrayList<>();
        var states=new ArrayList<BlockState>();
        for(String name:List.of("spruce","jungle","mangrove","azalea","flowering_azalea")) {
            Block b=registry.m_7745_(new ResourceLocation("minecraft",name+"_leaves"));
            states.add(b.m_49966_());members.add(registry.m_263177_(b));
        }
        BlockState jungle=states.get(1),oak=registry.m_7745_(new ResourceLocation("minecraft:oak_leaves")).m_49966_();
        registry.m_203652_(Map.of(keep,members,SeasonalLeaves.BIOME_EVERGREEN,List.of(registry.m_263177_(jungle.m_60734_()))));
        var pos=new BlockPos(4,65,7);int nativeRgb=0x4a9631,rawBiome=0x458234;
        BlockAndTintGetter view=(BlockAndTintGetter)Proxy.newProxyInstance(EvergreenColorRuntimeTest.class.getClassLoader(),new Class[]{BlockAndTintGetter.class},(proxy,m,a)->{
            if(m.getName().equals("m_6171_")) {
                check(a[1]==ClientSeasons.NATIVE_FOLIAGE,"Use the unseasoned biome resolver, not Serene Seasons");return rawBiome;
            }
            throw new AssertionError("Unexpected view access: "+m);
        });
        for(double season:new double[]{0,3,6,7,8,9,10,11.9}) {
            phase.setDouble(null,season);
            for(var state:states) {
                check(ClientSeasons.color(state,pos,nativeRgb,0)==nativeRgb,"Evergreen native colour changed in season "+season);
            }
            check(ClientSeasons.color(jungle,view,pos,nativeRgb,0)==rawBiome,"Biome-tinted evergreen inherited Serene Seasons' autumn tint");
        }
        phase.setDouble(null,8);
        check(ClientSeasons.color(oak,pos,nativeRgb,0)!=nativeRgb,"Deciduous autumn colour must remain active");
        check(ClientSeasons.color(states.get(0),null,nativeRgb,0)==nativeRgb,"Inventory tint must stay native");
        registry.m_203652_(Map.of(keep,List.of(),SeasonalLeaves.BIOME_EVERGREEN,List.of()));
        check(!SeasonalLeaves.isEvergreen(states.get(0)),"Removed datapack tag must take effect without restart");
        check(ClientSeasons.color(states.get(0),pos,nativeRgb,0)!=nativeRgb,"Renderer retained stale evergreen membership after reload");
        System.out.println("EVERGREEN_COLOR_RUNTIME_PASS assertions="+checks+" vanilla states, eight seasons, native tint, raw biome resolver and live tags");
    }
}
