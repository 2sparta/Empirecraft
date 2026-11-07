package com.epicdeathscreen.client;

import com.epicdeathscreen.core.DeathProfiles;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Small client adapter; the original screen, revival hooks and respawn logic remain intact. */
public final class EmpirecraftDeaths {
    private EmpirecraftDeaths() {}
    private static String entityId(Entity entity) {
        return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
    private static DeathProfiles.Match current() {
        var player = Minecraft.getInstance().player;
        var source = player == null ? null : player.getLastDamageSource();
        if (source == null) return null;
        var direct = source.getDirectEntity();
        String block = direct instanceof FallingBlockEntity falling
                ? BuiltInRegistries.BLOCK.getKey(falling.getBlockState().getBlock()).toString() : "";
        return DeathProfiles.match(source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse(""),
                entityId(direct), entityId(source.getEntity()), block);
    }
    public static String capture() {
        var match = current();
        return match == null ? "" : match.cue();
    }
    public static String phrase(String language, String theme, String damage, String entity, String player, String attacker) {
        var match = current();
        if (match != null) {
            if (!match.damage().isEmpty()) damage = match.damage();
            if (!match.entity().isEmpty()) entity = match.entity();
        }
        return ClientFiles.phrase(language, theme, damage, entity, player, attacker);
    }
    public static SceneCue start(String cue) {
        float gain = ClientFiles.settings().staticVolume();
        if (cue == null || cue.isEmpty() || gain <= 0) return null;
        SceneCue sound = new SceneCue(cue, Math.min(0.75f, gain));
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }
    public static void stop(SceneCue sound) {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
    }
    public static void ensureUkrainianFile() {
        Path file = ClientFiles.directory().resolve("phrases/uk_ua.txt");
        try {
            if (!Files.isDirectory(file.getParent()) || Files.exists(file)) return;
            Files.writeString(file, "# Власні фрази українською, по одній на рядок.\n# [fall], [fire], [mob], [damage:musketmod:bullet]\n# {player}, {attacker}\n\n[generic]\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) { LogUtils.getLogger().warn("Epic Death Screen: cannot create Ukrainian phrase template", e); }
    }
}
