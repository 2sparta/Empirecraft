package com.empirecraft.whalehitboxes.test;

import java.util.*;
import net.devra.accuratehitboxes.empirecraft.*;
import net.devra.accuratehitboxes.network.DynamicHitboxManager;
import net.devra.accuratehitboxes.util.*;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.collision.WeaponHitBoxes;
import net.minecraft.core.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/** Run only in smoke.py's disposable world. No graphics or invented Minecraft classes. */
@Mod("empirecraft_whale_hitbox_test")
public final class WhaleHitboxSmoke {
    MinecraftServer server;ServerLevel level;int tick,checks;ServerPlayer player;LivingEntity whale;
    ServerGamePacketListenerImpl handler;
    public WhaleHitboxSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void started(ServerStartedEvent event){server=event.getServer();level=server.overworld();}
    void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    void aim(Vec3 eye,Vec3 target){
        player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);player.setDeltaMovement(Vec3.ZERO);
        Vec3 d=target.subtract(eye).normalize();player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
    }
    List<OrientedBoundingBox> parts(Matrix4f rotation){
        // These are the body, tail, mouth and fin cubes from bluewhale.geo.json,
        // at its renderer's 1.1 scale, with a second animated orientation.
        double scale=1.1/16;
        return List.of(
                part(-35,-6,-70,70,60,170,scale,rotation),
                part(-50,32.5,360,100,5,50,scale,rotation),
                part(-33,0,-255,66,30,155,scale,rotation),
                part(30,2.5,-73.5,50,3,25,scale,rotation));
    }
    OrientedBoundingBox part(double x,double y,double z,double sx,double sy,double sz,double scale,Matrix4f transform){
        return new OrientedBoundingBox(new AABB(x*scale,y*scale,z*scale,(x+sx)*scale,(y+sy)*scale,(z+sz)*scale),transform,whale.position());
    }
    void publish(List<OrientedBoundingBox> parts){
        var accurate=(IAccurateEntity)whale;accurate.accuratehitboxes$clearHitboxes();for(var part:parts)accurate.accuratehitboxes$addHitbox(part);
        DynamicHitboxManager.put(player.getUUID(),whale.getUUID(),parts,level.getGameTime());
    }
    void setup()throws Exception{
        level.setChunkForced(0,0,true);level.setChunkForced(0,1,true);level.setChunkForced(0,-1,true);
        player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.fromString("0bc969ed-2644-4a67-83fe-368e0511d37b"),"WhaleHitTester"));
        player.setGameMode(GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
        handler=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);player.connection=handler;
        // Mohist ignores interaction packets from offline players. Register the
        // fixture actor in the real level and UUID lookup before exercising it.
        level.addNewPlayer(player);server.getPlayerList().players.add(player);player.setNoGravity(true);
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())
                && field.getGenericType().getTypeName().contains("java.util.UUID") && field.getGenericType().getTypeName().contains("net.minecraft.server.level.ServerPlayer")){
            field.setAccessible(true);((Map)field.get(server.getPlayerList())).put(player.getUUID(),player);
        }
        check(!player.isImmobile(),"packet fixture player is genuinely online to Mohist");
        whale=(LivingEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("wonderoussea","blue_whale")).create(level);
        whale.setPos(0,75,0);((Mob)whale).setNoAi(true);whale.setNoGravity(true);level.addFreshEntity(whale);
    }
    boolean combat(WeaponAttributes.Attack attack,double range){
        Vec3 origin=player.getEyePosition().subtract(0,player.getBbHeight()*.15*player.getScale(),0);
        Vec3 size=WeaponHitBoxes.createHitbox(attack.hitbox(),range,false);
        var shape=new net.bettercombat.client.collision.OrientedBoundingBox(origin,size,player.getXRot(),player.getYRot()).offsetAlongAxisZ(size.z/2).updateVertex();
        BetterCombatGeometry.begin(player);
        try{return Boolean.TRUE.equals(BetterCombatGeometry.collides(shape,whale)) && Boolean.TRUE.equals(BetterCombatGeometry.radial(whale,origin,shape.axisZ,range,attack.angle()));}
        finally{BetterCombatGeometry.clear();}
    }
    void attacks(){
        var attack=new com.google.gson.Gson().fromJson("{\"angle\":180,\"hitbox\":\"HORIZONTAL_PLANE\"}",WeaponAttributes.Attack.class);
        int hits=0;
        for(float angle:new float[]{0,90,180,270}){
            Matrix4f transform=new Matrix4f().rotationY((float)Math.toRadians(angle));var boxes=parts(transform);publish(boxes);
            for(int index:new int[]{1,2,3}){
                var frame=WhaleCandidates.model(boxes.get(index));var center=frame.center;
                var outward=new Vector3d(center).sub(whale.getX(),whale.getY(),whale.getZ()).normalize();
                var surface=frame.closest(new Vector3d(center).fma(100,outward));
                var eye=new Vector3d(surface).fma(1.7,outward);aim(new Vec3(eye.x,eye.y,eye.z),new Vec3(center.x,center.y,center.z));
                AABB search=player.getBoundingBox().expandTowards(player.getLookAngle().scale(3)).inflate(4);
                boolean original=level.getEntities(player,search,e->e==whale).contains(whale);
                check(WhaleCandidates.query(level,player,search,e->e==whale).contains(whale),"model candidate discovered at yaw "+angle+" part "+index);
                if(index==1)check(!original,"regression really lies outside original vanilla candidate bounds");
                check(player.canReachRaw(whale,0),"server reach uses visible model part at yaw "+angle+" part "+index);
                check(combat(attack,3),"Better Combat weapon volume and production model filters accept the same visible part");
                float health=whale.getHealth();whale.invulnerableTime=0;whale.hurtTime=0;
                handler.handleInteract(ServerboundInteractPacket.createAttackPacket(whale,false));
                check(whale.getHealth()<health,"vanilla server interaction packet deals real damage to remote model part");hits++;
                var arrow=new Arrow(level,player);var from=player.getEyePosition();var to=from.add(player.getLookAngle().scale(3));
                var shot=ProjectileUtil.getEntityHitResult(level,arrow,from,to,new AABB(from,to).inflate(.5),e->e==whale);
                check(shot!=null&&shot.getEntity()==whale,"actual projectile query hits the model beyond central bounds");
                if(index==1){
                    aim(new Vec3(eye.x+outward.x*5,eye.y+outward.y*5,eye.z+outward.z*5),new Vec3(center.x,center.y,center.z));
                    check(!player.canReachRaw(whale,0),"tail does not gain unlimited melee range");
                    check(!combat(attack,3),"Better Combat respects actual weapon reach");
                }
            }
        }
        // A canceled Forge attack still cannot bypass protection through model reach.
        var boxes=parts(new Matrix4f());publish(boxes);var tail=WhaleCandidates.model(boxes.get(1)).center;
        aim(new Vec3(tail.x,tail.y,tail.z+3.3),new Vec3(tail.x,tail.y,tail.z));
        java.util.function.Consumer<AttackEntityEvent> cancel=e->e.setCanceled(true);MinecraftForge.EVENT_BUS.addListener(cancel);
        float health=whale.getHealth();whale.invulnerableTime=0;handler.handleInteract(ServerboundInteractPacket.createAttackPacket(whale,false));MinecraftForge.EVENT_BUS.unregister(cancel);
        check(whale.getHealth()==health,"normal Forge attack cancellation is preserved");
        BlockPos wall=BlockPos.containing(player.getEyePosition().lerp(new Vec3(tail.x,tail.y,tail.z),.5));
        for(int x=-5;x<=5;x++)for(int y=-4;y<=4;y++)level.setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());
        check(!player.canReachRaw(whale,0),"server rejects model part behind solid wall");
        check(!combat(attack,3),"Better Combat still rejects attacks through walls");
        for(int x=-5;x<=5;x++)for(int y=-4;y<=4;y++)level.setBlockAndUpdate(wall.offset(x,y,0),Blocks.AIR.defaultBlockState());
        // Unsupported vanilla mobs retain the original Forge reach implementation.
        Cow cow=EntityType.COW.create(level);cow.setPos(0,75,0);aim(new Vec3(20,76,0),new Vec3(0,76,0));
        check(!player.canReachRaw(cow,0),"vanilla distant cow reach unchanged");aim(new Vec3(1,76,0),new Vec3(0,76,0));check(player.canReachRaw(cow,0),"vanilla nearby cow reach unchanged");
        check(hits==12,"all four whale orientations and three remote parts were tested");
    }
    void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null)return;
        try{
            if(++tick==5)setup();
            if(tick==20){attacks();System.out.println("WHALE_HITBOX_NATIVE_PASS assertions="+checks+" realDamageCases=12");server.halt(false);server=null;}
        }catch(Throwable failure){System.out.println("WHALE_HITBOX_NATIVE_FAIL tick="+tick);failure.printStackTrace();server.halt(false);server=null;}
    }
}
