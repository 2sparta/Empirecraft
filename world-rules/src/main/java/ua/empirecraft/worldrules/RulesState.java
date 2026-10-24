package ua.empirecraft.worldrules;

/** Do not touch registries while Minecraft/Forge are bootstrapping static ItemStacks. */
public final class RulesState {
    public static volatile boolean active;
    private RulesState() {}
}
