package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Persisted postage reserve and outbox. Native Contact receipts survive a failed SQL acknowledgement. */
public final class MarketMail {
    final CityMarketService m;
    Function<Map<String,Object>,Map<String,Object>> gateway;
    String paperSpec;
    public static final int MAX_RECIPIENTS=2000, BATCH_SIZE=8;
    MarketMail(CityMarketService m)throws SQLException {
        this.m=m;
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_mail_samples(id VARCHAR(36) PRIMARY KEY,fair VARCHAR(36),actor VARCHAR(36),version BIGINT,expires BIGINT,state VARCHAR(24),item CLOB,template CLOB,paper_spec VARCHAR(64),department VARCHAR(8),funded BOOLEAN DEFAULT FALSE)");
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_mail_jobs(id VARCHAR(36) PRIMARY KEY,sample VARCHAR(36),person VARCHAR(36),address VARCHAR(200),generation VARCHAR(36),instance VARCHAR(36),state VARCHAR(24),next_try BIGINT DEFAULT 0,attempts INT DEFAULT 0,reason VARCHAR(200),refunded BOOLEAN DEFAULT FALSE,UNIQUE(sample,person))");
        m.db.execute("CREATE INDEX IF NOT EXISTS cm_mail_queue ON cm_mail_jobs(state,next_try)");
    }
    void available(){require(gateway!=null&&paperSpec!=null,"Пошта Contact недоступна. Потрібен серверний міст Empirecraft 1.6.1 і Contact; чернетку збережено.");}
    Map<String,Object> call(Map<String,Object> request){available();var response=gateway.apply(request);require(response!=null,"Contact не підтвердив запит.");return response;}
    public Map<String,Object> sample(String token)throws SQLException {var row=m.db.one("SELECT * FROM cm_mail_samples WHERE id=?",token);require(row!=null,"Лист-зразок не знайдено. Замовте новий зразок.");return row;}
    List<Map<String,Object>> targets(String token)throws SQLException {return m.db.query("SELECT * FROM cm_mail_jobs WHERE sample=? ORDER BY person",token);}
    String content(Map<String,Object> fair)throws SQLException {
        var market=m.market(string(fair,"market"));var counter=m.db.one("SELECT * FROM cm_departments WHERE market=? AND state='OPEN' ORDER BY kind LIMIT 1",string(fair,"market"));
        require(counter!=null,"Немає відкритої стійки.");
        return string(fair,"body")+"\n\nЯрмарок: "+string(fair,"name")+"\nТема: "+string(fair,"theme")+"\nРинок: "+string(market,"name")
            +"\nПочаток: "+time(number(fair,"starts"))+"\nКінець: "+time(number(fair,"ends"))+"\nВідділи: "+string(fair,"departments")
            +"\nКаталог: "+string(fair,"catalog")+"\nЗбори (б.п.): покупець "+number(fair,"buyer")+", продавець "+number(fair,"seller")+", послуги "+number(fair,"jobs")
            +"\nМісто: "+m.s.estate(string(market,"town")).name()+"\nСтійка: "+number(counter,"cx")+" "+number(counter,"cy")+" "+number(counter,"cz");
    }
    public String preview(Actor a,String fair,Place p)throws SQLException {
        available();var row=m.fairs.fair(fair);m.manage(a,string(row,"market"));m.fairs.present(string(row,"market"),p);
        require("DRAFT".equals(string(row,"state"))&&number(row,"starts")>m.s.now(),"Потрібна майбутня чернетка ярмарку.");
        require(!string(row,"body").isBlank(),"Спершу напишіть текст запрошення.");
        var old=m.db.one("SELECT id FROM cm_mail_samples WHERE fair=? AND actor=? AND version=? AND expires>? AND state IN('NEW','ISSUING','ISSUED')",fair,a.id(),number(row,"version"),m.s.now());
        if(old!=null)return string(old,"id");
        var response=call(Map.of("action","snapshot"));var recipients=(List<?>)response.get("recipients");
        require(recipients!=null&&!recipients.isEmpty(),"Немає зареєстрованих поштових скриньок Contact.");
        require(recipients.size()<=MAX_RECIPIENTS,"Розсилка підтримує до "+MAX_RECIPIENTS+" адресатів за один ярмарок.");
        String token=id();var card=call(Map.of("action","template","actor",a.id(),"sample",token,"text",content(row)));
        String item=(String)card.get("item"),template=(String)card.get("template");require(item!=null&&template!=null,"Contact не створив листівку.");
        return m.db.atomic(()->{
            m.db.execute("INSERT INTO cm_mail_samples(id,fair,actor,version,expires,state,item,template,paper_spec,department) VALUES(?,?,?,?,?,'NEW',?,?,?,?)",token,fair,a.id(),number(row,"version"),Math.min(number(row,"starts"),m.s.now()+1800000),item,template,paperSpec,presentDepartment(string(row,"market"),p));
            var seen=new HashSet<String>();for(Object value:recipients){var target=(Map<?,?>)value;String person=target.get("person").toString();UUID.fromString(person);if(!seen.add(person))continue;
                m.db.execute("INSERT INTO cm_mail_jobs(id,sample,person,address,generation,instance,state,reason) VALUES(?,?,?,?,?,?,'SNAPSHOT','')",id(),token,person,target.get("address"),target.get("generation"),target.get("instance"));
            }
            m.event(a,string(row,"market"),"FAIR_SAMPLE",fair+" v"+number(row,"version")+" recipients="+seen.size());return token;
        });
    }
    String presentDepartment(String market,Place p)throws SQLException {for(var d:Department.values())try{m.access(market,d,p);return d.name();}catch(IllegalArgumentException ignored){}throw new IllegalArgumentException("Підійдіть до стійки.");}
    public String issue(Actor a,String token,CommerceService.InventoryChange change){return m.once(a,token,"fair-sample|"+token,()->{
        var sample=sample(token);require(a.id().equals(string(sample,"actor"))&&"NEW".equals(string(sample,"state")),"Зразок уже видано або передача ще не завершена.");
        require(change!=null&&a.id().equals(change.person()),"Потрібна фізична передача зразка.");
        String op=m.commerce.intent(a,"CM_IN",token,1,List.of(change));m.db.execute("INSERT INTO cm_transfers VALUES(?,'FAIR_SAMPLE',?,1)",op,token);m.db.execute("UPDATE cm_mail_samples SET state='ISSUING' WHERE id=?",token);return op;
    });}
    public String confirm(Actor a,String req,String fair,long version,String token,Place p,CommerceService.InventoryChange change){return m.once(a,req,"fair-confirm|"+fair+"|"+version+"|"+token,()->{
        available();var row=m.fairs.fair(fair);var sample=sample(token);m.manage(a,string(row,"market"));m.fairs.present(string(row,"market"),p);
        require("DRAFT".equals(string(row,"state"))&&number(row,"starts")>m.s.now(),"Ярмарок уже опублікований, скасований або розпочався.");
        require(fair.equals(string(sample,"fair"))&&a.id().equals(string(sample,"actor"))&&version==number(row,"version")&&version==number(sample,"version")&&number(sample,"expires")>m.s.now()&&"ISSUED".equals(string(sample,"state")),"Зразок застарів, належить іншому гравцю або ще не виданий. Перегляньте новий фізичний зразок.");
        require(change!=null&&a.id().equals(change.person()),"Потрібні фізичний зразок і папір для копій.");
        long count=targets(token).size();require(count>0,"Немає адресатів.");
        String op=m.commerce.intent(a,"CM_IN",token,count,List.of(change));m.db.execute("INSERT INTO cm_transfers VALUES(?,'FAIR_PUBLISH',?,?)",op,token,count);
        m.db.execute("UPDATE cm_mail_samples SET state='FUNDING' WHERE id=?",token);m.db.execute("UPDATE cm_fairs SET state='MAIL_PREPARED' WHERE id=?",fair);return op;
    });}
    void finish(String kind,String token,boolean applied)throws SQLException {
        var sample=sample(token);var fair=m.fairs.fair(string(sample,"fair"));String market=string(fair,"market");
        if(kind.equals("FAIR_SAMPLE")){m.db.execute("UPDATE cm_mail_samples SET state=? WHERE id=?",applied?"ISSUED":"ABORTED",token);return;}
        if(!applied){m.db.execute("UPDATE cm_mail_samples SET state='ISSUED' WHERE id=?",token);m.db.execute("UPDATE cm_fairs SET state='DRAFT' WHERE id=? AND state='MAIL_PREPARED'",string(fair,"id"));return;}
        m.db.execute("UPDATE cm_mail_samples SET state='CONFIRMED',funded=TRUE WHERE id=?",token);
        boolean valid="MAIL_PREPARED".equals(string(fair,"state"))&&number(fair,"version")==number(sample,"version")&&number(fair,"starts")>m.s.now()&&"OPEN".equals(string(m.market(market),"state"))&&m.territory(market);
        m.db.execute("UPDATE cm_mail_jobs SET state='QUEUED' WHERE sample=?",token);
        m.db.execute("UPDATE cm_fairs SET state=? WHERE id=?",valid?"ANNOUNCED":"CANCELLED",string(fair,"id"));
        m.event(new Actor(string(sample,"actor"),false),market,valid?"FAIR_ANNOUNCE":"FAIR_MAIL_CANCEL",string(fair,"id")+" copies="+targets(token).size());
    }
    /** Called outside the economy transaction: commit an attempt before crossing into native storage. */
    public void deliver() {
        if(gateway==null)return;
        var work=m.db.atomic(()->{
            var rows=m.db.query("SELECT j.*,s.template,s.fair FROM cm_mail_jobs j JOIN cm_mail_samples s ON s.id=j.sample WHERE s.funded=TRUE AND j.state IN('QUEUED','WAITING','SENDING') AND j.next_try<=? ORDER BY j.next_try,j.id LIMIT "+BATCH_SIZE,m.s.now());
            for(var row:rows){var fair=m.fairs.fair(string(row,"fair"));boolean send=Set.of("ANNOUNCED","ACTIVE").contains(string(fair,"state"))&&number(fair,"starts")>m.s.now()&&"OPEN".equals(string(m.market(string(fair,"market")),"state"));row.put("send",send);
                m.db.execute("UPDATE cm_mail_jobs SET state='SENDING',attempts=attempts+1,next_try=? WHERE id=?",m.s.now()+30000,string(row,"id"));}
            return rows;
        });
        if(work.isEmpty())return;
        // A native error leaves SENDING intact. The same job ID is retried; Contact
        // checks its persisted receipt before inserting another physical item.
        Map<String,Object> results=call(Map.of("action","deliver","jobs",work));
        m.db.atomic(()->{
            for(var row:work){String job=string(row,"id"),status=Objects.toString(results.get(job),"WAITING");
                require(Set.of("DELIVERED","WAITING","STALE","CANCELLED").contains(status),"Неправильна відповідь Contact.");
                m.db.execute("UPDATE cm_mail_jobs SET state=?,reason=? WHERE id=?",status,status,job);
                if(Set.of("STALE","CANCELLED").contains(status))refund(row);
            }return null;
        });
    }
    void refund(Map<String,Object> job)throws SQLException {
        if(m.db.execute("UPDATE cm_mail_jobs SET refunded=TRUE WHERE id=? AND refunded=FALSE",string(job,"id"))!=1)return;
        var sample=sample(string(job,"sample"));var fair=m.fairs.fair(string(sample,"fair"));
        m.asset(string(fair,"market"),Department.valueOf(string(sample,"department")),"p:"+string(sample,"actor"),string(sample,"paper_spec"),"",1,string(sample,"id"),"MAIL_PAPER_REFUND");
    }
    public List<String> status(Actor a)throws SQLException {
        var lines=new ArrayList<String>();for(var row:m.db.query("SELECT id,market,name,state FROM cm_fairs ORDER BY starts DESC LIMIT 100"))try{
            m.manage(a,string(row,"market"));lines.add(string(row,"name")+" · "+string(row,"state")+" · "+string(row,"id"));
            for(var count:m.db.query("SELECT j.state,COUNT(*) AS amount FROM cm_mail_jobs j JOIN cm_mail_samples s ON s.id=j.sample WHERE s.fair=? AND s.funded=TRUE GROUP BY j.state",string(row,"id")))lines.add("  "+string(count,"state")+": "+number(count,"amount"));
        }catch(IllegalArgumentException ignored){}
        if(lines.isEmpty())lines.add("Немає власних ярмарків. Запрошення надходять у вашу скриньку Contact.");return lines;
    }
}
