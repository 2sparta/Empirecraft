package ua.empirecraft.fauna;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod("empirecraft_fauna")
public final class FaunaMod {
  public static final Logger LOG = LogUtils.getLogger();
  public static volatile Session session;
  private final Map<UUID, Long> notices = new HashMap<>();

  public FaunaMod() {
    MinecraftForge.EVENT_BUS.register(this);
  }

  @SubscribeEvent
  public void started(ServerStartedEvent event) {
    try {
      session = new Session(event.getServer());
    } catch (Exception error) {
      throw new IllegalStateException("Cannot start persistent fauna census", error);
    }
  }

  @SubscribeEvent
  public void stopped(ServerStoppedEvent event) {
    if (session != null) {
      session.close();
      session = null;
    }
    notices.clear();
  }

  @SubscribeEvent
  public void commands(RegisterCommandsEvent event) {
    FaunaCommands.register(event.getDispatcher());
  }

  @SubscribeEvent
  public void tick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END && session != null) session.tick();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void join(EntityJoinLevelEvent event) {
    if (session == null
        || !(event.getLevel() instanceof ServerLevel level)
        || !(event.getEntity() instanceof Mob mob)
        || !FaunaTypes.managed(mob.getType())) return;
    if (!session.join(mob, level, event.loadedFromDisk()).allowed()) event.setCanceled(true);
  }

  @SubscribeEvent
  public void leave(EntityLeaveLevelEvent event) {
    if (session != null
        && event.getLevel() instanceof ServerLevel
        && event.getEntity() instanceof Mob mob
        && FaunaTypes.managed(mob.getType())) session.leave(mob);
  }

  @SubscribeEvent
  public void moved(net.minecraftforge.event.entity.EntityEvent.EnteringSection event) {
    if (session != null
        && event.getEntity() instanceof Mob mob
        && FaunaTypes.managed(mob.getType())) session.moved(mob);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void baby(BabyEntitySpawnEvent event) {
    if (session == null
        || event.getChild() == null
        || !(event.getParentA().level() instanceof ServerLevel level)
        || !FaunaTypes.managed(event.getChild().getType())) return;
    var result = session.birth(event.getChild(), event.getParentA(), level);
    if (result.allowed()) return;
    event.setCanceled(true);
    if (event.getCausedByPlayer() instanceof ServerPlayer player
        && System.currentTimeMillis() - notices.getOrDefault(player.getUUID(), 0L) >= 5000) {
      notices.put(player.getUUID(), System.currentTimeMillis());
      player.sendSystemMessage(Component.literal(I18n.text(I18n.locale(player), result.reason())));
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void spawns(LevelEvent.PotentialSpawns event) {
    if (session == null || !(event.getLevel() instanceof ServerLevel level)) return;
    if (event.getSpawnerDataList().stream().noneMatch(entry -> FaunaTypes.managed(entry.type)))
      return;
    String
        biome =
            FaunaTypes.biome(
                level, event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()),
        world = level.dimension().location().toString();
    for (var entry : new ArrayList<>(event.getSpawnerDataList()))
      if (FaunaTypes.managed(entry.type)) {
        var animal =
            new Population.Animal(
                new UUID(0, 0),
                FaunaTypes.id(entry.type),
                world,
                event.getPos().getX(),
                event.getPos().getY(),
                event.getPos().getZ(),
                biome,
                false,
                false);
        double factor = session.population.weight(animal);
        event.removeSpawnerData(entry);
        if (factor > 0)
          event.addSpawnerData(
              new MobSpawnSettings.SpawnerData(
                  entry.type,
                  (int)
                      Math.max(1, Math.min(32767, Math.round(entry.getWeight().asInt() * factor))),
                  entry.minCount,
                  entry.maxCount));
      }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void position(MobSpawnEvent.PositionCheck event) {
    if (session == null
        || event.getSpawnType() != MobSpawnType.NATURAL
            && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION
        || !FaunaTypes.managed(event.getEntity().getType())) return;
    ServerLevel level = event.getLevel().getLevel();
    double chance = session.population.density(FaunaTypes.animal(event.getEntity(), level, false));
    if (chance < 1 && event.getEntity().getRandom().nextDouble() >= chance)
      event.setResult(Event.Result.DENY);
  }

  public static boolean captive(Entity entity) {
    var s = session;
    return s != null
        && entity instanceof Mob
        && FaunaTypes.managed(entity.getType())
        && (s.settings.domesticTypes.contains(FaunaTypes.id(entity.getType()))
            || s.settings.isPet(
                FaunaTypes.id(entity.getType()),
                entity instanceof TamableAnimal tame && tame.isTame()
                    || entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse
                        && horse.isTamed()));
  }

  public static Iterable<Entity> wildMobCapEntities(Iterable<Entity> source) {
    return () ->
        java.util.stream.StreamSupport.stream(source.spliterator(), false)
            .filter(entity -> !captive(entity))
            .iterator();
  }
}
