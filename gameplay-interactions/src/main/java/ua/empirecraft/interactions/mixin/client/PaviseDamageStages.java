package ua.empirecraft.interactions.mixin.client;

import java.util.SortedSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelRenderer.class, remap = false)
public interface PaviseDamageStages {
    @Accessor("f_109409_")
    Long2ObjectMap<SortedSet<BlockDestructionProgress>> empirecraft$damageStages();
}
