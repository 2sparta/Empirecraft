package ua.empirecraft.interactions;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
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
    static final String PEN="EmpirecraftShelter";
    static final String PEN_DIMENSION="EmpirecraftShelterDimension";
    static final String OWNER = "EmpirecraftThreatAttacker";
    static final String UNTIL = "EmpirecraftThreatUntil";
    static final String DEFEND_UNTIL = "EmpirecraftThreatDefendUntil";
    public static final int FEAR_TICKS = 7 * 24000;
    private static final Set<String> DEFENSIVE = Set.of(
        "alexsmobs:bison", "alexsmobs:gorilla", "alexsmobs:kangaroo", "alexsmobs:komodo_dragon",
        "naturalist:bear", "naturalist:rhino", "naturalist:elephant", "tfc:moose", "tfc:musk_ox");
    static void commands(RegisterCommandsEvent event) {
        var pen=Commands.literal("pen").executes(ctx -> shelter(ctx.getSource(),false));
        pen.then(Commands.literal("clear").executes(ctx -> shelter(ctx.getSource(),true)));
        event.getDispatcher().register(Commands.literal("animal").then(pen));
    }

    private static int shelter(CommandSourceStack source,boolean clear) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();
        Vec3 eye=player.getEyePosition(), reach=player.getLookAngle().scale(6);
        var block=player.pick(6,1,false);
        var target=ProjectileUtil.getEntityHitResult(player,eye,eye.add(reach),player.getBoundingBox().expandTowards(reach).inflate(1),
            entity -> entity instanceof Mob&&entity.isPickable(),Math.min(36,eye.distanceToSqr(block.getLocation())));
        if (player.isSpectator() || target==null || !(target.getEntity() instanceof Mob mob) || !supported(mob)
            || !player.getUUID().equals(owner(mob))) {
            source.sendFailure(Component.translatable("empirecraft.animal.pen.look"));
            return 0;
        }
        var data=mob.getPersistentData();
        if (clear) {
            data.remove(PEN);
            data.remove(PEN_DIMENSION);
        } else {
            data.putLong(PEN,mob.blockPosition().asLong());
            data.putString(PEN_DIMENSION,mob.level().dimension().location().toString());
        }
        source.sendSuccess(() -> Component.translatable(clear?"empirecraft.animal.pen.clear":"empirecraft.animal.pen.set"),false);
        return 1;
    }

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
            || !(mob.level() instanceof ServerLevel level) || !supported(mob) || reaction(mob) == null
            || event.getAmount() <= 0 || domesticated(mob) || !visible(attacker)) return;
        long now = mob.level().getGameTime();
        var data = mob.getPersistentData();
        data.putUUID(OWNER, attacker.getUUID());
        data.putLong(UNTIL, now + FEAR_TICKS);
        data.putLong(DEFEND_UNTIL, now + 240);
        AnimalFamiliarity.hurt(mob,attacker.getUUID(),now);
        for (Mob neighbour:level.getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(14),
            other -> other!=mob && other.getType()==mob.getType() && !domesticated(other))) {
            var response=reaction(neighbour);
            if (response==null || neighbour.distanceToSqr(mob)>14*14) continue;
            response.herdAt=attacker.position();
            response.herdUntil=now+200;
        }
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
            if (mob.distanceToSqr(position) > radius * radius) continue;
            var reaction = reaction(mob);
            if (reaction == null || reaction.noiseUntil > until - 20) continue;
            reaction.noiseAt = position;
            reaction.noiseUntil = until;
        }
    }

    private static UUID owner(Mob mob) {
        if (mob instanceof OwnableEntity pet) return pet.getOwnerUUID();
        if (mob instanceof AbstractHorse horse && horse.isTamed()) return horse.getOwnerUUID();
        return null;
    }

    private static boolean domesticated(Mob mob) {
        return owner(mob)!=null || mob instanceof AbstractHorse horse && horse.isTamed();
    }

    private static boolean visible(Player player) {
        return player!=null && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private static final class Reaction extends Goal {
        private final PathfinderMob animal;
        private Vec3 noiseAt, herdAt, threat, refuge;
        private Player waryPlayer;
        private long noiseUntil, herdUntil, repathAt, strikeAt, nextObservation;
        private Player defenderTarget;

        private Reaction(PathfinderMob animal) {
            this.animal = animal;
            nextObservation=animal.level().getGameTime()+Math.floorMod(animal.getId(),40);
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private Player knownAttacker(long now) {
            var data = animal.getPersistentData();
            if (data.getLong(UNTIL) <= now || !data.hasUUID(OWNER)) return null;
            Player attacker = animal.level().getPlayerByUUID(data.getUUID(OWNER));
            return visible(attacker) && animal.distanceToSqr(attacker) < 24*24 ? attacker:null;
        }

        private void observe(long now) {
            if (now<nextObservation) return;
            nextObservation=now+40;
            waryPlayer=null;
            double dangerRange=24*24;
            for (Player player:animal.level().getEntitiesOfClass(Player.class,animal.getBoundingBox().inflate(24),AnimalThreats::visible)) {
                double distance=animal.distanceToSqr(player);
                boolean afraid=AnimalFamiliarity.afraid(animal,player.getUUID(),now);
                boolean calm=!player.isSprinting()&&!player.swinging;
                if (!afraid && distance<=12*12 && calm && noiseUntil<=now && herdUntil<=now
                    && animal.getSensing().hasLineOfSight(player)) {
                    AnimalFamiliarity.observe(animal,player.getUUID(),now);
                }
                int trust=AnimalFamiliarity.score(animal,player.getUUID(),now);
                double shyDistance=calm?Math.max(0,4-trust*.55):Math.max(2,9-trust*.55);
                if (distance<dangerRange && (afraid||distance<shyDistance*shyDistance)) {
                    waryPlayer=player;
                    dangerRange=distance;
                }
            }
        }

        private Vec3 refuge() {
            var level=animal.level();
            var data=animal.getPersistentData();
            if (data.contains(PEN) && data.getString(PEN_DIMENSION).equals(level.dimension().location().toString())) {
                var pen=BlockPos.of(data.getLong(PEN));
                Vec3 position=Vec3.atBottomCenterOf(pen);
                if (level.hasChunkAt(pen) && animal.distanceToSqr(position)<48*48
                    && position.distanceToSqr(noiseAt)>36) return position;
            }
            UUID owner=owner(animal);
            Player familiar=owner==null?null:level.getPlayerByUUID(owner);
            if (visible(familiar) && !familiar.isSprinting() && animal.distanceToSqr(familiar)<14*14
                && familiar.position().distanceToSqr(noiseAt)>64) return familiar.position();
            return null;
        }

        @Override
        public boolean canUse() {
            if (animal.isPassenger() || animal.isVehicle() || !animal.isAlive()) return false;
            long now=animal.level().getGameTime();
            defenderTarget=null;
            refuge=null;
            if (domesticated(animal)) {
                threat=noiseUntil>now?noiseAt:null;
                if (threat!=null) refuge=refuge();
                return threat!=null;
            }
            Player attacker=knownAttacker(now);
            var key=ForgeRegistries.ENTITY_TYPES.getKey(animal.getType());
            if (attacker!=null && key!=null && DEFENSIVE.contains(key.toString()) && !animal.isBaby()
                && animal.getHealth()>=animal.getMaxHealth()*.55 && animal.getAttribute(Attributes.ATTACK_DAMAGE)!=null
                && animal.distanceToSqr(attacker)<9*9 && animal.getPersistentData().getLong(DEFEND_UNTIL)>now) {
                defenderTarget=attacker;
            }
            observe(now);
            threat=attacker!=null?attacker.position():noiseUntil>now?noiseAt:herdUntil>now?herdAt:
                visible(waryPlayer)&&animal.distanceToSqr(waryPlayer)<24*24?waryPlayer.position():null;
            return threat!=null;
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
            if (refuge!=null) {
                if (animal.distanceToSqr(refuge)<=2.25) {
                    animal.getNavigation().stop();
                    return;
                }
                if (animal.getNavigation().moveTo(refuge.x,refuge.y,refuge.z,1.35)) return;
            }
            if (defenderTarget != null) animal.getNavigation().moveTo(defenderTarget, 1.25);
            else {
                Vec3 away = DefaultRandomPos.getPosAway(animal, 16, 7, threat);
                if (away != null) animal.getNavigation().moveTo(away.x, away.y, away.z, 1.35);
            }
        }
    }
}
