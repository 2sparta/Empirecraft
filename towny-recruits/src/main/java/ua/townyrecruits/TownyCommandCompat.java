package ua.townyrecruits;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

/** Native Towny confirmation handler retains its timeout, cancellation events and transaction checks. */
final class TownyCommandCompat {
    private TownyCommandCompat(){}
    static Class<?> handler(Bridge bridge)throws Exception {
        return Class.forName("com.palmergames.bukkit.towny.confirmations.ConfirmationHandler",true,bridge.towny.plugin.getClass().getClassLoader());
    }
    static boolean pending(Bridge bridge,CommandSender sender)throws Exception {
        return Boolean.TRUE.equals(handler(bridge).getMethod("hasConfirmation",CommandSender.class).invoke(null,sender));
    }
    static int confirm(CommandSourceStack source,boolean accept){
        try{
            Bridge bridge=Bridge.INSTANCE;
            if(bridge==null||bridge.towny==null||!bridge.ready)throw new IllegalStateException("Towny ще не готовий.");
            if(!(source.m_81373_() instanceof ServerPlayer nativePlayer))throw new IllegalArgumentException("Підтвердження виконується тим самим гравцем.");
            Player player=Bukkit.getPlayer(nativePlayer.m_20148_());
            if(player==null||!pending(bridge,player))throw new IllegalArgumentException("Немає чинного підтвердження Towny. Дія могла прострочитися; повторіть початкову команду.");
            handler(bridge).getMethod(accept?"acceptConfirmation":"revokeConfirmation",CommandSender.class).invoke(null,player);
            return 1;
        }catch(Exception ex){source.m_81352_(net.minecraft.network.chat.Component.m_237113_(Bridge.errorText(ex)));return 0;}
    }
    static void route(PlayerCommandPreprocessEvent event){
        try{
            Bridge bridge=Bridge.INSTANCE;if(bridge==null||bridge.towny==null||!bridge.ready)return;
            Class<?> settings=Class.forName("com.palmergames.bukkit.towny.TownySettings",true,bridge.towny.plugin.getClass().getClassLoader());
            String confirm=settings.getMethod("getConfirmCommand").invoke(null).toString(),cancel=settings.getMethod("getCancelCommand").invoke(null).toString();
            event.setMessage(CommandRouting.route(event.getMessage(),pending(bridge,event.getPlayer()),confirm,cancel));
        }catch(Exception ex){TownyRecruits.LOG.debug("Command compatibility unavailable",ex);}
    }
    static void register(Listener listener,Plugin towny)throws Exception {
        @SuppressWarnings("unchecked") Class<? extends Event> event=(Class<? extends Event>)Class.forName(
            "com.palmergames.bukkit.towny.confirmations.event.ConfirmationSendEvent",true,towny.getClass().getClassLoader());
        Bukkit.getPluginManager().registerEvent(event,listener,EventPriority.MONITOR,(ignored,e)->{
            try{
                Object sender=e.getClass().getMethod("getSender").invoke(e);
                if(sender instanceof Player player)Bukkit.getScheduler().runTask(towny,()->{
                    Bridge bridge=Bridge.INSTANCE;
                    try{if(bridge!=null&&bridge.ready&&player.isOnline()&&pending(bridge,player))
                        player.sendMessage("Підтвердження Towny: /trconfirm — погодитися, /trcancel — скасувати. Діє початковий строк підтвердження.");}
                    catch(Exception ex){TownyRecruits.LOG.debug("Confirmation hint unavailable",ex);}
                });
            }catch(Exception ex){TownyRecruits.LOG.debug("Confirmation hint unavailable",ex);}
        },towny,true);
    }
}
