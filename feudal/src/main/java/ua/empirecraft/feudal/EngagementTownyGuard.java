package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.event.damage.*;
import com.palmergames.bukkit.towny.event.actions.TownyDestroyEvent;
import java.util.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;

/** Only Towny's own decision is overridden. Unrelated Bukkit/Forge cancellation stays in force. */
final class EngagementTownyGuard implements Listener {
    final EngagementController c;
    EngagementTownyGuard(EngagementController controller){c=controller;}
    private void damage(TownyDamageEvent event,Entity attacker){
        try {
            var data=new LinkedHashMap<String,Object>();data.put("victim",event.getEntity().getUniqueId().toString());if(attacker!=null)data.put("attacker",attacker.getUniqueId().toString());
            var result=c.nativeCall("towny-damage",data);if(Boolean.TRUE.equals(result.get("handled")))event.setCancelled(!Boolean.TRUE.equals(result.get("allow")));
        }catch(Exception unavailable){/* The native guard fails closed; Feudal can also run without the optional native module. */}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void players(TownyPlayerDamagePlayerEvent e){damage(e,e.getAttackingPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST) public void recruit(TownyPlayerDamageEntityEvent e){damage(e,e.getAttackingPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST) public void explosion(TownyExplosionDamagesEntityEvent e){damage(e,null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void dispenser(TownyDispenserDamageEntityEvent e){damage(e,null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void destroy(TownyDestroyEvent e){
        try {var p=e.getLocation();var result=c.nativeCall("towny-block",Map.of("player",e.getPlayer().getUniqueId().toString(),"x",p.getBlockX(),"y",p.getBlockY(),"z",p.getBlockZ()));if(Boolean.TRUE.equals(result.get("handled")))e.setCancelled(!Boolean.TRUE.equals(result.get("allow")));}
        catch(Exception unavailable){/* Preserve Towny's decision. */}
    }
}
