package ua.empirecraft.shipphysics;

/** Cache callback only; the runtime parser under test comes from the real JAR. */
public final class BlockProperties {
    public static int clears;
    public static void clearCache() { clears++; }
}
