package ua.empirecraft.fauna;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reload validates a complete candidate before replacing the active policy. */
public final class Settings {
  public int schema = 1;
  public boolean automaticTamedPets = true;
  public String defaultLocale = "uk_ua";
  public Map<Integer, Integer> townLevelCapacity =
      new TreeMap<>(Map.of(0, 20, 2, 35, 4, 80, 6, 150, 8, 200));
  public int capitalCapacity = 200,
      wildGlobalLimit = 1000,
      wildRegionLimit = 160,
      regionChunks = 16;
  public int wildernessDomesticGlobalLimit = 200,
      wildernessDomesticRegionLimit = 32,
      petTownLimit = -1,
      petWildernessLimit = -1;
  public int loadedAnimalWarning = 1500, monitorIntervalSeconds = 300, positionRefreshTicks = 40;
  public double densityWeightMaximum = 2;
  public boolean nearbySpawning = true;
  public boolean forwardSpawning = true, birdFlocks = true;
  public double forwardConeDegrees = 120;
  public int birdFlockSize = 20, birdNoiseRadius = 160, birdScatterSeconds = 8;
  public double gunPanicRadius = 24;
  public Map<String, Integer> courage = new LinkedHashMap<>();
  public int nearbyIntervalSeconds = 20, nearbyGroundLimit = 48, nearbySmallLimit = 24;
  public int roamingDistance = 160, roamingSeconds = 180;
  public int spawnChecksPerTick = 4;
  public Map<String, Integer> biomeRegionLimits =
      new TreeMap<>(
          Map.of(
              "#minecraft:is_forest",
              100,
              "minecraft:plains",
              80,
              "#minecraft:is_mountain",
              50,
              "minecraft:swamp",
              40));
  public Map<String, Integer> speciesGlobalLimits = new LinkedHashMap<>(),
      speciesRegionLimits = new LinkedHashMap<>();
  public Set<String> petTypes =
      new LinkedHashSet<>(List.of("minecraft:cat", "minecraft:wolf", "minecraft:parrot"));
  public Set<String> domesticTypes =
      new LinkedHashSet<>(
          List.of(
              "minecraft:cow",
              "minecraft:mooshroom",
              "minecraft:sheep",
              "minecraft:pig",
              "minecraft:chicken",
              "minecraft:goat",
              "minecraft:horse",
              "minecraft:donkey",
              "minecraft:mule",
              "minecraft:llama",
              "minecraft:camel",
              "dragnlivestock:o_cow",
              "dragnlivestock:o_mooshroom",
              "dragnlivestock:o_sheep",
              "dragnlivestock:o_pig",
              "dragnlivestock:o_chicken",
              "dragnlivestock:o_goat",
              "dragnlivestock:farm_goat",
              "dragnlivestock:o_horse",
              "dragnlivestock:o_donkey",
              "dragnlivestock:o_mule",
              "dragnlivestock:o_llama",
              "dragnlivestock:o_camel",
              "dragnlivestock:azalea_moobloom",
              "dragnlivestock:beetroot_moobloom",
              "dragnlivestock:carrot_moobloom",
              "dragnlivestock:flowering_moobloom",
              "dragnlivestock:glow_berry_moobloom",
              "dragnlivestock:melon_moobloom",
              "dragnlivestock:peach_moobloom",
              "dragnlivestock:potato_moobloom",
              "dragnlivestock:pumpkin_moobloom",
              "dragnlivestock:sweet_berry_moobloom",
              "dragnlivestock:wheat_moobloom",
              "horse_colors:horse_felinoid",
              "horse_colors:donkey",
              "horse_colors:mule",
              "horse_colors:hinny"));

  public void validate() {
    if (schema != 1 || !Set.of("uk_ua", "ru_ru").contains(defaultLocale))
      throw new IllegalArgumentException("schema/defaultLocale");
    if (townLevelCapacity == null || !townLevelCapacity.containsKey(0))
      throw new IllegalArgumentException("townLevelCapacity needs level 0");
    townLevelCapacity = new TreeMap<>(townLevelCapacity);
    townLevelCapacity.forEach(
        (k, v) -> {
          if (k < 0 || k > 100) throw new IllegalArgumentException("Towny level");
          limit(v, false);
        });
    for (int n :
        List.of(
            capitalCapacity,
            wildGlobalLimit,
            wildRegionLimit,
            wildernessDomesticGlobalLimit,
            wildernessDomesticRegionLimit)) limit(n, false);
    limit(petTownLimit, true);
    limit(petWildernessLimit, true);
    if (regionChunks < 1
        || regionChunks > 128
        || positionRefreshTicks < 1
        || positionRefreshTicks > 200
        || loadedAnimalWarning < 1
        || monitorIntervalSeconds < 10
        || monitorIntervalSeconds > 86400
        || !Double.isFinite(densityWeightMaximum)
        || densityWeightMaximum < 1
        || densityWeightMaximum > 8)
      throw new IllegalArgumentException("monitor/region/density settings");
    if (!Double.isFinite(forwardConeDegrees) || forwardConeDegrees < 30 || forwardConeDegrees > 180
        || birdFlockSize < 2 || birdFlockSize > 32 || birdNoiseRadius < 16 || birdNoiseRadius > 256
        || birdScatterSeconds < 2 || birdScatterSeconds > 60)
      throw new IllegalArgumentException("forward/flock settings");
    if (!Double.isFinite(gunPanicRadius) || gunPanicRadius < 0 || gunPanicRadius > 256)
      throw new IllegalArgumentException("gunPanicRadius must be 0..256");
    for (var e : courage.entrySet())
      if (e.getValue() < 0 || e.getValue() > 10)
        throw new IllegalArgumentException("courage[" + e.getKey() + "] must be 0..10");
    if (nearbyIntervalSeconds < 5
        || nearbyIntervalSeconds > 3600
        || nearbyGroundLimit < 0
        || nearbyGroundLimit > 256
        || nearbySmallLimit < 0
        || nearbySmallLimit > 256
        || roamingDistance < 128
        || roamingDistance > 512
        || roamingSeconds < 30
        || roamingSeconds > 86400
        || spawnChecksPerTick < 1
        || spawnChecksPerTick > 8) throw new IllegalArgumentException("nearby/roaming settings");
    for (var map : List.of(biomeRegionLimits, speciesGlobalLimits, speciesRegionLimits))
      for (var e : map.entrySet()) {
        id(e.getKey().replaceFirst("^#", ""));
        limit(e.getValue(), false);
      }
    for (var set : List.of(domesticTypes, petTypes)) for (String id : set) id(id);
  }

  static void limit(int n, boolean optional) {
    if (n < (optional ? -1 : 0) || n > 1000000)
      throw new IllegalArgumentException("Capacity must be 0..1000000 (-1 only for pets)");
  }

  static void id(String id) {
    if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
      throw new IllegalArgumentException("Invalid entity/biome ID: " + id);
  }

  public boolean isPet(String type, boolean tame) {
    return tame && (petTypes.contains(type) || automaticTamedPets && !domesticTypes.contains(type));
  }

  public int cityCapacity(int level, boolean capital) {
    int result = 0;
    for (var e : townLevelCapacity.entrySet()) if (e.getKey() <= level) result = e.getValue();
    return capital ? Math.max(capitalCapacity, result) : result;
  }

  public static Settings load(Path file) throws IOException {
    var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    Settings candidate;
    if (Files.exists(file))
      candidate = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
    else {
      candidate = new Settings();
      Files.createDirectories(file.getParent());
      Files.writeString(file, gson.toJson(candidate) + "\n", StandardCharsets.UTF_8);
    }
    if (candidate == null) throw new IllegalArgumentException("Empty fauna configuration");
    candidate.validate();
    return candidate;
  }

  /** Build-time export; does not read or alter a running server's configuration. */
  public static void main(String[] args) throws IOException {
    Path target = Path.of(args[0]);
    Files.createDirectories(target.getParent());
    var defaults = new Settings();
    defaults.validate();
    Files.writeString(
        target,
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(defaults)
            + "\n",
        StandardCharsets.UTF_8);
  }
}
