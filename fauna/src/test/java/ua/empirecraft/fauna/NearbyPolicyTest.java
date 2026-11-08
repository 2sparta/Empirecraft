package ua.empirecraft.fauna;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NearbyPolicyTest {
  @TempDir Path directory;

  @Test
  void catalogIncludesFortyTwoIndependentGroupsWithoutHedgehog() {
    assertEquals(42, SpawnCatalog.ENTRIES.size());
    Set<String> ids = new HashSet<>();
    for (var row : SpawnCatalog.ENTRIES)
      for (var id : row.ids()) {
        assertTrue(ids.add(id));
        assertFalse(id.contains("hedgehog"));
      }
    var squirrel = SpawnCatalog.ENTRIES.get(0);
    for (int month = 0; month < 12; month++)
      assertEquals(
          new int[] {25, 25, 45, 45, 45, 40, 40, 40, 55, 55, 55, 25}[month],
          squirrel.chance(month, false));
  }

  @Test
  void percentagesDescribeOneRollNotASecondSeasonalMultiplier() {
    for (var row : SpawnCatalog.ENTRIES)
      for (int month = 0; month < 12; month++) {
        int hits = 0;
        for (int roll = 0; roll < 100; roll++)
          if (SpawnCatalog.rolled(row.chance(month, false), roll)) hits++;
        assertEquals(row.chances()[SpawnCatalog.season(month)], hits, row.name());
      }
    for (var row : SpawnCatalog.ENTRIES)
      if (row.warmSummer())
        for (int month = 0; month < 12; month++)
          assertEquals(row.chances()[1], row.chance(month, true));
    assertFalse(SpawnCatalog.rolled(0, 0));
    assertFalse(SpawnCatalog.rolled(100, 100));
  }

  @Test
  void oldConfigRetainsCustomLimitsAndReceivesNewDefaults() throws Exception {
    var path = directory.resolve("fauna.json");
    Files.writeString(path, "{\"wildGlobalLimit\":321,\"capitalCapacity\":71}");
    var settings = Settings.load(path);
    assertEquals(321, settings.wildGlobalLimit);
    assertEquals(71, settings.capitalCapacity);
    assertTrue(settings.nearbySpawning);
    assertEquals(30, settings.nearbyIntervalSeconds);
    assertEquals(180, settings.roamingSeconds);
    assertEquals("{\"wildGlobalLimit\":321,\"capitalCapacity\":71}", Files.readString(path));
  }

  @Test
  void activeBudgetReleasesUnloadedWildlifeButKeepsUnloadedCityLivestock() {
    var rules = new Settings();
    rules.wildGlobalLimit = 1;
    rules.wildernessDomesticGlobalLimit = 1;
    var p = new Population(rules);
    p.context(
        rules,
        new Population.Land(
            true,
            16,
            Map.of("minecraft:overworld/0/0", "A"),
            Map.of("A", new Population.City("A", "Town", 0, false))),
        Map.of());
    p.censusReady = true;
    var deer =
        new Population.Animal(
            UUID.randomUUID(),
            "naturalist:deer",
            "minecraft:overworld",
            64,
            70,
            0,
            "minecraft:plains",
            false,
            true,
            true);
    p.put(deer);
    assertFalse(p.canAdd(copy(deer)).allowed());
    p.put(deer.unloaded());
    assertTrue(p.canAdd(copy(deer)).allowed());
    assertEquals(1, p.count("WILD"));
    assertEquals(0, p.count("budget/WILD"));
    var cow =
        new Population.Animal(
            UUID.randomUUID(),
            "minecraft:cow",
            "minecraft:overworld",
            64,
            70,
            0,
            "minecraft:plains",
            false,
            true,
            true);
    p.put(cow);
    assertFalse(p.canAdd(copy(cow)).allowed());
    p.put(cow.unloaded());
    assertTrue(p.canAdd(copy(cow)).allowed());
    var cityCow =
        new Population.Animal(
            UUID.randomUUID(),
            "minecraft:cow",
            "minecraft:overworld",
            0,
            70,
            0,
            "minecraft:plains",
            false,
            false,
            true);
    p.put(cityCow);
    assertEquals(1, p.count("budget/DOMESTIC/city/A"));
    var owned =
        new Population.Animal(
            UUID.randomUUID(),
            "minecraft:cow",
            "minecraft:overworld",
            64,
            70,
            0,
            "minecraft:plains",
            false,
            false,
            false);
    p.put(owned);
    assertFalse(p.canAdd(copy(cow)).allowed());
  }

  Population.Animal copy(Population.Animal a) {
    return new Population.Animal(
        UUID.randomUUID(),
        a.type(),
        a.world(),
        a.x(),
        a.y(),
        a.z(),
        a.biome(),
        a.tame(),
        true,
        a.roaming());
  }

  @Test
  void expirationRequiresThreeMinutesAndHandlesClockRollback() {
    assertFalse(Roaming.expired(180999, 1000, 180));
    assertTrue(Roaming.expired(181000, 1000, 180));
    assertFalse(Roaming.expired(1, 1000, 180));
    assertFalse(Roaming.expired(200000, 0, 180));
  }

  @Test
  void inventoryAndOwnerTagsProtectAnimalsWithoutInventingOriginForOldSaves() {
    var tag = new CompoundTag();
    assertFalse(Roaming.protectedTag(tag));
    var items = new ListTag();
    items.add(new CompoundTag());
    tag.put("Items", items);
    assertFalse(Roaming.protectedTag(tag));
    var item = new CompoundTag();
    item.putString("id", "minecraft:diamond");
    items.add(item);
    assertTrue(Roaming.protectedTag(tag));
    tag = new CompoundTag();
    tag.putUUID("Owner", UUID.randomUUID());
    assertTrue(Roaming.protectedTag(tag));
    tag = new CompoundTag();
    var forge = new CompoundTag();
    forge.putBoolean(Roaming.PROTECTED, true);
    tag.put("ForgeData", forge);
    assertTrue(Roaming.protectedTag(tag));
  }
}
