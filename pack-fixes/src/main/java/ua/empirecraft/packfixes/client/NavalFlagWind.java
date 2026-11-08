package ua.empirecraft.packfixes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.quintonc.vs_sails.wind.WindManager;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import ua.empirecraft.engagement.NavalFlags;
import ua.empirecraft.packfixes.FlagWind;

public final class NavalFlagWind {
    private static final WeakHashMap<NavalFlags.FlagEntity,FlagWind.Turn> TURNS=new WeakHashMap<>();
    private NavalFlagWind() {}
    public static void rotate(PoseStack pose,NavalFlags.FlagEntity flag,float partial) {
        var level=flag.getLevel();if(level==null)return;
        Vec3 world=VSGameUtilsKt.toWorldCoordinates(level,Vec3.atCenterOf(flag.getBlockPos()));
        double strength=WindManager.getWindStrength(level,BlockPos.containing(world));
        double radians=Math.toRadians(WindManager.getWindDirection(level,world)+(strength<0?180:0));
        var wind=new Vector3d(Math.cos(radians),0,Math.sin(radians));
        var ship=VSGameUtilsKt.getLoadedShipManagingPos(level,flag.getBlockPos());
        if(ship instanceof ClientShip client)client.getRenderTransform().getWorldToShip().transformDirection(wind);
        double fallback=180-flag.getBlockState().getValue(NavalFlags.FlagBlock.FACING).toYRot();
        var turn=TURNS.computeIfAbsent(flag,key->new FlagWind.Turn());
        double target=Math.abs(strength)<1e-5?fallback:FlagWind.yaw(wind.x,wind.z,fallback);
        pose.mulPose(Axis.YP.rotationDegrees((float)turn.update(target,level.getGameTime()+partial)));
    }
}
