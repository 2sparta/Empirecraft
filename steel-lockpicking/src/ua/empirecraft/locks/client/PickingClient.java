package ua.empirecraft.locks.client;

import net.minecraft.client.Minecraft;
import ua.empirecraft.locks.PickPackets;

public final class PickingClient {
    private PickingClient() {}
    public static void receive(PickPackets.Result result) {
        if (Minecraft.getInstance().screen instanceof SteelLockPickingScreen screen) screen.receive(result);
    }
}
