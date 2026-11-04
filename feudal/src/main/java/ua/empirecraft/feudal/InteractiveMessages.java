package ua.empirecraft.feudal;

import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.*;

/** Components are sent directly to the chosen player; ordinary player prose is never parsed as actions. */
public final class InteractiveMessages {
    private InteractiveMessages() {}
    public static void button(Player player,String label,String command,boolean suggest){
        TextComponent component=new TextComponent(label);
        component.setClickEvent(new ClickEvent(suggest?ClickEvent.Action.SUGGEST_COMMAND:ClickEvent.Action.RUN_COMMAND,command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new net.md_5.bungee.api.chat.hover.content.Text(command)));
        component.setInsertion(command);player.spigot().sendMessage(component);
    }
}
