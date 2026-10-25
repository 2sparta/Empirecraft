package ua.empirecraft.content;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_content_policy")
public final class ContentMod {
    public ContentMod() {
        MinecraftForge.EVENT_BUS.addListener(this::starting);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::interact);
        MinecraftForge.EVENT_BUS.addListener(this::place);
    }
    private void starting(ServerAboutToStartEvent event){Stacks.initialize();}
    private void stopped(ServerStoppedEvent event){Stacks.active=false;}
    private void join(EntityJoinLevelEvent event) {
        var id=ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().m_6095_());
        if(!event.getLevel().m_5776_() && id!=null && Policy.ENTITIES.contains(id.toString()))event.setCanceled(true);
    }
    private static boolean blocked(net.minecraft.world.level.block.state.BlockState state) {
        var id=ForgeRegistries.BLOCKS.getKey(state.m_60734_());return id!=null && Policy.BLOCKS.contains(id.toString());
    }
    private void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getLevel().m_5776_() && blocked(event.getLevel().m_8055_(event.getPos())))event.setCanceled(true);
    }
    private void place(BlockEvent.EntityPlaceEvent event) {
        if(blocked(event.getPlacedBlock()))event.setCanceled(true);
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            for(var snapshot:multi.getReplacedBlockSnapshots())if(blocked(snapshot.getCurrentBlock()))event.setCanceled(true);
    }
}
