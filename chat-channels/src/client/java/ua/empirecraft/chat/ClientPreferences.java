package ua.empirecraft.chat;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.config.Config;
import com.ebicep.chatplus.features.speechtotext.SpeechToText;
import java.io.IOException;
import me.shedaniel.clothconfig2.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;

/** A small settings screen remains reachable even when ChatPlus itself is disabled. */
@Mod.EventBusSubscriber(modid="chatplus",value=Dist.CLIENT)
public final class ClientPreferences {
    private static final ChannelPreferencesStore STORE=new ChannelPreferencesStore(FMLPaths.CONFIGDIR.get().resolve("chatplus/empirecraft-channels.json"));
    private static boolean loaded,openRequested;
    private ClientPreferences() {}
    public static boolean merged() {
        if(!loaded){loaded=true;try{STORE.load();}catch(IOException error){LoggerFactory.getLogger("EmpirecraftChatPlus").warn("Could not load channel preferences; using separate tabs",error);}}
        return STORE.merged();
    }
    public static Screen screen(Screen previous) {
        boolean[] draft={Config.INSTANCE.getValues().getEnabled(),merged()};
        var builder=ConfigBuilder.create().setParentScreen(previous).setTitle(Component.m_237115_("empirecraft.chat.settings.title"));
        var entries=builder.entryBuilder();var category=builder.getOrCreateCategory(Component.m_237115_("empirecraft.chat.settings.category"));
        category.addEntry(entries.startBooleanToggle(Component.m_237115_("empirecraft.chat.settings.enabled"),draft[0])
            .setDefaultValue(true).setTooltip(Component.m_237115_("empirecraft.chat.settings.enabled.tooltip"))
            .setSaveConsumer(value->draft[0]=value).build());
        category.addEntry(entries.startBooleanToggle(Component.m_237115_("empirecraft.chat.settings.merge"),draft[1])
            .setDefaultValue(false).setTooltip(Component.m_237115_("empirecraft.chat.settings.merge.tooltip"))
            .setSaveConsumer(value->draft[1]=value).build());
        builder.setSavingRunnable(()->apply(draft[0],draft[1]));return builder.build();
    }
    public static void apply(boolean enabled,boolean merge) {
        var values=Config.INSTANCE.getValues();boolean oldEnabled=values.getEnabled(),oldMerge=merged();
        try {
            STORE.save(merge);values.setEnabled(enabled);
            if(!enabled)SpeechToText.INSTANCE.setRecordMic(false);
            ClientChannels.configure();Config.INSTANCE.save();
        }catch(IOException|RuntimeException error){
            values.setEnabled(oldEnabled);
            try{STORE.save(oldMerge);ClientChannels.configure();}catch(IOException|RuntimeException rollback){error.addSuppressed(rollback);}
            LoggerFactory.getLogger("EmpirecraftChatPlus").error("Could not save ChatPlus settings",error);
            ChatPlus.INSTANCE.sendMessage(Component.m_237115_("empirecraft.chat.settings.save_failed"));
        }
    }
    public static void requestOpen(boolean value){if(value)openRequested=true;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&openRequested){openRequested=false;var client=Minecraft.m_91087_();client.m_91152_(screen(client.f_91080_));}
    }
}
