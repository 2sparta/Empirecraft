package ua.empirecraft.nativepatch;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.extensions.IForgeEntity;

/** SRG names target the pinned Forge 1.20.1 runtime; injected into Wonderous Sea itself. */
public final class WhaleHarvest {
    static final String CLAIMED = "empirecraft.whale_harvested";
    static final JsonObject PROFILES;
    static {
        try (var in = WhaleHarvest.class.getResourceAsStream("/empirecraft/whale-loot.json")) {
            if (in == null) throw new IOException("Missing whale loot profiles");
            PROFILES = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static void harvest(Entity corpse) {
        if (corpse == null || !(corpse.m_9236_() instanceof ServerLevel level) || corpse.m_213877_()) return;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(corpse.m_6095_());
        if (key == null || !key.m_135827_().equals("wonderoussea")) return;
        String species = key.m_135815_().replaceFirst("_corpse(?:_[23])?$", "");
        if (species.equals("humpback")) species = "humpback_whale";
        if (!PROFILES.has(species) || !key.m_135815_().contains("corpse")) return;
        var data = ((IForgeEntity)corpse).getPersistentData();
        if (data.m_128471_(CLAIMED)) return;
        // Claim before spawning any items; repeated packets or a saved carcass cannot pay twice.
        data.m_128379_(CLAIMED, true);
        if (level.m_46469_().m_46207_(GameRules.f_46135_)) {
            for (var row : PROFILES.getAsJsonObject(species).getAsJsonObject("loot").entrySet()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(row.getKey()));
                if (item == null) throw new IllegalStateException("Unregistered whale resource: " + row.getKey());
                JsonArray range = row.getValue().getAsJsonArray();
                int min = range.get(0).getAsInt(), max = range.get(1).getAsInt();
                int remaining = min + level.m_213780_().m_188503_(max - min + 1);
                int limit = new ItemStack(item).m_41741_();
                while (remaining > 0) {
                    int amount = Math.min(remaining, limit);
                    corpse.m_19983_(new ItemStack(item, amount)); remaining -= amount;
                }
            }
        }
        corpse.m_146870_();
    }
}
