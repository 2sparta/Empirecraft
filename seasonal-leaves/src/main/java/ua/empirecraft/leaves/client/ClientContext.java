package ua.empirecraft.leaves.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

/** Typed access to the client, attached by its client-only Mixin after construction. */
public interface ClientContext {
    ClientLevel empirecraft$level();
    LevelRenderer empirecraft$renderer();
}
