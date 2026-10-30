package com.quintonc.vs_sails.compat;

import com.quintonc.vs_sails.blocks.SailToggleBlock;
import com.quintonc.vs_sails.ship.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="vs_sails")
public final class RiggingEvents {
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        var level=event.getLevel();var pos=event.getPos();var state=level.getBlockState(pos);
        if(CopycatMaterials.isCopycat(state) && !level.isClientSide)SailsShipControl.markDirty(level,pos);
        if(event.getHand()!=InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty() || event.getEntity().isShiftKeyDown())return;
        if(!(state.getBlock() instanceof SailToggleBlock) && !SailRigging.hasSail(level,pos,state))return;
        // Honour vanilla interaction restrictions and don't mutate from the client thread.
        if(event.getEntity().isSpectator() || !event.getEntity().mayBuild() || !level.mayInteract(event.getEntity(),pos))return;
        if(!level.isClientSide)SailRigging.set(level,pos,!SailRigging.isOpen(level,pos,state));
        event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e){if(e.getLevel() instanceof net.minecraft.world.level.Level l)SailsShipControl.markDirty(l,e.getPos());}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){if(e.getLevel() instanceof net.minecraft.world.level.Level l)SailsShipControl.markDirty(l,e.getPos());}
    @SubscribeEvent public static void neighbours(BlockEvent.NeighborNotifyEvent e){if(e.getLevel() instanceof net.minecraft.world.level.Level l)SailsShipControl.markDirty(l,e.getPos());}
}
