package ua.empirecraft.fauna;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** READ-only region parser: never opens Minecraft's write-capable RegionFile implementation. */
public final class DiskCensus implements AutoCloseable {
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "Empirecraft fauna census");
            t.setDaemon(true);
            return t;
          });
  private volatile boolean cancelled;
  public volatile boolean running;
  public volatile Throwable error;
  private long nextWarning;

  private record Found(Population.Animal animal, long version) {}

  private static final Pattern REGION = Pattern.compile("r[.](-?[0-9]+)[.](-?[0-9]+)[.]mca");

  public void start(List<ServerLevel> levels, Population population) {
    if (running) return;
    running = true;
    error = null;
    long epoch = population.beginScan();
    var roots = new HashMap<ServerLevel, Path>();
    for (var level : levels) roots.put(level, FaunaTypes.folder(level));
    executor.submit(
        () -> {
          try {
            var found = new HashMap<UUID, Found>();
            var worlds = new HashSet<String>();
            var failures = new HashSet<String>();
            for (var level : levels) {
              if (cancelled) return;
              String world = level.dimension().location().toString();
              worlds.add(world);
              for (String directory : List.of("region", "entities")) {
                Path folder = roots.get(level).resolve(directory);
                if (!Files.isDirectory(folder)) continue;
                try (var files = Files.list(folder)) {
                  for (Path path :
                      files
                          .filter(p -> REGION.matcher(p.getFileName().toString()).matches())
                          .sorted()
                          .toList()) {
                    if (cancelled) return;
                    Matcher name = REGION.matcher(path.getFileName().toString());
                    name.matches();
                    int rx = Integer.parseInt(name.group(1)) * 32,
                        rz = Integer.parseInt(name.group(2)) * 32;
                    try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ)) {
                      ByteBuffer header = readHeader(file);
                      // RegionFile legitimately creates zero-byte placeholders before its first save.
                      if (header == null) continue;
                      for (int slot = 0; slot < 1024; slot++) {
                        if (cancelled) return;
                        int location = header.getInt(slot * 4);
                        if (location == 0) continue;
                        int x = rx + (slot & 31), z = rz + (slot >> 5);
                        String chunk = world + "/" + x + "/" + z;
                        boolean success = false;
                        for (int attempt = 0; attempt < 3 && !success; attempt++)
                          try {
                            CompoundTag root = readChunk(file, path, x, z, slot);
                            if (root.contains("Level", Tag.TAG_COMPOUND))
                              root = root.getCompound("Level");
                            long stamp =
                                (Integer.toUnsignedLong(header.getInt(4096 + slot * 4)) << 1)
                                    + (directory.equals("entities") ? 1 : 0);
                            CompoundTag terrain = root;
                            if (directory.equals("entities")
                                && !root.getList("Entities", Tag.TAG_COMPOUND).isEmpty()) {
                              Path terrainPath =
                                  roots
                                      .get(level)
                                      .resolve(
                                          "region/r."
                                              + Math.floorDiv(x, 32)
                                              + "."
                                              + Math.floorDiv(z, 32)
                                              + ".mca");
                              if (Files.isRegularFile(terrainPath))
                                try (FileChannel terrainFile =
                                    FileChannel.open(terrainPath, StandardOpenOption.READ)) {
                                  terrain =
                                      readChunk(
                                          terrainFile,
                                          terrainPath,
                                          x,
                                          z,
                                          Math.floorMod(x, 32) + Math.floorMod(z, 32) * 32);
                                  if (terrain.contains("Level", Tag.TAG_COMPOUND))
                                    terrain = terrain.getCompound("Level");
                                }
                            }
                            for (String key : List.of("Entities", "entities"))
                              for (Tag value : root.getList(key, Tag.TAG_COMPOUND))
                                collect(
                                    (CompoundTag) value,
                                    level,
                                    found,
                                    stamp,
                                    new double[] {x * 16, 64, z * 16},
                                    0,
                                    terrain);
                            success = true;
                          } catch (IOException | RuntimeException malformed) {
                            if (attempt == 2) failures.add(chunk);
                          }
                        population.chunksScanned++;
                      }
                    } catch (IOException | RuntimeException unreadable) {
                      for (int x = 0; x < 32; x++)
                        for (int z = 0; z < 32; z++)
                          failures.add(world + "/" + (rx + x) + "/" + (rz + z));
                    }
                  }
                }
              }
            }
            if (cancelled) return;
            for (var value : found.values()) population.importDisk(value.animal, epoch);
            population.finishScan(found.keySet(), worlds, failures, epoch);
            if (!failures.isEmpty() && System.currentTimeMillis() >= nextWarning) {
              nextWarning = System.currentTimeMillis() + 300_000;
              FaunaMod.LOG.warn("Fauna census retained {} unreadable chunks; spawning continues outside affected regions and towns; retry scheduled", failures.size());
            }
          } catch (Throwable failure) {
            error = failure;
            population.abortScan();
            population.scanFailures = Math.max(1,population.scanFailures);
            FaunaMod.LOG.warn(
                "Fauna disk census failed; existing animals are retained and a retry is scheduled",
                failure);
          } finally {
            running = false;
          }
        });
  }

  private void collect(
      CompoundTag tag,
      ServerLevel level,
      Map<UUID, Found> found,
      long stamp,
      double[] parent,
      int depth,
      CompoundTag terrain) {
    if (depth > 32) return;
    var animal = FaunaTypes.disk(tag, level, parent);
    if (animal != null) {
      String biome =
          savedBiome(
              terrain,
              (int) Math.floor(animal.x()),
              (int) Math.floor(animal.y()),
              (int) Math.floor(animal.z()),
              animal.biome());
      animal =
          new Population.Animal(
              animal.uuid(),
              animal.type(),
              animal.world(),
              animal.x(),
              animal.y(),
              animal.z(),
              biome,
              animal.tame(),
              false,
              animal.roaming());
      var old = found.get(animal.uuid());
      if (old == null || stamp > old.version) found.put(animal.uuid(), new Found(animal, stamp));
    }
    var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
    double[] location =
        pos.size() == 3
            ? new double[] {pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)}
            : parent;
    for (Tag child : tag.getList("Passengers", Tag.TAG_COMPOUND))
      collect((CompoundTag) child, level, found, stamp, location, depth + 1, terrain);
  }

  public static String savedBiome(CompoundTag chunk, int x, int y, int z, String fallback) {
    for (Tag value : chunk.getList("sections", Tag.TAG_COMPOUND)) {
      var section = (CompoundTag) value;
      if (section.getByte("Y") != Math.floorDiv(y, 16)) continue;
      var biomes = section.getCompound("biomes");
      var palette = biomes.getList("palette", Tag.TAG_STRING);
      if (palette.isEmpty()) return fallback;
      if (palette.size() == 1) return palette.getString(0);
      int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(palette.size() - 1)),
          perLong = 64 / bits,
          index =
              (Math.floorMod(y, 16) >> 2) * 16
                  + (Math.floorMod(z, 16) >> 2) * 4
                  + (Math.floorMod(x, 16) >> 2);
      long[] packed = biomes.getLongArray("data");
      if (index / perLong >= packed.length) return fallback;
      int choice =
          (int) (packed[index / perLong] >>> ((index % perLong) * bits) & ((1L << bits) - 1));
      return choice < palette.size() ? palette.getString(choice) : fallback;
    }
    return fallback;
  }

  public static ByteBuffer readHeader(FileChannel channel) throws IOException {
    if (channel.size() == 0) return null;
    ByteBuffer header=ByteBuffer.allocate(8192);
    read(channel,header,0);header.flip();return header;
  }

  static void read(FileChannel channel, ByteBuffer out, long position) throws IOException {
    while (out.hasRemaining()) {
      int n = channel.read(out, position);
      if (n < 0) throw new EOFException();
      if (n == 0) throw new IOException("Incomplete region read");
      position += n;
    }
  }

  public static CompoundTag readChunk(FileChannel channel, Path region, int x, int z, int slot)
      throws IOException {
    var offset = ByteBuffer.allocate(4);
    read(channel, offset, slot * 4L);
    int location = offset.flip().getInt();
    int sector = location >>> 8, size = location & 255;
    if (sector < 2 || size == 0) throw new IOException("Invalid region sector");
    var prefix = ByteBuffer.allocate(5);
    read(channel, prefix, sector * 4096L);
    prefix.flip();
    int length = prefix.getInt(), codec = Byte.toUnsignedInt(prefix.get());
    if (length < 1 || length > size * 4096 - 4) throw new IOException("Invalid region length");
    InputStream raw;
    if ((codec & 128) != 0) {
      raw =
          Files.newInputStream(
              region.getParent().resolve("c." + x + "." + z + ".mcc"), StandardOpenOption.READ);
      codec &= 127;
    } else {
      var bytes = ByteBuffer.allocate(length - 1);
      read(channel, bytes, sector * 4096L + 5);
      raw = new ByteArrayInputStream(bytes.array());
    }
    try (InputStream original = raw;
        InputStream decoded =
            switch (codec) {
              case 1 -> new GZIPInputStream(original);
              case 2 -> new InflaterInputStream(original);
              case 3 -> original;
              default -> throw new IOException("Unsupported region compression");
            };
        var in =
            new DataInputStream(
                new FilterInputStream(decoded) {
                  long count;

                  void add(long n) throws IOException {
                    count += Math.max(0, n);
                    if (count > 16 * 1024 * 1024) throw new IOException("Oversized chunk NBT");
                  }

                  public int read() throws IOException {
                    int n = super.read();
                    if (n >= 0) add(1);
                    return n;
                  }

                  public int read(byte[] b, int o, int n) throws IOException {
                    int read = super.read(b, o, n);
                    add(read);
                    return read;
                  }
                })) {
      CompoundTag root = NbtIo.read(in, new NbtAccounter(16 * 1024 * 1024));
      var after = ByteBuffer.allocate(4);
      read(channel, after, slot * 4L);
      if (after.flip().getInt() != location) throw new IOException("Region changed during census");
      return root;
    }
  }

  @Override
  public void close() {
    cancelled = true;
    executor.shutdownNow();
  }
}
