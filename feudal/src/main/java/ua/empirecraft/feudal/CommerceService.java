package ua.empirecraft.feudal;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Item intents are durable before inventories change; money never leaves SQL without a journal. */
public final class CommerceService {
    final FeudalService s;
    final Database db;
    public record InventoryChange(String person,String before,String after) {}
    public record Payment(String from,String to,String code,long amount) {}
    public CommerceService(FeudalService s) throws SQLException {
        this.s=s;db=s.db;
        db.execute("CREATE TABLE IF NOT EXISTS orders(id VARCHAR(36) PRIMARY KEY, owner VARCHAR(100), creator VARCHAR(36), item CLOB, total BIGINT, delivered BIGINT, unclaimed BIGINT, code VARCHAR(8), reward BIGINT, paid BIGINT, status VARCHAR(16), created BIGINT, ends BIGINT)");
        db.execute("CREATE INDEX IF NOT EXISTS orders_status ON orders(status,ends)");
    }
    public static void inventoryAvailable(Database db,String person) throws SQLException {
        require(db.one("SELECT p.op FROM item_participants p JOIN item_ops o ON o.id=p.op WHERE p.person=? AND o.status='PREPARED'",person)==null,LocalText.tr("Незавершену передачу предметів має перевірити адміністратор: /commerce pending."));
    }
    private void available(String person) throws SQLException {
        inventoryAvailable(db,person);
        require(db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",person)==null,LocalText.tr("Спершу завершіть невизначену операцію золотого банку."));
    }
    String intent(Actor actor,String kind,String related,long quantity,List<InventoryChange> changes) throws SQLException {
        require(!changes.isEmpty() && changes.size()<=2,LocalText.tr("Неправильна кількість учасників."));
        String id=UUID.randomUUID().toString();
        db.execute("INSERT INTO item_ops VALUES(?,?,?,?,?,'PREPARED',?)",id,kind,related,quantity,actor.id(),s.now());
        for(var c:changes){UUID.fromString(c.person());available(c.person());require(c.before()!=null && c.after()!=null,LocalText.tr("Немає знімка інвентарю."));db.execute("INSERT INTO item_participants VALUES(?,?,?,?)",id,c.person(),c.before(),c.after());}
        s.audit(actor,"ITEM_PREPARE",id+" "+kind+" "+related);return id;
    }
    public String trade(Actor actor,String other,List<InventoryChange> changes,List<Payment> payments) {
        require(s.cityMarkets==null,LocalText.tr("Глобальний /trade замінено товарними заявками: /market menu."));
        return db.transaction(()->{
            require(!actor.id().equals(other) && changes.size()==2 && new HashSet<>(changes.stream().map(InventoryChange::person).toList()).equals(Set.of(actor.id(),other)),LocalText.tr("Торгівля потребує двох різних учасників."));
            require(payments.size()<=2,LocalText.tr("Забагато грошових пропозицій."));
            String id=intent(actor,"TRADE",null,0,changes);
            for(var p:payments) {
                require((p.from().equals("p:"+actor.id()) && p.to().equals("p:"+other)) || (p.from().equals("p:"+other) && p.to().equals("p:"+actor.id())),LocalText.tr("Неправильні рахунки торгівлі."));
                require(p.amount()>0 && p.amount()<=CurrencyService.MAX_MONEY,LocalText.tr("Неправильна сума."));
                s.move(p.from(),escrow(id,p.from()),p.code(),p.amount());
                db.execute("INSERT INTO item_payments VALUES(?,?,?,?,?)",id,p.from(),p.to(),p.code(),p.amount());
            }return id;
        });
    }
    private static String escrow(String id,String source) {return "x:"+id+":"+source.substring(2);}
    private void own(Actor actor,String owner) throws SQLException {
        require(owner.equals("p:"+actor.id()) || owner.startsWith("e:"),LocalText.tr("Недоступний рахунок."));
        if(owner.startsWith("e:"))s.controlTreasury(actor,s.estate(owner.substring(2)));
    }
    public String create(Actor actor,String owner,String item,long count,String code,long reward,int days) {
        require(s.cityMarkets==null,LocalText.tr("Нові замовлення: /market jobs біля стійки Відділу роботи і услуг."));
        return db.transaction(()->{
            own(actor,owner);available(actor.id());require(item!=null && item.length()<=262144,LocalText.tr("Предмет має забагато даних."));
            require(count>=1 && count<=1_000_000 && reward>0 && reward<=CurrencyService.MAX_MONEY && days>=1 && days<=90,LocalText.tr("Кількість: 1–1000000; оплата додатна; строк: 1–90 діб."));
            String id=UUID.randomUUID().toString();s.move(owner,"o:"+id,code,reward);
            db.execute("INSERT INTO orders VALUES(?,?,?,?,?,0,0,?,?,0,'OPEN',?,?)",id,owner,actor.id(),item,count,code,reward,s.now(),Math.addExact(s.now(),days*FeudalService.DAY));
            s.audit(actor,"ORDER_CREATE",id+" "+count+LocalText.tr(" за ")+format(reward)+" "+code);return id;
        });
    }
    public Map<String,Object> order(String id) throws SQLException {
        var r=db.one("SELECT * FROM orders WHERE id=?",id);require(r!=null,LocalText.tr("Замовлення не знайдено."));return r;
    }
    private void orderAvailable(String id) throws SQLException {
        require(db.one("SELECT id FROM item_ops WHERE related=? AND status='PREPARED'",id)==null,LocalText.tr("Для замовлення є незавершена передача предметів."));
    }
    public static long cumulative(long reward,long delivered,long total) {
        return BigInteger.valueOf(reward).multiply(BigInteger.valueOf(delivered)).divide(BigInteger.valueOf(total)).longValueExact();
    }
    public String deliver(Actor actor,String order,long count,InventoryChange inventory) {
        require(s.cityMarkets==null,LocalText.tr("Старі замовлення закрито; постачання: /market contract ID deliver кількість."));
        return db.transaction(()->{
            var o=order(order);orderAvailable(order);
            require("OPEN".equals(string(o,"status")) && number(o,"ends")>s.now(),LocalText.tr("Замовлення вже закрите або прострочене."));
            require(!string(o,"owner").equals("p:"+actor.id()),LocalText.tr("Не можна виконати власне особисте замовлення."));
            require(count>0 && count<=number(o,"total")-number(o,"delivered"),LocalText.tr("Завелика або нульова поставка."));
            require(inventory.person().equals(actor.id()),LocalText.tr("Неправильний інвентар."));
            return intent(actor,"DELIVER",order,count,List.of(inventory));
        });
    }
    public String collect(Actor actor,String order,long count,InventoryChange inventory) {
        require(s.cityMarkets==null,LocalText.tr("Для старих предметів адміністратор реєструє місце повернення: /market admin legacy."));
        return db.transaction(()->{
            var o=order(order);own(actor,string(o,"owner"));orderAvailable(order);
            require(count>0 && count<=number(o,"unclaimed"),LocalText.tr("Стількох предметів у сховищі немає."));
            require(inventory.person().equals(actor.id()),LocalText.tr("Неправильний інвентар."));
            return intent(actor,"COLLECT",order,count,List.of(inventory));
        });
    }
    /** Caller has saved every participant's after-inventory, or explicitly reconciled it. */
    public void finish(String id,boolean applied) {
        db.transaction(()->{
            var op=db.one("SELECT * FROM item_ops WHERE id=?",id);require(op!=null,LocalText.tr("Операцію не знайдено."));
            if(!"PREPARED".equals(string(op,"status"))){require(string(op,"kind").startsWith("CM_") && (applied?"APPLIED":"FAILED").equals(string(op,"status")),LocalText.tr("Операцію завершено або її результат відрізняється."));return null;}
            Actor actor=new Actor(string(op,"actor"),false);
            for(var p:db.query("SELECT * FROM item_payments WHERE op=?",id)) {
                String source=string(p,"source"),target=applied?string(p,"target"):source,code=string(p,"code");long n=number(p,"amount");
                if(applied)s.move(escrow(id,source),target,code,n);else s.restore(escrow(id,source),target,code,n);s.currencies.log(actor,code,target,applied?"TRADE":"TRADE_REFUND",id+" "+format(n));
            }
            String kind=string(op,"kind"),related=string(op,"related");long n=number(op,"quantity");
            if(applied && kind.equals("DELIVER")) {
                var o=order(related);long delivered=Math.addExact(number(o,"delivered"),n);
                long paid=cumulative(number(o,"reward"),delivered,number(o,"total")),payout=paid-number(o,"paid");
                if(payout>0)s.move("o:"+related,"p:"+actor.id(),string(o,"code"),payout);
                db.execute("UPDATE orders SET delivered=?,unclaimed=unclaimed+?,paid=?,status=? WHERE id=?",delivered,n,paid,delivered==number(o,"total")?"FULFILLED":"OPEN",related);
                s.currencies.log(actor,string(o,"code"),"p:"+actor.id(),"ORDER_PAYMENT",related+" "+n+" -> "+format(payout));
            }
            if(applied && kind.equals("COLLECT")) {
                require(db.execute("UPDATE orders SET unclaimed=unclaimed-? WHERE id=? AND unclaimed>=?",n,related,n)==1,LocalText.tr("Сховище замовлення змінилося."));
            }
            if(kind.equals("VC_IN") || kind.equals("VC_OUT"))ContractService.finishItems(db,id,applied);
            if(kind.equals("PROV_IN") || kind.equals("PROV_OUT"))ProvisionService.finish(db,id,applied);
            if(kind.equals("CM_IN") || kind.equals("CM_OUT")){require(s.cityMarkets!=null,LocalText.tr("Система ринків недоступна."));s.cityMarkets.finishTransfer(id,applied);}
            db.execute("UPDATE item_ops SET status=? WHERE id=?",applied?"APPLIED":"FAILED",id);
            s.audit(actor,"ITEM_RESOLVE",id+" "+applied);return null;
        });
    }
    public void cancel(Actor actor,String id) {
        db.transaction(()->{var o=order(id);own(actor,string(o,"owner"));close(o,"CANCELLED");s.audit(actor,"ORDER_CANCEL",id);return null;});
    }
    private void close(Map<String,Object> o,String state) throws SQLException {
        String id=string(o,"id");orderAvailable(id);require("OPEN".equals(string(o,"status")),LocalText.tr("Замовлення вже закрите."));
        long refund=number(o,"reward")-number(o,"paid");if(refund>0)s.restore("o:"+id,string(o,"owner"),string(o,"code"),refund);
        db.execute("UPDATE orders SET status=? WHERE id=?",state,id);
    }
    public void tick() {
        db.transaction(()->{
            for(var o:db.query("SELECT * FROM orders WHERE status='OPEN' AND ends<=?",s.now())) {
                if(db.one("SELECT id FROM item_ops WHERE related=? AND status='PREPARED'",string(o,"id"))!=null)continue;
                close(o,"EXPIRED");s.audit(new Actor("orders",true),"ORDER_EXPIRE",string(o,"id"));
            }return null;
        });
    }
}
