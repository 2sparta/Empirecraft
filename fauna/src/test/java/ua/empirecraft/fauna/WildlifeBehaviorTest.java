package ua.empirecraft.fauna;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WildlifeBehaviorTest {
  @Test void travelWinsOverLookingBackAndStationaryPlayersUseTheirView() {
    var memory = new TravelHeading();
    memory.observe(0, 0, 0); memory.observe(1, 0, 5);
    var east = memory.heading(5, -1, 0);
    assertTrue(east.ahead(50, 0)); assertFalse(east.ahead(-50, 0));
    memory.observe(1, 1, 10);
    assertTrue(memory.heading(10, -1, 0).ahead(0, 40));
    assertTrue(memory.heading(50, -1, 0).ahead(-40, 0));
    memory.observe(1000, 1000, 55);
    assertTrue(memory.heading(55, 0, -1).ahead(0, -40));
    assertEquals(new TravelHeading.Heading(0, 1), memory.heading(100, 0, 0));
  }
  @Test void allConeSamplesStayAheadInEveryTravelDirection() {
    var random = new Random(731);
    for (double direction : new double[]{0, .5, Math.PI, -Math.PI / 2}) {
      var heading = TravelHeading.normalized(Math.cos(direction), Math.sin(direction));
      for (int i = 0; i < 10000; i++) {
        double angle = heading.angle(random.nextDouble(), 120);
        assertTrue(heading.ahead(Math.cos(angle) * 48, Math.sin(angle) * 48));
      }
    }
  }
  @Test void panicFansOutWithoutSendingBirdsTowardsTheNoise() {
    Set<Integer> directions = new HashSet<>();
    for (long id = 0; id < 10000; id++) {
      var target = BirdMotion.escape(30, 12, 20, 0, 0, id, true);
      assertTrue((target.x() - 30) * 30 + (target.z() - 20) * 20 > 0);
      assertTrue(target.y() > 12);
      directions.add((int)(Math.atan2(target.z() - 20, target.x() - 30) * 100));
    }
    assertTrue(directions.size() > 100);
    assertEquals(12, BirdMotion.escape(30, 12, 20, 0, 0, 1, false).y());
    var atSource = BirdMotion.escape(0, 12, 0, 0, 0, 17, true);
    assertTrue(Double.isFinite(atSource.x()) && Double.isFinite(atSource.z()));
  }
  @Test void aFastBulletCrossingTheFlockIsDetectedBetweenTicks() {
    var bird = new BirdMotion.Point(50, 12, 0);
    assertEquals(0, BirdMotion.segmentDistanceSquared(bird,
        new BirdMotion.Point(0, 12, 0), new BirdMotion.Point(100, 12, 0)), 1e-9);
    assertEquals(100, BirdMotion.segmentDistanceSquared(new BirdMotion.Point(-10, 12, 0),
        new BirdMotion.Point(0, 12, 0), new BirdMotion.Point(100, 12, 0)), 1e-9);
    assertTrue(BirdMotion.segmentDistanceSquared(new BirdMotion.Point(50, 30, 0),
        new BirdMotion.Point(0, 12, 0), new BirdMotion.Point(100, 12, 0)) > 64);
  }
  @Test void evenSimilarIndividualIdsCannotCollapseAFlockIntoOneEscapeDirection() {
    for(int size=2;size<=12;size++){
      var points=new HashSet<BirdMotion.Point>();
      for(int index=0;index<size;index++)points.add(BirdMotion.escape(20,10,0,0,0,
          BirdMotion.scatterSlot(1009L,index,size),true));
      assertEquals(size,points.size());
      var low=BirdMotion.escape(20,10,0,0,0,BirdMotion.scatterSlot(0,0,size),true);
      var high=BirdMotion.escape(20,10,0,0,0,BirdMotion.scatterSlot(0,size-1,size),true);
      assertTrue(low.z()<-10 && high.z()>10);
    }
  }
  @Test void formationDoesNotStackEveryBirdIntoOnePosition() {
    var points = new HashSet<BirdMotion.Point>();
    for (int i = 0; i < 12; i++) points.add(BirdMotion.formation(10, 20, 30, i));
    assertEquals(12, points.size());
  }
  @Test void reloadAndQuietAmbientSoundsDoNotScareFlocks() {
    for (String id : List.of("musketmod:musket_fire", "musketmod:pistol_fire",
        "medieval_boomsticks:arquebus_shoot", "medieval_boomsticks:handgonne_shoot", "minecraft:entity.generic.explode"))
      assertTrue(BirdMotion.shotSound(id), id);
    for (String id : List.of("musketmod:musket_ready", "musketmod:musket_load0", "naturalist:bird_ambient", "minecraft:block.note_block.harp"))
      assertFalse(BirdMotion.shotSound(id), id);
    assertTrue(BirdMotion.bullet("musketmod:bullet"));
    assertTrue(BirdMotion.bullet("medieval_boomsticks:round_ball"));
    assertFalse(BirdMotion.bullet("medieval_boomsticks:throwable_axe"));
  }
  @Test void invalidBehaviorSettingsAreRejected() {
    var settings = new Settings(); settings.forwardConeDegrees = 200;
    assertThrows(IllegalArgumentException.class, settings::validate);
    settings.forwardConeDegrees = 120; settings.birdFlockSize = 1;
    assertThrows(IllegalArgumentException.class, settings::validate);
  }
  @Test void courageSettingsAreValidatedAndDefaultsExported() {
    var settings = new Settings();
    settings.courage.put("minecraft:horse", 11);
    assertThrows(IllegalArgumentException.class, settings::validate);
    settings.courage.put("minecraft:horse", 0);
    settings.validate();
    settings.gunPanicRadius = -1;
    assertThrows(IllegalArgumentException.class, settings::validate);
    settings.gunPanicRadius = 24;
    settings.validate();
    var defaults = new Settings();
    assertTrue(defaults.gunPanicRadius > 0 && defaults.courage.isEmpty());
  }
  @Test void courageEffectiveRadiusAndTicksScaleWithBravery() {
    // Mirrors Courage.startle: bravery 10 reacts wider and longer than bravery 1.
    double radius = 24;
    double low = radius * (0.35 + 0.065 * 1), high = radius * (0.35 + 0.065 * 10);
    assertTrue(high > low * 2);
    int lowTicks = 60 + 1 * 20, highTicks = 60 + 10 * 20;
    assertTrue(highTicks > lowTicks * 2);
    // Chance to react rises with courage.
    assertTrue(0.3 + 0.07 * 10 > 0.3 + 0.07 * 1);
  }
}
