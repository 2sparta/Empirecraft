package ua.empirecraft.chat;

import java.util.Locale;

public enum Channel {
    GLOBAL("global"), LOCAL("local"), SYSTEM("system"), TOWN("town"), KINGDOM("kingdom");
    public final String id;
    Channel(String id) { this.id = id; }
    public String key() { return "empirecraft.chat." + id; }
    public String marker() { return "empirecraft:chat/v1/" + id; }
    public static Channel fromId(String value) {
        if (value != null) for (Channel c : values()) if (c.id.equals(value.toLowerCase(Locale.ROOT))) return c;
        throw new IllegalArgumentException("Невідомий канал. global, local, town, kingdom.");
    }
    public static Channel fromMarker(String value) {
        for (Channel c : values()) if (c.marker().equals(value)) return c;
        return SYSTEM;
    }
}
