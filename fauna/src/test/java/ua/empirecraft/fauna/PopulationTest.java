package ua.empirecraft.fauna;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class PopulationTest {
  @Test
  void configuredLivestockNamesAreLocalizedInBothLanguages() {
    for (String locale : List.of("uk_ua", "ru_ru")) {
      for (String id : new Settings().domesticTypes) {
        String name = I18n.entity(locale, id);
        assertNotEquals(id, name, locale + ": " + id);
        if (id.contains("moobloom")) assertFalse(name.matches(".*[A-Za-z].*"), name);
      }
    }
  }

  static final String WORLD = "minecraft:overworld",
      COW = "minecraft:cow",
      DEER = "naturalist:deer";

  Settings rules() {
    var s = new Settings();
    s.townLevelCapacity = new TreeMap<>(Map.of(0, 3, 4, 6));
    s.capitalCapacity = 10;
    s.validate();
    return s;
  }

  Population.Land land(int level) {
    return new Population.Land(
        true,
        16,
        Map.of(WORLD + "/0/0", "A", WORLD + "/0/1", "A", WORLD + "/2/0", "B"),
        Map.of(
            "A",
            new Population.City("A", "Київ", level, false),
            "B",
            new Population.City("B", "Львів", 0, false)));
  }

  Population book(Settings s) {
    var p = new Population(s);
    p.context(s, land(0), Map.of("minecraft:forest", Set.of("minecraft:is_forest")));
    p.censusReady = true;
    return p;
  }

  Population.Animal animal(String type, double x, double z) {
    return new Population.Animal(
        UUID.randomUUID(), type, WORLD, x, 65, z, "minecraft:forest", false, true);
  }

  @Test
  void defaultFiveTownTiersAndCapital() {
    var s = new Settings();
    s.validate();
    assertEquals(
        List.of(20, 35, 80, 150, 200),
        List.of(
            s.cityCapacity(0, false),
            s.cityCapacity(2, false),
            s.cityCapacity(4, false),
            s.cityCapacity(6, false),
            s.cityCapacity(8, false)));
    assertEquals(200, s.cityCapacity(0, true));
  }

  @Test
  void installedSeasonProfilesFavourDifferentModAnimalsInDifferentMonths() {
    assertTrue(
        ua.empirecraft.calendar.FaunaRules.factor("naturalist:deer", "CREATURE", 3)
            > ua.empirecraft.calendar.FaunaRules.factor("naturalist:deer", "CREATURE", 0));
    assertTrue(
        ua.empirecraft.calendar.FaunaRules.factor("naturalist:butterfly", "CREATURE", 6)
            > ua.empirecraft.calendar.FaunaRules.factor("naturalist:butterfly", "CREATURE", 0));
    assertTrue(
        ua.empirecraft.calendar.FaunaRules.factor("ecologics:penguin", "CREATURE", 0)
            > ua.empirecraft.calendar.FaunaRules.factor("ecologics:penguin", "CREATURE", 6));
    assertEquals(1, ua.empirecraft.calendar.FaunaRules.factor("minecraft:zombie", "MONSTER", 0));
  }

  @Test
  void cityCapacityIsSharedAcrossPlotsAndSpecies() {
    var s = rules();
    var p = book(s);
    assertTrue(p.admit(animal(COW, 1, 1)).allowed());
    assertTrue(p.admit(animal("dragnlivestock:o_sheep", 2, 20)).allowed());
    assertTrue(p.admit(animal("minecraft:chicken", 1, 19)).allowed());
    assertFalse(p.admit(animal(COW, 1, 1)).allowed());
    assertEquals(3, p.count("DOMESTIC/city/A"));
    assertEquals(0, p.count("WILD"));
  }

  @Test
  void existingAboveLimitAreNeverRemovedByDowngrade() {
    var s = rules();
    var p = book(s);
    p.context(s, land(4), Map.of());
    for (int i = 0; i < 5; i++) assertTrue(p.admit(animal(COW, 1, 1)).allowed());
    p.context(s, land(0), Map.of());
    assertEquals(5, p.all().size());
    assertFalse(p.canAdd(animal(COW, 1, 1)).allowed());
    p.context(s, land(4), Map.of());
    assertTrue(p.canAdd(animal(COW, 1, 1)).allowed());
  }

  @Test
  void movementAndClaimChangesReassignTheSameAnimal() {
    var s = rules();
    var p = book(s);
    var a = animal(COW, 1, 1);
    p.put(a);
    p.put(new Population.Animal(a.uuid(), a.type(), WORLD, 33, 65, 1, a.biome(), false, true));
    assertEquals(0, p.count("DOMESTIC/city/A"));
    assertEquals(1, p.count("DOMESTIC/city/B"));
    p.context(s, new Population.Land(true, 16, Map.of(), Map.of()), Map.of());
    assertEquals(1, p.count("DOMESTIC/city/"));
    assertEquals(1, p.all().size());
  }

  @Test
  void unloadingDoesNotFreeCityCapacity() {
    var s = rules();
    var p = book(s);
    var animals = List.of(animal(COW, 1, 1), animal(COW, 1, 1), animal(COW, 1, 1));
    animals.forEach(p::put);
    animals.forEach(a -> p.put(a.unloaded()));
    assertEquals(0, p.count("loaded"));
    assertFalse(p.canAdd(animal(COW, 1, 1)).allowed());
    p.remove(animals.get(0).uuid());
    assertTrue(p.canAdd(animal(COW, 1, 1)).allowed());
  }

  @Test
  void wildernessHasGlobalAndRegionalLimits() {
    var s = rules();
    s.wildernessDomesticGlobalLimit = 3;
    s.wildernessDomesticRegionLimit = 2;
    var p = book(s);
    assertTrue(p.admit(animal(COW, 80, 80)).allowed());
    assertTrue(p.admit(animal(COW, 81, 80)).allowed());
    assertFalse(p.canAdd(animal(COW, 82, 80)).allowed());
    assertTrue(p.admit(animal(COW, 1024, 1024)).allowed());
    assertFalse(p.canAdd(animal(COW, 2048, 2048)).allowed());
  }

  @Test
  void tamePetsAreSeparateIncludingModPetsButNotFarmHorses() {
    var s = rules();
    var p = book(s);
    for (String type : List.of("minecraft:wolf", "minecraft:cat", "example:dog")) {
      var a = animal(type, 1, 1);
      p.put(new Population.Animal(a.uuid(), type, WORLD, 1, 65, 1, a.biome(), true, true));
    }
    assertEquals(3, p.count("PET"));
    assertEquals(0, p.count("WILD"));
    var horse = animal("minecraft:horse", 1, 1);
    assertEquals(
        Population.Kind.DOMESTIC,
        p.kind(
            new Population.Animal(
                horse.uuid(), horse.type(), WORLD, 1, 65, 1, horse.biome(), true, true)));
  }

  @Test
  void wildGlobalLimitDoesNotIncludeCityLivestock() {
    var s = rules();
    s.wildGlobalLimit = 2;
    var p = book(s);
    for (int i = 0; i < 25; i++) p.put(animal(COW, 1, 1));
    assertTrue(p.admit(animal(DEER, 1, 1)).allowed());
    assertTrue(p.admit(animal(DEER, 1, 1)).allowed());
    assertFalse(p.canAdd(animal(DEER, 1, 1)).allowed());
    assertEquals(25, p.count("DOMESTIC"));
    assertEquals(2, p.count("WILD"));
  }

  @Test
  void biomeAndSpeciesLimitsAreLocalToTheConfiguredRegion() {
    var s = rules();
    s.biomeRegionLimits = Map.of("#minecraft:is_forest", 2);
    s.speciesRegionLimits = Map.of(DEER, 1);
    var p = book(s);
    assertTrue(p.admit(animal(DEER, 1, 1)).allowed());
    assertFalse(p.canAdd(animal(DEER, 2, 1)).allowed());
    assertTrue(p.admit(animal("naturalist:bear", 2, 1)).allowed());
    assertFalse(p.canAdd(animal("naturalist:duck", 3, 1)).allowed());
    assertTrue(p.canAdd(animal(DEER, 1024, 1)).allowed());
  }

  @Test
  void speciesGlobalLimitCrossesDimensions() {
    var s = rules();
    s.speciesGlobalLimits = Map.of(DEER, 1);
    var p = book(s);
    p.put(animal(DEER, 1, 1));
    var candidate = animal(DEER, 1024, 1);
    candidate =
        new Population.Animal(
            candidate.uuid(),
            DEER,
            "minecraft:the_nether",
            1024,
            65,
            1,
            candidate.biome(),
            false,
            false);
    assertFalse(p.canAdd(candidate).allowed());
  }

  @Test
  void decliningPopulationIncreasesAcceptanceAndRelativeWeight() {
    var s = rules();
    s.wildGlobalLimit = 10;
    var p = book(s);
    var probe = animal(DEER, 1, 1);
    double empty = p.density(probe);
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 8; i++) {
      var a = animal(DEER, 1, 1);
      p.put(a);
      ids.add(a.uuid());
    }
    double crowded = p.density(probe);
    assertTrue(crowded < empty);
    p.remove(ids.get(0));
    assertTrue(p.density(probe) > crowded);
    assertEquals(p.density(probe) * s.densityWeightMaximum, p.weight(probe));
  }

  @Test
  void zeroCapacityDoesNotDivideByZero() {
    var s = rules();
    s.wildGlobalLimit = 0;
    var p = book(s);
    assertFalse(p.canAdd(animal(DEER, 1, 1)).allowed());
    assertEquals(0, p.density(animal(DEER, 1, 1)));
    assertEquals(0, p.weight(animal(DEER, 1, 1)));
  }

  @Test
  void naturalGenerationReservationsAreAtomicAcrossThreads() throws Exception {
    var s = rules();
    s.wildGlobalLimit = 12;
    var p = book(s);
    try (var pool = new Pool()) {
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 100; i++)
        results.add(pool.executor.submit(() -> p.admit(animal(DEER, 1, 1)).allowed()));
      int successes = 0;
      for (var result : results) if (result.get()) successes++;
      assertEquals(12, successes);
      assertEquals(12, p.count("WILD"));
    }
  }

  static class Pool implements AutoCloseable {
    final ExecutorService executor = Executors.newFixedThreadPool(8);

    public void close() {
      executor.shutdownNow();
    }
  }

  @Test
  void duplicateUuidIsOneAnimal() {
    var s = rules();
    var p = book(s);
    var a = animal(COW, 1, 1);
    for (int i = 0; i < 10; i++) p.put(a);
    assertEquals(1, p.count("DOMESTIC"));
    p.remove(a.uuid());
    p.remove(a.uuid());
    assertEquals(0, p.count("DOMESTIC"));
  }

  @Test
  void diskCensusNeverOverwritesAnAlreadyLoadedAnimal() {
    var s = rules();
    var p = book(s);
    var live = animal(COW, 33, 1);
    p.put(live);
    long epoch = p.revision();
    p.importDisk(
        new Population.Animal(live.uuid(), COW, WORLD, 1, 65, 1, live.biome(), false, false),
        epoch);
    assertEquals(live, p.get(live.uuid()));
    assertEquals(1, p.count("DOMESTIC/city/B"));
    assertEquals(1, p.count("loaded"));
  }

  @Test
  void diskCensusCannotResurrectAKilledAnimalOrOverwriteAMovedOne() {
    var s = rules();
    var p = book(s);
    var a = animal(COW, 1, 1);
    p.put(a);
    p.censusReady = false;
    long epoch = p.revision();
    p.remove(a.uuid());
    p.importDisk(a, epoch);
    assertFalse(p.contains(a.uuid()));
    var moved = animal(COW, 1, 1);
    p.put(moved);
    epoch = p.revision();
    p.put(new Population.Animal(moved.uuid(), COW, WORLD, 33, 65, 1, moved.biome(), false, true));
    p.importDisk(moved, epoch);
    assertEquals(1, p.count("DOMESTIC/city/B"));
  }

  @Test
  void diskCensusPrunesStaleRowsButRetainsUnreadableChunksAndLiveAnimals() {
    var s = rules();
    var p = book(s);
    var gone = animal(COW, 1, 1).unloaded();
    var failed = animal(COW, 33, 1).unloaded();
    var live = animal(COW, 1, 1);
    p.put(gone);
    p.put(failed);
    p.put(live);
    long epoch = p.revision();
    p.finishScan(Set.of(), Set.of(WORLD), Set.of(failed.chunk()), epoch);
    assertFalse(p.contains(gone.uuid()));
    assertTrue(p.contains(failed.uuid()));
    assertTrue(p.contains(live.uuid()));
    assertFalse(p.censusReady);
  }

  @Test
  void unreadyCensusBlocksOnlyNewAnimals() {
    var s = rules();
    var p = book(s);
    var a = animal(COW, 1, 1);
    p.put(a);
    p.censusReady = false;
    assertTrue(p.canAdd(a).allowed());
    assertFalse(p.canAdd(animal(COW, 1, 1)).allowed());
    assertEquals(1, p.all().size());
  }

  @Test
  void negativeCoordinatesUseFloorNotTruncation() {
    var s = rules();
    var p = book(s);
    p.context(
        s,
        new Population.Land(true, 16, Map.of(WORLD + "/-1/-1", "A"), land(0).cities()),
        Map.of());
    p.put(animal(COW, -.1, -.1));
    assertEquals(1, p.count("DOMESTIC/city/A"));
  }

  @Test
  void capacityReloadReclassifiesWithoutDeletingAnimals() {
    var s = rules();
    var p = book(s);
    p.put(animal(DEER, 1, 1));
    var next = rules();
    next.domesticTypes.add(DEER);
    p.context(next, land(0), Map.of());
    assertEquals(0, p.count("WILD"));
    assertEquals(1, p.count("DOMESTIC"));
    assertEquals(1, p.all().size());
  }

  @Test
  void invalidConfigurationIsRejectedBeforeActivation() {
    var s = rules();
    s.regionChunks = 0;
    assertThrows(IllegalArgumentException.class, s::validate);
    var other = rules();
    other.densityWeightMaximum = Double.NaN;
    assertThrows(IllegalArgumentException.class, other::validate);
    var missing = rules();
    missing.townLevelCapacity = Map.of(4, 20);
    assertThrows(IllegalArgumentException.class, missing::validate);
  }

  @Test
  void bothLocalesHaveCorrectTerminologyAndDecimalComma() {
    assertTrue(I18n.text("uk_ua", "domestic").contains("СВІЙСЬКІ"));
    assertTrue(I18n.text("ru_ru", "domestic").contains("СЕЛЬСКОХОЗЯЙСТВЕННЫЕ"));
    assertFalse(I18n.text("ru_ru", "domestic").contains("СВІЙ"));
    assertTrue(I18n.percent("uk_ua", 1250, 1500).contains("83,3"));
    assertTrue(I18n.percent("ru_ru", 1250, 1500).contains("83,3"));
    assertEquals("Корова", I18n.entity("ru_ru", COW));
    assertEquals("Курка", I18n.entity("uk_ua", "minecraft:chicken"));
  }
}
