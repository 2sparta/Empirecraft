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
import java.util.EnumMap;
import net.minecraft.network.chat.Component;

/** Called by the pinned ChatPlus bytecode patch; no new Mixin implementation classes. */
public final class ClientChannels {
    private static final Map<ChatTab, Channel> CHANNELS = new WeakHashMap<>();
    private ClientChannels() {}
    public static boolean enabled(){return Config.INSTANCE.getValues().getEnabled();}
    public static void configure() {
        CHANNELS.clear();
        ConfigVariables values = Config.INSTANCE.getValues();
        boolean merged=ClientPreferences.merged();
        values.setMaxMessages(5000);
        values.setCompactMessagesEnabled(false);
        values.setFilterMessagesEnabled(false);
        values.setShowVanillaWhenUnfocused(false);
        values.getTabNotificationSettings().setEnabled(true);
        values.getTabNotificationSettings().setShowCount(true);
        if (values.getChatWindows().isEmpty()) values.getChatWindows().add(new ChatWindow());
        for (ChatWindow window : values.getChatWindows()) {
            TabSettings tabs = window.getTabSettings();
            Channel selected=Channel.GLOBAL;
            if(!tabs.getTabs().isEmpty())for(Channel c:Channel.values())if(c.key().equals(tabs.getSelectedTab().getCurrentSettings().getName()))selected=c;
            var existing=new EnumMap<Channel,ChatTab>(Channel.class);
            for(ChatTab tab:tabs.getTabs())for(Channel c:Channel.values())if(c.key().equals(tab.getCurrentSettings().getName()))existing.putIfAbsent(c,tab);
            boolean hadSystem=existing.containsKey(Channel.SYSTEM);
            ChatTab global=existing.computeIfAbsent(Channel.GLOBAL,c->create(c,window));
            ChatTab system=existing.computeIfAbsent(Channel.SYSTEM,c->create(c,window));
            var history=ChannelLayout.history(global.getMessages(),system.getMessages(),m->m.getGuiMessage().f_90786_(),merged?values.getMaxMessages():0);
            int unreadGlobal=global.getUnreadCount(),unreadSystem=system.getUnreadCount();
            global.setMessages(new ArrayList<>());system.setMessages(new ArrayList<>());
            for(var message:history){
                if(merged||incoming(message.getGuiMessage().f_240363_())==Channel.GLOBAL)global.getMessages().add(message);
                else system.getMessages().add(message);
            }
            if(!merged)for(ChatTab tab:List.of(global,system))if(tab.getMessages().size()>values.getMaxMessages()&&values.getMaxMessages()>0)
                tab.setMessages(new ArrayList<>(tab.getMessages().subList(tab.getMessages().size()-values.getMaxMessages(),tab.getMessages().size())));
            global.setUnreadCount(Math.min(merged?unreadGlobal+unreadSystem:unreadGlobal,global.getMessages().size()));
            system.setUnreadCount(Math.min(hadSystem?unreadSystem:unreadGlobal,system.getMessages().size()));
            List<ChatTab> channels = new ArrayList<>();
            for (Channel channel : ChannelLayout.tabs(merged)) {
                ChatTab tab=existing.computeIfAbsent(channel,c->create(c,window));
                CHANNELS.put(tab, channel);
                tab.setChatWindow(window);
                channels.add(tab);
            }
            tabs.setTabs(channels);
            tabs.setSelectedTabIndex(ChannelLayout.tabs(merged).indexOf(ChannelLayout.selection(selected,merged)));
            tabs.setStartRenderTabIndex(0);tabs.setTabPositionsInitialized(false);
            tabs.getSelectedTab().setUnreadCount(0);
            tabs.setHideTabs(false);
            tabs.setShowTabsWhenChatNotOpen(true);
            window.updateWindowReference();
            for(ChatTab tab:List.of(global,system)){
                tab.setRescaleChat(true);
                if(Config.INSTANCE.getLoaded())tab.refreshDisplayMessages();else tab.queueRefreshDisplayedMessages(true);
            }
        }
        ChatManager.INSTANCE.resetGlobalSortedTabs();
    }
    private static ChatTab create(Channel channel,ChatWindow window) {
        var settings=new ServerChatTabSettings("(?s).*");settings.setName(channel.key());settings.setAlwaysAdd(true);settings.setSkipOthers(false);
        settings.setCommandsOverrideAutoPrefix(true);settings.getNotificationSettings().setDisableNotifications(false);
        var tab=new ChatTab(new ArrayList<>(List.of(settings)));tab.setChatWindow(window);return tab;
    }
    public static Channel channel(ChatTab tab) {
        if (CHANNELS.containsKey(tab)) return CHANNELS.get(tab);
        String key = tab.getCurrentSettings().getName();
        for (Channel c : Channel.values()) if (c.key().equals(key)) return c;
        return Channel.SYSTEM;
    }
    public static String label(ChatTab tab) {
        if(ClientPreferences.merged()&&channel(tab)==Channel.GLOBAL)return Component.m_237115_("empirecraft.chat.tab.global_merged").getString();
        return Component.m_237115_("empirecraft.chat.tab." + channel(tab).id).getString();
    }
    public static boolean matches(ChatTab tab, Component message) {
        return ChannelLayout.matches(channel(tab),incoming(message),ClientPreferences.merged());
    }
    public static Channel incoming(Component message) {
        return IncomingChannel.read(message);
    }
    public static void send(ChatScreenSendMessagePreEvent event) {
        if(!Config.INSTANCE.getValues().getEnabled())return;
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
