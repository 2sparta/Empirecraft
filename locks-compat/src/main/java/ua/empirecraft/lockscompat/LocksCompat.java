package ua.empirecraft.lockscompat;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

/** Server-side guards for the existing Locks storage and key system. */
@Mod("empirecraft_locks_compat")
public final class LocksCompat {
    public LocksCompat() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, serverSide) -> true));
    }
}
