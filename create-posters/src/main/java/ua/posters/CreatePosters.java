package ua.posters;

import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(CreatePosters.ID)
public final class CreatePosters {
    public static final String ID="create_posters";
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ID);
    public static final RegistryObject<EntityType<PosterEntity>> POSTER=ENTITIES.register("poster",()->
        EntityType.Builder.<PosterEntity>m_20704_(PosterEntity::new,MobCategory.MISC)
            .m_20699_(0.5f,0.5f).setTrackingRange(10).setUpdateInterval(Integer.MAX_VALUE)
            .setShouldReceiveVelocityUpdates(false).m_20712_(ID+":poster"));
    public static final RegistryObject<Item> ITEM=ITEMS.register("poster",()->new PosterItem(new Item.Properties().m_41487_(1)));
    public CreatePosters(){
        var bus=FMLJavaModLoadingContext.get().getModEventBus();ITEMS.register(bus);ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(PosterNetwork::register));
    }
}
