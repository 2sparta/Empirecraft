package ua.empirecraft.fauna;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * One server lifetime. New births reserve capacity before insertion; existing animals always
 * survive.
 */
public final class Session implements AutoCloseable {
  public final MinecraftServer server;
  public final Population population;
  public final TownyLand towns = new TownyLand();
  public final IndexData data;
  public final Set<Mob> loaded = ConcurrentHashMap.newKeySet();
  public volatile Settings settings;
  public final Path config = FMLPaths.CONFIGDIR.get().resolve("empirecraft-fauna.json");
  public final Map<String, Set<String>> tags;
  private final DiskCensus census = new DiskCensus();
  private final Map<UUID, Mob> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Optional<Population.Animal>> generation = new ConcurrentHashMap<>();
  private Iterator<Mob> cursor = Collections.emptyIterator();
  public final NearbyFauna nearby = new NearbyFauna(this);
  private long ticks, savedRevision, nextScan = 200, nextWarning;
  public long trackingNanos;
  public final AtomicLong blocked = new AtomicLong();

  public Session(MinecraftServer server) throws Exception {
    this.server = server;
    settings = Settings.load(config);
    FaunaTypes.configure(settings);
    population = new Population(settings);
    tags = FaunaTypes.biomeTags(server.overworld());
    data =
        server
            .overworld()
            .getDataStorage()
            .computeIfAbsent(IndexData::load, IndexData::new, "empirecraft-fauna");
    data.population = population;
    for (var animal : data.initial) population.importSaved(animal);
    data.initial.clear();
    refreshLand(true);
    for (var level : server.getAllLevels())
      for (Entity entity : level.getAllEntities())
        if (entity instanceof Mob mob && FaunaTypes.managed(mob.getType())) {
          loaded.add(mob);
          nearby.returned(mob);
          existing(mob, level);
        }
    scan();
  }

  public void refreshLand(boolean force) {
    try {
      var land = towns.read(server, force);
      if (!land.equals(population.land())) population.context(settings, land, tags);
    } catch (ReflectiveOperationException | RuntimeException error) {
      if (population.land().ready())
        population.context(settings, Population.Land.unavailable(), tags);
      if (ticks >= nextWarning) {
        nextWarning = ticks + 6000;
        FaunaMod.LOG.warn("Towny fauna census is waiting for claims", error);
      }
    }
  }

  public void existing(Mob mob, ServerLevel level) {
    mob.getPersistentData().putBoolean(FaunaTypes.SEEN, true);
    population.put(FaunaTypes.animal(mob, level, true));
  }

  public Population.Decision join(Mob mob, ServerLevel level, boolean fromDisk) {
    Entity duplicate = level.getEntity(mob.getUUID());
    if (duplicate != null && duplicate != mob) return new Population.Decision(true, "");
    if (fromDisk) {
      nearby.returned(mob);
      if (nearby.expire(mob, System.currentTimeMillis())) {
        population.remove(mob.getUUID());
        return new Population.Decision(false, "");
      }
    }
    // Birth events run before vanilla moves the child to its final position.
    // Recheck its actual insertion location, releasing only that child's reservation.
    if (!fromDisk
        && !mob.getPersistentData().getBoolean(FaunaTypes.SEEN)
        && pending.get(mob.getUUID()) == mob) {
      population.remove(mob.getUUID());
      pending.remove(mob.getUUID());
    }
    if (fromDisk
        || mob.getPersistentData().getBoolean(FaunaTypes.SEEN)
        || population.contains(mob.getUUID())
        || mob instanceof net.minecraft.world.entity.animal.Bucketable bucket
            && bucket.fromBucket()) {
      existing(mob, level);
      loaded.add(mob);
      pending.put(mob.getUUID(), mob);
      return new Population.Decision(true, "");
    }
    var decision = population.admit(FaunaTypes.animal(mob, level, true));
    if (decision.allowed()) {
      mob.getPersistentData().putBoolean(FaunaTypes.SEEN, true);
      loaded.add(mob);
      pending.put(mob.getUUID(), mob);
    } else blocked.incrementAndGet();
    return decision;
  }

  public Population.Decision birth(Mob child, Mob parent, ServerLevel level) {
    refreshLand(true);
    refreshLoaded();
    // Forge posts BabyEntitySpawnEvent before setting the child's coordinates.
    var decision =
        population.admit(
            FaunaTypes.animalAt(child, level, parent.getX(), parent.getY(), parent.getZ(), false));
    if (decision.allowed()) pending.put(child.getUUID(), child);
    else blocked.incrementAndGet();
    return decision;
  }

  public void leave(Mob mob) {
    nearby.forget(mob);
    loaded.remove(mob);
    pending.remove(mob.getUUID());
    var reason = mob.getRemovalReason();
    if (reason != null && reason.shouldDestroy()) population.remove(mob.getUUID());
    else if (mob.level() instanceof ServerLevel level)
      population.put(FaunaTypes.animal(mob, level, false));
  }

  public void moved(Mob mob) {
    if (loaded.contains(mob)) refresh(mob);
  }

  void refresh(Mob mob) {
    if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved()) return;
    var old = population.get(mob.getUUID());
    boolean tame =
        mob instanceof TamableAnimal animal && animal.isTame()
            || mob instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse
                && horse.isTamed();
    if (old == null
        || !old.loaded()
        || old.tame() != tame
        || old.roaming() != (Roaming.natural(mob) && !Roaming.protectedAnimal(mob))
        || !old.world().equals(level.dimension().location().toString())
        || (int) Math.floor(old.x()) != mob.blockPosition().getX()
        || (int) Math.floor(old.y()) != mob.blockPosition().getY()
        || (int) Math.floor(old.z()) != mob.blockPosition().getZ())
      population.put(FaunaTypes.animal(mob, level, true));
  }

  public void refreshLoaded() {
    for (Mob mob : loaded) refresh(mob);
  }

  public boolean beforeGeneration(Mob mob, ServerLevel level) {
    var old = population.get(mob.getUUID());
    var decision = population.admit(FaunaTypes.animal(mob, level, false));
    if (!decision.allowed()) {
      blocked.incrementAndGet();
      return false;
    }
    generation.put(mob.getUUID(), Optional.ofNullable(old));
    mob.getPersistentData().putBoolean(FaunaTypes.SEEN, true);
    return true;
  }

  public void afterGeneration(Mob mob, boolean success) {
    var old = generation.remove(mob.getUUID());
    if (old != null && !success) {
      if (old.isPresent()) population.put(old.get());
      else {
        population.remove(mob.getUUID());
        mob.getPersistentData().remove(FaunaTypes.SEEN);
      }
    }
  }

  public void reload() throws Exception {
    Settings candidate = Settings.load(config);
    settings = candidate;
    FaunaTypes.configure(candidate);
    towns.invalidate();
    refreshLand(true);
    population.context(candidate, population.land(), tags);
    if (!census.running && !population.censusReady) scan();
  }

  private void scan() {
    var levels = new ArrayList<ServerLevel>();
    server.getAllLevels().forEach(levels::add);
    census.start(levels, population);
  }

  public void tick() {
    ticks++;
    long start = System.nanoTime();
    if (ticks % 20 == 0) refreshLand(true);
    for (var entry : List.copyOf(pending.entrySet())) {
      Mob mob = entry.getValue();
      if (mob.isAddedToWorld() && !mob.isRemoved()) {
        loaded.add(mob);
        refresh(mob);
      } else if (!mob.isAddedToWorld()) {
        loaded.remove(mob);
        population.remove(entry.getKey());
        mob.getPersistentData().remove(FaunaTypes.SEEN);
      }
      pending.remove(entry.getKey());
    }
    if (ticks % settings.positionRefreshTicks == 0 || cursor.hasNext()) {
      if (!cursor.hasNext()) cursor = loaded.iterator();
      int inspected = 0;
      while (cursor.hasNext() && inspected++ < 512 && System.nanoTime() - start < 2000000)
        refresh(cursor.next());
    }
    if (!census.running && !population.censusReady && ticks >= nextScan) {
      nextScan = ticks + 200;
      scan();
    }
    if (savedRevision != population.revision()) {
      data.setDirty();
      savedRevision = population.revision();
    }
    nearby.tick(ticks);
    trackingNanos = System.nanoTime() - start;
    if (ticks % (settings.monitorIntervalSeconds * 20L) == 0
        && population.count("loaded") >= settings.loadedAnimalWarning)
      FaunaMod.LOG.warn(
          I18n.text(
              settings.defaultLocale,
              "monitor_warning",
              population.count("loaded"),
              settings.loadedAnimalWarning));
  }

  @Override
  public void close() {
    census.close();
    data.setDirty();
    loaded.clear();
  }
}
