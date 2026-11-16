package ua.empirecraft.armory;
public final class OttomanCombat {
    public static final String NS="hamzabus_ottoman_mod:";
    private OttomanCombat(){}
    public static String profile(String id){return id.startsWith(NS)?id.equals(NS+"shishak_helmet")?"plate":"padded":null;}
    public static String weapon(String id){return switch(id){case NS+"topuz",NS+"nakkareci"->NS+"mace";case NS+"yatagan",NS+"kilij"->NS+"sword";default->id;};}
}
