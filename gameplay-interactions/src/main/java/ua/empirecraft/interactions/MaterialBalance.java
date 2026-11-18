package ua.empirecraft.interactions;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public final class MaterialBalance {
    public record Profile(float mining, float blast) {}
    private static volatile Map<Block, Profile> profiles = Map.of();
    private MaterialBalance() {}

    static void load() {
        var result = new IdentityHashMap<Block, Profile>();
        var input = Objects.requireNonNull(MaterialBalance.class.getResourceAsStream("/material-balance.json"));
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = item.getAsJsonObject();
                var profile = new Profile(row.get("mining").getAsFloat(), row.get("blast").getAsFloat());
                for (var value : row.getAsJsonArray("blocks")) {
                    var key = new ResourceLocation(value.getAsString());
                    if (ForgeRegistries.BLOCKS.containsKey(key)) result.put(ForgeRegistries.BLOCKS.getValue(key), profile);
                }
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Material profile is unreadable", error);
        }
        profiles = result;
    }

    public static Profile of(Block block) { return profiles.get(block); }
}
