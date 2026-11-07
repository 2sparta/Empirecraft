package ua.empirecraft.locks;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
public final class PickRegistration {
    private PickRegistration() {}
    public static Supplier<Item> supplier() { return SteelLockPickItem::new; }
}
