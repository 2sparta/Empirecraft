package guardtest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_towny_probe")
@Mod.EventBusSubscriber(modid = "empirecraft_towny_probe")
public final class ForgeImpactProbe {
    private static int ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticks > 0 && --ticks == 0) verify(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void test(ServerStartedEvent event) {
        ticks = 40;
    }

    private static void verify(net.minecraft.server.MinecraftServer server) {
        server.execute(() -> {
            try {
                var level = server.m_129783_();
                var claim = new BlockPos(Integer.getInteger("empirecraft.test.claimX", 8), 65,
                        Integer.getInteger("empirecraft.test.claimZ", 8));
                var wilderness = new BlockPos(Integer.getInteger("empirecraft.test.wildX", 24), 65,
                        Integer.getInteger("empirecraft.test.wildZ", 8));
                if (Boolean.getBoolean("empirecraft.test.freshFeudal")) verifyFresh(server, level, claim);
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
                if (Boolean.getBoolean("empirecraft.test.nativeBridges")) {
                    Class<?> map = Class.forName("ua.empirecraft.townymap.TownyAccess");
                    var constructor = map.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    var layers = map.getDeclaredMethod("snapshotAll", boolean.class);
                    layers.setAccessible(true);
                    var views = (java.util.Map<?, ?>) layers.invoke(constructor.newInstance(), true);
                    require(views.values().stream().filter(java.util.Map.class::isInstance)
                            .map(java.util.Map.class::cast).anyMatch(view -> !view.isEmpty()), "JourneyMap projected native claims");
                    Class<?> recruits = Class.forName("ua.townyrecruits.Bridge");
                    var instance = recruits.getDeclaredField("INSTANCE");
                    instance.setAccessible(true);
                    String status = (String) recruits.getMethod("status").invoke(instance.get(null));
                    require(status.contains("ready=true") && status.contains("census=true"), "TownyRecruits " + status);
                    if (Boolean.getBoolean("empirecraft.test.recruitMenu")) {
                        var commands = server.m_129892_().m_82094_().getRoot();
                        var hiring = commands.getChild("trecruit");
                        require(hiring != null && hiring.getChild("hire") != null && hiring.getChild("dismiss") != null,
                                "Native hiring and dismissal commands");
                        var recruitment = recruits.getDeclaredField("recruitment");
                        recruitment.setAccessible(true);
                        Object service = recruitment.get(instance.get(null));
                        var fake = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TestRecruitMenu"));
                        Class<?> menuType = Class.forName("ua.townyrecruits.RecruitmentCommands$ForgeHireMenu");
                        var menuConstructor = menuType.getDeclaredConstructor(int.class, Inventory.class, ServerPlayer.class, service.getClass());
                        menuConstructor.setAccessible(true);
                        var menu = (ChestMenu) menuConstructor.newInstance(1, fake.m_150109_(), fake, service);
                        for (int slot : new int[]{11, 13, 15}) {
                            require(!menu.m_39261_().m_8020_(slot).m_41619_(), "Native recruit menu action at " + slot);
                            require(menu.m_7648_(fake, slot).m_41619_(), "Recruit menu icons cannot be extracted");
                        }
                    }
                    Class<?> towny = Class.forName("ua.empirecraft.townyforge.TownyForge");
                    String archive = (String) towny.getMethod("feudalMap").invoke(null);
                    require(archive != null && archive.contains("\"estates\""), "Feudal map archive available");
                    System.out.println("FORGE_TOWNY_BRIDGES_PASS " + status);
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

    private static void verifyFresh(net.minecraft.server.MinecraftServer server, ServerLevel level, BlockPos claim)
            throws ReflectiveOperationException {
        var towny = Class.forName("ua.empirecraft.townyforge.TownyForge");
        String initial = (String) towny.getMethod("integrationSnapshot").invoke(null);
        require(initial.contains("\"towns\":{}") && initial.contains("\"claims\":[]"), "empty Towny world");
        var providerType = Class.forName("ua.empirecraft.feudal.ForgeFeudal");
        Object provider = providerType.getMethod("active").invoke(null);
        require(provider != null, "live Feudal in the fresh world");
        var sails = Class.forName("com.quintonc.vs_sails.protection.FleetNative");
        require(sails.getMethod("plugin").invoke(null) == provider, "Valkyrien Skies fleet provider");
        var dispatcher = server.m_129892_().m_82094_();
        var town = dispatcher.getRoot().getChild("town");
        require(town.getChild("jail") == null && town.getChild("unjail") == null, "jail commands removed");
        for (String command : new String[]{"market", "treasury", "provisions", "fleet", "shiprequest", "currency"}) {
            require(dispatcher.getRoot().getChild(command) != null, "registered " + command);
        }
        var mayor = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ForgeMayor"));
        mayor.m_6034_(claim.m_123341_() + .5, claim.m_123342_(), claim.m_123343_() + .5);
        var source = mayor.m_20203_().m_81325_(0);
        require(server.m_129892_().m_230957_(source, "town new ForgeProbe") == 1, "found a fresh town");
        require(server.m_129892_().m_230957_(source, "town state status") == 1, "native Feudal town synchronization");
        require(server.m_129892_().m_230957_(source, "treasury balance") == 1, "native town treasury");
        require(server.m_129892_().m_230957_(source, "market create Forge Bazaar") == 1, "native market project");
        if (Boolean.getBoolean("empirecraft.test.provisionInventory")) {
            var bread = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.f_42406_, 16);
            mayor.m_150109_().f_35974_.set(0, bread);
            require(server.m_129892_().m_230957_(source, "provisions deposit 1 8") == 1, "save provision deposit to disk");
            require(mayor.m_150109_().f_35974_.get(0).m_41613_() == 8, "inventory debit");
            require(server.m_129892_().m_230957_(source, "provisions withdraw 1 4") == 1, "save provision withdrawal to disk");
            require(mayor.m_150109_().f_35974_.get(0).m_41613_() == 12, "inventory credit");
        }
        String map = (String) providerType.getMethod("mapView").invoke(null);
        var request = providerType.getMethod("engagementRequest", String.class, java.util.Map.class);
        var point = request.invoke(provider, "point", java.util.Map.of("town", "", "world", "minecraft:overworld",
                "x", claim.m_123341_(), "z", claim.m_123343_()));
        require(point.toString().contains("UNKNOWN") && map.contains("ForgeProbe"), "native engagement classifies the new claim");
        System.out.println("FORGE_FEUDAL_FRESH_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
