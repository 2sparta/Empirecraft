package ua.empirecraft.potato.smoke;

import com.google.gson.Gson;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.nio.file.*;
import java.util.*;

/** Dedicated test server intentionally has no potato addon. No production worlds are used. */
public final class ServerProbe {
    private ServerPlayer player;
    private Zombie target;
    private boolean grounded, moved, jumped, hit;
    private double x,y,z;
    public static void register() { MinecraftForge.EVENT_BUS.register(new ServerProbe()); }
    @SubscribeEvent public void started(ServerStartedEvent event) throws Exception {
        if(ModList.get().isLoaded("empirecraft_potato")) throw new AssertionError("Optional addon was loaded on test server");
        event.getServer().overworld().setDayTime(13000);
        Files.writeString(Path.of("potato-server-ready.json"),"{\"addon_loaded\":false}");
    }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if(target!=null) target.discard();
        grounded=moved=jumped=hit=false;
        player=p; target=EntityType.ZOMBIE.create(p.serverLevel());
        target.setNoAi(true); target.setPersistenceRequired(); target.setCustomName(Component.literal("Potato target"));
        target.moveTo(p.getX()+2,p.getY(),p.getZ(),0,0); p.serverLevel().addFreshEntity(target);
    }
    @SubscribeEvent public void hurt(LivingHurtEvent event) {
        if(event.getEntity()==target && event.getSource().getEntity()==player && event.getAmount()>0) hit=true;
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END || player==null) return;
        if(!grounded && player.onGround()) { grounded=true; x=player.getX(); y=player.getY(); z=player.getZ(); }
        if(grounded) {
            moved|=Math.hypot(player.getX()-x,player.getZ()-z)>0.4;
            jumped|=player.getY()>y+0.2;
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("addon_loaded",false);result.put("joined",true);
        result.put("moved",moved);result.put("jumped",jumped);result.put("hit",hit);result.put("passed",moved&&jumped&&hit);
        Files.writeString(Path.of("potato-server-result.json"),new Gson().toJson(result));
    }
}
