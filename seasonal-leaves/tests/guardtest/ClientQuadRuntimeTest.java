package guardtest;

import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import ua.empirecraft.leaves.client.*;

public final class ClientQuadRuntimeTest {
    public static void main(String[] args)throws Exception {
        int[] vertices=new int[32];vertices[3]=0xffececec;
        var plain=new BakedQuad(vertices,-1,Direction.UP,null,true,false);
        var nativeTint=new BakedQuad(vertices,2,Direction.NORTH,null,false,true);
        var model=new LeafQuadTint();
        var quads=List.of(plain,nativeTint);
        var result=model.apply(quads);
        if(result.get(0).m_111305_()!=ClientSeasons.UNTINTED||!result.get(0).m_111304_())throw new AssertionError("Untinted mod leaf did not gain a tint index");
        if(result.get(0).m_111303_()!=vertices||result.get(0).m_111306_()!=Direction.UP||!result.get(0).m_111307_()||result.get(0).hasAmbientOcclusion())throw new AssertionError("Original geometry/UV/lighting changed");
        if(result.get(1)!=nativeTint||plain.m_111304_())throw new AssertionError("Original quads were mutated");
        if(model.apply(quads)!=result)throw new AssertionError("Repeated model faces must reuse the cached list");
        if(model.apply(List.of(nativeTint)).get(0)!=nativeTint)throw new AssertionError("Tinted native leaf changed");
        System.out.println("CLIENT_QUAD_RUNTIME_PASS actual production helper and Forge BakedQuad execution: native tint preserved; untinted leaf enabled; UV/geometry/lighting and cache reuse preserved");
    }
}
