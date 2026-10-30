package ua.empirecraft.townymap;

import java.util.Locale;

/** Per-player presentation; it never changes ownership or permissions. */
public enum MapMode {
    KINGDOMS("Королівства"), TOWNS("Міста"), HIERARCHY("Васальна ієрархія"), TRADE("Торгові центри");
    public final String label;
    MapMode(String label){this.label=label;}
    public MapMode next(){return values()[(ordinal()+1)%values().length];}
    public static MapMode parse(String text){return valueOf(text.toUpperCase(Locale.ROOT));}
}
