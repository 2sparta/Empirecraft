package ua.empirecraft.armor.client;

import net.minecraft.network.chat.Component;

/** Safe UI labels even while language resources are unavailable or reloading. */
public final class ArmorLabels {
    private ArmorLabels() {}
    public static Component title(){return Component.translatableWithFallback("empirecraft.armor.title","Armor layers");}
    public static Component rank(int rank){return Component.translatableWithFallback("empirecraft.armor.rank."+rank,switch(rank){case 0->"Padding";case 1->"Mail";case 2->"Outer";default->throw new IllegalArgumentException("Unknown armor layer");});}
}
