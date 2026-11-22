package ua.empirecraft.armory.mixin;

import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import ua.empirecraft.armory.SmithsAnvilWorkshop;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmithsAnvilBlock.class, remap = false)
public abstract class SmithsAnvilUseMixin {
    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$openAssembly(BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> info) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).isEmpty() || player.isSpectator()) return;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SmithsAnvilBlockEntity anvil
                && !anvil.isLockedFor(player.getUUID())) {
            NetworkHooks.openScreen(serverPlayer, ((SmithsAnvilWorkshop) anvil).empirecraft$workshop(), pos);
        }
        info.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
