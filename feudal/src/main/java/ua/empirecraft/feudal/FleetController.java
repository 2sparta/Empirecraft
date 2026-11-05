package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.event.actions.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Authenticated native menus, live Towny permissions and reconciliation of the native projection. */
public final class FleetController implements CommandExecutor,TabCompleter,Listener {
    final FeudalPlugin plugin;final FleetService fleet;final FleetWorld world;final TownyBridge towny;
    private final Map<UUID,Session> sessions=new HashMap<>();private final List<Map<String,Object>> openings=new ArrayList<>();
    private int sightCursor;
    private record Prepared(String token,String request,String form,List<String> values,String stamp,String city,long expires,String summary) {}
    private static final class Session {
        final String id=UUID.randomUUID().toString();long expires=System.currentTimeMillis()+300_000;String section="fleet",assembly="";Prepared prepared;
    }
    FleetController(FeudalPlugin plugin,CityMarketService markets,TownyBridge towny,PoliticsService politics,java.util.function.Supplier<ArmyPolicy.View> diplomacy)throws Exception {
        this.plugin=plugin;this.towny=towny;world=new FleetWorld(markets.s,politics,diplomacy);fleet=new FleetService(markets,world);
    }
    private Actor actor(Player p){return new Actor(p.getUniqueId().toString(),false);}
    String home(Player p){return world.hometown(p.getUniqueId().toString());}
    String town(String input)throws Exception {
        Town t;try{t=TownyAPI.getInstance().getTown(UUID.fromString(input.replace("t:","")));}catch(IllegalArgumentException ex){t=TownyAPI.getInstance().getTown(input);}
        require(t!=null,"Місто не знайдено.");return TownyBridge.townId(t);
    }
    String person(String input){
        if(input.isBlank() || input.equals("-"))return "";var api=TownyAPI.getInstance();com.palmergames.bukkit.towny.object.Resident r;
        try{r=api.getResident(UUID.fromString(input));}catch(IllegalArgumentException ex){r=api.getResident(input);}
        require(r!=null,"Мешканця не знайдено.");return r.getUUID().toString();
    }
    private List<String> buildTowns(Player p)throws Exception {
        String home=home(p);List<String> result=new ArrayList<>();for(var e:fleet.s.estates())if(e.active() && e.id().startsWith("t:") && (e.id().equals(home) || fleet.manages(actor(p),e.id())))result.add(e.id());return result;
    }
    private String cityName(String id){try{return id.isEmpty()?"Без міста":fleet.s.estate(id).name();}catch(Exception ex){return id;}}
    private void ensure(long id)throws Exception {
        if(fleet.db.one("SELECT id FROM naval_ships WHERE id=?",id)!=null)return;var raw=world.raw(id);var hull=FleetWorld.hull(raw);require(hull!=null,"Корабель не знайдено.");
        fleet.db.atomic(()->{
            fleet.register(hull,hull.nativeTown(),Objects.toString(raw.get("founder"),""));
            String captain=Objects.toString(raw.get("captain"),"");if(!captain.isBlank())fleet.db.execute("UPDATE naval_ships SET captain=? WHERE id=?",captain,id);
            for(Object person:(List<?>)raw.getOrDefault("crew",List.of()))fleet.db.execute("MERGE INTO naval_crew KEY(ship,person) VALUES(?,?)",id,person.toString());return null;
        });
    }
    void project(long id)throws Exception {
        var row=fleet.ship(id);var lock=fleet.db.one("SELECT offer FROM naval_locks WHERE ship=?",id);var data=new LinkedHashMap<String,Object>();
        data.put("ship",id);data.put("town",string(row,"town"));data.put("name",string(row,"name"));data.put("captain",string(row,"captain"));data.put("revision",number(row,"revision"));data.put("lock",lock==null?"":string(lock,"offer"));
        data.put("crew",fleet.db.query("SELECT person FROM naval_crew WHERE ship=? ORDER BY person",id).stream().map(r->string(r,"person")).toList());world.call("project",data);
    }
    void reconcile()throws Exception {for(var row:fleet.db.query("SELECT id FROM naval_ships")){long id=number(row,"id");if(world.hull(id)!=null)project(id);}}
    public Map<String,Object> request(String action,Map<String,Object> data)throws Exception {
        require(Bukkit.isPrimaryThread(),"Потрібен серверний потік.");
        if(action.equals("policy")){
            long id=((Number)data.get("ship")).longValue();ensure(id);var p=fleet.policy(id,Objects.toString(data.get("player"),null));
            return Map.of("allow",p.allow(),"protect",p.protect(),"locked",p.locked(),"town",p.owner());
        }
        UUID id=UUID.fromString(data.get("player").toString());Player player=Bukkit.getPlayer(id);require(player!=null,"Гравець вийшов із сервера.");
        require(player.hasPermission("empirecraft.feudal.use"),"Немає дозволу на міський флот.");
        if(action.equals("view")){return world.call("view",data);}
        if(action.equals("hide")){fleet.hide(id.toString(),((Number)data.get("ship")).longValue(),true);return Map.of("ok",true);}
        if(action.equals("assembly")){require(!buildTowns(player).isEmpty(),"Приєднайтесь до міста, щоб створити міський корабель.");Session session=open(player,"build");session.assembly=data.get("token").toString();return Map.of("ok",true);}
        if(action.equals("can-build"))return Map.of("ok",buildTowns(player).contains(data.get("town").toString()));
        if(action.equals("registered")){
            long ship=((Number)data.get("ship")).longValue();String town=data.get("town").toString();
            if(!town.isEmpty())require(buildTowns(player).contains(town),"Місто недоступне для створення корабля.");ensure(ship);project(ship);plugin.message(player,"Корабель #"+ship+" належить місту "+cityName(string(fleet.ship(ship),"town"))+". Капітана й команду призначає мер або адмірал: /fleet.");return Map.of("ok",true);
        }
        throw new IllegalArgumentException("Невідома дія корабельної системи.");
    }
    private Session open(Player p,String section){Session session=new Session();session.section=section;sessions.put(p.getUniqueId(),session);openings.add(Map.of("player",p.getUniqueId().toString(),"session",session.id,"expires",session.expires));return session;}
    public void openMarket(Player p)throws Exception {require(p.hasPermission("empirecraft.feudal.use"),"Немає дозволу на міський флот.");counter(p,currentMarket(p));open(p,"market");}
    public List<Map<String,Object>> poll(){var result=List.copyOf(openings);openings.clear();return result;}
    String currentMarket(Player p)throws Exception {
        var place=MarketCommands.place(p);var row=fleet.db.one("SELECT market FROM cm_claims WHERE world=? AND x=? AND z=?",place.world(),place.cx(),place.cz());require(row!=null,"Підійдіть до базару міського ринку.");return string(row,"market");
    }
    private void counter(Player p,String market)throws Exception {fleet.m.access(market,CityMarketService.Department.GOODS,MarketCommands.place(p));}
    private void accessOffer(Player p,Map<String,Object> offer)throws Exception {
        if(!string(offer,"market").isEmpty())counter(p,string(offer,"market"));
        else require(Objects.equals(home(p),string(offer,"buyer")) || Objects.equals(home(p),string(offer,"seller")),"Це обмін інших міст.");
    }
    private String actionStamp(Player p,String form,List<String> values)throws Exception {
        if(form.equals("buy") || form.equals("bid") || form.equals("cancel"))return fleet.stamp(values.get(0));
        if(Set.of("name","captain","crew-add","crew-remove","sell","auction","direct","register").contains(form))return fleet.ship(Long.parseLong(values.get(0))).toString();
        return Objects.toString(home(p),"")+"|"+fleet.s.membershipStamp(p.getUniqueId().toString());
    }
    private static String value(List<String> v,int i){require(i<v.size(),"Заповніть усі поля.");return v.get(i).strip();}
    private static long shipId(List<String> v){return Long.parseLong(value(v,0));}
    private List<String> canonical(Player p,String form,List<String> source)throws Exception {
        List<String> values=new ArrayList<>(source);
        if(Set.of("captain","crew-add","crew-remove").contains(form))values.set(1,person(value(values,1)));
        if(form.equals("admiral"))values.set(0,person(value(values,0)));
        if(form.equals("direct"))values.set(1,town(value(values,1)));
        if(form.equals("register"))values.set(1,value(values,1).isBlank()?Objects.toString(home(p),""):town(value(values,1)));
        if((form.equals("sell")||form.equals("auction"))&&value(values,1).isEmpty())values.set(1,currentMarket(p));
        if(form.equals("port")&&value(values,0).isEmpty()){
            var market=fleet.db.one("SELECT id FROM cm_markets WHERE town=? AND state<>'ARCHIVED' ORDER BY name LIMIT 1",home(p));require(market!=null,"Ваше місто ще не має ринку.");values.set(0,string(market,"id"));
        }
        return List.copyOf(values);
    }
    private String perform(Player p,String request,String form,List<String> values,boolean commit,String expected)throws Exception {
        Actor actor=actor(p);String home=home(p);
        switch(form){
            case "register" -> {long id=shipId(values);ensure(id);String city=value(values,1);var vessel=fleet.ship(id);require(string(vessel,"town").isEmpty() && actor.id().equals(string(vessel,"founder")) && city.equals(home),"Зареєструвати особистий корабель може його засновник у своє місто.");if(commit)fleet.assign(actor,request,id,city);return "Зареєструвати корабель #"+id+" як власність міста "+cityName(city)+". Команду призначить мер або адмірал.";}
            case "name","captain","crew-add","crew-remove" -> {
                long id=shipId(values);ensure(id);var row=fleet.ship(id);require(fleet.manages(actor,string(row,"town")),"Потрібен мер або адмірал міста-власника.");fleet.unlocked(id);
                String input=value(values,1),result=form.equals("name")?input:person(input);
                if(commit)fleet.edit(actor,request,id,form,result);return "Корабель #"+id+": "+form+" → "+input;
            }
            case "admiral" -> {require(home!=null && fleet.mayor(actor,home),"Адмірала призначає мер міста.");String who=person(value(values,0));if(commit)fleet.admiral(actor,request,home,who);return "Адмірал міста "+cityName(home)+": "+value(values,0);}
            case "port" -> {String market=value(values,0);boolean enabled=value(values,1).equalsIgnoreCase("on");require(Set.of("on","off").contains(value(values,1).toLowerCase(Locale.ROOT)),"Вкажіть on або off.");String host=string(fleet.m.market(market),"town");require(fleet.mayor(actor,host),"Потрібен мер міста ринку.");if(commit)fleet.port(actor,request,market,enabled);return (enabled?"Дозволити":"Заборонити")+" продаж кораблів і мирну гавань: "+string(fleet.m.market(market),"name");}
            case "sell","auction","direct" -> {
                long id=shipId(values);ensure(id);var row=fleet.ship(id);require(fleet.mayor(actor,string(row,"town")),"Продавати може лише мер міста-власника.");fleet.unlocked(id);
                String market=form.equals("direct")?"":value(values,1),buyer=form.equals("direct")?town(value(values,1)):"";
                long price=money(value(values,2)),step=form.equals("auction")?money(value(values,3)):1;
                String code=value(values,form.equals("auction")?4:3).toUpperCase(Locale.ROOT);
                int hours=form.equals("direct")?1:Integer.parseInt(value(values,form.equals("auction")?5:4));String description=value(values,form.equals("auction")?6:form.equals("direct")?4:5);
                if(!market.isEmpty()){counter(p,market);require(fleet.eligible(id,market),"Потрібні приват республіки та відстань до базару не більше 200 блоків від корпусу.");}
                if(commit){String result=fleet.list(actor,request,id,market,form.equals("sell")?"SALE":form.equals("auction")?"AUCTION":"DIRECT",buyer,code,price,step,hours,description);
                    if(form.equals("direct")){var target=FleetWorld.town(buyer);Player mayor=target==null||target.getMayor()==null?null:Bukkit.getPlayer(target.getMayor().getUUID());if(mayor!=null){open(mayor,"fleet");plugin.message(mayor,"Місто "+cityName(string(row,"town"))+" пропонує обмін корабля #"+id+". Умови доступні у /fleet протягом 30 секунд.");}}
                    return result;}
                return "Корабель #"+id+" «"+string(row,"name")+"»; "+format(price)+" "+code+"; "+(market.isEmpty()?"прямий обмін із "+cityName(buyer):"ринок "+string(fleet.m.market(market),"name"))+". "+description;
            }
            case "buy","bid" -> {
                String id=value(values,0);var offer=fleet.offer(id);accessOffer(p,offer);require(home!=null && fleet.mayor(actor,home),"Купівлю й ставки від міста підтверджує мер.");
                long price=form.equals("buy")?number(offer,"price"):money(value(values,1));String market=string(offer,"market");int bps=market.isEmpty()?0:(int)number(fleet.m.tariff(market),"buyer");
                if(commit){if(form.equals("buy"))fleet.buy(actor,request,id,home,expected);else fleet.bid(actor,request,id,home,price,expected);}
                return (form.equals("buy")?"Купівля":"Ставка")+" від міста "+cityName(home)+": "+format(price)+" + збір "+format(CityMarketService.fee(price,bps))+" = "+format(Math.addExact(price,CityMarketService.fee(price,bps)))+" "+string(offer,"code")+". Казна міста.";
            }
            case "cancel" -> {String id=value(values,0);var offer=fleet.offer(id);require(fleet.mayor(actor,string(offer,"seller")),"Оголошення належить іншому місту.");if(commit)fleet.cancel(actor,request,id);return "Зняти корабель #"+number(offer,"ship")+" з продажу.";}
            default -> throw new IllegalArgumentException("Невідома дія флоту.");
        }
    }
    public Map<String,Object> ui(UUID id,String sessionId,String action,String section,String form,List<String> values,String token,int page){
        try{
            Player p=Bukkit.getPlayer(id);require(p!=null && Bukkit.isPrimaryThread(),"Сесію завершено.");require(p.hasPermission("empirecraft.feudal.use"),"Немає дозволу на міський флот.");Session session=sessions.get(id);
            require(session!=null && session.id.equals(sessionId) && session.expires>System.currentTimeMillis(),"Відкрийте /fleet повторно.");session.expires=System.currentTimeMillis()+300_000;
            require(values.size()<=10 && values.stream().mapToInt(String::length).sum()<=8000,"Форма завелика.");
            if(action.equals("choose-town")){
                String city=value(values,0);require(!session.assembly.isEmpty() && buildTowns(p).contains(city),"Місто недоступне.");String assembly=session.assembly;session.assembly="";
                world.call("assembly-resume",Map.of("player",id.toString(),"token",assembly,"town",city));session.section="fleet";
            }else if(action.equals("prepare")){
                values=canonical(p,form,values);
                String summary=perform(p,UUID.randomUUID().toString(),form,values,false,"");String stamp=actionStamp(p,form,values);
                session.prepared=new Prepared(UUID.randomUUID().toString(),UUID.randomUUID().toString(),form,List.copyOf(values),stamp,Objects.toString(home(p),""),System.currentTimeMillis()+30_000,summary);
            }else if(action.equals("confirm")){
                Prepared prepared=session.prepared;require(prepared!=null && prepared.token.equals(token) && prepared.expires>System.currentTimeMillis(),"Підтвердження застаріло.");
                require(prepared.city.equals(Objects.toString(home(p),"")),"Ваше місто змінилося. Перегляньте умови повторно.");
                require(prepared.stamp.equals(actionStamp(p,prepared.form,prepared.values)),"Умови змінилися. Перегляньте їх повторно.");
                perform(p,prepared.request,prepared.form,prepared.values,true,prepared.stamp);session.prepared=null;reconcile();
            }else if(action.equals("discard")){session.prepared=null;}
            else if(action.equals("hide") || action.equals("restore")){fleet.hide(id.toString(),Long.parseLong(value(values,0)),action.equals("hide"));}
            else if(action.equals("look")){
                var offer=fleet.offer(value(values,0));accessOffer(p,offer);require("OPEN".equals(string(offer,"state")) && fleet.valid(offer),"Корабель уже знято з продажу.");var raw=world.raw(number(offer,"ship"));var hull=FleetWorld.hull(raw);require(hull!=null,"Корабель недоступний.");
                var reply=view(p,session,session.section,page);reply.put("look",Map.of("ship",hull.id(),"name",string(fleet.ship(hull.id()),"name"),"dimension",raw.get("dimension"),"x",hull.x(),"y",hull.y(),"z",hull.z()));return reply;
            }else require(action.equals("view"),"Невідома дія.");
            if(action.equals("view") && !section.isBlank())session.section=Set.of("fleet","market","auctions","manage","sightings","build").contains(section)?section:"fleet";
            return view(p,session,session.section,page);
        }catch(Exception ex){return Map.of("error",Objects.toString(ex.getMessage(),"Дія недоступна."));}
    }
    private static Map<String,Object> button(String label,String action,String form,String value){return Map.of("label",label,"action",action,"form",form,"value",value);}
    private static Map<String,Object> form(String id,String title,String...fields){return Map.of("id",id,"title",title,"fields",List.of(fields));}
    Map<String,Object> view(Player p,Session session,String section,int page)throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("session",session.id);data.put("section",section);data.put("title","Міський флот");data.put("city",cityName(Objects.toString(home(p),"")));
        List<Map<String,Object>> rows=new ArrayList<>(),forms=new ArrayList<>();String home=home(p);
        if(section.equals("build")){
            for(String town:buildTowns(p))rows.add(Map.of("id",town,"title",cityName(town),"detail","Корабель належатиме цьому місту.","buttons",List.of(button("Обрати місто","choose-town","",town))));
        }else if(section.equals("fleet")){
            for(var row:fleet.db.query("SELECT * FROM naval_ships WHERE town=? ORDER BY name",home==null?"":home)){
                long id=number(row,"id");String captain=string(row,"captain");var lock=fleet.db.one("SELECT offer FROM naval_locks WHERE ship=?",id);
                rows.add(Map.of("id",""+id,"title","#"+id+" «"+string(row,"name")+"»","detail","Капітан: "+(captain.isEmpty()?"не призначений":towny.playerName(captain))+"; команда: "+fleet.db.query("SELECT person FROM naval_crew WHERE ship=?",id).size()+(lock==null?"":"; на продажі"),"buttons",List.of()));
            }
            for(var row:fleet.db.query("SELECT * FROM naval_offers WHERE kind='DIRECT' AND state='OPEN' AND (buyer=? OR seller=?)",home,home))addOffer(rows,row);
        }else if(section.equals("market") || section.equals("auctions")){
            String market=currentMarket(p);counter(p,market);data.put("market",market);
            for(var row:fleet.db.query("SELECT * FROM naval_offers WHERE market=? AND kind=? AND state='OPEN' ORDER BY created",market,section.equals("auctions")?"AUCTION":"SALE"))addOffer(rows,row);
            forms.add(form("sell","Виставити на продаж","ID корабля","ID ринку","Ціна","Код валюти","Строк у годинах","Опис продавця"));
            forms.add(form("auction","Виставити на аукціон","ID корабля","ID ринку","Початкова ціна","Крок ставки","Код валюти","Строк у годинах","Опис продавця"));
            forms.add(form("bid","Зробити ставку","ID оголошення","Сума ставки"));forms.add(form("cancel","Зняти з продажу","ID оголошення"));
        }else if(section.equals("manage")){
            forms.add(form("name","Назвати корабель","ID корабля","Назва"));forms.add(form("captain","Призначити капітана","ID корабля","Гравець або - для звільнення"));
            forms.add(form("crew-add","Додати до команди","ID корабля","Гравець"));forms.add(form("crew-remove","Прибрати з команди","ID корабля","Гравець"));
            forms.add(form("admiral","Призначити адмірала","Гравець або - для звільнення"));forms.add(form("port","Продаж кораблів і мирна гавань","ID ринку","on або off"));
            forms.add(form("direct","Запропонувати прямий обмін","ID корабля","Місто-покупець","Ціна","Код валюти","Опис"));forms.add(form("cancel","Скасувати оголошення","ID оголошення"));
            forms.add(form("register","Передати особистий корабель своєму місту","ID корабля","Місто (порожньо — ваше)"));
        }else if(section.equals("sightings")){
            for(var row:fleet.db.query("SELECT * FROM naval_seen WHERE person=? ORDER BY observed DESC LIMIT 256",p.getUniqueId().toString())){
                boolean hidden=bool(row,"hidden");rows.add(Map.of("id",""+number(row,"ship"),"title",string(row,"name"),"detail",cityName(string(row,"town"))+"; останнє спостереження "+CityMarketService.time(number(row,"observed"))+(hidden?"; приховано":""),"buttons",List.of(button(hidden?"Показувати знову":"Приховати мітку",hidden?"restore":"hide","",""+number(row,"ship")))));
            }
        }
        int total=Math.max(1,(rows.size()+9)/10);page=Math.max(1,Math.min(total,page));data.put("page",page);data.put("pages",total);data.put("rows",rows.subList((page-1)*10,Math.min(rows.size(),page*10)));data.put("forms",forms);
        if(data.containsKey("market"))for(int i=0;i<forms.size();i++){
            var schema=new LinkedHashMap<String,Object>(forms.get(i));String id=schema.get("id").toString();
            if(id.equals("sell"))schema.put("defaults",List.of("",data.get("market"),"","","72",""));
            if(id.equals("auction"))schema.put("defaults",List.of("",data.get("market"),"","","","24",""));forms.set(i,schema);
        }
        if(session.prepared!=null)data.put("confirmation",Map.of("token",session.prepared.token,"text",session.prepared.summary));return data;
    }
    private void addOffer(List<Map<String,Object>> rows,Map<String,Object> offer)throws Exception {
        String id=string(offer,"id"),kind=string(offer,"kind");long ship=number(offer,"ship");List<Map<String,Object>> buttons=new ArrayList<>();
        buttons.add(button("Подивитися","look","",id));if(!kind.equals("AUCTION"))buttons.add(button("Купити від міста","prepare","buy",id));else buttons.add(button("Зробити ставку","form","bid",id));
        rows.add(Map.of("id",id,"title","#"+ship+" «"+string(fleet.ship(ship),"name")+"» · "+format(number(offer,kind.equals("AUCTION")&&!string(offer,"bidder").isEmpty()?"bid":"price"))+" "+string(offer,"code"),"detail",cityName(string(offer,"seller"))+". "+string(offer,"information")+". "+string(offer,"description")+". До "+CityMarketService.time(number(offer,"ends")),"buttons",buttons));
    }
    public List<Map<String,Object>> marks(UUID player)throws Exception {
        List<Map<String,Object>> out=new ArrayList<>();for(var row:fleet.sightings(player.toString())){
            var world=Bukkit.getWorld(UUID.fromString(string(row,"world")));if(world==null)continue;
            out.add(Map.of("ship",number(row,"ship"),"name",string(row,"name"),"dimension",world.getKey().toString(),"x",row.get("x"),"y",row.get("y"),"z",row.get("z"),"observed",number(row,"observed")));
        }return out;
    }
    public void tick()throws Exception {
        var hulls=world.all();for(var raw:hulls){long id=((Number)raw.get("id")).longValue();ensure(id);}fleet.tick();reconcile();
        List<Map.Entry<Player,FleetService.Hull>> candidates=new ArrayList<>();
        for(Player p:Bukkit.getOnlinePlayers())for(var raw:hulls){var hull=FleetWorld.hull(raw);String home=home(p);
            if(hull!=null&&hull.loaded()&&home!=null&&hull.world().equals(p.getWorld().getUID().toString())&&FleetGeometry.distance(hull.footprint(),p.getLocation().getX(),p.getLocation().getZ())<=192){
                String owner=string(fleet.ship(hull.id()),"town");if(!owner.isBlank() && world.allied(home,owner)&&!world.war(home,owner))candidates.add(Map.entry(p,hull));
            }
        }
        for(int i=0;i<Math.min(128,candidates.size());i++){var candidate=candidates.get(Math.floorMod(sightCursor++,candidates.size()));fleet.seen(candidate.getKey().getUniqueId().toString(),candidate.getValue());}
        sessions.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis() || Bukkit.getPlayer(e.getKey())==null);
    }
    private void towny(TownyActionEvent event){
        try{var l=event.getLocation();var found=world.call("at",Map.of("world",l.getWorld().getUID().toString(),"x",l.getBlockX(),"y",l.getBlockY(),"z",l.getBlockZ()));if(!found.containsKey("ship"))return;
            long id=((Number)found.get("ship")).longValue();ensure(id);var policy=fleet.policy(id,event.getPlayer().getUniqueId().toString());
            if(policy.protect()){event.setCancelled(!policy.allow());if(policy.allow())event.suppressMessage();}
        }catch(Exception ex){/* Native Forge guard independently denies unavailable fleet permissions. */}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void build(TownyBuildEvent e){towny(e);}
    @EventHandler(priority=EventPriority.LOWEST) public void destroy(TownyDestroyEvent e){towny(e);}
    @EventHandler(priority=EventPriority.LOWEST) public void use(TownyItemuseEvent e){towny(e);}
    @EventHandler(priority=EventPriority.LOWEST) public void switchBlock(TownySwitchEvent e){towny(e);}
    public boolean trade(Player p,String[] args)throws Exception {
        if(args.length==0 || !args[0].equalsIgnoreCase("ship"))return false;
        require(p.hasPermission("empirecraft.feudal.use"),"Немає дозволу на міський флот.");
        if(args.length==1){open(p,"manage");return true;}
        require(args.length>=5,"/trade ship <ID корабля> <місто-покупець> <ціна> <валюта> [опис]");
        var values=canonical(p,"direct",List.of(args[1],args[2],args[3],args[4],args.length>5?String.join(" ",Arrays.copyOfRange(args,5,args.length)):""));
        Session session=open(p,"fleet");String summary=perform(p,req(),"direct",values,false,"");session.prepared=new Prepared(req(),req(),"direct",values,actionStamp(p,"direct",values),Objects.toString(home(p),""),System.currentTimeMillis()+30_000,summary);return true;
    }
    private static String req(){return UUID.randomUUID().toString();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
        require(sender instanceof Player,"Команда доступна гравцю.");Player p=(Player)sender;require(p.hasPermission("empirecraft.feudal.use"),"Немає дозволу на міський флот.");
        if(args.length==0 || args[0].equals("menu")){open(p,"fleet");return true;}
        if(args[0].equals("market")){openMarket(p);return true;}
        if(args[0].equals("assign")){require(p.hasPermission("vs_sails.ship.bypass")&&args.length==3,"/fleet assign <ID> <місто> — лише адміністратор.");long id=Long.parseLong(args[1]);ensure(id);fleet.assign(new Actor(p.getUniqueId().toString(),true),req(),id,town(args[2]));project(id);plugin.message(p,"Місто-власника призначено.");return true;}
        if(args[0].equals("map")){if(args.length>=3){fleet.hide(p.getUniqueId().toString(),Long.parseLong(args[2]),!args[1].equals("show"));plugin.message(p,"Мітку оновлено.");}else open(p,"sightings");return true;}
        require(args[0].equals("edit") && args.length>=2,"/fleet — флот; /fleet market — базар; /fleet map — спостереження; керування доступне в меню.");open(p,"manage");return true;
    }catch(Exception ex){plugin.message(sender,Objects.toString(ex.getMessage(),"Дія недоступна."));return true;}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] args){return args.length==1?List.of("menu","market","map","edit"):List.of();}
}
