package ua.empirecraft.fauna;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Only known natural animals can expire. Protection is sticky across saves and bucket/egg
 * transfers.
 */
public final class Roaming {
  public static final String NATURAL = "EmpirecraftFaunaNatural",
      PROTECTED = "EmpirecraftFaunaProtected",
      NEAR = "EmpirecraftFaunaNear";

  private Roaming() {}

  public static boolean natural(Mob mob) {
    return mob.getPersistentData().getBoolean(NATURAL);
  }

  public static void protect(Mob mob) {
    mob.getPersistentData().putBoolean(PROTECTED, true);
  }

  public static void mark(Mob mob, long now) {
    mob.getPersistentData().putBoolean(NATURAL, true);
    mob.getPersistentData().putLong(NEAR, now);
  }

  public static boolean protectedAnimal(Mob mob) {
    return mob.getPersistentData().getBoolean(PROTECTED)
        || mob.hasCustomName()
        || mob.isLeashed()
        || mob.isPersistenceRequired()
        || mob.isVehicle()
        || mob.isPassenger()
        || mob instanceof TamableAnimal t && t.isTame()
        || mob instanceof AbstractHorse h && (h.isTamed() || h.isSaddled())
        || mob instanceof Bucketable b && b.fromBucket();
  }

  public static boolean protectedTag(CompoundTag tag) {
    var data = tag.getCompound("ForgeData");
    if (data.getBoolean(PROTECTED)
        || tag.contains("CustomName")
        || tag.contains("Leash")
        || tag.getBoolean("PersistenceRequired")
        || tag.hasUUID("Owner")
        || tag.hasUUID("OwnerUUID")
        || !tag.getString("OwnerUUID").isEmpty()
        || tag.getBoolean("Tame")
        || tag.getBoolean("Saddle")
        || tag.getBoolean("ChestedHorse")
        || tag.getBoolean("FromBucket")) return true;
    for (String key :
        java.util.List.of(
            "Items",
            "Inventory",
            "HandItems",
            "ArmorItems",
            "SaddleItem",
            "ArmorItem",
            "CheekPouchLeft",
            "CheekPouchRight")) {
      if (tag.contains(key, 10) && !tag.getCompound(key).isEmpty()) return true;
      if (tag.contains(key, 9))
        for (var item : tag.getList(key, 10)) if (!((CompoundTag) item).isEmpty()) return true;
    }
    return false;
  }

  public static boolean expired(long now, long last, int seconds) {
    return last > 0 && now >= last && now - last >= seconds * 1000L;
  }
}
