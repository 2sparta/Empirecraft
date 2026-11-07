package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** City events share Contact's durable invitation outbox; prizes never depend on an online organizer. */
public final class MarketFishing {
    final CityMarketService m;
    boolean nativeReady=true;
    java.util.function.Function<String,String> specLabel=spec->spec;
    public enum Rule { COUNT, TOTAL_WEIGHT, LARGEST }
    MarketFishing(CityMarketService m)throws SQLException {
        this.m=m;
        for(String ddl:List.of(
            "CREATE TABLE IF NOT EXISTS cm_fishing(id VARCHAR(36) PRIMARY KEY,rule VARCHAR(20))",
            "CREATE TABLE IF NOT EXISTS cm_fish_scores(event VARCHAR(36),person VARCHAR(36),score BIGINT DEFAULT 0,catches BIGINT DEFAULT 0,reached BIGINT DEFAULT 0,PRIMARY KEY(event,person))",
            "CREATE TABLE IF NOT EXISTS cm_fish_catches(bobber VARCHAR(36) PRIMARY KEY,event VARCHAR(36),person VARCHAR(36),fish VARCHAR(200),weight BIGINT,at_time BIGINT)",
            "CREATE INDEX IF NOT EXISTS cm_fish_scores_person ON cm_fish_scores(person,event)",
            "CREATE TABLE IF NOT EXISTS cm_fish_prizes(id VARCHAR(36) PRIMARY KEY,event VARCHAR(36),rank INT,source VARCHAR(100),code VARCHAR(8),spec VARCHAR(64),quantity BIGINT,state VARCHAR(16),winner VARCHAR(36) DEFAULT '')"))m.db.execute(ddl);
        m.db.execute("ALTER TABLE cm_fishing ADD COLUMN IF NOT EXISTS entry_fee BIGINT DEFAULT 0");
        m.db.execute("ALTER TABLE cm_fishing ADD COLUMN IF NOT EXISTS entry_code VARCHAR(8) DEFAULT ''");
        m.db.execute("ALTER TABLE cm_fishing ADD COLUMN IF NOT EXISTS entry_settled BOOLEAN DEFAULT FALSE");
        m.db.execute("ALTER TABLE cm_fish_scores ADD COLUMN IF NOT EXISTS paid BIGINT DEFAULT 0");
    }
    public Map<String,Object> event(String id)throws SQLException {
        var row=m.db.one("SELECT f.*,s.rule,s.entry_fee,s.entry_code,s.entry_settled FROM cm_fairs f JOIN cm_fishing s ON s.id=f.id WHERE f.id=?",id);
        require(row!=null,LocalText.tr("Турнір з риболовлі не знайдено."));return row;
    }
    void draft(Actor a,String id)throws SQLException {var row=event(id);m.manage(a,string(row,"market"));require(string(row,"state").equals("DRAFT")&&number(row,"starts")>m.s.now(),LocalText.tr("Нагороди й правила змінюються лише до оголошення турніру."));}
    public String create(Actor a,String request,String market,Place p,String name,String description,long starts,int minutes,Rule rule){return m.once(a,request,"fishing-create|"+market+name+description+starts+minutes+rule,()->{
        m.manage(a,market);m.access(market,Department.GOODS,p);
        require(!name.isBlank()&&name.length()<=100&&!description.isBlank()&&description.length()<=2000&&!name.contains("§")&&!description.contains("§"),LocalText.tr("Потрібні назва до 100 і опис до 2000 символів."));
        require(starts>=m.s.now()+300000&&minutes>=5&&minutes<=240&&starts<=m.s.now()+30*FeudalService.DAY,LocalText.tr("Турнір: початок через 5 хвилин — 30 діб, тривалість 5–240 хвилин."));
        long ends=starts+minutes*60000L;String town=string(m.market(market),"town");
        require(m.db.one("SELECT f.id FROM cm_fairs f JOIN cm_markets k ON k.id=f.market WHERE k.town=? AND f.kind='FISHING' AND f.state NOT IN('FINISHED','CANCELLED') AND f.starts<? AND f.ends>?",town,ends,starts)==null,LocalText.tr("У місті вже запланований турнір на цей час."));
        String id=id();m.db.execute("INSERT INTO cm_fairs(id,market,organizer,name,theme,starts,ends,departments,catalog,buyer,seller,jobs,body,state,kind) VALUES(?,?,?,?,?,?,?,'GOODS','*',0,0,0,?,'DRAFT','FISHING')",id,market,a.id(),name.strip(),description.strip(),starts,ends,description.strip());
        m.db.execute("INSERT INTO cm_fishing(id,rule) VALUES(?,?)",id,rule.name());m.event(a,market,"FISHING_CREATE",id);return id;
    });}
    static String fund(String prize){return "fishprize:"+prize;}
    static String entries(String event){return "fishentry:"+event;}
    public String entryFee(Actor a,String request,String id,long fee,String code){return m.once(a,request,"fishing-entry-fee|"+id+fee+code,()->{
        draft(a,id);require(fee>=0&&fee<=CurrencyService.MAX_MONEY,LocalText.tr("Вступний внесок має бути невід’ємним."));if(fee>0)m.payment(string(event(id),"market"),code);
        m.db.execute("UPDATE cm_fishing SET entry_fee=?,entry_code=? WHERE id=?",fee,fee==0?"":code,id);changed(id);return id;
    });}
    void rank(int rank,long quantity){require(rank>=1&&rank<=3&&quantity>0&&quantity<=CurrencyService.MAX_MONEY,LocalText.tr("Нагорода: місце 1–3 і додатна кількість."));}
    public String money(Actor a,String request,String event,int rank,String code,long amount,boolean city){return m.once(a,request,"fishing-money|"+event+rank+code+amount+city,()->{
        draft(a,event);rank(rank,amount);var row=event(event);String market=string(row,"market"),town=string(m.market(market),"town");m.payment(market,code);
        String source=city?"e:"+town:wallet(a);if(city)m.s.controlTreasury(a,m.s.estate(town));
        String id=id();m.move(source,fund(id),code,amount);m.db.execute("INSERT INTO cm_fish_prizes(id,event,rank,source,code,spec,quantity,state) VALUES(?,?,?,?,?,'',?,'HELD')",id,event,rank,source,code,amount);changed(event);return id;
    });}
    public String items(Actor a,String request,String event,int rank,String spec,long quantity,Place p,CommerceService.InventoryChange change){return m.once(a,request,"fishing-items|"+event+rank+spec+quantity,()->{
        draft(a,event);rank(rank,quantity);var row=event(event);m.access(string(row,"market"),Department.GOODS,p);m.spec(spec);
        require(quantity<=4096&&(!bool(m.spec(spec),"unique_lot")||quantity==1)&&change!=null&&change.person().equals(a.id()),LocalText.tr("Передайте до 4096 предметів; унікальна річ — одна."));
        String id=id();m.db.execute("INSERT INTO cm_fish_prizes(id,event,rank,source,code,spec,quantity,state) VALUES(?,?,?,?,'',?,?,'PREPARED')",id,event,rank,wallet(a),spec,quantity);
        String op=m.commerce.intent(a,"CM_IN",id,quantity,List.of(change));m.db.execute("INSERT INTO cm_transfers VALUES(?,'FISH_PRIZE',?,?)",op,id,quantity);changed(event);return op;
    });}
    void changed(String event)throws SQLException {m.db.execute("UPDATE cm_fairs SET version=version+1 WHERE id=?",event);}
    void finish(String id,boolean applied)throws SQLException {
        var row=m.db.one("SELECT * FROM cm_fish_prizes WHERE id=?",id);if(row==null||!string(row,"state").equals("PREPARED"))return;
        m.db.execute("UPDATE cm_fish_prizes SET state=? WHERE id=?",applied?"HELD":"ABORTED",id);
        if(applied&&Set.of("CANCELLED","FINISHED").contains(string(event(string(row,"event")),"state")))pay(row,"");
    }
    void ready(String event)throws SQLException {
        require(nativeReady,LocalText.tr("Серверний модуль заліку Starcatcher ще не готовий."));
        require(m.db.one("SELECT id FROM cm_fish_prizes WHERE event=? AND state='PREPARED'",event)==null&&(m.db.one("SELECT id FROM cm_fish_prizes WHERE event=? AND state='HELD'",event)!=null||number(event(event),"entry_fee")>0),LocalText.tr("Спершу внесіть нагороду або встановіть вступний внесок у призовий фонд."));
    }
    public String announce(Actor a,String request,String id){return m.once(a,request,"fishing-announce|"+id,()->{
        draft(a,id);var row=event(id);require(m.open(string(row,"market"),Department.GOODS),LocalText.tr("Потрібен відкритий базар."));ready(id);
        m.db.execute("UPDATE cm_fairs SET state='ANNOUNCED' WHERE id=?",id);m.event(a,string(row,"market"),"FISHING_ANNOUNCE",id);return id;
    });}
    public String join(Actor a,String request,String id){return m.once(a,request,"fishing-join|"+id,()->{
        UUID.fromString(a.id());var row=event(id);require(Set.of("ANNOUNCED","ACTIVE").contains(string(row,"state"))&&m.s.now()<number(row,"ends")&&m.open(string(row,"market"),Department.GOODS),LocalText.tr("Реєстрація відкрита лише на оголошений чинний турнір."));
        if(m.db.one("SELECT person FROM cm_fish_scores WHERE event=? AND person=?",id,a.id())==null){require(number(m.db.one("SELECT COUNT(*) AS n FROM cm_fish_scores WHERE event=?",id),"n")<2000,LocalText.tr("Ліміт: 2000 учасників."));long fee=number(row,"entry_fee");if(fee>0){m.payment(string(row,"market"),string(row,"entry_code"));m.move(wallet(a),entries(id),string(row,"entry_code"),fee);}m.db.execute("INSERT INTO cm_fish_scores(event,person,paid) VALUES(?,?,?)",id,a.id(),fee);}return id;
    });}
    /** Called only by the native server catch hook, never through a command or a client packet. */
    public boolean caught(String bobber,String person,Place angler,Place hook,String fish,long weight){return m.db.atomic(()->{
        UUID.fromString(bobber);UUID.fromString(person);
        if(weight<0||weight>10000000000L||fish==null||fish.length()>200||!fish.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!angler.world().equals(hook.world())||!Double.isFinite(angler.x()+angler.y()+angler.z()+hook.x()+hook.y()+hook.z()))return false;
        if(m.db.one("SELECT bobber FROM cm_fish_catches WHERE bobber=?",bobber)!=null)return false;
        for(var row:m.db.query("SELECT f.*,k.town,k.world,s.rule FROM cm_fairs f JOIN cm_markets k ON k.id=f.market JOIN cm_fishing s ON s.id=f.id JOIN cm_fish_scores p ON p.event=f.id AND p.person=? WHERE f.state IN('ANNOUNCED','ACTIVE') AND f.starts<=? AND f.ends>?",person,m.s.now(),m.s.now())){
            String market=string(row,"market"),town=string(row,"town");if(!m.open(market,Department.GOODS)||!angler.world().equals(string(row,"world"))||!m.land.owns(town,hook.world(),(int)Math.floor(hook.x())>>4,(int)Math.floor(hook.z())>>4)||!m.land.owns(town,angler.world(),(int)Math.floor(angler.x())>>4,(int)Math.floor(angler.z())>>4))continue;
            String id=string(row,"id");var score=m.db.one("SELECT * FROM cm_fish_scores WHERE event=? AND person=?",id,person);long before=number(score,"score"),after=switch(Rule.valueOf(string(row,"rule"))){case COUNT->Math.addExact(before,1);case TOTAL_WEIGHT->Math.addExact(before,weight);case LARGEST->Math.max(before,weight);};
            m.db.execute("INSERT INTO cm_fish_catches VALUES(?,?,?,?,?,?)",bobber,id,person,fish,weight,m.s.now());m.db.execute("UPDATE cm_fish_scores SET score=?,catches=catches+1,reached=? WHERE event=? AND person=?",after,after>before||number(score,"catches")==0?m.s.now():number(score,"reached"),id,person);return true;
        }return false;
    });}
    public List<Map<String,Object>> scores(String id)throws SQLException {event(id);return m.db.query("SELECT * FROM cm_fish_scores WHERE event=? ORDER BY CASE WHEN catches>0 THEN 0 ELSE 1 END,score DESC,reached,person LIMIT 100",id);}
    public List<Map<String,Object>> prizes(String id)throws SQLException {event(id);return m.db.query("SELECT * FROM cm_fish_prizes WHERE event=? AND state NOT IN('ABORTED') ORDER BY rank,id",id);}
    void pay(Map<String,Object> prize,String winner)throws SQLException {
        String id=string(prize,"id"),source=string(prize,"source"),event=string(prize,"event"),owner=winner.isEmpty()?source:"p:"+winner;
        if(m.db.execute("UPDATE cm_fish_prizes SET state=?,winner=? WHERE id=? AND state='HELD'",winner.isEmpty()?"REFUNDED":"PAID",winner,id)!=1)return;
        if(!string(prize,"code").isEmpty())m.move(fund(id),owner,string(prize,"code"),number(prize,"quantity"));
        else m.asset(string(event(event),"market"),Department.GOODS,owner,string(prize,"spec"),"",number(prize,"quantity"),event,winner.isEmpty()?"FISH_PRIZE_REFUND":"FISH_PRIZE");
    }
    public String cancel(Actor a,String request,String id){return m.once(a,request,"fishing-cancel|"+id,()->{var row=event(id);m.manage(a,string(row,"market"));require(!Set.of("FINISHED","CANCELLED").contains(string(row,"state")),LocalText.tr("Турнір уже закрито."));close(id,false);m.event(a,string(row,"market"),"FISHING_CANCEL",id);return id;});}
    void close(String id,boolean finish)throws SQLException {
        var winners=finish?m.db.query("SELECT person FROM cm_fish_scores WHERE event=? AND catches>0 ORDER BY score DESC,reached,person LIMIT 3",id):List.<Map<String,Object>>of();
        for(var prize:m.db.query("SELECT * FROM cm_fish_prizes WHERE event=? AND state='HELD'",id)){int rank=(int)number(prize,"rank");pay(prize,rank<=winners.size()?string(winners.get(rank-1),"person"):"");}
        var row=event(id);if(!bool(row,"entry_settled")){
            String code=string(row,"entry_code");if(number(row,"entry_fee")>0){if(!winners.isEmpty()){long pool=m.s.balance(entries(id),code);if(pool>0)m.move(entries(id),"p:"+string(winners.get(0),"person"),code,pool);}else for(var entrant:m.db.query("SELECT person,paid FROM cm_fish_scores WHERE event=? AND paid>0",id))m.move(entries(id),"p:"+string(entrant,"person"),code,number(entrant,"paid"));}
            m.db.execute("UPDATE cm_fishing SET entry_settled=TRUE WHERE id=?",id);
        }
        m.db.execute("UPDATE cm_fairs SET state=?,version=version+1 WHERE id=?",finish?"FINISHED":"CANCELLED",id);
    }
    void tick()throws SQLException {
        for(var row:m.db.query("SELECT f.* FROM cm_fairs f JOIN cm_fishing s ON s.id=f.id WHERE f.state NOT IN('FINISHED','CANCELLED')")){
            String id=string(row,"id"),state=string(row,"state");
            if(!m.open(string(row,"market"),Department.GOODS)||Set.of("DRAFT","MAIL_PREPARED").contains(state)&&number(row,"starts")<=m.s.now()){close(id,false);continue;}
            if(number(row,"ends")<=m.s.now()){close(id,true);continue;}
            if(state.equals("ANNOUNCED")&&number(row,"starts")<=m.s.now())m.db.execute("UPDATE cm_fairs SET state='ACTIVE' WHERE id=?",id);
        }
    }
    String description(String id)throws SQLException {
        var row=event(id);StringJoiner out=new StringJoiner("\n");
        out.add(LocalText.tr("Турнір з риболовлі: ")+string(row,"name")).add(string(row,"theme")).add(time(number(row,"starts"))+" — "+time(number(row,"ends"))).add(LocalText.tr("Залік: ")+string(row,"rule"))
            .add(LocalText.tr("Вступний внесок: ")+format(number(row,"entry_fee"))+" "+string(row,"entry_code"))
            .add(LocalText.tr("Усі вступні внески — нагорода першого місця. Без залікового улову або при скасуванні внески повертаються кожному учаснику."))
            .add(LocalText.tr("Лише справжній улов Starcatcher у межах міста після реєстрації. Нічия: раніше досягнутий результат, потім UUID. Нагороди без переможця повертаються організатору."));
        for(var p:prizes(id))out.add("#"+number(p,"rank")+" · "+(string(p,"code").isEmpty()?number(p,"quantity")+" × "+specLabel.apply(string(p,"spec")):format(number(p,"quantity"))+" "+string(p,"code")));return out.toString();
    }
}
