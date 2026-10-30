package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.blocks.*;
import com.quintonc.vs_sails.compat.CopycatMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.*;

/** Iterative connected rig toggling, including mixed native/copycat cloth and ropes. */
public final class SailRigging {
    private SailRigging() {}
    public static boolean hasSail(Level level,BlockPos pos,BlockState state) {
        return state.getBlock() instanceof SailBlock || CopycatMaterials.parts(level,pos,state).stream().anyMatch(p->p.material().getBlock() instanceof SailBlock);
    }
    public static boolean isOpen(Level level,BlockPos pos,BlockState state) {
        if(state.getBlock() instanceof SailBlock || state.getBlock() instanceof SailToggleBlock)return state.getValue(SailBlock.SET);
        return CopycatMaterials.parts(level,pos,state).stream().anyMatch(p->p.material().getBlock() instanceof SailBlock && p.material().getValue(SailBlock.SET));
    }
    public static void set(Level level,BlockPos start,boolean open) {
        if(!(level instanceof ServerLevel server))return;
        var owner=VSGameUtilsKt.getShipObjectManagingPos(server,start);
        long id=owner==null?Long.MIN_VALUE:owner.getId();
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start.immutable());
        List<BlockPos> changed=new ArrayList<>();
        while(!queue.isEmpty() && seen.size()<100000) {
            BlockPos pos=queue.removeFirst();if(!seen.add(pos)||server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var body=VSGameUtilsKt.getShipObjectManagingPos(server,pos);
            if((body==null?Long.MIN_VALUE:body.getId())!=id)continue;
            BlockState state=level.getBlockState(pos);
            boolean nativeSail=state.getBlock() instanceof SailBlock;
            boolean rope=state.getBlock() instanceof SailToggleBlock;
            if(!nativeSail && !rope && !hasSail(level,pos,state))continue;
            if(nativeSail || rope) {
                BlockState next=state.setValue(SailBlock.SET,open);
                if(nativeSail)next=next.setValue(SailBlock.INVISIBLE,!open && !supported(level,pos));
                if(next!=state){level.setBlock(pos,next,2);changed.add(pos);}
            }else if(CopycatMaterials.setSails(level,pos,state,open))changed.add(pos);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {
                int distance=Math.abs(x)+Math.abs(y)+Math.abs(z);
                if(distance==1||distance==2)queue.add(pos.offset(x,y,z));
            }
        }
        for(BlockPos pos:changed)SailsShipControl.markDirty(level,pos);
    }
    private static boolean supported(Level level,BlockPos pos) {
        for(int y:new int[]{-1,1})for(int[] offset:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
            BlockPos other=pos.offset(offset[0],y,offset[1]);
            if(level instanceof ServerLevel server && server.getChunkSource().getChunkNow(other.getX()>>4,other.getZ()>>4)==null)continue;
            BlockState state=level.getBlockState(other);
            if(!state.isAir() && !hasSail(level,other,state) && !(state.getBlock() instanceof SailToggleBlock) && state.isCollisionShapeFullBlock(level,other))return true;
        }
        return false;
    }
}
