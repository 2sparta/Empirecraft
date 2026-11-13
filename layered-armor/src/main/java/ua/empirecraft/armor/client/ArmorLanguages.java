package ua.empirecraft.armor.client;

import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.armor.LayeredArmor;

/** Always load this mod's language files, independently of optional pack selection. */
@Mod.EventBusSubscriber(modid=LayeredArmor.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArmorLanguages {
    public static final String PACK_ID="empirecraft_armor_languages";
    private ArmorLanguages() {}
    @SubscribeEvent public static void packs(AddPackFindersEvent event) {
        if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
        Path root=ModList.get().getModFileById(LayeredArmor.ID).getFile().findResource("pack.mcmeta").getParent();
        event.addRepositorySource(consumer->consumer.accept(create(root)));
    }
    public static Pack create(Path root) {
        Pack pack=Pack.readMetaAndCreate(PACK_ID,Component.literal("Empirecraft Armor Languages"),true,
            id->new PathPackResources(id,root,true),PackType.CLIENT_RESOURCES,Pack.Position.TOP,PackSource.BUILT_IN);
        if(pack==null)throw new IllegalStateException("Missing armor language pack metadata");
        return pack;
    }
}
