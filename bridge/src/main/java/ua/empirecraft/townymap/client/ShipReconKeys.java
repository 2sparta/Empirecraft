package ua.empirecraft.townymap.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.extensions.IForgeKeyMapping;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.townymap.EmpireTownyMap;

@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ShipReconKeys {
    public static final KeyMapping SAVE=new KeyMapping("key.empirecraft.save_ship",76,"key.categories.empirecraft.navigation");
    private static final IKeyConflictContext SCOPING=new IKeyConflictContext(){
        public boolean isActive(){return ShipReconClient.focus()>=0;}
        public boolean conflicts(IKeyConflictContext other){return other==this;}
    };
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){((IForgeKeyMapping)(Object)SAVE).setKeyConflictContext(SCOPING);event.register(SAVE);}
    private ShipReconKeys(){}
}
