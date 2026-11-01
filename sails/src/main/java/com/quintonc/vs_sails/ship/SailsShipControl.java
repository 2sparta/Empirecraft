package com.quintonc.vs_sails.ship;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quintonc.vs_sails.util.SailsCommands;
import com.quintonc.vs_sails.wind.ServerWindManager;
import com.quintonc.vs_sails.config.ConfigUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.impl.game.ships.PhysShipImpl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

import static java.lang.Math.*;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
@SuppressWarnings({"deprecation","UnstableApiUsage"})
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SailsShipControl implements ShipPhysicsListener, ServerTickListener {

    @JsonIgnore
    public static final Logger LOGGER = LoggerFactory.getLogger("ship_control");

    @JsonIgnore
    private ConcurrentLinkedQueue<Vector3dc> invForces = new ConcurrentLinkedQueue<Vector3dc>();
    @JsonIgnore
    private ConcurrentLinkedQueue<Vector3dc> rotForces = new ConcurrentLinkedQueue<Vector3dc>();
    @JsonIgnore
    private ConcurrentLinkedQueue<ForceAtPos> invPosForces = new ConcurrentLinkedQueue<ForceAtPos>();
    @JsonIgnore
    private ConcurrentLinkedQueue<ForceAtPos> rotPosForces = new ConcurrentLinkedQueue<ForceAtPos>();
    @JsonIgnore
    private ConcurrentLinkedQueue<Double> buoyForces = new ConcurrentLinkedQueue<Double>();
    @JsonIgnore
    private ConcurrentLinkedQueue<Double> rotTorques = new ConcurrentLinkedQueue<Double>();

//    @Volatile
//    boolean toBeStatic = false;
//
//    @Volatile
//    boolean toBeStaticUpdated = false;

    @JsonIgnore
    private static final int sailSpeed = Integer.parseInt(ConfigUtils.config.getOrDefault("sail-power","25000"));
    @JsonIgnore
    private static final boolean forgivingSails = Boolean.parseBoolean(ConfigUtils.config.getOrDefault("forgiving-sails","false"));
    @JsonIgnore
    private static final double noSailZone = toRadians((double)Integer.parseInt(ConfigUtils.config.getOrDefault("no-sail-zone", "90"))/2);
    @JsonIgnore
    private static final double keelStrength = Double.parseDouble(ConfigUtils.config.getOrDefault(
            "keel-power","4.0"));
    @JsonIgnore
    private static final double magicBallastForce = Double.parseDouble(ConfigUtils.config.getOrDefault(
            "magic-ballast-righting-force","0.25"));
    @JsonIgnore
    private static final double ballastStrength = Double.parseDouble(ConfigUtils.config.getOrDefault(
            "ballast-float-strength","0.0625"));
    @JsonIgnore
    private static final double buoyStrength = Double.parseDouble(ConfigUtils.config.getOrDefault(
            "buoy-float-strength","0.125"));

    public double rudderMod = 0;

    public volatile boolean anchorDeployed;
    public long anchorCapstan, anchorBlock;
    public double anchorX, anchorY, anchorZ, anchorLength;
    public String anchorDimension = "";

    public volatile boolean anchorRaising, anchorLocked;
    public boolean anchorWasStatic;
    public double anchorProgress;
    @JsonIgnore volatile boolean anchorReadyToHold;
    @JsonIgnore transient com.quintonc.vs_sails.entity.AnchorVisualEntity anchorVisual;

    public void releaseAnchor() { AnchorMechanics.release(this, true); }
    public void raiseAnchor() { AnchorMechanics.raise(this); }

    public long helmBlock;
    public boolean hasHelm;
    @JsonIgnore private volatile HelmSample helmSample;
    private record HelmSample(long pos, Direction bow, double turn, boolean back, int priority, long tick) {}
    @JsonIgnore private boolean reversing;

    public void submitHelm(BlockPos pos, Direction bow, double turn, boolean piloted, boolean equipped) {
        submitHelm(pos, bow, turn, piloted, equipped, false);
    }

    public void submitHelm(BlockPos pos, Direction bow, double turn, boolean piloted, boolean equipped, boolean back) {
        if (!(world instanceof ServerLevel level) || !ownsPosition(level,pos) || !bow.getAxis().isHorizontal()) return;
        long tick = world.getGameTime();
        int priority = piloted ? 2 : equipped ? 1 : 0;
        HelmSample old = helmSample;
        if (old != null && old.pos != pos.asLong() && old.tick >= tick - 2) {
            if (old.priority > priority || (old.priority == priority && old.pos < pos.asLong())) return;
        }
        Direction stern = bow.getOpposite();
        if (shipDirection.getAxis() != stern.getAxis()) {
            int count = numSquareSails; numSquareSails = numFnASails; numFnASails = count;
            blocksDirty = true;
        }
        shipDirection = stern;
        helmBlock = pos.asLong(); hasHelm = true;
        helmSample = new HelmSample(helmBlock, bow, equipped ? java.lang.Math.max(-1, java.lang.Math.min(1, turn)) : 0,
                back && piloted && equipped, priority, tick);
    }



    public volatile int numSails = 0;
    public volatile int numFurledSails = 0;
    @JsonIgnore private volatile RigSample countedRig;
    private record RigSample(int open, int furled) {}

    void publishSailCounts(int open, int furled) {
        numSails = open; numFurledSails = furled;
        countedRig = new RigSample(open, furled);
    }
    public volatile int numFnASails = 0;
    public volatile int numSquareSails = 0;
    public volatile int numBallast = 0;
    public volatile int numMagicBallast = 0;
    public volatile int numBuoys = 0;
    @JsonIgnore volatile double copiedBuoyVolume;
    public volatile int numHelms = 0;

    public volatile double waterAmount = 0.0;

    public volatile int boundx = 1;
    public volatile int boundz = 1;

    @JsonIgnore
    public volatile Level world = null;

    @JsonIgnore private transient volatile ShipBlockScan blockScan;
    @JsonIgnore private transient volatile boolean blocksDirty = true;
    @JsonIgnore private transient int scanCooldown;
    @JsonIgnore private static final boolean windEnabled = Boolean.parseBoolean(ConfigUtils.config.getOrDefault("enable-wind", "true"));
    @JsonIgnore private volatile WindSample sampledWind = new WindSample(0, 0);
    private record WindSample(double direction, double strength) {}
    @JsonIgnore private volatile BuoyancySample countedHull;
    @JsonIgnore private volatile BuoyancySample currentHullBounds;
    private record BuoyancySample(int solids, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean matches(BuoyancySample b) {
            return b != null && minX == b.minX && minY == b.minY && minZ == b.minZ
                    && maxX == b.maxX && maxY == b.maxY && maxZ == b.maxZ;
        }
        boolean matches(org.joml.primitives.AABBic b) {
            return b != null && minX == b.minX() && minY == b.minY() && minZ == b.minZ()
                    && maxX == b.maxX() && maxY == b.maxY() && maxZ == b.maxZ();
        }
    }
    void publishBuoyancyVolume(int solids, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        countedHull = new BuoyancySample(java.lang.Math.max(0,solids),minX,minY,minZ,maxX,maxY,maxZ);
    }
    private void captureHullBounds() {
        var b = ship.getShipAABB();
        currentHullBounds = b == null ? null : new BuoyancySample(0,b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }
    private double buoyancyVolume(PhysShipImpl body) {
        // PhysShipImpl's AABB accessors are unimplemented in VS 2.4.11; consume only the main-thread snapshot.
        BuoyancySample bounds = currentHullBounds;
        if (bounds == null) return Double.POSITIVE_INFINITY;
        BuoyancySample hull = countedHull;
        // A recount is a replacement in progress, not an empty hull. Keep the last
        // complete displacement even when an edit changes the bounding box.
        double blocks = hull != null && hull.solids > 0
                ? hull.solids : java.lang.Math.max(1.0, (double)(bounds.maxX()-bounds.minX())
                    * (bounds.maxY()-bounds.minY()) * (bounds.maxZ()-bounds.minZ()));
        // Old saves retain their original inertia until VS updates it; both masses remain safe.
        var scale = body.getTransform().getShipToWorldScaling();
        return blocks * java.lang.Math.abs(scale.x()*scale.y()*scale.z());
    }
    @JsonIgnore private transient List<WaterContact.Voxel> waterBlocks=List.of();
    @JsonIgnore private transient volatile WaterContact.Frame waterFrame;
    void publishWaterBlocks(List<WaterContact.Voxel> blocks,boolean buoys) {
        waterBlocks=buoys?List.copyOf(blocks):List.of();
        if(waterBlocks.isEmpty())waterFrame=null;
        else if(world instanceof ServerLevel level && owns(ship))waterFrame=WaterContact.sample(level,ship,waterBlocks);
    }
    @JsonIgnore private final SailDrive sailDrive = new SailDrive();

    // Persist owned holds so a restart never mistakes our freeze for an administrator's freeze.
    public volatile boolean sailSafetyHeld;
    public boolean sailSafetyWasStatic;
    public volatile boolean motionSafetyTripped;
    @JsonIgnore private volatile boolean hullReady;
    @JsonIgnore private volatile boolean clothWithoutHull;
    private record Recovery(double x,double y,double z) {}
    @JsonIgnore private volatile Recovery recoveryTarget,recoveryAcknowledged;

    public void beginRecovery(double x,double y,double z) {
        motionSafetyTripped=true;sailSafetyHeld=true;sailSafetyWasStatic=false;
        recoveryAcknowledged=null;recoveryTarget=new Recovery(x,y,z);
    }

    void publishHullSafety(int cloth, int hull) {
        clothWithoutHull = ShipMotionSafety.needsHull(cloth, hull);
        updateSafetyHold(true);
        hullReady = true;
    }
    private void updateSafetyHold() { updateSafetyHold(hullReady); }
    private void updateSafetyHold(boolean complete) {
        boolean hold = motionSafetyTripped || (complete ? clothWithoutHull : sailSafetyHeld);
        if (hold && !sailSafetyHeld) {
            sailSafetyWasStatic = ship.isStatic();
            sailSafetyHeld = true;
        }
        if (sailSafetyHeld) {
            if (hold) ship.setStatic(true);
            else {
                ship.setStatic(sailSafetyWasStatic || anchorLocked);
                sailSafetyHeld = false;
            }
        }
    }

    @JsonIgnore
    public Component message;

    public Direction shipDirection = Direction.NORTH;

    @JsonIgnore
    public volatile LoadedServerShip ship = null;
    @JsonIgnore private transient volatile long boundShipId = Long.MIN_VALUE;

    public boolean owns(LoadedServerShip body) { return body != null && boundShipId == body.getId(); }
    public boolean ownsPosition(ServerLevel level, BlockPos pos) {
        LoadedServerShip body = VSGameUtilsKt.getShipObjectManagingPos(level,pos);
        return body != null && owns(body);
    }


    @JsonIgnore
    public static SailsShipControl getOrCreate(LoadedServerShip ship, Level world) {
        if (ship != null) {
            SailsShipControl controller = ship.getAttachment(SailsShipControl.class);
            // A copied attachment must never steal the source ship's runtime state.
            if (controller == null || (controller.boundShipId != Long.MIN_VALUE && !controller.owns(ship))) {
                controller = new SailsShipControl();
                ship.setAttachment(SailsShipControl.class,controller);
            }
            controller.boundShipId = ship.getId();
            controller.ship = ship;
            controller.captureHullBounds();
            assert controller != null;
            if (world != null) {
                controller.world = world;
            }
            if (ship.getShipAABB() != null) {
                controller.boundx = java.lang.Math.max(1, ship.getShipAABB().maxX() - ship.getShipAABB().minX());
                controller.boundz = java.lang.Math.max(1, ship.getShipAABB().maxZ() - ship.getShipAABB().minZ());
            } else {
                controller.boundx = 0;
                controller.boundz = 0;
            }
            return controller;
        } else {
            return null;
        }
    }

    public static SailsShipControl getOrCreate(LoadedServerShip ship) {
        if (ship == null) return null;
        MinecraftServer server = ValkyrienSkiesMod.getCurrentServer();
        return getOrCreate(ship, server != null ? VSGameUtilsKt.getLevelFromDimensionId(server, ship.getChunkClaimDimension()) : null);
    }

    @Override
    public void physTick(@NotNull PhysShip physShip, @NotNull PhysLevel physLevel) {
        //LOGGER.info("forces applied");
        // Do not change even the buoyancy/drag flags of a different physics body.
        if (physShip.getId() != boundShipId) return;
        PhysShipImpl physShip1 = (PhysShipImpl) physShip;
        boolean unsafeMotion = !Double.isFinite(physShip1.getMass()) || physShip1.getMass()<=0
                || ShipMotionSafety.unsafe(physShip1.getTransform().getPositionInWorld(),
                physShip1.getVelocity(), physShip1.getAngularVelocity());
        if (unsafeMotion) motionSafetyTripped = true;
        Recovery recovery=recoveryTarget;
        if(recovery!=null && !unsafeMotion && physShip1.getVelocity().lengthSquared()<1
                && physShip1.getAngularVelocity().lengthSquared()<.01
                && physShip1.getTransform().getPositionInWorld().distanceSquared(recovery.x,recovery.y,recovery.z)<.01)
            recoveryAcknowledged=recovery;
        if (motionSafetyTripped || sailSafetyHeld || !hullReady) {
            physShip1.setDoFluidDrag(false);
            physShip1.setBuoyantFactor(0);
            sailDrive.reset();
            invForces.clear(); rotForces.clear(); invPosForces.clear(); rotPosForces.clear();
            if (motionSafetyTripped || sailSafetyHeld) physShip1.setStatic(true);
            return;
        }
        double step = org.valkyrienskies.core.impl.config.VSCoreConfig.SERVER.getPhysics().getPhysicsSpeed()
                / (20.0 * java.lang.Math.max(1, org.valkyrienskies.core.impl.config.VSCoreConfig.SERVER.getPt().getPhysicsTicksPerGameTick()));

        boolean braking = anchorDeployed && !anchorRaising;
        HelmSample helm = helmSample;
        Vector3dc velocity = physShip1.getVelocity();
        reversing = ArcadeSailing.reverseEngaged(reversing, !braking && helm != null && helm.back && helm.priority == 2, velocity);
        RigSample rig = countedRig;
        int openSails = java.lang.Math.max(0, rig != null ? rig.open : numSails);
        int furledSails = java.lang.Math.max(0, rig != null ? rig.furled : numFurledSails);
        boolean coasting = !braking && openSails == 0;
        double displacementVolume = buoyancyVolume(physShip1);
        boolean safeFluidInertia = BuoyancySafety.safeForFluidDrag(physShip1.getMass(),displacementVolume);
        physShip1.setDoFluidDrag(waterFrame==null && safeFluidInertia);
        if (anchorLocked && anchorDeployed && !anchorRaising) {
            physShip1.setBuoyantFactor(0);
            sailDrive.reset();
            invForces.clear(); rotForces.clear(); invPosForces.clear(); rotPosForces.clear();
            return;
        }

        while (!invForces.isEmpty()) {
            physShip1.applyInvariantForce(Objects.requireNonNull(invForces.poll()));
            //LOGGER.info("invForce applied");
        }

        while (!rotForces.isEmpty()) {
            physShip1.applyRotDependentForce(Objects.requireNonNull(rotForces.poll()));
            //LOGGER.info("rotDependentForce applied");
        }

        while (!invPosForces.isEmpty()) {
            ForceAtPos invData = invPosForces.poll();
            if (invData != null) { //fixme if you ever come across a physpipelinecrash(too many game frames)
                physShip1.applyInvariantForceToPos(invData.force, invData.pos);
            }
            //LOGGER.info("invForceTOPOS applied");
        }
        while (!rotPosForces.isEmpty()) {
            ForceAtPos rotData = rotPosForces.poll();
            if (rotData != null && rotData.force != null && rotData.pos != null) { //fixme if rotposforces are not applying
                physShip1.applyRotDependentForceToPos(rotData.force, rotData.pos);
            }
            //LOGGER.info("rotDependentForceTOPOS applied");
        }

//        while (!rotTorques.isEmpty()) {
//            physShip1.applyRotDependentTorque(rotTorques.poll());
//        }

        if (numMagicBallast > 0) {
            Vector3d shipUp = new Vector3d(0.0, 1.0, 0.0);
            Vector3d worldUp = new Vector3d(0.0, 1.0, 0.0);
            //todo possibly modify worldUp based on wind angle & numsails to make ship heel (should really do it separately)
            physShip1.getTransform().getShipToWorldRotation().transform(shipUp);

            double angleBetween = shipUp.angle(worldUp);
            Vector3d idealAngularAcceleration = new Vector3d(0,0,0);

            if (angleBetween > 0.01) {
                Vector3d stabilizationRotationAxisNormalized = shipUp.cross(worldUp, new Vector3d());
                if (stabilizationRotationAxisNormalized.lengthSquared() < 1.0e-12) stabilizationRotationAxisNormalized.set(1, 0, 0);
                else stabilizationRotationAxisNormalized.normalize();
                idealAngularAcceleration.add(stabilizationRotationAxisNormalized.mul(
                        angleBetween, stabilizationRotationAxisNormalized)
                );
            }

            Vector3dc omega = physShip1.getAngularVelocity();
            idealAngularAcceleration.sub(omega.x(), omega.y(), omega.z());

            Vector3d stabilizationTorque = physShip1.getTransform().getShipToWorldRotation().transform(
                    physShip1.getMomentOfInertia().transform(
                            physShip1.getTransform().getShipToWorldRotation().transformInverse(idealAngularAcceleration)
                    )
            );

            stabilizationTorque.mul(numMagicBallast * magicBallastForce);
            physShip1.applyInvariantTorque(stabilizationTorque);

        }

        WaterContact.Frame contact=waterFrame;
        if(contact!=null) {
            // Custom hydrostatics replaces native vertical lift/drag for buoy ships.
            // Actual water volume is sampled independently of VS liquidOverlap and density gates.
            physShip1.setBuoyantFactor(0);
            WaterContact.apply(contact,physShip1,numBuoys,copiedBuoyVolume,numBallast,buoyStrength,ballastStrength,step);
        } else {
            // Ordinary hulls keep native support; a zero/unavailable overlap report must not sink them.
            physShip1.setBuoyantFactor(safeFluidInertia?1+java.lang.Math.max(0,numBallast)*ballastStrength:0);
        }

        Direction bow = helm != null ? helm.bow : shipDirection.getOpposite();
        Vector3d heading = ArcadeSailing.heading(physShip1.getTransform().getShipToWorldRotation(), bow.getStepX(), bow.getStepZ());
        double forwardSpeed = velocity.x() * heading.x + velocity.z() * heading.z;
        WindSample wind = sampledWind;
        double target = reversing ? -1.5 : ArcadeSailing.targetSpeed(heading, wind.direction, wind.strength,
                openSails, furledSails, sailSpeed, physShip1.getMass(), windEnabled);
        Vector3d acceleration = sailDrive.acceleration(heading, velocity, target,
                physShip1.getMass(), hasHelm, braking, reversing, coasting, step);
        target = sailDrive.target();
        physShip1.applyInvariantForce(acceleration.mul(physShip1.getMass()));
        if (hasHelm || braking) {
            double turn = helm != null ? helm.turn : 0;
            double length = bow.getAxis() == Direction.Axis.X ? boundx : boundz;
            double width = bow.getAxis() == Direction.Axis.X ? boundz : boundx;
            Vector3d angular = ArcadeSailing.angularAcceleration(physShip1.getAngularVelocity(), turn, forwardSpeed, length, width, braking);
            var rotation = physShip1.getTransform().getShipToWorldRotation();
            Vector3d torque = rotation.transform(physShip1.getMomentOfInertia().transform(rotation.transformInverse(angular)));
            if (torque.isFinite()) physShip1.applyInvariantTorque(torque);
        }
        if (braking) anchorReadyToHold = ArcadeSailing.canHold(velocity, physShip1.getAngularVelocity());
        if (SailsCommands.debug) {
            DecimalFormat f = new DecimalFormat("0.00");
            message = Component.literal("Ship #"+boundShipId+" Bow: " + bow + " sails: " + openSails + "/" + furledSails + " mass: " + f.format(physShip1.getMass())
                    + " wind: " + f.format(wind.direction) + "/" + f.format(wind.strength)
                    + " windFactor: " + f.format(ArcadeSailing.windFactor(heading, wind.direction, wind.strength))
                    + " target: " + f.format(target) + " speed: " + f.format(forwardSpeed)
                    + " drive: " + f.format(sailDrive.drive()) + " assist: " + f.format(sailDrive.assistance())
                    + " liquid: " + f.format(physShip1.getLiquidOverlap()) + " S10"
                    + " reverse: " + reversing
                    + " turn: " + f.format(helm != null ? helm.turn : 0) + " size: " + boundx + "x" + boundz
                    + " anchor: " + (anchorLocked ? "held" : anchorRaising ? "raising" : braking ? "braking" : "up"));
        }
        if (numSails < 0) numSails = 0;
        if (numSquareSails < 0) {
            numSquareSails = 0;
            LOGGER.info("forced numSquareSails = 0");
        }
        if (numFnASails < 0) {
            numFnASails = 0;
            LOGGER.info("forced numFnASails = 0");
        }
        if (numBallast < 0) {
            numBallast = 0;
            LOGGER.info("forced numBallast = 0");
        }
        if (numMagicBallast < 0) {
            numMagicBallast = 0;
            LOGGER.info("forced numMagicBallast = 0");
        }
        if (numBuoys < 0) {
            numBuoys = 0;
            LOGGER.info("forced numBuoys = 0");
        }
        if (numHelms < 0) {
            numHelms = 0;
            LOGGER.info("forced numHelms = 0");
        }

//        if (toBeStaticUpdated) {
//            physShip1.setStatic(toBeStatic);
//            toBeStaticUpdated = false;
//        }
        waterAmount = physShip1.getLiquidOverlap();

    }

    public void applyInvariantForce (Vector3dc force) {
        //LOGGER.info("inv force requested");
        if (force != null && force.isFinite()) invForces.add(new Vector3d(force));
    }

    public void applyRotDependentForce(Vector3dc force) {
        if (force != null && force.isFinite()) rotForces.add(new Vector3d(force));
        //LOGGER.info("applyrotforce called");
    }

    public void applyInvariantForceToPos(Vector3dc force, Vector3dc pos) {
        ForceAtPos data = new ForceAtPos();
        if (force == null || pos == null || !force.isFinite() || !pos.isFinite()) return;
        data.force = new Vector3d(force);
        data.pos = new Vector3d(pos);
        invPosForces.add(data);
    }

    public void applyRotDependentForceToPos(Vector3dc force, Vector3dc pos) {
        ForceAtPos data = new ForceAtPos();
        if (force == null || pos == null || !force.isFinite() || !pos.isFinite()) return;
        data.force = new Vector3d(force);
        data.pos = new Vector3d(pos);
        rotPosForces.add(data);
        //LOGGER.info("applyrotforceTOPOS called");
    }

    public void updateRudderAngle(double angle) {

    }

    public static double getShipYaw(Quaterniondc shipRotation) {
        Vector3d worldForwardDirection = new Vector3d();
        Vector3d LOCAL_SHIP_FORWARD_NEGATIVE_Z = new Vector3d(0.0, 0.0, -1.0);
        shipRotation.transform(LOCAL_SHIP_FORWARD_NEGATIVE_Z, worldForwardDirection);

        if (worldForwardDirection.lengthSquared() < 1.0e-12) {
            return 0.0;
        }

        double horizontalDistance = sqrt(worldForwardDirection.x * worldForwardDirection.x + worldForwardDirection.z * worldForwardDirection.z);

        double yaw;
        if (horizontalDistance < 1.0e-9) {
            yaw = 0.0;
        } else {
            yaw = atan2(worldForwardDirection.x, -worldForwardDirection.z);
        }
        if (yaw < 0) {
            yaw = 2*PI + yaw;
        }

        return yaw;
    }

    public void addBuoyancy(double buoyancy) {
        buoyForces.add(buoyancy);
    }

//    public void convertToShipDirection(double force) {
//
//    }

//    //fixme ship is always null
//    public double getShipWidth() {
//        if (shipDirection == Direction.NORTH || shipDirection == Direction.SOUTH) { //fixme change to && with != for perf
//            return ship.getShipAABB().maxX() - ship.getShipAABB().minX();
//        } else {
//            return ship.getShipAABB().maxZ() - ship.getShipAABB().minZ();
//        }
//    }
//
//    //fixme ship is always null
//    public double getShipLength() {
//        if (shipDirection == Direction.NORTH || shipDirection == Direction.SOUTH) {
//            return ship.getShipAABB().maxZ() - ship.getShipAABB().minZ();
//        } else {
//            return ship.getShipAABB().maxX() - ship.getShipAABB().minX();
//        }
//    }

//    public void setStatic(boolean b) {
//        toBeStatic = b;
//        toBeStaticUpdated = true;
//    }

    public int getNumSails() {
        return numSails;
    }
    public int getNumBallast() {
        return numBallast;
    }

    private void deleteIfEmpty() { //fixme add call for this
        if (numBallast <= 0 && numSails <= 0 && numFurledSails <= 0 && numMagicBallast <= 0 && numBuoys <= 0 && numHelms == 0) {
            ship.removeAttachment(SailsShipControl.class);
        }
    }

    @Override
    public void onServerTick() {
        if (ship == null || !owns(ship)) return;
        MinecraftServer server = ValkyrienSkiesMod.getCurrentServer();
        if (server == null) return;
        Level currentWorld = VSGameUtilsKt.getLevelFromDimensionId(server, ship.getChunkClaimDimension());
        if (currentWorld == null) return;
        Recovery recovery=recoveryTarget;
        if(recovery!=null && recoveryAcknowledged==recovery) {
            motionSafetyTripped=false;recoveryTarget=null;recoveryAcknowledged=null;
        }
        updateSafetyHold();
        if (world != currentWorld) {
            if (world != null && anchorDeployed) releaseAnchor();
            world = currentWorld; blocksDirty = true; helmSample = null; waterBlocks=List.of(); waterFrame=null;
        }
        if (helmSample != null && (helmSample.tick < currentWorld.getGameTime() - 2
                || !ownsPosition((ServerLevel)currentWorld,BlockPos.of(helmSample.pos))
                || !currentWorld.hasChunkAt(BlockPos.of(helmSample.pos))
                || !(currentWorld.getBlockEntity(BlockPos.of(helmSample.pos)) instanceof com.quintonc.vs_sails.blocks.entity.HelmBlockEntity))) {
            helmSample = null; hasHelm = false;
        }
        captureHullBounds();
        AnchorMechanics.tick(this, (ServerLevel) currentWorld);
        if (windEnabled && (numSails > 0 || numFurledSails > 0)) {
            Vector3dc p = ship.getTransform().getPositionInWorld();
            double direction = ServerWindManager.getWindDirection(world, new Vec3(p.x(), p.y(), p.z()));
            double strength = ServerWindManager.getWindStrength(world, BlockPos.containing(p.x(), p.y(), p.z()));
            sampledWind = new WindSample(Double.isFinite(direction) ? direction : 0, Double.isFinite(strength) ? strength : 0);
        }
        if (blocksDirty || (blockScan == null && countedHull != null && !countedHull.matches(ship.getShipAABB())) || (blockScan != null && !blockScan.matches(ship.getShipAABB(), shipDirection))
                || (blockScan == null && --scanCooldown <= 0)) {
            if (ship.getShipAABB() == null) return;
            // Edits and routine recounts both retain the last completed support
            // snapshot until ShipBlockScan publishes its complete replacement.
            blockScan = new ShipBlockScan(ship.getShipAABB(), shipDirection);
            blocksDirty = false;
        }
        if (blockScan != null && blockScan.step(world, this)) {
            blockScan = null;
            scanCooldown = 200;
        }
        if(!waterBlocks.isEmpty())waterFrame=WaterContact.sample((ServerLevel)currentWorld,ship,waterBlocks);
    }

    public static void markDirty(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(server, pos);
        if (ship != null) {
            SailsShipControl controller = getOrCreate(ship, level);
            controller.blocksDirty = true;
            // Readiness describes the last complete snapshot, not the pending scan.
            // Clearing it here disabled all lift for every neighbour notification.
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ForceAtPos {
        Vector3dc force;
        Vector3dc pos;
    }
}
