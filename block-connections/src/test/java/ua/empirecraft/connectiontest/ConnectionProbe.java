package ua.empirecraft.connectiontest;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
@Mod("empirecraft_connection_probe")
public class ConnectionProbe {
 final List<String> failures=new ArrayList<>();int checks;
 void check(boolean ok,String message){checks++;if(!ok)failures.add(message);}
 Block block(String id){return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));}
 boolean retired(String id)throws Exception{return Boolean.TRUE.equals(Class.forName("ua.empirecraft.noend.Policy").getMethod("blocked",String.class).invoke(null,id));}
 void place(ServerLevel level,BlockPos pos,Block block){
  level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
  var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
  var player=FakePlayerFactory.getMinecraft(level);var stack=new ItemStack(block);
  player.setItemInHand(InteractionHand.MAIN_HAND,stack);
  var state=block.getStateForPlacement(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,hit));
  if(state==null)throw new AssertionError("No placement state: "+block);
  level.setBlockAndUpdate(pos,state);
 }
 void pair(ServerLevel level,String first,String second,Direction direction,int row){
  var p=new BlockPos(row*5,90,0);var q=p.relative(direction);var a=block(first);var b=block(second);
  place(level,p,a);place(level,q,b);
  var sa=level.getBlockState(p);var sb=level.getBlockState(q);
  if(a instanceof FenceBlock){
   var props=Map.of(Direction.NORTH,CrossCollisionBlock.NORTH,Direction.SOUTH,CrossCollisionBlock.SOUTH,Direction.EAST,CrossCollisionBlock.EAST,Direction.WEST,CrossCollisionBlock.WEST);
   check(sa.getValue(props.get(direction)),first+" connects to "+second+" "+direction);
   check(sb.getValue(props.get(direction.getOpposite())),second+" connects back to "+first+" "+direction);
  }else{
   var props=Map.of(Direction.NORTH,WallBlock.NORTH_WALL,Direction.SOUTH,WallBlock.SOUTH_WALL,Direction.EAST,WallBlock.EAST_WALL,Direction.WEST,WallBlock.WEST_WALL);
   check(sa.getValue(props.get(direction))!=WallSide.NONE,first+" connects to "+second+" "+direction);
   check(sb.getValue(props.get(direction.getOpposite()))!=WallSide.NONE,second+" connects back to "+first+" "+direction);
  }
  level.setBlockAndUpdate(q,Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
 }
 void tagLoaderRegression(){
  var loader=new TagLoader<Block>(id->ForgeRegistries.BLOCKS.containsKey(id)?Optional.of(ForgeRegistries.BLOCKS.getValue(id)):Optional.empty(),"tags/blocks");
  var missing=new ResourceLocation("empirecraft_connection_probe","removed_mod_wall");
  var group=new ResourceLocation("empirecraft_connection_probe","regression");
  var vanilla=new TagLoader.EntryWithSource(TagEntry.element(new ResourceLocation("minecraft:cobblestone_wall")),"regression");
  check(!loader.build(Map.of(group,List.of(vanilla,new TagLoader.EntryWithSource(TagEntry.element(missing),"regression")))).containsKey(group),"negative control: one missing required mod block rejects the whole tag");
  var repaired=loader.build(Map.of(group,List.of(vanilla,new TagLoader.EntryWithSource(TagEntry.optionalElement(missing),"regression"))));
  check(repaired.containsKey(group)&&repaired.get(group).contains(Blocks.COBBLESTONE_WALL),"optional missing mod block preserves the vanilla tag");
 }
 void gatesAndExistingBlocks(ServerLevel level){
  var p=new BlockPos(0,90,30);var q=p.east();
  level.setBlockAndUpdate(q,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,Direction.NORTH));
  place(level,p,Blocks.OAK_FENCE);
  check(level.getBlockState(p).getValue(CrossCollisionBlock.EAST),"fence connects to a correctly oriented gate");
  level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());place(level,p,Blocks.COBBLESTONE_WALL);
  check(level.getBlockState(p).getValue(WallBlock.EAST_WALL)!=WallSide.NONE,"wall connects to gate");
  check(level.getBlockState(q).getValue(FenceGateBlock.IN_WALL),"gate detects adjacent wall");
  level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
  check(!level.getBlockState(q).getValue(FenceGateBlock.IN_WALL),"gate leaves wall mode after removal");
  // Preserve vanilla wood/non-wood separation.
  p=new BlockPos(0,90,35);q=p.east();place(level,p,Blocks.OAK_FENCE);place(level,q,Blocks.NETHER_BRICK_FENCE);
  check(!level.getBlockState(p).getValue(CrossCollisionBlock.EAST)&&!level.getBlockState(q).getValue(CrossCollisionBlock.WEST),"wooden and nether-brick fences retain vanilla separation");
  // Old saved connection states are refreshed by a real neighboring block update.
  p=new BlockPos(0,90,40);q=p.east();
  level.setBlock(p,Blocks.OAK_FENCE.defaultBlockState(),2);level.setBlock(q,Blocks.OAK_FENCE.defaultBlockState(),2);
  place(level,p.west(),Blocks.STONE);level.setBlockAndUpdate(p.west(),Blocks.AIR.defaultBlockState());
  check(level.getBlockState(p).getValue(CrossCollisionBlock.EAST)&&level.getBlockState(q).getValue(CrossCollisionBlock.WEST),"neighbor update repairs old disconnected fence states");
 }
 public ConnectionProbe(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,(ServerStartedEvent e)->{
  try{
   var level=e.getServer().overworld();
   tagLoaderRegression();
   for(String id:List.of("minecraft:oak_fence","minecraft:spruce_fence","minecraft:cobblestone_wall","minecraft:stone_brick_wall")){
    var b=block(id);check(b.defaultBlockState().is(b instanceof FenceBlock?BlockTags.FENCES:BlockTags.WALLS),"Tag membership "+id);
   }
   // Report missing hard references using the live registry, not item model guesses.
   var missing=new LinkedHashMap<String,List<String>>();
   for(String name:List.of("fences","walls","fence_gates","wall_post_override")){
    var p=Path.of("kubejs/data/minecraft/tags/blocks/"+name+".json");var absent=new ArrayList<String>();
    if(Files.exists(p))for(var value:JsonParser.parseString(Files.readString(p)).getAsJsonObject().getAsJsonArray("values")){
     String id=value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("id").getAsString();
     boolean required=value.isJsonPrimitive()||!value.getAsJsonObject().has("required")||value.getAsJsonObject().get("required").getAsBoolean();
     if(required&&!id.startsWith("#")&&!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(id)))absent.add(id);
    }
    missing.put(name,absent);
   }
   var fences=new ArrayList<String>();var walls=new ArrayList<String>();var untagged=new ArrayList<String>();var disabled=new ArrayList<String>();
   for(var b:ForgeRegistries.BLOCKS){if(b instanceof FenceBlock||b instanceof WallBlock){String id=ForgeRegistries.BLOCKS.getKey(b).toString();if(retired(id)){disabled.add(id);continue;}if(b instanceof FenceBlock)fences.add(id);else walls.add(id);boolean tagged=b.defaultBlockState().is(b instanceof FenceBlock?BlockTags.FENCES:BlockTags.WALLS);check(tagged,"registered connecting block belongs to its tag: "+id);if(!tagged)untagged.add(id);}}
   int row=0;for(Direction direction:Direction.Plane.HORIZONTAL){
    pair(level,"minecraft:oak_fence","minecraft:oak_fence",direction,row++);
    pair(level,"minecraft:oak_fence","minecraft:spruce_fence",direction,row++);
    pair(level,"minecraft:cobblestone_wall","minecraft:cobblestone_wall",direction,row++);
    pair(level,"minecraft:cobblestone_wall","minecraft:stone_brick_wall",direction,row++);
   }
   // Exercise every fence and each wall provider, including generated Chipped walls.
   int sample=0;
   for(String id:fences)pair(level,id,id,Direction.EAST,++sample);
   var providers=new HashSet<String>();
   for(String id:walls)if(providers.add(id.split(":")[0]))pair(level,id,id,Direction.SOUTH,++sample);
   gatesAndExistingBlocks(level);
   var report=new LinkedHashMap<String,Object>();report.put("registered_fences",fences);report.put("registered_walls",walls);report.put("intentionally_retired",disabled);report.put("untagged_connecting_blocks",untagged);report.put("checks",checks);report.put("failures",failures);report.put("missing_required_blocks",missing);
   Files.writeString(Path.of("connections.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
   System.out.println("CONNECTION_PROBE_COMPLETE checks="+checks+" failed="+failures.size());
  }catch(Throwable error){error.printStackTrace();System.out.println("CONNECTION_PROBE_ERROR "+error);}
  e.getServer().halt(false);
 });}
}
