package ua.empirecraft.feudal;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Durable intent and coin escrow precede the separate Bukkit inventory delivery. */
public final class TradeLedger {
    private final FeudalService s;private final Database db;
    public TradeLedger(FeudalService s) throws SQLException {this.s=s;db=s.db;
        db.execute("CREATE TABLE IF NOT EXISTS trade_ops (id VARCHAR(36) PRIMARY KEY,a VARCHAR(36),b VARCHAR(36),status VARCHAR(16),before_a CLOB,after_a CLOB,before_b CLOB,after_b CLOB,created BIGINT)");
        db.execute("CREATE TABLE IF NOT EXISTS trade_coins (id VARCHAR(36),side INT,code VARCHAR(8),amount BIGINT CHECK(amount>0),PRIMARY KEY(id,side,code))");db.execute("CREATE INDEX IF NOT EXISTS trade_status ON trade_ops(status)");}
    public boolean pending(String p) throws SQLException {return db.one("SELECT id FROM trade_ops WHERE status='PREPARED' AND (a=? OR b=?)",p,p)!=null;}
    public List<Map<String,Object>> pending() throws SQLException {return db.query("SELECT * FROM trade_ops WHERE status='PREPARED' ORDER BY created LIMIT 50");}
    public Map<String,Object> operation(String id) throws SQLException {var row=db.one("SELECT * FROM trade_ops WHERE id=?",id);require(row!=null,"Обмін не знайдено.");return row;}
    private static String escrow(String id,int side){return "t:"+id+":"+side;}
    public String prepare(String a,String b,Map<String,Long> first,Map<String,Long> second,String beforeA,String afterA,String beforeB,String afterB){
        var ac=Map.copyOf(first);var bc=Map.copyOf(second);return db.transaction(()->{
            require(UUID.fromString(a).toString().equals(a)&&UUID.fromString(b).toString().equals(b)&&!a.equals(b),"Потрібні два різні гравці.");require(!pending(a)&&!pending(b),"Попередній обмін потребує перевірки адміністратора.");
            require(beforeA!=null&&afterA!=null&&beforeB!=null&&afterB!=null,"Немає знімків інвентарів.");var codes=new HashSet<String>();codes.addAll(ac.keySet());codes.addAll(bc.keySet());
            for(String code:codes){s.currency(code);long x=ac.getOrDefault(code,0L),y=bc.getOrDefault(code,0L);require((!ac.containsKey(code)||x>0)&&(!bc.containsKey(code)||y>0)&&x<=CurrencyService.MAX_MONEY&&y<=CurrencyService.MAX_MONEY,"Неправильна сума монет.");long av=s.balance("p:"+a,code),bv=s.balance("p:"+b,code);require(av>=x&&bv>=y,"Учасник уже не має запропонованих монет: "+code);require(Math.addExact(av-x,y)<=CurrencyService.MAX_MONEY&&Math.addExact(bv-y,x)<=CurrencyService.MAX_MONEY,"Ліміт балансу: "+code);}
            String id=UUID.randomUUID().toString();db.execute("INSERT INTO trade_ops VALUES(?,?,?,'PREPARED',?,?,?,?,?)",id,a,b,beforeA,afterA,beforeB,afterB,s.now());reserve(id,0,a,ac);reserve(id,1,b,bc);s.audit(new Actor(a,false),"TRADE_PREPARE",id+" "+a+" <-> "+b);return id;
        });}
    private void reserve(String id,int side,String p,Map<String,Long> coins) throws SQLException {for(var e:coins.entrySet()){s.setBalance("p:"+p,e.getKey(),s.balance("p:"+p,e.getKey())-e.getValue());s.setBalance(escrow(id,side),e.getKey(),e.getValue());db.execute("INSERT INTO trade_coins VALUES(?,?,?,?)",id,side,e.getKey(),e.getValue());}}
    /** Called only after both inventories were saved in the chosen state. */
    public void finish(String id,boolean applied,Actor actor){db.transaction(()->{
        var op=operation(id);require("PREPARED".equals(string(op,"status")),"Обмін уже завершено; повтор заборонений.");require(actor.admin()||actor.id().equals(string(op,"a")),"Немає доступу.");
        var rows=db.query("SELECT * FROM trade_coins WHERE id=?",id);var credits=new LinkedHashMap<String,Map<String,Long>>();
        for(var r:rows){int side=(int)number(r,"side");String code=string(r,"code");long n=number(r,"amount");require(s.balance(escrow(id,side),code)==n,"Пошкоджений резерв обміну.");String target="p:"+string(op,applied?(side==0?"b":"a"):(side==0?"a":"b"));var account=credits.computeIfAbsent(target,k->new LinkedHashMap<>());long balance=Math.addExact(account.getOrDefault(code,s.balance(target,code)),n);require(balance<=CurrencyService.MAX_MONEY,"Ліміт балансу.");account.put(code,balance);}
        for(var a:credits.entrySet())for(var c:a.getValue().entrySet())s.setBalance(a.getKey(),c.getKey(),c.getValue());
        for(var r:rows){int side=(int)number(r,"side");String code=string(r,"code"),from=string(op,side==0?"a":"b"),to=string(op,side==0?"b":"a");s.setBalance(escrow(id,side),code,0);if(applied){s.currencies.log(new Actor(from,false),code,"p:"+from,"TRADE_OUT",id+" -> "+to+" "+format(number(r,"amount")));s.currencies.log(new Actor(to,false),code,"p:"+to,"TRADE_IN",id+" <- "+from+" "+format(number(r,"amount")));}}
        db.execute("UPDATE trade_ops SET status=? WHERE id=?",applied?"APPLIED":"CANCELLED",id);s.audit(actor,applied?"TRADE_COMPLETE":"TRADE_ROLLBACK",id);return null;
    });}
}
