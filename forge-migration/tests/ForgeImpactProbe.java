package guardtest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_towny_probe")
@Mod.EventBusSubscriber(modid = "empirecraft_towny_probe")
public final class ForgeImpactProbe {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void test(ServerStartedEvent event) {
        var server = event.getServer();
        server.execute(() -> {
            try {
                var level = server.m_129783_();
                var claim = new BlockPos(Integer.getInteger("empirecraft.test.claimX", 8), 65,
                        Integer.getInteger("empirecraft.test.claimZ", 8));
                var wilderness = new BlockPos(Integer.getInteger("empirecraft.test.wildX", 24), 65,
                        Integer.getInteger("empirecraft.test.wildZ", 8));
                for (String owner : new String[]{"claimguard", "siegeworks"}) {
                    Class<?> gate = Class.forName("ua.empirecraft." + owner + ".TownyGate");
                    Class<?> target = Class.forName(gate.getName() + "$Target");
                    var allows = gate.getMethod("allows", Object.class, int.class, int.class, int.class, target, UUID.class);
                    Object instance = gate.getConstructor().newInstance();
                    for (Object type : target.getEnumConstants()) {
                        require(!(Boolean) allows.invoke(instance, level, claim.m_123341_(), claim.m_123342_(),
                                claim.m_123343_(), type, null), owner + " claim " + type);
                        require((Boolean) allows.invoke(instance, level, wilderness.m_123341_(), wilderness.m_123342_(),
                                wilderness.m_123343_(), type, null), owner + " wilderness " + type);
                    }
                }
                var terrain = Class.forName("ua.empirecraft.tfc.OneTimeTerrain")
                        .getDeclaredMethod("wilderness", ServerLevel.class, BlockPos.class);
                terrain.setAccessible(true);
                require(!(Boolean) terrain.invoke(null, level, claim), "TFC protected claim");
                require((Boolean) terrain.invoke(null, level, wilderness), "TFC changes in wilderness");
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
