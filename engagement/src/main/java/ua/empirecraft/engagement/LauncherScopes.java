package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.quintonc.vs_sails.protection.ShipProtection;

public final class LauncherScopes {
    private LauncherScopes(){}
    public static ShotOrigins.Scope block(ServerLevel level,BlockPos pos,UUID player){
        var ship=ShipProtection.ship(level,pos);var actor=player==null?null:level.getPlayerByUUID(player);
        if(ship!=null){
            if(player!=null&&!Government.controls(level,player,ship.getId()))throw new IllegalArgumentException("Unauthorized ship launcher");
            var source=Combatants.ship(level,ship,actor,player,null);return ShotOrigins.launcher(ShotOrigins.of(source,level));
        }
        return ShotOrigins.enter(actor==null?ShotOrigins.current():ShotOrigins.of(Combatants.party(actor),level));
    }
}
