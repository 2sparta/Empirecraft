package ua.empirecraft.chat;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.config.Config;
import com.ebicep.chatplus.config.ConfigVariables;
import com.ebicep.chatplus.features.chattabs.ChatTab;
import com.ebicep.chatplus.features.chattabs.ServerChatTabSettings;
import com.ebicep.chatplus.features.chatwindows.ChatWindow;
import com.ebicep.chatplus.features.chatwindows.TabSettings;
import com.ebicep.chatplus.hud.ChatManager;
import com.ebicep.chatplus.hud.ChatScreenSendMessagePreEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;

/** Called by the pinned ChatPlus bytecode patch; no new Mixin implementation classes. */
public final class ClientChannels {
    private static final Map<ChatTab, Channel> CHANNELS = new WeakHashMap<>();
    private ClientChannels() {}
    public static void configure() {
        CHANNELS.clear();
        ConfigVariables values = Config.INSTANCE.getValues();
        values.setEnabled(true);
        values.setMaxMessages(5000);
        values.setCompactMessagesEnabled(false);
        values.setFilterMessagesEnabled(false);
        values.setShowVanillaWhenUnfocused(false);
        values.getTabNotificationSettings().setEnabled(true);
        values.getTabNotificationSettings().setShowCount(true);
        if (values.getChatWindows().isEmpty()) values.getChatWindows().add(new ChatWindow());
        for (ChatWindow window : values.getChatWindows()) {
            TabSettings tabs = window.getTabSettings();
            List<ChatTab> channels = new ArrayList<>();
            for (Channel channel : Channel.values()) {
                ServerChatTabSettings settings = new ServerChatTabSettings("(?s).*");
                settings.setName(channel.key());
                settings.setAlwaysAdd(true);
                settings.setSkipOthers(false);
                settings.setCommandsOverrideAutoPrefix(true);
                settings.getNotificationSettings().setDisableNotifications(false);
                ChatTab tab = new ChatTab(new ArrayList<>(List.of(settings)));
                CHANNELS.put(tab, channel);
                tab.setChatWindow(window);
                channels.add(tab);
            }
            tabs.setTabs(channels);
            tabs.setHideTabs(false);
            tabs.setShowTabsWhenChatNotOpen(true);
            window.updateWindowReference();
        }
        ChatManager.INSTANCE.resetGlobalSortedTabs();
    }
    public static Channel channel(ChatTab tab) {
        if (CHANNELS.containsKey(tab)) return CHANNELS.get(tab);
        String key = tab.getCurrentSettings().getName();
        for (Channel c : Channel.values()) if (c.key().equals(key)) return c;
        return Channel.SYSTEM;
    }
    public static String label(ChatTab tab) {
        return Component.m_237115_(channel(tab).key()).getString();
    }
    public static boolean matches(ChatTab tab, Component message) {
        return channel(tab) == incoming(message);
    }
    public static Channel incoming(Component message) {
        return IncomingChannel.read(message);
    }
    public static void send(ChatScreenSendMessagePreEvent event) {
        String message = event.getMessage();
        if (message.isBlank() || message.startsWith("/")) return;
        Channel channel = channel(ChatManager.INSTANCE.getGlobalSelectedTab());
        if (channel == Channel.SYSTEM) {
            event.setReturnFunction(true);
            ChatPlus.INSTANCE.sendMessage(Component.m_237115_("empirecraft.chat.system_read_only"));
            return;
        }
        String command = "/ecchat " + channel.id + " " + message;
        if (command.length() > 256) {
            event.setReturnFunction(true);
            ChatPlus.INSTANCE.sendMessage(Component.m_237115_("empirecraft.chat.too_long"));
            return;
        }
        event.setMessage(command);
    }
}
