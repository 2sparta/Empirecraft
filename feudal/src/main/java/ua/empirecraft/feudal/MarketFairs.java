package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Fair drafts become public only after the physical sample and postage are committed. */
public final class MarketFairs {
    final CityMarketService m;
    public final MarketMail mail;
    MarketFairs(CityMarketService m)throws SQLException {
        this.m=m;m.db.execute("CREATE TABLE IF NOT EXISTS cm_fairs(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),organizer VARCHAR(36),name VARCHAR(100),theme VARCHAR(2000),starts BIGINT,ends BIGINT,departments VARCHAR(40),catalog CLOB,buyer INT,seller INT,jobs INT,version BIGINT DEFAULT 1,body CLOB,state VARCHAR(24),corrections INT DEFAULT 0)");
        mail=new MarketMail(m);
    }
    public Map<String,Object> fair(String id)throws SQLException {var r=m.db.one("SELECT * FROM cm_fairs WHERE id=?",id);require(r!=null,"Ярмарок не знайдено.");return r;}
    public String create(Actor a,String req,String market,Place p,String name,String theme,long starts,int hours,String departments,String catalog,int buyer,int seller,int jobs){return m.once(a,req,"fair|"+market+name+theme+starts+hours+departments+catalog+buyer+seller+jobs,()->{
        m.manage(a,market);present(market,p);require(name.length()>0&&name.length()<=100&&theme.length()<=2000,"Перевірте назву й тему.");
        require(starts>=m.s.now()+FeudalService.DAY&&hours>=2&&hours<=72,"Початок щонайменше через 24 години, тривалість 2–72 години.");
        require(m.db.one("SELECT id FROM cm_fairs WHERE market=? AND state NOT IN('CANCELLED','FINISHED')",market)==null,"На ринку вже є запланований ярмарок.");
        for(String d:departments.split(","))require(m.open(market,Department.valueOf(d)),"Потрібен відкритий відділ "+d);
        require(!departments.isBlank()&&!catalog.isBlank()&&catalog.length()<=4000,"Потрібні відділи й каталог.");for(int bps:List.of(buyer,seller,jobs))require(bps>=0&&bps<=500,"Збори: 0–500 б.п.");
        String key=id();m.db.execute("INSERT INTO cm_fairs(id,market,organizer,name,theme,starts,ends,departments,catalog,buyer,seller,jobs,body,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'','DRAFT')",key,market,a.id(),name,theme,starts,starts+hours*3600000L,departments,catalog,buyer,seller,jobs);
        m.event(a,market,"FAIR_DRAFT",key);return key;
    });}
    void present(String market,Place p)throws SQLException {for(var d:Department.values())try{m.access(market,d,p);return;}catch(IllegalArgumentException ignored){}throw new IllegalArgumentException("Особисто підійдіть до стійки відкритого відділу.");}
    public String edit(Actor a,String req,String id,String body){return m.once(a,req,"fair-text|"+id+body,()->{
        var r=fair(id);m.manage(a,string(r,"market"));require("DRAFT".equals(string(r,"state")),"Опублікований текст незмінний; потрібне окреме погоджене виправлення.");
        require(body.length()<=4000,"Текст запрошення: до 4000 символів.");m.db.execute("UPDATE cm_fairs SET body=?,version=version+1 WHERE id=?",body,id);m.event(a,string(r,"market"),"FAIR_TEXT",id+" invalidates previous sample");return id;
    });}
    public String preview(Actor a,String id,Place p)throws SQLException {return mail.preview(a,id,p);}
    public String confirm(Actor a,String req,String id,long version,String token,Place p){return mail.confirm(a,req,id,version,token,p,null);}
    public String cancelLetter(Actor a,String req,String id){return m.once(a,req,"fair-letter-cancel|"+id,()->{var r=fair(id);m.manage(a,string(r,"market"));require("DRAFT".equals(string(r,"state")),"Підготовка листа вже завершена.");m.db.execute("UPDATE cm_fairs SET version=version+1 WHERE id=?",id);return id;});}
    public String cancel(Actor a,String req,String id){return m.once(a,req,"fair-cancel|"+id,()->{var r=fair(id);m.manage(a,string(r,"market"));m.db.execute("UPDATE cm_fairs SET state='CANCELLED',version=version+1 WHERE id=?",id);m.event(a,string(r,"market"),"FAIR_CANCEL",id);return id;});}
    public record Benefit(String id,int bps,long ends) {}
    List<Benefit> benefits(String market,Department d,String spec,String column)throws SQLException {
        var result=new ArrayList<Benefit>();for(var r:m.db.query("SELECT * FROM cm_fairs WHERE market=? AND state IN('ANNOUNCED','ACTIVE') AND starts<=? AND ends>?",market,m.s.now(),m.s.now()))
            if(Arrays.asList(string(r,"departments").split(",")).contains(d.name())&&(spec.isEmpty()||column.equals("jobs")||string(r,"catalog").equals("*")||Arrays.asList(string(r,"catalog").split(",")).contains(spec)))result.add(new Benefit(string(r,"id"),(int)number(r,column),number(r,"ends")));
        return result;
    }
    void tick()throws SQLException {
        m.db.execute("UPDATE cm_fairs SET state='ACTIVE' WHERE state='ANNOUNCED' AND starts<=? AND ends>?",m.s.now(),m.s.now());
        m.db.execute("UPDATE cm_fairs SET state='FINISHED' WHERE state IN('ACTIVE','ANNOUNCED') AND ends<=?",m.s.now());
        m.db.execute("UPDATE cm_fairs SET state='CANCELLED',version=version+1 WHERE state NOT IN('FINISHED','CANCELLED') AND market IN(SELECT id FROM cm_markets WHERE state IN('CLOSED','ARCHIVED'))");
    }
}
