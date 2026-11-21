package ua.empirecraft.forgemigration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Standalone migration of the established Bukkit dimension ID in copied player NBT. */
public final class PlayerDimensions {
    public static void main(String[] args) throws IOException {
        Path playerdata = Path.of(args[0]);
        int updated = 0;
        try (var entries = Files.newDirectoryStream(playerdata, "*.dat")) {
            for (Path file : entries) {
                CompoundTag root = NbtIo.m_128937_(file.toFile());
                if (root == null) throw new IOException("Missing player NBT root");
                boolean changed = remap(root, "Dimension") | remap(root, "SpawnDimension");
                if (root.m_128425_("LastDeathLocation", 10)) {
                    changed |= remap(root.m_128469_("LastDeathLocation"), "dimension");
                }
                if (changed) {
                    var temporary = Files.createTempFile(playerdata, ".dimension-", ".dat.tmp");
                    try {
                        NbtIo.m_128944_(root, temporary.toFile());
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                    updated++;
                }
            }
        }
        System.out.println(updated);
    }

    private static boolean remap(CompoundTag tag, String key) {
        if (!"minecraft:world_europe".equals(tag.m_128461_(key))) return false;
        tag.m_128359_(key, "minecraft:overworld");
        return true;
    }
}
