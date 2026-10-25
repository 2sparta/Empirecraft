package com.quintonc.vs_sails.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "vs_sails", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AnchorEntities {
    public static EntityType<AnchorVisualEntity> TYPE;
    private AnchorEntities() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            TYPE = EntityType.Builder.<AnchorVisualEntity>of(AnchorVisualEntity::new, MobCategory.MISC)
                    .sized(.1f, .1f).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("vs_sails:lowered_anchor");
            helper.register(new ResourceLocation("vs_sails", "lowered_anchor"), TYPE);
        });
    }
}
