package ua.empirecraft.packfixes.client;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.packfixes.PackFixes;

@Mod.EventBusSubscriber(modid=PackFixes.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TranslationPack {
    private TranslationPack() {}
    @SubscribeEvent public static void packs(AddPackFindersEvent event) {
        if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
        var root=ModList.get().getModFileById(PackFixes.ID).getFile().findResource("builtin","translations");
        event.addRepositorySource(consumer->consumer.accept(create(root)));
    }
    public static Pack create(java.nio.file.Path root) {
        Pack pack=Pack.readMetaAndCreate("empirecraft_builtin_translations",Component.literal("Empirecraft — українська / русский"),true,
            id->new PathPackResources(id,root,true),PackType.CLIENT_RESOURCES,Pack.Position.TOP,PackSource.BUILT_IN);
        if(pack==null)throw new IllegalStateException("Missing built-in Empirecraft translations");
        return pack;
    }
}
