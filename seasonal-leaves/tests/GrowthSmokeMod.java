package ua.empirecraft.leaves.test;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.leaves.*;

/** Test-only mod. The runner always supplies a new disposable world. */
@Mod("empirecraft_growth_test")
public final class GrowthSmokeMod {
  static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, "empirecraft_growth_test");
  static final RegistryObject<Block> PLANT = BLOCKS.register("plant", () -> new TestPlant());
  static final RegistryObject<Block> CROP = BLOCKS.register("crop", () -> new TestCrop());

  public static final class TestPlant extends SweetBerryBushBlock {
    TestPlant() {
      super(BlockBehaviour.Properties.copy(Blocks.SWEET_BERRY_BUSH));
    }

    @Override
    public void onRemove(
        BlockState state,
        net.minecraft.world.level.Level level,
        BlockPos pos,
        BlockState replacement,
        boolean moving) {
      if (PerformanceScenario.slowUpdates && !replacement.is(state.getBlock())) {
        PerformanceScenario.slowUpdatesSeen++;
        java.util.concurrent.locks.LockSupport.parkNanos(5_000_000L);
      }
      super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public void randomTick(
        BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
      if (state.getValue(AGE) < 3)
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }
  }

  public static final class TestCrop extends CropBlock {
    TestCrop() {
      super(BlockBehaviour.Properties.copy(Blocks.WHEAT));
    }

    @Override
    public void randomTick(
        BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
      if (state.getValue(AGE) < 7)
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }
  }

  public static final class Capture implements CommandSource {
    final List<String> messages = new ArrayList<>();

    public void sendSystemMessage(Component c) {
      messages.add(c.getString());
    }

    public boolean acceptsSuccess() {
      return true;
    }

    public boolean acceptsFailure() {
      return true;
    }

    public boolean shouldInformAdmins() {
      return false;
    }
  }

  MinecraftServer server;
  ServerLevel level;
  int ticks, checks;
  boolean resume = Boolean.getBoolean("growth.resume");
  PerformanceScenario performance;
  final Capture capture = new Capture();

  static BlockPos p(int x, int z) {
    return new BlockPos(x, 65, z);
  }

  public GrowthSmokeMod() {
    BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
    MinecraftForge.EVENT_BUS.addListener(this::started);
    MinecraftForge.EVENT_BUS.addListener(
        net.minecraftforge.eventbus.api.EventPriority.HIGHEST, this::seasonTick);
    MinecraftForge.EVENT_BUS.addListener(this::tick);
  }

  void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  void started(ServerStartedEvent event) {
    server = event.getServer();
    level = server.overworld();
    level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
  }

  int command(String command, int permission) throws Exception {
    return server
        .getCommands()
        .getDispatcher()
        .execute(
            command,
            server.createCommandSourceStack().withSource(capture).withPermission(permission));
  }

  void season(boolean winter) {
    int d = SeasonHelper.getSeasonState(level).getSubSeasonDuration();
    SeasonHandler.getSeasonSavedData(level).seasonCycleTicks = d * (winter ? 10 : 4);
  }

  // Force the test season after Calendar's START phase and before the mod's END processing.
  void seasonTick(TickEvent.LevelTickEvent event) {
    if (level != null && event.level == level && event.phase == TickEvent.Phase.END)
      season(!resume && ticks < 80);
  }

  static Object field(Object object, String name) throws Exception {
    var f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  Object vegetation() throws Exception {
    var f = SeasonalVegetation.class.getDeclaredField("instance");
    f.setAccessible(true);
    return f.get(null);
  }

  Object work() throws Exception {
    return ((Map<?, ?>) ((Map<?, ?>) field(vegetation(), "worlds")).get(level))
        .get(net.minecraft.world.level.ChunkPos.asLong(0, 0));
  }

  net.minecraft.nbt.CompoundTag records() throws Exception {
    return (net.minecraft.nbt.CompoundTag) field(work(), "records");
  }

  void ready() throws Exception {
    for (Object other :
        ((Map<?, ?>) ((Map<?, ?>) field(vegetation(), "worlds")).get(level)).values())
      ((net.minecraft.nbt.CompoundTag) field(other, "tag")).putLong("GrowthAt", Long.MAX_VALUE);
    ((net.minecraft.nbt.CompoundTag) field(work(), "tag")).putLong("GrowthAt", 0);
    var f = work().getClass().getDeclaredField("restore");
    f.setAccessible(true);
    f.set(work(), null);
  }

  void setup() throws Exception {
    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
    if (resume) return;
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        level.setBlock(p(x, z).below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (int y = 65; y <= 68; y++)
          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    level.setBlock(p(1, 1), Blocks.OAK_LOG.defaultBlockState(), 2);
    level.setBlock(
        p(2, 1), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1), 2);
    level.setBlock(
        p(3, 1), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2), 2);
    level.setBlock(p(5, 5), Blocks.DANDELION.defaultBlockState(), 2);
    level.setBlock(p(8, 5), Blocks.OAK_SAPLING.defaultBlockState(), 2);
    SeasonalVegetation.rememberTree(level, p(8, 5));
    ((net.minecraft.nbt.CompoundTag) field(work(), "tag"))
        .getCompound("NaturalTrees")
        .getCompound(Long.toString(p(8, 5).asLong()))
        .putLong("ready", 0);
    level.setBlock(p(12, 5), Blocks.OAK_SAPLING.defaultBlockState(), 2);
  }

  void permissionAndOff() throws Exception {
    check(
        !server
            .getCommands()
            .getDispatcher()
            .getRoot()
            .getChild("plantgrowth")
            .canUse(server.createCommandSourceStack().withPermission(0)),
        "normal player cannot access command");
    check(command("plantgrowth off", 2) == 1, "OP can disable growth");
    check(
        capture.messages.contains("Ріст рослин успішно вимкнено"),
        "exact Ukrainian off confirmation");
    check(!PlantGrowth.enabled(), "global switch disabled");
    try {
      command("plantgrowth on", 0);
      throw new AssertionError("ordinary caller executed on");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      checks++;
    }
    check(!PlantGrowth.enabled(), "denied command cannot change switch");
    check(command("plantgrowth status", 2) == 1, "OP status command");
  }

  void paused() throws Exception {
    check(!PlantGrowth.enabled(), "off survives subsequent ticks/restart");
    check(level.getBlockState(p(5, 5)).isAir(), "seed bank does not restore a plant while off");
    check(records().contains(Long.toString(p(5, 5).asLong())), "dormant plant record retained");
    check(
        level.getBlockState(p(2, 1)).isAir() && level.getBlockState(p(3, 1)).isAir(),
        "crown cannot regrow while off");
    check(
        !SeasonalVegetation.allowRenewal(level, 0, 0, Blocks.DANDELION.defaultBlockState()),
        "renewal candidates blocked");
    check(
        !SeasonalVegetation.allowNativeAttempt(level, 0, 0, false),
        "World Rules plant attempts blocked");
    check(
        !SeasonalVegetation.allowNativeAttempt(level, 0, 0, true),
        "World Rules tree attempts blocked");
    level.setBlock(p(6, 5), PLANT.get().defaultBlockState(), 2);
    for (int i = 0; i < 4; i++)
      level.getBlockState(p(6, 5)).randomTick(level, p(6, 5), level.getRandom());
    check(
        level.getBlockState(p(6, 5)).getValue(SweetBerryBushBlock.AGE) == 0,
        "managed plant random ticking blocked");
    var meal =
        new BonemealEvent(
            FakePlayerFactory.getMinecraft(level),
            level,
            p(6, 5),
            level.getBlockState(p(6, 5)),
            new ItemStack(Items.BONE_MEAL));
    check(MinecraftForge.EVENT_BUS.post(meal), "managed bonemeal event cancelled");
    var grow = new BlockEvent.CropGrowEvent.Pre(level, p(6, 5), level.getBlockState(p(6, 5)));
    MinecraftForge.EVENT_BUS.post(grow);
    check(
        grow.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY,
        "managed Forge grow event denied");
    ((SaplingBlock) Blocks.OAK_SAPLING)
        .advanceTree(level, p(8, 5), level.getBlockState(p(8, 5)), level.getRandom());
    check(
        level.getBlockState(p(8, 5)).getValue(SaplingBlock.STAGE) == 0,
        "managed sapling direct growth blocked");
    level.setBlock(p(10, 5).below(), Blocks.FARMLAND.defaultBlockState(), 2);
    level.setBlock(p(10, 5), CROP.get().defaultBlockState(), 2);
    level.getBlockState(p(10, 5)).randomTick(level, p(10, 5), level.getRandom());
    check(
        level.getBlockState(p(10, 5)).getValue(CropBlock.AGE) == 1,
        "unmanaged crop retains vanilla growth");
  }

  void tick(TickEvent.ServerTickEvent event) {
    if (server == null || event.phase != TickEvent.Phase.END) return;
    try {
      if (Boolean.getBoolean("leaves.performance")) {
        if (++ticks == 20) performance = new PerformanceScenario(server, level);
        else if (performance != null) performance.tick();
        return;
      }
      ticks++;
      season(!resume && ticks < 80);
      if (ticks == 5) setup();
      if (!resume && ticks == 80) {
        check(level.getBlockState(p(5, 5)).isAir(), "winter cleanup continues");
        check(
            level.getBlockState(p(2, 1)).isAir() && level.getBlockState(p(3, 1)).isAir(),
            "winter crown removal retained");
        permissionAndOff();
        ready();
      }
      if (!resume && ticks == 160) {
        paused();
        System.out.println("GROWTH_SMOKE_PASS phase=off assertions=" + checks);
        server.halt(false);
        server = null;
      }
      if (resume && ticks == 80) {
        paused();
        check(command("plantgrowth on", 2) == 1, "OP can enable after restart");
        check(
            capture.messages.contains("Ріст рослин успішно увімкнено"),
            "exact Ukrainian on confirmation");
        ready();
        level.getBlockState(p(6, 5)).randomTick(level, p(6, 5), level.getRandom());
        check(
            level.getBlockState(p(6, 5)).getValue(SweetBerryBushBlock.AGE) == 1,
            "managed random tick resumes");
        ((SaplingBlock) Blocks.OAK_SAPLING)
            .advanceTree(level, p(8, 5), level.getBlockState(p(8, 5)), level.getRandom());
        check(
            level.getBlockState(p(8, 5)).getValue(SaplingBlock.STAGE) == 1,
            "managed direct sapling growth resumes");
      }
      if (resume && ticks == 200) {
        check(PlantGrowth.enabled(), "enabled state stable");
        check(
            level.getBlockState(p(5, 5)).is(Blocks.DANDELION),
            "seed bank resumes restoring after on");
        check(
            level.getBlockState(p(2, 1)).is(Blocks.OAK_LEAVES)
                && level.getBlockState(p(3, 1)).is(Blocks.OAK_LEAVES),
            "crown resumes progressive restoration after on");
        System.out.println("GROWTH_SMOKE_PASS phase=on assertions=" + checks);
        server.halt(false);
        server = null;
      }
    } catch (Throwable failure) {
      failure.printStackTrace();
      System.out.println("GROWTH_SMOKE_FAIL " + failure);
      server.halt(false);
      server = null;
    }
  }
}
