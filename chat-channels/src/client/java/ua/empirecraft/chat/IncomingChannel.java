package ua.empirecraft.chat;

import net.minecraft.network.chat.Component;

public final class IncomingChannel {
    private IncomingChannel() {}
    public static Channel read(Component message) {
        if (message == null) return Channel.SYSTEM;
        String insertion = message.m_7383_().m_131189_();
        if (insertion != null && insertion.startsWith("empirecraft:chat/v1/")) return Channel.fromMarker(insertion);
        // Bungee/Mohist may wrap the actual payload in an empty root component.
        if (message.m_214077_() == net.minecraft.network.chat.ComponentContents.f_237124_ ||
            message.m_214077_() instanceof net.minecraft.network.chat.contents.LiteralContents literal && literal.f_237368_().isEmpty()) {
            for (Component child : message.m_7360_()) {
                String marker = child.m_7383_().m_131189_();
                if (marker != null && marker.startsWith("empirecraft:chat/v1/")) return Channel.fromMarker(marker);
            }
        }
        return Channel.SYSTEM;
    }
}
