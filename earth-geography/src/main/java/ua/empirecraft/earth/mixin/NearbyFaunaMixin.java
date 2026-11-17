package ua.empirecraft.earth.mixin;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.earth.*;
/** The pack's additional EVENT spawner has its own seasonal chance table. */
@Pseudo
@Mixin(targets="ua.empirecraft.fauna.NearbyFauna",remap=false)
public abstract class NearbyFaunaMixin {
    @Redirect(method="attempt",at=@At(value="INVOKE",target="Lua/empirecraft/fauna/SpawnCatalog$Entry;chance(IZ)I"))
    private int earth$localChance(@Coerce Object entry,int month,boolean hot,@Coerce Object originalEntry,ServerPlayer player,int roll){
        if(EarthMod.enabled(player.serverLevel())){
            var p=EarthMod.data.coordinates(player.getX(),player.getZ());
            month=Geography.localMonth(month,p.lat());
            if(Math.abs(p.lat())<10)month=6;
            hot=EarthMod.temperature(player.serverLevel(),player.blockPosition())>=22;
        }
        try{return (int)entry.getClass().getMethod("chance",int.class,boolean.class).invoke(entry,month,hot);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported fauna chance API",e);}
    }
}
