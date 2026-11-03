package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BiPredicate;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Bilateral privileges are signed by both parties and each participating market's municipality. */
public final class MarketTreaties {
    final CityMarketService m;
    public BiPredicate<String,String> citizen;
    MarketTreaties(CityMarketService m)throws SQLException {
        this.m=m;citizen=(person,estate)->m.s.citizen(person,estate);
        for(String sql:List.of(
            "CREATE TABLE IF NOT EXISTS cm_treaties(id VARCHAR(36) PRIMARY KEY,active_version BIGINT DEFAULT 0,pending_version BIGINT DEFAULT 1,terminate_at BIGINT DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS cm_treaty_terms(id VARCHAR(36),version BIGINT,left_party VARCHAR(80),right_party VARCHAR(80),left_market VARCHAR(36),right_market VARCHAR(36),buyer INT,seller INT,jobs INT,quota BIGINT,period_days INT,catalog CLOB,starts BIGINT,ends BIGINT,notice_hours INT,expires BIGINT,state VARCHAR(16),book_mode VARCHAR(8),body CLOB,PRIMARY KEY(id,version))",
            "CREATE TABLE IF NOT EXISTS cm_treaty_signatures(id VARCHAR(36),version BIGINT,party VARCHAR(80),signer VARCHAR(36),PRIMARY KEY(id,version,party))",
            "CREATE TABLE IF NOT EXISTS cm_privilege_reservations(order_id VARCHAR(36) PRIMARY KEY,treaty VARCHAR(36),version BIGINT,party VARCHAR(80),period_start BIGINT,reserved BIGINT,spent BIGINT DEFAULT 0)"))m.db.execute(sql);
    }
    Map<String,Object> terms(String id,long version)throws SQLException {var row=m.db.one("SELECT * FROM cm_treaty_terms WHERE id=? AND version=?",id,version);require(row!=null,"Версію договору не знайдено.");return row;}
    public Map<String,Object> pending(String id)throws SQLException {var h=m.db.one("SELECT * FROM cm_treaties WHERE id=?",id);require(h!=null,"Торговий договір не знайдено.");return terms(id,number(h,"pending_version"));}
    Set<String> required(Map<String,Object> r)throws SQLException {
        var parties=new LinkedHashSet<>(List.of(string(r,"left_party"),string(r,"right_party")));
        for(String key:List.of("left_market","right_market"))parties.add(string(m.market(string(r,key)),"town"));
        return parties;
    }
    void representative(Actor a,Map<String,Object> r)throws SQLException {boolean allowed=false;for(String estate:required(r))if(a.id().equals(m.s.estate(estate).lord()))allowed=true;require(allowed,"Потрібні повноваження чинного представника сторони або міста ринку.");}
    public String create(Actor a,String req,String left,String right,String leftMarket,String rightMarket,Place p,int buyer,int seller,int jobs,long quota,int periodDays,String catalog,long starts,long ends,int notice){return m.once(a,req,"treaty-create|"+left+right+leftMarket+rightMarket+buyer+seller+jobs+quota+periodDays+catalog+starts+ends+notice,()->{
        require(!left.equals(right)&&m.s.estate(left).active()&&m.s.estate(right).active(),"Потрібні дві різні чинні сторони.");
        m.manage(a,leftMarket);m.access(leftMarket,Department.JOBS,p);
        for(int rate:List.of(buyer,seller,jobs))require(rate>=0&&rate<=500,"Тариф 0–500 базисних пунктів.");
        require(quota>0&&quota<=CurrencyService.MAX_MONEY&&periodDays>=1&&periodDays<=30,"Потрібні ліміт і період 1–30 діб.");
        require(starts>=m.s.now()&&ends>starts&&ends-starts<=365*FeudalService.DAY&&notice>=0&&notice<=720,"Перевірте початок, кінець і строк повідомлення.");
        require(catalog.length()<=4000&&!catalog.isBlank(),"Потрібен перелік точних специфікацій або *.");
        for(String market:List.of(leftMarket,rightMarket)){m.market(market);}
        String key=id();m.db.execute("INSERT INTO cm_treaties(id) VALUES(?)",key);
        m.db.execute("INSERT INTO cm_treaty_terms VALUES(?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT','AUTO','')",key,left,right,leftMarket,rightMarket,buyer,seller,jobs,quota,periodDays,catalog,starts,ends,notice,m.s.now()+2*FeudalService.DAY);
        m.event(a,leftMarket,"TREATY_DRAFT",key);return key;
    });}
    public String book(Actor a,String req,String id,String mode,String body){return m.once(a,req,"treaty-book|"+id+mode+body,()->{
        var r=pending(id);representative(a,r);require(!"ACTIVE".equals(string(r,"state")),"Для чинного договору спочатку створіть поправку.");
        require(Set.of("AUTO","MANUAL").contains(mode)&&body.length()<=12000,"Оформлення AUTO/MANUAL, до 12000 символів.");
        long version=number(r,"version")+1;copy(r,version,"DRAFT");m.db.execute("UPDATE cm_treaty_terms SET book_mode=?,body=? WHERE id=? AND version=?",mode,body,id,version);
        m.db.execute("UPDATE cm_treaties SET pending_version=? WHERE id=?",version,id);return id;
    });}
    void copy(Map<String,Object> r,long version,String state)throws SQLException {
        m.db.execute("INSERT INTO cm_treaty_terms VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",string(r,"id"),version,string(r,"left_party"),string(r,"right_party"),string(r,"left_market"),string(r,"right_market"),number(r,"buyer"),number(r,"seller"),number(r,"jobs"),number(r,"quota"),number(r,"period_days"),string(r,"catalog"),number(r,"starts"),number(r,"ends"),number(r,"notice_hours"),m.s.now()+2*FeudalService.DAY,state,string(r,"book_mode"),string(r,"body"));
    }
    public String amend(Actor a,String req,String id,int buyer,int seller,int jobs,long quota,long ends){return m.once(a,req,"treaty-amend|"+id+buyer+seller+jobs+quota+ends,()->{
        var h=m.db.one("SELECT * FROM cm_treaties WHERE id=?",id);require(h!=null&&number(h,"active_version")>0,"Потрібен чинний договір.");
        var old=terms(id,number(h,"active_version"));representative(a,old);for(int rate:List.of(buyer,seller,jobs))require(rate>=0&&rate<=500,"Тариф 0–500 б.п.");
        require(quota>0&&quota<=CurrencyService.MAX_MONEY&&ends>m.s.now()&&ends-m.s.now()<=365*FeudalService.DAY,"Перевірте ліміт і строк.");
        long version=number(pending(id),"version")+1;copy(old,version,"DRAFT");m.db.execute("UPDATE cm_treaty_terms SET buyer=?,seller=?,jobs=?,quota=?,ends=?,starts=? WHERE id=? AND version=?",buyer,seller,jobs,quota,ends,m.s.now(),id,version);
        m.db.execute("UPDATE cm_treaties SET pending_version=? WHERE id=?",version,id);return id;
    });}
    public String publish(Actor a,String req,String id,Place place){return m.once(a,req,"treaty-publish|"+id,()->{var r=pending(id);representative(a,r);m.access(string(r,"left_market"),Department.JOBS,place);require("DRAFT".equals(string(r,"state")),"Пропозицію вже оприлюднено.");m.db.execute("UPDATE cm_treaty_terms SET state='OFFERED',expires=? WHERE id=? AND version=?",m.s.now()+2*FeudalService.DAY,id,number(r,"version"));return id;});}
    public MarketDocuments.Document document(String id)throws SQLException {
        var r=pending(id);List<String> lines=new ArrayList<>();lines.add("Торговий договір "+id+", версія "+number(r,"version"));
        for(String key:List.of("left_party","right_party"))lines.add("Сторона: "+m.s.estate(string(r,key)).name()+" ["+string(r,key)+"]");
        lines.add("Ринки: "+string(r,"left_market")+" / "+string(r,"right_market"));
        lines.add("Збори покупця/продавця/послуг: "+number(r,"buyer")+" / "+number(r,"seller")+" / "+number(r,"jobs")+" б.п.");
        lines.add("Каталог точних специфікацій: "+string(r,"catalog"));lines.add("Ліміт пільгового обороту: "+format(number(r,"quota"))+" за "+number(r,"period_days")+" діб; під заявки резервується наперед.");
        lines.add("Початок: "+time(number(r,"starts"))+"; завершення: "+time(number(r,"ends")));
        lines.add("Повідомлення про припинення: "+number(r,"notice_hours")+" год. Забезпечення окремих контрактів зберігається.");
        lines.add("Потрібні підписи представників: "+required(r));lines.add("Договір не змінює чужих комісій, допусків або скарбниць. Постачання фінансується окремими контрактами.");
        lines.add("Книга "+string(r,"book_mode")+": вільний текст не виконується сервером.");
        String authority="";for(String estate:required(r))authority+=m.s.estate(estate).toString();
        return new MarketDocuments.Document("TREATY",id,number(r,"version"),hash(r+"|"+authority),List.copyOf(lines));
    }
    public String accept(Actor a,String id,long version,String token){return m.once(a,token,"sign-treaty|"+id+version,()->{
        var r=pending(id);require(Set.of("OFFERED","WAITING").contains(string(r,"state"))&&number(r,"expires")>m.s.now(),"Пропозиція ще не оприлюднена або строк минув.");
        representative(a,r);m.documents.consume(a,document(id),version,token);
        for(String party:required(r))if(a.id().equals(m.s.estate(party).lord()))m.db.execute("MERGE INTO cm_treaty_signatures KEY(id,version,party) VALUES(?,?,?,?)",id,version,party,a.id());
        if(signed(r)){m.db.execute("UPDATE cm_treaty_terms SET state='WAITING' WHERE id=? AND version=?",id,version);activate(terms(id,version));}
        m.event(a,string(r,"left_market"),"TREATY_SIGN",id+" version="+version);return id;
    });}
    boolean signed(Map<String,Object> r)throws SQLException {for(String party:required(r)){var signature=m.db.one("SELECT signer FROM cm_treaty_signatures WHERE id=? AND version=? AND party=?",string(r,"id"),number(r,"version"),party);if(signature==null||!m.s.estate(party).active()||!m.s.estate(party).lord().equals(string(signature,"signer")))return false;}return true;}
    void activate(Map<String,Object> r)throws SQLException {if(number(r,"starts")>m.s.now()||!signed(r))return;m.db.execute("UPDATE cm_treaties SET active_version=?,terminate_at=0 WHERE id=?",number(r,"version"),string(r,"id"));m.db.execute("UPDATE cm_treaty_terms SET state='ACTIVE' WHERE id=? AND version=?",string(r,"id"),number(r,"version"));}
    public String reject(Actor a,String req,String id){return m.once(a,req,"treaty-reject|"+id,()->{var r=pending(id);representative(a,r);require(!"ACTIVE".equals(string(r,"state")),"Чинний договір припиняється з погодженим повідомленням.");m.db.execute("UPDATE cm_treaty_terms SET state='REJECTED' WHERE id=? AND version=?",id,number(r,"version"));return id;});}
    public String terminate(Actor a,String req,String id){return m.once(a,req,"treaty-end|"+id,()->{var h=m.db.one("SELECT * FROM cm_treaties WHERE id=?",id);require(h!=null&&number(h,"active_version")>0,"Договір не чинний.");var r=terms(id,number(h,"active_version"));representative(a,r);m.db.execute("UPDATE cm_treaties SET terminate_at=? WHERE id=?",Math.min(number(r,"ends"),m.s.now()+number(r,"notice_hours")*3600000),id);m.event(a,string(r,"left_market"),"TREATY_TERMINATE",id);return id;});}
    public record Benefit(String treaty,long version,String party,int bps,long ends,long period,long quota) {}
    List<Benefit> benefits(Actor a,String market,String spec,String column,long volume,int maximum)throws SQLException {
        var result=new ArrayList<Benefit>();
        for(var h:m.db.query("SELECT * FROM cm_treaties WHERE active_version>0")){
            var r=terms(string(h,"id"),number(h,"active_version"));long end=number(r,"ends");if(number(h,"terminate_at")>0)end=Math.min(end,number(h,"terminate_at"));
            if(m.s.now()<number(r,"starts")||m.s.now()>=end||!"ACTIVE".equals(string(r,"state")))continue;
            String party=market.equals(string(r,"left_market"))?string(r,"right_party"):market.equals(string(r,"right_market"))?string(r,"left_party"):null;
            if(party==null||!m.s.estate(party).active()||!citizen.test(a.id(),party))continue;
            if(!string(r,"catalog").equals("*")&&!Arrays.asList(string(r,"catalog").split(",")).contains(spec))continue;
            int bps=(int)number(r,column);if(bps>=maximum)continue;long window=number(r,"period_days")*FeudalService.DAY;
            long period=number(r,"starts")+Math.floorDiv(m.s.now()-number(r,"starts"),window)*window;
            long used=number(m.db.one("SELECT COALESCE(SUM(reserved+spent),0) AS n FROM cm_privilege_reservations WHERE treaty=? AND party=? AND period_start=?",string(r,"id"),party,period),"n");
            if(volume<=number(r,"quota")-used)result.add(new Benefit(string(r,"id"),number(r,"version"),party,bps,Math.min(end,period+window),period,number(r,"quota")));
        }
        return result;
    }
    boolean valid(Map<String,Object> o)throws SQLException {
        var reservation=m.db.one("SELECT * FROM cm_privilege_reservations WHERE order_id=?",string(o,"id"));if(reservation==null)return true;
        var h=m.db.one("SELECT * FROM cm_treaties WHERE id=?",string(reservation,"treaty"));var r=terms(string(reservation,"treaty"),number(reservation,"version"));
        return number(h,"active_version")==number(r,"version")&&number(r,"ends")>m.s.now()&&(number(h,"terminate_at")==0||number(h,"terminate_at")>m.s.now())&&citizen.test(string(o,"owner"),string(reservation,"party"));
    }
    void update(Map<String,Object> o,long volume)throws SQLException {var r=m.db.one("SELECT order_id FROM cm_privilege_reservations WHERE order_id=?",string(o,"id"));if(r!=null)m.db.execute("UPDATE cm_privilege_reservations SET reserved=?,spent=spent+? WHERE order_id=?",Math.multiplyExact(number(o,"remaining"),number(o,"price")),volume,string(o,"id"));}
    void tick()throws SQLException {
        for(var r:m.db.query("SELECT * FROM cm_treaty_terms WHERE state='WAITING' AND starts<=?",m.s.now()))activate(r);
        m.db.execute("UPDATE cm_treaty_terms SET state='EXPIRED' WHERE state IN('DRAFT','OFFERED') AND expires<=?",m.s.now());
        for(var o:m.db.query("SELECT * FROM cm_orders WHERE state='OPEN'"))if(!valid(o))m.cancelWork(o,"PRIVILEGE_ENDED");
    }
}
