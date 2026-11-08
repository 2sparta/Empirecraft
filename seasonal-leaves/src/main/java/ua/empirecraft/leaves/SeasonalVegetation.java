package ua.empirecraft.leaves;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import sereneseasons.api.season.SeasonHelper;

/** Seasonal seed banks are saved atomically with the affected chunk, not in a global world map. */
public final class SeasonalVegetation {
  private static final String DATA = "empirecraft_seasonal_vegetation_v1";
  private static SeasonalVegetation instance;
  private final Map<ChunkAccess, CompoundTag> data =
      Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<ServerLevel, LinkedHashMap<Long, Work>> worlds = new IdentityHashMap<>();
  private final Map<ServerLevel, LinkedHashMap<Long, Work>> growthWorlds = new IdentityHashMap<>();
  // Diagnostics use operation counts, not a profiler hook in the inner loop.
  private ScanBudget lastScan, lastGrowth;
  private final WildernessAccess wilderness = new WildernessAccess();

  SeasonalVegetation() {
    instance = this;
    MinecraftForge.EVENT_BUS.addListener(this::read);
    MinecraftForge.EVENT_BUS.addListener(this::save);
    MinecraftForge.EVENT_BUS.addListener(this::load);
    MinecraftForge.EVENT_BUS.addListener(this::unload);
    MinecraftForge.EVENT_BUS.addListener(this::tick);
    MinecraftForge.EVENT_BUS.addListener(this::placed);
    MinecraftForge.EVENT_BUS.addListener(this::broken);
    MinecraftForge.EVENT_BUS.addListener(this::stopped);
  }

  private void stopped(ServerStoppedEvent event) {
    worlds.clear();
    growthWorlds.clear();
    data.clear();
    instance = null;
  }

  private void read(ChunkDataEvent.Load e) {
    data.put(e.getChunk(), e.getData().getCompound(DATA).copy());
  }

  private void save(ChunkDataEvent.Save e) {
    var tag = data.get(e.getChunk());
    if (tag != null) e.getData().put(DATA, tag.copy());
  }

  private void load(ChunkEvent.Load e) {
    if (e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk chunk) {
      Work work = new Work(chunk, data.computeIfAbsent(chunk, x -> new CompoundTag()));
      worlds.computeIfAbsent(level, x -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), work);
      growthWorlds
          .computeIfAbsent(level, x -> new LinkedHashMap<>())
          .put(chunk.getPos().toLong(), work);
    }
  }

  private void unload(ChunkEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l) {
      var map = worlds.get(l);
      if (map != null) map.remove(e.getChunk().getPos().toLong());
      var growth = growthWorlds.get(l);
      if (growth != null) growth.remove(e.getChunk().getPos().toLong());
    }
  }

  private Work work(ServerLevel l, BlockPos p) {
    var map = worlds.get(l);
    return map == null
        ? null
        : map.get(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
  }

  private static String key(BlockPos p) {
    return Long.toString(p.asLong());
  }

  private void placed(BlockEvent.EntityPlaceEvent e) {
    if (!(e.getLevel() instanceof ServerLevel l) || !(e.getEntity() instanceof Player)) return;
    if (e instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
      for (var snapshot : multiple.getReplacedBlockSnapshots()) protect(l, snapshot.getPos());
    } else protect(l, e.getPos());
  }

  private void protect(ServerLevel l, BlockPos p) {
    Work w = work(l, p);
    if (w == null) return;
    cancel(w, p);
    cancel(w, p.below());
    w.tag.getCompound("NaturalTrees").remove(key(p));
    w.dirty();
    BlockState state = l.getBlockState(p);
    if (PlantCatalog.profile(state) != null || state.is(Blocks.SNOW)) {
      w.protectedPos.add(p.asLong());
      if (upper(state)) w.protectedPos.add(p.below().asLong());
      w.dirty();
    }
  }

  private void broken(BlockEvent.BreakEvent e) {
    if (e.getLevel() instanceof ServerLevel l) {
      Work w = work(l, e.getPos());
      if (w != null) {
        BlockPos p = e.getPos();
        cancel(w, p);
        cancel(w, p.below());
        w.tag.getCompound("NaturalTrees").remove(key(p));
        w.dirty();
        boolean changed = w.protectedPos.remove(p.asLong());
        if (tall(e.getState()))
          changed |= w.protectedPos.remove((upper(e.getState()) ? p.below() : p.above()).asLong());
        if (changed) w.dirty();
      }
    }
  }

  private static void cancel(Work w, BlockPos p) {
    if (w.records.contains(key(p))) {
      w.records.remove(key(p));
      w.dirty();
    }
  }

  private static boolean enabled(ServerLevel l) {
    return SeasonalLeaves.enabled()
        && VegetationConfig.ENABLED.get()
        && sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(l.dimension());
  }

  private static boolean waterlogged(BlockState s) {
    return !s.getFluidState().isEmpty()
        || s.hasProperty(BlockStateProperties.WATERLOGGED)
            && s.getValue(BlockStateProperties.WATERLOGGED);
  }

  private static boolean upper(BlockState s) {
    return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
        && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
  }

  private static boolean tall(BlockState s) {
    return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
  }

  private static double visible(ServerLevel l, BlockPos p, PlantCatalog.Profile profile) {
    if (!enabled(l) || SeasonHelper.usesTropicalSeasons(l.getBiome(p))) return 1;
    var season = SeasonHelper.getSeasonState(l);
    return VegetationPolicy.visibility(
        profile.seasons(), season.getSeasonCycleTicks(), season.getSubSeasonDuration());
  }

  private static boolean inSeason(
      ServerLevel l, BlockPos p, BlockState s, PlantCatalog.Profile profile) {
    return SeasonCurve.threshold(p.asLong() ^ PlantCatalog.id(s).hashCode())
        < visible(l, p, profile);
  }

  private static boolean replaceable(BlockState s) {
    return s.isAir() || s.is(Blocks.SNOW);
  }

  private static boolean soil(ServerLevel l, BlockPos p) {
    return VegetationPolicy.SOILS.contains(PlantCatalog.id(l.getBlockState(p.below())));
  }

  private static int rareCount(ServerLevel l, int cx, int cz) {
    var chunk = l.getChunkSource().getChunkNow(cx, cz);
    if (chunk == null) return Integer.MAX_VALUE;
    int count = 0;
    for (int x = cx * 16; x < cx * 16 + 16; x++)
      for (int z = cz * 16; z < cz * 16 + 16; z++) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        for (int d = 0; d < 4; d++) {
          BlockState state = chunk.getBlockState(new BlockPos(x, y - d, z));
          if (VegetationPolicy.RARE.contains(PlantCatalog.id(state)) && !upper(state)) {
            count++;
            break;
          }
        }
      }
    return count;
  }

  private boolean rareAllowed(ServerLevel l, BlockPos p, Work w) {
    return VegetationPolicy.rareChunk(
            l.getSeed(), p.getX() >> 4, p.getZ() >> 4, VegetationConfig.RARE_CHUNKS.get())
        && (w == null || l.getGameTime() >= w.tag.getLong("RareUntil"))
        && rareCount(l, p.getX() >> 4, p.getZ() >> 4) == 0;
  }

  private void rareSeen(ServerLevel l, Work w) {
    long until =
        l.getGameTime()
            + (long) VegetationConfig.RARE_DAYS.get()
                * SeasonHelper.getSeasonState(l).getDayDuration();
    if (until > w.tag.getLong("RareUntil")) {
      w.tag.putLong("RareUntil", until);
      w.dirty();
    }
  }

  /** Called by a small optional Mixin at the existing planter's candidate-list insertion. */
  public static boolean allowRenewal(ServerLevel l, int cx, int cz, BlockState state) {
    if (!PlantGrowth.enabled()) return false;
    if (instance == null || !enabled(l)) return true;
    BlockPos p = new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
    PlantCatalog.Profile profile = PlantCatalog.profile(state);
    if (profile != null && !inSeason(l, p, state, profile)) return false;
    return !VegetationPolicy.RARE.contains(PlantCatalog.id(state))
        || instance.rareAllowed(l, p, instance.work(l, p));
  }

  /** Record a successful native appearance before a player can harvest it between scans. */
  public static void renewalComplete(ServerLevel l, int cx, int cz) {
    if (instance == null
        || !enabled(l)
        || !VegetationPolicy.rareChunk(l.getSeed(), cx, cz, VegetationConfig.RARE_CHUNKS.get()))
      return;
    BlockPos p = new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
    Work w = instance.work(l, p);
    if (w != null && rareCount(l, cx, cz) > 0) instance.rareSeen(l, w);
  }

  private boolean restore(ServerLevel l, Work w, String k) {
    if (!PlantGrowth.enabled()) return false;
    CompoundTag r = w.records.getCompound(k);
    BlockPos p = BlockPos.of(r.getLong("pos"));
    var id = ResourceLocation.tryParse(r.getCompound("state").getString("Name"));
    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
    BlockState original =
        NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), r.getCompound("state"));
    var profile = PlantCatalog.profile(original);
    if (profile != null && !inSeason(l, p, original, profile)) return false;
    if (enabled(l)
        && VegetationPolicy.RARE.contains(PlantCatalog.id(original))
        && !rareAllowed(l, p, w)) return false;
    if (!wilderness.allowed(l, p)) return false;
    boolean doubled = r.contains("upper", Tag.TAG_COMPOUND);
    // Changed soil, player replacements, and occupied upper halves cancel the seed bank entry.
    if (!replaceable(l.getBlockState(p))
        || doubled && !replaceable(l.getBlockState(p.above()))
        || !soil(l, p)
        || !original.canSurvive(l, p)) {
      w.records.remove(k);
      w.dirty();
      return true;
    }
    if (doubled) {
      var topId = ResourceLocation.tryParse(r.getCompound("upper").getString("Name"));
      if (topId == null || !BuiltInRegistries.BLOCK.containsKey(topId)) return false;
      BlockState top =
          NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), r.getCompound("upper"));
      l.setBlock(p, original, Block.UPDATE_CLIENTS);
      l.setBlock(p.above(), top, Block.UPDATE_CLIENTS);
    } else l.setBlock(p, original, Block.UPDATE_CLIENTS);
    w.records.remove(k);
    w.dirty();
    if (VegetationPolicy.RARE.contains(PlantCatalog.id(original))) rareSeen(l, w);
    return true;
  }

  private boolean hide(ServerLevel l, Work w, BlockPos p, BlockState state) {
    var profile = PlantCatalog.profile(state);
    if (profile == null
        || upper(state)
        || waterlogged(state)
        || !soil(l, p)
        || w.protectedPos.contains(p.asLong())
        || !l.canSeeSky(p.above())
        || inSeason(l, p, state, profile)) return false;
    if (!wilderness.allowed(l, p) || w.records.size() >= 32768) return false;
    boolean doubled = tall(state);
    BlockState top = l.getBlockState(p.above());
    if (doubled && (!top.is(state.getBlock()) || !upper(top) || waterlogged(top))) return false;
    CompoundTag record = new CompoundTag();
    record.putLong("pos", p.asLong());
    record.put("state", NbtUtils.writeBlockState(state));
    if (doubled) record.put("upper", NbtUtils.writeBlockState(top));
    w.records.put(key(p), record);
    w.dirty();
    if (doubled) l.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    return true;
  }

  private int groundCount(ServerLevel l, Work w) {
    int count = 0;
    var c = w.chunk;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int y = c.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int d = 0; d < 3; d++)
          if (PlantCatalog.profile(
                  c.getBlockState(
                      new BlockPos(
                          c.getPos().getMinBlockX() + x, y - d, c.getPos().getMinBlockZ() + z)))
              != null) {
            count++;
            break;
          }
      }
    return count;
  }

  private boolean grow(ServerLevel l, Work w) {
    if (!PlantGrowth.enabled()) return false;
    if (!enabled(l)
        || !VegetationConfig.SPAWN.get()
        || groundCount(l, w) >= VegetationConfig.DENSITY.get()) return false;
    var random = l.getRandom();
    for (int attempt = 0; attempt < 8; attempt++) {
      int x = w.chunk.getPos().getMinBlockX() + random.nextInt(16),
          z = w.chunk.getPos().getMinBlockZ() + random.nextInt(16);
      BlockPos p = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
      if (!l.getBlockState(p).isAir()
          || !soil(l, p)
          || !l.canSeeSky(p)
          || !wilderness.allowed(l, p)
          || w.protectedPos.contains(p.asLong())
          || w.records.contains(key(p))) continue;
      var choices = new ArrayList<BlockState>();
      // Grass dominates; other flora spreads only from this chunk's observed native seed bank.
      var seeds = new HashSet<>(w.seeds);
      seeds.add("minecraft:grass");
      boolean flowers = random.nextInt(8) == 0;
      for (String id : seeds) {
        if (!flowers && !id.equals("minecraft:grass") && !id.equals("minecraft:fern")) continue;
        ResourceLocation name = ResourceLocation.tryParse(id);
        if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) continue;
        BlockState s = BuiltInRegistries.BLOCK.get(name).defaultBlockState();
        var profile = PlantCatalog.profile(s);
        if (profile == null
            || profile.renewal()
            || tall(s)
            || waterlogged(s)
            || !inSeason(l, p, s, profile)
            || !VegetationPolicy.climate(
                profile.climate(), l.getBiome(p).value().getBaseTemperature())
            || !s.canSurvive(l, p)) continue;
        choices.add(s);
      }
      if (!choices.isEmpty())
        return l.setBlock(p, choices.get(random.nextInt(choices.size())), Block.UPDATE_CLIENTS);
    }
    return false;
  }

  private void tick(TickEvent.LevelTickEvent e) {
    if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l)) return;
    var map = worlds.get(l);
    if (map == null || map.isEmpty()) return;
    var season = SeasonHelper.getSeasonState(l);
    int duration = season.getSubSeasonDuration();
    int current =
        duration > 0
            ? (int)
                (Math.floorMod((long) season.getSeasonCycleTicks(), 12L * duration)
                    / (3L * duration))
            : -1;
    boolean winter = current == 3, summer = current == 1;
    // Cleanup never spends its budget on restoration. Existing configs retain these independent
    // limits.
    int scans = winter || summer ? VegetationConfig.FAST_SCAN.get() : VegetationConfig.SCAN.get();
    int changes =
        winter || summer ? VegetationConfig.FAST_CHANGES.get() : VegetationConfig.CHANGES.get();
    var budget =
        new ScanBudget(
            scans,
            VegetationConfig.SECTIONS.get(),
            VegetationConfig.CHUNKS.get(),
            VegetationConfig.MICROS.get());
    lastScan = budget;
    int visits = map.size();
    while (enabled(l) && visits-- > 0 && scans > 0 && changes > 0 && budget.chunk()) {
      var it = map.entrySet().iterator();
      var entry = it.next();
      Work w = entry.getValue();
      it.remove();
      map.put(entry.getKey(), w);
      if (l.getChunkSource().getChunkNow(w.chunk.getPos().x, w.chunk.getPos().z) != w.chunk)
        continue;
      if (!l.getWorldBorder().isWithinBounds(w.chunk.getPos().getWorldPosition())) continue;
      if (w.lastSeason != current) {
        w.lastSeason = current;
        w.nextScan = 0;
      }
      if (l.getGameTime() < w.nextScan) continue;
      var sections = w.chunk.getSections();
      // Limit each chunk's slice so a dense chunk cannot monopolize the queue.
      int slice = Math.min(scans, 4096);
      int checkedSection = -1;
      while (w.cursor < sections.length * 4096 && slice > 0 && changes > 0) {
        int index = w.cursor >>> 12;
        var section = sections[index];
        if (index != checkedSection) {
          if (!budget.section()) break;
          checkedSection = index;
          if (!section.hasOnlyAir()) budget.palettes++;
          if (section.hasOnlyAir()
              || !section.maybeHas(
                  state ->
                      PlantCatalog.profile(state) != null || summer && state.is(Blocks.SNOW))) {
            w.cursor = (index + 1) * 4096;
            scans--;
            slice--;
            continue;
          }
        }
        if (!budget.position()) break;
        int local = w.cursor++ & 4095;
        scans--;
        slice--;
        BlockState state = section.getBlockState(local & 15, local >>> 8, (local >>> 4) & 15);
        boolean snow = summer && state.is(Blocks.SNOW);
        var profile = PlantCatalog.profile(state);
        if (!snow && (profile == null || upper(state))) continue;
        BlockPos pos =
            new BlockPos(
                w.chunk.getPos().getMinBlockX() + (local & 15),
                w.chunk.getMinBuildHeight() + index * 16 + (local >>> 8),
                w.chunk.getPos().getMinBlockZ() + ((local >>> 4) & 15));
        if (snow && melt(l, w, pos)) {
          changes--;
          continue;
        }
        if (profile == null || upper(state)) continue;
        if (!profile.renewal() && w.seeds.add(PlantCatalog.id(state))) w.dirty();
        if (VegetationPolicy.RARE.contains(PlantCatalog.id(state))) rareSeen(l, w);
        if (hide(l, w, pos, state)) changes--;
      }
      if (w.cursor >= sections.length * 4096) {
        w.cursor = 0;
        w.nextScan =
            l.getGameTime()
                + (winter || summer
                    ? VegetationConfig.FAST_DELAY.get()
                    : VegetationConfig.DELAY.get());
      }
    }
    if (!PlantGrowth.enabled()) return;
    // One appearance per level at a time; persisted per-chunk delay also applies after reloads.
    if (l.getGameTime() % VegetationConfig.GROW_INTERVAL.get() != 0) return;
    var growth = growthWorlds.get(l);
    if (growth == null) return;
    var appearance =
        new ScanBudget(
            8192,
            Integer.MAX_VALUE,
            VegetationConfig.GROW_CHUNKS.get(),
            VegetationConfig.MICROS.get());
    lastGrowth = appearance;
    int candidates = growth.size();
    while (candidates-- > 0 && appearance.chunk()) {
      var iterator = growth.entrySet().iterator();
      var entry = iterator.next();
      Work w = entry.getValue();
      iterator.remove();
      growth.put(entry.getKey(), w);
      if (l.getChunkSource().getChunkNow(w.chunk.getPos().x, w.chunk.getPos().z) != w.chunk)
        continue;
      long now = l.getGameTime();
      if (!w.tag.contains("GrowthAt")) {
        scheduleGrowth(l, w);
        continue;
      }
      if (now < w.tag.getLong("GrowthAt")) continue;
      scheduleGrowth(l, w);
      if (w.restore == null || w.restore.isEmpty()) {
        var keys = new ArrayList<>(w.records.getAllKeys());
        Collections.shuffle(keys);
        w.restore = new ArrayDeque<>(keys);
      }
      // Bounded search: out-of-season records cannot block other chunks or removal.
      int remaining = 64;
      while (!w.restore.isEmpty() && remaining-- > 0) {
        if (!appearance.position()) break;
        String key = w.restore.remove();
        if (w.records.contains(key, 10) && restore(l, w, key)) return;
      }
      if (appearance.time() && grow(l, w)) return;
    }
  }

  private void scheduleGrowth(ServerLevel l, Work w) {
    int delay = VegetationConfig.GROW_DELAY.get();
    w.tag.putLong("GrowthAt", l.getGameTime() + delay + l.getRandom().nextInt(delay));
    w.dirty();
  }

  private boolean melt(ServerLevel l, Work w, BlockPos p) {
    if (!enabled(l)
        || w.protectedPos.contains(p.asLong())
        || !l.canSeeSky(p.above())
        || SeasonHelper.usesTropicalSeasons(l.getBiome(p))
        // Preserve snow in naturally freezing biomes, on mountains, and in protected claims.
        || l.getBiome(p).value().coldEnoughToSnow(p)
        || !wilderness.allowed(l, p)) return false;
    return l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
  }

  public static boolean allowNativeAttempt(ServerLevel l, int cx, int cz, boolean tree) {
    if (!PlantGrowth.enabled()) return false;
    if (instance == null || !enabled(l)) return true;
    BlockPos p = new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
    Work w = instance.work(l, p);
    if (w == null) return false;
    String key = tree ? "NativeTreeAt" : "NativePlantAt";
    long now = l.getGameTime();
    if (!w.tag.contains(key)) {
      w.tag.putLong(
          key,
          now + (tree ? VegetationConfig.TREE_DELAY.get() : VegetationConfig.NATIVE_DELAY.get()));
      w.dirty();
      return false;
    }
    if (now < w.tag.getLong(key)) return false;
    int delay = tree ? VegetationConfig.TREE_DELAY.get() : VegetationConfig.NATIVE_DELAY.get();
    w.tag.putLong(key, now + delay + l.getRandom().nextInt(delay));
    w.dirty();
    if (nearPlayer(l, p, tree ? VegetationConfig.TREE_DISTANCE.get() + 12 : 24)) return false;
    if (tree) {
      var season = SeasonHelper.getSeasonState(l);
      if (season.getSeason().toString().equals("WINTER")) return false;
    }
    return l.getRandom().nextInt(tree ? 32 : 8) == 0;
  }

  private static boolean nearPlayer(ServerLevel l, BlockPos pos, int radius) {
    for (var player : l.players()) {
      double dx = player.getX() - (pos.getX() + .5), dz = player.getZ() - (pos.getZ() + .5);
      if (dx * dx + dz * dz < (double) radius * radius) return true;
    }
    return false;
  }

  public static BlockState youngTree(BlockState state) {
    return state.hasProperty(SaplingBlock.STAGE) ? state.setValue(SaplingBlock.STAGE, 0) : state;
  }

  public static void rememberTree(ServerLevel l, BlockPos p) {
    if (instance == null) return;
    Work w = instance.work(l, p);
    if (w == null) return;
    CompoundTag record = new CompoundTag();
    record.putString("id", PlantCatalog.id(l.getBlockState(p)));
    record.putLong("ready", l.getGameTime() + VegetationConfig.TREE_AGE.get());
    w.tag.getCompound("NaturalTrees").put(key(p), record);
    w.dirty();
  }

  public static boolean delayTree(ServerLevel l, BlockPos p) {
    if (!PlantGrowth.enabled() && managedTree(l, p, l.getBlockState(p))) return true;
    if (instance == null || !enabled(l)) return false;
    Work w = instance.work(l, p);
    if (w == null) return false;
    var trees = w.tag.getCompound("NaturalTrees");
    String key = key(p);
    if (!trees.contains(key)) return false;
    var record = trees.getCompound(key);
    if (!record.getString("id").equals(PlantCatalog.id(l.getBlockState(p)))) {
      trees.remove(key);
      w.dirty();
      return false;
    }
    return l.getGameTime() < record.getLong("ready")
        || nearPlayer(l, p, VegetationConfig.TREE_DISTANCE.get());
  }

  public static boolean managedTree(ServerLevel level, BlockPos pos, BlockState state) {
    if (instance == null) return false;
    Work work = instance.work(level, pos);
    if (work == null) return false;
    var trees = work.tag.getCompound("NaturalTrees");
    String key = key(pos);
    return trees.contains(key, Tag.TAG_COMPOUND)
        && trees.getCompound(key).getString("id").equals(PlantCatalog.id(state));
  }

  private static final class Work {
    final LevelChunk chunk;
    final CompoundTag tag, records;
    final Set<Long> protectedPos = new HashSet<>();
    final Set<String> seeds = new HashSet<>();
    ArrayDeque<String> restore;
    int cursor;
    long nextScan;
    int lastSeason = -2;

    Work(LevelChunk chunk, CompoundTag tag) {
      this.chunk = chunk;
      this.tag = tag;
      if (!tag.contains("NaturalTrees", Tag.TAG_COMPOUND))
        tag.put("NaturalTrees", new CompoundTag());
      records = tag.getCompound("Dormant");
      tag.put("Dormant", records);
      for (long pos : tag.getLongArray("Protected")) protectedPos.add(pos);
      for (Tag value : tag.getList("Seeds", Tag.TAG_STRING)) seeds.add(value.getAsString());
    }

    void dirty() {
      tag.putLongArray("Protected", protectedPos.stream().mapToLong(Long::longValue).toArray());
      var list = new ListTag();
      seeds.stream().sorted().forEach(s -> list.add(StringTag.valueOf(s)));
      tag.put("Seeds", list);
      chunk.setUnsaved(true);
    }
  }
}
