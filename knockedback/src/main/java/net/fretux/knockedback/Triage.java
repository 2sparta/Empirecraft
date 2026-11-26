package net.fretux.knockedback;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
@Mod.EventBusSubscriber(modid="knockedback")
public final class Triage {
 public static final String RECOVERY="EmpirecraftRecoveryUntil";
 private static SimpleChannel channel;
 public record State(UUID id, boolean down, int recovery){
  static void encode(State p,FriendlyByteBuf b){b.writeUUID(p.id);b.writeBoolean(p.down);b.writeVarInt(p.recovery);}
  static State decode(FriendlyByteBuf b){return new State(b.readUUID(),b.readBoolean(),Math.max(0,Math.min(2400,b.readVarInt())));}
  static void handle(State p,Supplier<NetworkEvent.Context> context){var c=context.get();c.enqueueWork(()->ClientTriage.accept(p));c.setPacketHandled(true);}
 }
 public static void register(SimpleChannel c){channel=c;c.registerMessage(6,State.class,State::encode,State::decode,State::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
 public static int remaining(ServerPlayer p){return (int)Math.max(0,Math.min(2400,p.getPersistentData().getLong(RECOVERY)-p.server.overworld().getGameTime()));}
 public static boolean blocksSprint(LivingEntity e){return e instanceof ServerPlayer p?remaining(p)>0:e.level().isClientSide&&e instanceof net.minecraft.world.entity.player.Player&&ClientTriage.recovering(e.getUUID());}
 public static void recovered(ServerPlayer p){
  if(!p.isAlive())return;
  int duration=java.util.concurrent.ThreadLocalRandom.current().nextInt(1200,2401);
  p.getPersistentData().putLong(RECOVERY,p.server.overworld().getGameTime()+duration);
  p.setSprinting(false);p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration,0,false,true,true));sync(p);
 }
 public static void sync(LivingEntity e){if(e instanceof ServerPlayer p&&channel!=null)channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new State(p.getUUID(),KnockedManager.isKnocked(p),remaining(p)));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
  int left=remaining(p);boolean down=KnockedManager.isKnocked(p);
  if(left>0&&p.isAlive()){
   p.setSprinting(false);
   var weakness=p.getEffect(MobEffects.WEAKNESS);
   if(weakness==null||weakness.getDuration()<left-20)p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,left,0,false,true,true));
  }else if(p.getPersistentData().contains(RECOVERY)){
   p.getPersistentData().remove(RECOVERY);
   // Vanilla effects pause offline, but our world-time deadline continues.
   var weakness=p.getEffect(MobEffects.WEAKNESS);
   if(weakness!=null&&weakness.getAmplifier()==0&&weakness.getDuration()<=2400)p.removeEffect(MobEffects.WEAKNESS);
   sync(p);
  }
  if((left>0||down)&&p.tickCount%20==0)sync(p);
  if(down&&p.tickCount%160==0&&p.getRandom().nextBoolean())p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_HURT,SoundSource.PLAYERS,.25f,.65f);
 }
 @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer watcher&&e.getTarget() instanceof ServerPlayer target&&channel!=null)channel.send(PacketDistributor.PLAYER.with(()->watcher),new State(target.getUUID(),KnockedManager.isKnocked(target),remaining(target)));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){p.getPersistentData().remove(RECOVERY);sync(p);}}
 /** Falls of 40+ damage skip the bleed-out window and kill on the spot. */
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fall(LivingHurtEvent e){
  if(!(e.getEntity() instanceof ServerPlayer p))return;
  if(!e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)||e.getAmount()<40f)return;
  if(FALL_DEATHS.contains(p.getUUID()))return;
  e.setCanceled(true);
  FALL_DEATHS.add(p.getUUID());
  KnockedManager.markKillInProgress(p);
  try{
   KnockedManager.removeKnockedState(p);
   p.hurt(p.damageSources().fall(),Math.max(p.getMaxHealth(),e.getAmount())*2f);
  }finally{
   KnockedManager.unmarkKillInProgress(p);
   FALL_DEATHS.remove(p.getUUID());
  }
 }
 private static final java.util.Set<UUID> FALL_DEATHS=java.util.concurrent.ConcurrentHashMap.newKeySet();
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){e.getEntity().getPersistentData().remove(RECOVERY);}
}
