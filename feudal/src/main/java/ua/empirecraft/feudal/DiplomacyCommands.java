package ua.empirecraft.feudal;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import java.time.Instant;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

final class DiplomacyCommands implements CommandExecutor,TabCompleter,Listener {
    private final FeudalPlugin plugin;private final FeudalService s;private final TownyBridge towny;private final DiplomacyService diplomacy;
    DiplomacyCommands(FeudalPlugin p,FeudalService s,TownyBridge t,DiplomacyService d){plugin=p;this.s=s;towny=t;diplomacy=d;}
    private void show(CommandSender p,Map<String,Object> d) throws Exception {
        plugin.message(p,string(d,"id")+" · "+DiplomacyService.Type.valueOf(string(d,"kind")).label+" · "+s.estate(string(d,"a")).name()+" ↔ "+s.estate(string(d,"b")).name()+" · "+string(d,"status"));
        plugin.message(p,"Умови: "+string(d,"terms")+"; строк: "+number(d,"days")+" днів; підписи: "+towny.playerName(string(d,"signer_a"))+" / "+towny.playerName(string(d,"signer_b")));
        plugin.message(p,number(d,"ends")>0?"Початок: "+Instant.ofEpochMilli(number(d,"starts"))+"; кінець: "+Instant.ofEpochMilli(number(d,"ends")):"Прийняти до: "+Instant.ofEpochMilli(number(d,"proposal_expires")));
        if(string(d,"reason")!=null)plugin.message(p,"Завершено: "+Instant.ofEpochMilli(number(d,"ended"))+"; "+string(d,"reason"));
    }
    @Override public boolean onCommand(CommandSender p,Command command,String label,String[] a){try{
        towny.sync();diplomacy.tick();var actor=new FeudalService.Actor(p instanceof Player x?x.getUniqueId().toString():"консоль",p.hasPermission("empirecraft.feudal.admin"));
        if(a.length==0){plugin.message(p,"/diplomacy offer <своя_держава> <інша_держава> <союз|ненапад|торгівля|прохід> <дні> <умови>");plugin.message(p,"/diplomacy sign <ID> · end <ID> <причина> · info <ID> · history <ID> · list <держава>");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "offer","запропонувати" -> {require(a.length>=6,"Укажіть держави, тип, строк та умови.");String id=diplomacy.propose(actor,s.resolve(a[1]).id(),s.resolve(a[2]).id(),DiplomacyService.Type.parse(a[3]),Integer.parseInt(a[4]),String.join(" ",Arrays.copyOfRange(a,5,a.length)));plugin.message(p,"Ваш підпис збережено. Інша держава підписує: /diplomacy sign "+id);}
            case "sign","підписати" -> {require(a.length==2,"/diplomacy sign <ID>");diplomacy.sign(actor,a[1]);show(p,diplomacy.get(a[1]));}
            case "end","розірвати" -> {require(a.length>=3,"/diplomacy end <ID> <причина>");diplomacy.end(actor,a[1],String.join(" ",Arrays.copyOfRange(a,2,a.length)));show(p,diplomacy.get(a[1]));}
            case "info","інфо" -> {require(a.length==2,"/diplomacy info <ID>");show(p,diplomacy.get(a[1]));}
            case "history","історія" -> {require(a.length==2,"/diplomacy history <ID>");for(var e:diplomacy.history(a[1]))plugin.message(p,Instant.ofEpochMilli(number(e,"at_time"))+" · "+towny.playerName(string(e,"actor"))+" · "+string(e,"event")+" · "+string(e,"detail"));}
            case "list","список" -> {require(a.length==2,"/diplomacy list <держава>");for(var e:diplomacy.list(s.resolve(a[1]).id()))show(p,e);}
            default -> throw new IllegalArgumentException("/diplomacy — довідка.");
        }
    }catch(IllegalArgumentException ex){plugin.message(p,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Дипломатія",ex);plugin.message(p,"§cНе вдалося зберегти договір.");}return true;}
    private String nation(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null||!r.hasNation()?null:TownyBridge.nationId(r.getNationOrNull());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void attack(EntityDamageByEntityEvent e){
        if(!(e.getEntity() instanceof Player target))return;Player attacker=e.getDamager() instanceof Player p?p:e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player p?p:null;if(attacker==null)return;
        try{if(diplomacy.peaceful(nation(attacker),nation(target))){e.setCancelled(true);attacker.sendMessage("§eДержави мають чинний союз або договір про ненапад.");}}catch(Exception ex){e.setCancelled(true);plugin.failure("Перевірка ненападу",ex);}
    }
    @Override public List<String> onTabComplete(CommandSender p,Command c,String alias,String[] a){var values=new ArrayList<String>();if(a.length==1)values.addAll(List.of("offer","sign","end","info","history","list"));if(a.length==4&&a[0].equals("offer"))values.addAll(List.of("союз","ненапад","торгівля","прохід"));try{if((a.length==2&&(a[0].equals("offer")||a[0].equals("list")))||(a.length==3&&a[0].equals("offer")))for(var e:s.estates())if(e.active()&&e.kind()==Kind.KINGDOM)values.add("держава:"+e.name().replace(' ','_'));}catch(Exception ignored){}String prefix=a[a.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
