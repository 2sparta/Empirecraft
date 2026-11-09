package ua.empirecraft.fauna;

import java.util.*;

/** Thread-safe UUID ledger; chunk generation and disk census never mutate Minecraft worlds. */
public final class Population {
  public record Animal(
      UUID uuid,
      String type,
      String world,
      double x,
      double y,
      double z,
      String biome,
      boolean tame,
      boolean loaded,
      boolean roaming) {
    public Animal(
        UUID uuid,
        String type,
        String world,
        double x,
        double y,
        double z,
        String biome,
        boolean tame,
        boolean loaded) {
      this(uuid, type, world, x, y, z, biome, tame, loaded, false);
    }

    public String chunk() {
      return world
          + "/"
          + Math.floorDiv((int) Math.floor(x), 16)
          + "/"
          + Math.floorDiv((int) Math.floor(z), 16);
    }

    public Animal unloaded() {
      return new Animal(uuid, type, world, x, y, z, biome, tame, false, roaming);
    }
  }

  public record City(String id, String name, int level, boolean capital) {}

  public record Land(
      boolean ready, int blockSize, Map<String, String> claims, Map<String, City> cities) {
    public static Land unavailable() {
      return new Land(false, 16, Map.of(), Map.of());
    }

    public String at(Animal a) {
      return claims.getOrDefault(
          a.world
              + "/"
              + (int) Math.floor(a.x / blockSize)
              + "/"
              + (int) Math.floor(a.z / blockSize),
          "");
    }
  }

  public enum Kind {
    DOMESTIC,
    WILD,
    PET
  }

  public record Decision(boolean allowed, String reason) {}

  private final Map<UUID, Animal> animals = new LinkedHashMap<>();
  private final Map<UUID, Long> revisions = new HashMap<>();
  private final Map<String, Integer> counts = new HashMap<>();
  private Map<String, Set<String>> biomeTags = Map.of();
  private Settings settings;
  private Land land = Land.unavailable();
  private long revision;
  private boolean scanActive;
  private Set<String> unreadableChunks = Set.of();
  private final Set<String> uncertainRegions = new HashSet<>(), uncertainCities = new HashSet<>();
  public volatile boolean censusReady;
  public volatile int chunksScanned, scanFailures;

  public Population(Settings settings) {
    this.settings = settings;
  }

  public synchronized void context(Settings settings, Land land, Map<String, Set<String>> tags) {
    this.settings = settings;
    this.land = land;
    this.biomeTags = tags;
    rebuildUncertainty();
    counts.clear();
    for (var animal : animals.values()) adjust(animal, 1);
  }

  public synchronized Kind kind(Animal a) {
    return settings.isPet(a.type, a.tame)
        ? Kind.PET
        : settings.domesticTypes.contains(a.type) ? Kind.DOMESTIC : Kind.WILD;
  }

  String region(Animal a) {
    int size = settings.regionChunks * 16;
    return a.world + "/" + (int) Math.floor(a.x / size) + "/" + (int) Math.floor(a.z / size);
  }

  List<String> keys(Animal a) {
    String city = land.at(a), region = region(a);
    var kind = kind(a);
    var keys = new ArrayList<String>();
    keys.add("all");
    if (a.loaded) keys.add("loaded");
    keys.add(kind.name());
    keys.add(kind + "/type/" + a.type);
    keys.add(kind + "/city/" + city);
    keys.add(kind + "/region/" + region);
    if (kind == Kind.WILD) {
      keys.add("wild/type-region/" + region + "/" + a.type);
      keys.add("wild/biome-region/" + region + "/" + a.biome);
      for (String tag : biomeTags.getOrDefault(a.biome, Set.of()))
        keys.add("wild/biome-region/" + region + "/#" + tag);
    }
    // Retain historical statistics, but distant wildlife must not fill the active budget.
    boolean budget =
        kind != Kind.WILD
            ? !(kind == Kind.DOMESTIC && city.isEmpty() && a.roaming && !a.loaded)
            : a.loaded;
    if (budget) for (String key : List.copyOf(keys)) keys.add("budget/" + key);
    return keys;
  }

  void adjust(Animal a, int change) {
    for (String key : keys(a)) {
      int next = counts.getOrDefault(key, 0) + change;
      if (next == 0) counts.remove(key);
      else counts.put(key, next);
    }
  }

  public synchronized void put(Animal animal) {
    Animal old = animals.put(animal.uuid, animal);
    if (old != null) adjust(old, -1);
    adjust(animal, 1);
    revisions.put(animal.uuid, ++revision);
  }

  public synchronized void remove(UUID id) {
    var old = animals.remove(id);
    if (old != null) adjust(old, -1);
    revision++;
    if (censusReady && !scanActive) revisions.remove(id);
    else revisions.put(id, revision);
  }

  public synchronized boolean contains(UUID id) {
    return animals.containsKey(id);
  }

  public synchronized Animal get(UUID id) {
    return animals.get(id);
  }

  public synchronized int count(String key) {
    return counts.getOrDefault(key, 0);
  }

  public synchronized List<Animal> all() {
    return List.copyOf(animals.values());
  }

  public synchronized Map<String, Integer> counts() {
    return Map.copyOf(counts);
  }

  public synchronized Land land() {
    return land;
  }

  public synchronized long beginScan() { scanActive = true; return revision; }
  public synchronized void abortScan() { scanActive = false; }

  public synchronized long revision() {
    return revision;
  }

  public synchronized void importSaved(Animal a) {
    if (!animals.containsKey(a.uuid)) put(a.unloaded());
  }

  public synchronized void importDisk(Animal a, long epoch) {
    var live = animals.get(a.uuid);
    if ((live == null || !live.loaded) && revisions.getOrDefault(a.uuid, 0L) <= epoch)
      put(a.unloaded());
  }

  public synchronized void finishScan(
      Set<UUID> found, Set<String> worlds, Set<String> failedChunks, long epoch) {
    for (var a : List.copyOf(animals.values()))
      if (worlds.contains(a.world)
          && !a.loaded
          && !found.contains(a.uuid)
          && !failedChunks.contains(a.chunk())
          && revisions.getOrDefault(a.uuid, 0L) <= epoch) remove(a.uuid);
    unreadableChunks = Set.copyOf(failedChunks);
    rebuildUncertainty();
    censusReady = true;
    scanActive = false;
    scanFailures = failedChunks.size();
    revisions.keySet().retainAll(animals.keySet());
  }

  private void rebuildUncertainty() {
    uncertainRegions.clear(); uncertainCities.clear();
    for (String key : unreadableChunks) {
      int last=key.lastIndexOf('/'), previous=key.lastIndexOf('/',last-1);
      String world=key.substring(0,previous);
      int x=Integer.parseInt(key.substring(previous+1,last)), z=Integer.parseInt(key.substring(last+1));
      uncertainRegions.add(world+"/"+Math.floorDiv(x,settings.regionChunks)+"/"+Math.floorDiv(z,settings.regionChunks));
      for (int px=Math.floorDiv(x*16,land.blockSize);px<=Math.floorDiv(x*16+15,land.blockSize);px++)
        for (int pz=Math.floorDiv(z*16,land.blockSize);pz<=Math.floorDiv(z*16+15,land.blockSize);pz++) {
          String city=land.claims.get(world+"/"+px+"/"+pz);
          if (city!=null) uncertainCities.add(city);
        }
    }
  }
  private boolean uncertain(Animal a) {
    return uncertainRegions.contains(region(a)) || uncertainCities.contains(land.at(a));
  }

  List<String> bounds(Animal a) {
    var keys = new ArrayList<String>();
    String city = land.at(a), region = region(a);
    var kind = kind(a);
    if (kind == Kind.DOMESTIC) {
      if (!city.isEmpty()) keys.add("DOMESTIC/city/" + city);
      else {
        keys.add("DOMESTIC/city/");
        keys.add("DOMESTIC/region/" + region);
      }
    } else if (kind == Kind.PET) keys.add("PET/city/" + city);
    else {
      keys.add("WILD");
      keys.add("WILD/region/" + region);
      for (var rule : settings.biomeRegionLimits.entrySet())
        if (rule.getKey().equals(a.biome)
            || rule.getKey().startsWith("#")
                && biomeTags.getOrDefault(a.biome, Set.of()).contains(rule.getKey().substring(1)))
          keys.add("wild/biome-region/" + region + "/" + rule.getKey());
      if (settings.speciesGlobalLimits.containsKey(a.type)) keys.add("WILD/type/" + a.type);
      if (settings.speciesRegionLimits.containsKey(a.type))
        keys.add("wild/type-region/" + region + "/" + a.type);
    }
    return keys;
  }

  int maximum(String key, Animal a) {
    String city = land.at(a);
    return switch (kind(a)) {
      case DOMESTIC ->
          !city.isEmpty()
              ? capacity(land.cities.get(city))
              : key.equals("DOMESTIC/city/")
                  ? settings.wildernessDomesticGlobalLimit
                  : settings.wildernessDomesticRegionLimit;
      case PET -> city.isEmpty() ? settings.petWildernessLimit : settings.petTownLimit;
      case WILD ->
          key.equals("WILD")
              ? settings.wildGlobalLimit
              : key.startsWith("WILD/region/")
                  ? settings.wildRegionLimit
                  : key.startsWith("wild/biome-region/")
                      ? settings.biomeRegionLimits.get(
                          key.substring(("wild/biome-region/" + region(a) + "/").length()))
                      : key.startsWith("WILD/type/")
                          ? settings.speciesGlobalLimits.get(a.type)
                          : settings.speciesRegionLimits.get(a.type);
    };
  }

  public synchronized int capacity(City city) {
    return settings.cityCapacity(city.level, city.capital);
  }

  public synchronized Decision canAdd(Animal a) {
    if (animals.containsKey(a.uuid)) return new Decision(true, "");
    if (!censusReady || !land.ready || uncertain(a)) return new Decision(false, "waiting_census");
    for (String key : bounds(a)) {
      int maximum = maximum(key, a);
      if (maximum >= 0 && count("budget/" + key) >= maximum)
        return new Decision(
            false,
            kind(a) == Kind.DOMESTIC
                ? "limit_domestic"
                : kind(a) == Kind.PET ? "limit_pets" : "limit_wild");
    }
    return new Decision(true, "");
  }

  public synchronized Decision admit(Animal a) {
    var decision = canAdd(a);
    if (decision.allowed) put(a);
    return decision;
  }

  public synchronized double density(Animal a) {
    if (!censusReady || !land.ready || uncertain(a)) return 0;
    double headroom = 1;
    for (String key : bounds(a)) {
      int maximum = maximum(key, a);
      if (maximum >= 0)
        headroom =
            Math.min(
                headroom,
                maximum == 0 ? 0 : Math.max(0, 1 - count("budget/" + key) / (double) maximum));
    }
    return headroom;
  }

  public synchronized double weight(Animal a) {
    return density(a) * settings.densityWeightMaximum;
  }
}
