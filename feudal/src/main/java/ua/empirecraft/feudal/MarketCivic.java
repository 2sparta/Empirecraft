package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.MarketCommands.*;

/** Vanilla signs are physical entry points to the existing native market screen. */
public final class MarketCivic implements Listener {
    final MarketCommands c;final CityMarketService m;
    MarketCivic(MarketCommands c){this.c=c;m=c.m;m.notices.markerValid=this::valid;m.fishing.specLabel=spec->{try{var item=ItemTransactions.prototype(string(m.spec(spec),"item"));return item.getType().getKey().toString()+(item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?" · "+item.getItemMeta().getDisplayName():"");}catch(Exception error){throw new IllegalStateException(error);}};}
    boolean valid(Map<String,Object> board){
        try{if(string(board,"world").isBlank())return false;World w=Bukkit.getWorld(UUID.fromString(string(board,"world")));int x=(int)number(board,"x"),y=(int)number(board,"y"),z=(int)number(board,"z");return w!=null&&(!w.isChunkLoaded(x>>4,z>>4)||w.getBlockAt(x,y,z).getState() instanceof Sign);}catch(Exception error){return false;}
    }
    String at(Block block)throws Exception {if(block==null)return "";var row=m.db.one("SELECT market FROM cm_boards WHERE world=? AND x=? AND y=? AND z=?",block.getWorld().getUID().toString(),block.getX(),block.getY(),block.getZ());return row==null?"":string(row,"market");}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        try{String market=at(e.getClickedBlock());if(market.isEmpty())return;e.setCancelled(true);m.notices.present(market,place(e.getPlayer()));c.screens.openSection(e.getPlayer(),"notice");}catch(Exception error){c.plugin.message(e.getPlayer(),error.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void edit(SignChangeEvent e){try{if(!at(e.getBlock()).isEmpty())e.setCancelled(true);}catch(Exception error){e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protect(BlockBreakEvent e){try{String market=at(e.getBlock());if(!market.isEmpty())m.notices.mayor(actor(e.getPlayer()),market);}catch(Exception error){e.setCancelled(true);c.plugin.message(e.getPlayer(),error.getMessage());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){try{String market=at(e.getBlock());if(!market.isEmpty())m.db.atomic(()->{m.notices.unbind(market);return null;});}catch(Exception error){c.plugin.getLogger().warning("Board break: "+error.getMessage());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(block->{try{return !at(block).isEmpty();}catch(Exception error){return true;}});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(block->{try{return !at(block).isEmpty();}catch(Exception error){return true;}});}
    void tick()throws Exception {for(var board:m.db.query("SELECT * FROM cm_boards WHERE world<>''"))if(!valid(board))m.db.atomic(()->{m.notices.unbind(string(board,"market"));return null;});}
    String stamp(Player p,String[] a)throws Exception {
        if(a[0].equals("board")){String mode=arg(a,1);if(Set.of("approve","reject","remove").contains(mode))return hash(m.notices.notice(arg(a,2)).toString());String market=Set.of("configure","herald","place").contains(mode)?arg(a,2):c.current(p);return hash(m.notices.settings(market).toString());}
        return a[1].equals("create")?hash(m.market(c.current(p)).toString()):hash(m.fishing.event(arg(a,2)).toString());
    }
    boolean read(Player p,String[] a)throws Exception {
        String sub=a[0],mode=a.length>1?a[1]:"menu";
        if(mode.equals("menu")){c.screens.openSection(p,sub.equals("board")?"notice":"fishing");return true;}
        if(sub.equals("board")){
            if(mode.equals("event")){String id=arg(a,2);var event=m.fairs.fair(id);m.notices.present(string(event,"market"),place(p));require(Set.of("ANNOUNCED","ACTIVE").contains(string(event,"state")),LocalText.tr("Подія ще не оголошена або вже закрита."));if(string(event,"kind").equals("FISHING"))return read(p,new String[]{"fishing","view",id});c.say(p,string(event,"name")+" · "+time(number(event,"starts"))+" — "+time(number(event,"ends")));c.say(p,string(event,"theme"));c.say(p,string(event,"catalog"));return true;}
            if(mode.equals("list")){String market=c.current(p);var board=m.notices.settings(market);m.notices.present(market,place(p));c.say(p,LocalText.tr("Дошка: плата ")+format(number(board,"fee"))+" "+string(board,"code")+LocalText.tr("; перевірка ")+bool(board,"approval"));for(var row:m.notices.publicEntries(market,place(p),a.length>2?intArg(a,2):1))c.say(p,string(row,"id")+" · "+string(row,"title"));events(p,market);return true;}
            if(mode.equals("read")){var row=m.notices.read(actor(p),arg(a,2),place(p));c.say(p,string(row,"title")+" · "+c.towny.playerName(string(row,"author"))+" · "+string(row,"state"));if(!string(row,"reason").isBlank())c.say(p,string(row,"reason"));String body=string(row,"body");for(int i=0;i<body.length();i+=1500)c.say(p,body.substring(i,Math.min(body.length(),i+1500)));return true;}
            if(mode.equals("review")){String market=c.current(p);m.notices.present(market,place(p));require(m.notices.moderator(actor(p),market),LocalText.tr("Потрібна посада міського вісника або мера."));for(var row:m.db.query("SELECT id,title FROM cm_notices WHERE market=? AND state='PENDING' AND ends>? ORDER BY created LIMIT 100",market,m.s.now()))c.say(p,string(row,"id")+" · "+string(row,"title"));return true;}
        }else {
            if(mode.equals("list")){for(var row:m.db.query("SELECT id,name,state,starts FROM cm_fairs WHERE kind='FISHING' AND state IN('ANNOUNCED','ACTIVE','FINISHED') ORDER BY starts DESC LIMIT 50"))c.say(p,string(row,"id")+" · "+string(row,"name")+" · "+string(row,"state")+" · "+time(number(row,"starts")));return true;}
            if(mode.equals("view")){String id=arg(a,2);var row=m.fishing.event(id);if(!Set.of("ANNOUNCED","ACTIVE","FINISHED").contains(string(row,"state")))m.manage(actor(p),string(row,"market"));for(String line:m.fishing.description(id).split("\n"))c.say(p,line);int rank=0;for(var score:m.fishing.scores(id)){long value=number(score,"score");String result=string(row,"rule").equals("COUNT")?Long.toString(value):java.math.BigDecimal.valueOf(value,3).stripTrailingZeros().toPlainString()+" g";c.say(p,"#"+(++rank)+" · "+c.towny.playerName(string(score,"person"))+" · "+result+" · "+number(score,"catches"));}return true;}
        }return false;
    }
    void events(Player p,String market)throws Exception {for(var row:m.db.query("SELECT id,kind,name,starts,ends FROM cm_fairs WHERE market=? AND state IN('ANNOUNCED','ACTIVE') AND ends>? ORDER BY starts LIMIT 50",market,m.s.now()))c.say(p,(string(row,"kind").equals("FISHING")?LocalText.tr("Рибальський турнір: "):LocalText.tr("Ярмарок: "))+string(row,"name")+" · "+time(number(row,"starts"))+" · "+string(row,"id"));}
    void quote(Player p,String[] a)throws Exception {
        if(a[0].equals("board")&&a[1].equals("post")){var board=m.notices.settings(c.current(p));m.notices.present(c.current(p),place(p));c.say(p,LocalText.tr("Плата за публікацію: ")+format(number(board,"fee"))+" "+string(board,"code")+LocalText.tr("; попередня перевірка: ")+bool(board,"approval")+LocalText.tr(". Відхилення або прострочення перевірки повертає плату."));}
        if(a[0].equals("fishing")&&a[1].equals("join")){var row=m.fishing.event(arg(a,2));c.say(p,string(row,"name")+LocalText.tr(" · Вступний внесок: ")+format(number(row,"entry_fee"))+" "+string(row,"entry_code")+LocalText.tr(". Внески отримує перше місце. Скасування або відсутність залікового улову повертає їх усім учасникам."));}
    }
    String mutate(Player p,MarketExtensionCommands.Pending q)throws Exception {
        String[] a=q.args();String mode=arg(a,1),request=q.id();var who=actor(p);
        if(a[0].equals("board"))return switch(mode){
            case "configure"->m.notices.configure(who,request,arg(a,2),money(arg(a,3)),code(a,4),flag(a,5));
            case "herald"->{String person=arg(a,3);try{UUID.fromString(person);}catch(IllegalArgumentException name){person=c.towny.residentId(person);}yield m.notices.appoint(who,request,arg(a,2),person,flag(a,4));}
            case "place"->{Block block=p.getTargetBlockExact(8);require(block!=null&&block.getState() instanceof Sign,LocalText.tr("Дивіться на звичайну табличку в будівлі базару."));String market=arg(a,2);String result=m.notices.bind(who,request,market,new Place(block.getWorld().getUID().toString(),block.getX(),block.getY(),block.getZ()));Sign sign=(Sign)block.getState();sign.setWaxed(true);sign.setLine(0,LocalText.tr("Дошка оголошень"));sign.setLine(1,string(m.market(market),"name"));sign.setLine(2,LocalText.tr("Натисніть для огляду"));sign.setLine(3,"Empirecraft");sign.update(true,false);yield result;}
            case "post"->{String market=c.current(p);yield m.notices.submit(who,request,market,place(p),arg(a,3),String.join(" ",Arrays.copyOfRange(a,4,a.length)),intArg(a,2),number(m.notices.settings(market),"version"));}
            case "approve","reject"->m.notices.review(who,request,arg(a,2),mode.equals("approve"),a.length>3?String.join(" ",Arrays.copyOfRange(a,3,a.length)):"");
            case "remove"->m.notices.remove(who,request,arg(a,2),a.length>3?String.join(" ",Arrays.copyOfRange(a,3,a.length)):"");
            default->throw new IllegalArgumentException(LocalText.tr("Дошка: menu, list, read, post, review, approve, reject, remove, place, configure, herald."));};
        return switch(mode){
            case "create"->m.fishing.create(who,request,c.current(p),place(p),arg(a,2),arg(a,3),MarketExtensionCommands.date(arg(a,4)),intArg(a,5),MarketFishing.Rule.valueOf(code(a,6)));
            case "fee"->m.fishing.entryFee(who,request,arg(a,2),money(arg(a,3)),code(a,4));
            case "prize_money"->m.fishing.money(who,request,arg(a,2),intArg(a,3),code(a,4),money(arg(a,5)),flag(a,6));
            case "prize_item"->{String op=m.fishing.items(who,request,arg(a,2),intArg(a,3),q.spec(),numberArg(a,4),place(p),c.take(p,q.spec(),numberArg(a,4)));c.apply(op);yield op;}
            case "announce"->m.fishing.announce(who,request,arg(a,2));
            case "join"->m.fishing.join(who,request,arg(a,2));
            case "cancel"->m.fishing.cancel(who,request,arg(a,2));
            default->throw new IllegalArgumentException(LocalText.tr("Турнір: menu, list, view, create, fee, prize_money, prize_item, announce, join, cancel, letter."));};
    }
}
