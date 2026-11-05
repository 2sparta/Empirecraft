package ua.empirecraft.furnitureaudit;

import com.google.gson.GsonBuilder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Test-only server probe, never included in either installed mod set. */
@Mod("furniture_audit")
public final class FurnitureAudit {
    public FurnitureAudit() { MinecraftForge.EVENT_BUS.register(this); }
    @SubscribeEvent public void started(ServerStartedEvent event) throws Exception {
        var result = new LinkedHashMap<String,Object>();
        try {
            Set<String> ids = ForgeRegistries.BLOCKS.getKeys().stream().map(Object::toString).collect(Collectors.toSet());
            Map<String,Long> counts = new TreeMap<>();
            for (String mod : List.of("handcrafted", "timber_joinery")) {
                if (!ModList.get().isLoaded(mod)) throw new AssertionError("Missing mod: " + mod);
                counts.put(mod, ids.stream().filter(id -> id.startsWith(mod + ":")).count());
            }
            for (String id : List.of("handcrafted:oak_cupboard", "handcrafted:oak_drawer", "handcrafted:oak_counter",
                    "handcrafted:oak_nightstand", "handcrafted:oak_chair", "handcrafted:oak_couch",
                    "timber_joinery:joinery_table", "timber_joinery:oak_cabinet", "timber_joinery:oak_nightstand",
                    "timber_joinery:oak_shutter", "timber_joinery:oak_sofa", "timber_joinery:oak_grandfather_clock"))
                if (!ids.contains(id)) throw new AssertionError("Block not registered: " + id);
            if (ids.stream().anyMatch(id -> id.startsWith("mcw"))) throw new AssertionError("Retired Macaw blocks loaded");
            result.put("registered_blocks", counts); result.put("representative_blocks_checked", 12);
            result.put("passed", true);
        } catch (Throwable failure) {
            result.put("passed", false); result.put("error", failure.toString()); failure.printStackTrace();
        }
        result.put("runtime", "Forge 47.4.23 / Minecraft 1.20.1 dedicated server; isolated test world");
        Files.writeString(Path.of("furniture-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("FURNITURE_RUNTIME_RESULT " + result);
    }
}
