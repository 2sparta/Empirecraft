package net.minecraft.resources;

/** No registries or Forge bootstrap; validates the resource IDs used by this pack. */
public final class ResourceLocation {
    public ResourceLocation(String id) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid resource ID: " + id);
    }
}
