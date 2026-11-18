package ua.empirecraft.interactions;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

final class AnimalFamiliarity {
    private static final String KEY="EmpirecraftFamiliarity";
    private static final int MAX_PLAYERS=8;
    static final int MAX_SCORE=10;
    static final int OBSERVATION_INTERVAL=100;
    private static final long DECAY_INTERVAL=72_000;
    private AnimalFamiliarity() {}

    static int score(Mob animal,UUID player,long now) {
        return score(bonds(animal).getCompound(player.toString()),now);
    }

    static boolean afraid(Mob animal,UUID player,long now) {
        return bonds(animal).getCompound(player.toString()).getLong("afraidUntil")>now;
    }

    static void observe(Mob animal,UUID player,long now) {
        var bonds=bonds(animal);
        var key=player.toString();
        var entry=bonds.getCompound(key);
        if (entry.getLong("afraidUntil")>now
            || entry.contains("seen",Tag.TAG_LONG)&&now-entry.getLong("seen")<OBSERVATION_INTERVAL) return;
        entry.putInt("trust",Math.min(MAX_SCORE,score(entry,now)+1));
        entry.putLong("seen",now);
        store(animal,bonds,key,entry);
    }

    static void hurt(Mob animal,UUID attacker,long now) {
        var bonds=bonds(animal);
        var entry=new CompoundTag();
        entry.putInt("trust",0);
        entry.putLong("seen",now);
        entry.putLong("afraidUntil",now+AnimalThreats.FEAR_TICKS);
        store(animal,bonds,attacker.toString(),entry);
    }

    private static int score(CompoundTag entry,long now) {
        long elapsed=Math.max(0,now-entry.getLong("seen"));
        return (int)Math.max(0,entry.getInt("trust")-elapsed/DECAY_INTERVAL);
    }

    private static CompoundTag bonds(Mob animal) {
        return animal.getPersistentData().getCompound(KEY);
    }

    private static void store(Mob animal,CompoundTag bonds,String key,CompoundTag entry) {
        if (!bonds.contains(key) && bonds.size()>=MAX_PLAYERS) {
            bonds.getAllKeys().stream().min(Comparator.comparingLong(k -> bonds.getCompound(k).getLong("seen")))
                .ifPresent(bonds::remove);
        }
        bonds.put(key,entry);
        animal.getPersistentData().put(KEY,bonds);
    }
}
