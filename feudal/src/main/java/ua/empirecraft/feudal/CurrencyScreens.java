package ua.empirecraft.feudal;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Server-owned UI sessions. A quote is bound to its player, parameters, lifetime and one use. */
public final class CurrencyScreens {
    final FeudalPlugin plugin;final FeudalService s;final TownyBridge towny;final CurrencyMarket market;
    record Permit(String action,String from,String to,String amount,long minimum,long expires,String minOut,String settings,String stamp){}
    final Map<UUID,Map<String,Permit>> permits=new HashMap<>();
    final Map<UUID,Map<String,Object>> opens=new LinkedHashMap<>();
    final Map<UUID,Long> throttles=new HashMap<>();
    public CurrencyScreens(FeudalPlugin p,FeudalService s,TownyBridge t,CurrencyMarket m){plugin=p;this.s=s;towny=t;market=m;}
    Actor actor(Player p){return new Actor(p.getUniqueId().toString(),p.hasPermission("empirecraft.feudal.admin"));}
    public String own(Player p)throws SQLException {
        String person=p.getUniqueId().toString();
        for(var c:s.db.query("SELECT code,issuer FROM currencies WHERE backing='GOLD' ORDER BY code"))if(market.manages(actor(p),string(c,"issuer")))return string(c,"code");
        return "";
    }
    public String estate(Player player)throws Exception {
        String person=player.getUniqueId().toString();
        var managed=s.estates().stream().filter(Estate::active).filter(e->Objects.equals(e.lord(),person)).sorted(Comparator.comparingInt((Estate e)->e.kind().level).reversed()).toList();
        for(var e:managed)if(market.eligible(e.id()))return e.id();
        for(var e:s.estates())if(e.active() && market.eligible(e.id()) && market.manages(actor(player),e.id()))return e.id();
        String code=own(player);if(!code.isEmpty())return string(s.currency(code),"issuer");
        throw new IllegalArgumentException("Потрібна незалежна держава належного рівня та повноваження мера/Банкіра.");
    }
    public void open(Player p,String mode,String from,String to){
        require(p.hasPermission("empirecraft.feudal.use"),"Немає доступу.");
        opens.put(p.getUniqueId(),Map.of("player",p.getUniqueId().toString(),"mode",mode,"from",from,"to",to,"expires",s.now()+10_000));
    }
    /** Polled on the same server thread by the Forge bridge; no plugin-to-mod classloader dependency. */
    public List<Map<String,Object>> poll(){
        var result=new ArrayList<>(opens.values());opens.clear();return result;
    }
    public void expire(){
        permits.values().forEach(m->m.values().removeIf(p->p.expires()<s.now()));permits.entrySet().removeIf(e->e.getValue().isEmpty());
        throttles.entrySet().removeIf(e->e.getValue()<s.now()-60_000);
        opens.entrySet().removeIf(e->{if(((Number)e.getValue().get("expires")).longValue()>s.now())return false;var p=Bukkit.getPlayer(e.getKey());if(p!=null)plugin.message(p,"Для цього екрана оновіть клієнтський і серверний міст Empirecraft до 1.3.0.");return true;});
    }
    String permit(Player p,String action,String from,String to,String amount,long minimum){
        var map=permits.computeIfAbsent(p.getUniqueId(),id->new HashMap<>());map.clear();String token=UUID.randomUUID().toString();
        map.put(token,new Permit(action,from,to,amount,minimum,s.now()+30_000,"","",""));return token;
    }
    Permit consume(Player p,String token,String action,String from,String to,String amount){
        var map=permits.get(p.getUniqueId());Permit found=map==null?null:map.remove(token);
        require(found!=null && found.expires()>=s.now() && found.action().equals(action) && found.from().equals(from) && found.to().equals(to) && found.amount().equals(amount),"Підтвердження застаріло або вже використане; повторіть розрахунок.");return found;
    }
    String approve(Player p,String action,String from,String to,String amount,String minimum,String settings,String stamp){
        String token=permit(p,action,from,to,amount,0);permits.get(p.getUniqueId()).put(token,new Permit(action,from,to,amount,0,s.now()+30000,minimum,settings,stamp));return token;
    }
    Permit consumeApproved(Player p,String token,String action,String from,String to,String amount,String minimum,String settings){
        Permit approved=consume(p,token,action,from,to,amount);require(approved.minOut().equals(minimum)&&approved.settings().equals(settings),"Параметри підтвердження змінилися.");return approved;
    }
    public Map<String,Object> request(UUID id,String action,String from,String to,String amount,String minimum,String settings,String period,String token){
        require(Bukkit.isPrimaryThread(),"Валютний облік доступний лише в головному потоці.");Player p=Bukkit.getPlayer(id);require(p!=null && p.isOnline() && p.hasPermission("empirecraft.feudal.use"),"Гравець недоступний.");
        var result=new LinkedHashMap<String,Object>();result.put("action",action);
        try{
            require(action.length()<=20 && from.length()<=8 && to.length()<=8 && amount.length()<=32 && token.length()<=40 && minimum.length()<=32 && settings.length()<=256,"Завеликий запит.");
            long previous=throttles.getOrDefault(id,0L);require(s.now()-previous>=250,"Зачекайте перед наступним запитом.");throttles.put(id,s.now());CurrencyMarket.period(period);
            towny.sync();market.exchange.tick();Actor actor=actor(p);String owner="p:"+id;
            if(Set.of("buy","redeem","print","cancel","resume","policy-save","refund").contains(action)){
                Permit approved=consumeApproved(p,token,action,from,to,amount,minimum,settings);
                switch(action){
                    case "buy"->{market.exchange.execute(actor,from,to,amount,new BigDecimal(minimum),approved.stamp());result.put("message","Обмін виконано.");}
                    case "redeem"->{String request=market.exchange.request(actor,owner,from,money(amount),gold(minimum),approved.stamp());result.put("message","Погашення в черзі: "+request);}
                    case "print"->{market.start(actor,to,Long.parseLong(amount));result.put("message","Чергу друку запущено.");}
                    case "cancel"->{market.cancel(actor,to);result.put("message","Друк скасовано.");}
                    case "resume"->{market.resume(actor,to);result.put("message","Друк продовжено.");}
                    case "policy-save"->{market.exchange.schedule(actor,to,CurrencyExchange.parse(settings));result.put("message","Зміна набере чинності через 1 годину.");}
                    case "refund"->{market.exchange.cancel(actor,settings);result.put("message","Монети заявки повернуто.");}
                }
            }else require(Set.of("view","quote","preview","cancel-preview","resume-preview","policy-quote","refund-quote","log").contains(action),"Невідома дія.");
            var codes=new ArrayList<String>();codes.add("@GOLD");for(var c:s.db.query("SELECT code FROM currencies WHERE backing='GOLD' ORDER BY code"))codes.add(string(c,"code"));
            if(!codes.contains(from))from="@GOLD";if(!codes.contains(to))to=codes.size()>1?codes.get(1):"@GOLD";
            String own=own(p);result.put("own",own);result.put("codes",codes);result.put("from",from);result.put("to",to);result.put("period",period);result.put("now",s.now());result.put("gold",show(s.currencies.goldBalance(owner)));result.put("requests",market.exchange.requests(actor));
            var balances=new LinkedHashMap<String,Object>();for(String code:codes)if(!code.equals("@GOLD"))balances.put(code,format(s.balance(owner,code)));result.put("balances",balances);
            if(!from.equals("@GOLD"))result.put("liquidityA",market.exchange.liquidity(from));if(!to.equals("@GOLD"))result.put("liquidityB",market.exchange.liquidity(to));
            result.put("history",market.history(from,to,period));String context=to.equals("@GOLD")?from:to;
            if(!context.equals("@GOLD")){
                var c=s.currency(context);String estate=string(c,"issuer");boolean manage=market.manages(actor,estate);result.put("manage",manage);result.put("issuer",s.estate(estate).name());result.put("interval",CurrencyMarket.interval(market.politics.state(estate).stage())/1000);result.put("eligible",market.eligible(estate));
                result.put("reserve",show(s.currencies.reserve(context)));result.put("supply",format(number(c,"supply")));result.put("value",number(c,"supply")==0?"—":show(s.currencies.value(context)));result.put("treasury",format(s.balance("e:"+estate,context)));result.put("settings",market.exchange.settings(context));
                long cursor=action.equals("log")&&!settings.isEmpty()?Long.parseLong(settings):0;result.put("journal",market.exchange.journal(context,cursor));
                if(manage){var job=market.job(context);if(job!=null)result.put("job",Map.of("remaining",number(job,"remaining"),"issued",number(job,"issued"),"status",string(job,"status"),"seconds",number(job,"remaining")==0 || Set.of("DONE","CANCELLED").contains(string(job,"status"))?0L:("PAUSED".equals(string(job,"status"))?number(job,"remaining")*CurrencyMarket.interval(market.politics.state(estate).stage())/1000:Math.max(0,(number(job,"next_due")-s.now()+999)/1000)+Math.max(0,number(job,"remaining")-1)*CurrencyMarket.interval(market.politics.state(estate).stage())/1000)));}
            }
            if(action.equals("quote")){
                var q=market.exchange.quote(from,to,amount);require(from.equals("@GOLD")?s.currencies.goldBalance(owner).compareTo(gold(amount))>=0:s.balance(owner,from)>=money(amount),"Недостатньо коштів.");
                String min=show(CurrencyExchange.minimum(minimum,q));result.put("trade",q.view());result.put("minimum",min);result.put("nextAction",to.equals("@GOLD")?"redeem":"buy");result.put("token",approve(p,to.equals("@GOLD")?"redeem":"buy",from,to,amount,min,settings,q.stamp()));
            }else if(action.equals("preview")){
                market.manage(actor,to);require(amount.matches("[1-9][0-9]{0,9}"),"Друк потребує цілої кількості монет.");result.put("preview",market.preview(to,Long.parseLong(amount)));result.put("nextAction","print");result.put("token",approve(p,"print",from,to,amount,minimum,settings,""));
            }else if(action.equals("policy-quote")){
                market.manage(actor,to);var next=CurrencyExchange.parse(settings);result.put("policyPreview",next.view());result.put("nextAction","policy-save");result.put("token",approve(p,"policy-save",from,to,amount,minimum,settings,""));
            }else if(action.equals("refund-quote")){
                require(s.db.one("SELECT id FROM exchange_redemptions WHERE id=? AND person=? AND status='PENDING'",settings,id.toString())!=null,"Заявку не знайдено.");result.put("nextAction","refund");result.put("token",approve(p,"refund",from,to,amount,minimum,settings,""));
            }else if(action.endsWith("-preview")){
                market.manage(actor,to);String next=action.substring(0,action.indexOf('-'));result.put("nextAction",next);result.put("token",approve(p,next,from,to,amount,minimum,settings,""));
            }
        }catch(Exception ex){result.put("error",ex instanceof IllegalArgumentException?ex.getMessage():"Операцію не завершено; перевірте суму та стан валюти.");if(!(ex instanceof IllegalArgumentException))plugin.failure("Валютний екран",ex);}
        return result;
    }
}
