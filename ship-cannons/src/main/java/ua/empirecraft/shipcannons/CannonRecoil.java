package ua.empirecraft.shipcannons;

import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.mehvahdjukaar.supplementaries.common.items.CannonBallItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class CannonRecoil {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue MULTIPLIER,BALL,ITEM,TNT,ARROW,MAX,ROLL,LEVER,KICK,RATE,LINEAR,PULSE,BIG,AUTO,GRAPE,SPREAD;
    private static final ForgeConfigSpec.BooleanValue CBC,CARRY,GRAPESHOT;
    private static final ForgeConfigSpec.IntValue MAX_PELLETS,MAX_BLOCKS,LIGHT_LAYERS;
    static {
        var b=new ForgeConfigSpec.Builder();
        MULTIPLIER=b.comment("Momentum balance multiplier; zero disables this add-on's recoil.").defineInRange("recoilMultiplier",4.0,0,100);
        BALL=b.defineInRange("cannonballMassKg",12.0,.001,10000);ITEM=b.defineInRange("itemMassKg",1.0,.001,10000);
        TNT=b.defineInRange("tntMassKg",4.0,.001,10000);ARROW=b.defineInRange("arrowMassKg",.05,.001,10000);MAX=b.defineInRange("maximumImpulseNs",100000.0,1,1e7);
        ROLL=b.comment("Amplifies the horizontal overturning impulse so broadside shots visibly heel the ship.").defineInRange("rollImpulseMultiplier",20.0,1,100);
        LEVER=b.defineInRange("minimumRollLeverMetres",1.0,0,10);KICK=b.defineInRange("maximumAngularKickDegreesPerShot",4.0,.1,30);
        RATE=b.defineInRange("maximumRecoilAngularSpeedDegrees",20.0,1,90);LINEAR=b.defineInRange("maximumVelocityKickPerShot",.75,.01,10);
        PULSE=b.defineInRange("recoilPulseSeconds",.12,.02,.5);
        CBC=b.define("bigCannonsRecoil",true);BIG=b.defineInRange("bigCannonShellMassKg",25.0,.01,10000);
        AUTO=b.defineInRange("autocannonRoundMassKg",.8,.001,1000);GRAPE=b.defineInRange("grapeshotBagMassKg",8.0,.01,1000);
        CARRY=b.define("carriageDeckMovement",true);GRAPESHOT=b.define("grapeshotLightBlockDamage",true);
        SPREAD=b.defineInRange("grapeshotMinimumSpread",.25,0,1);MAX_PELLETS=b.defineInRange("grapeshotMaximumPellets",128,1,512);
        MAX_BLOCKS=b.defineInRange("grapeshotMaximumBrokenBlocksPerBurst",64,1,512);LIGHT_LAYERS=b.defineInRange("grapeshotLightLayersPerPellet",3,1,8);
        SPEC=b.build();
    }
    private CannonRecoil() {}
    public static RecoilMath.Settings settings() {return new RecoilMath.Settings(ROLL.get(),LEVER.get(),Math.toRadians(KICK.get()),Math.toRadians(RATE.get()),LINEAR.get(),PULSE.get());}
    public static boolean cbcEnabled() {return CBC.get();}
    public static boolean carryEnabled() {return CARRY.get();}
    public static boolean grapeshotEnabled() {return GRAPESHOT.get();}
    public static double grapeshotSpread() {return SPREAD.get();}
    public static int maximumPellets() {return MAX_PELLETS.get();}
    public static int maximumGrapeshotBlocks() {return MAX_BLOCKS.get();}
    public static int lightLayers() {return LIGHT_LAYERS.get();}
    public static double cbcMass(boolean autocannon,boolean grapeshot) {return grapeshot?GRAPE.get():autocannon?AUTO.get():BIG.get();}
    public static double impulse(double mass,double blocksPerTick) {return RecoilMath.momentum(mass,blocksPerTick,MULTIPLIER.get(),MAX.get());}
    public static void fired(CannonBlockTile tile,ServerLevel level,float pitch,float yaw) {
        LoadedServerShip ship=VSGameUtilsKt.getLoadedShipManagingPos(level,tile.getBlockPos());
        if(ship==null || ship.isStatic())return;
        var item=tile.getProjectile().getItem();
        double mass=item instanceof CannonBallItem?BALL.get():item==Items.TNT?TNT.get():item==Items.ARROW || item==Items.SPECTRAL_ARROW || item==Items.TIPPED_ARROW?ARROW.get():ITEM.get();
        // Supplementaries fires the negative of directionFromRotation; its recoil is positive.
        Vec3 recoil=Vec3.directionFromRotation(pitch,yaw);
        double impulse=RecoilMath.momentum(mass,tile.getFirePower(),MULTIPLIER.get(),MAX.get());
        enqueue(ship,new Vector3d(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5),new Vector3d(recoil.x,recoil.y,recoil.z).mul(impulse));
    }
    public static boolean fireWorld(LoadedServerShip ship,Vec3 worldPoint,Vec3 worldVelocity,double mass) {
        if(ship==null || ship.isStatic() || worldVelocity.lengthSqr()<1e-12)return false;
        double magnitude=RecoilMath.momentum(mass,worldVelocity.length(),MULTIPLIER.get(),MAX.get());
        Vector3d point=ship.getWorldToShip().transformPosition(new Vector3d(worldPoint.x,worldPoint.y,worldPoint.z));
        Vector3d impulse=ship.getTransform().getShipToWorldRotation().transformInverse(new Vector3d(worldVelocity.x,worldVelocity.y,worldVelocity.z).normalize().mul(-magnitude));
        return enqueue(ship,point,impulse);
    }
    private static boolean enqueue(LoadedServerShip ship,Vector3d point,Vector3d impulse) {
        RecoilController controller=ship.getAttachment(RecoilController.class);
        if(controller==null || controller.owner!=ship.getId()) {controller=new RecoilController(ship.getId());ship.setAttachment(RecoilController.class,controller);}
        return controller.enqueue(point,impulse,settings());
    }
}
