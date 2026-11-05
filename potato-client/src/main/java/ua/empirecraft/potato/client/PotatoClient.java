package ua.empirecraft.potato.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import ua.empirecraft.potato.PotatoState;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class PotatoClient {
    private PotatoClient() {}
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new PotatoScreen(parent)));
        if (PotatoState.error() != null) LogUtils.getLogger().error("Potato settings could not be read; profile left disabled: {}", PotatoState.error());
    }
    public static CompletableFuture<Void> setEnabled(boolean enabled) throws IOException {
        PotatoState.setEnabled(enabled);
        // Mipmaps, biome colours and ambient lighting require a real resource/chunk rebuild.
        return Minecraft.getInstance().reloadResourcePacks();
    }
}
