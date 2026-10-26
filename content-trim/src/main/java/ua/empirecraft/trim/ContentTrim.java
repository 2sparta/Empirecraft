package ua.empirecraft.trim;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod("empirecraft_content_trim")
public final class ContentTrim {
    public static final Set<String> MOBS;
    public static final Map<String, String> REPLACEMENTS;
    static {
        MOBS = new HashSet<>();
        for (JsonElement id : read("/blocked-mobs.json").getAsJsonArray()) MOBS.add(id.getAsString());
        var replacements = new HashMap<String,String>();
        for (var row : read("/removed-content.json").getAsJsonObject().getAsJsonObject("replacements").entrySet())
            replacements.put(row.getKey(), row.getValue().getAsString());
        REPLACEMENTS = Map.copyOf(replacements);
    }
    static JsonElement read(String path) {
        try (var stream = ContentTrim.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public ContentTrim() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::join);
        MinecraftForge.EVENT_BUS.addListener(this::missing);
    }
    private void join(EntityJoinLevelEvent event) {
        if (event.getLevel().m_5776_()) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().m_6095_());
        if (id != null && MOBS.contains(id.toString())) event.setCanceled(true);
    }
    private void missing(MissingMappingsEvent event) {
        for (var mapping : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            String target = REPLACEMENTS.get(mapping.getKey().toString());
            if (target != null) {
                var id = new ResourceLocation(target);
                if (ForgeRegistries.ITEMS.containsKey(id)) mapping.remap(ForgeRegistries.ITEMS.getValue(id));
            }
        }
        for (var mapping : event.getAllMappings(ForgeRegistries.Keys.BLOCKS)) {
            if (REPLACEMENTS.containsKey(mapping.getKey().toString()))
                mapping.remap(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")));
        }
    }
}
