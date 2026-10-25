package ua.empirecraft.feudal;

import java.math.*;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Main-thread H2 ledger. PREPARED inventory intents freeze the town until explicit reconciliation. */
public final class ProvisionService {
    final FeudalService s;final Database db;final CommerceService commerce;
    public static final int SLOTS=45;
    public ProvisionService(FeudalService s,CommerceService commerce)throws SQLException {
        this.s=s;this.db=s.db;this.commerce=commerce;
        for(String ddl:List.of(
            "CREATE TABLE IF NOT EXISTS provision_towns(town VARCHAR(80) PRIMARY KEY,last_at BIGINT,chunks BIGINT,credit DECIMAL(38,0),hungry_since BIGINT,hungry_ms BIGINT,worst_ms BIGINT,demand DECIMAL(38,0),unmet DECIMAL(38,0),clock_epoch BIGINT,clock_phase BIGINT,clock_pause BOOLEAN)",
            "CREATE TABLE IF NOT EXISTS provision_stock(town VARCHAR(80),slot INT,item CLOB,quantity INT,nutrition INT,max_stack INT,version BIGINT,PRIMARY KEY(town,slot),CHECK(quantity>=0 AND quantity<=max_stack),CHECK(nutrition>0))",
            "CREATE TABLE IF NOT EXISTS provision_transfers(op VARCHAR(36) PRIMARY KEY,town VARCHAR(80),slot INT,before_version BIGINT,item CLOB,quantity INT,nutrition INT,max_stack INT)",
            "CREATE TABLE IF NOT EXISTS provision_actions(id VARCHAR(200) PRIMARY KEY,town VARCHAR(80),streak BIGINT,period BIGINT,target VARCHAR(160),status VARCHAR(16),created BIGINT,detail CLOB)",
            "CREATE INDEX IF NOT EXISTS provision_transfer_town ON provision_transfers(town)",
            "CREATE INDEX IF NOT EXISTS provision_action_period ON provision_actions(town,streak,period)",
            "CREATE INDEX IF NOT EXISTS provision_action_status ON provision_actions(status)"))db.execute(ddl);
    }
    static BigInteger energy(Map<String,Object> row,String key){return new BigInteger(string(row,key));}
    public Map<String,Object> town(String town)throws SQLException{return db.one("SELECT * FROM provision_towns WHERE town=?",town);}
    public List<Map<String,Object>> stock(String town)throws SQLException{return db.query("SELECT * FROM provision_stock WHERE town=? AND quantity>0 ORDER BY slot",town);}
    public boolean pending(String town)throws SQLException{return db.one("SELECT p.op FROM provision_transfers p JOIN item_ops o ON o.id=p.op WHERE p.town=? AND o.status='PREPARED'",town)!=null;}
    public void available(String town)throws SQLException{require(!pending(town),"Передачу провізії потрібно завершити: /commerce pending, inspect, recover або abort.");}
    public BigInteger reserve(String town)throws SQLException {
        BigInteger total=energy(town(town),"credit");
        for(var row:stock(town))total=total.add(ProvisionClock.UNIT.multiply(BigInteger.valueOf(number(row,"quantity")*number(row,"nutrition"))));return total;
    }
    /** First installation grants no retroactive debt. An offline interval uses the last durable land count. */
    public boolean settle(String town,long chunks,long now,ProvisionClock clock) {
        return db.transaction(()->{
            var t=town(town);
            if(t==null){db.execute("INSERT INTO provision_towns VALUES(?,?,?,0,0,0,0,0,0,?,?,?)",town,now,chunks,clock.epoch(),clock.phase(),clock.paused());return true;}
            if(pending(town))return false;
            long from=number(t,"last_at"),end=Math.max(from,now),oldChunks=number(t,"chunks");
            BigInteger demand=cost(t,clock,from,end,oldChunks),reserve=reserve(town),paid=demand.min(reserve),remaining=paid;
            BigInteger credit=energy(t,"credit"),used=credit.min(remaining);credit=credit.subtract(used);remaining=remaining.subtract(used);
            for(var row:stock(town)){
                if(remaining.signum()==0)break;
                BigInteger one=ProvisionClock.UNIT.multiply(BigInteger.valueOf(number(row,"nutrition")));
                int count=remaining.add(one).subtract(BigInteger.ONE).divide(one).min(BigInteger.valueOf(number(row,"quantity"))).intValueExact();
                BigInteger taken=one.multiply(BigInteger.valueOf(count));used=remaining.min(taken);remaining=remaining.subtract(used);credit=credit.add(taken.subtract(used));
                db.execute("UPDATE provision_stock SET quantity=quantity-?,version=version+1 WHERE town=? AND slot=?",count,town,number(row,"slot"));
            }
            long since=number(t,"hungry_since"),hungry=0,worst=number(t,"worst_ms");
            if(oldChunks>0 && demand.compareTo(reserve)>=0 && end>from){
                if(since==0){long low=from,high=end;while(low<high){long mid=low+(high-low)/2;if(cost(t,clock,from,mid,oldChunks).compareTo(reserve)>=0)high=mid;else low=mid+1;}since=low;}
                hungry=end-Math.max(from,since);worst=Math.max(worst,end-since);
            }else if(reserve.subtract(demand).signum()>0 || oldChunks==0)since=0;
            db.execute("UPDATE provision_towns SET last_at=?,chunks=?,credit=?,hungry_since=?,hungry_ms=hungry_ms+?,worst_ms=?,demand=demand+?,unmet=unmet+?,clock_epoch=?,clock_phase=?,clock_pause=? WHERE town=?",
                end,chunks,new BigDecimal(credit),since,hungry,worst,new BigDecimal(demand),new BigDecimal(demand.subtract(paid)),clock.epoch(),clock.phase(),clock.paused(),town);
            return true;
        });
    }
    private static BigInteger cost(Map<String,Object> town,ProvisionClock current,long from,long to,long chunks){
        long split=Math.min(to,Math.max(from,current.epoch()));
        if(split==from)return current.cost(from,to,chunks);
        var previous=new ProvisionClock(number(town,"clock_epoch"),number(town,"clock_phase"),bool(town,"clock_pause"),number(town,"clock_epoch"));
        return previous.cost(from,split,chunks).add(current.cost(split,to,chunks));
    }
    public boolean canSupport(String town,long proposedChunks,long now,ProvisionClock clock)throws SQLException {
        available(town);return reserve(town).compareTo(clock.cost(now,Math.addExact(now,ProvisionClock.DAY),proposedChunks))>=0;
    }
    public String transfer(Actor actor,String town,int slot,long expectedVersion,String item,int delta,int nutrition,int maxStack,CommerceService.InventoryChange inventory) {
        return db.transaction(()->{
            available(town);require(town(town)!=null && inventory.person().equals(actor.id()),"Неправильне місто або інвентар.");
            require(slot>=0 && slot<SLOTS && delta!=0 && nutrition>0 && nutrition<=1_000_000 && maxStack>=1 && maxStack<=99,"Неправильний предмет провізії.");
            require(item!=null && item.length()<=262144,"Предмет має забагато даних.");
            var before=db.one("SELECT * FROM provision_stock WHERE town=? AND slot=?",town,slot);
            long version=before==null?0:number(before,"version");int quantity=before==null?0:(int)number(before,"quantity");
            require(version==expectedVersion,"Сховище змінилося. Відкрийте його знову.");
            require(quantity==0 && delta>0 || before!=null && string(before,"item").equals(item) && number(before,"nutrition")==nutrition && number(before,"max_stack")==maxStack,"Предмет у комірці змінився.");
            require((long)quantity+delta>=0 && (long)quantity+delta<=maxStack,"У комірці недостатньо предметів або місця.");
            String op=commerce.intent(actor,delta>0?"PROV_IN":"PROV_OUT",null,Math.abs((long)delta),List.of(inventory));
            db.execute("INSERT INTO provision_transfers VALUES(?,?,?,?,?,?,?,?)",op,town,slot,version,item,quantity+delta,nutrition,maxStack);return op;
        });
    }
    /** Runs INSIDE the original inventory journal transaction; never nests transactions. */
    static void finish(Database db,String op,boolean applied)throws SQLException {
        var t=db.one("SELECT * FROM provision_transfers WHERE op=?",op);require(t!=null,"Немає плану провізії.");if(!applied)return;
        String town=string(t,"town");long slot=number(t,"slot");var old=db.one("SELECT * FROM provision_stock WHERE town=? AND slot=?",town,slot);
        require((old==null?0:number(old,"version"))==number(t,"before_version"),"Комірка провізії змінилася під час передачі.");
        db.execute("MERGE INTO provision_stock KEY(town,slot) VALUES(?,?,?,?,?,?,?)",town,slot,string(t,"item"),number(t,"quantity"),number(t,"nutrition"),number(t,"max_stack"),number(t,"before_version")+1);
        if(number(t,"quantity")>0){
            db.execute("UPDATE provision_actions SET status='CANCELLED',detail='Місто знову отримало їжу' WHERE town=? AND status='PENDING'",town);
            db.execute("UPDATE provision_towns SET hungry_since=0 WHERE town=?",town);
        }
    }
    /** A durable fixed batch prevents a restart from choosing extra plots in the same hunger period. */
    public List<Map<String,Object>> plan(String town,long streak,long period,List<String> targets,long now) {
        return db.transaction(()->{
            if(db.one("SELECT id FROM provision_actions WHERE town=? AND streak=? AND period=?",town,streak,period)==null){
                List<String> selected=targets.isEmpty()?List.of("NONE"):targets;
                for(String target:selected){String id=town+":"+streak+":"+period+":"+target;
                    db.execute("INSERT INTO provision_actions VALUES(?,?,?,?,?,'PENDING',?,'')",id,town,streak,period,target,now);}
            }return db.query("SELECT * FROM provision_actions WHERE town=? AND streak=? AND period=?",town,streak,period);
        });
    }
    public void action(String id,String state,String detail){db.transaction(()->{db.execute("UPDATE provision_actions SET status=?,detail=? WHERE id=?",state,detail,id);s.audit(new Actor("provisions",true),"PROVISION_ACTION",id+" "+state+" "+detail);return null;});}
}
