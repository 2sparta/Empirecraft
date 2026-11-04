package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.protection.ShipProtection;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Normal buckets for full measures; a separate partial bucket cannot masquerade as a source in other mods. */
@Mod.EventBusSubscriber(modid="vs_sails")
public final class FiniteBuckets {
    private FiniteBuckets(){}
    public static int contents(ItemStack stack){
        if(stack.is(Items.WATER_BUCKET))return 8;
        return FloodItems.BILGE_BUCKET!=null && stack.is(FloodItems.BILGE_BUCKET) && stack.hasTag()
                ?Math.max(0,Math.min(7,stack.getTag().getInt("WaterUnits"))):0;
    }
    public static ItemStack bucket(int units){
        if(units<=0)return new ItemStack(Items.BUCKET);
        if(units>=8)return new ItemStack(Items.WATER_BUCKET);
        ItemStack stack=new ItemStack(FloodItems.BILGE_BUCKET);stack.getOrCreateTag().putInt("WaterUnits",units);return stack;
    }
    private static boolean supported(ItemStack stack){return stack.is(Items.BUCKET)||stack.is(Items.WATER_BUCKET)||FloodItems.BILGE_BUCKET!=null&&stack.is(FloodItems.BILGE_BUCKET);}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void item(PlayerInteractEvent.RightClickItem event){
        var player=event.getEntity();if(!(player.level() instanceof ServerLevel level) || !supported(event.getItemStack()))return;
        double reach=Math.max(1,Math.min(64,player.getAttributeValue(ForgeMod.BLOCK_REACH.get())));
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(reach));
        var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return;
        if(use(level,player,event.getHand(),hit)){event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);}
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void block(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level) || !supported(event.getItemStack()))return;
        if(use(level,event.getEntity(),event.getHand(),event.getHitVec())){event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);}
    }
    public static boolean use(ServerLevel level,Player player,InteractionHand hand,BlockHitResult hit){
        ItemStack held=player.getItemInHand(hand);if(!supported(held))return false;
        boolean partial=FloodItems.BILGE_BUCKET!=null && held.is(FloodItems.BILGE_BUCKET);
        BlockPos hitPos=hit.getBlockPos();boolean onShip=ShipFlooding.shipPosition(level,hitPos);
        if(!onShip && !partial)return false;
        if(player.getCooldowns().isOnCooldown(held.getItem()))return true;
        int current=contents(held),available=onShip?ShipFlooding.volume(level,hitPos):0;
        boolean collect=current<8 && available>0 && !(partial&&player.isShiftKeyDown());
        BlockPos target=collect?hitPos:DoorWater.canContain(level.getBlockState(hitPos))?hitPos:hitPos.relative(hit.getDirection());
        if(player.isSpectator() || !player.mayBuild() || !level.mayInteract(player,worldPosition(level,target))
                || !ShipProtection.allows(player,level,target)){ShipProtection.denied(player);return true;}
        var permission=new FillBucketEvent(player,held,level,hit);
        if(MinecraftForge.EVENT_BUS.post(permission))return true;
        ItemStack result;
        if(collect){
            int take=ShipFlooding.bail(level,hitPos,8-current);if(take==0)return false;result=bucket(current+take);
            level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,1,1);
        }else if(current>0){
            boolean placed=ShipFlooding.shipPosition(level,target)?ShipFlooding.pour(level,target,current):partial&&pourOutside(level,target,current);
            if(!placed)return partial;result=new ItemStack(Items.BUCKET);
            level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BUCKET_EMPTY,SoundSource.PLAYERS,1,1);
        }else return false;
        player.setItemInHand(hand,ItemUtils.createFilledResult(held,player,result));player.getCooldowns().addCooldown(result.getItem(),3);
        return true;
    }
    public static ItemStack pickupAutomated(ServerLevel level,BlockPos pos){
        int amount=ShipFlooding.bail(level,pos,8);return amount==0?ItemStack.EMPTY:bucket(amount);
    }
    private static BlockPos worldPosition(ServerLevel level,BlockPos pos){
        var ship=VSGameUtilsKt.getShipObjectManagingPos(level,pos);if(ship==null)return pos;
        var p=ship.getShipToWorld().transformPosition(new Vector3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));return BlockPos.containing(p.x,p.y,p.z);
    }
    private static boolean pourOutside(ServerLevel level,BlockPos pos,int units){
        if(!level.hasChunkAt(pos))return false;var state=level.getBlockState(pos);
        if(state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource())return true; // Poured back into the exterior reservoir.
        if(!state.isAir())return false;
        level.setBlock(pos,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,units==8?8:8-units),Block.UPDATE_ALL);return true;
    }
    public static void dispensers(){
        for(Item item:List.of(Items.BUCKET,Items.WATER_BUCKET,FloodItems.BILGE_BUCKET)){
            DispenseItemBehavior original=DispenserBlock.DISPENSER_REGISTRY.getOrDefault(item,new DefaultDispenseItemBehavior());
            DispenserBlock.registerBehavior(item,(source,stack)->{
                var level=source.getLevel();var direction=source.getBlockState().getValue(DispenserBlock.FACING);var target=source.getPos().relative(direction);
                boolean ship=ShipFlooding.shipPosition(level,target),partial=stack.is(FloodItems.BILGE_BUCKET);
                if(!ship&&!partial)return original.dispense(source,stack);
                ItemStack replacement;
                if(stack.is(Items.BUCKET)){
                    replacement=pickupAutomated(level,target);if(replacement.isEmpty())return stack;
                }else{
                    int units=contents(stack);if(units==0)return stack;
                    if(!(ship?ShipFlooding.pour(level,target,units):pourOutside(level,target,units)))return stack;
                    replacement=new ItemStack(Items.BUCKET);
                }
                level.levelEvent(1000,source.getPos(),0);
                if(stack.getCount()==1)return replacement;
                stack.shrink(1);DispenserBlockEntity inventory=source.getEntity();
                if(inventory.addItem(replacement)<0){
                    Vector3d point=new Vector3d(target.getX()+.5,target.getY()+.5,target.getZ()+.5);
                    var body=VSGameUtilsKt.getShipObjectManagingPos(level,target);if(body!=null)body.getShipToWorld().transformPosition(point);
                    level.addFreshEntity(new ItemEntity(level,point.x,point.y,point.z,replacement));
                }
                return stack;
            });
        }
    }
}
