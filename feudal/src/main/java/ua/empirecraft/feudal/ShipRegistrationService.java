package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** A city/name application precedes physical assembly; only an administrator can finalize ownership. */
final class ShipRegistrationService {
    record Anchor(String world,int x,int y,int z){}
    final CityMarketService markets;final FleetService fleet;final FeudalService s;final Database db;final Predicate<Anchor> valid;
    ShipRegistrationService(CityMarketService markets,FleetService fleet,Predicate<Anchor> valid)throws SQLException {
        this.markets=markets;this.fleet=fleet;this.valid=valid;s=markets.s;db=s.db;
        db.execute("CREATE TABLE IF NOT EXISTS naval_applications(id VARCHAR(36) PRIMARY KEY,applicant VARCHAR(36),requested_town VARCHAR(80),name VARCHAR(64),world VARCHAR(100),x INT,y INT,z INT,state VARCHAR(16),created BIGINT,reviewer VARCHAR(80) DEFAULT '',reason VARCHAR(1000) DEFAULT '',assembler VARCHAR(36) DEFAULT '',permit VARCHAR(36) DEFAULT '',ship BIGINT,revision BIGINT DEFAULT 0,final_town VARCHAR(80) DEFAULT '',final_name VARCHAR(64) DEFAULT '')");
        db.execute("CREATE INDEX IF NOT EXISTS naval_application_anchor ON naval_applications(world,x,y,z,state)");
        db.execute("CREATE UNIQUE INDEX IF NOT EXISTS naval_application_ship ON naval_applications(ship)");
    }
    static void name(String name){require(name!=null&&!name.isBlank()&&name.length()<=64&&!name.contains("§")&&name.codePoints().noneMatch(Character::isISOControl),LocalText.tr("Назва корабля: 1–64 звичайні символи."));}
    void administrator(Actor actor){require(actor.admin(),LocalText.tr("Реєстрацію та місто-власника корабля підтверджує адміністратор."));}
    Map<String,Object> application(String id)throws SQLException {var row=db.one("SELECT * FROM naval_applications WHERE id=?",id);require(row!=null,LocalText.tr("Заявку на корабель не знайдено."));return row;}
    String submit(Actor actor,String request,String title,Anchor anchor){return markets.once(actor,request,"ship-request|"+title+anchor,()->{
        name(title);String town=fleet.world.hometown(actor.id());require(town!=null&&town.startsWith("t:")&&s.estate(town).active(),LocalText.tr("Для заявки на корабель потрібно належати до активного міста."));
        require(valid.test(anchor),LocalText.tr("Наведіться на завантажений блок майбутнього корабля без фізики."));
        require(db.one("SELECT id FROM naval_applications WHERE world=? AND x=? AND y=? AND z=? AND state IN('PENDING','APPROVED','ASSEMBLING','ASSEMBLED')",anchor.world,anchor.x,anchor.y,anchor.z)==null,LocalText.tr("Для цього корабля вже є незавершена заявка."));
        require(number(db.one("SELECT COUNT(*) AS n FROM naval_applications WHERE applicant=? AND state IN('PENDING','APPROVED','ASSEMBLING','ASSEMBLED')",actor.id()),"n")<3,LocalText.tr("Ліміт: три незавершені заявки на кораблі."));
        var last=db.one("SELECT MAX(created) AS last_time FROM naval_applications WHERE applicant=?",actor.id());require(last.get("last_time")==null||s.now()-number(last,"last_time")>=60_000,LocalText.tr("Нову заявку на корабель можна подати раз на хвилину."));
        String id=UUID.randomUUID().toString();db.execute("INSERT INTO naval_applications(id,applicant,requested_town,name,world,x,y,z,state,created) VALUES(?,?,?,?,?,?,?,?,'PENDING',?)",id,actor.id(),town,title,anchor.world,anchor.x,anchor.y,anchor.z,s.now());
        RegistrationNotices.shipRequest(s,id,title,town,actor.id(),anchor);s.audit(actor,"SHIP_REQUEST",id+" "+title+" "+anchor);return id;
    });}
    String review(Actor actor,String request,String id,boolean approved,String reason){return markets.once(actor,request,"ship-review|"+id+approved+reason,()->{
        administrator(actor);var row=application(id);require(Set.of("PENDING","APPROVED").contains(string(row,"state")),LocalText.tr("Цю заявку вже зібрано або закрито."));
        require(reason!=null&&reason.length()<=1000,LocalText.tr("Примітка адміністратора завелика."));if(approved)require(valid.test(anchor(row)),LocalText.tr("Блок заявки недоступний або вже має фізику."));
        db.execute("UPDATE naval_applications SET state=?,reviewer=?,reason=?,revision=revision+1 WHERE id=?",approved?"APPROVED":"REJECTED",actor.id(),reason,id);
        s.audit(actor,approved?"SHIP_REQUEST_APPROVE":"SHIP_REQUEST_REJECT",id+" "+reason);return id;
    });}
    String withdraw(Actor actor,String request,String id){return markets.once(actor,request,"ship-withdraw|"+id,()->{
        var row=application(id);require(actor.id().equals(string(row,"applicant")),LocalText.tr("Відкликати можна лише власну заявку."));require(Set.of("PENDING","APPROVED").contains(string(row,"state")),LocalText.tr("Після початку збирання заявку завершує адміністратор."));
        db.execute("UPDATE naval_applications SET state='CANCELLED',revision=revision+1 WHERE id=?",id);s.audit(actor,"SHIP_REQUEST_CANCEL",id);return id;
    });}
    Map<String,Object> begin(Actor admin,Anchor anchor){return db.atomic(()->{
        administrator(admin);require(valid.test(anchor),LocalText.tr("Цей блок уже має фізику або недоступний."));
        var row=db.one("SELECT * FROM naval_applications WHERE world=? AND x=? AND y=? AND z=? AND state='APPROVED'",anchor.world,anchor.x,anchor.y,anchor.z);
        require(row!=null,LocalText.tr("Спочатку схваліть заявку на цей блок корабля."));String permit=UUID.randomUUID().toString();
        db.execute("UPDATE naval_applications SET state='ASSEMBLING',assembler=?,permit=?,revision=revision+1 WHERE id=?",admin.id(),permit,string(row,"id"));
        return Map.of("permit",permit,"application",string(row,"id"),"name",string(row,"name"),"applicant",string(row,"applicant"));
    });}
    void abort(String actor,String permit){db.atomic(()->{db.execute("UPDATE naval_applications SET state='APPROVED',permit='',assembler='',revision=revision+1 WHERE permit=? AND assembler=? AND state='ASSEMBLING' AND ship IS NULL",permit,actor);return null;});}
    Map<String,Object> complete(String permit,long ship){return db.atomic(()->{
        var row=db.one("SELECT * FROM naval_applications WHERE permit=?",permit);require(row!=null,LocalText.tr("Дозвіл збирання корабля застарів."));
        if(Set.of("ASSEMBLED","ASSIGNED").contains(string(row,"state"))){require(row.get("ship")!=null&&number(row,"ship")==ship,LocalText.tr("Дозвіл належить іншому кораблю."));return row;}
        require(string(row,"state").equals("ASSEMBLING"),LocalText.tr("Заявка не очікує завершення збирання."));
        require(db.one("SELECT id FROM naval_applications WHERE ship=? AND id<>?",ship,string(row,"id"))==null,LocalText.tr("Корабель уже пов’язаний з іншою заявкою."));
        db.execute("UPDATE naval_applications SET ship=?,state='ASSEMBLED',revision=revision+1 WHERE id=?",ship,string(row,"id"));return application(string(row,"id"));
    });}
    String finalizeShip(Actor admin,String request,String id,String town,String title){return markets.once(admin,request,"ship-finalize|"+id+town+title,()->{
        administrator(admin);name(title);var row=application(id);require(string(row,"state").equals("ASSEMBLED")&&row.get("ship")!=null,LocalText.tr("Спочатку адміністратор має зібрати схвалений корабель VS-паличкою."));long ship=number(row,"ship");
        String child=UUID.nameUUIDFromBytes((request+"|owner").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();fleet.assign(admin,child,ship,town);
        db.execute("UPDATE naval_ships SET name=?,revision=revision+1 WHERE id=?",title,ship);db.execute("UPDATE naval_applications SET state='ASSIGNED',final_town=?,final_name=?,revision=revision+1 WHERE id=?",town,title,id);
        s.audit(admin,"SHIP_REQUEST_FINALIZE",id+" #"+ship+" "+town+" "+title);return id;
    });}
    boolean pending(long ship)throws SQLException {return db.one("SELECT id FROM naval_applications WHERE ship=? AND state='ASSEMBLED'",ship)!=null;}
    static Anchor anchor(Map<String,Object> row){return new Anchor(string(row,"world"),(int)number(row,"x"),(int)number(row,"y"),(int)number(row,"z"));}
}
