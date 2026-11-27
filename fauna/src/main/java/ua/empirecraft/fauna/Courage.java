package ua.empirecraft.fauna;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gunfire startles ridable animals (horses, camels, donkeys, cows and other livestock).
 * Each species has a courage rating 0..10 from the fauna config ("courage"): 0 ignores
 * gunfire entirely, 10 panics at nearly the full configured radius and for the longest
 * time. Higher courage also means a higher chance to react at all. Ridden animals bolt
 * away from the shooter, carrying the rider with them.
 */
public final class Courage {
  /** Default courage for ridable species not listed in the config. */
  public static final int DEFAULT_COURAGE = 3;
  /** Species that react to gunfire when no explicit courage is configured. */
  private static final Set<String> RIDEABLE_DEFAULTS = Set.of(
      "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama",
      "minecraft:camel", "minecraft:cow", "minecraft:mooshroom", "minecraft:sheep",
      "minecraft:goat", "minecraft:pig",
      "dragnlivestock:o_cow", "dragnlivestock:o_mooshroom", "dragnlivestock:o_sheep",
      "dragnlivestock:o_pig", "dragnlivestock:o_goat", "dragnlivestock:o_horse",
      "dragnlivestock:o_donkey", "dragnlivestock:o_mule", "dragnlivestock:o_llama",
      "dragnlivestock:o_camel", "dragnlivestock:farm_goat",
      "horse_colors:horse_felinoid", "horse_colors:donkey", "horse_colors:mule",
      "horse_colors:hinny");
  /** Extra gunfire sound ids beyond BirdMotion.LOUD (musket and cannon mods). */
  private static final Set<String> GUN_SOUNDS = Set.of("thirtyyearswar:gunshot");
  private static final double DEFAULT_RADIUS = 24;
  private static final int BASE_PANIC_TICKS = 60;

  private final Map<UUID, Long> recent = new HashMap<>();

  /** Courage of a species: config value, default for known ridables, or -1 (no reaction). */
  public static int of(Mob mob) {
    Settings rules = FaunaMod.session == null ? null : FaunaMod.session.settings;
    Map<String, Integer> courage = rules == null ? null : rules.courage;
    String id = FaunaTypes.id(mob.getType());
    if (courage != null && courage.containsKey(id)) return courage.get(id);
    return RIDEABLE_DEFAULTS.contains(id) ? DEFAULT_COURAGE : -1;
  }

  @SubscribeEvent
  public void sound(PlayLevelSoundEvent.AtPosition event) {
    if (event.getLevel() instanceof ServerLevel level && event.getSound() != null
        && audible(event)) startle(level, event.getPosition());
  }

  @SubscribeEvent
  public void soundAtEntity(PlayLevelSoundEvent.AtEntity event) {
    if (event.getLevel() instanceof ServerLevel level && event.getSound() != null
        && audible(event)) startle(level, event.getEntity().position());
  }

  private boolean audible(PlayLevelSoundEvent event) {
    Settings rules = FaunaMod.session == null ? null : FaunaMod.session.settings;
    if (rules == null || rules.gunPanicRadius <= 0 || event.getNewVolume() <= 0) return false;
    String id = event.getSound().value().getLocation().toString();
    return BirdMotion.shotSound(id) || GUN_SOUNDS.contains(id);
  }

  private void startle(ServerLevel level, Vec3 source) {
    Settings rules = FaunaMod.session.settings;
    long now = level.getGameTime();
    recent.values().removeIf(then -> now - then > 40);
    double radius = rules.gunPanicRadius;
    for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.ofSize(source, radius * 2, radius * 2, radius * 2))) {
      if (!(mob instanceof PathfinderMob path) || path.isNoAi() || path.isPassenger()
          || !isRideable(path)) continue;
      if (recent.containsKey(mob.getUUID())) continue;
      int bravery = of(path);
      if (bravery <= 0) continue;
      // Higher courage reacts more often, over a wider area, and for longer.
      if (level.random.nextDouble() > 0.3 + 0.07 * bravery) continue;
      double effective = radius * (0.35 + 0.065 * bravery);
      if (path.distanceToSqr(source) > effective * effective) continue;
      recent.put(mob.getUUID(), now);
      int ticks = BASE_PANIC_TICKS + bravery * 20;
      Vec3 away = path.position().subtract(source);
      Vec3 flee = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0)
          : away.normalize().scale(10).add(0, 0.5, 0);
      path.getNavigation().moveTo(
          path.getX() + flee.x, path.getY() + flee.y, path.getZ() + flee.z, 1.15 + 0.05 * bravery);
      path.getPersistentData().putLong("empirecraft_gun_panic_until", now + ticks);
      if (path instanceof AbstractHorse horse) {
        if (horse.isSaddled()) horse.standIfPossible();
      }
      level.sendParticles(ParticleTypes.POOF, path.getX(), path.getY(0.8), path.getZ(),
          6, 0.3, 0.3, 0.3, 0.02);
    }
  }

  private static boolean isRideable(Mob mob) {
    return mob instanceof AbstractHorse || mob instanceof net.minecraft.world.entity.Saddleable
        || RIDEABLE_DEFAULTS.contains(FaunaTypes.id(mob.getType()))
        || COW_LIKE.test(mob);
  }

  private static final java.util.function.Predicate<Mob> COW_LIKE =
      mob -> mob.getClass().getSimpleName().contains("Cow")
          || mob.getClass().getSimpleName().contains("Camel")
          || mob.getClass().getSimpleName().contains("Horse")
          || mob.getClass().getSimpleName().contains("Donkey")
          || mob.getClass().getSimpleName().contains("Mule")
          || mob.getClass().getSimpleName().contains("Llama");
}
