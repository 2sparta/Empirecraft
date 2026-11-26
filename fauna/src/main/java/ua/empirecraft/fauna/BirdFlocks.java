package ua.empirecraft.fauna;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import ua.empirecraft.fauna.mixin.MobGoalsAccessor;
import ua.empirecraft.fauna.mixin.SpawnFlightAccess;

/** Keeps flock membership through a scare, using each species' own movement and animations. */
public final class BirdFlocks {
  private static final Set<String> FLIGHT = Set.of("minecraft:parrot", "naturalist:sparrow",
      "naturalist:finch", "naturalist:robin", "naturalist:bluejay", "naturalist:cardinal", "naturalist:canary", "spawn:booby");
  private static final Set<String> GROUND = Set.of("minecraft:chicken", "naturalist:duck", "spawn:dodo",
      "ecologics:penguin", "dragnlivestock:o_chicken", "tfc:duck", "tfc:chicken", "tfc:penguin",
      "tfc:quail", "tfc:grouse", "tfc:pheasant", "tfc:peafowl", "tfc:turkey");
  private final Map<PathfinderMob, Member> birds = new IdentityHashMap<>();
  private final List<Flock> flocks = new ArrayList<>();
  private final Map<UUID, Shot> shots = new LinkedHashMap<>();
  private final Map<ServerLevel, Map<BlockPos, Long>> noises = new IdentityHashMap<>();
  private long tick;
  public long alarms, nearMisses;

  private static Settings rules() { return FaunaMod.session == null ? null : FaunaMod.session.settings; }
  public static boolean bird(Entity entity) {
    String id = FaunaTypes.id(entity.getType());
    return FLIGHT.contains(id) || GROUND.contains(id) || id.equals("spawn:booby");
  }
  private static boolean free(PathfinderMob mob) {
    return mob.isAlive() && !mob.isRemoved() && !mob.isNoAi() && !mob.isLeashed() && !mob.isPassenger()
        && !(mob instanceof TamableAnimal tame && tame.isTame());
  }
  private static boolean flying(PathfinderMob mob) {
    return FLIGHT.contains(FaunaTypes.id(mob.getType()))
        && (mob instanceof net.minecraft.world.entity.animal.FlyingAnimal || mob instanceof SpawnFlightAccess
            || mob.getNavigation() instanceof FlyingPathNavigation);
  }

  private final class Member {
    final PathfinderMob mob;
    final boolean flight;
    final Goal panic = new ScareGoal(this), together = new TogetherGoal(this);
    Flock flock;
    Vec3 source;
    long panicUntil;
    boolean active = true;
    Member(PathfinderMob mob) { this.mob = mob; flight = flying(mob); }
    boolean canFly() { return flight && (!(mob instanceof SpawnFlightAccess nativeFlight) || nativeFlight.empirecraft$canFly()); }
    void takeOff() { if (mob instanceof SpawnFlightAccess nativeFlight && nativeFlight.empirecraft$canFly()) nativeFlight.empirecraft$takeOff(true); }
  }
  private final class Flock {
    final ServerLevel level;
    final EntityType<?> species;
    final List<Member> members = new ArrayList<>();
    Vec3 target;
    long flightUntil, regroupUntil, nextFlight;
    Flock(Member first) {
      level = (ServerLevel)first.mob.level(); species = first.mob.getType();
      nextFlight = tick + 40 + first.mob.getRandom().nextInt(80);
    }
    Vec3 center() {
      Vec3 center = Vec3.ZERO; int count = 0;
      for (Member b : members) if (free(b.mob)) { center = center.add(b.mob.position()); count++; }
      return count == 0 ? null : center.scale(1.0 / count);
    }
    boolean scared() { return members.stream().anyMatch(b -> b.panicUntil > tick); }
  }
  private static final class Shot {
    final Entity entity;
    final long until;
    Vec3 previous;
    final Set<Object> warned = Collections.newSetFromMap(new IdentityHashMap<>());
    Shot(Entity entity, long until) { this.entity = entity; this.until = until; previous = entity.position(); }
  }

  public void attach(PathfinderMob mob) {
    if (birds.containsKey(mob) || birds.size() >= 2048 || !(mob.level() instanceof ServerLevel) || !bird(mob)) return;
    Member b = new Member(mob); birds.put(mob, b);
    var goals = ((MobGoalsAccessor)mob).empirecraft$birdGoals();
    goals.addGoal(0, b.panic);
    if (b.flight) {
      goals.addGoal(3, b.together);
      Flock group = flocks.stream().filter(g -> g.level == mob.level() && g.species == mob.getType()
          && g.members.size() < rules().birdFlockSize && g.center() != null
          && g.center().distanceToSqr(mob.position()) < 24 * 24).findFirst().orElse(null);
      if (group == null) { group = new Flock(b); flocks.add(group); }
      b.flock = group; group.members.add(b);
      group.members.sort(Comparator.comparing(member -> member.mob.getUUID()));
    }
  }
  private void forget(PathfinderMob mob) {
    Member b = birds.remove(mob); if (b == null) return;
    b.active = false;
    var goals = ((MobGoalsAccessor)mob).empirecraft$birdGoals();
    goals.removeGoal(b.panic); goals.removeGoal(b.together);
    if (b.flock != null) b.flock.members.remove(b);
  }
  public boolean startled(Mob mob) {
    return mob instanceof PathfinderMob path && birds.containsKey(path) && birds.get(path).panicUntil > tick;
  }
  public boolean sameFlock(Mob a, Mob b) {
    Member first = birds.get(a), second = birds.get(b);
    return first != null && second != null && first.flock != null && first.flock == second.flock;
  }

  @SubscribeEvent(priority=EventPriority.LOWEST)
  public void started(ServerStartedEvent event) {
    if (rules() != null) for (Mob mob : FaunaMod.session.loaded)
      if (mob instanceof PathfinderMob path) attach(path);
  }
  @SubscribeEvent public void stopped(ServerStoppedEvent event) {
    for (var mob : new ArrayList<>(birds.keySet())) forget(mob);
    flocks.clear(); shots.clear(); noises.clear(); tick = 0;
  }
  @SubscribeEvent(priority=EventPriority.LOWEST)
  public void joined(EntityJoinLevelEvent event) {
    if (rules() == null || !(event.getLevel() instanceof ServerLevel level)) return;
    if (!level.getServer().isSameThread()) { level.getServer().execute(() -> joined(event)); return; }
    if (event.getEntity() instanceof PathfinderMob mob) attach(mob);
    if (BirdMotion.bullet(FaunaTypes.id(event.getEntity().getType()))) {
      if (shots.size() >= 128) shots.remove(shots.keySet().iterator().next());
      shots.put(event.getEntity().getUUID(), new Shot(event.getEntity(), tick + 200));
      alarm(level, event.getEntity().position(), rules().birdNoiseRadius);
    }
  }
  @SubscribeEvent public void left(EntityLeaveLevelEvent event) {
    if (!event.getLevel().isClientSide && event.getEntity() instanceof PathfinderMob mob) forget(mob);
    // Retain the last bullet segment until the next scan, including an impact between scans.
  }
  @SubscribeEvent public void sound(PlayLevelSoundEvent.AtPosition event) {
    if (event.getLevel() instanceof ServerLevel level && event.getSound() != null && event.getNewVolume() > 0
        && BirdMotion.shotSound(event.getSound().value().getLocation().toString()) && rules() != null)
      alarm(level, event.getPosition(), rules().birdNoiseRadius);
  }
  @SubscribeEvent public void soundAtEntity(PlayLevelSoundEvent.AtEntity event) {
    if (event.getLevel() instanceof ServerLevel level && event.getSound() != null && event.getNewVolume() > 0
        && BirdMotion.shotSound(event.getSound().value().getLocation().toString()) && rules() != null)
      alarm(level, event.getEntity().position(), rules().birdNoiseRadius);
  }
  @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
    if (event.getLevel() instanceof ServerLevel level && rules() != null)
      alarm(level, event.getExplosion().getPosition(), Math.min(256, rules().birdNoiseRadius * 1.5));
  }

  public void alarm(ServerLevel level, Vec3 source, double radius) {
    if (rules() == null || !rules().birdFlocks) return;
    if (!level.getServer().isSameThread()) { level.getServer().execute(() -> alarm(level, source, radius)); return; }
    BlockPos cell = BlockPos.containing(source).atY(0);
    var recent = noises.computeIfAbsent(level, key -> new HashMap<>());
    if (tick - recent.getOrDefault(cell, Long.MIN_VALUE / 2) < 5) return;
    recent.put(cell, tick); alarms++;
    for (Member b : birds.values()) {
      if (b.mob.level() != level || !free(b.mob) || b.mob.distanceToSqr(source) > radius * radius) continue;
      b.source = source;
      b.panicUntil = tick + rules().birdScatterSeconds * 20L + Math.floorMod(b.mob.getUUID().hashCode(), 30);
      if (b.flock != null) {
        b.flock.target = null;
        b.flock.regroupUntil = b.panicUntil + 600;
        b.flock.nextFlight = b.flock.regroupUntil + 200;
      }
    }
  }

  @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || rules() == null) return;
    tick++;
    if (tick % 20 == 0) {
      for (var mob : new ArrayList<>(birds.keySet()))
        if (mob.isRemoved() || !mob.isAlive() || !mob.isAddedToWorld()) forget(mob);
      flocks.removeIf(g -> g.members.isEmpty());
      noises.values().forEach(map -> map.entrySet().removeIf(e -> tick - e.getValue() > 40));
      noises.entrySet().removeIf(e -> e.getValue().isEmpty());
      if (rules().birdFlocks) for (Flock group : flocks) update(group);
    }
    if (rules().birdFlocks && tick % 2 == 0) nearMisses();
    else if (!rules().birdFlocks) shots.clear();
  }

  private void update(Flock group) {
    Vec3 center = group.center(); if (center == null || group.members.size() < 2 || group.scared()) return;
    if (group.regroupUntil > tick) {
      if (group.target == null) group.target = center.add(0, 2, 0);
      if (group.members.stream().filter(b -> free(b.mob))
          .allMatch(b -> b.mob.distanceToSqr(group.target) < 10 * 10)) group.regroupUntil = 0;
      return;
    }
    if (!group.level.isDay() || tick < group.nextFlight) return;
    // Pick one destination for the flock; members retain different formation positions.
    for (int i = 0; i < 8; i++) {
      double angle = group.level.random.nextDouble() * Math.PI * 2;
      int x = (int)Math.floor(center.x + Math.cos(angle) * 24), z = (int)Math.floor(center.z + Math.sin(angle) * 24);
      if (group.level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
      // Cruise well above the terrain so flocks stay visible crossing the sky.
      double y = Math.max(center.y, group.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 28);
      group.target = new Vec3(x + .5, Math.min(y, group.level.getMaxBuildHeight() - 8), z + .5);
      group.flightUntil = tick + 160;
      group.nextFlight = tick + 100 + group.level.random.nextInt(160);
      break;
    }
  }

  private void nearMisses() {
    long start = System.nanoTime();
    for (var iterator = shots.values().iterator(); iterator.hasNext();) {
      Shot shot = iterator.next();
      if (tick > shot.until) { iterator.remove(); continue; }
      Entity entity = shot.entity;
      if (!(entity.level() instanceof ServerLevel level)) { iterator.remove(); continue; }
      Vec3 end = entity.position();
      for (Member bird : birds.values()) {
        Object key = bird.flock == null ? bird : bird.flock;
        if (bird.mob.level() != level || !free(bird.mob) || shot.warned.contains(key)) continue;
        if (BirdMotion.segmentDistanceSquared(point(bird.mob.getEyePosition()), point(shot.previous), point(end)) <= 8 * 8) {
          shot.warned.add(key); nearMisses++;
          alarm(level, bird.mob.position(), 32);
        }
      }
      shot.previous = end;
      if (entity.isRemoved()) iterator.remove();
      if (System.nanoTime() - start > 1_000_000) break;
    }
  }
  private static BirdMotion.Point point(Vec3 p) { return new BirdMotion.Point(p.x, p.y, p.z); }

  private boolean enabled(Member b) { return b.active && rules() != null && rules().birdFlocks && free(b.mob); }
  private boolean steer(Member b, Vec3 target, double speed) {
    ServerLevel level = (ServerLevel)b.mob.level();
    target = new Vec3(target.x, Math.max(level.getMinBuildHeight() + 2,
        Math.min(level.getMaxBuildHeight() - 4, target.y)), target.z);
    BlockPos from = b.mob.blockPosition(), to = BlockPos.containing(target);
    if (!level.getWorldBorder().isWithinBounds(to) || !level.hasChunksAt(
        new BlockPos(Math.min(from.getX(), to.getX()) - 1, from.getY(), Math.min(from.getZ(), to.getZ()) - 1),
        new BlockPos(Math.max(from.getX(), to.getX()) + 1, to.getY() + 2, Math.max(from.getZ(), to.getZ()) + 1))) return false;
    // Vanilla navigation builds a surrounding region. Do not request it across unloaded chunks.
    int padding = (int)Math.ceil(b.mob.getAttributeValue(Attributes.FOLLOW_RANGE)) + 12;
    if (level.hasChunksAt(from.offset(-padding, -1, -padding), from.offset(padding, 8, padding)))
      return b.mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
    if (b.flight && level.noCollision(b.mob, b.mob.getBoundingBox().move(target.subtract(b.mob.position())))
        && level.clip(new ClipContext(b.mob.getEyePosition(), target.add(0, b.mob.getEyeHeight(), 0),
        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.mob)).getType() == HitResult.Type.MISS) {
      b.mob.getNavigation().stop(); b.mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed); return true;
    }
    return false;
  }

  private final class ScareGoal extends Goal {
    final Member bird; long nextPath;
    ScareGoal(Member bird) { this.bird = bird; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() { return enabled(bird) && bird.source != null && tick < bird.panicUntil; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() { nextPath = 0; bird.takeOff(); }
    @Override public void tick() {
      if (BirdFlocks.this.tick < nextPath) return; nextPath = BirdFlocks.this.tick + 10;
      var mob = bird.mob; Vec3 target;
      if (bird.canFly()) {
        bird.takeOff();
        long direction = BirdMotion.scatterSlot(mob.getUUID().getLeastSignificantBits(),
            bird.flock == null ? -1 : bird.flock.members.indexOf(bird),
            bird.flock == null ? 1 : bird.flock.members.size());
        var p = BirdMotion.escape(mob.getX(), mob.getY(), mob.getZ(), bird.source.x, bird.source.z,
            direction, true); target = new Vec3(p.x(), p.y(), p.z());
      } else {
        int padding = (int)Math.ceil(mob.getAttributeValue(Attributes.FOLLOW_RANGE)) + 20;
        var level = (ServerLevel)mob.level(); var at = mob.blockPosition();
        if (!level.hasChunksAt(at.offset(-padding, -8, -padding), at.offset(padding, 8, padding))) return;
        target = DefaultRandomPos.getPosAway(mob, 14, 5, bird.source);
      }
      if (target != null) steer(bird, target, 1.9);
    }
    @Override public void stop() { bird.mob.getNavigation().stop(); }
  }
  private final class TogetherGoal extends Goal {
    final Member bird; long nextPath;
    TogetherGoal(Member bird) { this.bird = bird; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
      Flock group = bird.flock;
      return enabled(bird) && bird.canFly() && group != null && group.members.size() > 1 && group.target != null && !group.scared()
          && (tick < group.flightUntil && group.level.isDay() || tick < group.regroupUntil)
          && bird.mob.getLastHurtByMob() == null
          && group.level.getNearestPlayer(bird.mob, 10) == null;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() { nextPath = 0; bird.takeOff(); }
    @Override public void tick() {
      if (BirdFlocks.this.tick < nextPath) return; nextPath = BirdFlocks.this.tick + 20;
      Flock group = bird.flock; var target = group.target; if (target == null) return;
      bird.takeOff();
      var p = BirdMotion.formation(target.x, target.y, target.z, Math.max(0, group.members.indexOf(bird)));
      steer(bird, new Vec3(p.x(), p.y(), p.z()), 1.45);
    }
    @Override public void stop() { bird.mob.getNavigation().stop(); }
  }
}
