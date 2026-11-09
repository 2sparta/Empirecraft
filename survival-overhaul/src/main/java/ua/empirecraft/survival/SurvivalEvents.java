package ua.empirecraft.survival;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** A real bottle of untreated water, without restoring the pack's removed potion item. */
@Mod.EventBusSubscriber(modid=SurvivalRules.ID)
public final class SurvivalEvents {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void bottle(PlayerInteractEvent.RightClickItem event) {
        var player=event.getEntity();var stack=event.getItemStack();
        if(stack.getItem()!=Items.GLASS_BOTTLE||SurvivalRules.RAW_WATER==null)return;
        var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(4.5));
        var hit=player.level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,player));
        if(hit.getType()!=HitResult.Type.BLOCK||!player.level().getFluidState(hit.getBlockPos()).is(FluidTags.WATER))return;
        if(!player.level().mayInteract(player,hit.getBlockPos()))return;
        if(!player.level().isClientSide) {
            var filled=new ItemStack(SurvivalRules.RAW_WATER.get());
            if(!player.getAbilities().instabuild)stack.shrink(1);
            if(stack.isEmpty())player.setItemInHand(event.getHand(),filled);
            else if(!player.getInventory().add(filled))player.drop(filled,false);
            player.level().playSound(null,player.blockPosition(),SoundEvents.BOTTLE_FILL,SoundSource.PLAYERS,1,1);
            player.gameEvent(GameEvent.FLUID_PICKUP);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));event.setCanceled(true);
    }
}
