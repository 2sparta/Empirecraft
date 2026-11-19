package ua.empirecraft.trmt.test;

import java.lang.reflect.Method;
import java.util.List;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.ErosionMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.trmt.Surfaces;

@Mod("empirecraft_trmt_test")
public final class WalkingProbe {
    public WalkingProbe() {
        MinecraftForge.EVENT_BUS.addListener(WalkingProbe::run);
    }

    private static void run(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var level = server.m_129783_();
            var player = FakePlayerFactory.getMinecraft(level);
            var config = TRMTConfig.get();
            config.erosionThresholds.grass.min = config.erosionThresholds.grass.max = 1;
            config.erosionThresholds.dirt.min = config.erosionThresholds.dirt.max = 1;
            config.erosionThresholds.sand.min = config.erosionThresholds.sand.max = 1;
            ErosionMapManager.getInstance().loadState(server);

            Method playerTick = tick(net.minecraft.server.level.ServerPlayer.class);
            Method mobTick = tick(Mob.class);
            var cow = (Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft:cow")).m_20615_(level);
            cow.m_21463_(player, false);
            int checked = 0;
            for (var block : ForgeRegistries.BLOCKS) {
                var id = ForgeRegistries.BLOCKS.getKey(block);
                if (id == null || !(id.m_135827_().equals("tfc") || id.m_135827_().equals("empirecraft_tfc"))) continue;
                BlockState state = block.m_49966_();
                var category = Surfaces.trackedBlock(state);
                if (category == block) continue;
                require(category == Blocks.f_50440_ || category == Blocks.f_50493_ || category == Blocks.f_49992_, "surface category");
                for (boolean mob : List.of(false, true)) {
                    BlockPos pos = new BlockPos(1000 + checked * 4, 70, 1000);
                    level.m_46597_(pos.m_7495_(), Blocks.f_50752_.m_49966_());
                    level.m_46597_(pos, state);
                    level.m_46597_(pos.m_7494_(), Blocks.f_50016_.m_49966_());
                    var walker = mob ? cow : player;
                    walker.m_6034_(pos.m_123341_() + 0.5, 71, pos.m_123343_() + 0.5);
                    walker.m_6853_(true);
                    for (int step = 0; step < 12; step++) {
                        // Alternate ground positions so the native TRMT movement guard counts each crossing.
                        walker.m_6034_(pos.m_123341_() + 1.5, 71, pos.m_123343_() + 0.5);
                        (mob ? mobTick : playerTick).invoke(walker, new CallbackInfo("tick", false));
                        walker.m_6034_(pos.m_123341_() + 0.5, 71, pos.m_123343_() + 0.5);
                        (mob ? mobTick : playerTick).invoke(walker, new CallbackInfo("tick", false));
                    }
                    var result = ForgeRegistries.BLOCKS.getKey(level.m_8055_(pos).m_60734_());
                    require(result != null && result.m_135827_().equals("trmt"), "Native walking path for " + id + " mob=" + mob + " got=" + result);
                    checked++;
                }
            }
            require(checked >= 52, "TFC soil and all seven sand colors covered");
            for (String name : List.of("tfc:farmland/loam", "tfc:mud/loam", "empirecraft_tfc:farmland/chernozem")) {
                var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name));
                require(Surfaces.trackedBlock(block.m_49966_()) == block, "Cultivated/wet surface classification " + name);
            }
            System.out.println("TRMT_WALKING_PASS player_and_leashed_mob_cases=" + checked);
        } catch (Throwable error) {
            System.out.println("TRMT_WALKING_FAIL");
            error.printStackTrace();
        } finally {
            server.m_7570_(false);
        }
    }

    private static Method tick(Class<?> target) {
        for (var method : target.getDeclaredMethods()) {
            if (method.getName().contains("trmt") && method.getName().endsWith("onTick")) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new AssertionError("Missing TRMT tick injection on " + target);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
