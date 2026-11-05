package ua.empirecraft.fauna;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

public final class FaunaTypes {
  public static final String SEEN = "EmpirecraftFaunaExisting";
  private static volatile Settings policy = new Settings();
  private static final Set<MobCategory> ANIMALS =
      Set.of(
          MobCategory.CREATURE,
          MobCategory.AMBIENT,
          MobCategory.WATER_CREATURE,
          MobCategory.WATER_AMBIENT,
          MobCategory.UNDERGROUND_WATER_CREATURE,
          MobCategory.AXOLOTLS);

  private FaunaTypes() {}

  public static String id(EntityType<?> type) {
    return Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(type), "");
  }

  public static void configure(Settings settings) {
    policy = settings;
  }

  public static boolean managed(EntityType<?> type) {
    return ANIMALS.contains(type.getCategory())
        || policy.domesticTypes.contains(id(type))
        || policy.petTypes.contains(id(type));
  }

  public static String biome(ServerLevel level, double x, double y, double z) {
    var chunk =
        level
            .getChunkSource()
            .getChunkNow(
                Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16));
    if (chunk != null)
      return chunk
          .getNoiseBiome(
              QuartPos.fromBlock((int) Math.floor(x)),
              QuartPos.fromBlock((int) Math.floor(y)),
              QuartPos.fromBlock((int) Math.floor(z)))
          .unwrapKey()
          .map(key -> key.location().toString())
          .orElse("minecraft:plains");
    // Unloaded data uses the saved palette in DiskCensus; generator sampling
    // is only its fallback and for not-yet-saved world-generation chunks.
    var holder =
        level
            .getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .getNoiseBiome(
                QuartPos.fromBlock((int) Math.floor(x)),
                QuartPos.fromBlock((int) Math.floor(y)),
                QuartPos.fromBlock((int) Math.floor(z)),
                level.getChunkSource().randomState().sampler());
    return holder.unwrapKey().map(key -> key.location().toString()).orElse("minecraft:plains");
  }

  public static Map<String, Set<String>> biomeTags(ServerLevel level) {
    var result = new HashMap<String, Set<String>>();
    for (var holder : level.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
      var tags = new HashSet<String>();
      holder.tags().forEach(tag -> tags.add(tag.location().toString()));
      result.put(holder.key().location().toString(), Set.copyOf(tags));
    }
    return Map.copyOf(result);
  }

  public static Population.Animal animal(Mob mob, ServerLevel level, boolean loaded) {
    return animalAt(mob, level, mob.getX(), mob.getY(), mob.getZ(), loaded);
  }

  public static Population.Animal animalAt(
      Mob mob, ServerLevel level, double x, double y, double z, boolean loaded) {
    return new Population.Animal(
        mob.getUUID(),
        id(mob.getType()),
        level.dimension().location().toString(),
        x,
        y,
        z,
        biome(level, x, y, z),
        mob instanceof TamableAnimal tame && tame.isTame()
            || mob instanceof AbstractHorse horse && horse.isTamed(),
        loaded);
  }

  public static Population.Animal disk(CompoundTag tag, ServerLevel level, double[] parent) {
    var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(tag.getString("id")));
    if (type == null
        || !managed(type)
        || !tag.hasUUID("UUID")
        || tag.contains("Health", Tag.TAG_ANY_NUMERIC) && tag.getFloat("Health") <= 0) return null;
    ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
    double x = pos.size() == 3 ? pos.getDouble(0) : parent[0],
        y = pos.size() == 3 ? pos.getDouble(1) : parent[1],
        z = pos.size() == 3 ? pos.getDouble(2) : parent[2];
    if (!Double.isFinite(x)
        || !Double.isFinite(y)
        || !Double.isFinite(z)
        || Math.abs(x) > 2147483000
        || Math.abs(z) > 2147483000) return null;
    boolean tame =
        tag.hasUUID("Owner") || tag.getBoolean("Tame") || !tag.getString("OwnerUUID").isBlank();
    return new Population.Animal(
        tag.getUUID("UUID"),
        tag.getString("id"),
        level.dimension().location().toString(),
        x,
        y,
        z,
        biome(level, x, y, z),
        tame,
        false);
  }

  public static Path folder(ServerLevel level) {
    try {
      Object world = level.getClass().getMethod("getWorld").invoke(level);
      return ((File) Class.forName("org.bukkit.World").getMethod("getWorldFolder").invoke(world))
          .toPath();
    } catch (ReflectiveOperationException ignored) {
      return DimensionType.getStorageFolder(
          level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
    }
  }
}
