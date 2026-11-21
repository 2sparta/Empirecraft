package ua.empirecraft.townyforge;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.townyforge.TownySnapshot.Action;

@Mod("empirecraft_towny")
public final class TownyForge {
    private TownyStore store;

    public TownyForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void load(ServerAboutToStartEvent event) {
        Path world = event.getServer().m_129843_(LevelResource.f_78182_);
        try {
            store = new TownyStore(world);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Empirecraft Towny data could not be loaded", error);
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppedEvent event) {
        store = null;
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

    private TownySnapshot towny() {
        return store == null ? null : store.snapshot();
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
        if ((towny().restrictedSwitch(material(block)) || moddedContainer)
                && denied(level, pos, event.getEntity(), Action.SWITCH, "")) {
            event.setUseBlock(Event.Result.DENY);
        }
        var item = event.getItemStack().m_41720_();
        if (!(item instanceof BlockItem) && towny().restrictedItem(material(ForgeRegistries.ITEMS.getKey(item)))
                && (denied(level, pos, event.getEntity(), Action.ITEM_USE, "")
                        || denied(level, pos.m_121945_(event.getFace()), event.getEntity(), Action.ITEM_USE, ""))) {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void useItem(PlayerInteractEvent.RightClickItem event) {
        var item = ForgeRegistries.ITEMS.getKey(event.getItemStack().m_41720_());
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
            if (!environmental(attacker, "pvp", "forcepvp", "pvp")
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
