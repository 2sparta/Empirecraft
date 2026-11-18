package ua.empirecraft.workshop;
import net.minecraft.core.BlockPos;
/** Additional synchronized state on the native Siegeworks menu. */
public interface CastAccess {
    BlockPos workshop$position();
    void workshop$diameter(int value);
    int workshop$progress();
    int workshop$duration();
    int workshop$status();
}
