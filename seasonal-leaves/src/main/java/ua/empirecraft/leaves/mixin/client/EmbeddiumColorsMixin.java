package ua.empirecraft.leaves.mixin.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.leaves.SeasonalLeaves;
import ua.empirecraft.leaves.client.EmbeddiumLeafColor;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.model.color.ColorProviderRegistry",remap=false)
public abstract class EmbeddiumColorsMixin {
    @Unique private final Map<Block,ColorProvider<BlockState>> empirecraft$leaves=new ConcurrentHashMap<>();
    @Inject(method="getColorProvider(Lnet/minecraft/world/level/block/Block;)Lme/jellysquid/mods/sodium/client/model/color/ColorProvider;",at=@At("RETURN"),cancellable=true)
    private void empirecraft$leafColors(Block block,CallbackInfoReturnable<ColorProvider<BlockState>> ci) {
        if(SeasonalLeaves.isLeaves(block.defaultBlockState()))ci.setReturnValue(empirecraft$leaves.computeIfAbsent(block,b->new EmbeddiumLeafColor(ci.getReturnValue())));
    }
}
