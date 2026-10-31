package com.quintonc.vs_sails.protection;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.*;
import static net.minecraft.commands.Commands.*;

@Mod.EventBusSubscriber(modid="vs_sails")
public final class ProtectionCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent e) {
        var ship=literal("ship");
        for(String action:List.of("info","claim","accept","canceltransfer","recover"))
            ship.then(literal(action).then(argument("id",LongArgumentType.longArg(0)).executes(c->run(c,action,null))));
        for(String action:List.of("transfer","trusttown","untrusttown","trustplayer","untrustplayer","assign"))
            ship.then(literal(action).then(argument("id",LongArgumentType.longArg(0)).then(argument("target",StringArgumentType.string()).executes(c->run(c,action,StringArgumentType.getString(c,"target"))))));
        ship.then(literal("reload").requires(s->s.hasPermission(2)).executes(c->{ProtectionConfig.reload();c.getSource().sendSuccess(()->Component.literal("Sails ship protection reloaded"),false);return 1;}));
        var fleet=literal("fleet");
        fleet.then(literal("info").executes(c->fleet(c,"info",null)));
        for(String action:List.of("trusttown","untrusttown","trustplayer","untrustplayer"))
            fleet.then(literal(action).then(argument("target",StringArgumentType.string()).executes(c->fleet(c,action,StringArgumentType.getString(c,"target")))));
        e.getDispatcher().register(literal("vs_sails").then(ship).then(fleet));
    }
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalArgumentException(message);}
    private static UUID target(ShipTowny towny,String action,String name) throws ReflectiveOperationException {
        if(action.startsWith("untrust"))try{return UUID.fromString(name);}catch(IllegalArgumentException notUuid){/* resolve the current name */}
        UUID id=action.endsWith("player")?towny.playerId(name):ShipTowny.id(towny.town(name));require(id!=null,"Unknown town/player");return id;
    }
    private static void grant(ShipAccess.Grants grants,String action,UUID target) {
        Set<UUID> list=action.endsWith("player")?grants.players:grants.towns;
        if(action.startsWith("untrust"))list.remove(target);else list.add(target);
    }
    private static int fleet(CommandContext<CommandSourceStack> c,String action,String name) {
        try {
            var p=c.getSource().getPlayerOrException();var towny=new ShipTowny();UUID town=towny.playerTown(p.getUUID());
            require(towny.manages(p.getUUID(),town),"Only your town's mayor or fleet manager may change fleet access");
            var registry=ShipRegistry.get(p.getServer());var grants=registry.fleets.computeIfAbsent(town,id->new ShipAccess.Grants());
            if(action.equals("info")) {c.getSource().sendSuccess(()->Component.literal("Fleet towns="+grants.towns+"; players="+grants.players),false);return 1;}
            grant(grants,action,target(towny,action,name));registry.setDirty();
            c.getSource().sendSuccess(()->Component.literal("Fleet access updated: "+action+" "+name),true);return 1;
        }catch(Exception | LinkageError ex){c.getSource().sendFailure(Component.literal("Sails: "+ex.getMessage()));return 0;}
    }
    private static int run(CommandContext<CommandSourceStack> c,String action,String name) {
        var source=c.getSource();
        try {
            long id=LongArgumentType.getLong(c,"id");
            var body=VSGameUtilsKt.getShipObjectWorld(source.getServer()).getLoadedShips().getById(id);
            require(body!=null,"Ship must be loaded");
            if(action.equals("recover")) {
                require(source.hasPermission(2),"Admin permission required for emergency recovery");
                var p=source.getPosition();
                require(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                        && Math.abs(p.x)<29999000 && Math.abs(p.z)<29999000 && Math.abs(p.y)<2048,"Choose a safe recovery position");
                var control=com.quintonc.vs_sails.ship.SailsShipControl.getOrCreate(body);
                control.releaseAnchor();
                control.beginRecovery(p.x,p.y,p.z);
                double scale=body.getTransform().getShipToWorldScaling().x();if(!Double.isFinite(scale) || scale<=0)scale=1;
                body.setStatic(true);
                VSGameUtilsKt.getShipObjectWorld(source.getServer()).teleportShip(body,new org.valkyrienskies.core.impl.game.ShipTeleportDataImpl(
                        new org.joml.Vector3d(p.x,p.y,p.z),new org.joml.Quaterniond(),new org.joml.Vector3d(),new org.joml.Vector3d(),
                        VSGameUtilsKt.getDimensionId(source.getLevel()),scale,body.getTransform().getPositionInShip()));
                source.sendSuccess(()->Component.literal("Ship #"+id+" recovery requested; waiting for a safe physics snapshot"),true);return 1;
            }
            var towny=new ShipTowny();var registry=ShipRegistry.get(source.getServer());
            var vessel=registry.ships.get(id);
            if(action.equals("info")) {
                var control=body.getAttachment(com.quintonc.vs_sails.ship.SailsShipControl.class);
                String text="Ship #"+id+" owner: "+(vessel==null?"UNREGISTERED":towny.describe(vessel.town))
                    +"; ship towns="+(vessel==null?"[]":vessel.grants.towns)+"; ship players="+(vessel==null?"[]":vessel.grants.players)
                    +"; transfer="+(vessel==null?null:vessel.offeredTo)+"; safety="+(control!=null && (control.sailSafetyHeld || control.motionSafetyTripped));
                source.sendSuccess(()->Component.literal(text),false);return 1;
            }
            if(action.equals("assign")) {
                require(source.hasPermission(2),"Admin permission required for legacy owner assignment");
                var town=target(towny,action,name);registry.ships.put(id,new ShipAccess.Vessel(town,null));
            }else {
                var p=source.getPlayerOrException();UUID ownTown=towny.playerTown(p.getUUID());
                if(action.equals("claim")) {
                    require(vessel!=null && vessel.town==null && p.getUUID().equals(vessel.founder),"Only the recorded founder can register a personal ship; legacy ships need admin assign");
                    require(towny.manages(p.getUUID(),ownTown),"Your town's mayor or fleet manager must register the ship");vessel.town=ownTown;
                }else if(action.equals("accept")) {
                    require(vessel!=null && towny.manages(p.getUUID(),ownTown),"Only the destination town's mayor or fleet manager can accept");vessel.accept(ownTown);
                }else {
                    require(vessel!=null && towny.manages(p.getUUID(),vessel.town),"Only the owner town's mayor or fleet manager may change ship access");
                    switch(action) {
                        case "transfer" -> {UUID destination=target(towny,action,name);require(!destination.equals(vessel.town),"Ship already belongs to this town");vessel.offeredTo=destination;}
                        case "canceltransfer" -> vessel.offeredTo=null;
                        default -> grant(vessel.grants,action,target(towny,action,name));
                    }
                }
            }
            registry.setDirty();source.sendSuccess(()->Component.literal("Ship #"+id+": "+action+" saved"),true);return 1;
        }catch(Exception | LinkageError ex){source.sendFailure(Component.literal("Sails: "+ex.getMessage()));return 0;}
    }
}
