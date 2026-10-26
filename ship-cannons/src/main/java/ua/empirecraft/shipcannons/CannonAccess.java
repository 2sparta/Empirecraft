package ua.empirecraft.shipcannons;

import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.UUID;

public final class CannonAccess {
    private CannonAccess() {}
    public static boolean nearby(CannonBlockTile tile,Player player) {
        var level=tile.getLevel();
        return level!=null && level==player.level() && !tile.isRemoved()
            && level.getBlockEntity(tile.getBlockPos())==tile
            && VSGameUtilsKt.toWorldCoordinates(level,tile.getBlockPos().getCenter())
                .distanceToSqr(VSGameUtilsKt.toWorldCoordinates(level,player.position()))<=64;
    }
    public static UUID validate(CannonBlockTile tile) {
        UUID owner=tile.getPlayerWhoMayEdit();
        if(owner!=null) {
            var level=tile.getLevel();Player player=level==null?null:level.getPlayerByUUID(owner);
            if(player==null || !nearby(tile,player)){tile.setPlayerWhoMayEdit(null);return null;}
        }
        return owner;
    }
}
