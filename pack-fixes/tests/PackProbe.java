package ua.empirecraft.packprobe;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_pack_probe")
public final class PackProbe {
  public PackProbe() {
    MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::start);
    MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
      if (event.getEntity() instanceof ItemEntity item && "overgeared:steel_arrow".equals(Objects.toString(ForgeRegistries.ITEMS.getKey(item.getItem().getItem())))) {
        System.out.println("PACK_PROBE_STEEL_ARROW_PRODUCER");
        new Exception("Steel arrow producer").printStackTrace();
      }
    });
  }
  private void start(ServerStartedEvent event) {
    try {
      var level=event.getServer().overworld();
      var result=new LinkedHashMap<String,Object>();
      result.put("blocks", ForgeRegistries.BLOCKS.getKeys().stream().map(Object::toString).sorted().toList());
      result.put("items", ForgeRegistries.ITEMS.getKeys().stream().map(Object::toString).sorted().toList());
      result.put("stone_pickaxe_tag", Blocks.STONE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE));
      result.put("andesite_pickaxe_tag", Blocks.ANDESITE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE));
      result.put("oak_axe_tag", Blocks.OAK_LOG.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE));
      var player=new net.minecraft.server.level.ServerPlayer(event.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PackProbe"));
      var channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
        @Override public void write(io.netty.channel.ChannelHandlerContext c,Object m,io.netty.channel.ChannelPromise p){io.netty.util.ReferenceCountUtil.release(m);p.trySuccess();}
      });
      var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.channel=channel;
      channel.attr(net.minecraft.network.Connection.ATTRIBUTE_PROTOCOL).set(net.minecraft.network.ConnectionProtocol.PLAY);
      player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(event.getServer(),connection,player);
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);level.addNewPlayer(player);
      var pos=new BlockPos(0,90,0); player.moveTo(.5,90,2.5);
      var actions=new ArrayList<Map<String,Object>>();
      for(boolean sneak:List.of(false,true)) for(var block:List.of(Blocks.CHEST, Blocks.STONE, Blocks.GRASS_BLOCK)) {
        level.setBlockAndUpdate(pos,block.defaultBlockState());
        var bone=new ItemStack(Items.BONE_MEAL,16);
        player.setItemInHand(InteractionHand.MAIN_HAND,bone);
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setShiftKeyDown(sneak);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var blockEvent=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);
        MinecraftForge.EVENT_BUS.post(blockEvent);
        var itemEvent=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,InteractionHand.MAIN_HAND);
        MinecraftForge.EVENT_BUS.post(itemEvent);
        player.gameMode.useItemOn(player,level,bone,InteractionHand.MAIN_HAND,hit);
        player.connection.handleUseItem(new net.minecraft.network.protocol.game.ServerboundUseItemPacket(InteractionHand.MAIN_HAND,0));
        actions.add(Map.of("block",ForgeRegistries.BLOCKS.getKey(block).toString(),"sneaking",sneak,"remaining",bone.getCount(),"drops",level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).stream().map(i->Objects.toString(ForgeRegistries.ITEMS.getKey(i.getItem().getItem()))).toList()));
      }
      result.put("actions",actions);
      if(net.minecraftforge.fml.ModList.get().isLoaded("empirecraft_pack_fixes"))result.put("assertions",PackAssertions.run(level,player));
      Files.writeString(Path.of("pack-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
      System.out.println("PACK_PROBE_COMPLETE "+actions);
    } catch(Throwable failure) {failure.printStackTrace();System.out.println("PACK_PROBE_FAIL "+failure);}
    event.getServer().halt(false);
  }
}
