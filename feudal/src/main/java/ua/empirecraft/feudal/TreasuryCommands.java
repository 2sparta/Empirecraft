package ua.empirecraft.feudal;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;

/** Native Towny addon: /t treasury, never replaces Towny's own /t dispatcher. */
final class TreasuryCommands implements CommandExecutor,TabCompleter {
    final FeudalPlugin plugin;final FeudalService s;final TownyBridge towny;
    TreasuryCommands(FeudalPlugin plugin,FeudalService s,TownyBridge towny){this.plugin=plugin;this.s=s;this.towny=towny;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            require(sender instanceof Player,"Команда доступна гравцю.");Player p=(Player)sender;towny.sync();s.market.politics.syncDomains();
            var resident=TownyAPI.getInstance().getResident(p);require(resident!=null && resident.hasTown(),"Ви не належите до міста.");String id=TownyBridge.townId(resident.getTownOrNull());
            var actor=new FeudalService.Actor(p.getUniqueId().toString(),p.hasPermission("empirecraft.feudal.admin"));String owner="p:"+p.getUniqueId(),treasury="e:"+id,code=s.market.treasuryCurrency(id);
            String sub=args.length==0?"balance":args[0].toLowerCase(Locale.ROOT);
            if(sub.equals("gold")){
                s.controlTreasury(actor,s.estate(id));String mode=args.length>1?args[1]:"balance";
                if(mode.equals("withdraw")){require(args.length==3,"/t treasury gold withdraw <сума_ЗО>");s.currencies.transferGold(actor,treasury,owner,CurrencyService.gold(args[2]));}
                else require(mode.equals("balance"),"Нове золото вноситься до особистого банку або резерву держави; казна міста приймає лише "+code);
                plugin.message(p,"Золотий залишок казни: "+CurrencyService.show(s.currencies.goldBalance(treasury))+" ЗО.");
            }else if(sub.equals("banker") || sub.equals("банкір") || sub.equals("банків")){
                require(args.length==3,"/t treasury banker add|remove <гравець>");require(Set.of("add","remove").contains(args[1]),"Дія: add або remove.");s.market.banker(actor,id,towny.residentId(args[2]),args[1].equals("add"));plugin.message(p,"Роль Банкір оновлено.");
            }else if(Set.of("deposit","withdraw").contains(sub)){
                require(args.length>=2 && args.length<=3,"/t treasury deposit|withdraw <сума> [код]");String selected=args.length==3?args[2].toUpperCase(Locale.ROOT):code;
                s.transfer(actor,sub.equals("deposit")?owner:treasury,sub.equals("deposit")?treasury:owner,selected,money(args[1]));plugin.message(p,"Казну оновлено.");
            }else if(sub.equals("balance") || sub.equals("info")){
                plugin.message(p,"Казна міста "+resident.getTownOrNull().getName()+"; валюта: "+code+".");
                var balances=s.db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0 ORDER BY code",treasury);if(balances.isEmpty())plugin.message(p,"Баланс: 0.00 "+code);for(var row:balances)plugin.message(p,format(number(row,"balance"))+" "+string(row,"code"));
            }else plugin.message(p,"/t treasury [balance|deposit <сума> [код]|withdraw <сума> [код]|banker add|remove <гравець>]");
        }catch(IllegalArgumentException ex){plugin.message(sender,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Казна міста",ex);plugin.message(sender,"§cОперацію не завершено.");}return true;
    }
    public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return a.length==1?List.of("balance","deposit","withdraw","banker","gold").stream().filter(x->x.startsWith(a[0])).toList():a.length==2 && a[0].equals("banker")?List.of("add","remove"):List.of();}
}
