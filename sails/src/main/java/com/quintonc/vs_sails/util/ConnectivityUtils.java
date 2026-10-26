package com.quintonc.vs_sails.util;

import com.quintonc.vs_sails.config.ConfigUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.*;

/** Never grows assembly across diagonal contact, unloaded chunks or existing ships. */
public final class ConnectivityUtils {
    private static final int MAX=Integer.parseInt(ConfigUtils.config.getOrDefault("max-assemble-blocks","100000"));
    private static final Set<Block> BLACKLIST=Set.of(Blocks.AIR,Blocks.CAVE_AIR,Blocks.VOID_AIR,Blocks.WATER,Blocks.KELP,
            Blocks.KELP_PLANT,Blocks.SEAGRASS,Blocks.TALL_SEAGRASS,Blocks.GRASS,Blocks.TALL_GRASS,Blocks.DEAD_BUSH);
    private ConnectivityUtils() {}
    public static Set<BlockPos> tryFillByConnectivity(BlockGetter level,BlockPos start,Player player) {
        if (!valid(level.getBlockState(start))) { message(player,"vs_sails.assembly.invalid");return null; }
        Set<BlockPos> result=new HashSet<>(),visited=new HashSet<>();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start.immutable());visited.add(start.immutable());
        while(!queue.isEmpty()) {
            BlockPos pos=queue.removeFirst();result.add(pos);
            if(result.size()>MAX) { message(player,"vs_sails.assembly.too_large");return null; }
            for(Direction direction:Direction.values()) {
                BlockPos next=pos.relative(direction);
                if(!visited.add(next))continue;
                if(level instanceof Level world) {
                    if(!world.hasChunkAt(next)) { message(player,"vs_sails.assembly.unloaded");return null; }
                    if(VSGameUtilsKt.getShipObjectManagingPos(world,next)!=null)continue;
                }
                BlockState state=level.getBlockState(next);
                if(state.is(Blocks.BEDROCK)) { message(player,"vs_sails.assembly.bedrock");return null; }
                if(valid(state))queue.addLast(next);
            }
        }
        return result;
    }
    private static boolean valid(BlockState state) {
        return !state.isAir() && !BLACKLIST.contains(state.getBlock()) && !VSGameUtilsKt.inAssemblyBlacklist(state);
    }
    private static void message(Player player,String key) { if(player!=null)player.displayClientMessage(Component.translatable(key),true); }
}
