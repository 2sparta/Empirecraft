package ua.empirecraft.noend;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod("empirecraft_no_end")
public final class NoEnd {
    public NoEnd(){
        var modBus=FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);modBus.addListener(EventPriority.LOWEST,this::creative);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::travel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::join);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::place);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::interact);
        MinecraftForge.EVENT_BUS.addListener(this::missing);
    }
    private void setup(FMLCommonSetupEvent event){event.enqueueWork(Stacks::initialize);}
    private void creative(BuildCreativeModeTabContentsEvent event){
        var removed=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for(var entry:event.getEntries()){
            var id=ForgeRegistries.ITEMS.getKey(((RawStack)(Object)entry.getKey()).empirecraft$rawItem());
            if(id!=null && Policy.blocked(id.toString()))removed.add(entry.getKey());
        }
        for(var stack:removed)event.getEntries().remove(stack);
    }
    private void travel(EntityTravelToDimensionEvent event){if(event.getDimension().m_135782_().toString().equals("minecraft:the_end"))event.setCanceled(true);}
    private void join(EntityJoinLevelEvent event){
        if(event.getLevel().m_5776_())return;
        var id=ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().m_6095_());
        if(id!=null && Policy.ENTITIES.contains(id.toString()))event.setCanceled(true);
    }
    private void place(BlockEvent.EntityPlaceEvent event){
        var id=ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().m_60734_());
        if(id!=null && Policy.BLOCKS.contains(id.toString()))event.setCanceled(true);
    }
    private void interact(PlayerInteractEvent.RightClickBlock event){
        var id=ForgeRegistries.BLOCKS.getKey(event.getLevel().m_8055_(event.getPos()).m_60734_());
        if(id!=null && Policy.BLOCKS.contains(id.toString()))event.setCanceled(true);
    }
    private void missing(MissingMappingsEvent event){
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.ITEMS)){
            String target=Policy.REPLACEMENTS.get(mapping.getKey().toString());
            if(target!=null && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(target)))mapping.remap(ForgeRegistries.ITEMS.getValue(new ResourceLocation(target)));
        }
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.BLOCKS))if(Policy.BLOCKS.contains(mapping.getKey().toString()))mapping.remap(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")));
    }
}
