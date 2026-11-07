package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Physical public board. Review reserves the quoted fee; only publication pays the city. */
public final class MarketNotices {
    final CityMarketService m;
    Predicate<Map<String,Object>> markerValid=row->true;
    MarketNotices(CityMarketService m)throws SQLException {
        this.m=m;
        for(String ddl:List.of(
            "CREATE TABLE IF NOT EXISTS cm_boards(market VARCHAR(36) PRIMARY KEY,world VARCHAR(100) DEFAULT '',x INT DEFAULT 0,y INT DEFAULT 0,z INT DEFAULT 0,fee BIGINT DEFAULT 0,code VARCHAR(8) DEFAULT '',approval BOOLEAN DEFAULT FALSE,version BIGINT DEFAULT 1)",
            "CREATE TABLE IF NOT EXISTS cm_heralds(market VARCHAR(36),person VARCHAR(36),grantor VARCHAR(36),PRIMARY KEY(market,person))",
            "CREATE TABLE IF NOT EXISTS cm_notices(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),author VARCHAR(36),title VARCHAR(100),body VARCHAR(4000),state VARCHAR(16),created BIGINT,ends BIGINT,fee BIGINT,code VARCHAR(8),reviewer VARCHAR(36) DEFAULT '',reason VARCHAR(1000) DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS cm_notices_board ON cm_notices(market,state,ends)"))m.db.execute(ddl);
    }
    Map<String,Object> settings(String market)throws SQLException {
        m.market(market);var row=m.db.one("SELECT * FROM cm_boards WHERE market=?",market);
        if(row==null){m.db.execute("INSERT INTO cm_boards(market) VALUES(?)",market);row=m.db.one("SELECT * FROM cm_boards WHERE market=?",market);}return row;
    }
    Map<String,Object> notice(String id)throws SQLException {var row=m.db.one("SELECT * FROM cm_notices WHERE id=?",id);require(row!=null,LocalText.tr("Оголошення не знайдено."));return row;}
    String city(String market)throws SQLException {return string(m.market(market),"town");}
    void mayor(Actor actor,String market)throws SQLException {var town=m.s.estate(city(market));require(town.active()&&actor.id().equals(town.lord()),LocalText.tr("Дошку та посаду вісника налаштовує чинний мер."));}
    boolean moderator(Actor actor,String market)throws SQLException {
        var town=m.s.estate(city(market));return town.active()&&(actor.id().equals(town.lord())||m.db.one("SELECT person FROM cm_heralds WHERE market=? AND person=? AND grantor=?",market,actor.id(),town.lord())!=null);
    }
    void available(String market)throws SQLException {require(m.open(market,Department.GOODS),LocalText.tr("Дошка доступна лише на відкритому базарі."));}
    void present(String market,Place p)throws SQLException {
        available(market);var board=settings(market);
        require(!string(board,"world").isBlank()&&p.world().equals(string(board,"world"))&&markerValid.test(board),LocalText.tr("Мер має встановити дошку оголошень на базарі."));
        double dx=p.x()-number(board,"x")-.5,dy=p.y()-number(board,"y")-.5,dz=p.z()-number(board,"z")-.5;
        require(dx*dx+dy*dy+dz*dz<=64,LocalText.tr("Підійдіть до дошки оголошень."));
    }
    public String configure(Actor actor,String request,String market,long fee,String code,boolean approval){return m.once(actor,request,"board-config|"+market+fee+code+approval,()->{
        mayor(actor,market);require(fee>=0&&fee<=CurrencyService.MAX_MONEY,LocalText.tr("Плата за оголошення має бути невід’ємною."));if(fee>0)m.payment(market,code);else codeForFree(code);
        settings(market);m.db.execute("UPDATE cm_boards SET fee=?,code=?,approval=?,version=version+1 WHERE market=?",fee,fee==0?"":code,approval,market);m.event(actor,market,"BOARD_CONFIG",fee+" "+code+" review="+approval);return market;
    });}
    private static void codeForFree(String code){require(code!=null,LocalText.tr("Укажіть валюту або - для безкоштовних оголошень."));}
    public String appoint(Actor actor,String request,String market,String person,boolean enabled){return m.once(actor,request,"board-herald|"+market+person+enabled,()->{
        mayor(actor,market);UUID.fromString(person);m.db.execute("DELETE FROM cm_heralds WHERE market=? AND person=?",market,person);
        if(enabled)m.db.execute("INSERT INTO cm_heralds VALUES(?,?,?)",market,person,actor.id());m.event(actor,market,"BOARD_HERALD",person+" "+enabled);return market;
    });}
    public String bind(Actor actor,String request,String market,Place block){return m.once(actor,request,"board-bind|"+market+block,()->{
        mayor(actor,market);available(market);var dep=m.department(market,Department.GOODS);var row=m.market(market);
        require(block.world().equals(string(row,"world"))&&box(dep).contains(block),LocalText.tr("Дошка має стояти всередині схваленої будівлі базару."));
        settings(market);m.db.execute("UPDATE cm_boards SET world=?,x=?,y=?,z=?,version=version+1 WHERE market=?",block.world(),(int)Math.floor(block.x()),(int)Math.floor(block.y()),(int)Math.floor(block.z()),market);m.event(actor,market,"BOARD_BIND",block.toString());return market;
    });}
    public String submit(Actor actor,String request,String market,Place p,String title,String body,int days,long expectedVersion){return m.once(actor,request,"board-post|"+market+title+body+days+expectedVersion,()->{
        present(market,p);var board=settings(market);require(number(board,"version")==expectedVersion,LocalText.tr("Умови дошки змінилися. Перегляньте плату повторно."));
        require(!title.isBlank()&&title.length()<=100&&!body.isBlank()&&body.length()<=4000&&!title.contains("§")&&!body.contains("§")&&title.codePoints().noneMatch(Character::isISOControl)&&body.indexOf('\0')<0,LocalText.tr("Потрібні заголовок до 100 і текст до 4000 символів без службового форматування."));
        require(days>=1&&days<=30,LocalText.tr("Строк оголошення: 1–30 діб."));
        require(number(m.db.one("SELECT COUNT(*) AS n FROM cm_notices WHERE market=? AND author=? AND state IN('PENDING','PUBLISHED') AND ends>?",market,actor.id(),m.s.now()),"n")<10,LocalText.tr("Ліміт: десять чинних оголошень на гравця на цій дошці."));
        long fee=number(board,"fee");String code=string(board,"code"),key=id();if(fee>0){m.payment(market,code);m.move(wallet(actor),fund(key),code,fee);}
        m.db.execute("INSERT INTO cm_notices(id,market,author,title,body,state,created,ends,fee,code) VALUES(?,?,?,?,?,?,?,?,?,?)",key,market,actor.id(),title.strip(),body.strip(),bool(board,"approval")?"PENDING":"PUBLISHED",m.s.now(),m.s.now()+days*FeudalService.DAY,fee,code);
        if(!bool(board,"approval"))pay(notice(key));m.event(actor,market,"NOTICE_SUBMIT",key);return key;
    });}
    static String fund(String id){return "notice:"+id;}
    void pay(Map<String,Object> row)throws SQLException {if(number(row,"fee")>0)m.move(fund(string(row,"id")),"e:"+city(string(row,"market")),string(row,"code"),number(row,"fee"));}
    void refund(Map<String,Object> row)throws SQLException {if(string(row,"state").equals("PENDING")&&number(row,"fee")>0)m.move(fund(string(row,"id")),"p:"+string(row,"author"),string(row,"code"),number(row,"fee"));}
    public String review(Actor actor,String request,String id,boolean approve,String reason){return m.once(actor,request,"notice-review|"+id+approve+reason,()->{
        var row=notice(id);String market=string(row,"market");require(moderator(actor,market),LocalText.tr("Оголошення перевіряє мер або призначений ним міський вісник."));
        require(string(row,"state").equals("PENDING")&&number(row,"ends")>m.s.now(),LocalText.tr("Оголошення вже розглянуте або прострочене."));require(reason!=null&&reason.length()<=1000,LocalText.tr("Примітка: до 1000 символів."));
        if(approve){available(market);require(markerValid.test(settings(market)),LocalText.tr("Дошку знято. Публікація недоступна."));pay(row);}else refund(row);
        m.db.execute("UPDATE cm_notices SET state=?,reviewer=?,reason=?,ends=? WHERE id=?",approve?"PUBLISHED":"REJECTED",actor.id(),reason,approve?m.s.now()+number(row,"ends")-number(row,"created"):number(row,"ends"),id);m.event(actor,market,"NOTICE_REVIEW",id+" "+approve);return id;
    });}
    public String remove(Actor actor,String request,String id,String reason){return m.once(actor,request,"notice-remove|"+id+reason,()->{
        var row=notice(id);require(actor.id().equals(string(row,"author"))||moderator(actor,string(row,"market")),LocalText.tr("Зняти оголошення може автор, мер або вісник."));
        require(Set.of("PENDING","PUBLISHED").contains(string(row,"state")),LocalText.tr("Оголошення вже закрито."));require(reason!=null&&reason.length()<=1000,LocalText.tr("Примітка: до 1000 символів."));refund(row);
        m.db.execute("UPDATE cm_notices SET state='WITHDRAWN',reviewer=?,reason=? WHERE id=?",actor.id(),reason,id);m.event(actor,string(row,"market"),"NOTICE_REMOVE",id);return id;
    });}
    public List<Map<String,Object>> publicEntries(String market,Place p,int page)throws SQLException {
        present(market,p);require(page>=1&&page<=10000,LocalText.tr("Некоректна сторінка."));
        return m.db.query("SELECT id,author,title,body,created,ends FROM cm_notices WHERE market=? AND state='PUBLISHED' AND ends>? ORDER BY created DESC,id LIMIT 10 OFFSET ?",market,m.s.now(),(page-1)*10);
    }
    public Map<String,Object> read(Actor actor,String id,Place p)throws SQLException {var row=notice(id);present(string(row,"market"),p);require(string(row,"state").equals("PUBLISHED")&&number(row,"ends")>m.s.now()||actor.id().equals(string(row,"author"))||moderator(actor,string(row,"market")),LocalText.tr("Це оголошення очікує перевірки або недоступне."));return row;}
    void unbind(String market)throws SQLException {for(var row:m.db.query("SELECT * FROM cm_notices WHERE market=? AND state='PENDING'",market)){refund(row);m.db.execute("UPDATE cm_notices SET state='CANCELLED',reason=? WHERE id=?",LocalText.tr("Дошку знято; плату повернуто."),string(row,"id"));}m.db.execute("UPDATE cm_boards SET world='',version=version+1 WHERE market=?",market);}
    void tick()throws SQLException {
        for(var row:m.db.query("SELECT n.* FROM cm_notices n JOIN cm_markets m ON m.id=n.market WHERE n.state IN('PENDING','PUBLISHED') AND (n.ends<=? OR m.state IN('CLOSED','ARCHIVED'))",m.s.now())){refund(row);m.db.execute("UPDATE cm_notices SET state='EXPIRED' WHERE id=?",string(row,"id"));}
    }
}
