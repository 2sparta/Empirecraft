package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.math.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Bukkit-facing UI and a versioned JDK-only port for the server-side Forge addon. */
public final class ArmyController implements Listener,CommandExecutor,TabCompleter {
    final FeudalPlugin plugin; final FeudalService s; final ArmyService army; final ProvisionController provisions; final TownyBridge towny;
    volatile ArmyPolicy.View view=ArmyPolicy.empty();
    volatile Map<String,Boolean> claims=Map.of(),worlds=Map.of(); volatile int cell=16;
    volatile Map<String,String> factionEstates=Map.of();volatile List<Map<String,Object>> factions=List.of();
    final Map<String,Long> retaliation=new ConcurrentHashMap<>();
    boolean refreshQueued;
    @FunctionalInterface interface Action{void run(Player p)throws Exception;}
    static final class Menu implements InventoryHolder{
        final UUID owner;final Inventory inv;final Map<Integer,Action> actions=new HashMap<>();Menu(Player p,String title){owner=p.getUniqueId();inv=Bukkit.createInventory(this,54,title);}public Inventory getInventory(){return inv;}
    }
    public ArmyController(FeudalPlugin plugin,ArmyService army,ProvisionController provisions,TownyBridge towny){this.plugin=plugin;this.army=army;s=army.s;this.provisions=provisions;this.towny=towny;if(s.contractBuilder!=null)s.contractBuilder.changed=this::invalidate;army.canDeclare=(a,b)->System.currentTimeMillis()-view.updated()<5000&&!view.friendly(a,b)&&!view.related(view.pacts(),a,b);}
    void invalidate(){view=ArmyPolicy.empty();if(refreshQueued)return;refreshQueued=true;Bukkit.getScheduler().runTask(plugin,()->{try{towny.sync();army.tick();refresh();}catch(Exception e){plugin.failure("Оновлення дипломатії рекрутів",e);}finally{refreshQueued=false;}});}
    double hungerRate(){double rate=plugin.getConfig().getDouble("army.hunger-per-minute",2);return Double.isFinite(rate)?Math.max(.01,Math.min(100,rate)):2;}
    void main(){require(Bukkit.isPrimaryThread(),"Зміна армії дозволена лише в серверному потоці.");}
    public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,()->{try{army.tick();refresh();}catch(Exception e){plugin.failure("Облік рекрутів",e);}},1,20);}
    void access(Player p,String estate)throws Exception{require(p.hasPermission("empirecraft.feudal.use"),"Немає доступу.");require(p.hasPermission("empirecraft.feudal.admin")||army.citizen(p.getUniqueId().toString(),estate)||s.controls(new Actor(p.getUniqueId().toString(),false),s.estate(estate)),"Переглядати армію можуть громадяни володіння.");}
    static String text(Map<String,Object> d,String key){Object o=d.get(key);return o==null?null:o.toString();}
    static boolean flag(Map<String,Object> d,String key){return Boolean.TRUE.equals(d.get(key));}
    static long num(Map<String,Object> d,String key){return ((Number)d.get(key)).longValue();}
    String home(String owner){if(owner==null)return null;var r=TownyAPI.getInstance().getResident(UUID.fromString(owner));return r==null||r.getTownOrNull()==null?null:TownyBridge.townId(r.getTownOrNull());}
    public Map<String,Object> request(String action,Map<String,Object> data)throws Exception{
        if(action.equals("protocol"))return Map.of("protocol",2,"hungerPerMinute",hungerRate(),"starveEverySeconds",Math.max(1,plugin.getConfig().getInt("army.starve-every-seconds",10)),"starvation",true);
        if(action.equals("policy")){
            var snap=view;String source=text(data,"source"),target=text(data,"target");String a=snap.estate(source,text(data,"ownerA"),flag(data,"recruitA"));
            boolean defensive=retaliation.getOrDefault(a+"|"+target,0L)>System.currentTimeMillis();
            boolean pvp=territory(data,"worldA","xA","zA")&&territory(data,"worldB","xB","zB");
            var r=snap.decide(source,text(data,"ownerA"),flag(data,"recruitA"),target,text(data,"ownerB"),flag(data,"recruitB"),flag(data,"civilian"),flag(data,"monster"),pvp,defensive,System.currentTimeMillis());
            var out=new HashMap<String,Object>();out.put("allow",r.allow());out.put("reason",r.reason());out.put("a",r.attacker());out.put("b",r.target());return out;
        }
        if(action.equals("canCommand"))return Map.of("allow",System.currentTimeMillis()-view.updated()<5000&&view.commands(text(data,"actor"),text(data,"unit")));
        if(action.equals("factions"))return Map.of("factions",factions,"updated",view.updated());
        if(action.equals("relation")){
            var snap=view;String a=factionEstates.get(text(data,"a")),b=factionEstates.get(text(data,"b"));boolean managed=a!=null||b!=null;
            return Map.of("managed",managed,"relation",a!=null&&b!=null&&(snap.friendly(a,b)||snap.related(snap.pacts(),a,b))?"ALLY":a!=null&&b!=null&&snap.related(snap.wars(),a,b)?"ENEMY":"NEUTRAL");
        }
        main();
        switch(action){
            case "observe"->{String id=text(data,"unit"),owner=text(data,"owner");var known=army.unit(id);String desiredOwner=known!=null&&string(known,"pending_owner")!=null?string(known,"pending_owner"):owner;army.observe(id,owner,home(desiredOwner),((Number)data.get("hunger")).doubleValue());var u=army.unit(id);var result=new HashMap<String,Object>();result.put("owner",string(u,"owner"));result.put("estate",string(u,"estate"));result.put("home",string(u,"home"));String allegiance=string(u,"estate")==null?string(u,"home"):string(u,"estate");result.put("faction",allegiance==null?null:army.faction(allegiance));result.put("active",allegiance!=null&&s.estate(allegiance).active());result.put("alive",bool(u,"alive"));result.put("feedSeq",number(u,"feed_seq"));var st=string(u,"estate")==null?null:army.settings(string(u,"estate"));result.put("unpaid",st!=null&&number(st,"unpaid_since")>0&&s.now()-number(st,"unpaid_since")>=2*FeudalService.DAY);return result;}
            case "feed"->{String id=text(data,"unit");var u=army.unit(id);String h=u==null?null:string(u,"home");if(h==null)return Map.of("amount",0,"reason","Немає міста постачання");if(!provisions.settleArmy(h))return Map.of("amount",0,"paused",true,"reason","Облік провізії або передача ще не готові");return Map.of("amount",army.feed(id,num(data,"seq"),10));}
            case "retire"->{army.retire(text(data,"unit"));return Map.of();}
            case "lost","dead"->{army.presence(text(data,"unit"),action.equals("dead"));return Map.of();}
            case "incident"->{String defender=view.estate(text(data,"target"),text(data,"ownerB"),flag(data,"recruitB")),attacker=text(data,"source");if(defender!=null&&attacker!=null){retaliation.put(defender+"|"+attacker,System.currentTimeMillis()+120000);retaliation.entrySet().removeIf(e->e.getValue()<System.currentTimeMillis());}return Map.of();}
            case "canHire"->{String h=home(text(data,"actor"));boolean allow=h!=null&&army.count("home",h,false)<number(army.settings(h),"unit_limit");return Map.of("allow",allow);}
            default->throw new IllegalArgumentException("Невідомий запит до армії.");
        }
    }
    boolean territory(Map<String,Object> data,String worldKey,String xKey,String zKey){
        String world=text(data,worldKey);if(world==null||!data.containsKey(xKey)||!data.containsKey(zKey))return false;
        if(Bukkit.isPrimaryThread())for(World w:Bukkit.getWorlds())if(w.getKey().toString().equals(world))return w.getPVP()&&(!TownyAPI.getInstance().isTownyWorld(w)||TownyAPI.getInstance().isPVP(new Location(w,((Number)data.get(xKey)).doubleValue(),64,((Number)data.get(zKey)).doubleValue())));
        int x=(int)Math.floor(((Number)data.get(xKey)).doubleValue()/cell),z=(int)Math.floor(((Number)data.get(zKey)).doubleValue()/cell);
        return claims.getOrDefault(world+"|"+x+"|"+z,worlds.getOrDefault(world,false));
    }
    public void refresh()throws Exception{
        main();var api=TownyAPI.getInstance();var lands=new HashMap<String,ArmyPolicy.Land>();var citizens=new HashMap<String,String>();var units=new HashMap<String,ArmyPolicy.Soldier>();var allies=new HashSet<String>();var pacts=new HashSet<String>();var wars=new HashSet<String>();var officers=new HashSet<String>();
        for(var e:s.estates())if(e.active()){String root=e.id();var seen=new HashSet<String>();for(String p=e.id();p!=null&&seen.add(p);p=s.parent(p))root=p;lands.put(e.id(),new ArmyPolicy.Land(e.id(),e.nation(),root,e.lord()));}
        for(var town:api.getTowns())for(var resident:town.getResidents())citizens.put(resident.getUUID().toString(),TownyBridge.townId(town));
        for(var nation:com.palmergames.bukkit.towny.TownyUniverse.getInstance().getNations())for(var ally:nation.getAllies())if(ally.hasAlly(nation))allies.add(ArmyPolicy.pair(TownyBridge.nationId(nation),TownyBridge.nationId(ally)));
        for(var u:s.db.query("SELECT * FROM army_units"))units.put(string(u,"id"),new ArmyPolicy.Soldier(string(u,"owner"),string(u,"estate"),string(u,"home"),bool(u,"alive")));
        for(var o:s.db.query("SELECT * FROM army_officers"))if(lands.containsKey(string(o,"estate"))&&army.citizen(string(o,"person"),string(o,"estate")))officers.add(string(o,"estate")+"|"+string(o,"person"));
        for(var d:s.db.query("SELECT * FROM diplomacy WHERE status='ACTIVE' AND ends>?",s.now())){
            String a=string(d,"a"),b=string(d,"b");if("ALLIANCE".equals(string(d,"kind")))allies.add(ArmyPolicy.pair(a,b));if("NONAGGRESSION".equals(string(d,"kind")))pacts.add(ArmyPolicy.pair(a,b));
        }
        // Each side expands only through its own contract duties, never through allies of allies.
        for(var w:s.db.query("SELECT * FROM army_wars WHERE status='ACTIVE'")){
            String a=string(w,"attacker"),b=string(w,"defender");if(!lands.containsKey(a)||!lands.containsKey(b))continue;
            var left=participants(a,false,lands);var right=participants(b,true,lands);for(String x:left)for(String y:right)wars.add(ArmyPolicy.pair(x,y));
        }
        var factionList=new ArrayList<Map<String,Object>>();var factionIds=new HashMap<String,String>();
        for(var land:lands.values()){
            if(!land.id().startsWith("t:")&&units.values().stream().noneMatch(u->land.id().equals(u.estate())))continue;
            String faction=army.faction(land.id());factionIds.put(faction,land.id());var members=new ArrayList<Map<String,String>>();
            citizens.forEach((person,town)->{if(town.equals(land.id()))members.add(Map.of("uuid",person,"name",towny.playerName(person)));});
            var row=new HashMap<String,Object>();row.put("id",faction);row.put("estate",land.id());row.put("name",s.estate(land.id()).name());row.put("leader",land.ruler());row.put("leaderName",towny.playerName(land.ruler()));row.put("members",List.copyOf(members));factionList.add(Collections.unmodifiableMap(row));
        }
        var areas=new HashMap<String,Boolean>();var defaults=new HashMap<String,Boolean>();int size=Coord.getCellSize();
        for(World world:Bukkit.getWorlds()){
            String key=world.getKey().toString();boolean allowed=world.getPVP();if(api.isTownyWorld(world))allowed&=CombatUtil.isWorldPvP(api.getTownyWorld(world));defaults.put(key,allowed);
        }
        for(var block:api.getTownBlocks()){
            World world=Bukkit.getWorld(block.getWorldCoord().getWorldName());if(world==null)continue;String key=world.getKey().toString();var location=new Location(world,block.getX()*size+.5,64,block.getZ()*size+.5);areas.put(key+"|"+block.getX()+"|"+block.getZ(),world.getPVP()&&api.isPVP(location));
        }
        cell=size;claims=Map.copyOf(areas);worlds=Map.copyOf(defaults);factions=List.copyOf(factionList);factionEstates=Map.copyOf(factionIds);
        view=new ArmyPolicy.View(System.currentTimeMillis(),lands,citizens,units,allies,pacts,wars,officers);
    }
    Set<String> participants(String principal,boolean defending,Map<String,ArmyPolicy.Land> lands)throws Exception{
        var result=new HashSet<String>();result.add(principal);String nation=principal.startsWith("n:")?s.estate(principal).nation():null;
        for(var land:lands.values())if(Objects.equals(land.nation(),nation)&&nation!=null)result.add(land.id());
        boolean changed;do{changed=false;for(var c:s.db.query("SELECT * FROM vc_contract WHERE active_rev>0 AND status IN('ACTIVE','NOTICE')")){
            var t=s.contractBuilder.terms(number(c,"id"),(int)number(c,"active_rev"));String child=string(c,"child"),parent=string(c,"parent");
            if(result.contains(parent)&&(t.get("military").equals("ALL")||defending&&t.get("military").equals("DEFENSIVE")))changed|=result.add(child);
            if(defending&&result.contains(child)&&t.yes("defend"))changed|=result.add(parent);
        }}while(changed);return result;
    }
    List<Map<String,Object>> roster(String estate)throws Exception{
        var lands=army.politics==null?Set.of(estate):army.politics.lands(estate);var rows=new ArrayList<Map<String,Object>>();for(var u:s.db.query("SELECT * FROM army_units WHERE alive=TRUE ORDER BY id"))if(estate.equals(string(u,"estate"))||lands.contains(string(u,"home")))rows.add(u);return rows;
    }
    String estateLabel(String id)throws Exception{return id==null?"Не призначено":s.estate(id).name();}
    String debtDate(long time){return time<=0?"Боргу немає":java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))+" UTC";}
    void say(CommandSender p,String message){plugin.message(p,message);}
    Actor actor(Player p){return new Actor(p.getUniqueId().toString(),false);}
    String selected(Player p,String[] args,int index)throws Exception{return args.length>index?s.resolve(args[index]).id():Objects.requireNonNull(home(p.getUniqueId().toString()),"Ви не житель міста.");}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{require(sender instanceof Player,"Команда доступна гравцю.");Player p=(Player)sender;towny.sync();String sub=args.length==0?"menu":args[0];
            switch(sub){
                case "menu","status"->open(p,selected(p,args,1),0);
                case "assign"->{require(args.length==4,"/army assign UUID володіння місто_постачання");army.assign(actor(p),args[1],s.resolve(args[2]).id(),s.resolve(args[3]).id());say(p,"Рекрута передано володінню. Командування успадковується посадою.");}
                case "supply"->{require(args.length==3,"/army supply володіння власне_місто_постачання");army.supply(actor(p),s.resolve(args[1]).id(),s.resolve(args[2]).id());say(p,"Місто постачання гарнізону оновлено.");}
                case "release"->{require(args.length==2,"/army release UUID");army.release(actor(p),args[1]);say(p,"Рекрута повернуто первісному власнику.");}
                case "officer","revoke"->{require(args.length==3,"/army officer|revoke володіння гравець");army.officer(actor(p),s.resolve(args[1]).id(),towny.residentId(args[2]),sub.equals("officer"));say(p,"Склад командування оновлено.");}
                case "upkeep"->{require(args.length==5,"/army upkeep володіння сума_за_рекрута_на_добу валюта ліміт");say(p,"Рішення: "+army.propose(actor(p),s.resolve(args[1]).id(),"WAGE",args[2]+" "+args[3].toUpperCase(Locale.ROOT)+" "+args[4]));}
                case "war"->{require(args.length==3,"/army war ваше_володіння вороже_володіння");say(p,"Рішення: "+army.propose(actor(p),s.resolve(args[1]).id(),"WAR",s.resolve(args[2]).id()));}
                case "peace"->{require(args.length==3,"/army peace ваше_володіння UUID_війни");say(p,"Рішення: "+army.propose(actor(p),s.resolve(args[1]).id(),"PEACE",args[2]));}
                case "vote"->{require(args.length==3&&Set.of("yes","no").contains(args[2]),"/army vote UUID_рішення yes|no");army.vote(actor(p),args[1],args[2].equals("yes"));say(p,"Голос збережено.");}
                case "wars"->{for(var w:s.db.query("SELECT * FROM army_wars WHERE status='ACTIVE'"))say(p,string(w,"id")+": "+s.estate(string(w,"attacker")).name()+" → "+s.estate(string(w,"defender")).name());}
                case "history"->{String estate=selected(p,args,1);access(p,estate);for(var r:army.history(estate,args.length>2?Integer.parseInt(args[2])-1:0))say(p,string(r,"event")+": "+string(r,"detail")+" ["+number(r,"amount")+"]");}
                case "why"->{require(args.length==3,"/army why UUID_рекрута UUID_цілі");var u=army.unit(args[1]);require(p.hasPermission("empirecraft.feudal.admin")||army.commands(p.getUniqueId().toString(),u),"Діагностика доступна командиру свого загону.");var result=(Map<?,?>)Class.forName("ua.empirecraft.recruits.ArmyHooks").getMethod("diagnose",String.class,String.class).invoke(null,args[1],args[2]);say(p,"Атака: "+result.get("allow")+"; причина: "+result.get("reason")+"; сторони: "+result.get("a")+" → "+result.get("b"));}
                case "command"->{require(args.length>=3&&args.length<=4,"/army command володіння follow|hold|defend|attack [UUID_цілі]");String estate=s.resolve(args[1]).id();var result=Class.forName("ua.empirecraft.recruits.ArmyHooks").getMethod("orders",String.class,String.class,String.class,String.class).invoke(null,p.getUniqueId().toString(),estate,args[2],args.length==4?args[3]:null);say(p,"Наказ отримали рекрути: "+result+" (завантажені, у межах 128 блоків).");}
                case "inspect"->{require(p.hasPermission("empirecraft.feudal.admin")&&args.length==2,"/army inspect UUID — для адміністратора");var u=army.unit(args[1]);require(u!=null,"Рекрут ще не зареєстрований.");say(p,"Власник: "+string(u,"owner")+"; володіння: "+string(u,"estate")+"; провізія: "+string(u,"home")+"; завантажений: "+bool(u,"loaded")+"; голод: "+u.get("hunger")+"; спожито: "+number(u,"feed_total"));}
                default->say(p,"/army menu [місто]; assign, supply, release, officer, revoke, command, upkeep, war, peace, vote, wars, history, why, inspect. Передавання в гарнізон: лише свого рекрута, UUID видно у вкладці «Рекрути».");
            }refresh();
        }catch(IllegalArgumentException|NullPointerException e){say(sender,"§c"+e.getMessage());}catch(Exception e){plugin.failure("Армія",e);say(sender,"§cОперацію не завершено; дивіться журнал сервера.");}return true;
    }
    public void open(Player p,String estate,int page)throws Exception{
        access(p,estate);var rows=roster(estate);var m=new Menu(p,"Рекрути · "+s.estate(estate).name());long loaded=rows.stream().filter(u->bool(u,"loaded")).count(),hungry=rows.stream().filter(u->((Number)u.get("hunger")).doubleValue()<=1).count();
        double perDay=loaded*hungerRate()*1440/5;
        button(m,4,Material.BREAD,"Забезпечення рекрутів",null,"Відомих живих: "+rows.size()+"; завантажено: "+loaded,"Голодують: "+hungry,"Оцінка при поточній активності: "+String.format(Locale.ROOT,"%.2f",perDay)+" од. поживності / реальну добу","Джерело: міська провізія /t provision","Незавантажені рекрути не їдять; загальна кількість поповнюється при першому завантаженні старих загонів.");
        if(estate.startsWith("t:")&&army.food.town(estate)!=null){var sum=s.db.one("SELECT COALESCE(SUM(amount),0) AS n FROM army_log WHERE estate=? AND event='FOOD' AND at_time>?",estate,s.now()-FeudalService.DAY);String reserve=new BigDecimal(army.food.reserve(estate)).divide(new BigDecimal(ProvisionClock.UNIT),2,RoundingMode.DOWN).toPlainString();button(m,5,Material.CHEST,"Запас і фактичні витрати",q->q.performCommand("t provision"),"Спільний залишок: "+reserve+" од.","Рекрути з'їли за 24 год: "+number(sum,"n")+" од.","Запас також витрачається на територію міста.");}
        int start=page*27;for(int i=start;i<Math.min(start+27,rows.size());i++){var u=rows.get(i);String id=string(u,"id");button(m,9+i-start,Material.IRON_HELMET,id,q->{say(q,"UUID: "+id+"; передавання: /army assign "+id+" <володіння> <місто>");},"Власник: "+towny.playerName(string(u,"owner")),"Володіння: "+(string(u,"estate")==null?"Особистий рекрут":estateLabel(string(u,"estate"))),"Постачання: "+estateLabel(string(u,"home")),"Голод: "+String.format(Locale.ROOT,"%.1f",((Number)u.get("hunger")).doubleValue())+"/100; "+(bool(u,"loaded")?"завантажений":"поза завантаженими чанками"));}
        var st=army.settings(estate);button(m,40,Material.GOLD_INGOT,"Утримання гарнізону",null,"На рекрута / добу: "+format(number(st,"wage"))+" "+string(st,"code"),"Ліміт: "+number(st,"unit_limit"),"Утримання: "+debtDate(number(st,"unpaid_since")),"Зміна: /army upkeep володіння сума валюта ліміт");
        button(m,41,Material.PAPER,"Останні події",q->{for(var r:army.history(estate,0))say(q,string(r,"event")+": "+string(r,"detail")+" ["+number(r,"amount")+"]");});
        button(m,42,Material.LECTERN,"Рішення ради",q->{for(var r:s.db.query("SELECT * FROM army_motions WHERE estate=? AND status='OPEN'",estate))say(q,string(r,"id")+" "+string(r,"kind")+" "+string(r,"args")+"; /army vote UUID yes|no");});
        if(page>0)button(m,45,Material.ARROW,"Назад",q->open(q,estate,page-1));if(start+27<rows.size())button(m,53,Material.ARROW,"Далі",q->open(q,estate,page+1));button(m,49,Material.SUNFLOWER,"Оновити",q->open(q,estate,page));p.openInventory(m.inv);
    }
    void button(Menu m,int slot,Material material,String title,Action action,String... lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName("§e"+title);var lines=new ArrayList<String>();for(String text:lore){for(int i=0;i<text.length();i+=48)lines.add("§7"+text.substring(i,Math.min(i+48,text.length())));}meta.setLore(lines);item.setItemMeta(meta);m.inv.setItem(slot,item);if(action!=null)m.actions.put(slot,action);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change0(com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change1(com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change2(com.palmergames.bukkit.towny.event.NationAcceptAllyRequestEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change3(com.palmergames.bukkit.towny.event.nation.NationPreAddAllyEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change4(com.palmergames.bukkit.towny.event.TownAddResidentEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change5(com.palmergames.bukkit.towny.event.TownRemoveResidentEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change6(com.palmergames.bukkit.towny.event.NationAddTownEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change7(com.palmergames.bukkit.towny.event.NationRemoveTownEvent e){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void change8(com.palmergames.bukkit.towny.event.town.toggle.TownTogglePVPEvent e){invalidate();}
    @EventHandler public void townMenu(InventoryOpenEvent e){if(e.getInventory().getHolder() instanceof TownMenu.View v&&e.getInventory().getItem(51)==null){var item=new ItemStack(Material.IRON_HELMET);var meta=item.getItemMeta();meta.setDisplayName("§6Рекрути");meta.setLore(List.of("§7Кількість, провізія, витрати та гарнізони"));meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"army_menu"),PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);e.getInventory().setItem(51,item);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(e.getView().getTopInventory().getHolder() instanceof TownMenu.View v&&e.getRawSlot()==51){var item=e.getCurrentItem();if(item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"army_menu"),PersistentDataType.BYTE)){e.setCancelled(true);Bukkit.getScheduler().runTask(plugin,()->{try{open(p,v.estate,0);}catch(Exception ex){say(p,"§c"+ex.getMessage());}});}return;}
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);if(!m.owner.equals(p.getUniqueId()))return;var action=m.actions.get(e.getRawSlot());if(action!=null)try{action.run(p);}catch(Exception ex){say(p,"§c"+ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return a.length==1?List.of("menu","status","assign","release","officer","revoke","upkeep","war","peace","vote","wars","history","inspect","why","command","supply"):List.of();}
}
