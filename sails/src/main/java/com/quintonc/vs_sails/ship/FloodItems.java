package com.quintonc.vs_sails.ship;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="vs_sails",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FloodItems {
    public static Item BILGE_BUCKET;
    private FloodItems(){}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ITEM,helper->{
            BILGE_BUCKET=new Item(new Item.Properties().stacksTo(1)){
                @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
                    lines.add(Component.translatable("tooltip.vs_sails.bucket_volume",FiniteBuckets.contents(stack)));
                    lines.add(Component.translatable("tooltip.vs_sails.bucket_use"));
                }
                @Override public boolean isBarVisible(ItemStack stack){return true;}
                @Override public int getBarWidth(ItemStack stack){return Math.round(13f*FiniteBuckets.contents(stack)/8);}
                @Override public int getBarColor(ItemStack stack){return 0x3f76e4;}
            };
            helper.register(new ResourceLocation("vs_sails","bilge_bucket"),BILGE_BUCKET);
        });
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(()->{FloodNetwork.init();FiniteBuckets.dispensers();});}
}
