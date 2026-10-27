package ua.empirecraft.noend;
import net.minecraft.world.item.Item;
/** Inspect creative entries before the server conversion hook can change their identity. */
public interface RawStack { Item empirecraft$rawItem(); }
