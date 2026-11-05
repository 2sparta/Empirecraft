package ua.empirecraft.fauna;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;

/** Index is saved with the overworld; a read-only disk census reconciles crash-time differences. */
public final class IndexData extends SavedData {
  public final List<Population.Animal> initial = new ArrayList<>();
  public Population population;

  public static IndexData load(CompoundTag root) {
    if (root.getInt("schema") != 1)
      throw new IllegalStateException("Unsupported fauna index schema");
    var data = new IndexData();
    for (Tag value : root.getList("animals", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) value;
      data.initial.add(
          new Population.Animal(
              tag.getUUID("uuid"),
              tag.getString("type"),
              tag.getString("world"),
              tag.getDouble("x"),
              tag.getDouble("y"),
              tag.getDouble("z"),
              tag.getString("biome"),
              tag.getBoolean("tame"),
              false));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag root) {
    root.putInt("schema", 1);
    var list = new ListTag();
    for (var animal : population == null ? initial : population.all()) {
      var tag = new CompoundTag();
      tag.putUUID("uuid", animal.uuid());
      tag.putString("type", animal.type());
      tag.putString("world", animal.world());
      tag.putDouble("x", animal.x());
      tag.putDouble("y", animal.y());
      tag.putDouble("z", animal.z());
      tag.putString("biome", animal.biome());
      tag.putBoolean("tame", animal.tame());
      list.add(tag);
    }
    root.put("animals", list);
    return root;
  }
}
