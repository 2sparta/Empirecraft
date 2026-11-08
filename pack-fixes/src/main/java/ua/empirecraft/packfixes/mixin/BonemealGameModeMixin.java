package ua.empirecraft.packfixes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.packfixes.BonemealUse;

@Mixin(value=ServerPlayerGameMode.class,remap=false)
public abstract class BonemealGameModeMixin {
    @WrapMethod(method="m_7179_")
    private InteractionResult empirecraft$boneOn(ServerPlayer player,Level level,ItemStack stack,InteractionHand hand,BlockHitResult hit,Operation<InteractionResult> original) {
        try(var ignored=BonemealUse.enter(player,stack,hit.getLocation())){return original.call(player,level,stack,hand,hit);}
    }
    @WrapMethod(method="m_6261_")
    private InteractionResult empirecraft$boneAir(ServerPlayer player,Level level,ItemStack stack,InteractionHand hand,Operation<InteractionResult> original) {
        try(var ignored=BonemealUse.enter(player,stack,player.position())){return original.call(player,level,stack,hand);}
    }
}
