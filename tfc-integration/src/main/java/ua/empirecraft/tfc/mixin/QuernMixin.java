package ua.empirecraft.tfc.mixin;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.tfc.QuernDriveBlockEntity;

@Mixin(value=QuernBlockEntity.class,remap=false)
public abstract class QuernMixin {
    private QuernDriveBlockEntity empirecraft$drive() {
        QuernBlockEntity quern=(QuernBlockEntity)(Object)this;
        if(quern.getLevel()==null || !quern.getLevel().hasChunkAt(quern.getBlockPos().below()))return null;
        return quern.getLevel().getBlockEntity(quern.getBlockPos().below()) instanceof QuernDriveBlockEntity drive?drive:null;
    }
    @Inject(method="isConnectedToNetwork",at=@At("HEAD"),cancellable=true)
    private void empirecraft$connected(CallbackInfoReturnable<Boolean> cir) {
        if(empirecraft$drive()!=null)cir.setReturnValue(((QuernBlockEntity)(Object)this).hasHandstone());
    }
    @Inject(method="getRotationSpeed",at=@At("HEAD"),cancellable=true)
    private void empirecraft$speed(CallbackInfoReturnable<Float> cir) {
        var drive=empirecraft$drive();if(drive!=null)cir.setReturnValue(Math.abs(drive.getSpeed())*(float)(Math.PI*2/1200));
    }
    @Inject(method="getRotationAngle",at=@At("HEAD"),cancellable=true)
    private void empirecraft$angle(float partial,CallbackInfoReturnable<Float> cir) {
        var drive=empirecraft$drive();
        if(drive!=null&&drive.getLevel()!=null)cir.setReturnValue((drive.getLevel().getGameTime()+partial)*drive.getSpeed()*(float)(Math.PI*2/1200));
    }
}
