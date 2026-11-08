package ua.empirecraft.leaves.client;

import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/** Reuse every original sprite/UV/render type. Untinted leaf faces gain a dedicated tint index. */
public final class SeasonalLeafModel extends BakedModelWrapper<BakedModel> {
    private final LeafQuadTint tinted=new LeafQuadTint();
    public SeasonalLeafModel(BakedModel model) {super(model);}
    @Override public List<BakedQuad> getQuads(BlockState s,Direction d,RandomSource r) {return tinted.apply(originalModel.getQuads(s,d,r));}
    @Override public List<BakedQuad> getQuads(BlockState s,Direction d,RandomSource r,ModelData data,RenderType type) {return tinted.apply(originalModel.getQuads(s,d,r,data,type));}
}
