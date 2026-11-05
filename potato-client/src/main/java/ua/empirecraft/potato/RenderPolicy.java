package ua.empirecraft.potato;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import java.util.Set;

/** Deliberately finite: no distance, particles, simulation, input, HUD or resource-pack options. */
public final class RenderPolicy {
    public static final Set<String> KEYS = Set.of("options.biomeBlendRadius", "options.mipmapLevels",
            "options.ao", "options.renderClouds", "options.entityShadows", "options.graphics");
    private RenderPolicy() {}
    public static Object value(String key, Object original, boolean enabled) {
        if (!enabled || key == null) return original;
        return switch (key) {
            case "options.biomeBlendRadius", "options.mipmapLevels" -> original instanceof Integer ? 0 : original;
            case "options.ao", "options.entityShadows" -> original instanceof Boolean ? false : original;
            case "options.renderClouds" -> original instanceof CloudStatus ? CloudStatus.OFF : original;
            // FAST would make previously transparent leaves opaque. Keep the player's leaf visibility.
            case "options.graphics" -> original == GraphicsStatus.FABULOUS ? GraphicsStatus.FANCY : original;
            default -> original;
        };
    }
}
