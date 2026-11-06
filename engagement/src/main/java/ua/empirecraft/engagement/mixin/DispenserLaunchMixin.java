package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.engagement.LauncherScopes;

@Mixin(value=DispenserBlock.class,remap=false)
public abstract class DispenserLaunchMixin {
    @WrapMethod(method="m_5824_") private void launch(ServerLevel level,BlockPos pos,Operation<Void> original){try(var ignored=LauncherScopes.block(level,pos,null)){original.call(level,pos);}}
}
