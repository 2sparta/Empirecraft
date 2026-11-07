package melonslise.locks.client.init;

import melonslise.locks.client.gui.KeyRingScreen;
import melonslise.locks.common.init.LocksContainerTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import ua.empirecraft.locks.client.SteelLockPickingScreen;

public final class LocksScreens {
    private LocksScreens() {}
    public static void register() {
        MenuScreens.register(LocksContainerTypes.LOCK_PICKING.get(), SteelLockPickingScreen::new);
        MenuScreens.register(LocksContainerTypes.KEY_RING.get(), KeyRingScreen::new);
    }
}
