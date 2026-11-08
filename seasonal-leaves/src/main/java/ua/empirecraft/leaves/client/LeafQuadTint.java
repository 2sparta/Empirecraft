package ua.empirecraft.leaves.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;

/** Geometry-only helper: no renderer, GPU state, generated textures or mutable input quads. */
public final class LeafQuadTint {
    private final Map<List<BakedQuad>,List<BakedQuad>> cache=new ConcurrentHashMap<>();
    public List<BakedQuad> apply(List<BakedQuad> quads) {
        if(quads.isEmpty())return quads;
        boolean missing=false;for(var q:quads)if(!q.isTinted()){missing=true;break;}
        if(!missing)return quads;
        if(cache.size()>=128)return makeTinted(quads);
        return cache.computeIfAbsent(quads,this::makeTinted);
    }
    private List<BakedQuad> makeTinted(List<BakedQuad> list) {
        List<BakedQuad> result=new ArrayList<>(list.size());
        for(var q:list)result.add(q.isTinted()?q:new BakedQuad(q.getVertices(),ClientSeasons.UNTINTED,q.getDirection(),q.getSprite(),q.isShade(),q.hasAmbientOcclusion()));
        return List.copyOf(result);
    }
}
