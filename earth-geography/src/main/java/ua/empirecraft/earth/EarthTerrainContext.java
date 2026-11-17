package ua.empirecraft.earth;
/** MaterialPalette itself has no dimension parameter. Scope its override to an Earth terrain pass. */
public final class EarthTerrainContext {
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    public static void set(boolean value){ACTIVE.set(value);}
    public static void clear(){ACTIVE.remove();}
    public static boolean active(){return ACTIVE.get();}
}
