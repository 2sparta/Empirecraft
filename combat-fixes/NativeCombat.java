package combatfix;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Real native players, item uses and respawns on an isolated complete server. */
@Mod("empirecraft_combat_test")
public final class NativeCombat {
    MinecraftServer server; ServerLevel level; int ticks,checks,phase;
    ServerPlayer attacker,victim; Object api,worldRules; boolean finished;
    final Map<String,Object> report=new LinkedHashMap<>();
    final List<UUID> spawned=new ArrayList<>();
    final List<String> scenarios=new ArrayList<>();
    public NativeCombat(){MinecraftForge.EVENT_BUS.register(this);}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void start(ServerStartedEvent e){server=e.getServer();level=server.overworld();phase=Integer.getInteger("empirecraft.combat.phase",1);}
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    void near(double expected,double actual,String message){check(Math.abs(expected-actual)<.00001,message+": "+expected+" vs "+actual);}
    static Object call(Object target,String name,Object...args)throws Exception{
        for(Method m:target.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isPrimitive()&&!m.getParameterTypes()[i].isInstance(args[i]))match=false;
            if(match){m.setAccessible(true);return m.invoke(target,args);}
        }throw new NoSuchMethodException(target.getClass()+"#"+name);
    }
    static io.netty.channel.embedded.EmbeddedChannel channel(){return new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
        @Override public void write(io.netty.channel.ChannelHandlerContext c,Object m,io.netty.channel.ChannelPromise p){io.netty.util.ReferenceCountUtil.release(m);p.trySuccess();}
        @Override public void flush(io.netty.channel.ChannelHandlerContext c){}
    });}
    @SuppressWarnings({"rawtypes","unchecked"}) ServerPlayer player(String name)throws Exception{
        var p=new ServerPlayer(server,level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=channel();connection.channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        p.connection=new ServerGamePacketListenerImpl(server,connection,p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(64,6,64);level.addNewPlayer(p);
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){
            f.setAccessible(true);if(f.getType()==List.class)try{((List)f.get(server.getPlayerList())).add(p);break;}catch(UnsupportedOperationException ignored){}
        }
        for(Field f:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())&&f.getGenericType().getTypeName().contains("java.util.UUID")&&f.getGenericType().getTypeName().contains("ServerPlayer")){f.setAccessible(true);((Map)f.get(server.getPlayerList())).put(p.getUUID(),p);}
        return p;
    }
    Object towny()throws Exception{
        Object manager=Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
        Object plugin=call(manager,"getPlugin","Towny");check(plugin!=null&&Boolean.TRUE.equals(call(plugin,"isEnabled")),"Actual Towny enabled");
        return Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,plugin.getClass().getClassLoader()).getMethod("getInstance").invoke(null);
    }
    void setup()throws Exception{
        level.setChunkForced(0,0,true);level.setChunkForced(4,4,true);level.setChunkForced(5,4,true);level.setChunkForced(6,4,true);
        api=towny();Object bw=call(level,"getWorld");worldRules=call(api,"getTownyWorld",call(bw,"getName"));
        if(phase==1)check(Boolean.TRUE.equals(call(worldRules,"isPVP")),"Legacy false Towny wilderness PvP was migrated before player combat");
        else {
            check(Boolean.FALSE.equals(call(worldRules,"isPVP")),"Explicit admin PvP toggle after migration survives real restart");
            call(worldRules,"setPVP",true);call(worldRules,"save");
        }
        check(Boolean.FALSE.equals(call(worldRules,"isForcePVP")),"Migration never forces protected town PvP");
        check(Boolean.FALSE.equals(call(worldRules,"isFriendlyFireEnabled")),"Migration preserves allied friendly-fire rules");
        attacker=player("CombatAttacker");victim=player("CombatVictim");
        Object universe=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,api.getClass().getClassLoader()).getMethod("getInstance").invoke(null);
        for(ServerPlayer p:List.of(attacker,victim))if(call(api,"getResident",p.getUUID())==null)call(call(universe,"getDataSource"),"newResident",p.getGameProfile().getName(),p.getUUID());
        check(!attacker.getClass().getName().contains("FakePlayer")&&!victim.getClass().getName().contains("FakePlayer"),"Actual ServerPlayer implementations are used");
        scenarios.add("One-time Towny wilderness migration; towns and friendly fire preserved");
    }
    Set<UUID> recruitIds(){Set<UUID> ids=new HashSet<>();for(Entity e:level.getAllEntities())if(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace().equals("recruits"))ids.add(e.getUUID());return ids;}
    Entity egg(String id,String kind,int index)throws Exception{
        var stack=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
        if(kind.equals("name"))stack.setHoverName(net.minecraft.network.chat.Component.literal("Named egg"));
        if(kind.equals("quality"))stack.getOrCreateTag().putString("ForgingQuality","expert");
        if(kind.equals("entity-name")){var data=new CompoundTag();data.putString("CustomName","\"Test recruit\"");stack.getOrCreateTag().put("EntityTag",data);}
        if(kind.equals("saved")){
            var recruit=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits:recruit")).create(level);
            var nbt=recruit.saveWithoutId(new CompoundTag());stack.getOrCreateTag().put("EntityTag",nbt);
            check(nbt.contains("Items",9)&&nbt.contains("ArmorItems",9)&&nbt.contains("HandItems",9),"Actual serialized recruit has native saved-egg format");
        }
        BlockPos base=new BlockPos(70+index%12,4,64+index/12);level.setBlockAndUpdate(base,Blocks.STONE.defaultBlockState());
        attacker.setItemInHand(InteractionHand.MAIN_HAND,stack);Set<UUID> before=recruitIds();
        var result=stack.useOn(new UseOnContext(attacker,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false)));
        Set<UUID> after=recruitIds();after.removeAll(before);check(result.consumesAction()&&after.size()==1,"Egg spawns exactly one actual recruit: "+id+" "+kind);
        Entity recruit=level.getEntity(after.iterator().next());check(recruit!=null&&recruit.position().distanceToSqr(Vec3.atBottomCenterOf(base.above()))<1,"Egg recruit appears at clicked ground instead of world origin: "+id+" "+kind);
        check(recruit instanceof LivingEntity living&&living.isAlive(),"Spawned recruit is alive");
        if(kind.equals("entity-name"))check(recruit.getName().getString().equals("Test recruit"),"Vanilla EntityTag name survives initialization");
        if(recruit instanceof Mob mob)mob.setNoAi(true);spawned.add(recruit.getUUID());return recruit;
    }
    void eggs()throws Exception{
        var eggs=ForgeRegistries.ITEMS.getEntries().stream().filter(e->e.getKey().location().getNamespace().equals("recruits")&&e.getValue() instanceof SpawnEggItem).map(e->e.getKey().location().toString()).sorted().toList();
        check(eggs.size()==7,"All seven retained native recruit eggs are tested");int i=0;
        for(String id:eggs)for(String kind:List.of("plain","name","quality","entity-name","saved"))egg(id,kind,i++);
        int before=recruitIds().size();int result=server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"summon recruits:recruit 84 5 64");
        check(result>0&&recruitIds().size()==before+1,"Native summon command still creates recruits");
        report.put("egg_uses",i);scenarios.add("35 actual egg uses across seven types, including named/quality/EntityTag and saved eggs; native summon");
    }
    Item item(String id){return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));}
    double armor(ItemStack stack,EquipmentSlot slot){return stack.getAttributeModifiers(slot).get(Attributes.ARMOR).stream().mapToDouble(AttributeModifier::getAmount).sum();}
    void armor()throws Exception{
        var audit=new ArrayList<Map<String,Object>>();for(Item item:ForgeRegistries.ITEMS.getValues())if(item instanceof ArmorItem a){
            double total=armor(new ItemStack(item),a.getEquipmentSlot());
            check(total>=a.getDefense(),"Unforged armor is never weakened: "+ForgeRegistries.ITEMS.getKey(item));
            for(EquipmentSlot slot:EquipmentSlot.values())if(slot!=a.getEquipmentSlot())near(0,armor(new ItemStack(item),slot),"Armor bonus cannot appear in wrong equipment slot");
            audit.add(Map.of("id",ForgeRegistries.ITEMS.getKey(item).toString(),"base",a.getDefense(),"actual",total,"slot",a.getEquipmentSlot().name()));
        }
        var sets=List.of(
            Map.entry(List.of("magistuarmory:maximilian_helmet","magistuarmory:maximilian_chestplate","magistuarmory:maximilian_leggings","magistuarmory:maximilian_boots"),4d),
            Map.entry(List.of("magistuarmory:armet","magistuarmory:knight_chestplate","magistuarmory:knight_leggings","magistuarmory:knight_boots"),2d),
            Map.entry(List.of("magistuarmory:bascinet","magistuarmory:xivcenturyknight_chestplate","magistuarmory:xivcenturyknight_leggings","magistuarmory:xivcenturyknight_boots"),1.5d),
            Map.entry(List.of("minecraft:iron_helmet","minecraft:iron_chestplate","minecraft:iron_leggings","minecraft:iron_boots"),1d),
            Map.entry(List.of("magistuarmoryaddon:dark_maximilian_helmet","magistuarmoryaddon:dark_maximilian_chestplate","magistuarmoryaddon:dark_maximilian_leggings","magistuarmoryaddon:dark_maximilian_boots"),4d));
        String[] quality={"","poor","well","expert","perfect","master"};double[] delta={0,-1.5,-.5,0,.5,1};
        for(var set:sets)for(String id:set.getKey()){
            check(item(id) instanceof ArmorItem,"Retained armor item exists: "+id);var a=(ArmorItem)item(id);
            for(int q=0;q<quality.length;q++){var stack=new ItemStack(a);if(q>0)stack.getOrCreateTag().putString("ForgingQuality",quality[q]);near(a.getDefense()+set.getValue()+delta[q],armor(stack,a.getEquipmentSlot()),"Set tier stays independent of forging quality: "+id+" "+quality[q]);}
        }
        for(int n=0;n<30;n++)near(13,armor(new ItemStack(item("magistuarmory:maximilian_chestplate")),EquipmentSlot.CHEST),"Attribute queries cannot stack repeated armor bonuses");
        report.put("armor_audit",audit);scenarios.add("All registered armor slots audited; five full sets and all six quality states; repeat queries are idempotent");
    }
    void wounds()throws Exception{
        var effect=ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("magistuarmory:laceration"));check(effect!=null,"Existing effect ID stays save-compatible");
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits:recruit"));var target=(LivingEntity)type.create(level);float hp=target.getMaxHealth();
        check(!target.addEffect(new MobEffectInstance(effect,200,2)),"Laceration is rejected from direct effect application");near(hp,target.getMaxHealth(),"Rejected wound cannot reduce max health");
        var saved=target.saveWithoutId(new CompoundTag());var active=new ListTag();active.add(new MobEffectInstance(effect,200,2).save(new CompoundTag()));saved.put("ActiveEffects",active);
        target.load(saved);target.setPos(86,5,64);level.addFreshEntity(target);check(!target.hasEffect(effect),"Saved laceration is removed when the entity joins");near(hp,target.getMaxHealth(),"Old wound health penalty is repaired");
        UUID penalty=UUID.fromString("81AEAA56-376B-4498-935B-2F7F68070635");var orphan=(LivingEntity)type.create(level);
        orphan.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(penalty,"Legacy laceration",-6,AttributeModifier.Operation.ADDITION));
        orphan.setPos(87,5,64);level.addFreshEntity(orphan);near(hp,orphan.getMaxHealth(),"Orphaned saved max-health modifier is repaired");
        check(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,0)),"Unrelated allowed effects remain functional");
        Class<?> config=Class.forName("com.magistuarmory.config.GeneralConfig");
        // The native config is also shipped to clients so weapon descriptions match.
        report.put("laceration_removed",true);scenarios.add("Direct and saved laceration disabled, orphaned health repaired; ordinary effects retained");
    }
    void attack(String label,boolean allowed){
        victim.invulnerableTime=0;victim.setHealth(victim.getMaxHealth());float before=victim.getHealth();boolean hurt=victim.hurt(level.damageSources().playerAttack(attacker),4);
        check(allowed?hurt&&victim.getHealth()<before:!hurt&&victim.getHealth()==before,label);
        report.put(label,Map.of("hurt",hurt,"damage",before-victim.getHealth()));
    }
    void legacyPvp()throws Exception{
        call(worldRules,"setPVP",false);try{attack("Legacy Towny false blocks wilderness PvP",false);}finally{call(worldRules,"setPVP",true);}
        attack("Wilderness PvP works with repaired flag",true);scenarios.add("Original conflicting wilderness flag reproduces the block on actual player damage");
    }
    void respawn()throws Exception{
        victim.invulnerableTime=0;check(victim.hurt(level.damageSources().genericKill(),10000)&&!victim.isAlive(),"Actual player dies before respawn");
        victim=server.getPlayerList().respawn(victim,false);victim.setGameMode(GameType.SURVIVAL);victim.setNoGravity(true);victim.setPos(64,6,64);
    }
    void protectTown()throws Exception{
        Object universe=Class.forName("com.palmergames.bukkit.towny.TownyUniverse",true,api.getClass().getClassLoader()).getMethod("getInstance").invoke(null);
        Object town=call(api,"getTown","CombatShelter");if(town==null){call(universe,"newTown","CombatShelter");town=call(api,"getTown","CombatShelter");}
        call(town,"setPVP",false);Object block;
        try{block=call(worldRules,"getTownBlock",6,4);}catch(InvocationTargetException absent){Class<?> blockType=Class.forName("com.palmergames.bukkit.towny.object.TownBlock",true,api.getClass().getClassLoader());block=blockType.getConstructor(int.class,int.class,worldRules.getClass()).newInstance(6,4,worldRules);call(universe,"addTownBlock",block);}
        call(block,"setTown",town);victim.setPos(98,6,66);attacker.setPos(99,6,66);attack("Peaceful town still blocks player damage",false);
        victim.setPos(64,6,64);attacker.setPos(65,6,64);attack("Return to wilderness restores player damage",true);
        scenarios.add("Town/plot protection remains authoritative after migration");
    }
    void finish()throws Exception{
        for(UUID id:spawned)check(level.getEntity(id)!=null&&level.getEntity(id).isAlive(),"Egg recruit survives away from the void");
        protectTown();call(worldRules,"setPVP",false);call(worldRules,"save");
        report.put("checks",checks);report.put("scenarios",scenarios);report.put("phase",phase);report.put("passed",true);
        Files.writeString(Path.of("combat-phase-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("COMBAT_NATIVE_PASS phase="+phase+" checks="+checks);finished=true;server.halt(false);server=null;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(server==null||finished||e.phase!=TickEvent.Phase.END)return;ticks++;
        try{
            if(ticks==40)setup();
            if(ticks==160){eggs();armor();wounds();}
            if(ticks==240)legacyPvp();
            if(ticks>=260&&ticks<=1140&&(ticks-260)%220==0){attack("PvP before death at tick "+ticks,true);respawn();}
            if(ticks>=470&&ticks<=1350&&(ticks-470)%220==0)attack("PvP after respawn at tick "+ticks,true);
            if(ticks==1360)finish();
        }catch(Throwable error){error.printStackTrace();System.out.println("COMBAT_NATIVE_FAIL phase="+phase+" ticks="+ticks);finished=true;server.halt(false);server=null;}
    }
}
