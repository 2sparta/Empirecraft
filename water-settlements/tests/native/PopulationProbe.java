package ua.townyrecruits;
import java.util.*;
import java.lang.reflect.*;
import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.Bukkit;
@Mod("empirecraft_population_probe") public final class PopulationProbe {
 MinecraftServer server;int ticks,checks;
 public PopulationProbe(){MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e)->server=e.getServer());MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean good,String text){checks++;if(!good)throw new AssertionError(text);}
 void tests()throws Throwable{
  Bridge b=Bridge.INSTANCE;check(b!=null&&b.ready,"Towny recruitment bridge ready");
  var plugin=Bukkit.getPluginManager().getPlugin("EmpirecraftFrontier");check(plugin!=null&&plugin.isEnabled(),"Frontier server plugin enabled");
  Method chunk=plugin.getClass().getDeclaredMethod("prepareChunk",org.bukkit.World.class,int.class,int.class);chunk.setAccessible(true);check(chunk.invoke(null,Bukkit.getWorlds().get(0),0,0) instanceof java.util.concurrent.CompletableFuture,"Nonblocking native chunk future for safe spawn");
  Method make=plugin.getClass().getDeclaredMethod("modItem",String.class);make.setAccessible(true);
  for(String id:List.of("patchouli:guide_book","starcatcher:starcatcher_guide","siegeworks:siege_manual"))check(make.invoke(null,id) instanceof org.bukkit.inventory.ItemStack,"Actual Forge→Bukkit guide: "+id);
  var nativePlayer=FakePlayerFactory.get(server.m_129783_(),new GameProfile(UUID.fromString("0a31994a-5f0a-4615-b3b2-1520edc21b48"),"FrontierProbe"));
  org.bukkit.entity.Player player=(org.bukkit.entity.Player)nativePlayer.getClass().getMethod("getBukkitEntity").invoke(nativePlayer);
  player.getInventory().clear();org.bukkit.command.PluginCommand guides=((org.bukkit.plugin.java.JavaPlugin)plugin).getCommand("guides");
  guides.execute(player,"guides",new String[0]);
  var items=Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).toList();check(items.size()==6,"All six books delivered, actual plugin command");
  long patchouli=nativePlayer.m_150109_().f_35974_.stream().filter(s->s.m_41720_()==ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli:guide_book"))&&s.m_41782_()&&s.m_41783_().m_128461_("patchouli:book").contains(":" )).count();check(patchouli==4,"Four Patchouli book identities preserved");
  guides.execute(player,"guides",new String[0]);check(Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).count()==6,"Cooldown prevents repeat books");
  var key=new org.bukkit.NamespacedKey(plugin,"guides_at");check(player.getPersistentDataContainer().getOrDefault(key,org.bukkit.persistence.PersistentDataType.LONG,0L)>0,"Guide cooldown persisted with player");
  for(String id:List.of("empirecraft_guidebook:handbook","tfc:field_guide","chineseweapons:forging_notes","dragnlivestock:livestock_overhaul_guide")){
   Class<?> reg=Class.forName("vazkii.patchouli.common.book.BookRegistry");Object instance=reg.getField("INSTANCE").get(null);check(((Map<?,?>)reg.getField("books").get(instance)).containsKey(new ResourceLocation(id)),"Patchouli book registered: "+id);
  }
  UUID town=UUID.fromString("2e286254-de15-4b7b-b1e1-8c07356026ad");var result=VillageApi.approve("native_probe",town,4,6);check(((Number)result.get("capacity")).intValue()>=8,"Trusted plugin→mod population API");
  var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits:recruit"));check(type!=null,"Native recruit registered");
  var r=(AbstractRecruitEntity)type.m_20615_(server.m_129783_());check(r!=null,"Actual recruit constructed");r.getInventory().m_6836_(0,new ItemStack(Items.f_42417_,3));
  Militia.install(r,System.currentTimeMillis()+7200000L);check(Militia.member(r)&&r.getMorale()<=30,"120-minute demoralized levy");
  boolean empty=true;for(int i=0;i<r.getInventorySize();i++)empty&=r.getInventory().m_8020_(i).m_41619_();check(empty,"No free equipment in native levy inventory");
  r.getPersistentData().m_128356_("TRMilitiaUntil",System.currentTimeMillis()-1);b.loaded.add(r);Militia.tick(b.recruitment);check(r.m_213877_(),"Expired levy discarded without death drops");check(b.recruitment.state.dismissed.contains(r.m_20148_()),"Expiry retirement persisted");b.loaded.remove(r);
  System.out.println("POPULATION_NATIVE_PASS checks="+checks+" nativeMilitia=true guideCommand=true guideNBT=true cooldown=true pluginBridge=true");
 }
 void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null)return;ticks++;if(ticks==80)try{tests();server.m_7570_(false);}catch(Throwable t){System.out.println("POPULATION_NATIVE_FAIL "+t);t.printStackTrace();server.m_7570_(false);}}
}
