package com.quintonc.vs_sails.util;

import com.quintonc.vs_sails.blocks.BaseHelmBlock;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import java.util.*;

/** Explicit repair of old merged bodies; no automatic relocation on world load. */
@Mod.EventBusSubscriber(modid="vs_sails")
public final class ShipIsolationCommands {
    private record Snapshot(LoadedServerShip ship,BlockPos selected,List<Set<HullConnectivity.Cell>> parts,
                            Set<HullConnectivity.Cell> helms) {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vs_sails")
                .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource(),false)))
                .then(Commands.literal("separate").requires(s -> s.hasPermission(2)).executes(ctx -> inspect(ctx.getSource(),true))));
    }
    private static Snapshot scan(CommandSourceStack source) throws Exception {
        var player=source.getPlayerOrException();ServerLevel level=player.serverLevel();
        if(source.getLevel()!=level) { fail(source,"vs_sails.isolation.foreign");return null; }
        if (!(player.pick(8,0,false) instanceof BlockHitResult hit) || hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK) { fail(source,"vs_sails.isolation.look_at_ship");return null; }
        BlockPos selected=hit.getBlockPos();LoadedServerShip ship=VSGameUtilsKt.getShipObjectManagingPos(level,selected);
        if(ship==null) { fail(source,"vs_sails.isolation.look_at_ship");return null; }
        var b=ship.getShipAABB();if(b==null) { fail(source,"vs_sails.isolation.look_at_ship");return null; }
        long volume=(long)(b.maxX()-b.minX())*(b.maxY()-b.minY())*(b.maxZ()-b.minZ());
        if(volume<=0 || volume>1000000) { fail(source,"vs_sails.isolation.too_large");return null; }
        Set<HullConnectivity.Cell> cells=new HashSet<>(),helms=new HashSet<>();
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int cx=b.minX()>>4;cx<=((b.maxX()-1)>>4);cx++)for(int cz=b.minZ()>>4;cz<=((b.maxZ()-1)>>4);cz++) {
            if(level.getChunkSource().getChunkNow(cx,cz)==null) { fail(source,"vs_sails.isolation.unloaded");return null; }
            var owner=VSGameUtilsKt.getShipObjectManagingPos(level,cx,cz);
            if(owner==null || owner.getId()!=ship.getId()) { fail(source,"vs_sails.isolation.foreign");return null; }
        }
        for(int x=b.minX();x<b.maxX();x++)for(int y=b.minY();y<b.maxY();y++)for(int z=b.minZ();z<b.maxZ();z++) {
            pos.set(x,y,z);var state=level.getBlockState(pos);
            if(state.isAir() || state.getBlock() instanceof LiquidBlock)continue;
            var cell=new HullConnectivity.Cell(x,y,z);cells.add(cell);
            if(state.getBlock() instanceof BaseHelmBlock)helms.add(cell);
            if(cells.size()>100000) { fail(source,"vs_sails.isolation.too_large");return null; }
        }
        return new Snapshot(ship,selected,HullConnectivity.components(cells),helms);
    }
    private static int inspect(CommandSourceStack source,boolean separate) {
        try {
            Snapshot s=scan(source);if(s==null)return 0;
            long hulls=s.parts.stream().filter(p -> p.stream().anyMatch(s.helms::contains)).count();
            var control=SailsShipControl.getOrCreate(s.ship,source.getLevel());
            source.sendSuccess(() -> Component.translatable("vs_sails.isolation.stats",s.ship.getId(),
                    Math.round(s.ship.getInertiaData().getMass()),s.parts.size(),hulls,control.numBuoys),false);
            if(!separate)return 1;
            Set<HullConnectivity.Cell> selected=s.parts.stream().filter(p -> p.contains(new HullConnectivity.Cell(
                    s.selected.getX(),s.selected.getY(),s.selected.getZ()))).findFirst().orElse(Set.of());
            if(hulls<2 || selected.stream().noneMatch(s.helms::contains)) { fail(source,"vs_sails.isolation.not_separate");return 0; }
            // Refuse ambiguous loose decorations rather than leave another ship's sails behind.
            if(s.parts.stream().anyMatch(p -> p.stream().noneMatch(s.helms::contains))) { fail(source,"vs_sails.isolation.loose_parts");return 0; }
            Set<BlockPos> blocks=new HashSet<>();for(var p:selected)blocks.add(new BlockPos(p.x(),p.y(),p.z()));
            if(!AssemblySafety.allows(source.getLevel(),source.getPlayerOrException(),blocks)) { fail(source,"vs_sails.assembly.denied");return 0; }
            // Native VS relocation preserves block entities, inventories, pose, scale and velocity.
            // Release the old merged anchor before changing the physical body's membership.
            control.releaseAnchor();
            var result=ShipAssembler.assembleToShip(source.getLevel(),blocks,1.0);
            if(result instanceof LoadedServerShip loaded)SailsShipControl.getOrCreate(loaded,source.getLevel());
            SailsShipControl.markDirty(source.getLevel(),s.parts.stream().filter(p -> p!=selected).findFirst()
                    .map(p -> p.iterator().next()).map(p -> new BlockPos(p.x(),p.y(),p.z())).orElse(s.selected));
            source.sendSuccess(() -> Component.translatable("vs_sails.isolation.separated",s.ship.getId(),result.getId()),true);
            return 1;
        } catch(Exception | AssertionError failed) {
            SailsShipControl.LOGGER.error("Ship inspection/separation failed",failed);
            fail(source,"vs_sails.isolation.failed");return 0;
        }
    }
    private static void fail(CommandSourceStack source,String key) { source.sendFailure(Component.translatable(key)); }
}
