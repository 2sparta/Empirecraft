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
    private int sightCursor,missingCursor;
    private final Map<Long,String> unavailableOwners=new HashMap<>();
    private static final Map<String,FleetService.ShipUse> SHIP_FORMS=Map.ofEntries(
        Map.entry("name",FleetService.ShipUse.MANAGE),Map.entry("captain",FleetService.ShipUse.MANAGE),
        Map.entry("crew-add",FleetService.ShipUse.MANAGE),Map.entry("crew-remove",FleetService.ShipUse.MANAGE),
        Map.entry("sell",FleetService.ShipUse.SALE),Map.entry("auction",FleetService.ShipUse.SALE),Map.entry("direct",FleetService.ShipUse.SALE),
        Map.entry("flag-peaceful",FleetService.ShipUse.CONTROL),Map.entry("flag-state",FleetService.ShipUse.CONTROL),Map.entry("flag-pirate",FleetService.ShipUse.CONTROL),
        Map.entry("ship-owner",FleetService.ShipUse.ADMIN));
    private record Prepared(String token,String request,String form,List<String> values,String stamp,String city,long expires,String summary) {}
    private static final class Session {
        final String id=UUID.randomUUID().toString();long expires=System.currentTimeMillis()+300_000;String section="fleet",shipApplication="";ShipRegistrationService.Anchor shipAnchor;Long flagShip,selectedShip;Prepared prepared;
    }
    FleetController(FeudalPlugin plugin,CityMarketService markets,TownyBridge towny,PoliticsService politics,java.util.function.Supplier<ArmyPolicy.View> diplomacy)throws Exception {
        this.plugin=plugin;this.towny=towny;world=new FleetWorld(markets.s,politics,diplomacy);fleet=new FleetService(markets,world);
    }
    private Actor actor(Player p){return new Actor(p.getUniqueId().toString(),false);}
    String home(Player p){return world.hometown(p.getUniqueId().toString());}
    String town(String input)throws Exception {
        Town t;try{t=TownyAPI.getInstance().getTown(UUID.fromString(input.replace("t:","")));}catch(IllegalArgumentException ex){t=TownyAPI.getInstance().getTown(input);}
        require(t!=null,LocalText.tr("Місто не знайдено."));return TownyBridge.townId(t);
    }
    String person(String input){
        if(input.isBlank() || input.equals("-"))return "";var api=TownyAPI.getInstance();com.palmergames.bukkit.towny.object.Resident r;
        try{r=api.getResident(UUID.fromString(input));}catch(IllegalArgumentException ex){r=api.getResident(input);}
        require(r!=null,LocalText.tr("Мешканця не знайдено."));return r.getUUID().toString();
    }
    private List<String> buildTowns(Player p)throws Exception {
        String home=home(p);List<String> result=new ArrayList<>();for(var e:fleet.s.estates())if(e.active() && e.id().startsWith("t:") && (e.id().equals(home) || fleet.manages(actor(p),e.id())))result.add(e.id());return result;
    }
    String cityName(String id){try{return id.isEmpty()?LocalText.tr("Без міста"):fleet.s.estate(id).name();}catch(Exception ex){return id;}}
    void ensure(long id)throws Exception {
        if(fleet.db.one("SELECT id FROM naval_ships WHERE id=?",id)!=null)return;var raw=world.raw(id);var hull=FleetWorld.hull(raw);require(hull!=null,LocalText.tr("Корабель не знайдено."));
        fleet.importNative(hull,Objects.toString(raw.get("founder"),""),Objects.toString(raw.get("captain"),""),(List<?>)raw.getOrDefault("crew",List.of()));
    }
    void project(long id)throws Exception {
        var row=fleet.ship(id);var lock=fleet.db.one("SELECT offer FROM naval_locks WHERE ship=?",id);var data=new LinkedHashMap<String,Object>();
        data.put("ship",id);data.put("town",string(row,"town"));data.put("name",string(row,"name"));data.put("captain",string(row,"captain"));data.put("revision",number(row,"revision"));data.put("lock",lock==null?"":string(lock,"offer"));
        data.put("crew",fleet.db.query("SELECT person FROM naval_crew WHERE ship=? ORDER BY person",id).stream().map(r->string(r,"person")).toList());world.call("project",data);
    }
    void reconcile()throws Exception {for(var row:fleet.db.query("SELECT id FROM naval_ships")){long id=number(row,"id");if(world.hull(id)!=null)project(id);}}
    public Map<String,Object> request(String action,Map<String,Object> data)throws Exception {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Потрібен серверний потік."));
        if(action.startsWith("ship-"))return plugin.ships().nativeRequest(action,data);
        if(action.equals("command-authority"))return Map.of("allow",fleet.commanderAllowed(Objects.toString(data.get("town"),""),Objects.toString(data.get("player"),"")));
        if(action.equals("policy")){
            long id=((Number)data.get("ship")).longValue();ensure(id);var p=fleet.policy(id,Objects.toString(data.get("player"),null));
            return Map.of("allow",p.allow(),"protect",p.protect(),"locked",p.locked(),"town",p.owner());
        }
        UUID id=UUID.fromString(data.get("player").toString());Player player=Bukkit.getPlayer(id);require(player!=null,LocalText.tr("Гравець вийшов із сервера."));
        require(player.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));
        if(action.equals("view")){return world.call("view",data);}
        if(action.equals("hide")){fleet.hide(id.toString(),((Number)data.get("ship")).longValue(),true);return Map.of("ok",true);}
        if(action.equals("assembly")){plugin.ships().openRequest(player,plugin.ships().target(player));return Map.of("ok",true);}
        if(action.equals("can-build"))return Map.of("ok",false);
        if(action.equals("registered")){
            long ship=((Number)data.get("ship")).longValue();String town=data.get("town").toString();
            if(!town.isEmpty())require(buildTowns(player).contains(town),LocalText.tr("Місто недоступне для створення корабля."));ensure(ship);project(ship);plugin.message(player,LocalText.tr("Корабель #")+ship+LocalText.tr(" належить місту ")+cityName(string(fleet.ship(ship),"town"))+LocalText.tr(". Капітана й команду призначає мер або адмірал: /fleet."));return Map.of("ok",true);
        }
        throw new IllegalArgumentException(LocalText.tr("Невідома дія корабельної системи."));
    }
    private Session open(Player p,String section){Session session=new Session();session.section=section;sessions.put(p.getUniqueId(),session);openings.add(Map.of("player",p.getUniqueId().toString(),"session",session.id,"expires",session.expires));return session;}
    void openShipRequest(Player p,ShipRegistrationService.Anchor anchor){open(p,"ship-request").shipAnchor=anchor;}
    void openShipAssignment(Player p,String application){open(p,"ship-assign").shipApplication=application;}
    void openFlags(Player p,long ship)throws Exception {require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));ensure(ship);var session=open(p,"flags");session.flagShip=ship;}
    public void openMarket(Player p)throws Exception {require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));counter(p,currentMarket(p));open(p,"market");}
    public List<Map<String,Object>> poll(){var result=List.copyOf(openings);openings.clear();return result;}
    String currentMarket(Player p)throws Exception {
        var place=MarketCommands.place(p);var row=fleet.db.one("SELECT market FROM cm_claims WHERE world=? AND x=? AND z=?",place.world(),place.cx(),place.cz());require(row!=null,LocalText.tr("Підійдіть до базару міського ринку."));return string(row,"market");
    }
    private void counter(Player p,String market)throws Exception {fleet.m.access(market,CityMarketService.Department.GOODS,MarketCommands.place(p));}
    private void accessOffer(Player p,Map<String,Object> offer)throws Exception {
        if(!string(offer,"market").isEmpty())counter(p,string(offer,"market"));
        else require(Objects.equals(home(p),string(offer,"buyer")) || Objects.equals(home(p),string(offer,"seller")),LocalText.tr("Це обмін інших міст."));
    }
    private String actionStamp(Player p,String form,List<String> values)throws Exception {
        if(form.startsWith("ship-")){var session=sessions.get(p.getUniqueId());return plugin.ships().stamp(form,session.shipApplication,session.shipAnchor,values);}
        if(form.startsWith("ensign-"))return plugin.engagement().designStamp(p,form.substring(7));
        if(form.startsWith("flag-"))return plugin.engagement().nativeCall("inspect",Map.of("player",p.getUniqueId().toString(),"ship",shipId(values),"mode",form.substring(5).toUpperCase(Locale.ROOT))).get("stamp").toString();
        if(form.equals("buy") || form.equals("bid") || form.equals("cancel"))return fleet.stamp(values.get(0));
        if(Set.of("name","captain","crew-add","crew-remove","sell","auction","direct","register").contains(form))return fleet.ship(Long.parseLong(values.get(0))).toString();
        return Objects.toString(home(p),"")+"|"+fleet.s.membershipStamp(p.getUniqueId().toString());
    }
    private static String value(List<String> v,int i){require(i<v.size(),LocalText.tr("Заповніть усі поля."));return v.get(i).strip();}
    private static long shipId(List<String> v){return Long.parseLong(value(v,0));}
    private Actor selector(Player p){return new Actor(p.getUniqueId().toString(),p.hasPermission(ShipRegistrationController.ADMIN));}
    private List<String> canonical(Player p,String form,List<String> source)throws Exception {
        List<String> values=new ArrayList<>(source);
        if(SHIP_FORMS.containsKey(form))values.set(0,Long.toString(fleet.resolveShip(selector(p),value(values,0),SHIP_FORMS.get(form))));
        if(form.startsWith("ship-"))return plugin.ships().canonical(p,form,values);
        if(Set.of("captain","crew-add","crew-remove").contains(form))values.set(1,person(value(values,1)));
        if(form.equals("admiral"))values.set(0,person(value(values,0)));
        if(form.equals("direct"))values.set(1,town(value(values,1)));
        if(form.equals("register"))values.set(1,value(values,1).isBlank()?Objects.toString(home(p),""):town(value(values,1)));
        if((form.equals("sell")||form.equals("auction"))&&value(values,1).isEmpty())values.set(1,currentMarket(p));
        if(form.equals("port")&&value(values,0).isEmpty()){
            var market=fleet.db.one("SELECT id FROM cm_markets WHERE town=? AND state<>'ARCHIVED' ORDER BY name LIMIT 1",home(p));require(market!=null,LocalText.tr("Ваше місто ще не має ринку."));values.set(0,string(market,"id"));
        }
        return List.copyOf(values);
    }
    private String perform(Player p,String request,String form,List<String> values,boolean commit,String expected)throws Exception {
        if(form.startsWith("ship-")){var session=sessions.get(p.getUniqueId());return plugin.ships().perform(p,request,form,values,commit,session.shipApplication,session.shipAnchor);}
        Actor actor=actor(p);String home=home(p);
        switch(form){
            case "flag-peaceful","flag-state","flag-pirate" -> {
                String mode=form.substring(5).toUpperCase(Locale.ROOT);var data=new LinkedHashMap<String,Object>();data.put("player",p.getUniqueId().toString());data.put("ship",shipId(values));data.put("mode",mode);
                var preview=plugin.engagement().nativeCall("inspect",data);if(commit){data.put("expected",expected);plugin.engagement().nativeCall("apply",data);}
                return LocalText.tr("Прапор корабля #")+shipId(values)+": "+LocalText.tr(switch(mode){case "PEACEFUL"->LocalText.tr("Мирний");case "STATE"->LocalText.tr("Державний");default->LocalText.tr("Піратський");})+"; "+preview.get("width")+" × "+preview.get("height")+LocalText.tr(" блоків. Прапор без видимого полотна вважається піратським. Зміна прапора не скасовує PvP.");
            }
            case "ensign-banner","ensign-painting" -> {
                String source=form.substring(7);plugin.engagement().inspectDesign(p,source);if(commit)plugin.engagement().createDesign(p,source);
                return LocalText.tr("Затвердити державний морський прапор із ")+(source.equals("banner")?LocalText.tr("прапора в головній руці"):LocalText.tr("відкритої картини Immersive Paintings перед вами"))+LocalText.tr(". Дизайн буде доступний флоту всієї держави.");
            }
            case "name","captain","crew-add","crew-remove" -> {
                long id=shipId(values);ensure(id);var row=fleet.ship(id);require(fleet.manages(actor,string(row,"town")),LocalText.tr("Потрібен мер або адмірал міста-власника."));fleet.unlocked(id);
                String input=value(values,1),result=form.equals("name")?input:person(input);
                if(commit)fleet.edit(actor,request,id,form,result);return LocalText.tr("Корабель #")+id+": "+form+" → "+input;
            }
            case "admiral" -> {require(home!=null && fleet.mayor(actor,home),LocalText.tr("Адмірала призначає мер міста."));String who=person(value(values,0));if(commit)fleet.admiral(actor,request,home,who);return LocalText.tr("Адмірал міста ")+cityName(home)+": "+value(values,0);}
            case "port" -> {String market=value(values,0);boolean enabled=value(values,1).equalsIgnoreCase("on");require(Set.of("on","off").contains(value(values,1).toLowerCase(Locale.ROOT)),LocalText.tr("Вкажіть on або off."));String host=string(fleet.m.market(market),"town");require(fleet.mayor(actor,host),LocalText.tr("Потрібен мер міста ринку."));if(commit)fleet.port(actor,request,market,enabled);return (enabled?LocalText.tr("Дозволити"):LocalText.tr("Заборонити"))+LocalText.tr(" продаж кораблів і мирну гавань: ")+string(fleet.m.market(market),"name");}
            case "sell","auction","direct" -> {
                long id=shipId(values);ensure(id);var row=fleet.ship(id);require(fleet.mayor(actor,string(row,"town")),LocalText.tr("Продавати може лише мер міста-власника."));fleet.unlocked(id);
                String market=form.equals("direct")?"":value(values,1),buyer=form.equals("direct")?town(value(values,1)):"";
                long price=money(value(values,2)),step=form.equals("auction")?money(value(values,3)):1;
                String code=value(values,form.equals("auction")?4:3).toUpperCase(Locale.ROOT);
                int hours=form.equals("direct")?1:Integer.parseInt(value(values,form.equals("auction")?5:4));String description=value(values,form.equals("auction")?6:form.equals("direct")?4:5);
                if(!market.isEmpty()){counter(p,market);require(fleet.eligible(id,market),LocalText.tr("Потрібні приват республіки та відстань до базару не більше 200 блоків від корпусу."));}
                if(commit){String result=fleet.list(actor,request,id,market,form.equals("sell")?"SALE":form.equals("auction")?"AUCTION":"DIRECT",buyer,code,price,step,hours,description);
                    if(form.equals("direct")){var target=FleetWorld.town(buyer);Player mayor=target==null||target.getMayor()==null?null:Bukkit.getPlayer(target.getMayor().getUUID());if(mayor!=null){open(mayor,"fleet");plugin.message(mayor,LocalText.tr("Місто ")+cityName(string(row,"town"))+LocalText.tr(" пропонує обмін корабля #")+id+LocalText.tr(". Умови доступні у /fleet протягом 30 секунд."));}}
                    return result;}
                return LocalText.tr("Корабель #")+id+" «"+string(row,"name")+"»; "+format(price)+" "+code+"; "+(market.isEmpty()?LocalText.tr("прямий обмін із ")+cityName(buyer):LocalText.tr("ринок ")+string(fleet.m.market(market),"name"))+". "+description;
            }
            case "buy","bid" -> {
                String id=value(values,0);var offer=fleet.offer(id);accessOffer(p,offer);require(home!=null && fleet.mayor(actor,home),LocalText.tr("Купівлю й ставки від міста підтверджує мер."));
                long price=form.equals("buy")?number(offer,"price"):money(value(values,1));String market=string(offer,"market");int bps=market.isEmpty()?0:(int)number(fleet.m.tariff(market),"buyer");
                if(commit){if(form.equals("buy"))fleet.buy(actor,request,id,home,expected);else fleet.bid(actor,request,id,home,price,expected);}
                return (form.equals("buy")?LocalText.tr("Купівля"):LocalText.tr("Ставка"))+LocalText.tr(" від міста ")+cityName(home)+": "+format(price)+LocalText.tr(" + збір ")+format(CityMarketService.fee(price,bps))+" = "+format(Math.addExact(price,CityMarketService.fee(price,bps)))+" "+string(offer,"code")+LocalText.tr(". Казна міста.");
            }
            case "cancel" -> {String id=value(values,0);var offer=fleet.offer(id);require(fleet.mayor(actor,string(offer,"seller")),LocalText.tr("Оголошення належить іншому місту."));if(commit)fleet.cancel(actor,request,id);return LocalText.tr("Зняти корабель #")+number(offer,"ship")+LocalText.tr(" з продажу.");}
            default -> throw new IllegalArgumentException(LocalText.tr("Невідома дія флоту."));
        }
    }
    public Map<String,Object> ui(UUID id,String sessionId,String action,String section,String form,List<String> values,String token,int page){
        try(var menuLanguage=LocalText.useMenu(id)){
        try{
            Player p=Bukkit.getPlayer(id);require(p!=null && Bukkit.isPrimaryThread(),LocalText.tr("Сесію завершено."));require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));Session session=sessions.get(id);
            require(session!=null && session.id.equals(sessionId) && session.expires>System.currentTimeMillis(),LocalText.tr("Відкрийте /fleet повторно."));session.expires=System.currentTimeMillis()+300_000;
            require(values.size()<=10 && values.stream().mapToInt(String::length).sum()<=8000,LocalText.tr("Форма завелика."));
            if(action.equals("new-ship-request")){
                session.shipAnchor=plugin.ships().target(p);session.section="ship-request";session.prepared=null;
            }else if(action.equals("prepare")){
                values=canonical(p,form,values);
                String summary=perform(p,UUID.randomUUID().toString(),form,values,false,"");String stamp=actionStamp(p,form,values);
                session.prepared=new Prepared(UUID.randomUUID().toString(),UUID.randomUUID().toString(),form,List.copyOf(values),stamp,Objects.toString(home(p),""),System.currentTimeMillis()+30_000,summary);
            }else if(action.equals("confirm")){
                Prepared prepared=session.prepared;require(prepared!=null && prepared.token.equals(token) && prepared.expires>System.currentTimeMillis(),LocalText.tr("Підтвердження застаріло."));
                require(prepared.city.equals(Objects.toString(home(p),"")),LocalText.tr("Ваше місто змінилося. Перегляньте умови повторно."));
                require(prepared.stamp.equals(actionStamp(p,prepared.form,prepared.values)),LocalText.tr("Умови змінилися. Перегляньте їх повторно."));
                perform(p,prepared.request,prepared.form,prepared.values,true,prepared.stamp);session.prepared=null;reconcile();
                if(prepared.form.equals("ship-submit")||prepared.form.equals("ship-finalize"))session.section="fleet";
            }else if(action.equals("discard")){session.prepared=null;}
            else if(action.equals("hide") || action.equals("restore")){fleet.hide(id.toString(),Long.parseLong(value(values,0)),action.equals("hide"));}
            else if(action.equals("look")){
                var offer=fleet.offer(value(values,0));accessOffer(p,offer);require("OPEN".equals(string(offer,"state")) && fleet.valid(offer),LocalText.tr("Корабель уже знято з продажу."));var raw=world.raw(number(offer,"ship"));var hull=FleetWorld.hull(raw);require(hull!=null,LocalText.tr("Корабель недоступний."));
                var reply=view(p,session,session.section,page);reply.put("look",Map.of("ship",hull.id(),"name",string(fleet.ship(hull.id()),"name"),"dimension",raw.get("dimension"),"x",hull.x(),"y",hull.y(),"z",hull.z(),"observed",fleet.s.now()));return reply;
            }else if(action.equals("flags")){session.flagShip=Long.parseLong(value(values,0));ensure(session.flagShip);session.section="flags";}
            else if(action.equals("manage-ship")){session.selectedShip=fleet.resolveShip(selector(p),value(values,0),FleetService.ShipUse.MANAGE);session.section="manage";session.prepared=null;}
            else require(action.equals("view"),LocalText.tr("Невідома дія."));
            if(action.equals("view") && !section.isBlank())session.section=Set.of("fleet","market","auctions","manage","sightings","flags","ship-request","ship-assign").contains(section)?section:"fleet";
            return view(p,session,session.section,page);
        }catch(Exception ex){return Map.of("error",Objects.toString(ex.getMessage(),LocalText.tr("Дія недоступна.")));}
        }
    }
    private static Map<String,Object> button(String label,String action,String form,String value){return Map.of("label",label,"action",action,"form",form,"value",value);}
    private static Map<String,Object> form(String id,String title,String...fields){
        var schema=new LinkedHashMap<String,Object>();schema.put("id",id);schema.put("title",title);
        if(SHIP_FORMS.containsKey(id)){fields=fields.clone();fields[0]=LocalText.tr("Корабель");schema.put("shipField",0);schema.put("shipUse",SHIP_FORMS.get(id).name());}
        if(Set.of("captain","crew-add","crew-remove","admiral").contains(id))schema.put("personField",id.equals("admiral")?0:1);
        schema.put("fields",List.of(fields));return schema;
    }
    Map<String,Object> view(Player p,Session session,String section,int page)throws Exception {
        try(var menuLanguage=LocalText.useMenu(p)){
        var data=new LinkedHashMap<String,Object>();data.put("locale",CommandLanguage.menuLocale(p));data.put("session",session.id);data.put("section",section);data.put("title",LocalText.tr("Міський флот"));data.put("city",cityName(Objects.toString(home(p),"")));
        List<Map<String,Object>> rows=new ArrayList<>(),forms=new ArrayList<>();String home=home(p);
        if(section.equals("ship-request")){
            require(session.shipAnchor!=null,LocalText.tr("Наведіться на блок майбутнього корабля."));data.put("title",LocalText.tr("Заявка на корабель"));
            var anchor=session.shipAnchor;rows.add(Map.of("id","anchor","title",LocalText.tr("Блок збирання"),"detail",anchor.x()+" "+anchor.y()+" "+anchor.z(),"buttons",List.of()));
            forms.add(form("ship-submit",LocalText.tr("Подати на схвалення"),LocalText.tr("Назва корабля")));
        }else if(section.equals("ship-assign")){
            plugin.ships().service.administrator(plugin.ships().actor(p));var application=plugin.ships().service.application(session.shipApplication);
            require(string(application,"state").equals("ASSEMBLED"),LocalText.tr("Корабель ще не зібраний або вже оформлений."));data.put("title",LocalText.tr("Оформлення корабля"));
            rows.add(Map.of("id",session.shipApplication,"title","#"+number(application,"ship")+" «"+string(application,"name")+"»","detail",LocalText.tr("Заявник: ")+towny.playerName(string(application,"applicant")),"buttons",List.of()));
            var schema=new LinkedHashMap<String,Object>(form("ship-finalize",LocalText.tr("Підтвердити місто та назву"),LocalText.tr("Місто-власник"),LocalText.tr("Назва корабля")));schema.put("defaults",List.of(cityName(string(application,"requested_town")),string(application,"name")));forms.add(schema);
        }else if(section.equals("fleet")){
            rows.add(Map.of("id","ship-request","title",LocalText.tr("Реєстрація нового корабля"),"detail",LocalText.tr("Наведіться на блок корабля та подайте заявку з назвою."),"buttons",List.of(button(LocalText.tr("Подати заявку"),"new-ship-request","",""))));
            for(var row:fleet.db.query("SELECT * FROM naval_ships WHERE town=? ORDER BY name",home==null?"":home)){
                long id=number(row,"id");String captain=string(row,"captain");var lock=fleet.db.one("SELECT offer FROM naval_locks WHERE ship=?",id);
                List<Map<String,Object>> actions=new ArrayList<>();if(fleet.manages(actor(p),home))actions.add(button(LocalText.tr("Керувати"),"manage-ship","",""+id));actions.add(button(LocalText.tr("Морський прапор"),"flags","",""+id));
                rows.add(Map.of("id",""+id,"title","«"+string(row,"name")+"»","detail",LocalText.tr("Капітан: ")+(captain.isEmpty()?LocalText.tr("не призначений"):towny.playerName(captain))+LocalText.tr("; команда: ")+fleet.db.query("SELECT person FROM naval_crew WHERE ship=?",id).size()+(lock==null?"":LocalText.tr("; на продажі")),"buttons",actions));
            }
            for(var row:fleet.db.query("SELECT * FROM naval_offers WHERE kind='DIRECT' AND state='OPEN' AND (buyer=? OR seller=?)",home,home))addOffer(rows,row);
        }else if(section.equals("market") || section.equals("auctions")){
            String market=currentMarket(p);counter(p,market);data.put("market",market);
            for(var row:fleet.db.query("SELECT * FROM naval_offers WHERE market=? AND kind=? AND state='OPEN' ORDER BY created",market,section.equals("auctions")?"AUCTION":"SALE"))addOffer(rows,row);
            forms.add(form("sell",LocalText.tr("Виставити на продаж"),LocalText.tr("ID корабля"),LocalText.tr("ID ринку"),LocalText.tr("Ціна"),LocalText.tr("Код валюти"),LocalText.tr("Строк у годинах"),LocalText.tr("Опис продавця")));
            forms.add(form("auction",LocalText.tr("Виставити на аукціон"),LocalText.tr("ID корабля"),LocalText.tr("ID ринку"),LocalText.tr("Початкова ціна"),LocalText.tr("Крок ставки"),LocalText.tr("Код валюти"),LocalText.tr("Строк у годинах"),LocalText.tr("Опис продавця")));
            forms.add(form("bid",LocalText.tr("Зробити ставку"),LocalText.tr("ID оголошення"),LocalText.tr("Сума ставки")));forms.add(form("cancel",LocalText.tr("Зняти з продажу"),LocalText.tr("ID оголошення")));
        }else if(section.equals("manage")){
            forms.add(form("name",LocalText.tr("Назвати корабель"),LocalText.tr("ID корабля"),LocalText.tr("Назва")));forms.add(form("captain",LocalText.tr("Призначити капітана"),LocalText.tr("ID корабля"),LocalText.tr("Гравець або - для звільнення")));
            forms.add(form("crew-add",LocalText.tr("Додати до команди"),LocalText.tr("ID корабля"),LocalText.tr("Гравець")));forms.add(form("crew-remove",LocalText.tr("Прибрати з команди"),LocalText.tr("ID корабля"),LocalText.tr("Гравець")));
            forms.add(form("admiral",LocalText.tr("Призначити адмірала"),LocalText.tr("Гравець або - для звільнення")));forms.add(form("port",LocalText.tr("Продаж кораблів і мирна гавань"),LocalText.tr("ID ринку"),LocalText.tr("on або off")));
            forms.add(form("direct",LocalText.tr("Запропонувати прямий обмін"),LocalText.tr("ID корабля"),LocalText.tr("Місто-покупець"),LocalText.tr("Ціна"),LocalText.tr("Код валюти"),LocalText.tr("Опис")));forms.add(form("cancel",LocalText.tr("Скасувати оголошення"),LocalText.tr("ID оголошення")));
            if(p.hasPermission(ShipRegistrationController.ADMIN))forms.add(form("ship-owner",LocalText.tr("Змінити місто-власника"),LocalText.tr("ID корабля"),LocalText.tr("Місто-власник")));
        }else if(section.equals("flags")){
            data.put("title",LocalText.tr("Морські прапори"));String selected=session.flagShip==null?"":session.flagShip.toString();
            if(session.flagShip!=null){var state=plugin.engagement().nativeCall("status",Map.of("ship",session.flagShip,"player",p.getUniqueId().toString()));rows.add(Map.of("id",selected,"title","#"+selected+" «"+string(fleet.ship(session.flagShip),"name")+"»","detail",LocalText.tr("Поточний прапор: ")+LocalText.tr(switch(state.get("mode").toString()){case "STATE"->LocalText.tr("Державний");case "PEACEFUL"->LocalText.tr("Мирний");default->LocalText.tr("Піратський");})+(Boolean.TRUE.equals(state.get("combat"))?LocalText.tr("; бойовий режим активний"):""),"buttons",List.of()));}
            for(String mode:List.of("peaceful","state","pirate")){var schema=new LinkedHashMap<String,Object>(form("flag-"+mode,LocalText.tr(switch(mode){case "peaceful"->LocalText.tr("Підняти мирний прапор");case "state"->LocalText.tr("Підняти державний прапор");default->LocalText.tr("Підняти піратський прапор");}),LocalText.tr("ID корабля")));schema.put("defaults",List.of(selected));forms.add(schema);}
            forms.add(form("ensign-banner",LocalText.tr("Створити державний прапор із знамена")));forms.add(form("ensign-painting",LocalText.tr("Створити державний прапор із картини")));
        }else if(section.equals("sightings")){
            for(var row:fleet.db.query("SELECT * FROM naval_seen WHERE person=? ORDER BY observed DESC LIMIT 256",p.getUniqueId().toString())){
                boolean hidden=bool(row,"hidden");rows.add(Map.of("id",""+number(row,"ship"),"title",string(row,"name"),"detail",cityName(string(row,"town"))+LocalText.tr("; останнє спостереження ")+CityMarketService.time(number(row,"observed"))+(hidden?LocalText.tr("; приховано"):""),"buttons",List.of(button(hidden?LocalText.tr("Показувати знову"):LocalText.tr("Приховати мітку"),hidden?"restore":"hide","",""+number(row,"ship")))));
            }
        }
        int total=Math.max(1,(rows.size()+9)/10);page=Math.max(1,Math.min(total,page));data.put("page",page);data.put("pages",total);data.put("rows",rows.subList((page-1)*10,Math.min(rows.size(),page*10)));data.put("forms",forms);
        if(data.containsKey("market"))for(int i=0;i<forms.size();i++){
            var schema=new LinkedHashMap<String,Object>(forms.get(i));String id=schema.get("id").toString();
            if(id.equals("sell"))schema.put("defaults",List.of("",data.get("market"),"","","72",""));
            if(id.equals("auction"))schema.put("defaults",List.of("",data.get("market"),"","","","24",""));forms.set(i,schema);
        }
        if(session.selectedShip!=null)data.put("selectedShip",session.selectedShip.toString());
        if(forms.stream().anyMatch(f->f.containsKey("shipField"))){
            List<Map<String,Object>> choices=new ArrayList<>();for(var choice:fleet.choices(selector(p))){
                if(choices.size()>=256)break;
                choices.add(Map.of("id",Long.toString(choice.id()),"name",choice.name(),"town",cityName(choice.town()),"uses",choice.uses().stream().map(Enum::name).sorted().toList()));
            }data.put("ships",choices);
        }
        if(forms.stream().anyMatch(f->f.containsKey("personField")))data.put("players",TownyAPI.getInstance().getResidents().stream().sorted(Comparator.comparing(r->r.getName().toLowerCase(Locale.ROOT))).limit(256).map(r->Map.of("id",r.getUUID().toString(),"name",r.getName())).toList());
        if(session.prepared!=null)data.put("confirmation",Map.of("token",session.prepared.token,"text",session.prepared.summary));return data;
    }
    }
    private void addOffer(List<Map<String,Object>> rows,Map<String,Object> offer)throws Exception {
        String id=string(offer,"id"),kind=string(offer,"kind");long ship=number(offer,"ship");List<Map<String,Object>> buttons=new ArrayList<>();
        buttons.add(button(LocalText.tr("Подивитися"),"look","",id));if(!kind.equals("AUCTION"))buttons.add(button(LocalText.tr("Купити від міста"),"prepare","buy",id));else buttons.add(button(LocalText.tr("Зробити ставку"),"form","bid",id));
        rows.add(Map.of("id",id,"title","#"+ship+" «"+string(fleet.ship(ship),"name")+"» · "+format(number(offer,kind.equals("AUCTION")&&!string(offer,"bidder").isEmpty()?"bid":"price"))+" "+string(offer,"code"),"detail",cityName(string(offer,"seller"))+". "+string(offer,"information")+". "+string(offer,"description")+LocalText.tr(". До ")+CityMarketService.time(number(offer,"ends")),"buttons",buttons));
    }
    public List<Map<String,Object>> marks(UUID player)throws Exception {
        List<Map<String,Object>> out=new ArrayList<>();for(var row:fleet.sightings(player.toString())){
            var world=Bukkit.getWorld(UUID.fromString(string(row,"world")));if(world==null)continue;
            out.add(Map.of("ship",number(row,"ship"),"name",string(row,"name"),"dimension",world.getKey().toString(),"x",row.get("x"),"y",row.get("y"),"z",row.get("z"),"observed",number(row,"observed")));
        }return out;
    }
    public void tick()throws Exception {
        var hulls=world.all();for(var raw:hulls){long id=((Number)raw.get("id")).longValue();ensure(id);
            String owner=string(fleet.ship(id),"town");
            if(fleet.ownerAvailable(owner))unavailableOwners.remove(id);
            else if(!owner.equals(unavailableOwners.put(id,owner)))plugin.getLogger().warning("Ship #"+id+" retains unavailable owner "+owner+"; control and combat are locked. An administrator can reassign it with /shipadmin owner.");
        }fleet.tick();reconcile();
        List<Map.Entry<Player,FleetService.Hull>> candidates=new ArrayList<>();
        for(Player p:Bukkit.getOnlinePlayers())for(var raw:hulls){var hull=FleetWorld.hull(raw);String home=home(p);
            if(hull!=null&&hull.loaded()&&home!=null&&hull.world().equals(p.getWorld().getUID().toString())&&FleetGeometry.distance(hull.footprint(),p.getLocation().getX(),p.getLocation().getZ())<=192){
                String owner=string(fleet.ship(hull.id()),"town");if(!owner.isBlank() && world.allied(home,owner)&&!world.war(home,owner))candidates.add(Map.entry(p,hull));
            }
        }
        Set<String> fresh=new HashSet<>();
        for(int i=0;i<Math.min(128,candidates.size());i++){var candidate=candidates.get(Math.floorMod(sightCursor++,candidates.size()));String who=candidate.getKey().getUniqueId().toString();if(fleet.seen(who,candidate.getValue()))fresh.add(who+":"+candidate.getValue().id());}
        List<Map.Entry<String,Long>> missing=new ArrayList<>();
        for(Player p:Bukkit.getOnlinePlayers()){
            String who=p.getUniqueId().toString();var location=p.getLocation();
            for(var old:fleet.db.query("SELECT * FROM naval_seen WHERE person=? AND hidden=FALSE ORDER BY observed DESC LIMIT 256",who)){
                long ship=number(old,"ship");if(fresh.contains(who+":"+ship)||!string(old,"world").equals(p.getWorld().getUID().toString()))continue;
                double dx=((Number)old.get("x")).doubleValue()-location.getX(),dz=((Number)old.get("z")).doubleValue()-location.getZ();
                if(dx*dx+dz*dz<=48*48)missing.add(Map.entry(who,ship));
            }
        }
        for(int i=0;i<Math.min(32,missing.size());i++){var entry=missing.get(Math.floorMod(missingCursor++,missing.size()));fleet.checkMissing(entry.getKey(),entry.getValue());}
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
        require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));
        if(args.length==1){open(p,"manage");return true;}
        require(args.length>=5,LocalText.tr("/trade ship <ID корабля> <місто-покупець> <ціна> <валюта> [опис]"));
        var values=canonical(p,"direct",List.of(args[1],args[2],args[3],args[4],args.length>5?String.join(" ",Arrays.copyOfRange(args,5,args.length)):""));
        Session session=open(p,"fleet");String summary=perform(p,req(),"direct",values,false,"");session.prepared=new Prepared(req(),req(),"direct",values,actionStamp(p,"direct",values),Objects.toString(home(p),""),System.currentTimeMillis()+30_000,summary);return true;
    }
    private static String req(){return UUID.randomUUID().toString();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try(var commandLanguage=LocalText.use(sender)){try{
        require(sender instanceof Player,LocalText.tr("Команда доступна гравцю."));Player p=(Player)sender;require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу на міський флот."));
        if(args.length==0 || args[0].equals("menu")){open(p,"fleet");return true;}
        if(args[0].equals("market")){openMarket(p);return true;}
        if(args[0].equals("flag")){long id=args.length>1?fleet.resolveShip(selector(p),joined(args,1,args.length),FleetService.ShipUse.CONTROL):((Number)plugin.engagement().nativeCall("look",Map.of("player",p.getUniqueId().toString())).get("ship")).longValue();openFlags(p,id);return true;}
        if(args[0].equals("assign")){require(p.hasPermission(ShipRegistrationController.ADMIN)&&args.length>=3,LocalText.tr("/fleet assign <назва корабля> <місто> — лише адміністратор."));plugin.ships().owner(plugin.ships().actor(p),req(),fleet.resolveShip(selector(p),joined(args,1,args.length-1),FleetService.ShipUse.ADMIN),town(args[args.length-1]));plugin.message(p,LocalText.tr("Місто-власника призначено."));return true;}
        if(args[0].equals("map")){if(args.length>=3){require(Set.of("show","hide").contains(args[1]),LocalText.tr("/fleet map <show|hide> <назва корабля>"));fleet.hide(p.getUniqueId().toString(),fleet.resolveShip(selector(p),joined(args,2,args.length),FleetService.ShipUse.MAP),args[1].equals("hide"));plugin.message(p,LocalText.tr("Мітку оновлено."));}else open(p,"sightings");return true;}
        require(args[0].equals("edit"),LocalText.tr("/fleet — флот; /fleet edit [назва] — керування; /fleet flag [назва] — прапор; /fleet map — спостереження."));Session session=open(p,"manage");if(args.length>1)session.selectedShip=fleet.resolveShip(selector(p),joined(args,1,args.length),FleetService.ShipUse.MANAGE);return true;
    }catch(Exception ex){plugin.message(sender,Objects.toString(ex.getMessage(),LocalText.tr("Дія недоступна.")));return true;}}}
    private static String joined(String[] args,int start,int end){return String.join(" ",Arrays.copyOfRange(args,start,end));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] args){
        if(!(s instanceof Player p)||!p.hasPermission("empirecraft.feudal.use"))return List.of();
        if(args.length==1){List<String> roots=new ArrayList<>(List.of("menu","market","map","edit","flag"));if(p.hasPermission(ShipRegistrationController.ADMIN))roots.add("assign");return roots.stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
        if(args[0].equals("map")&&args.length==2)return List.of("show","hide").stream().filter(v->v.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        FleetService.ShipUse use=switch(args[0]){case "edit"->FleetService.ShipUse.MANAGE;case "flag"->FleetService.ShipUse.CONTROL;case "assign"->FleetService.ShipUse.ADMIN;case "map"->FleetService.ShipUse.MAP;default->null;};
        if(use==null)return List.of();int start=args[0].equals("map")?2:1;if(args.length<=start)return List.of();
        try{String prefix=joined(args,start,args.length),before=joined(args,start,args.length-1);if(!before.isEmpty())before+=" ";final String consumed=before;
            return fleet.choices(selector(p)).stream().filter(v->v.uses().contains(use)&&v.name().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).map(v->v.name().substring(Math.min(consumed.length(),v.name().length()))).distinct().limit(100).toList();
        }catch(Exception unavailable){return List.of();}
    }
}
