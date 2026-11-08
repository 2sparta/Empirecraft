package ua.empirecraft.leaves.test;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import ua.empirecraft.leaves.SeasonalVegetation;

/**
 * Synthetic 14k-entry workload through the real old/new tick, followed by real datapack reloads.
 */
final class PerformanceScenario {
  static boolean slowUpdates;
  static int slowUpdatesSeen;
  final MinecraftServer server;
  final ServerLevel level;
  final boolean optimized = !Boolean.getBoolean("leaves.baseline");
  final JsonObject result = new JsonObject();
  CompletableFuture<Void> reload;
  int stage, checks;
  Method profile;
  Object vegetation;

  PerformanceScenario(MinecraftServer server, ServerLevel level) throws Exception {
    this.server = server;
    this.level = level;
    benchmark();
    if (!optimized) {
      finish();
      return;
    }
    Class<?> catalog = Class.forName("ua.empirecraft.leaves.PlantCatalog");
    profile = catalog.getDeclaredMethod("profile", BlockState.class);
    profile.setAccessible(true);
    BlockState grass = Blocks.GRASS.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
    Object first = profile.invoke(null, grass);
    check(first != null, "grass is managed before reload");
    for (int i = 0; i < 10000; i++) {
      check(profile.invoke(null, grass) == first, "positive profile reuses the same cached object");
      check(profile.invoke(null, stone) == null, "negative profile remains excluded");
    }
    var cache = (Map<?, ?>) value(catalog, null, "profiles");
    check(cache.containsKey(stone), "negative answers are cached");
    Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("seasonal-cache-proof");
    Files.createDirectories(pack);
    Files.writeString(
        pack.resolve("pack.mcmeta"),
        "{\"pack\":{\"pack_format\":15,\"description\":\"TEST ONLY cache invalidation\"}}");
    Path tags = pack.resolve("data/empirecraft_seasonal_leaves/tags/blocks");
    Files.createDirectories(tags);
    Files.writeString(
        tags.resolve("evergreen_vegetation.json"), "{\"values\":[\"minecraft:grass\"]}");
    Files.writeString(
        tags.resolve("additional_vegetation.json"), "{\"values\":[\"minecraft:stone\"]}");
    server.getPackRepository().reload();
    var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
    selected.add("file/seasonal-cache-proof");
    reload = server.reloadResources(selected);
    stage = 1;
  }

  static Object value(Class<?> type, Object owner, String name) throws Exception {
    var f = type.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(owner);
  }

  static Object value(Object owner, String name) throws Exception {
    return value(owner.getClass(), owner, name);
  }

  static void config(Class<?> type, String name, Object value) throws Exception {
    Object option = value(type, null, name);
    option.getClass().getMethod("set", Object.class).invoke(option, value);
  }

  void check(boolean test, String message) {
    checks++;
    if (!test) throw new AssertionError(message);
  }

  void benchmark() throws Exception {
    Class<?> type = SeasonalVegetation.class,
        config = Class.forName("ua.empirecraft.leaves.VegetationConfig");
    vegetation = value(type, null, "instance");
    server
        .getCommands()
        .getDispatcher()
        .execute("plantgrowth off", server.createCommandSourceStack().withPermission(4));
    config(config, "FAST_SCAN", 32768);
    config(config, "FAST_CHANGES", 64);
    config(config, "WILDERNESS", true);
    if (optimized) {
      config(config, "SECTIONS", 32);
      config(config, "CHUNKS", 128);
      config(config, "MICROS", 1000);
      config(config, "GROW_CHUNKS", 128);
    }
    LevelChunk chunk = level.getChunk(0, 0);
    var section = chunk.getSection(chunk.getSectionIndex(80));
    Block[] inert = {
      Blocks.STONE,
      Blocks.GLASS,
      Blocks.WHITE_STAINED_GLASS,
      Blocks.RED_STAINED_GLASS,
      Blocks.BLUE_STAINED_GLASS,
      Blocks.GREEN_STAINED_GLASS,
      Blocks.BLACK_STAINED_GLASS,
      Blocks.YELLOW_STAINED_GLASS,
      Blocks.ORANGE_STAINED_GLASS,
      Blocks.PURPLE_STAINED_GLASS,
      Blocks.WHITE_CONCRETE,
      Blocks.RED_CONCRETE,
      Blocks.BLUE_CONCRETE,
      Blocks.GREEN_CONCRETE,
      Blocks.BLACK_CONCRETE,
      Blocks.YELLOW_CONCRETE,
      Blocks.ORANGE_CONCRETE,
      Blocks.PURPLE_CONCRETE,
      Blocks.GRANITE,
      Blocks.DIORITE,
      Blocks.ANDESITE,
      Blocks.COBBLESTONE,
      Blocks.MOSSY_COBBLESTONE,
      Blocks.TUFF,
      Blocks.DEEPSLATE,
      Blocks.OBSIDIAN,
      Blocks.BRICKS,
      Blocks.STONE_BRICKS,
      Blocks.END_STONE,
      Blocks.NETHERRACK,
      Blocks.QUARTZ_BLOCK,
      Blocks.MAGMA_BLOCK
    };
    for (int i = 0; i < 4096; i++)
      section.setBlockState(
          i & 15,
          i >>> 8,
          (i >>> 4) & 15,
          (i == 4095 ? Blocks.GRASS : inert[i % inert.length]).defaultBlockState(),
          false);
    Class<?> work = Class.forName("ua.empirecraft.leaves.SeasonalVegetation$Work");
    var ctor = work.getDeclaredConstructor(LevelChunk.class, net.minecraft.nbt.CompoundTag.class);
    ctor.setAccessible(true);
    var cursor = work.getDeclaredField("cursor");
    cursor.setAccessible(true);
    @SuppressWarnings("unchecked")
    var worlds = (Map<ServerLevel, LinkedHashMap<Long, Object>>) value(vegetation, "worlds");
    var previous = worlds.get(level);
    var queue = new LinkedHashMap<Long, Object>();
    for (int i = 0; i < 14000; i++) {
      Object item = ctor.newInstance(chunk, new net.minecraft.nbt.CompoundTag());
      cursor.setInt(item, chunk.getSectionIndex(80) * 4096);
      @SuppressWarnings("unchecked")
      var seeds = (Set<String>) value(item, "seeds");
      seeds.add("minecraft:grass");
      ((net.minecraft.nbt.CompoundTag) value(item, "tag")).putLong("GrowthAt", Long.MAX_VALUE);
      queue.put((long) i, item);
    }
    worlds.put(level, queue);
    Method tick = type.getDeclaredMethod("tick", TickEvent.LevelTickEvent.class);
    tick.setAccessible(true);
    var event =
        new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true);
    long[] samples = new long[30];
    int maxSections = 0, maxPalettes = 0, maxPositions = 0, maxChunks = 0;
    for (int i = -10; i < samples.length; i++) {
      long start = System.nanoTime();
      tick.invoke(vegetation, event);
      long elapsed = System.nanoTime() - start;
      if (i >= 0) samples[i] = elapsed;
      if (optimized) {
        Object budget = value(vegetation, "lastScan");
        int sections = ((Number) value(budget, "sections")).intValue(),
            palettes = ((Number) value(budget, "palettes")).intValue(),
            positions = ((Number) value(budget, "positions")).intValue(),
            chunks = ((Number) value(budget, "chunks")).intValue();
        check(
            sections <= 32 && palettes <= sections && positions <= 32768 && chunks <= 128,
            "hard scan budgets hold at 14k queued chunks");
        maxSections = Math.max(maxSections, sections);
        maxPalettes = Math.max(maxPalettes, palettes);
        maxPositions = Math.max(maxPositions, positions);
        maxChunks = Math.max(maxChunks, chunks);
      }
    }
    check(!section.getBlockState(15, 15, 15).isAir(), "benchmark does not delete the plant");
    if (optimized) check(maxPositions > 0, "bounded scanning still advances");
    Arrays.sort(samples);
    result.addProperty("median_tick_ms", samples[samples.length / 2] / 1e6);
    result.addProperty("p95_tick_ms", samples[(int) (samples.length * .95)] / 1e6);
    result.addProperty("max_tick_ms", samples[samples.length - 1] / 1e6);
    result.addProperty("queued_work_entries", 14000);
    result.addProperty("actual_test_chunks", 1);
    result.addProperty("warmup_ticks", 10);
    result.addProperty("measured_ticks", 30);
    if (optimized) {
      result.addProperty("max_sections", maxSections);
      result.addProperty("max_palettes", maxPalettes);
      result.addProperty("max_positions", maxPositions);
      result.addProperty("max_chunk_visits", maxChunks);
    }
    // Cooldown-only queues must be bounded too. Growth uses an independent rotation.
    for (Object item : queue.values()) {
      var f = work.getDeclaredField("nextScan");
      f.setAccessible(true);
      f.setLong(item, Long.MAX_VALUE);
    }
    tick.invoke(vegetation, event);
    if (optimized) {
      check(
          ((Number) value(value(vegetation, "lastScan"), "chunks")).intValue() <= 128,
          "cooldown-only scan visits bounded");
      @SuppressWarnings("unchecked")
      var growth =
          (Map<ServerLevel, LinkedHashMap<Long, Object>>) value(vegetation, "growthWorlds");
      var original = growth.put(level, new LinkedHashMap<>(queue));
      config(config, "GROWTH", true);
      config(config, "GROW_INTERVAL", 20);
      ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(4000);
      tick.invoke(vegetation, event);
      Object budget = value(vegetation, "lastGrowth");
      check(
          budget != null && ((Number) value(budget, "chunks")).intValue() <= 128,
          "growth visits bounded independently of population");
      growth.put(level, original);

      // Simulate an expensive physics/block callback. Only the current edit may overrun.
      config(config, "GROWTH", false);
      config(config, "WILDERNESS", false);
      for (int x = 1; x <= 4; x++) {
        level.setBlock(new BlockPos(x, 111, 1), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, 112, 1), GrowthSmokeMod.PLANT.get().defaultBlockState(), 2);
      }
      Object single = ctor.newInstance(chunk, new net.minecraft.nbt.CompoundTag());
      cursor.setInt(single, chunk.getSectionIndex(112) * 4096);
      var one = new LinkedHashMap<Long, Object>();
      one.put(0L, single);
      worlds.put(level, one);
      slowUpdatesSeen = 0;
      slowUpdates = true;
      try {
        tick.invoke(vegetation, event);
      } finally {
        slowUpdates = false;
      }
      check(
          slowUpdatesSeen == 1,
          "one slow block callback finishes; later changes yield to the next tick: "
              + slowUpdatesSeen);
      check(
          level.getBlockState(new BlockPos(2, 112, 1)).is(GrowthSmokeMod.PLANT.get()),
          "unprocessed candidate remains intact");
      check(
          cursor.getInt(single) == chunk.getSectionIndex(112) * 4096 + 18,
          "candidate cursor is retained on time yield");
      result.addProperty("slow_update_callbacks_in_slice", slowUpdatesSeen);
    }
    worlds.put(level, previous);
    config(config, "ENABLED", false);
    config(config, "GROWTH", false);
  }

  void tick() throws Exception {
    if (reload == null || !reload.isDone()) return;
    reload.join();
    BlockState grass = Blocks.GRASS.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
    if (stage == 1) {
      check(
          profile.invoke(null, grass) == null, "datapack KEEP invalidates positive profile cache");
      check(
          profile.invoke(null, stone) != null, "datapack EXTRA invalidates negative profile cache");
      var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
      selected.remove("file/seasonal-cache-proof");
      reload = server.reloadResources(selected);
      stage = 2;
    } else {
      check(
          profile.invoke(null, grass) != null, "disabling datapack restores grass classification");
      check(
          profile.invoke(null, stone) == null,
          "disabling datapack restores negative classification");
      result.addProperty("actual_tag_reloads", 2);
      finish();
    }
  }

  void finish() throws Exception {
    result.addProperty("optimized", optimized);
    result.addProperty("assertions", checks);
    Files.writeString(
        Path.of("seasonal-performance.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(result));
    System.out.println("SEASONAL_PERFORMANCE_PASS " + result);
    reload = null;
    server.halt(false);
  }
}
