package ua.empirecraft.fauna;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskCensusTest {
  @TempDir Path folder;

  @Test
  void readsRealNbtWithoutChangingRegionBytesOrTimestamp() throws Exception {
    for (int codec : List.of(1, 2, 3)) {
      Path region = region(codec, false);
      byte[] before = Files.readAllBytes(region);
      var timestamp = Files.getLastModifiedTime(region);
      try (var file = FileChannel.open(region, StandardOpenOption.READ)) {
        assertEquals(
            "saved-biome-fixture",
            DiskCensus.readChunk(file, region, 0, 0, 0).getString("fixture"));
      }
      assertArrayEquals(before, Files.readAllBytes(region));
      assertEquals(timestamp, Files.getLastModifiedTime(region));
    }
  }

  @Test
  void readsExternalEntityChunkAndRejectsMalformedSector() throws Exception {
    Path region = region(2, true);
    try (var file = FileChannel.open(region, StandardOpenOption.READ)) {
      assertEquals(
          "saved-biome-fixture", DiskCensus.readChunk(file, region, 0, 0, 0).getString("fixture"));
      assertThrows(IOException.class, () -> DiskCensus.readChunk(file, region, 1, 0, 1));
    }
  }

  Path region(int codec, boolean external) throws Exception {
    var root = new CompoundTag();
    root.putString("fixture", "saved-biome-fixture");
    var bytes = new ByteArrayOutputStream();
    OutputStream encoded =
        switch (codec) {
          case 1 -> new GZIPOutputStream(bytes);
          case 2 -> new DeflaterOutputStream(bytes);
          default -> bytes;
        };
    try (var out = new DataOutputStream(encoded)) {
      NbtIo.write(root, out);
    }
    byte[] payload = bytes.toByteArray();
    ByteBuffer data = ByteBuffer.allocate(12288);
    data.putInt(0, (2 << 8) | 1);
    data.position(8192);
    data.putInt(external ? 1 : payload.length + 1);
    data.put((byte) (codec | (external ? 128 : 0)));
    if (external) Files.write(folder.resolve("c.0.0.mcc"), payload);
    else data.put(payload);
    Path path = folder.resolve("r.0.0.mca");
    Files.write(path, data.array());
    return path;
  }

  @Test
  void usesSavedBiomePaletteIncludingNegativeSectionsAndPackedLongBoundaries() {
    var chunk = new CompoundTag();
    var sections = new ListTag();
    var section = new CompoundTag();
    section.putByte("Y", (byte) -1);
    var biomes = new CompoundTag();
    var palette = new ListTag();
    for (String id : List.of("minecraft:plains", "minecraft:forest", "minecraft:swamp"))
      palette.add(StringTag.valueOf(id));
    biomes.put("palette", palette);
    // Three biomes need two bits. Cell 33 occupies the second packed long.
    biomes.putLongArray("data", new long[] {0, 2L << 2});
    section.put("biomes", biomes);
    sections.add(section);
    chunk.put("sections", sections);
    assertEquals("minecraft:swamp", DiskCensus.savedBiome(chunk, -12, -8, -16, "fallback"));
    assertEquals("minecraft:plains", DiskCensus.savedBiome(chunk, 0, -16, 0, "fallback"));
    assertEquals("fallback", DiskCensus.savedBiome(chunk, 0, 64, 0, "fallback"));
    biomes.putLongArray("data", new long[0]);
    assertEquals("fallback", DiskCensus.savedBiome(chunk, -12, -8, -16, "fallback"));
    palette.clear();
    palette.add(StringTag.valueOf("minecraft:forest"));
    assertEquals("minecraft:forest", DiskCensus.savedBiome(chunk, 0, -1, 0, "fallback"));
  }
}
