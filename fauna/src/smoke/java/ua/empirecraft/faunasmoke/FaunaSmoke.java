package ua.empirecraft.faunasmoke;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.fauna.*;

/**
 * Actual Towny, native/modded animals, breeding, vanilla cap mixins, locales and two server boots.
 */
@Mod("empirecraft_fauna_smoke")
public final class FaunaSmoke {
  MinecraftServer server;
  ServerLevel level;
  int ticks, checks;
  boolean ran;
  Object api, dataSource, first, second;
  String firstId, secondId;
  final String cowId = "dragnlivestock:o_cow";
  final BlockPos remote = new BlockPos(1025, 75, 1025);

  public FaunaSmoke() {
    MinecraftForge.EVENT_BUS.addListener(this::placeholders);
    MinecraftForge.EVENT_BUS.addListener(this::started);
    MinecraftForge.EVENT_BUS.addListener(this::tick);
  }

  void placeholders(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
    try {
      Path folder=event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("entities");
      Files.createDirectories(folder);
      for(String name:List.of("r.9000.9000.mca","r.9000.9001.mca"))if(!Files.exists(folder.resolve(name)))Files.createFile(folder.resolve(name));
    }catch(java.io.IOException error){throw new AssertionError(error);}
  }

  void started(ServerStartedEvent event) {
    server = event.getServer();
    level = server.overworld();
    if (Integer.getInteger("empirecraft.fauna.phase", 1) == 1) {
      level.setChunkForced(0, 0, true);
      level.setChunkForced(2, 0, true);
      level.setChunkForced(64, 64, true);
      level.setChunkForced(32, 32, true);
    }
  }

  void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  Object invoke(Object target, String name, Object... args) throws Exception {
    for (Method method : target.getClass().getMethods())
      if (method.getName().equals(name) && method.getParameterCount() == args.length) {
        boolean matches = true;
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < args.length; i++)
          if (args[i] != null
              && !parameters[i].isInstance(args[i])
              && !(parameters[i] == int.class && args[i] instanceof Integer)
              && !(parameters[i] == boolean.class && args[i] instanceof Boolean)) matches = false;
        if (matches) return method.invoke(target, args);
      }
    throw new NoSuchMethodException(name);
  }

  void towny(boolean create) throws Exception {
    var bukkit = Class.forName("org.bukkit.Bukkit");
    Object manager = bukkit.getMethod("getPluginManager").invoke(null),
        plugin =
            Class.forName("org.bukkit.plugin.PluginManager")
                .getMethod("getPlugin", String.class)
                .invoke(manager, "Towny");
    check(
        plugin != null && (boolean) invoke(plugin, "isEnabled"),
        "real installed Towny plugin is enabled");
    ClassLoader loader = plugin.getClass().getClassLoader();
    api =
        Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader)
            .getMethod("getInstance")
            .invoke(null);
    Object universe =
        Class.forName("com.palmergames.bukkit.towny.TownyUniverse", true, loader)
            .getMethod("getInstance")
            .invoke(null);
    dataSource = invoke(universe, "getDataSource");
    if (create) {
      for (String name : List.of("FaunaKyiv", "FaunaLviv")) {
        invoke(universe, "newTown", name);
        Object town = invoke(api, "getTown", name),
            resident =
                invoke(
                    dataSource,
                    "newResident",
                    name + "Mayor",
                    UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        invoke(resident, "setTown", town);
        invoke(town, "setMayor", resident);
        invoke(town, "setManualTownLevel", 0);
        invoke(dataSource, "saveResident", resident);
        invoke(dataSource, "saveTown", town);
      }
    }
    first = invoke(api, "getTown", "FaunaKyiv");
    second = invoke(api, "getTown", "FaunaLviv");
    check(first != null && second != null, "both real Towny cities are present");
    firstId = invoke(first, "getUUID").toString();
    secondId = invoke(second, "getUUID").toString();
    if (create) {
      Object world =
          invoke(api, "getTownyWorld", level.getClass().getMethod("getWorld").invoke(level));
      invoke(world, "setWorldMobs", true);
      invoke(world, "setWildernessMobs", true);
      invoke(world, "setForceTownMobs", false);
      Class<?>
          blockType = Class.forName("com.palmergames.bukkit.towny.object.TownBlock", true, loader),
          worldType = Class.forName("com.palmergames.bukkit.towny.object.TownyWorld", true, loader);
      for (int[] cell : new int[][] {{0, 0, 0}, {64, 64, 0}, {2, 0, 1}}) {
        Object
            block =
                blockType
                    .getConstructor(int.class, int.class, worldType)
                    .newInstance(cell[0], cell[1], world),
            town = cell[2] == 0 ? first : second;
        invoke(block, "setTown", town);
        if (cell[1] == 0) {
          invoke(town, "setHomeBlock", block);
          Object spawn =
              Class.forName("org.bukkit.Location")
                  .getConstructor(
                      Class.forName("org.bukkit.World"), double.class, double.class, double.class)
                  .newInstance(
                      level.getClass().getMethod("getWorld").invoke(level),
                      cell[0] * 16 + 1d,
                      75d,
                      1d);
          invoke(town, "setSpawn", spawn);
        }
        invoke(dataSource, "saveTownBlock", block);
      }
      invoke(dataSource, "saveWorld", world);
      invoke(dataSource, "saveTown", first);
      invoke(dataSource, "saveTown", second);
    }
    FaunaMod.session.towns.invalidate();
    FaunaMod.session.refreshLand(true);
    check(
        FaunaMod.session.population.land().ready(),
        "production Towny adapter reads real claims and levels");
  }

  Session s() {
    return FaunaMod.session;
  }

  int city(String id) {
    return s().population.count("DOMESTIC/city/" + id);
  }

  Animal cow(double x, double z, boolean existing) {
    var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(cowId));
    Animal mob = (Animal) type.create(level);
    mob.setPos(x, 75, z);
    mob.setNoAi(true);
    mob.setNoGravity(true);
    mob.finalizeSpawn(
        level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
    if (existing) mob.getPersistentData().putBoolean(FaunaTypes.SEEN, true);
    check(level.addFreshEntity(mob), "actual mod livestock joins the world");
    return mob;
  }

  void breed(Animal a, Animal b) {
    a.setAge(0);
    b.setAge(0);
    a.spawnChildFromBreeding(level, b);
  }

  void firstBoot() throws Exception {
    towny(true);
    level.setChunkForced(0, 0, true);
    level.setChunkForced(2, 0, true);
    level.getChunk(64, 64);
    for (var mob : List.copyOf(s().loaded)) mob.discard();
    for (var animal : s().population.all()) s().population.remove(animal.uuid());
    Animal a = cow(1, 1, false),
        b = cow(3, 1, false),
        far = cow(remote.getX(), remote.getZ(), false);
    check(city(firstId) == 3, "one cap counts all livestock across distant town plots");
    breed(a, b);
    check(city(firstId) == 3, "actual modded breeding is blocked at the city capacity");
    far.setPos(33, 75, 1);
    s().refreshLoaded();
    check(
        city(firstId) == 2 && city(secondId) == 1,
        "movement transfers the animal between real Towny cities");
    breed(a, b);
    check(city(firstId) == 3, "breeding resumes after an animal leaves the city");
    far.setPos(remote.getX(), 75, remote.getZ());
    s().refreshLoaded();
    check(
        city(firstId) == 4,
        "existing imported animals are retained even above capacity: A="
            + city(firstId)
            + " B="
            + city(secondId)
            + " removed="
            + far.isRemoved()
            + " rows="
            + s().population.all());
    invoke(first, "setManualTownLevel", 4);
    s().refreshLand(true);
    check(
        s().population.capacity(s().population.land().cities().get(firstId)) == 6,
        "Towny's actual effective level increases capacity");
    breed(a, b);
    check(city(firstId) == 5, "upgraded city can breed again");
    invoke(first, "setManualTownLevel", 0);
    s().refreshLand(true);
    check(
        city(firstId) == 5
            && s().population.capacity(s().population.land().cities().get(firstId)) == 3,
        "level downgrade retains all five animals");
    breed(a, b);
    check(city(firstId) == 5, "downgraded city cannot create another animal");
    Animal otherA = cow(33, 1, false), otherB = cow(35, 1, false);
    breed(otherA, otherB);
    check(
        city(secondId) == 3 && city(firstId) == 5,
        "birth uses the parents' city rather than the child's uninitialized origin");
    breed(otherA, otherB);
    check(city(secondId) == 3, "the second city's own capacity blocks its next birth");
    var wolf = EntityType.WOLF.create(level);
    wolf.setOwnerUUID(UUID.randomUUID());
    wolf.setTame(true);
    wolf.setPos(1, 75, 1);
    wolf.setNoAi(true);
    wolf.setNoGravity(true);
    check(level.addFreshEntity(wolf), "tamed wolf is admitted separately");
    check(
        s().population.count("PET") == 1 && city(firstId) == 5,
        "pet occupies no city livestock slot");
    var wild = new ArrayList<Mob>();
    for (int i = 0; i < 6; i++) {
      Mob deer =
          (Mob)
              ForgeRegistries.ENTITY_TYPES
                  .getValue(new ResourceLocation("naturalist:deer"))
                  .create(level);
      deer.setPos(80 + i, 75, 80);
      deer.setNoAi(true);
      deer.setNoGravity(true);
      check(level.addFreshEntity(deer), "actual Naturalist deer has independent wild capacity");
      wild.add(deer);
    }
    Mob extra =
        (Mob)
            ForgeRegistries.ENTITY_TYPES
                .getValue(new ResourceLocation("naturalist:deer"))
                .create(level);
    extra.setPos(80, 75, 80);
    check(!level.addFreshEntity(extra), "global wild cap rejects an additional new deer");
    Zombie zombie = EntityType.ZOMBIE.create(level);
    zombie.setPos(80, 75, 80);
    zombie.setNoAi(true);
    check(level.addFreshEntity(zombie), "monster spawning is unchanged by animal limits");
    var proto =
        new ProtoChunk(
            new ChunkPos(50, 50),
            UpgradeData.EMPTY,
            level,
            level.registryAccess().registryOrThrow(Registries.BIOME),
            null);
    proto.setStatus(ChunkStatus.FULL);
    var region = new WorldGenRegion(level, List.of(proto), ChunkStatus.FULL, 0);
    Mob generated =
        (Mob)
            ForgeRegistries.ENTITY_TYPES
                .getValue(new ResourceLocation("naturalist:deer"))
                .create(level);
    generated.setPos(801, 75, 801);
    check(
        !region.addFreshEntity(generated) && proto.getEntities().isEmpty(),
        "real WorldGenRegion insertion obeys the wild cap");
    wild.get(0).discard();
    check(
        region.addFreshEntity(generated) && proto.getEntities().size() == 1,
        "real proto-chunk insertion succeeds after capacity is freed");
    check(
        s().population.contains(generated.getUUID()),
        "not-yet-loaded generated animals are already counted");
    s().population.remove(generated.getUUID());
    Cow plain = EntityType.COW.create(level);
    plain.setPos(1, 75, 1);
    Fox fox = EntityType.FOX.create(level);
    fox.setPos(2, 75, 1);
    var state =
        NaturalSpawner.createState(
            289,
            List.of(plain, fox),
            (packed, consumer) -> consumer.accept(level.getChunk(0, 0)),
            new LocalMobCapCalculator(level.getChunkSource().chunkMap));
    check(
        state.getMobCategoryCounts().getInt(MobCategory.CREATURE) == 1,
        "actual vanilla createState excludes livestock from the wild creature cap");
    var after = state.getClass().getDeclaredMethod("m_47131_", Mob.class, ChunkAccess.class);
    after.setAccessible(true);
    after.invoke(state, plain, level.getChunk(0, 0));
    check(
        state.getMobCategoryCounts().getInt(MobCategory.CREATURE) == 1,
        "post-spawn vanilla accounting also keeps livestock separate");
    var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FaunaLocale"));
    var language = ServerPlayer.class.getDeclaredField("language");
    language.setAccessible(true);
    language.set(fake, "ru_ru");
    check(I18n.locale(fake).equals("ru_ru"), "actual player language selects Russian");
    server
        .getCommands()
        .getDispatcher()
        .execute("fauna admin stats", server.createCommandSourceStack().withEntity(fake));
    language.set(fake, "uk_ua");
    check(I18n.locale(fake).equals("uk_ua"), "actual player language selects Ukrainian");
    server
        .getCommands()
        .getDispatcher()
        .execute("fauna admin city FaunaKyiv", server.createCommandSourceStack().withEntity(fake));
    server
        .getCommands()
        .getDispatcher()
        .execute("fauna admin monitor", server.createCommandSourceStack());
    var previous = s().settings;
    String json = Files.readString(s().config);
    Files.writeString(s().config, "{bad json");
    server
        .getCommands()
        .getDispatcher()
        .execute("fauna admin reload", server.createCommandSourceStack());
    check(s().settings == previous, "invalid reload keeps the previous live policy");
    Files.writeString(s().config, json);
    server
        .getCommands()
        .getDispatcher()
        .execute("fauna admin reload", server.createCommandSourceStack());
    nearbyChecks();
    invoke(dataSource, "saveTown", first);
    invoke(dataSource, "saveTown", second);
    s().refreshLoaded();
    // This flat fixture's floor is at Y=3; keep newly born calves at the test farm's Y=75.
    for (Mob mob : s().loaded) { mob.setNoAi(true); mob.setNoGravity(true); }
    ((net.minecraft.world.level.saveddata.SavedData) s().data).setDirty();
    level.getDataStorage().save();
    var terms = new CompoundTag();
    terms.putString("city", firstId);
    terms.putInt("count", city(firstId));
    terms.putUUID("remote", far.getUUID());
    NbtIo.writeCompressed(terms, new java.io.File("fauna-smoke-terms.dat"));
    level.setChunkForced(64, 64, false);
    level.setChunkForced(32, 32, false);
  }

  void nearbyChecks() throws Exception {
    check(SpawnCatalog.ENTRIES.size() == 42, "42 catalog groups without hedgehog");
    var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
    var coverage = new JsonObject();
    var missingHabitats = new ArrayList<String>();
    for (var row : SpawnCatalog.ENTRIES) {
      var found = new JsonArray();
      var habitats = new JsonArray();
      for (var id : row.ids()) {
        var key = new ResourceLocation(id);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(key)) continue;
        var type = ForgeRegistries.ENTITY_TYPES.getValue(key);
        check(FaunaTypes.managed(type), "managed catalog animal: " + id);
        found.add(id);
        for (var biome : biomes.holders().toList())
          if (NearbyFauna.habitat(type, biome)) habitats.add(id + "@" + biome.key().location());
      }
      check(!found.isEmpty(), "installed entity for " + row.name());
      if (habitats.isEmpty()) missingHabitats.add(row.name());
      var info = new JsonObject();
      info.add("registered", found);
      info.add("habitats", habitats);
      coverage.add(row.name(), info);
    }
    Files.writeString(
        Path.of("fauna-habitats.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(coverage));
    check(missingHabitats.isEmpty(), "native eligible habitats missing: " + missingHabitats);
    var squirrel =
        ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("ecologics:squirrel"));
    check(
        NearbyFauna.habitat(
            squirrel, biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.FOREST)),
        "squirrel now has forest habitat");
    check(
        !NearbyFauna.habitat(
            squirrel, biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DESERT)),
        "squirrel is excluded from deserts");
    check(NearbyFauna.enabledDimension(level), "Overworld seasonal dimension is enabled");
    check(!NearbyFauna.enabledDimension(server.getLevel(Level.NETHER)), "Nether excluded");
    check(NearbyFauna.month(level) >= 0, "live real-time calendar supplies a month");
    check(
        NearbyFauna.timeAllowed("night", 18000) && !NearbyFauna.timeAllowed("day", 18000),
        "time-of-day rules");
    var fake =
        FakePlayerFactory.get(
            level, new GameProfile(UUID.nameUUIDFromBytes("NearbyA".getBytes()), "NearbyA"));
    var secondFake =
        FakePlayerFactory.get(
            level, new GameProfile(UUID.nameUUIDFromBytes("NearbyB".getBytes()), "NearbyB"));
    level.getChunk(16,16);
    int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,256,256);
    fake.setPos(256, groundY, 256);
    secondFake.setPos(260, groundY, 260);
    check(
        NearbyFauna.areas(List.of(fake, secondFake)).size() == 1,
        "nearby players share a roll area");
    secondFake.setPos(512, groundY, 256);
    check(
        NearbyFauna.areas(List.of(fake, secondFake)).size() == 2,
        "distant players receive independent areas");
    var land = s().population.land();
    var settings = s().settings;
    int oldLimit = settings.wildGlobalLimit;
    settings.wildGlobalLimit = 1000;
    s().population.context(settings, land, s().tags);
    for (int cx = 9; cx <= 23; cx++) for (int cz = 9; cz <= 23; cz++) level.getChunk(cx, cz);
    level.players().add(fake);
    try {
      level.setDayTime(6000);
      var row = SpawnCatalog.ENTRIES.get(0);
      var beforeSpawn = new java.util.HashSet<java.util.UUID>();
      for (var mob : s().loaded) beforeSpawn.add(mob.getUUID());
      int count = 0;
      for (int tries = 0; tries < 10 && count == 0; tries++)
        count = s().nearby.attempt(row, fake, 0);
      check(
          count >= 2 && count <= 3, "native squirrel group spawns in loaded wilderness: " + count);
      int checkedSpawns = 0;
      for (var mob : s().loaded)
        if (mob.getType() == squirrel && mob.getSpawnType() == MobSpawnType.EVENT && !beforeSpawn.contains(mob.getUUID())) {
          checkedSpawns++;
          check(
              Roaming.natural(mob) && !Roaming.protectedAnimal(mob),
              "new squirrel carries natural provenance");
          check(
              fake.distanceToSqr(mob) >= 32 * 32 && fake.distanceToSqr(mob) <= 100 * 100,
              "spawn ring respected: at="+mob.position()+" observer="+fake.position()+" distance="+fake.distanceTo(mob));
          check(s().nearby.ahead(fake, mob.blockPosition()), "actual new wildlife spawns ahead of its observer");
        }
      check(checkedSpawns == count,"every additional spawn was checked independently of asynchronous chunk-generation animals");
      int oldGround = settings.nearbyGroundLimit;
      settings.nearbyGroundLimit = 0;
      check(s().nearby.attempt(row, fake, 0) == 0, "local zero cap forbids spawns");
      settings.nearbyGroundLimit = oldGround;
      s().nearby.tick(0);
      fake.setPos(257, groundY, 256);
      s().nearby.tick(5);
      fake.setYRot(90); // Face west while the observed movement is east.
      check(s().nearby.ahead(fake, new BlockPos(300, 4, 256)), "travel direction wins over looking backwards");
      check(!s().nearby.ahead(fake, new BlockPos(200, 4, 256)), "moving observer rejects animals behind");
      check(!s().nearby.naturalPosition(level, new BlockPos(200, 4, 256)), "vanilla natural spawning uses the same forward policy");
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      check(s().nearby.attempt(row, fake, 0) == 0, "doMobSpawning=false respected");
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
      check(s().nearby.attempt(row, fake, 99) == 0, "failed percentage roll creates no squirrel");
      var distant = new BlockPos(10000, 4, 10000);
      check(
          level.getChunkSource().getChunkNow(625, 625) == null,
          "distant test chunk starts unloaded");
      check(!s().nearby.usable(level, distant, fake), "unloaded destinations are rejected");
      check(
          level.getChunkSource().getChunkNow(625, 625) == null,
          "rejection never loads a distant chunk");
      fake.setPos(8, 4, 80);
      check(
          !s().nearby.usable(level, new BlockPos(8, 4, 8), fake),
          "Towny claims reject nearby spawns");
      fake.setPos(256, 4, 256);
      long now = System.currentTimeMillis();
      Mob natural = (Mob) squirrel.create(level);
      natural.setPos(512, 75, 512);
      natural.setNoGravity(true);
      natural.setNoAi(true);
      level.getChunk(32, 32);
      Roaming.mark(natural, now - 180000);
      check(level.addFreshEntity(natural), "natural expiry test mob admitted");
      check(
          s().nearby.expire(natural, now) && natural.isRemoved(),
          "unowned distant natural animal expires after three minutes");
      Mob keep = (Mob) squirrel.create(level);
      keep.setPos(512, 75, 512);
      Roaming.mark(keep, now - 180000);
      keep.setCustomName(net.minecraft.network.chat.Component.literal("Keep"));
      check(
          !s().nearby.expire(keep, now) && keep.getPersistentData().getBoolean(Roaming.PROTECTED),
          "named animal is permanently protected");
      Mob unknown = (Mob) squirrel.create(level);
      unknown.setPos(512, 75, 512);
      check(!s().nearby.expire(unknown, now), "old animal with unknown origin is preserved");
      Mob inventory = (Mob) squirrel.create(level);
      inventory.setPos(512, 75, 512);
      Roaming.mark(inventory, now - 180000);
      inventory.setItemSlot(
          EquipmentSlot.MAINHAND,
          new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
      check(
          !s().nearby.expire(inventory, now)
              && inventory.getPersistentData().getBoolean(Roaming.PROTECTED),
          "equipment inventory protects an animal");
      Mob town = (Mob) squirrel.create(level);
      town.setPos(1, 75, 1);
      Roaming.mark(town, now - 180000);
      check(
          !s().nearby.expire(town, now) && town.getPersistentData().getBoolean(Roaming.PROTECTED),
          "Towny-claimed animal is permanently protected");
      Mob near = (Mob) squirrel.create(level);
      near.setPos(270, 4, 256);
      Roaming.mark(near, now - 180000);
      check(
          !s().nearby.expire(near, now) && near.getPersistentData().getLong(Roaming.NEAR) == now,
          "nearby player resets expiry timer");
      Mob returned = (Mob) squirrel.create(level);
      returned.setPos(270, 4, 256);
      Roaming.mark(returned, now - 180000);
      s().nearby.returned(returned);
      check(
          s().nearby.expire(returned, now),
          "stale known natural animal recycles on chunk reload without loading far chunks");
      Mob unavailable = (Mob) squirrel.create(level);
      unavailable.setPos(512, 75, 512);
      Roaming.mark(unavailable, now - 180000);
      s().population.context(settings, Population.Land.unavailable(), s().tags);
      check(!s().nearby.expire(unavailable, now), "Towny outage never deletes animals");
      s().population.context(settings, land, s().tags);
      Mob persist = (Mob) squirrel.create(level);
      persist.setPos(513, 75, 513);
      persist.setNoAi(true);
      persist.setNoGravity(true);
      Roaming.mark(persist, now);
      check(level.addFreshEntity(persist), "natural provenance restart fixture admitted");
      var encoded = new CompoundTag();
      persist.save(encoded);
      var decoded = FaunaTypes.disk(encoded, level, new double[] {0, 0, 0});
      check(
          decoded != null && decoded.roaming(),
          "natural provenance survives real entity NBT serialization: " + encoded);
      var terms = new CompoundTag();
      terms.putUUID("animal", persist.getUUID());
      NbtIo.writeCompressed(terms, new java.io.File("fauna-nearby-terms.dat"));
    } finally {
      level.players().remove(fake);
      settings.wildGlobalLimit = oldLimit;
      s().population.context(settings, land, s().tags);
    }
  }

  void secondBoot() throws Exception {
    towny(false);
    var terms = NbtIo.readCompressed(new java.io.File("fauna-smoke-terms.dat"));
    check(
        !level.hasChunkAt(remote),
        "remote farm chunk remains unloaded after a real server restart");
    var remoteAnimal = s().population.get(terms.getUUID("remote"));
    check(
        remoteAnimal != null && !remoteAnimal.loaded(),
        "disk census retains livestock in the unloaded farm chunk");
    check(
        city(firstId) == terms.getInt("count"),
        "whole-city livestock total survives the server restart: expected=" + terms.getInt("count")
            + " actual=" + city(firstId) + " records=" + s().population.all());
    check(
        !s().population
            .canAdd(
                new Population.Animal(
                    UUID.randomUUID(),
                    cowId,
                    level.dimension().location().toString(),
                    1,
                    75,
                    1,
                    "minecraft:plains",
                    false,
                    false))
            .allowed(),
        "unloaded farm still blocks breeding at the shared city limit");
    check(
        s().population.land().cities().get(firstId).level() == 0,
        "downgraded Towny level persists across restart");
    check(s().population.count("PET") == 1, "pet census persists separately");
    var nearbyTerms = NbtIo.readCompressed(new java.io.File("fauna-nearby-terms.dat"));
    var restored = s().population.get(nearbyTerms.getUUID("animal"));
    check(
        restored != null && restored.roaming(),
        "natural provenance survives restart and real disk census: " + restored);
  }

  void tick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || server == null || ran) return;
    if (++ticks > 1200) {
      ran = true;
      System.out.println("FAUNA_NATIVE_FAIL census timeout");
      server.halt(false);
      return;
    }
    if (ticks < 40 || FaunaMod.session == null || !s().population.censusReady) return;
    ran = true;
    try {
      int phase = Integer.getInteger("empirecraft.fauna.phase", 1);
      if (phase == 1) firstBoot();
      else secondBoot();
      System.out.println("FAUNA_NATIVE_PASS phase=" + phase + " assertions=" + checks);
    } catch (Throwable error) {
      System.out.println("FAUNA_NATIVE_FAIL");
      error.printStackTrace();
    } finally {
      server.halt(false);
      server = null;
    }
  }
}
