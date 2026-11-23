package guardtest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;

@Mod("empirecraft_towny_probe")
@Mod.EventBusSubscriber(modid = "empirecraft_towny_probe")
public final class ForgeImpactProbe {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void test(ServerStartedEvent event) {
        var server = event.getServer();
        server.execute(() -> {
            try {
                var level = server.m_129783_();
                for (String owner : new String[]{"claimguard", "siegeworks"}) {
                    Class<?> gate = Class.forName("ua.empirecraft." + owner + ".TownyGate");
                    Class<?> target = Class.forName(gate.getName() + "$Target");
                    var allows = gate.getMethod("allows", Object.class, int.class, int.class, int.class, target, UUID.class);
                    Object instance = gate.getConstructor().newInstance();
                    for (Object type : target.getEnumConstants()) {
                        require(!(Boolean) allows.invoke(instance, level, 8, 65, 8, type, null), owner + " claim " + type);
                        require((Boolean) allows.invoke(instance, level, 24, 65, 8, type, null), owner + " wilderness " + type);
                    }
                }
                if (ModList.get().isLoaded("empirecraft_tfc_integration")) {
                    var terrain = Class.forName("ua.empirecraft.tfc.OneTimeTerrain")
                            .getDeclaredMethod("wilderness", ServerLevel.class, BlockPos.class);
                    terrain.setAccessible(true);
                    require(!(Boolean) terrain.invoke(null, level, new BlockPos(8, 65, 8)), "TFC protected claim");
                    require((Boolean) terrain.invoke(null, level, new BlockPos(24, 65, 8)), "TFC changes in wilderness");
                }
                System.out.println("FORGE_TOWNY_IMPACT_PASS");
            } catch (ReflectiveOperationException | AssertionError error) {
                System.out.println("FORGE_TOWNY_IMPACT_FAIL " + error);
                error.printStackTrace();
            } finally {
                server.m_7570_(false);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
