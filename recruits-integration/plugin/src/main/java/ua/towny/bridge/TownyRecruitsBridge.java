package ua.towny.bridge;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.util.*;

/** Protocol adapter. Political state and food remain in Feudal's main-thread ledger. */
public final class TownyRecruitsBridge extends JavaPlugin implements CommandExecutor,TabCompleter {
    private Object feudal;private Method command,attach,detach;private boolean connected;
    @Override public void onEnable(){
        try{
            feudal=Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");if(feudal==null)throw new IllegalStateException("Потрібен Feudal 1.11.0+");
            Method request=feudal.getClass().getMethod("armyRequest",String.class,Map.class);Object caps=request.invoke(feudal,"protocol",Map.of());if(!(caps instanceof Map<?,?> map)||!Objects.equals(map.get("protocol"),2))throw new IllegalStateException("Потрібен Army protocol 2");
            command=feudal.getClass().getMethod("armyCommand",CommandSender.class,Command.class,String.class,String[].class);
            getCommand("army").setExecutor(this);getCommand("army").setTabCompleter(this);
            Bukkit.getScheduler().runTaskTimer(this,()->{if(connected)return;try{Class<?> hooks=Class.forName("ua.empirecraft.recruits.ArmyHooks");attach=hooks.getMethod("attach",Object.class);detach=hooks.getMethod("detach");attach.invoke(null,feudal);connected=true;getLogger().info("Recruits ↔ Towny/Feudal підключено. Провізія: /t provision; статистика: /t menu → Рекрути.");}catch(Exception e){getLogger().warning("Очікується серверний мод empirecraft-recruits-integration-1.0.0: "+e.getClass().getSimpleName());}},1,200);
        }catch(Exception e){getLogger().severe("Неможливо ввімкнути зв’язку рекрутів: "+e);Bukkit.getPluginManager().disablePlugin(this);}
    }
    @Override public void onDisable(){if(connected&&detach!=null)try{detach.invoke(null);}catch(Exception ignored){}connected=false;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){try{return Boolean.TRUE.equals(command.invoke(feudal,sender,cmd,label,args));}catch(Exception e){sender.sendMessage("§cНе вдалося виконати команду армії. Дивіться журнал Feudal.");getLogger().warning(e.toString());return true;}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return a.length==1?List.of("menu","status","assign","release","officer","revoke","upkeep","war","peace","vote","wars","history","inspect","why","command","supply"):List.of();}
}
