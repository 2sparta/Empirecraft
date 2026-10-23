package ua.empirecraft.feudal;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.time.Instant;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

final class DynastyCommands implements CommandExecutor,TabCompleter,Listener,SuccessionService.Authority {
    private final FeudalPlugin plugin;private final FeudalService s;private final TownyBridge towny;SuccessionService succession;
    DynastyCommands(FeudalPlugin p,FeudalService s,TownyBridge t){plugin=p;this.s=s;towny=t;}
    private Resident resident(String id){var r=TownyAPI.getInstance().getResident(UUID.fromString(id));require(r!=null,"Спадкоємця немає в Towny.");return r;}
    @Override public void validate(Estate e,String heir) throws Exception {
        var r=resident(heir);require(e.active(),"Володіння неактивне.");
        if(e.kind()==Kind.BARONY){var t=TownyAPI.getInstance().getTown(UUID.fromString(e.id().substring(2)));require(t!=null&&t.hasResident(r),"Спадкоємець міста має бути його жителем.");require(!t.hasNation()||!t.equals(t.getNationOrNull().getCapital()),"Для столичного міста призначайте спадкоємця держави: зміна мера також змінює короля.");}
        else{var n=TownyAPI.getInstance().getNation(UUID.fromString(e.nation().substring(2)));require(n!=null&&n.hasResident(r),"Спадкоємець має бути громадянином держави.");if(e.kind()==Kind.KINGDOM)require(r.isMayor(),"Towny передає корону лише меру міста цієї держави.");}
    }
    @Override public String leader(Estate e) throws Exception {
        if(e.kind()==Kind.KINGDOM){var n=TownyAPI.getInstance().getNation(UUID.fromString(e.id().substring(2)));require(n!=null&&n.hasKing(),"Державу не знайдено.");return n.getKing().getUUID().toString();}
        if(e.kind()==Kind.BARONY){var t=TownyAPI.getInstance().getTown(UUID.fromString(e.id().substring(2)));require(t!=null&&t.hasMayor(),"Місто не знайдено.");return t.getMayor().getUUID().toString();}return s.estate(e.id()).lord();
    }
    @Override public void transfer(Estate e,String heir) throws Exception {
        require(Bukkit.isPrimaryThread(),"Передача влади лише в серверному потоці.");validate(e,heir);var r=resident(heir);
        if(e.kind()==Kind.KINGDOM){var n=TownyAPI.getInstance().getNation(UUID.fromString(e.id().substring(2)));var event=new NationKingChangeEvent(n.getKing(),r);Bukkit.getPluginManager().callEvent(event);require(!event.isCancelled(),"Towny заборонив передачу корони: "+event.getCancelMessage());n.setKing(r);n.save();}
        else if(e.kind()==Kind.BARONY){var t=TownyAPI.getInstance().getTown(UUID.fromString(e.id().substring(2)));var ruler=Bukkit.getPlayer(UUID.fromString(e.lord()));var event=new TownMayorChangeEvent(ruler==null?Bukkit.getConsoleSender():ruler,t.getMayor(),r);Bukkit.getPluginManager().callEvent(event);require(!event.isCancelled(),"Towny заборонив передачу міста: "+event.getCancelMessage());t.setMayor(r);t.save();}
    }
    @Override public boolean online(String person){try{return Bukkit.getPlayer(UUID.fromString(person))!=null;}catch(IllegalArgumentException ignored){return false;}}
    @EventHandler public void join(PlayerJoinEvent e){succession.touch(e.getPlayer().getUniqueId().toString());}
    @EventHandler public void quit(PlayerQuitEvent e){succession.touch(e.getPlayer().getUniqueId().toString());}
    @Override public boolean onCommand(CommandSender p,Command c,String label,String[] a){try{
        if(a.length==1&&a[0].equals("pending")){require(p.hasPermission("empirecraft.feudal.admin"),"Потрібне право адміністратора.");for(var row:succession.pending())plugin.message(p,string(row,"id")+" · "+string(row,"estate")+" · "+string(row,"previous_lord")+" -> "+string(row,"heir"));return true;}
        if(a.length==2&&a[0].equals("cancel-pending")){succession.abandon(new FeudalService.Actor(p instanceof Player x?x.getUniqueId().toString():"консоль",p.hasPermission("empirecraft.feudal.admin")),a[1]);plugin.message(p,"Намір передачі скасовано; чинна влада збережена.");return true;}
        require(p instanceof Player,"Ця команда доступна правителю у грі.");var actor=new FeudalService.Actor(((Player)p).getUniqueId().toString(),false);towny.sync();
        if(a.length==0){plugin.message(p,"/dynasty create <назва> · heir <володіння> <гравець> · accept <володіння> · revoke <володіння>");plugin.message(p,"/dynasty info <володіння> · history <володіння> · abdicate <володіння> confirm");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "create","створити" -> {require(a.length>=2,"/dynasty create <назва>");plugin.message(p,"Династію засновано: "+succession.create(actor,String.join(" ",Arrays.copyOfRange(a,1,a.length))));}
            case "heir","спадкоємець" -> {require(a.length==3,"/dynasty heir <володіння> <гравець>");String id=s.resolve(a[1]).id();succession.nominate(actor,id,towny.residentId(a[2]));plugin.message(p,"Спадкоємець має погодитися: /dynasty accept "+id);}
            case "accept","прийняти" -> {require(a.length==2,"/dynasty accept <володіння>");succession.accept(actor,s.resolve(a[1]).id());plugin.message(p,"Згоду на спадкування збережено.");}
            case "revoke","відкликати" -> {require(a.length==2,"/dynasty revoke <володіння>");succession.revoke(actor,s.resolve(a[1]).id());plugin.message(p,"Призначення відкликано.");}
            case "abdicate","зректися" -> {require(a.length==3&&a[2].equals("confirm"),"Передача чинному спадкоємцю: /dynasty abdicate <володіння> confirm");succession.abdicate(actor,s.resolve(a[1]).id());towny.sync();plugin.message(p,"Владу передано. Землі, договори й скарбниця збережені.");}
            case "info","інфо" -> {require(a.length==2,"/dynasty info <володіння>");var e=s.resolve(a[1]);var h=succession.heir(e.id());plugin.message(p,e.name()+" · правитель: "+towny.playerName(e.lord()));plugin.message(p,h==null?"Спадкоємця не призначено.":"Династія: "+string(h,"house_name")+"; спадкоємець: "+towny.playerName(string(h,"heir"))+"; погодився: "+(bool(h,"accepted")?"так":"ні")+"; чинний: "+Objects.equals(e.lord(),string(h,"lord")));plugin.message(p,"Остання активність: "+Instant.ofEpochMilli(succession.lastSeen(e.lord()))+"; поріг: "+plugin.getConfig().getInt("dynasty.inactivity-days",30)+" днів.");}
            case "history","історія" -> {require(a.length==2,"/dynasty history <володіння>");for(var row:succession.history(s.resolve(a[1]).id()))plugin.message(p,Instant.ofEpochMilli(number(row,"at_time"))+" · "+string(row,"event")+" · "+string(row,"detail"));}
            default -> throw new IllegalArgumentException("/dynasty — довідка.");
        }
    }catch(IllegalArgumentException ex){plugin.message(p,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Спадкування",ex);plugin.message(p,"§cПередачу не завершено. Намір збережено в журналі; перевірте Towny.");}return true;}
    @Override public List<String> onTabComplete(CommandSender p,Command c,String alias,String[] a){var v=new ArrayList<String>();if(a.length==1)v.addAll(List.of("create","heir","accept","revoke","abdicate","info","history"));if(a.length==2&&!a[0].equals("create"))try{for(var e:s.estates())if(e.active())v.add(e.id());}catch(Exception ignored){}if(a.length==3&&a[0].equals("heir"))for(var x:Bukkit.getOnlinePlayers())v.add(x.getName());if(a.length==3&&a[0].equals("abdicate"))v.add("confirm");String prefix=a[a.length-1].toLowerCase(Locale.ROOT);return v.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
