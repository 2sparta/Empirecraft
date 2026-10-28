package ua.empirecraft.shipcannons.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBurst;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;
import ua.empirecraft.shipcannons.*;

@Pseudo
@Mixin(targets="rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst",remap=false)
public abstract class GrapeshotBurstMixin extends Projectile implements GrapeshotState {
    @Unique private int empirecraft$broken;
    @Unique private net.minecraft.core.BlockPos empirecraft$stop;
    protected GrapeshotBurstMixin(EntityType<? extends Projectile> type,Level level) {super(type,level);}
    @Override public int empirecraft$broken() {return empirecraft$broken;}
    @Override public void empirecraft$setBroken(int count) {empirecraft$broken=count;}
    @Override public void empirecraft$stopAt(net.minecraft.core.BlockPos pos) {empirecraft$stop=pos;}
    @Override public boolean empirecraft$consumeStop(net.minecraft.core.BlockPos pos) {
        boolean stop=pos.equals(empirecraft$stop);empirecraft$stop=null;return stop;
    }
    @Inject(method="clipAndDamage",at=@At("HEAD"),cancellable=true)
    private void empirecraft$pellet(ProjectileBurst.SubProjectile pellet,CallbackInfoReturnable<HitResult> cir) {
        if((Object)this instanceof GrapeshotBurst && !level().isClientSide && CannonRecoil.grapeshotEnabled())
            cir.setReturnValue(GrapeshotDamage.clip((ProjectileBurst)(Object)this,pellet,this::canHitEntity));
    }
    @Inject(method="m_7380_",at=@At("RETURN"))
    private void empirecraft$save(CompoundTag tag,CallbackInfo ci) {tag.putInt("EmpirecraftTornBlocks",empirecraft$broken);}
    @Inject(method="m_7378_",at=@At("RETURN"))
    private void empirecraft$load(CompoundTag tag,CallbackInfo ci) {empirecraft$broken=Math.max(0,tag.getInt("EmpirecraftTornBlocks"));}
}
