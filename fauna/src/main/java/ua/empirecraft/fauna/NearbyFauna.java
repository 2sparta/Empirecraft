package ua.empirecraft.fauna;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Bounded, staggered additional spawns in loaded wilderness; never requests a chunk ticket. */
public final class NearbyFauna {
  private final Session session;
  private final ArrayDeque<Job> jobs = new ArrayDeque<>();
  private Iterator<Mob> cleanup = Collections.emptyIterator();
  private final Set<UUID> returning = new HashSet<>();
  private long nextCycle;
  private long headingTick;
  private final Map<UUID, TravelHeading> headings = new HashMap<>();
  public long attempts, spawned, expired;

  private record Job(UUID player, SpawnCatalog.Entry entry, long due) {}

  private static final Map<String, SpawnCatalog.Entry> CATALOG = new HashMap<>();
  private static final Method CURRENT, MONTH, WHITELIST;
  private static final Object SEASONS;

  static {
    for (var row : SpawnCatalog.ENTRIES) for (var id : row.ids()) CATALOG.put(id, row);
    try {
      CURRENT =
          Class.forName("ua.empirecraft.calendar.WallClockCalendar")
              .getMethod("current", ServerLevel.class);
      MONTH =
          Class.forName("ua.empirecraft.calendar.CalendarMath").getMethod("monthIndex", int.class);
      SEASONS = Class.forName("sereneseasons.init.ModConfig").getField("seasons").get(null);
      WHITELIST =
          SEASONS
              .getClass()
              .getMethod("isDimensionWhitelisted", net.minecraft.resources.ResourceKey.class);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public NearbyFauna(Session session) {
    this.session = session;
  }

  public void returned(Mob mob) {
    returning.add(mob.getUUID());
  }

  public void forget(Mob mob) {
    returning.remove(mob.getUUID());
  }

  public static int month(ServerLevel level) {
    try {
      Object projection = CURRENT.invoke(null, level);
      if (projection == null) return -1;
      int ticks = (int) projection.getClass().getMethod("seasonTicks").invoke(projection);
      return (int) MONTH.invoke(null, ticks);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read server calendar", e);
    }
  }

  public static boolean enabledDimension(ServerLevel level) {
    try {
      return level.dimensionType().hasSkyLight()
          && (boolean) WHITELIST.invoke(SEASONS, level.dimension());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read seasonal dimensions", e);
    }
  }

  public static boolean active(ServerPlayer player) {
    return player.isAlive() && !player.isSpectator();
  }

  private TravelHeading.Heading heading(ServerPlayer player) {
    var look = player.getLookAngle();
    return headings.computeIfAbsent(player.getUUID(), id -> new TravelHeading())
        .heading(headingTick, look.x, look.z);
  }

  public boolean ahead(ServerPlayer player, BlockPos pos) {
    return !session.settings.forwardSpawning
        || heading(player).ahead(pos.getX() + .5 - player.getX(), pos.getZ() + .5 - player.getZ());
  }

  /** Natural spawns on the server thread follow the nearest observer too. */
  public boolean naturalPosition(ServerLevel level, BlockPos pos) {
    // Asynchronous terrain generation must not read live players or their movement history.
    if (!session.settings.forwardSpawning || !level.getServer().isSameThread()) return true;
    var player = level.players().stream().filter(NearbyFauna::active)
        .filter(p -> horizontal(pos.getX(), pos.getZ(), p.getX(), p.getZ()) <= 128 * 128)
        .min(Comparator.comparingDouble(p -> horizontal(pos.getX(), pos.getZ(), p.getX(), p.getZ())));
    return player.isEmpty() || ahead(player.get(), pos);
  }

  /**
   * Non-overlapping 96-block areas; UUID order makes the choice independent of player-list order.
   */
  public static List<ServerPlayer> areas(List<ServerPlayer> players) {
    var result = new ArrayList<ServerPlayer>();
    players.stream()
        .filter(NearbyFauna::active)
        .sorted(Comparator.comparing(Entity::getUUID))
        .forEach(
            player -> {
              if (result.stream()
                  .noneMatch(
                      other ->
                          horizontal(player.getX(), player.getZ(), other.getX(), other.getZ())
                              < 192 * 192)) result.add(player);
            });
    return result;
  }

  private static double horizontal(double x, double z, double a, double b) {
    return (x - a) * (x - a) + (z - b) * (z - b);
  }

  public static boolean timeAllowed(String time, long sun) {
    long day = Math.floorMod(sun, 24000);
    return switch (time) {
      case "day" -> day < 12000;
      case "night" -> day >= 13000 && day < 23000;
      case "dusk" -> day >= 12000 && day < 14000 || day >= 22000;
      default -> true;
    };
  }

  public static boolean habitat(EntityType<?> type, ServerLevel level, BlockPos pos) {
    return habitat(type, level.getBiome(pos));
  }

  public static boolean habitat(EntityType<?> type, net.minecraft.core.Holder<Biome> biome) {
    if (FaunaTypes.id(type).equals("ecologics:squirrel")
        && (biome.is(BiomeTags.IS_FOREST)
            || biome
                .unwrapKey()
                .map(k -> k.location().toString().equals("minecraft:plains"))
                .orElse(false))) return true;
    String id = FaunaTypes.id(type);
    // Vanilla camels are village-generated, so they have no biome spawn-list entry.
    if (id.equals("minecraft:camel") || id.endsWith(":o_camel") || id.equals("ecologics:camel"))
      return biome.is(net.minecraft.world.level.biome.Biomes.DESERT)
          || biome.is(
              net.minecraft.tags.TagKey.create(
                  net.minecraft.core.registries.Registries.BIOME,
                  new ResourceLocation("forge:is_desert")));
    if (nativeHabitat(type, biome)) return true;
    if (id.startsWith("dragnlivestock:o_")) {
      String animal = id.substring("dragnlivestock:o_".length());
      try {
        Object flag =
            Class.forName("com.dragn0007.dragnlivestock.util.LivestockOverhaulCommonConfig")
                .getField(
                    "REPLACE_"
                        + switch (animal) {
                          case "sheep" -> "SHEEP";
                          case "wolf" -> "WOLVES";
                          default -> animal.toUpperCase(java.util.Locale.ROOT) + "S";
                        })
                .get(null);
        if (!((java.util.function.Supplier<?>) flag).get().equals(true)) return false;
        var vanilla =
            ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft", animal));
        return vanilla != null && nativeHabitat(vanilla, biome);
      } catch (ReflectiveOperationException e) {
        return false;
      }
    }
    return false;
  }

  private static boolean nativeHabitat(EntityType<?> type, net.minecraft.core.Holder<Biome> biome) {
    // Naturalist puts its ambient butterflies in the CREATURE pool. Inspect all pools.
    var settings = biome.value().getMobSettings();
    return settings.getSpawnerTypes().stream()
        .flatMap(category -> settings.getMobs(category).unwrap().stream())
        .anyMatch(s -> s.type == type && s.getWeight().asInt() > 0);
  }

  private boolean wilderness(ServerLevel level, BlockPos pos) {
    var land = session.population.land();
    return land.ready()
        && land.at(
                new Population.Animal(
                    new UUID(0, 0),
                    "",
                    level.dimension().location().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    "",
                    false,
                    true))
            .isEmpty();
  }

  public boolean usable(ServerLevel level, BlockPos pos, ServerPlayer player) {
    // Check the full neighborhood before querying height, blocks, biome or collision.
    if (!level.hasChunksAt(pos.offset(-8, -1, -8), pos.offset(8, 3, 8))
        || !level.getWorldBorder().isWithinBounds(pos)
        || !wilderness(level, pos)
        || !ahead(player, pos)
        || horizontal(pos.getX(), pos.getZ(), player.getX(), player.getZ()) > 96 * 96
        || Math.abs(player.getY() - pos.getY()) > 64) return false;
    return level.players().stream()
        .filter(NearbyFauna::active)
        .noneMatch(p -> p.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) < 32 * 32);
  }

  public int localCount(ServerLevel level, BlockPos pos, boolean small) {
    int count = 0;
    for (Mob mob :
        level.getEntitiesOfClass(
            Mob.class,
            new AABB(pos).inflate(96),
            m ->
                FaunaTypes.managed(m.getType())
                    && m.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) <= 96 * 96)) {
      var row = CATALOG.get(FaunaTypes.id(mob.getType()));
      if ((row == null && mob.getType().getCategory() != MobCategory.CREATURE)
          || Roaming.protectedAnimal(mob)
          || session.settings.domesticTypes.contains(FaunaTypes.id(mob.getType()))
              && !Roaming.natural(mob)) continue;
      if ((row != null && row.small()) == small) count++;
    }
    return count;
  }

  /** Called once per scheduled species/area. The same percentile is used for every candidate. */
  public int attempt(SpawnCatalog.Entry row, ServerPlayer player, int roll) {
    ServerLevel level = player.serverLevel();
    if (!active(player)
        || !enabledDimension(level)
        || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
        || !session.population.censusReady
        || !session.population.land().ready()
        || !timeAllowed(row.time(), level.getDayTime())) return 0;
    int month = month(level);
    if (month < 0) return 0;
    attempts++;
    int limit =
        row.small() ? session.settings.nearbySmallLimit : session.settings.nearbyGroundLimit;
    if (limit == 0 || localCount(level, player.blockPosition(), row.small()) >= limit) return 0;
    var random = level.random;
    for (int trial = 0; trial < 12; trial++) {
      double angle = session.settings.forwardSpawning
              ? heading(player).angle(random.nextDouble(), session.settings.forwardConeDegrees)
              : random.nextDouble() * Math.PI * 2,
          radius = Math.sqrt(32 * 32 + random.nextDouble() * (96 * 96 - 32 * 32));
      int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius),
          z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
      if (level.getChunkSource().getChunkNow(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null)
        continue;
      BlockPos center =
          new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
      if (!usable(level, center, player)
          || !SpawnCatalog.rolled(
              row.chance(month, level.getBiome(center).value().getBaseTemperature() >= 1.2f), roll))
        continue;
      var types =
          row.ids().stream()
              .map(ResourceLocation::new)
              .filter(ForgeRegistries.ENTITY_TYPES::containsKey)
              .map(ForgeRegistries.ENTITY_TYPES::getValue)
              .filter(Objects::nonNull)
              .filter(t -> FaunaTypes.managed(t) && habitat(t, level, center))
              .toList();
      if (types.isEmpty()) continue;
      var modded = types.stream().filter(t -> !FaunaTypes.id(t).startsWith("minecraft:")).toList();
      if (!modded.isEmpty()) types = modded;
      EntityType<?> type = types.get(random.nextInt(types.size()));
      int count = row.min() + random.nextInt(row.max() - row.min() + 1), made = 0;
      SpawnGroupData group = null;
      for (int i = 0; i < count; i++) {
        int bx = x + (i == 0 ? 0 : random.nextInt(9) - 4),
            bz = z + (i == 0 ? 0 : random.nextInt(9) - 4);
        BlockPos pos =
            new BlockPos(
                bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
        if (!usable(level, pos, player)
            || !habitat(type, level, pos)
            || !SpawnCatalog.rolled(
                row.chance(month, level.getBiome(pos).value().getBaseTemperature() >= 1.2f), roll)
            || localCount(level, pos, row.small()) >= limit
            || localCount(level, player.blockPosition(), row.small()) >= limit) continue;
        if (!SpawnPlacements.getPlacementType(type).canSpawnAt(level, pos, type)
            || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.EVENT, pos, random))
          continue;
        if (!(type.create(level) instanceof Mob mob)) continue;
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, random.nextFloat() * 360, 0);
        if (!level.noCollision(mob)
            || !ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.EVENT)) continue;
        Roaming.mark(mob, System.currentTimeMillis());
        group =
            ForgeEventFactory.onFinalizeSpawn(
                mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, group, null);
        if (mob.isSpawnCancelled()
            || !usable(level, mob.blockPosition(), player)
            || !level.noCollision(mob)) continue;
        if (level.addFreshEntity(mob)) {
          made++;
          spawned++;
        }
      }
      if (made > 0) return made;
    }
    return 0;
  }

  /**
   * Safe even with no players online, unavailable Towny, wall-clock rollback or old unmarked saves.
   */
  public boolean expire(Mob mob, long now) {
    if (mob.isRemoved() || !Roaming.natural(mob) || !(mob.level() instanceof ServerLevel level)) {
      forget(mob);
      return false;
    }
    if (Roaming.protectedAnimal(mob)) {
      Roaming.protect(mob);
      forget(mob);
      return false;
    }
    if (!session.population.land().ready()) return false;
    if (!wilderness(level, mob.blockPosition())) {
      Roaming.protect(mob);
      forget(mob);
      return false;
    }
    long last = mob.getPersistentData().getLong(Roaming.NEAR);
    boolean staleReturn =
        returning.remove(mob.getUUID())
            && Roaming.expired(now, last, session.settings.roamingSeconds);
    if (!staleReturn
        && (level.players().stream()
                .filter(p -> p.isAlive() && !p.isSpectator())
                .anyMatch(
                    p ->
                        p.distanceToSqr(mob)
                            <= (double) session.settings.roamingDistance
                                * session.settings.roamingDistance)
            || last <= 0
            || now < last)) {
      mob.getPersistentData().putLong(Roaming.NEAR, now);
      return false;
    }
    if (!Roaming.expired(now, last, session.settings.roamingSeconds)) return false;
    var tag = new CompoundTag();
    mob.saveWithoutId(tag);
    if (Roaming.protectedTag(tag)) {
      Roaming.protect(mob);
      return false;
    }
    mob.discard();
    expired++;
    return true;
  }

  public void tick(long tick) {
    headingTick = tick;
    if (tick % 5 == 0) {
      var online = new HashSet<UUID>();
      for (var level : session.server.getAllLevels()) for (var player : level.players()) {
        online.add(player.getUUID());
        headings.computeIfAbsent(player.getUUID(), id -> new TravelHeading())
            .observe(player.getX(), player.getZ(), tick);
      }
      headings.keySet().retainAll(online);
    }
    long now = System.currentTimeMillis(), start = System.nanoTime();
    if (!cleanup.hasNext()) cleanup = session.loaded.iterator();
    for (int i = 0; i < 16 && cleanup.hasNext() && System.nanoTime() - start < 2_000_000; i++)
      expire(cleanup.next(), now);
    if (!session.settings.nearbySpawning) {
      jobs.clear();
      nextCycle = 0;
      return;
    }
    if (now >= nextCycle) {
      jobs.clear();
      nextCycle = now + session.settings.nearbyIntervalSeconds * 1000L;
      var pending = new ArrayList<Job>();
      for (var level : session.server.getAllLevels())
        if (enabledDimension(level))
          for (var player : areas(level.players()))
            for (var row : SpawnCatalog.ENTRIES) pending.add(new Job(player.getUUID(), row, 0));
      Collections.shuffle(pending);
      for (int i = 0; i < pending.size(); i++) {
        var job = pending.get(i);
        jobs.add(new Job(job.player(), job.entry(), now + (nextCycle - now) * i / pending.size()));
      }
    }
    for (int i = 0;
        i < session.settings.spawnChecksPerTick
            && !jobs.isEmpty()
            && jobs.peek().due() <= now
            && System.nanoTime() - start < 2_000_000;
        i++) {
      var job = jobs.remove();
      var player = session.server.getPlayerList().getPlayer(job.player());
      if (player != null && areas(player.serverLevel().players()).contains(player))
        attempt(job.entry(), player, player.getRandom().nextInt(100));
    }
  }
}
