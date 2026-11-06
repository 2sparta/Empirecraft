package com.empirecraft.engagementtest;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import com.quintonc.vs_sails.protection.FleetNative;
import ua.empirecraft.engagement.*;

/** Controlled contacts and synthetic citizens; real Towny, native entities, blocks, seats and saved worlds. */
@Mod("empirecraft_engagement_smoke")
public final class EngagementSmokeMod extends FleetFixtureBase {
    final List<String> seats=new ArrayList<>();
    final List<String> failures=new ArrayList<>();
    @FunctionalInterface interface Scenario {void run()throws Exception;}
    void scenario(String name,Scenario action)throws Exception {try{action.run();System.out.println("ENGAGEMENT_NATIVE_STEP_PASS "+name);}catch(Throwable failure){failures.add(name);System.out.println("ENGAGEMENT_NATIVE_STEP_FAIL "+name);failure.printStackTrace();resetCombat();Government.clear();}}
    BlockPos flagA,flagB;String storedShot,shipApplication;long requestedShip;
    public EngagementSmokeMod(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    @SuppressWarnings("unchecked") Map<String,Long> timers()throws Exception{return (Map<String,Long>)field(CombatLedger.get(server),"until");}
    void resetCombat()throws Exception {timers().clear();CombatLedger.get(server).setDirty();EngagementRuntime.clear();}
    static Object invokeStatic(Class<?> type,String name,Object...args)throws Exception {
        for(var method:type.getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);return method.invoke(null,args);}throw new NoSuchMethodException(name);
    }
    static Object construct(String name,Object...args)throws Exception {for(var constructor:Class.forName(name).getDeclaredConstructors())if(constructor.getParameterCount()==args.length){constructor.setAccessible(true);return constructor.newInstance(args);}throw new NoSuchMethodException(name);}
    static Object invoke(Object receiver,String name,Object...args)throws Exception {for(var method:receiver.getClass().getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);return method.invoke(receiver,args);}throw new NoSuchMethodException(name);}
    Vec3 world(LoadedServerShip ship,BlockPos pos){return CombatRaycast.worldPoint(ship,Vec3.atCenterOf(pos));}
    void stand(ServerPlayer player,LoadedServerShip ship,BlockPos deck){Vec3 p=world(ship,deck);player.setPos(p.x,p.y+.5,p.z);player.setOnGround(true);Deck.forget(player.getUUID());}
    BlockPos deck(LoadedServerShip ship,int dx){var box=ship.getShipAABB();return new BlockPos(box.minX()+dx,box.minY(),box.minZ()+1);}
    NavalFlags.FlagEntity flag(LoadedServerShip ship,BlockPos pos)throws Exception {
        for(int y=1;y<8;y++)level.setBlockAndUpdate(pos.below(y),Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(pos,NavalFlags.BLOCK.get().defaultBlockState());var flag=(NavalFlags.FlagEntity)level.getBlockEntity(pos);flag.ship=ship.getId();
        ((Map<Long,Long>)field(CombatLedger.get(server),"flagPositions")).put(ship.getId(),pos.asLong());CombatLedger.get(server).setDirty();return flag;
    }
    void configure(ServerPlayer actor,LoadedServerShip ship,BlockPos pos,CombatPolicy.Flag mode)throws Exception {
        Vec3 target=world(ship,pos);actor.setPos(target.x,target.y+1,target.z+2);Deck.forget(actor.getUUID());
        NavalFlags.configure(actor,ship,mode,true);check(NavalFlags.effective(level,ship,Government.read(level,"ship",Map.of("ship",ship.getId())))==mode,"official ensign is visible and authoritative: "+mode);
    }
    void flags()throws Exception {
        var a=ship(longShip);var b=ship(auctionShip);
        check(Combatants.ship(level,a,null,null,null).side().flag()==CombatPolicy.Flag.PIRATE,"missing flag counts as pirate in claimed waters");
        flagA=deck(a,80).above(8);flagB=deck(b,4).above(8);flag(a,flagA);flag(b,flagB);
        configure(ownerMayor,a,flagA,CombatPolicy.Flag.PEACEFUL);configure(mayor,b,flagB,CombatPolicy.Flag.PEACEFUL);
        check(((NavalFlags.FlagEntity)level.getBlockEntity(flagA)).width==8,"large 201-block hull requires eight-block cloth");
        check(((NavalFlags.FlagEntity)level.getBlockEntity(flagB)).width==2,"small hull uses two-block cloth");
        check(Combatants.ship(level,a,null,null,null).side().water()==CombatPolicy.Water.NEUTRAL,"real Towny neutral claimed water classification");
        check(Combatants.ship(level,b,null,null,null).side().water()==CombatPolicy.Water.OWN,"real Towny own claimed water classification");
        check(!EngagementRuntime.decide(Combatants.ship(level,a,null,null,null),Combatants.ship(level,b,null,null,null),level,false).allow(),"peaceful sheltered victim cannot be attacked from another safe hull");
        level.setBlockAndUpdate(flagA.east(2).above(),Blocks.GLASS.defaultBlockState());
        check(Combatants.ship(level,a,null,null,null).side().flag()==CombatPolicy.Flag.PIRATE,"covered cloth loses protection even through glass");
        level.setBlockAndUpdate(flagA.east(2).above(),Blocks.AIR.defaultBlockState());
        check(Combatants.ship(level,a,null,null,null).side().flag()==CombatPolicy.Flag.PEACEFUL,"clearing the canopy restores an eligible untagged flag");
        ownerMayor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BLUE_BANNER));
        var design=PaintingDesign.inspect(ownerMayor,"banner");check(PaintingDesign.valid(design.get("kind").toString(),design.get("payload").toString(),design.get("hash").toString()),"actual held banner produces a validated state design");
        check(!PaintingDesign.valid("banner",design.get("payload").toString(),"0".repeat(64)),"forged design hash rejected");
    }
    void stateDesign()throws Exception {
        Object nation=call(host,"getNationOrNull");if(nation==null){Object nations=call(universe,"getDataSource");call(nations,"newNation","EnsignState");nation=call(api,"getNation","EnsignState");call(host,"setNation",nation);}call(nation,"setCapital",host);
        Object authority=field(plugin,"engagement");mayor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BLUE_BANNER));
        boolean refused=false;try{invoke(authority,"createDesign",bukkit(captain),"banner");}catch(InvocationTargetException expected){refused=expected.getCause() instanceof IllegalArgumentException;}check(refused,"ordinary captain cannot approve a national design");
        invoke(authority,"createDesign",bukkit(mayor),"banner");check(row("SELECT * FROM naval_ensigns WHERE nation=?",id(nation).toString())!=null,"actual Towny king approves a durable state ensign");Government.clear();
        configure(mayor,ship(auctionShip),flagB,CombatPolicy.Flag.STATE);
        check(!EngagementRuntime.decide(Combatants.ship(level,ship(auctionShip),null,null,null),Combatants.ship(level,ship(longShip),null,null,null),level,false).allow(),"state ensign cannot initiate combat from its own waters");
        configure(ownerMayor,ship(longShip),flagA,CombatPolicy.Flag.PIRATE);
        check(EngagementRuntime.decide(Combatants.ship(level,ship(longShip),null,null,null),Combatants.ship(level,ship(auctionShip),null,null,null),level,false).allow(),"pirate may attack a state ship in its actual own waters");
        configure(ownerMayor,ship(longShip),flagA,CombatPolicy.Flag.PEACEFUL);configure(mayor,ship(auctionShip),flagB,CombatPolicy.Flag.PEACEFUL);
    }
    void crewAndSeats()throws Exception {
        var a=ship(longShip);var b=ship(auctionShip);
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"captain",captain.getUUID().toString());
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"crew-add",admiral.getUUID().toString());
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"crew-add",stranger.getUUID().toString());
        invoke(controller,"reconcile");Government.clear();stand(captain,a,deck(a,30));stand(admiral,a,deck(a,35));stranger.setPos(3,81,3);Deck.forget(stranger.getUUID());
        check(Deck.support(captain)!=null&&Deck.support(captain).getId()==longShip,"standing crew has real hull support");
        check(Deck.support(stranger)==null,"registered shore crew is not aboard by roster or loose bounds");
        for(var entry:ForgeRegistries.ENTITY_TYPES.getEntries()){
            String name=entry.getKey().location().toString();if(!name.contains("seat")&&!name.contains("chair"))continue;
            if(!Set.of("create","dawnoftimebuilder","doapi","vinery","timber_joinery").contains(entry.getKey().location().getNamespace()))continue;
            Entity seat=entry.getValue().create(level);if(seat==null)continue;BlockPos anchor=deck(a,40).above();seat.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);level.addFreshEntity(seat);
            check(captain.startRiding(seat,true),"real modded seat accepts native passenger: "+name);Deck.forget(captain.getUUID());check(Deck.support(captain)!=null&&Deck.support(captain).getId()==longShip,"vehicle root resolves modded seat on a ship: "+name);
            var carrier=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);carrier.setPos(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5);level.addFreshEntity(carrier);check(seat.startRiding(carrier,true),"nested modded transport accepts seat: "+name);Deck.forget(captain.getUUID());
            check(Deck.support(captain)!=null&&Deck.support(captain).getId()==longShip,"nested seat resolves root hull: "+name);
            check(Objects.equals(Combatants.party(carrier).ship(),longShip),"attacking occupied nested transport cannot bypass crew protection: "+name);
            carrier.setPos(5,81,5);Deck.forget(captain.getUUID());check(Deck.support(captain)==null,"same nested seat on shore is not aboard: "+name);
            seat.stopRiding();carrier.discard();
            captain.stopRiding();seat.discard();seats.add(name);
        }
        check(seats.stream().anyMatch(s->s.startsWith("create:")),"Create seats exercised");
        stand(captain,a,deck(a,30));stand(admiral,a,deck(a,35));
        EngagementRuntime.tag(Combatants.ship(level,a,captain,captain.getUUID(),null),level,false);
        check(Combatants.active(captain)&&Combatants.active(admiral),"all actual aboard crew enter combat");
        check(!Combatants.active(stranger),"crew ashore never receives a ship's combat tag");
        EngagementRuntime.attack(captain,stranger,true);check(!Combatants.active(stranger),"early Forge intent does not create a shore civilian's combat consent before Towny runs");
        check(Boolean.FALSE.equals(EngagementNative.request("towny-damage",Map.of("attacker",captain.getUUID().toString(),"victim",stranger.getUUID().toString())).get("handled")),"naval aggressor cannot override ordinary Towny protection of unrelated shore civilians");
        EngagementRuntime.damaged(stranger,level.damageSources().playerAttack(captain));check(Combatants.active(stranger),"actual admitted damage can tag a previously inactive civilian after Towny's decision");
        check(!com.quintonc.vs_sails.protection.ShipProtection.allows(otherMayor,level,a),"PvP does not grant outsiders helm or inventory access");
        resetCombat();var frame=new net.minecraft.world.entity.decoration.ItemFrame(level,deck(a,20).above(),Direction.UP);level.addFreshEntity(frame);
        check(Objects.equals(Combatants.target(frame).ship(),longShip),"actual hanging decorations belong to the protected hull");
        check(!EngagementRuntime.attack(otherMayor,frame,true),"destroying a hanging ship decoration cannot bypass hull protection");captain.setPos(3,81,3);Deck.forget(captain.getUUID());check(EngagementRuntime.attack(captain,frame,true),"authorized crew can maintain its own ship decorations from shore");frame.discard();
        resetCombat();
    }
    void recruit()throws Exception {
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits","recruit"));check(type!=null,"actual recruit entity type registered");
        var recruit=(LivingEntity)type.create(level);call(recruit,"setIsOwned",true);call(recruit,"setOwnerUUID",Optional.of(admiral.getUUID()));UUID group=UUID.randomUUID();call(recruit,"setGroupUUID",group);
        ((Mob)recruit).setNoAi(true);recruit.setPos(4,81,4);level.addFreshEntity(recruit);
        check(Combatants.unit(recruit).commander().equals(admiral.getUUID()),"real recruit commander identity");
        var victim=Combatants.party(recruit);check(victim.side().kind()==CombatPolicy.Kind.RECRUIT&&victim.side().sheltered(),"owned recruits protected in actual own claims");
        ownerMayor.setPos(4,81,6);Deck.forget(ownerMayor.getUUID());float health=recruit.getHealth();
        check(!EngagementRuntime.attack(ownerMayor,recruit,true),"first attack cannot hurt a protected company");check(recruit.getHealth()==health,"denied intent does not consume health");
        check(Combatants.active(ownerMayor)&&!Combatants.active(recruit),"only the aggressor loses immunity on blocked intent");
        check(EngagementRuntime.attack(recruit,ownerMayor,true),"protected recruits can retaliate against the flagged aggressor");
        check(Combatants.active(recruit)&&Combatants.active(admiral),"participating recruit and its shore commander enter combat");
        var second=(LivingEntity)type.create(level);call(second,"setIsOwned",true);call(second,"setOwnerUUID",Optional.of(admiral.getUUID()));call(second,"setGroupUUID",group);((Mob)second).setNoAi(true);second.setPos(5,81,4);level.addFreshEntity(second);
        EngagementRuntime.tag(Combatants.party(recruit),level,true);call(second,"setGroupUUID",UUID.randomUUID());check(Combatants.active(second),"regrouping cannot clear a participating soldier's personal combat tag");
        resetCombat();call(second,"setGroupUUID",group);var a=ship(longShip);var aboard=world(a,deck(a,35));recruit.setPos(aboard.x,aboard.y+.5,aboard.z);Deck.forget(recruit.getUUID());
        EngagementRuntime.tag(Combatants.party(recruit),level,true);check(Combatants.active(recruit)&&!Combatants.active(second),"naval combat tags actual recruit crew without dragging its shore company into PvP");
        recruit.discard();second.discard();resetCombat();
        var townless=player("FleetTownless",null,false);var outsider=(LivingEntity)type.create(level);call(outsider,"setIsOwned",true);call(outsider,"setOwnerUUID",Optional.of(townless.getUUID()));((Mob)outsider).setNoAi(true);outsider.setPos(4,81,4);level.addFreshEntity(outsider);
        check(!Combatants.party(outsider).side().sheltered(),"a commander without a city cannot obtain protection in every claimed town");outsider.discard();
    }
    void boarding()throws Exception {
        // Forge's FakePlayerNetHandler deliberately makes teleport() a no-op.
        // Use the real listener with our discard-only packet sink for this check.
        captain.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,captain.connection.connection,captain);
        var a=ship(longShip);var b=ship(auctionShip);resetCombat();stand(captain,a,deck(a,0));Deck.remember(captain,a);var safe=captain.position();var destination=world(b,deck(b,9));captain.setPos(destination.x,destination.y+.5,destination.z);
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);EngagementRuntime.tick(level);
        check(captain.position().distanceToSqr(safe)<1,"forbidden boarding returns the intruder to actual prior deck support");check(!CombatLedger.get(server).active(Combatants.shipKey(auctionShip),server.overworld().getGameTime()),"forbidden boarding does not force a protected victim into PvP");
        resetCombat();configure(ownerMayor,a,flagA,CombatPolicy.Flag.PIRATE);configure(mayor,b,flagB,CombatPolicy.Flag.STATE);stand(admiral,a,deck(a,30));stand(mayor,b,deck(b,1));
        stand(captain,a,deck(a,0));Deck.remember(captain,a);captain.setPos(destination.x,destination.y+.5,destination.z);((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);EngagementRuntime.tick(level);
        check(CombatLedger.get(server).active(Combatants.shipKey(longShip),server.overworld().getGameTime())&&CombatLedger.get(server).active(Combatants.shipKey(auctionShip),server.overworld().getGameTime()),"admitted pirate boarding of a state ship tags both actual vessels");
        check(Combatants.active(captain)&&Combatants.active(admiral)&&Combatants.active(mayor)&&!Combatants.active(stranger),"boarding tags both physically present crews and leaves shore crew alone");
        resetCombat();configure(ownerMayor,a,flagA,CombatPolicy.Flag.PEACEFUL);configure(mayor,b,flagB,CombatPolicy.Flag.PEACEFUL);
    }
    @SuppressWarnings("unchecked") void ram(LoadedServerShip source,LoadedServerShip target,BlockPos cell,double energy,boolean moving)throws Exception {
        Class<?> damage=Class.forName("ua.empirecraft.shipphysics.CollisionDamage"),properties=Class.forName("ua.empirecraft.shipphysics.BlockProperties");
        var state=level.getBlockState(cell);Object material=invokeStatic(properties,"at",level,cell,state),surface=invokeStatic(properties,"responseAt",level,cell,state);
        Object targetCell=construct("ua.empirecraft.shipphysics.CollisionDamage$Target",cell,material,new org.joml.Vector3d(cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5),new org.joml.Vector3d(0,1,0),1.0,surface);
        String dimension=org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(level);Object pair=invokeStatic(Class.forName("ua.empirecraft.shipphysics.EpisodeGate$Pair"),"of",dimension,source.getId(),target.getId());
        long sortedA=((Number)call(pair,"a")).longValue();double nx=sortedA==source.getId()?1:-1;var point=world(target,cell);
        Object contact=construct("ua.empirecraft.shipphysics.CollisionDamage$Contact",point.x,point.y,point.z,nx,0.0,0.0,10.0,10.0,0.0,0.0,null,null);
        Object frame=construct("ua.empirecraft.shipphysics.CollisionDamage$Frame",pair,List.of(contact),false,0L,10.0,0.0);
        Object fast=construct("ua.empirecraft.engagement.RamCombat$Motion",new org.joml.Vector3d(),new org.joml.Vector3d(moving?10:0,0,0),new org.joml.Vector3d());
        Object still=construct("ua.empirecraft.engagement.RamCombat$Motion",new org.joml.Vector3d(),new org.joml.Vector3d(),new org.joml.Vector3d());
        Object motions=construct("ua.empirecraft.engagement.RamCombat$Motions",source.getId(),target.getId(),fast,still,fast,still);
        Field frames=RamCombat.class.getDeclaredField("FRAMES");frames.setAccessible(true);((Map<Object,Object>)frames.get(null)).put(frame,motions);
        Object work;try(var scope=RamCombat.frame(server,frame)){work=invokeStatic(damage,"plan",level,target,targetCell,energy);}
        Field pending=damage.getDeclaredField("PENDING");pending.setAccessible(true);((Deque<Object>)pending.get(null)).add(work);invokeStatic(damage,"drain",server);
        check(((Deque<?>)pending.get(null)).isEmpty(),"controlled real collision queue drains");
    }
    double remaining(BlockPos cell)throws Exception {
        Class<?> properties=Class.forName("ua.empirecraft.shipphysics.BlockProperties"),data=Class.forName("ua.empirecraft.shipphysics.DamageData");
        return ((Number)call(invokeStatic(data,"get",level),"remaining",cell,invokeStatic(properties,"at",level,cell,level.getBlockState(cell)))).doubleValue();
    }
    void ramming()throws Exception {
        var a=ship(longShip);var b=ship(auctionShip);BlockPos cell=deck(b,7);double hp=remaining(cell);
        var bounds=b.getShipAABB();List<BlockPos> originalDeck=new ArrayList<>();for(int x=bounds.minX();x<bounds.maxX();x++)for(int z=bounds.minZ();z<bounds.maxZ();z++)originalDeck.add(new BlockPos(x,bounds.minY(),z));
        resetCombat();ram(a,b,cell,1,true);check(remaining(cell)==hp&&!level.getBlockState(cell).isAir(),"forbidden ram cannot reduce partial block durability");
        check(timers().isEmpty(),"denied ram does not force either ship or crew into PvP");
        configure(ownerMayor,a,flagA,CombatPolicy.Flag.PIRATE);configure(mayor,b,flagB,CombatPolicy.Flag.PIRATE);
        ram(a,b,cell,1,true);check(remaining(cell)<hp&&!level.getBlockState(cell).isAir(),"permitted light ram damages a real block without destroying it");
        check(timers().isEmpty(),"allowed partial damage does not enter PvP before a block breaks");
        stand(captain,a,deck(a,30));stand(admiral,a,deck(a,35));stand(mayor,b,deck(b,1));stranger.setPos(3,81,3);Deck.forget(stranger.getUUID());
        ram(a,b,cell,200000,true);check(level.getBlockState(cell).isAir(),"permitted ram destroys actual target hull blocks");
        check(CombatLedger.get(server).active(Combatants.shipKey(longShip),server.overworld().getGameTime())&&CombatLedger.get(server).active(Combatants.shipKey(auctionShip),server.overworld().getGameTime()),"first destroyed target block tags both vessels");
        check(Combatants.active(captain)&&Combatants.active(admiral)&&Combatants.active(mayor),"first destroyed target block tags both actual crews");check(!Combatants.active(stranger),"ram never tags roster members on shore");
        resetCombat();level.setBlockAndUpdate(cell,Blocks.OAK_PLANKS.defaultBlockState());
        ram(a,b,cell,200000,false);check(timers().isEmpty(),"stationary vessel cannot be blamed when another hull breaks against it");
        for(var block:originalDeck)level.setBlockAndUpdate(block,Blocks.OAK_PLANKS.defaultBlockState());
        configure(ownerMayor,a,flagA,CombatPolicy.Flag.PEACEFUL);configure(mayor,b,flagB,CombatPolicy.Flag.PEACEFUL);
    }
    void cannon()throws Exception {
        var a=ship(longShip);BlockPos pos=deck(a,50).above();var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("supplementaries","cannon"));check(block!=null,"installed Supplementaries cannon registered");
        level.setBlockAndUpdate(pos,block.defaultBlockState());Object cannon=level.getBlockEntity(pos);call(cannon,"setProjectile",new ItemStack(Items.TNT));call(cannon,"setFuel",new ItemStack(Items.GUNPOWDER,16));
        Field gunner=cannon.getClass().getDeclaredField("playerWhoIgnitedUUID");gunner.setAccessible(true);gunner.set(cannon,captain.getUUID());
        Set<UUID> before=new HashSet<>();for(Entity entity:level.getAllEntities())before.add(entity.getUUID());
        Method fire=cannon.getClass().getDeclaredMethod("shootProjectile",ServerLevel.class);fire.setAccessible(true);check(Boolean.TRUE.equals(fire.invoke(cannon,level)),"real Supplementaries cannon fires TNT");
        PrimedTnt shot=null;for(Entity entity:level.getAllEntities())if(entity instanceof PrimedTnt tnt&&!before.contains(entity.getUUID()))shot=tnt;
        check(shot!=null&&ShotOrigins.find(shot)!=null&&Objects.equals(ShotOrigins.find(shot).ship(),longShip),"real cannon TNT carries its firing hull, including automation scope");
        var b=ship(auctionShip);BlockPos protectedCell=deck(b,1);level.setBlockAndUpdate(protectedCell,Blocks.OAK_PLANKS.defaultBlockState());resetCombat();
        var explosion=new net.minecraft.world.level.Explosion(level,shot,world(b,protectedCell).x,world(b,protectedCell).y,world(b,protectedCell).z,4,false,net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(protectedCell);var detonate=new net.minecraftforge.event.level.ExplosionEvent.Detonate(level,explosion,new ArrayList<>());MinecraftForge.EVENT_BUS.post(detonate);
        check(!detonate.getAffectedBlocks().contains(protectedCell),"real cannon TNT explosion event removes a protected hull from its blast");
        try(var scope=ShotOrigins.enter(ShotOrigins.find(shot))){check(!level.destroyBlock(protectedCell,false)&&!level.getBlockState(protectedCell).isAir(),"final block mutation guard also denies protected cannon penetration");}
        resetCombat();configure(mayor,b,flagB,CombatPolicy.Flag.PIRATE);explosion.getToBlow().clear();explosion.getToBlow().add(protectedCell);MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.ExplosionEvent.Detonate(level,explosion,new ArrayList<>()));
        check(explosion.getToBlow().contains(protectedCell),"same real TNT source may damage an unprotected pirate hull in a claim");
        try(var scope=ShotOrigins.enter(ShotOrigins.find(shot))){check(level.destroyBlock(protectedCell,false),"admitted ordnance can actually destroy the unprotected hull block");}
        check(CombatLedger.get(server).active(Combatants.shipKey(auctionShip),server.overworld().getGameTime()),"admitted cannon blast tags its actual victim ship");
        shot.discard();resetCombat();level.setBlockAndUpdate(protectedCell,Blocks.OAK_PLANKS.defaultBlockState());configure(mayor,b,flagB,CombatPolicy.Flag.PEACEFUL);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
    }
    void provenance()throws Exception {
        var a=ship(longShip);stand(captain,a,deck(a,30));var origin=ShotOrigins.of(Combatants.party(captain),level);
        PrimedTnt tnt=new PrimedTnt(level,10,120,10,captain);tnt.setFuse(6000);tnt.setNoGravity(true);
        try(var scope=ShotOrigins.launcher(origin)){level.addFreshEntity(tnt);}
        var source=ShotOrigins.find(tnt);check(source!=null&&Objects.equals(source.ship(),longShip),"primed TNT inherits launch ship, including block-launched ordnance");
        check(source.launch().flag()==CombatPolicy.Flag.PEACEFUL,"launch flag captured before it can change");
        configure(ownerMayor,a,flagA,CombatPolicy.Flag.PIRATE);check(ShotOrigins.find(tnt).launch().flag()==CombatPolicy.Flag.PEACEFUL,"lowering an ensign cannot rewrite an existing TNT shot");
        storedShot=tnt.getUUID().toString();saved.setProperty("shot",storedShot);saved.setProperty("flagA",Long.toString(flagA.asLong()));saved.setProperty("flagB",Long.toString(flagB.asLong()));
        check(timers().containsKey(Combatants.shipKey(longShip)),"firing while protected exposes the firing ship");
    }
    void carriageProvenance()throws Exception {
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("createbigcannons","cannon_carriage"));check(type!=null,"actual CBC wheeled carriage is registered");
        var carriage=type.create(level);var point=world(ship(longShip),deck(ship(longShip),45));carriage.setPos(point.x,point.y+.5,point.z);level.addFreshEntity(carriage);
        check(Deck.support(carriage)!=null&&Deck.support(carriage).getId()==longShip,"real CBC wheels have physical hull support");
        ownerMayor.setPos(3,81,3);Deck.forget(ownerMayor.getUUID());var shot=new PrimedTnt(level,point.x,point.y+2,point.z,ownerMayor);shot.getPersistentData().putUUID("EmpirecraftGrapeshotVehicle",carriage.getUUID());level.addFreshEntity(shot);
        check(ShotOrigins.find(shot)!=null&&Objects.equals(ShotOrigins.find(shot).ship(),longShip),"server-stamped wheeled launcher outranks a gunner standing ashore");shot.discard();
        carriage.setPos(5,81,5);Deck.forget(carriage.getUUID());var landShot=new PrimedTnt(level,5,83,5,null);landShot.getPersistentData().putUUID("EmpirecraftGrapeshotVehicle",carriage.getUUID());level.addFreshEntity(landShot);
        check(ShotOrigins.find(landShot)!=null&&ShotOrigins.find(landShot).ship()==null,"same CBC carriage ashore cannot retain a stale firing ship");landShot.discard();carriage.discard();resetCombat();
    }
    void refreshAuthority()throws Exception {
        call(field(plugin,"towny"),"sync");call(field(plugin,"armyController"),"refresh");
        var type=Class.forName("ua.townyrecruits.Bridge");var instance=type.getDeclaredField("INSTANCE");instance.setAccessible(true);Object bridge=instance.get(null);
        var refresh=type.getDeclaredMethod("refreshNow");refresh.setAccessible(true);refresh.invoke(bridge);
        check(Boolean.TRUE.equals(field(bridge,"ready")),"actual native army authority is ready");Government.clear();
    }
    @SuppressWarnings("unchecked") void alliedCommand()throws Exception {
        Object homeNation=call(host,"getNationOrNull"),allyNation=call(owner,"getNationOrNull"),source=call(universe,"getDataSource");
        if(allyNation==null){call(source,"newNation","AlliedCommand");allyNation=call(api,"getNation","AlliedCommand");call(owner,"setNation",allyNation);call(allyNation,"setCapital",owner);}
        call(homeNation,"addAlly",allyNation);call(allyNation,"addAlly",homeNation);refreshAuthority();
        check(Boolean.TRUE.equals(call(fleet,"commanderAllowed",town(owner),admiral.getUUID().toString())),"actual Towny allied-city captain is eligible");
        // Earlier boarding/seat cases granted this player an independent crew role.
        // Isolate captain delegation so revocation cannot fall back to that grant.
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"crew-remove",admiral.getUUID().toString());
        call(fleet,"edit",actor(ownerMayor),request(),longShip,"captain",admiral.getUUID().toString());
        check(Boolean.TRUE.equals(call(fleet,"controls",admiral.getUUID().toString(),longShip)),"allied captain controls the owner city's actual ship");
        check(town(owner).equals(row("SELECT town FROM naval_ships WHERE id=?",longShip).get("town")),"captain delegation does not transfer ship ownership");
        var bridgeType=Class.forName("ua.townyrecruits.Bridge");var instance=bridgeType.getDeclaredField("INSTANCE");instance.setAccessible(true);Object bridge=instance.get(null);
        Object military=field(bridge,"military"),data=field(military,"data"),army=field(bridge,"data");
        Map<UUID,Object> regiments=(Map<UUID,Object>)field(data,"regiments"),companies=(Map<UUID,Object>)field(data,"companies"),units=(Map<UUID,Object>)field(army,"units");
        Map<UUID,UUID> members=(Map<UUID,UUID>)field(data,"members"),cities=(Map<UUID,UUID>)field(data,"cities");
        call(military,"command",mayor,false,new String[]{"create","Allied_Test"});Object regiment=null;
        for(var r:regiments.values())if(call(r,"name").equals("Allied_Test"))regiment=r;
        check(regiment!=null,"real native command creates the city regiment");UUID regimentId=(UUID)call(regiment,"id"),nation=id(homeNation);
        call(military,"command",mayor,false,new String[]{"form",regimentId.toString(),"Allied_Company","FleetAdmiral"});UUID group=null;
        for(var entry:companies.entrySet())if(call(entry.getValue(),"regiment").equals(regimentId))group=entry.getKey();
        check(group!=null,"real native command creates its company");
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits","recruit"));var recruit=(LivingEntity)type.create(level);
        call(recruit,"setIsOwned",true);call(recruit,"setOwnerUUID",Optional.of(admiral.getUUID()));call(recruit,"setGroupUUID",group);((Mob)recruit).setNoAi(true);recruit.setPos(6,81,6);level.addFreshEntity(recruit);
        UUID offline=UUID.randomUUID();var unitType=Class.forName("ua.townyrecruits.ArmyData$Unit");var constructor=unitType.getConstructor(UUID.class,UUID.class,boolean.class);
        units.put(recruit.getUUID(),constructor.newInstance(admiral.getUUID(),nation,false));units.put(offline,constructor.newInstance(admiral.getUUID(),nation,false));members.put(recruit.getUUID(),group);members.put(offline,group);
        ((Collection<Object>)field(bridge,"loaded")).add(recruit);
        call(military,"command",mayor,false,new String[]{"commander",regimentId.toString(),group.toString(),"FleetCaptain"});
        check(call(units.get(offline),"owner").equals(captain.getUUID()),"allied delegation rewrites the persisted unloaded soldier record");
        check(call(recruit,"getOwnerUUID").equals(captain.getUUID()),"allied delegation rewrites the real loaded recruit");
        check(call(units.get(offline),"nation").equals(nation),"delegation preserves the owning national quota");
        check(cities.get(regimentId).equals(id(host)),"regiment city remains the original owning city");
        check(Combatants.party(recruit).town().equals(town(host))&&Combatants.party(recruit).side().water()==CombatPolicy.Water.OWN,"combat protection uses regiment ownership instead of its foreign commander's city");
        call(military,"command",mayor,false,new String[]{"leader",regimentId.toString(),"FleetCaptain"});
        check(call(regiments.get(regimentId),"leader").equals(captain.getUUID()),"actual Towny command appoints an allied regiment leader");
        int before=companies.size();call(military,"command",captain,false,new String[]{"form",regimentId.toString(),"Allied_Second","FleetCaptain"});
        check(companies.size()==before+1,"foreign leader can organize its assigned regiment through the real command");
        call(military,"command",captain,false,new String[]{"leader",regimentId.toString(),"FleetSeller"});
        check(call(regiments.get(regimentId),"leader").equals(captain.getUUID()),"foreign leader cannot replace the owning nation's appointment authority");
        call(homeNation,"removeAlly",allyNation);call(allyNation,"removeAlly",homeNation);refreshAuthority();
        check(!Boolean.TRUE.equals(call(fleet,"controls",admiral.getUUID().toString(),longShip)),"dissolving the real alliance immediately blocks foreign captain controls");call(fleet,"tick");
        check(row("SELECT captain FROM naval_ships WHERE id=?",longShip).get("captain").equals(""),"fleet reconciliation clears the revoked appointment");
        check(call(regiments.get(regimentId),"leader").equals(mayor.getUUID())&&call(units.get(offline),"owner").equals(mayor.getUUID()),"native reconciliation restores the owning city's command including unloaded soldiers");
        call(homeNation,"addAlly",allyNation);call(allyNation,"addAlly",homeNation);refreshAuthority();
        call(military,"command",mayor,false,new String[]{"commander",regimentId.toString(),group.toString(),"FleetCaptain"});call(military,"command",mayor,false,new String[]{"leader",regimentId.toString(),"FleetCaptain"});
        // These citizens were created synthetically, without the real Towny join/save
        // commands. Persist their town membership before testing a process restart.
        for(Object resident:(Collection<?>)call(api,"getResidents"))call(source,"saveResident",resident);
        call(source,"saveNation",homeNation);call(source,"saveNation",allyNation);call(source,"saveTown",host);call(source,"saveTown",owner);
        call(source,"saveTown",third);
        saved.setProperty("allied-regiment",regimentId.toString());saved.setProperty("allied-offline",offline.toString());saved.setProperty("allied-nation",nation.toString());saved.setProperty("allied-recruit",recruit.getUUID().toString());
    }
    void persistState()throws Exception {
        saved.setProperty("long",Long.toString(longShip));saved.setProperty("auction",Long.toString(auctionShip));saved.setProperty("built","0");saved.setProperty("offer","");saved.setProperty("market",market);
        try(var out=Files.newOutputStream(Path.of("fleet-save.properties"))){saved.store(out,"Controlled engagement fixture");}
    }
    void shipApplication()throws Exception {
        Object registrations=field(plugin,"ships"),service=field(registrations,"service");BlockPos anchor=new BlockPos(55,90,24);
        level.setBlockAndUpdate(anchor,Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(anchor.east(),Blocks.OAK_PLANKS.defaultBlockState());
        mayor.setPos(55.5,91,26);ownerMayor.setPos(55.5,91,26);mayor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);ownerMayor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Object attachment=call(bukkit(ownerMayor),"addAttachment",towny);call(attachment,"setPermission","empirecraft.feudal.ships.admin",true);
        Object admin=actorType.getConstructor(String.class,boolean.class).newInstance(ownerMayor.getUUID().toString(),true);
        String world=FleetNative.request("snapshot",Map.of("ship",longShip)).get("world").toString();
        var anchorType=Class.forName("ua.empirecraft.feudal.ShipRegistrationService$Anchor",true,plugin.getClass().getClassLoader());var constructor=anchorType.getDeclaredConstructor(String.class,int.class,int.class,int.class);constructor.setAccessible(true);Object point=constructor.newInstance(world,55,90,24);
        shipApplication=invoke(service,"submit",actor(mayor),request(),"Заявлена Чайка",point).toString();
        check(row("SELECT name,state FROM naval_applications WHERE id=?",shipApplication).get("name").equals("Заявлена Чайка"),"actual player's application stores the requested ship name before physics");
        check(row("SELECT payload FROM registration_outbox WHERE kind='SHIP' AND reference=?",shipApplication)!=null,"ship application queues an administrative Discord notice");
        var wand=ForgeRegistries.ITEMS.getValues().stream().filter(item->item.getClass().getName().equals("org.valkyrienskies.mod.common.item.ShipAssemblerItem")).findFirst().orElseThrow();
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(anchor),Direction.UP,anchor,false);
        for(var item:ForgeRegistries.ITEMS.getValues())if(Set.of("org.valkyrienskies.mod.common.item.ShipAssemblerItem","org.valkyrienskies.mod.common.item.ShipCreatorItem","com.quintonc.vs_sails.items.DedicationBottle").contains(item.getClass().getName())){
            mayor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(item));item.useOn(new net.minecraft.world.item.context.UseOnContext(mayor,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            check(level.getBlockState(anchor).is(Blocks.OAK_PLANKS),"non-admin cannot give physics through actual item: "+item.getClass().getSimpleName());
        }
        ownerMayor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(wand));wand.useOn(new net.minecraft.world.item.context.UseOnContext(ownerMayor,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        check(level.getBlockState(anchor).is(Blocks.OAK_PLANKS),"even an administrator cannot assemble an unapproved application");
        invoke(service,"review",admin,request(),shipApplication,true,"Native admin review");
        wand.useOn(new net.minecraft.world.item.context.UseOnContext(ownerMayor,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        check(level.getBlockState(anchor).isAir(),"approved actual VS wand moves the hull blocks into a physical ship");
        ShipAssemblyGate.flush(server);var application=row("SELECT * FROM naval_applications WHERE id=?",shipApplication);
        check(application.get("ship") instanceof Number,"real VS assembly binds the physical ship ID to the approved application");requestedShip=((Number)application.get("ship")).longValue();
        check(application.get("state").equals("ASSEMBLED"),"physical assembly waits for administrator city/name confirmation");
        saved.setProperty("application",shipApplication);saved.setProperty("requested-ship",Long.toString(requestedShip));
    }
    @SuppressWarnings("unchecked") void shipAssignment()throws Exception {
        Object registrations=field(plugin,"ships");invoke(registrations,"tick");invoke(controller,"ensure",requestedShip);Government.clear();
        check(ship(requestedShip).isStatic(),"new hull stays in place while administrator ownership menu is pending");
        check(!Boolean.TRUE.equals(Government.read(level,"ship",Map.of("ship",requestedShip)).get("ready")),"unassigned application cannot enter naval combat");
        var sessions=(Map<UUID,Object>)field(controller,"sessions");Object session=sessions.get(ownerMayor.getUUID());
        check(field(session,"section").equals("ship-assign"),"actual assembly automatically opens administrator assignment menu");
        String sessionId=field(session,"id").toString();
        var view=(Map<String,Object>)call(plugin,"fleetUi",ownerMayor.getUUID(),sessionId,"view","","",List.of(),"",1);
        var forms=(List<Map<String,Object>>)view.get("forms");check(forms!=null&&forms.size()==1,"assignment menu exposes the server-bound finalization form");
        check(((List<?>)forms.get(0).get("defaults")).get(1).equals("Заявлена Чайка"),"assignment form prefills the name entered by the applicant");
        var prepared=(Map<String,Object>)call(plugin,"fleetUi",ownerMayor.getUUID(),sessionId,"prepare","","ship-finalize",List.of(town(host),"Затверджена Чайка"),"",1);
        check(!prepared.containsKey("error"),"administrator can preview owning city and final name: "+prepared);
        String token=((Map<?,?>)prepared.get("confirmation")).get("token").toString();
        var finished=(Map<String,Object>)call(plugin,"fleetUi",ownerMayor.getUUID(),sessionId,"confirm","","",List.of(),token,1);check(!finished.containsKey("error"),"administrator confirms actual native ship ownership: "+finished);
        ShipAssemblyGate.flush(server);check(!ship(requestedShip).isStatic(),"finishing the menu releases the newly physical hull");
        var vessel=row("SELECT * FROM naval_ships WHERE id=?",requestedShip);check(vessel.get("town").equals(town(host))&&vessel.get("name").equals("Затверджена Чайка"),"menu assigns chosen city and name to the real ship");
        var creator=ForgeRegistries.ITEMS.getValues().stream().filter(item->item.getClass().getName().equals("org.valkyrienskies.mod.common.item.ShipCreatorItem")).findFirst().orElseThrow();
        // The stock wand reserves a 7x7 patch, including air around the two
        // solid test blocks. Its claim minimum is not necessarily a hull block.
        var bounds=ship(requestedShip).getShipAABB();BlockPos existing=null;
        for(var position:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))if(level.getBlockState(position).is(Blocks.OAK_PLANKS)){existing=position.immutable();break;}
        check(existing!=null,"existing-hull Creator probe targets a real assembled block");var original=level.getBlockState(existing);
        ownerMayor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(creator));creator.useOn(new net.minecraft.world.item.context.UseOnContext(ownerMayor,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(existing),Direction.UP,existing,false)));
        check(level.getBlockState(existing).equals(original),"VS Creator cannot bypass an application by splitting a block off an existing physical hull");
        Object commands=call(plugin,"getCommand","shipadmin");call(commands,"execute",bukkit(ownerMayor),"shipadmin",new String[]{"owner",Long.toString(requestedShip),"FleetOwner"});
        check(row("SELECT town FROM naval_ships WHERE id=?",requestedShip).get("town").equals(town(owner)),"actual administrator command changes the owning city");
        check(row("SELECT state FROM naval_applications WHERE id=?",shipApplication).get("state").equals("ASSIGNED"),"completed application is durable and closed");
    }
    void restored()throws Exception {
        check(CombatLedger.get(server).active(Combatants.shipKey(longShip),server.overworld().getGameTime()),"ship combat persists across a real server restart");
        BlockPos flag=BlockPos.of(Long.parseLong(saved.getProperty("flagA")));check(level.getBlockEntity(flag) instanceof NavalFlags.FlagEntity,"physical flag and block entity persist");
        check(((NavalFlags.FlagEntity)level.getBlockEntity(flag)).mode==CombatPolicy.Flag.PIRATE,"changed pirate flag persists without clearing combat");
        var shot=level.getEntity(UUID.fromString(saved.getProperty("shot")));check(shot!=null&&ShotOrigins.find(shot)!=null,"in-flight TNT provenance survives a real saved world");
        check(ShotOrigins.find(shot).launch().flag()==CombatPolicy.Flag.PEACEFUL,"original launch authority persists independently of current flag");
        shot.discard();
        if(saved.containsKey("application")){
            long requested=Long.parseLong(saved.getProperty("requested-ship"));var application=row("SELECT * FROM naval_applications WHERE id=?",saved.getProperty("application"));
            check(application!=null&&application.get("state").equals("ASSIGNED"),"approved ship application survives a real restart");
            var vessel=row("SELECT town,name FROM naval_ships WHERE id=?",requested);check(vessel.get("town").equals(town(owner))&&vessel.get("name").equals("Затверджена Чайка"),"administrator's city change and requested ship name survive restart");
            check(ship(requested)!=null&&!ship(requested).isStatic(),"finalized physical ship remains usable after restart");
        }
        if(saved.containsKey("allied-regiment")){
            refreshAuthority();var system=Class.forName("ua.townyrecruits.MilitarySystem").getMethod("current").invoke(null);Object data=field(system,"data");
            var regiments=(Map<?,?>)field(data,"regiments");Object regiment=regiments.get(UUID.fromString(saved.getProperty("allied-regiment")));
            check(regiment!=null,"persisted Towny nations retain the saved regiment after restart");
            check(call(regiment,"leader").equals(captain.getUUID()),"allied regiment leader survives a real restart");
            check(((Map<?,?>)field(data,"cities")).get(UUID.fromString(saved.getProperty("allied-regiment"))).equals(id(host)),"canonical owning city survives restart independently of the allied leader");
            var bridgeType=Class.forName("ua.townyrecruits.Bridge");var instance=bridgeType.getDeclaredField("INSTANCE");instance.setAccessible(true);Object army=field(instance.get(null),"data");Object offline=((Map<?,?>)field(army,"units")).get(UUID.fromString(saved.getProperty("allied-offline")));
            check(call(offline,"owner").equals(captain.getUUID())&&call(offline,"nation").equals(UUID.fromString(saved.getProperty("allied-nation")))&&!Boolean.TRUE.equals(call(offline,"retired")),"unloaded delegated soldier retains commander and original nation on restart");
        }
    }
    void tick(TickEvent.ServerTickEvent event){if(server==null||event.phase!=TickEvent.Phase.END)return;
        try {ticks++;if(ticks==5)setup();if(ticks==30)market();if(ticks==70){
            if(restart){restored();System.out.println("ENGAGEMENT_NATIVE_PASS phase=restart assertions="+checks);server.halt(false);server=null;}
            else{scenario("flags",this::flags);scenario("national-design",this::stateDesign);scenario("crew-seats",this::crewAndSeats);scenario("recruits",this::recruit);scenario("ramming",this::ramming);scenario("cannon",this::cannon);scenario("boarding",this::boarding);scenario("CBC-carriage",this::carriageProvenance);scenario("provenance",this::provenance);scenario("allied-command",this::alliedCommand);scenario("ship-application",this::shipApplication);if(!failures.isEmpty())throw new AssertionError("Failed scenarios: "+failures);}
        }else if(ticks==100&&!restart){scenario("ship-assignment",this::shipAssignment);if(!failures.isEmpty())throw new AssertionError("Failed scenarios: "+failures);persistState();
            System.out.println("ENGAGEMENT_NATIVE_PASS phase=flags-crew-seats-recruits-provenance-applications assertions="+checks+" seats="+seats);server.halt(false);server=null;
        }}catch(Throwable failure){System.out.println("ENGAGEMENT_NATIVE_FAIL tick="+ticks);failure.printStackTrace();server.halt(false);server=null;}
    }
}
