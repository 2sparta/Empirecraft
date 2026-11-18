package ua.empirecraft.interactions;

import java.util.EnumSet;
import java.util.Set;
import net.dries007.tfc.common.entities.prey.WildAnimal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.fauna.BirdMotion;
import ua.empirecraft.interactions.mixin.MobGoalAccess;

/** Long-term fear survives a save. A sound is a short-lived alarm, never a reason to attack. */
public final class AnimalThreats {
    static final String OWNER = "EmpirecraftThreatAttacker";
    static final String UNTIL = "EmpirecraftThreatUntil";
    static final String DEFEND_UNTIL = "EmpirecraftThreatDefendUntil";
    public static final int FEAR_TICKS = 7 * 24000;
    private static final Set<String> DEFENSIVE = Set.of(
        "alexsmobs:bison", "alexsmobs:gorilla", "alexsmobs:kangaroo", "alexsmobs:komodo_dragon",
        "naturalist:bear", "naturalist:rhino", "naturalist:elephant", "tfc:moose", "tfc:musk_ox");
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof PathfinderMob mob)
            || !supported(mob) || reaction(mob) != null) return;
        ((MobGoalAccess) mob).empirecraft$goals().addGoal(0, new Reaction(mob));
    }

    private static boolean supported(Mob mob) {
        return (mob instanceof Animal || mob instanceof WildAnimal) && mob.getNavigation() instanceof GroundPathNavigation;
    }

    private static Reaction reaction(Mob mob) {
        for (var goal : ((MobGoalAccess) mob).empirecraft$goals().getAvailableGoals()) {
            if (goal.getGoal() instanceof Reaction reaction) return reaction;
        }
        return null;
    }

    private static boolean loud(String id) {
        return ModList.get().isLoaded("empirecraft_fauna") && BirdMotion.shotSound(id)
            || id.startsWith("thirtyyearswar:") && (id.contains("fire") || id.contains("shot") || id.contains("shoot"))
            || id.startsWith("siegeworks:") && (id.contains("fire") || id.contains("shot"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || !(event.getEntity() instanceof Mob mob)
            || !supported(mob) || reaction(mob) == null || event.getAmount() <= 0 || domesticated(mob)) return;
        long now = mob.level().getGameTime();
        var data = mob.getPersistentData();
        data.putUUID(OWNER, attacker.getUUID());
        data.putLong(UNTIL, now + FEAR_TICKS);
        data.putLong(DEFEND_UNTIL, now + 240);
    }

    @SubscribeEvent
    public void soundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel() instanceof ServerLevel level && event.getSound() != null && event.getNewVolume() > 0
            && loud(event.getSound().value().getLocation().toString())) alarm(level, event.getPosition(), 40);
    }

    @SubscribeEvent
    public void soundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        if (event.getLevel() instanceof ServerLevel level && event.getSound() != null && event.getNewVolume() > 0
            && loud(event.getSound().value().getLocation().toString())) alarm(level, event.getEntity().position(), 40);
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) alarm(level, event.getExplosion().getPosition(), 52);
    }

    private void alarm(ServerLevel level, Vec3 position, double radius) {
        var nearby = new AABB(position, position).inflate(radius);
        long until = level.getGameTime() + 160;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, nearby, AnimalThreats::supported)) {
            if (domesticated(mob) || mob.distanceToSqr(position) > radius * radius) continue;
            var reaction = reaction(mob);
            if (reaction == null || reaction.noiseUntil > until - 20) continue;
            reaction.noiseAt = position;
            reaction.noiseUntil = until;
        }
    }

    private static boolean domesticated(Mob mob) {
        return mob instanceof OwnableEntity pet && pet.getOwnerUUID() != null || mob instanceof AbstractHorse horse && horse.isTamed();
    }

    private static final class Reaction extends Goal {
        private final PathfinderMob animal;
        private Vec3 noiseAt, threat;
        private long noiseUntil, repathAt, strikeAt;
        private Player defenderTarget;

        private Reaction(PathfinderMob animal) {
            this.animal = animal;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private Player knownAttacker(long now) {
            var data = animal.getPersistentData();
            if (data.getLong(UNTIL) <= now || !data.hasUUID(OWNER)) return null;
            Player attacker = animal.level().getPlayerByUUID(data.getUUID(OWNER));
            return attacker != null && attacker.isAlive() && !attacker.isCreative() && !attacker.isSpectator()
                && animal.distanceToSqr(attacker) < 24 * 24 ? attacker : null;
        }

        @Override
        public boolean canUse() {
            if (domesticated(animal) || animal.isPassenger() || !animal.isAlive()) return false;
            long now = animal.level().getGameTime();
            Player attacker = knownAttacker(now);
            var key = ForgeRegistries.ENTITY_TYPES.getKey(animal.getType());
            defenderTarget = attacker != null && key != null && DEFENSIVE.contains(key.toString()) && !animal.isBaby()
                && animal.getHealth() >= animal.getMaxHealth() * 0.55
                && animal.getAttribute(Attributes.ATTACK_DAMAGE) != null && animal.distanceToSqr(attacker) < 9 * 9
                && animal.getPersistentData().getLong(DEFEND_UNTIL) > now ? attacker : null;
            threat = attacker != null ? attacker.position() : noiseUntil > now ? noiseAt : null;
            return threat != null;
        }

        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { repathAt = 0; }
        @Override public void stop() { animal.getNavigation().stop(); }

        @Override
        public void tick() {
            long now = animal.level().getGameTime();
            if (defenderTarget != null) {
                animal.getLookControl().setLookAt(defenderTarget);
                double reach = animal.getBbWidth() + 1.2;
                if (animal.distanceToSqr(defenderTarget) < reach * reach && now >= strikeAt
                    && animal.getSensing().hasLineOfSight(defenderTarget)) {
                    strikeAt = now + 25;
                    animal.doHurtTarget(defenderTarget);
                }
            }
            if (threat == null || now < repathAt) return;
            repathAt = now + 12;
            if (defenderTarget != null) animal.getNavigation().moveTo(defenderTarget, 1.25);
            else {
                Vec3 away = DefaultRandomPos.getPosAway(animal, 16, 7, threat);
                if (away != null) animal.getNavigation().moveTo(away.x, away.y, away.z, 1.35);
            }
        }
    }
}
