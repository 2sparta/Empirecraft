package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Canonical city ownership and money share one durable database commit. Native ships are projections. */
public final class FleetService {
    public record Hull(long id,String world,List<FleetGeometry.Point> footprint,double x,double y,double z,String nativeTown,String name,String information,boolean loaded) {}
    public interface World {
        Hull hull(long id);
        String hometown(String person);
        boolean person(String id);
        default boolean mayor(String person,String town){return true;}
        default boolean direct(String seller,String buyer){return true;}
        default boolean visible(String person,Hull hull){return false;}
        boolean allied(String a,String b);
        boolean war(String a,String b);
        boolean republic(String town);
        Set<String> territories(Hull hull);
        boolean docked(Hull hull,String town,Place counter);
    }
    public record Policy(boolean protect,boolean allow,boolean locked,String owner) {}
    final CityMarketService m;final FeudalService s;final Database db;public final World world;
    private int offerCursor;
    public FleetService(CityMarketService markets,World world)throws SQLException {
        m=markets;s=m.s;db=s.db;this.world=world;
        for(String ddl:List.of(
            "CREATE TABLE IF NOT EXISTS naval_ships(id BIGINT PRIMARY KEY,town VARCHAR(80),founder VARCHAR(36),name VARCHAR(64),captain VARCHAR(36) DEFAULT '',revision BIGINT DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS naval_crew(ship BIGINT,person VARCHAR(36),PRIMARY KEY(ship,person))",
            "CREATE TABLE IF NOT EXISTS naval_admirals(town VARCHAR(80) PRIMARY KEY,person VARCHAR(36))",
            "CREATE TABLE IF NOT EXISTS naval_ports(market VARCHAR(36) PRIMARY KEY,enabled BOOLEAN)",
            "CREATE TABLE IF NOT EXISTS naval_offers(id VARCHAR(36) PRIMARY KEY,ship BIGINT,seller VARCHAR(80),buyer VARCHAR(80),market VARCHAR(36),kind VARCHAR(12),state VARCHAR(16),description VARCHAR(2000),information VARCHAR(2000),code VARCHAR(8),price BIGINT,step BIGINT,created BIGINT,ends BIGINT,bidder VARCHAR(80) DEFAULT '',bid BIGINT DEFAULT 0,buyer_bps INT DEFAULT 0,seller_bps INT DEFAULT 0,revision BIGINT DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS naval_locks(ship BIGINT PRIMARY KEY,offer VARCHAR(36) UNIQUE)",
            "CREATE TABLE IF NOT EXISTS naval_seen(person VARCHAR(36),ship BIGINT,world VARCHAR(100),x DOUBLE PRECISION,y DOUBLE PRECISION,z DOUBLE PRECISION,name VARCHAR(64),town VARCHAR(80),observed BIGINT,hidden BOOLEAN DEFAULT FALSE,PRIMARY KEY(person,ship))"
        ))db.execute(ddl);
    }
    public Map<String,Object> ship(long id)throws SQLException {var row=db.one("SELECT * FROM naval_ships WHERE id=?",id);require(row!=null,"Корабель не зареєстрований у флоті.");return row;}
    public void register(Hull hull,String town,String founder){db.atomic(()->{
        if(db.one("SELECT id FROM naval_ships WHERE id=?",hull.id)!=null)return null;
        require(town==null || town.isEmpty() || s.estate(town).active(),"Місто-власник недоступне.");
        String name=hull.name==null || hull.name.isBlank()?"Корабель "+hull.id:hull.name;
        db.execute("INSERT INTO naval_ships(id,town,founder,name) VALUES(?,?,?,?)",hull.id,town==null?"":town,founder==null?"":founder,name.substring(0,Math.min(64,name.length())));
        return null;
    });}
    boolean mayor(Actor actor,String town)throws SQLException {return town!=null && !town.isBlank() && s.estate(town).active() && actor.id().equals(s.estate(town).lord()) && world.mayor(actor.id(),town);}
    public boolean manages(Actor actor,String town)throws SQLException {
        if(mayor(actor,town))return true;
        return Objects.equals(world.hometown(actor.id()),town) && db.one("SELECT person FROM naval_admirals WHERE town=? AND person=?",town,actor.id())!=null;
    }
    public boolean controls(String actor,long id)throws SQLException {
        if(actor==null)return false;var row=ship(id);return manages(new Actor(actor,false),string(row,"town")) || actor.equals(string(row,"captain")) || db.one("SELECT person FROM naval_crew WHERE ship=? AND person=?",id,actor)!=null;
    }
    void unlocked(long id)throws SQLException {require(db.one("SELECT ship FROM naval_locks WHERE ship=?",id)==null,"Корабель зарезервований для продажу; спершу зніміть оголошення.");}
    public String admiral(Actor actor,String request,String town,String person){return m.once(actor,request,"naval-admiral|"+town+person,()->{
        require(mayor(actor,town),"Адмірала призначає чинний мер міста.");
        db.execute("DELETE FROM naval_admirals WHERE town=?",town);
        if(!person.isBlank()){require(world.person(person) && town.equals(world.hometown(person)),"Адмірал має бути мешканцем цього міста.");db.execute("INSERT INTO naval_admirals VALUES(?,?)",town,person);}
        s.audit(actor,"FLEET_ADMIRAL",town+" "+person);return town;
    });}
    public String edit(Actor actor,String request,long id,String action,String value){return m.once(actor,request,"naval-edit|"+id+action+value,()->{
        var row=ship(id);require(manages(actor,string(row,"town")),"Потрібен мер або чинний адмірал міста-власника.");unlocked(id);
        switch(action){
            case "name" -> {require(!value.isBlank() && value.length()<=64 && value.codePoints().noneMatch(Character::isISOControl) && !value.contains("§"),"Назва: 1–64 звичайні символи.");db.execute("UPDATE naval_ships SET name=? WHERE id=?",value,id);}
            case "captain" -> {require(value.isBlank() || world.person(value),"Мешканця не знайдено.");db.execute("UPDATE naval_ships SET captain=? WHERE id=?",value,id);}
            case "crew-add" -> {require(world.person(value),"Мешканця не знайдено.");require(db.query("SELECT person FROM naval_crew WHERE ship=?",id).size()<128,"Ліміт команди: 128 осіб.");db.execute("MERGE INTO naval_crew KEY(ship,person) VALUES(?,?)",id,value);}
            case "crew-remove" -> db.execute("DELETE FROM naval_crew WHERE ship=? AND person=?",id,value);
            default -> throw new IllegalArgumentException("Невідома дія флоту.");
        }
        db.execute("UPDATE naval_ships SET revision=revision+1 WHERE id=?",id);s.audit(actor,"FLEET_"+action.toUpperCase(Locale.ROOT),id+" "+value);return ""+id;
    });}
    public String assign(Actor actor,String request,long id,String town){return m.once(actor,request,"naval-assign|"+id+town,()->{
        var row=ship(id);require(s.estate(town).active() && town.startsWith("t:"),"Виберіть активне місто.");
        require(actor.admin() || string(row,"town").isEmpty() && actor.id().equals(string(row,"founder")) && town.equals(world.hometown(actor.id())),"Особистий корабель реєструє його засновник у своє місто; іншу передачу виконує адміністратор.");
        var lock=db.one("SELECT offer FROM naval_locks WHERE ship=?",id);if(lock!=null)cancelWork(offer(string(lock,"offer")),"CANCELLED");
        db.execute("UPDATE naval_ships SET town=?,captain='',revision=revision+1 WHERE id=?",town,id);db.execute("DELETE FROM naval_crew WHERE ship=?",id);
        s.audit(actor,"SHIP_REGISTER",id+" "+string(row,"town")+" -> "+town);return ""+id;
    });}
    public String port(Actor actor,String request,String market,boolean enabled){return m.once(actor,request,"naval-port|"+market+enabled,()->{
        String town=string(m.market(market),"town");require(mayor(actor,town),"Продаж кораблів дозволяє мер міста ринку.");
        if(enabled)require(world.republic(town) && m.open(market,Department.GOODS),"Потрібен відкритий базар торговельної республіки.");
        db.execute("MERGE INTO naval_ports KEY(market) VALUES(?,?)",market,enabled);m.event(actor,market,"SHIP_SALES",Boolean.toString(enabled));
        if(!enabled)for(var offer:db.query("SELECT * FROM naval_offers WHERE market=? AND state='OPEN'",market))cancelWork(offer,"CANCELLED");
        return market;
    });}
    public boolean portOpen(String market)throws SQLException {
        var row=db.one("SELECT enabled FROM naval_ports WHERE market=?",market);
        return row!=null && bool(row,"enabled") && world.republic(string(m.market(market),"town")) && m.open(market,Department.GOODS);
    }
    public boolean eligible(long id,String market)throws SQLException {
        if(!portOpen(market))return false;var vessel=ship(id);Hull hull=world.hull(id);if(hull==null)return false;
        var host=m.market(market);String town=string(host,"town");if(world.war(string(vessel,"town"),town) || !hull.world.equals(string(host,"world")))return false;
        var counter=m.department(market,Department.GOODS);
        return world.docked(hull,town,new Place(hull.world,number(counter,"cx")+.5,number(counter,"cy")+.5,number(counter,"cz")+.5));
    }
    public Map<String,Object> offer(String id)throws SQLException {var row=db.one("SELECT * FROM naval_offers WHERE id=?",id);require(row!=null,"Оголошення про корабель не знайдено.");return row;}
    public String list(Actor actor,String request,long id,String market,String kind,String buyer,String code,long price,long step,int hours,String description){return m.once(actor,request,"naval-list|"+id+market+kind+buyer+code+price+step+hours+description,()->{
        var vessel=ship(id);String seller=string(vessel,"town");require(mayor(actor,seller),"Продавати корабель може чинний мер міста-власника.");unlocked(id);
        require(Set.of("DIRECT","SALE","AUCTION").contains(kind),"Невідомий спосіб продажу.");require(price>0 && price<=CurrencyService.MAX_MONEY && step>=1 && step<=CurrencyService.MAX_MONEY,"Ціна й крок мають бути додатними.");
        require(hours>=1 && hours<=168 && description.length()<=2000,"Строк: 1–168 годин; опис: до 2000 символів.");s.currency(code);
        Hull hull=world.hull(id);require(hull!=null,"Корабель недоступний.");
        int sellerBps=0;String destination=kind.equals("DIRECT")?buyer:"";
        if(kind.equals("DIRECT")){require(!seller.equals(buyer) && s.estate(buyer).active() && buyer.startsWith("t:"),"Виберіть інше активне місто-покупця.");require(market.isEmpty(),"Прямий обмін не прив'язаний до ринку.");}
        else {require(eligible(id,market),"Корабель має бути в приваті торговельної республіки й не далі 200 блоків від базару.");m.payment(market,code);sellerBps=(int)number(m.tariff(market),"seller");}
        String key=id();db.execute("INSERT INTO naval_offers(id,ship,seller,buyer,market,kind,state,description,information,code,price,step,created,ends,seller_bps) VALUES(?,?,?,?,?,?,'OPEN',?,?,?,?,?,?,?,?)",key,id,seller,destination,market,kind,description,hull.information,code,price,step,s.now(),s.now()+(kind.equals("DIRECT")?30_000:hours*3_600_000L),sellerBps);
        db.execute("INSERT INTO naval_locks VALUES(?,?)",id,key);s.audit(actor,"SHIP_LIST",key+" ship="+id+" "+kind);return key;
    });}
    boolean valid(Map<String,Object> offer)throws SQLException {
        long id=number(offer,"ship");var vessel=ship(id);if(!string(vessel,"town").equals(string(offer,"seller")) || !s.estate(string(offer,"seller")).active() || world.hull(id)==null)return false;
        if(string(offer,"kind").equals("DIRECT"))return s.estate(string(offer,"buyer")).active();
        if(!eligible(id,string(offer,"market")))return false;
        var permit=db.one("SELECT payment FROM cm_permits WHERE market=? AND code=?",string(offer,"market"),string(offer,"code"));return permit!=null && bool(permit,"payment");
    }
    public String stamp(String id)throws SQLException {
        var row=offer(id);String market=string(row,"market");
        return hash(row.toString()+"|"+ship(number(row,"ship")).get("revision")+"|"+(market.isEmpty()?"":m.tariff(market).toString()+"|"+m.market(market).get("version")));
    }
    public String buy(Actor actor,String request,String id,String buyingTown,String expected){return m.once(actor,request,"naval-buy|"+id+buyingTown+expected,()->{
        var row=offer(id);require(stamp(id).equals(expected),"Умови продажу змінилися. Перегляньте їх повторно.");
        require("OPEN".equals(string(row,"state")) && !"AUCTION".equals(string(row,"kind")) && number(row,"ends")>s.now() && valid(row),"Корабель уже недоступний для купівлі.");
        require(mayor(actor,buyingTown) && !buyingTown.equals(string(row,"seller")),"Купівлю від міста підтверджує його чинний мер.");
        if(string(row,"kind").equals("DIRECT")){require(buyingTown.equals(string(row,"buyer")),"Обмін запропоновано іншому місту.");require(world.direct(string(row,"seller"),actor.id()),"Мери обох міст мають бути поруч для прямого обміну.");}
        long price=number(row,"price");int buyerBps=string(row,"market").isEmpty()?0:(int)number(m.tariff(string(row,"market")),"buyer");
        m.move("e:"+buyingTown,escrow(id),string(row,"code"),Math.addExact(price,fee(price,buyerBps)));
        transfer(row,buyingTown,price,buyerBps);return id;
    });}
    public String bid(Actor actor,String request,String id,String town,long amount,String expected){return m.once(actor,request,"naval-bid|"+id+town+amount+expected,()->{
        var row=offer(id);require(stamp(id).equals(expected),"Ставки змінилися. Перегляньте аукціон повторно.");
        require("OPEN".equals(string(row,"state")) && "AUCTION".equals(string(row,"kind")) && number(row,"ends")>s.now() && valid(row),"Аукціон корабля завершений або недоступний.");
        require(mayor(actor,town) && !town.equals(string(row,"seller")),"Ставку від іншого міста підтверджує його мер.");
        long minimum=string(row,"bidder").isEmpty()?number(row,"price"):Math.addExact(number(row,"bid"),number(row,"step"));require(amount>=minimum && amount<=CurrencyService.MAX_MONEY,"Ставка замала або завелика.");
        int bps=(int)number(m.tariff(string(row,"market")),"buyer");long needed=Math.addExact(amount,fee(amount,bps)),held=s.balance(escrow(id),string(row,"code"));
        String previous=string(row,"bidder");
        if(!previous.isEmpty() && !previous.equals(town)){m.move(escrow(id),"e:"+previous,string(row,"code"),held);held=0;}
        if(needed>held)m.move("e:"+town,escrow(id),string(row,"code"),needed-held);else if(held>needed)m.move(escrow(id),"e:"+town,string(row,"code"),held-needed);
        db.execute("UPDATE naval_offers SET bidder=?,bid=?,buyer_bps=?,revision=revision+1 WHERE id=?",town,amount,bps,id);s.audit(actor,"SHIP_BID",id+" "+town+" "+amount);return id;
    });}
    private static String escrow(String id){return "m:ship:"+id;}
    private void transfer(Map<String,Object> row,String town,long price,int buyerBps)throws SQLException {
        String id=string(row,"id"),code=string(row,"code"),seller=string(row,"seller"),market=string(row,"market");
        long sellerFee=fee(price,(int)number(row,"seller_bps")),buyerFee=fee(price,buyerBps);
        m.move(escrow(id),"e:"+seller,code,price-sellerFee);
        if(sellerFee+buyerFee>0)m.move(escrow(id),"e:"+string(m.market(market),"town"),code,sellerFee+buyerFee);
        long ship=number(row,"ship");db.execute("UPDATE naval_ships SET town=?,captain='',revision=revision+1 WHERE id=?",town,ship);db.execute("DELETE FROM naval_crew WHERE ship=?",ship);
        db.execute("DELETE FROM naval_locks WHERE ship=? AND offer=?",ship,id);db.execute("UPDATE naval_offers SET state='SOLD',buyer=?,revision=revision+1 WHERE id=?",town,id);
        s.audit(new Actor("fleet-settlement",true),"SHIP_TRANSFER",id+" "+seller+" -> "+town);
    }
    private void cancelWork(Map<String,Object> row,String state)throws SQLException {
        if(!"OPEN".equals(string(row,"state")))return;String id=string(row,"id"),bidder=string(row,"bidder");
        long held=s.balance(escrow(id),string(row,"code"));if(held>0){require(!bidder.isEmpty(),"Невідомий власник резерву ставки.");m.move(escrow(id),"e:"+bidder,string(row,"code"),held);}
        db.execute("UPDATE naval_offers SET state=?,revision=revision+1 WHERE id=?",state,id);db.execute("DELETE FROM naval_locks WHERE ship=? AND offer=?",number(row,"ship"),id);
        s.audit(new Actor("fleet-settlement",true),"SHIP_"+state,id);
    }
    public String cancel(Actor actor,String request,String id){return m.once(actor,request,"naval-cancel|"+id,()->{
        var row=offer(id);require(mayor(actor,string(row,"seller")),"Оголошення скасовує мер міста-продавця.");require(string(row,"bidder").isEmpty(),"Після першої ставки аукціон очікує завершення.");cancelWork(row,"CANCELLED");return id;
    });}
    private void settleWork(Map<String,Object> row)throws SQLException {
        if(!"OPEN".equals(string(row,"state")))return;
        if(!valid(row)){cancelWork(row,"CANCELLED");return;}
        if(number(row,"ends")<=s.now()){
            if("AUCTION".equals(string(row,"kind")) && !string(row,"bidder").isEmpty() && s.estate(string(row,"bidder")).active())transfer(row,string(row,"bidder"),number(row,"bid"),(int)number(row,"buyer_bps"));
            else cancelWork(row,"EXPIRED");
        }
    }
    public void tick(){db.atomic(()->{
        var rows=db.query("SELECT * FROM naval_offers WHERE state='OPEN' ORDER BY created,id LIMIT 500 OFFSET ?",offerCursor);
        for(var row:rows)settleWork(row);offerCursor=rows.size()<500?0:offerCursor+500;return null;
    });}
    public Policy policy(long id,String actor)throws SQLException {
        var vessel=ship(id);String owner=string(vessel,"town");Hull hull=world.hull(id);if(hull==null)return new Policy(true,false,true,owner);
        var lock=db.one("SELECT offer FROM naval_locks WHERE ship=?",id);
        if(lock!=null){var offer=offer(string(lock,"offer"));if(!valid(offer) || number(offer,"ends")<=s.now()){db.atomic(()->{settleWork(offer);return null;});lock=db.one("SELECT offer FROM naval_locks WHERE ship=?",id);owner=string(ship(id),"town");}}
        if(lock!=null)return new Policy(true,false,true,owner);
        Set<String> claims=world.territories(hull);boolean protect=false;
        for(String territory:claims){
            if(owner.isEmpty() || territory.equals(owner) || !world.war(owner,territory) && world.allied(owner,territory)){protect=true;break;}
            if(!world.war(owner,territory))for(var port:db.query("SELECT p.market FROM naval_ports p JOIN cm_markets m ON m.id=p.market WHERE p.enabled=TRUE AND m.town=?",territory))if(portOpen(string(port,"market"))){protect=true;break;}
        }
        return new Policy(protect,!protect || controls(actor,id),false,owner);
    }
    public void seen(String person,Hull hull){db.atomic(()->{
        var vessel=ship(hull.id);String owner=string(vessel,"town"),home=world.hometown(person);
        if(home==null || world.war(home,owner) || !world.allied(home,owner) || !world.visible(person,hull))return null;
        var old=db.one("SELECT hidden FROM naval_seen WHERE person=? AND ship=?",person,hull.id);if(old!=null && bool(old,"hidden"))return null;
        db.execute("MERGE INTO naval_seen KEY(person,ship) VALUES(?,?,?,?,?,?,?,?,?,FALSE)",person,hull.id,hull.world,hull.x,hull.y,hull.z,string(vessel,"name"),owner,s.now());return null;
    });}
    public void hide(String person,long ship,boolean hidden){db.atomic(()->{require(db.one("SELECT ship FROM naval_seen WHERE person=? AND ship=?",person,ship)!=null,"Спостереження відсутнє.");db.execute("UPDATE naval_seen SET hidden=? WHERE person=? AND ship=?",hidden,person,ship);return null;});}
    public List<Map<String,Object>> sightings(String person)throws SQLException {
        String home=world.hometown(person);List<Map<String,Object>> out=new ArrayList<>();if(home==null)return out;
        for(var row:db.query("SELECT * FROM naval_seen WHERE person=? AND hidden=FALSE ORDER BY observed DESC LIMIT 256",person)){
            var vessel=db.one("SELECT town FROM naval_ships WHERE id=?",number(row,"ship"));
            if(vessel!=null && string(vessel,"town").equals(string(row,"town")) && !world.war(home,string(row,"town")) && world.allied(home,string(row,"town")))out.add(row);
        }return out;
    }
}
