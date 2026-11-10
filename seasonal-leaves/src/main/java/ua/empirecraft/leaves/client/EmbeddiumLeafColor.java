package ua.empirecraft.leaves.client;

import java.util.Arrays;
import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Embeddium bypasses BlockColors.getColor. Apply the same palette to its ABGR vertex colours. */
public final class EmbeddiumLeafColor implements ColorProvider<BlockState> {
    private final ColorProvider<BlockState> original;
    public EmbeddiumLeafColor(ColorProvider<BlockState> original) {this.original=original;}
    public void getColors(WorldSlice slice,BlockPos pos,BlockState state,ModelQuadView quad,int[] colors) {
        if(original!=null&&quad.getColorIndex()!=ClientSeasons.UNTINTED)original.getColors(slice,pos,state,quad,colors);
        else Arrays.fill(colors,-1);
        for(int i=0;i<colors.length;i++) {
            int abgr=colors[i];int rgb=((abgr&255)<<16)|(abgr&0xff00)|((abgr>>>16)&255);
            int color=ClientSeasons.color(state,slice,pos,rgb,quad.getColorIndex());
            colors[i]=0xff000000|((color&255)<<16)|(color&0xff00)|((color>>>16)&255);
        }
    }
}
