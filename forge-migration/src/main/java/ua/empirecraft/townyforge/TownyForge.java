package ua.empirecraft.townyforge;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.townyforge.TownySnapshot.Action;

@Mod("empirecraft_towny")
public final class TownyForge {
    private static volatile TownyStore store;
    private static String feudalMap;
    private int dailyTicks;

    public TownyForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void load(ServerAboutToStartEvent event) {
        Path world = event.getServer().m_129843_(LevelResource.f_78182_);
        try {
            store = new TownyStore(world);
            Path map = world.resolve("empirecraft/feudal-map.json");
            feudalMap = Files.isRegularFile(map) ? Files.readString(map) : null;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Empirecraft Towny data could not be loaded", error);
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppedEvent event) {
        store = null;
        feudalMap = null;
        dailyTicks = 0;
    }

    @SubscribeEvent
    public void upkeep(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || store == null) return;
        if (++dailyTicks % 20 == 0) {
            for (var player : event.getServer().m_6846_().m_11314_()) {
                try {
                    TownyJail.enforce(store, player);
                } catch (IOException | TownyProblem error) {
                    if (dailyTicks == 1200) LogUtils.getLogger().warn("Could not apply the Towny jail sentence", error);
                }
            }
        }
        if (dailyTicks < 1200) return;
        dailyTicks = 0;
        try {
            store.collectDaily(java.time.Instant.now().getEpochSecond());
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("Towny did not commit the daily collection", failure);
        }
    }

    @SubscribeEvent
    public void jailCommands(CommandEvent event) {
        TownySnapshot snapshot = towny();
        var context = event.getParseResults().getContext();
        if (snapshot == null || context.getNodes().isEmpty() || !(context.getSource().m_81373_() instanceof ServerPlayer player)
                || !TownyJail.confined(snapshot.resident(snapshot.residentOf(player.m_20148_())))) return;
        String command = context.getNodes().get(0).getNode().getName();
        command = command.substring(command.indexOf(':') + 1);
        String banned = snapshot.settings().getOrDefault("jail.blacklisted_commands", "home,spawn,tp,teleport,kill,warp");
        if (TownySnapshot.listed(banned, command)) {
            event.setCanceled(true);
            context.getSource().m_81352_(net.minecraft.network.chat.Component.m_237113_("Команда недоступна у в’язниці."));
        }
    }

    @SubscribeEvent
    public void jailTeleport(EntityTeleportEvent event) {
        TownySnapshot snapshot = towny();
        if (snapshot == null || !event.isCancelable() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var inmate = snapshot.resident(snapshot.residentOf(player.m_20148_()));
        if (!TownyJail.confined(inmate)) return;
        if (!snapshot.settingEnabled("jail.jail_allows_teleport_items")
                && (event instanceof EntityTeleportEvent.EnderPearl || event instanceof EntityTeleportEvent.ChorusFruit)) {
            event.setCanceled(true);
            return;
        }
        var point = TownyTravel.parse(snapshot.dimensions(), inmate.get("forgeJailSpawn"));
        if (!point.dimension().equals(dimension(player.m_9236_()))
                || ((int)Math.floor(event.getTargetX()) >> 4) != point.cx()
                || ((int)Math.floor(event.getTargetZ()) >> 4) != point.cz()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        new TownyCommands(() -> store).register(event.getDispatcher());
    }

    @SubscribeEvent
    public void enroll(PlayerEvent.PlayerLoggedInEvent event) {
        if (store == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        var snapshot = store.snapshot();
        if (!snapshot.editable() || !snapshot.residentOf(player.m_20148_()).isEmpty()) return;
        try {
            store.change(data -> new TownyGovernance(data, TownyCommands.actor(player)));
        } catch (TownyProblem | IOException failure) {
            LogUtils.getLogger().warn("Towny could not enroll a player", failure);
        }
    }

    private static TownySnapshot towny() {
        TownyStore current = store;
        return current == null ? null : current.snapshot();
    }

    public static TownyView view() {
        var server = ServerLifecycleHooks.getCurrentServer();
        var snapshot = towny();
        return server != null && server.m_18695_() && snapshot != null ? snapshot.view() : null;
    }

    public static String integrationSnapshot() {
        var server = ServerLifecycleHooks.getCurrentServer();
        var snapshot = towny();
        return server != null && server.m_18695_() && snapshot != null ? snapshot.integrationJson() : null;
    }

    public static String feudalMap() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.m_18695_() ? feudalMap : null;
    }

    /** Shared guard for block and entity impacts performed directly by server mods. */
    public static boolean allowsImpact(Object level, int x, int y, int z, String target, UUID shooter) {
        if (!(level instanceof ServerLevel serverLevel) || !onServerThread(serverLevel)) return false;
        TownySnapshot snapshot = towny();
        if (snapshot == null) return false;
        String dimension = dimension(serverLevel);
        if (!snapshot.isClaimed(dimension, x, z)) return true;
        return switch (target) {
            case "PLAYER" -> snapshot.environmental(dimension, x, z, "pvp", "forcepvp", "pvp");
            case "MOB" -> shooter != null && serverLevel.m_7654_().m_6846_().m_11259_(shooter) != null
                    && snapshot.allows(dimension, x, z, shooter, Action.DESTROY, material(
                            ForgeRegistries.BLOCKS.getKey(serverLevel.m_8055_(new BlockPos(x, y, z)).m_60734_())));
            default -> false;
        };
    }

    /** Checked by the one-time TFC terrain migration before it edits loaded chunks. */
    public static boolean wilderness(ServerLevel level, BlockPos pos) {
        if (!onServerThread(level)) return false;
        TownySnapshot snapshot = towny();
        return snapshot != null && !snapshot.isClaimed(dimension(level), pos.m_123341_(), pos.m_123343_());
    }

    private static boolean onServerThread(ServerLevel level) {
        var server = level.m_7654_();
        return server == ServerLifecycleHooks.getCurrentServer() && server.m_18695_();
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level
                && denied(level, event.getPos(), event.getPlayer(), Action.DESTROY,
                        material(ForgeRegistries.BLOCKS.getKey(event.getState().m_60734_())))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        String material = material(ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().m_60734_()));
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var replaced : multi.getReplacedBlockSnapshots()) {
                if (denied(level, replaced.getPos(), event.getEntity(), Action.BUILD, material)) {
                    event.setCanceled(true);
                    return;
                }
            }
        } else if (denied(level, event.getPos(), event.getEntity(), Action.BUILD, material)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void clickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (towny() == null) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        var state = level.m_8055_(pos);
        var block = ForgeRegistries.BLOCKS.getKey(state.m_60734_());
        boolean moddedContainer = material(block).isEmpty() && state.m_155947_()
                && towny().claim(dimension(level), pos.m_123341_(), pos.m_123343_()) != null;
        if ((towny().restricted(dimension(level), pos.m_123341_(), pos.m_123343_(), material(block), Action.SWITCH) || moddedContainer)
                && denied(level, pos, event.getEntity(), Action.SWITCH, "")) {
            event.setUseBlock(Event.Result.DENY);
        }
        var item = event.getItemStack().m_41720_();
        if (!(item instanceof BlockItem) && towny().restricted(dimension(level), pos.m_123341_(), pos.m_123343_(),
                material(ForgeRegistries.ITEMS.getKey(item)), Action.ITEM_USE)
                && (denied(level, pos, event.getEntity(), Action.ITEM_USE, "")
                        || denied(level, pos.m_121945_(event.getFace()), event.getEntity(), Action.ITEM_USE, ""))) {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void useItem(PlayerInteractEvent.RightClickItem event) {
        var item = ForgeRegistries.ITEMS.getKey(event.getItemStack().m_41720_());
        if (towny() != null && event.getEntity() instanceof ServerPlayer player
                && TownyJail.confined(towny().resident(towny().residentOf(player.m_20148_())))
                && !towny().settingEnabled("jail.jail_allows_teleport_items")
                && (material(item).equals("ENDER_PEARL") || material(item).equals("CHORUS_FRUIT"))) {
            event.setCanceled(true);
            return;
        }
        if (towny() != null && towny().restrictedItem(material(item))
                && denied(event.getLevel(), event.getPos(), event.getEntity(), Action.ITEM_USE, "")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void bucket(FillBucketEvent event) {
        if (event.getTarget() instanceof BlockHitResult hit
                && (denied(event.getLevel(), hit.m_82425_(), event.getEntity(), Action.ITEM_USE, "")
                        || denied(event.getLevel(), hit.m_82425_().m_121945_(hit.m_82434_()),
                                event.getEntity(), Action.ITEM_USE, ""))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void interactEntity(PlayerInteractEvent.EntityInteract event) {
        vehicle(event, event.getTarget());
    }

    @SubscribeEvent
    public void interactEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        vehicle(event, event.getTarget());
    }

    private void vehicle(PlayerInteractEvent event, Entity target) {
        String type = material(ForgeRegistries.ENTITY_TYPES.getKey(target.m_6095_()));
        if (towny() != null && towny().restrictedSwitch(type)
                && denied(event.getLevel(), target.m_20183_(), event.getEntity(), Action.SWITCH, "")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void attack(LivingAttackEvent event) {
        if (towny() == null || !(event.getSource().m_7639_() instanceof ServerPlayer attacker)) return;
        Entity victim = event.getEntity();
        if (victim instanceof ServerPlayer) {
            boolean jail = towny().settingEnabled("jail.do_jail_plots_deny_pvp")
                    && (TownyJail.confined(towny().resident(towny().residentOf(attacker.m_20148_())))
                    || TownyJail.confined(towny().resident(towny().residentOf(victim.m_20148_()))));
            if (jail || !environmental(attacker, "pvp", "forcepvp", "pvp")
                    || !environmental(victim, "pvp", "forcepvp", "pvp")) {
                event.setCanceled(true);
            }
        } else if (towny().claim(dimension(victim.m_9236_()),
                victim.m_20183_().m_123341_(), victim.m_20183_().m_123343_()) != null
                && denied(victim.m_9236_(), victim.m_20183_(), attacker, Action.DESTROY, "")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (towny() == null || event.getLevel().m_5776_()) return;
        String dimension = dimension(event.getLevel());
        event.getAffectedBlocks().removeIf(pos -> !towny().environmental(
                dimension, pos.m_123341_(), pos.m_123343_(), "explosions", "forceexplosions", "explosion"));
    }

    @SubscribeEvent
    public void piston(PistonEvent.Pre event) {
        if (towny() == null || !(event.getLevel() instanceof Level level) || level.m_5776_()) return;
        var structure = event.getStructureHelper();
        if (structure == null || !structure.m_60422_()) return;
        BlockPos origin = event.getPos();
        String dimension = dimension(level);
        for (BlockPos block : structure.m_60436_()) {
            if (crossesClaim(dimension, origin, block, block.m_121945_(structure.m_155942_()))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos block : structure.m_60437_()) {
            if (crossesClaim(dimension, origin, block, block)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private boolean crossesClaim(String dimension, BlockPos origin, BlockPos from, BlockPos to) {
        return !towny().sameClaim(dimension, origin.m_123341_(), origin.m_123343_(), from.m_123341_(), from.m_123343_())
                || !towny().sameClaim(dimension, origin.m_123341_(), origin.m_123343_(), to.m_123341_(), to.m_123343_());
    }

    private boolean environmental(Entity entity, String worldFlag, String forceFlag, String plotFlag) {
        BlockPos pos = entity.m_20183_();
        return towny().environmental(dimension(entity.m_9236_()), pos.m_123341_(), pos.m_123343_(),
                worldFlag, forceFlag, plotFlag);
    }

    private boolean denied(Level level, BlockPos pos, Entity actor, Action action, String material) {
        return towny() != null && actor instanceof ServerPlayer player && !level.m_5776_()
                && !player.m_20310_(2)
                && !towny().allows(dimension(level), pos.m_123341_(), pos.m_123343_(),
                        player.m_20148_(), action, material);
    }

    private static String dimension(Level level) {
        return level.m_46472_().m_135782_().toString();
    }

    private static String material(ResourceLocation id) {
        return id != null && id.m_135827_().equals("minecraft") ? id.m_135815_().toUpperCase(Locale.ROOT) : "";
    }
}
