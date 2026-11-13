package ua.empirecraft.chat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

public final class ChatFormat {
    private ChatFormat() {}
    public static TextComponent message(Channel channel, String city, String kingdom, String name, String title, String cosmetic, String body) {
        TextComponent root = new TextComponent("");
        root.setInsertion(channel.marker());
        root.addExtra(part("[" + city + "|" + kingdom + "] ", ChatColor.GRAY));
        root.addExtra(part(name, ChatColor.WHITE));
        if (!title.isBlank()) root.addExtra(part(" " + title, ChatColor.GOLD));
        if (!cosmetic.isBlank()) {
            root.addExtra(part(" ", ChatColor.WHITE));
            TextComponent icon = part(cosmetic, ChatColor.WHITE);
            if(CosmeticDisplay.bitmap(cosmetic))icon.setFont("empirecraft_chat:titles");
            root.addExtra(icon);
        }
        root.addExtra(part(": ", ChatColor.DARK_GRAY));
        root.addExtra(part(body, ChatColor.WHITE));
        return root;
    }
    public static TextComponent part(String text, ChatColor color) {
        TextComponent result = new TextComponent(text); result.setColor(color); return result;
    }
}
