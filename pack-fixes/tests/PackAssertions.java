package ua.empirecraft.packprobe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.packfixes.BonemealUse;

final class PackAssertions {
    private int checks,emitted;private boolean emit,throwCallback;
    private ServerLevel level;private ServerPlayer player;private final BlockPos pos=new BlockPos(12,90,0);
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private void clean(){for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(10)))item.discard();}
    private boolean spawn(Item item){return level.addFreshEntity(new ItemEntity(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(item)));}
    private Item steelArrow(){return ForgeRegistries.ITEMS.getValue(new ResourceLocation("overgeared:steel_arrow"));}
    private void callback(PlayerInteractEvent.RightClickBlock event){
        if(!emit||event.getEntity()!=player)return;emitted++;
        if(throwCallback)throw new IllegalStateException("Intentional pack probe callback exception");
        spawn(steelArrow());spawn(Items.WHEAT_SEEDS);
    }
    private void boneMeal()throws Exception {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW,this::callback);
        for(var hand:InteractionHand.values())for(var block:List.of(Blocks.STONE,Blocks.CHEST)){
            clean();level.setBlockAndUpdate(pos,block.defaultBlockState());player.moveTo(pos.getX()+.5,90,2.5);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.setItemInHand(hand,new ItemStack(Items.BONE_MEAL,16));emit=true;int before=emitted;
            player.gameMode.useItemOn(player,level,player.getItemInHand(hand),hand,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));emit=false;
            check(emitted>before,"Actual interaction callback executed: "+hand+" "+block);
            check(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(5)).stream().noneMatch(i->i.getItem().is(steelArrow())),"Bone meal cannot create steel-arrow items: "+hand+" "+block);
            check(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(5)).stream().anyMatch(i->i.getItem().is(Items.WHEAT_SEEDS)),"Other interaction drops remain available");
        }
        clean();check(spawn(steelArrow()),"Normal dropped steel arrows survive outside bone-meal use");clean();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE_MEAL));emit=true;throwCallback=true;
        try{player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));throw new AssertionError("Missing test exception");}
        catch(IllegalStateException expected){check(expected.getMessage().contains("Intentional"),"Expected callback failed");}
        finally{emit=false;throwCallback=false;}
        check(spawn(steelArrow()),"Exception cleanup does not suppress later drops");clean();
        try(var scope=BonemealUse.enter(player,new ItemStack(Items.BONE_MEAL),Vec3.atCenterOf(pos))){
            check(!spawn(steelArrow()),"Scoped arrow suppression");check(spawn(Items.WHEAT_SEEDS),"Scoped seed preservation");
        }
        clean();check(spawn(steelArrow()),"Scope closes without leaking into the next action");clean();
    }
    private void tools()throws Exception {
        for(String name:List.of("pickaxe","axe")){
            var path=Path.of("kubejs/data/minecraft/tags/blocks/mineable/"+name+".json");
            var tag=TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new ResourceLocation("minecraft:mineable/"+name));
            for(var raw:JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("values")){
                String id=raw.isJsonPrimitive()?raw.getAsString():raw.getAsJsonObject().get("id").getAsString();
                if(id.startsWith("#")||!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(id)))continue;
                check(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)).defaultBlockState().is(tag),"Tool tag membership: "+name+" "+id);
            }
        }
        for(Item item:ForgeRegistries.ITEMS){
            var stack=new ItemStack(item);
            if(item instanceof PickaxeItem)for(Block block:List.of(Blocks.STONE,Blocks.ANDESITE,Blocks.GRANITE,Blocks.FURNACE))
                check(stack.getDestroySpeed(block.defaultBlockState())>1,"Pickaxe speed "+ForgeRegistries.ITEMS.getKey(item)+" / "+block);
            if(item instanceof AxeItem)check(stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())>1,"Axe speed "+ForgeRegistries.ITEMS.getKey(item));
            if(item instanceof ShovelItem)check(stack.getDestroySpeed(Blocks.DIRT.defaultBlockState())>1,"Shovel speed "+ForgeRegistries.ITEMS.getKey(item));
            if(item instanceof HoeItem)check(stack.getDestroySpeed(Blocks.HAY_BLOCK.defaultBlockState())>1,"Hoe speed "+ForgeRegistries.ITEMS.getKey(item));
        }
        var steel=ForgeRegistries.ITEMS.getValue(new ResourceLocation("overgeared:steel_pickaxe"));check(steel instanceof PickaxeItem,"Live steel pickaxe exists");
        for(Block block:List.of(Blocks.STONE,Blocks.ANDESITE)){
            clean();level.setBlockAndUpdate(pos,block.defaultBlockState());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(steel));
            check(player.gameMode.destroyBlock(pos),"Steel pickaxe destroys "+block);
            check(level.getBlockState(pos).isAir(),"Mined block removed");
            check(!level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).isEmpty(),"Mined block produced its normal loot");
        }
        clean();
        check(new ItemStack(Items.SHEARS).getDestroySpeed(Blocks.OAK_LEAVES.defaultBlockState())>1,"Shears retain leaf efficiency");
    }
    private void flagGeometry(){
        var blocks=new HashSet<BlockPos>();
        for(int x=64;x<67;x++)for(int z=32;z<35;z++){var p=new BlockPos(x,90,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
        var body=org.valkyrienskies.mod.common.assembly.ShipAssembler.assembleToShip(level,blocks,1);body.setStatic(true);
        var bounds=body.getShipAABB();var pole=new BlockPos(bounds.minX()+1,bounds.maxY()+8,bounds.minZ()+1);
        for(int x=(pole.getX()-8)>>4;x<=((pole.getX()+8)>>4);x++)for(int z=(pole.getZ()-8)>>4;z<=((pole.getZ()+8)>>4);z++)level.setChunkForced(x,z,true);
        level.setBlockAndUpdate(pole,ua.empirecraft.engagement.NavalFlags.BLOCK.get().defaultBlockState());
        var flag=(ua.empirecraft.engagement.NavalFlags.FlagEntity)level.getBlockEntity(pole);flag.width=2;
        check(ua.empirecraft.packfixes.FlagClearance.visible(level,body,flag),"Unobstructed rotating cloth is visible on a real VS ship");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)level.setBlockAndUpdate(pole.offset(x,0,z),Blocks.GLASS.defaultBlockState());
        check(!ua.empirecraft.packfixes.FlagClearance.visible(level,body,flag),"Cloth obscured after turning loses visibility, including glass");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)level.setBlockAndUpdate(pole.offset(x,0,z),Blocks.AIR.defaultBlockState());
        check(ua.empirecraft.packfixes.FlagClearance.visible(level,body,flag),"Clearing rotated cloth restores visibility");
    }
    static int run(ServerLevel level,ServerPlayer player)throws Exception {
        var test=new PackAssertions();test.level=level;test.player=player;test.boneMeal();test.tools();test.flagGeometry();
        System.out.println("PACK_FIXES_NATIVE_PASS assertions="+test.checks+"; bone meal callbacks, both hands/chests, exception cleanup, normal arrow/seed drops, tool tags, tool speeds and real mining loot");
        return test.checks;
    }
}
