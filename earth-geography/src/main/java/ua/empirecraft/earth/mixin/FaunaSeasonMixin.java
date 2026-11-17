package ua.empirecraft.earth.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.calendar.FaunaRules;
import ua.empirecraft.earth.*;
@Mixin(targets="ua.empirecraft.calendar.CalendarMod",remap=false)
public abstract class FaunaSeasonMixin {
    @Redirect(method="factor",at=@At(value="INVOKE",target="Lua/empirecraft/calendar/FaunaRules;factor(Ljava/lang/String;Ljava/lang/String;I)D"))
    private static double earth$season(String id,String category,int month,ServerLevel l,BlockPos p,EntityType<?> type){
        if(!EarthMod.enabled(l))return FaunaRules.factor(id,category,month);
        var coord=EarthMod.data.coordinates(p.getX(),p.getZ());
        return Math.abs(coord.lat())<10?1:FaunaRules.factor(id,category,Geography.localMonth(month,coord.lat()));
    }
}
