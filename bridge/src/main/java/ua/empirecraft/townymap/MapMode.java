package ua.empirecraft.townymap;

import java.util.Locale;

/** Per-player presentation; it never changes ownership or permissions. */
public enum MapMode {
    KINGDOMS("Королівства"), TOWNS("Міста"), HIERARCHY("Васальна ієрархія"), TRADE("Торгові центри"),
    PROVINCES("Провінції"), CLIMATE("Кліматичні зони"), RAINFALL("Опади"), SEASONS("Сезонні ризики");
    public final String label;
    MapMode(String label){this.label=label;}
    public boolean environmental(){return this==CLIMATE || this==RAINFALL || this==SEASONS;}
    public MapMode next(){return values()[(ordinal()+1)%values().length];}
    public static MapMode parse(String text){return valueOf(text.toUpperCase(Locale.ROOT));}
}
