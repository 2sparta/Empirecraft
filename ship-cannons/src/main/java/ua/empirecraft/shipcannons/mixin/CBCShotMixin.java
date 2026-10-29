package ua.empirecraft.shipcannons.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import ua.empirecraft.shipcannons.CBCRecoil;

@Pseudo
@Mixin(targets={"rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption","rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption"},remap=false)
public abstract class CBCShotMixin {
    @WrapMethod(method="fireShot")
    private void empirecraft$shot(ServerLevel level,PitchOrientedContraptionEntity cannon,Operation<Void> original) {
        var shot=CBCRecoil.begin(cannon);
        try {original.call(level,cannon);} finally {CBCRecoil.end(shot);}
    }
    @WrapOperation(method="fireShot",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean empirecraft$launched(ServerLevel level,Entity projectile,Operation<Boolean> original) {
        var launch=CBCRecoil.capture(level,projectile);
        boolean added=original.call(level,projectile);if(added)CBCRecoil.launched(launch);return added;
    }
}
