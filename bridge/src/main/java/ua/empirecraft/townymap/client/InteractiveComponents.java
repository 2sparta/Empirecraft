package ua.empirecraft.townymap.client;

import net.minecraft.network.chat.Component;

/** Inspect styles without converting a component tree into plain text. */
public final class InteractiveComponents {
    private InteractiveComponents() {}
    public static boolean actionable(Component message){
        if(message.getStyle().getClickEvent()!=null)return true;
        for(Component child:message.getSiblings())if(actionable(child))return true;
        return false;
    }
}
