package ua.empirecraft.feudal;

import java.math.*;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** All ledger changes, including both sides of an exchange, commit on the same H2 connection. */
public final class CurrencyService {
    public static final int GOLD_SCALE=12;
    public static final long MAX_MONEY=900_000_000_000_000L;
    public static final BigDecimal ZERO=BigDecimal.ZERO.setScale(GOLD_SCALE);
    private final FeudalService s;
    private final Database db;
    CurrencyService(FeudalService s){this.s=s;this.db=s.db;}
    public static BigDecimal gold(String text) {
        try {
            require(text!=null && text.matches("[0-9]{1,13}([.,][0-9]{1,12})?"),LocalText.tr("Неправильна сума золота."));
            BigDecimal n=new BigDecimal(text.replace(',','.')).setScale(GOLD_SCALE);
            require(n.signum()>=0 && n.compareTo(new BigDecimal("9000000000000"))<=0,LocalText.tr("Завелика сума золота."));return n;
        }catch(RuntimeException ex){throw new IllegalArgumentException(LocalText.tr("Золото: невід’ємне число, до 12 знаків після коми; без експонент."));}
    }
    public static String show(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
    public BigDecimal goldBalance(String owner) throws SQLException {
        var r=db.one("SELECT balance FROM gold_accounts WHERE owner=?",owner);return r==null?ZERO:(BigDecimal)r.get("balance");
    }
    void setGold(String owner,BigDecimal n) throws SQLException {
        require(n.signum()>=0,LocalText.tr("Недостатньо золота."));
        n=n.setScale(GOLD_SCALE,RoundingMode.UNNECESSARY);
        require(n.precision()<=38,LocalText.tr("Переповнення золотого рахунку."));
        db.execute("MERGE INTO gold_accounts KEY(owner) VALUES(?,?)",owner,n);
    }
    private void moveGold(String from,String to,BigDecimal n) throws SQLException {
        require(n.signum()>0 && !from.equals(to),LocalText.tr("Сума має бути додатною, рахунки — різними."));
        setGold(from,goldBalance(from).subtract(n));setGold(to,goldBalance(to).add(n));
    }
    /** A payable is only a right to collect physical ingots, never a transferable bank account. */
    public void exchangePhysical(Actor actor,String id,BigDecimal minimum,String stamp){s.market.exchange.executePhysical(actor,id,minimum,stamp);}
    public void migratePersonalGold(){db.transaction(()->{
        for(var row:db.query("SELECT owner,balance FROM gold_accounts WHERE owner LIKE 'p:%' AND balance>0")){
            String owner=string(row,"owner"),due="d:"+owner.substring(2);BigDecimal n=(BigDecimal)row.get("balance");
            setGold(due,goldBalance(due).add(n));setGold(owner,ZERO);
        }return null;
    });}
    public BigDecimal payable(String person)throws SQLException{return goldBalance("d:"+person);}
    void owe(String person,BigDecimal amount)throws SQLException{setGold("d:"+person,payable(person).add(amount));}
    public void treasuryPayout(Actor actor,String estate,BigDecimal amount){db.transaction(()->{
        s.controlTreasury(actor,s.estate(estate));require(amount.signum()>0,LocalText.tr("Потрібна додатна сума."));
        String owner="e:"+estate;setGold(owner,goldBalance(owner).subtract(amount));owe(actor.id(),amount);return null;
    });}
    public Map<String,Object> goldCurrency(String code) throws SQLException {
        var c=s.currency(code);require("GOLD".equals(string(c,"backing")),LocalText.tr("Ця валюта не має фізичного золотого резерву. Стару валюту BASE можна лише погасити або перевести після повного погашення."));return c;
    }
    public BigDecimal reserve(String code) throws SQLException {goldCurrency(code);return goldBalance("r:"+code);}
    public BigDecimal value(String code) throws SQLException {
        var c=goldCurrency(code);long supply=number(c,"supply");
        require(supply>0,LocalText.tr("Обіг нульовий: курс ще не визначений. Внесіть резерв і виконайте перший випуск."));
        return reserve(code).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(supply),GOLD_SCALE,RoundingMode.DOWN);
    }
    void owned(Actor actor,String owner) throws SQLException {
        require(owner.equals("p:"+actor.id()) || owner.startsWith("e:"),LocalText.tr("Недоступний рахунок."));
        if(owner.startsWith("e:"))s.controlTreasury(actor,s.estate(owner.substring(2)));
    }
    private void issuer(Actor actor,String code) throws SQLException {goldCurrency(code);if(s.market!=null)s.market.manage(actor,code);else s.control(actor,s.estate(string(goldCurrency(code),"issuer")));}
    void log(Actor actor,String code,String owner,String event,String detail) throws SQLException {
        db.execute("INSERT INTO currency_log(at_time,actor,code,owner,event,detail) VALUES(?,?,?,?,?,?)",s.now(),actor.id(),code,owner,event,detail);
        s.audit(actor,event,(code==null?"GOLD":code)+" "+owner+" "+detail);
        if(s.market!=null && code!=null)s.market.record(code);
    }
    public void transferGold(Actor actor,String from,String to,BigDecimal amount) {
        db.transaction(()->{
            owned(actor,from);
            require(to.matches("p:[0-9a-fA-F-]{36}") || to.startsWith("e:"),LocalText.tr("Недоступний одержувач."));
            if(to.startsWith("e:")){if(s.market!=null)require(s.market.eligible(to.substring(2)),LocalText.tr("Казна залежного володіння приймає лише валюту сюзерена, не золото."));var e=s.estate(to.substring(2));require(e.active() || s.controlsTreasury(actor,e),LocalText.tr("Архівна скарбниця недоступна."));}
            moveGold(from,to,amount);log(actor,null,from,"GOLD_TRANSFER",to+" "+show(amount)+LocalText.tr(" ЗО"));log(actor,null,to,"GOLD_RECEIVE",from+" "+show(amount)+LocalText.tr(" ЗО"));return null;
        });
    }
    public void depositReserve(Actor actor,String from,String code,BigDecimal amount) {
        db.transaction(()->{issuer(actor,code);owned(actor,from);moveGold(from,"r:"+code,amount);log(actor,code,from,"RESERVE_DEPOSIT",show(amount)+LocalText.tr(" ЗО"));if(s.market!=null)s.market.exchange.manual(actor,code,amount);return null;});
    }
    public void mint(Actor actor,String code,long amount) {
        require(s.market==null,LocalText.tr("Друк монет доступний лише через /cu print; миттєвий випуск вимкнено."));
        db.transaction(()->{issuer(actor,code);issue(actor,code,amount);return null;});
    }
    // Only the durable queue calls this in production, within its existing transaction.
    void issue(Actor actor,String code,long amount)throws SQLException {
        require(amount>0,LocalText.tr("Сума має бути більшою за нуль."));var c=goldCurrency(code);
        long supply=Math.addExact(number(c,"supply"),amount);require(supply<=MAX_MONEY,LocalText.tr("Перевищено ліміт обігу."));
        String treasury="e:"+string(c,"issuer");s.setBalance(treasury,code,Math.addExact(s.balance(treasury,code),amount));
        db.execute("UPDATE currencies SET supply=? WHERE code=?",supply,code);
        log(actor,code,treasury,"MINT",format(amount)+"; R="+show(reserve(code))+"; M="+format(supply));
    }
    public Quote buyGold(Actor actor,String amount,String to,long minimum){
        require(s.market!=null,LocalText.tr("Ринок недоступний."));
        return s.market.exchange.execute(actor,"@GOLD",to,amount,BigDecimal.valueOf(minimum,2),null).legacy();
    }
    public void burn(Actor actor,String from,String code,long amount) {
        db.transaction(()->{
            issuer(actor,code);owned(actor,from);var c=goldCurrency(code);
            require(amount>0 && s.balance(from,code)>=amount && number(c,"supply")>=amount,LocalText.tr("Недостатньо власних монет для вилучення."));
            s.setBalance(from,code,s.balance(from,code)-amount);db.execute("UPDATE currencies SET supply=supply-? WHERE code=?",amount,code);
            log(actor,code,from,"BURN",format(amount)+LocalText.tr("; золото залишається в резерві"));return null;
        });
    }
    private BigDecimal redemption(Map<String,Object> c,long amount) throws SQLException {
        long supply=number(c,"supply");require(amount>0 && amount<=supply,LocalText.tr("Неправильна сума погашення."));
        BigDecimal r=reserve(string(c,"code"));require(r.signum()>0,LocalText.tr("Золотий резерв порожній; погашення та обмін недоступні."));
        // Last redemption receives all remaining dust; every earlier payout rounds down.
        BigDecimal result=amount==supply?r:r.multiply(BigDecimal.valueOf(amount)).divide(BigDecimal.valueOf(supply),GOLD_SCALE,RoundingMode.DOWN);
        require(result.signum()>0,LocalText.tr("Сума замала для точності золотого обліку."));return result;
    }
    public BigDecimal redeem(Actor actor,String from,String code,long amount) {
        if(s.market!=null){try{if("GOLD".equals(string(s.currency(code),"backing"))){s.market.exchange.request(actor,from,code,amount,ZERO,null);return ZERO;}}catch(SQLException ex){throw new IllegalStateException(ex);}}
        return db.transaction(()->{
            owned(actor,from);var c=s.currency(code);
            require(!code.equals("BASE") && amount>0 && s.balance(from,code)>=amount,LocalText.tr("Недостатньо монет для погашення."));
            if("LEGACY_BASE".equals(string(c,"backing"))){
                require(number(c,"supply")>=amount && s.balance("r:"+code,"BASE")>=amount,LocalText.tr("Недостатньо старого резерву BASE."));
                s.setBalance("r:"+code,"BASE",s.balance("r:"+code,"BASE")-amount);
                s.setBalance(from,"BASE",Math.addExact(s.balance(from,"BASE"),amount));
                s.setBalance(from,code,s.balance(from,code)-amount);db.execute("UPDATE currencies SET supply=supply-? WHERE code=?",amount,code);
                log(actor,code,from,"LEGACY_REDEEM",format(amount)+" BASE");return ZERO;
            }
            BigDecimal payout=redemption(c,amount);moveGold("r:"+code,from,payout);
            s.setBalance(from,code,s.balance(from,code)-amount);db.execute("UPDATE currencies SET supply=supply-? WHERE code=?",amount,code);
            log(actor,code,from,"REDEEM",format(amount)+" -> "+show(payout)+LocalText.tr(" ЗО"));return payout;
        });
    }
    public record Quote(long coins,BigDecimal gold,BigDecimal targetGold,BigDecimal change) {}
    public Quote quote(long amount,String from,String to) throws SQLException {
        if(s.market!=null)return s.market.exchange.quote(from,to,format(amount)).legacy();
        require(!from.equals(to),LocalText.tr("Оберіть різні валюти."));var a=goldCurrency(from);var b=goldCurrency(to);
        require(s.market==null?s.estate(string(b,"issuer")).active():s.market.eligible(string(b,"issuer")),LocalText.tr("Держава одержуваної валюти неактивна; нова емісія заборонена."));
        BigDecimal sourceGold=redemption(a,amount), targetReserve=reserve(to);long supply=number(b,"supply");
        require(supply>0 && targetReserve.signum()>0,LocalText.tr("Цільова валюта ще не має курсу або золотого резерву."));
        long coins;
        try {coins=sourceGold.multiply(BigDecimal.valueOf(supply)).divide(targetReserve,0,RoundingMode.DOWN).longValueExact();}
        catch(ArithmeticException ex){throw new IllegalArgumentException(LocalText.tr("Сума обміну завелика."));}
        require(coins>0,LocalText.tr("Результат менший за 0.01 монети; збільшіть суму обміну."));
        require(Math.addExact(supply,coins)<=MAX_MONEY,LocalText.tr("Перевищено ліміт обігу одержуваної валюти."));
        // Use CEILING for backing the issued target cents: rounding cannot dilute its holders.
        BigDecimal targetGold=targetReserve.multiply(BigDecimal.valueOf(coins)).divide(BigDecimal.valueOf(supply),GOLD_SCALE,RoundingMode.CEILING);
        require(targetGold.compareTo(sourceGold)<=0,LocalText.tr("Сума замала для точного обміну."));
        return new Quote(coins,sourceGold,targetGold,sourceGold.subtract(targetGold));
    }
    public Quote convert(Actor actor,long amount,String from,String to,long minimum) {
        if(s.market!=null)return s.market.exchange.execute(actor,from,to,format(amount),BigDecimal.valueOf(minimum,2),null).legacy();
        return db.transaction(()->{
            String owner="p:"+actor.id();require(s.balance(owner,from)>=amount,LocalText.tr("Недостатньо монет для обміну."));
            Quote q=quote(amount,from,to);require(q.coins()>=minimum,LocalText.tr("Курс змінився: результат менший за вказаний мінімум."));
            s.setBalance(owner,from,s.balance(owner,from)-amount);db.execute("UPDATE currencies SET supply=supply-? WHERE code=?",amount,from);
            setGold("r:"+from,reserve(from).subtract(q.gold()));setGold("r:"+to,reserve(to).add(q.targetGold()));
            setGold(owner,goldBalance(owner).add(q.change()));
            s.setBalance(owner,to,Math.addExact(s.balance(owner,to),q.coins()));db.execute("UPDATE currencies SET supply=supply+? WHERE code=?",q.coins(),to);
            String detail=format(amount)+" "+from+" -> "+format(q.coins())+" "+to+LocalText.tr("; решта=")+show(q.change())+LocalText.tr(" ЗО; комісія=0");
            log(actor,from,owner,"EXCHANGE_OUT",detail);log(actor,to,owner,"EXCHANGE_IN",detail);return q;
        });
    }
    public void upgrade(Actor actor,String code) {
        db.transaction(()->{
            var c=s.currency(code);require("LEGACY_BASE".equals(string(c,"backing")),LocalText.tr("Це не стара валюта BASE."));if(s.market!=null)s.market.creation(actor,string(c,"issuer"));else s.control(actor,s.estate(string(c,"issuer")));
            require(number(c,"supply")==0 && s.balance("r:"+code,"BASE")==0,LocalText.tr("Спочатку погасіть увесь старий випуск у BASE."));
            require(db.one("SELECT owner FROM accounts WHERE code=? AND balance<>0",code)==null,LocalText.tr("Сума рахунків не відповідає нульовому обігу."));
            db.execute("UPDATE currencies SET backing='GOLD' WHERE code=?",code);log(actor,code,"e:"+string(c,"issuer"),"CURRENCY_UPGRADE",LocalText.tr("Новий золотий облік; R=0; M=0"));return null;
        });
    }
    /** Durable intent precedes any Bukkit inventory change; uncertain outcomes are never retried. */
    public String prepareGold(Actor actor,String direction,String target,int amount,String before,String after) {
        return db.transaction(()->{
            require(Set.of("IN","OUT").contains(direction) && amount>0 && amount<=2304,LocalText.tr("Кількість злитків: ціле число від 1 до 2304."));
            CommerceService.inventoryAvailable(db,actor.id());
            require(before!=null && after!=null && !before.equals(after),LocalText.tr("Немає зміни інвентарю."));
            require(db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",actor.id())==null,LocalText.tr("Попередню операцію зі злитками має перевірити адміністратор: /cu gold pending."));
            if(direction.equals("IN") && target.startsWith("r:"))issuer(actor,target.substring(2));
            else if(direction.equals("IN") && target.startsWith("b:"))goldCurrency(target.substring(2));
            else if(direction.equals("OUT") && target.equals("d:"+actor.id())){}
            else require(target.equals("p:"+actor.id()),LocalText.tr("Злитки доступні лише через власний золотий рахунок."));
            String id=UUID.randomUUID().toString();if(direction.equals("OUT"))moveGold(target,"q:"+id,BigDecimal.valueOf(amount));
            db.execute("INSERT INTO gold_ops VALUES(?,?,?,?,?,'PREPARED',?,?,?)",id,actor.id(),direction,target,amount,before,after,s.now());
            s.audit(actor,"GOLD_PREPARE",id+" "+direction+" "+amount);return id;
        });
    }
    public void finishGold(Actor reviewer,String id,boolean applied) {
        db.transaction(()->{
            var op=db.one("SELECT * FROM gold_ops WHERE id=?",id);require(op!=null,LocalText.tr("Операцію не знайдено."));
            require(reviewer.admin() || reviewer.id().equals(string(op,"actor")),LocalText.tr("Немає доступу."));
            require("PREPARED".equals(string(op,"status")),LocalText.tr("Результат уже зафіксовано; повтор заборонений."));
            String target=string(op,"target");BigDecimal n=BigDecimal.valueOf(number(op,"amount"));
            if("IN".equals(string(op,"direction")) && applied){if(target.startsWith("b:") || s.market!=null && target.startsWith("p:"))owe(string(op,"actor"),n);else setGold(target,goldBalance(target).add(n));}
            if("OUT".equals(string(op,"direction"))){if(applied)setGold("q:"+id,ZERO);else moveGold("q:"+id,s.market!=null&&target.startsWith("p:")?"d:"+target.substring(2):target,n);}
            db.execute("UPDATE gold_ops SET status=? WHERE id=?",applied?"APPLIED":"FAILED",id);
            if(applied&&"IN".equals(string(op,"direction"))&&target.startsWith("r:")&&s.market!=null)s.market.exchange.manual(new Actor(string(op,"actor"),false),target.substring(2),n);
            log(reviewer,target.startsWith("r:")?target.substring(2):null,"p:"+string(op,"actor"),"GOLD_"+string(op,"direction"),id+" "+amountText(op)+"; "+(applied?LocalText.tr("виконано"):LocalText.tr("скасовано")));return null;
        });
    }
    private String amountText(Map<String,Object> op){return number(op,"amount")+LocalText.tr(" ЗО -> ")+string(op,"target");}
    public List<Map<String,Object>> history(Actor actor,String code) throws SQLException {
        if(code!=null && s.market!=null)return s.market.exchange.journal(code,0);
        if(code!=null){require(!code.equals("BASE"),LocalText.tr("BASE не має емітента; власні операції доступні через /currency history."));var c=s.currency(code);if(s.market!=null)s.market.manage(actor,code);else s.controlTreasury(actor,s.estate(string(c,"issuer")));return db.query("SELECT * FROM currency_log WHERE code=? ORDER BY seq DESC LIMIT 20",code);}
        return db.query("SELECT * FROM currency_log WHERE owner=? ORDER BY seq DESC LIMIT 20","p:"+actor.id());
    }
    public List<String> check() throws SQLException {
        var issues=new ArrayList<String>();
        for(var c:db.query("SELECT * FROM currencies WHERE code<>'BASE'")){
            String code=string(c,"code");long sum=0;for(var a:db.query("SELECT balance FROM accounts WHERE code=?",code))sum=Math.addExact(sum,number(a,"balance"));
            if(sum!=number(c,"supply"))issues.add(LocalText.tr("Обіг не дорівнює сумі всіх рахунків: ")+code);
            if("LEGACY_BASE".equals(string(c,"backing"))) {if(s.balance("r:"+code,"BASE")!=sum)issues.add(LocalText.tr("Старий резерв BASE не відповідає випуску: ")+code);}
            else if(!"GOLD".equals(string(c,"backing")))issues.add(LocalText.tr("Невідоме забезпечення: ")+code);
        }
        BigDecimal total=ZERO,net=ZERO;
        for(var r:db.query("SELECT balance FROM gold_accounts"))total=total.add((BigDecimal)r.get("balance"));
        for(var op:db.query("SELECT * FROM gold_ops")){
            BigDecimal n=BigDecimal.valueOf(number(op,"amount"));String status=string(op,"status"),dir=string(op,"direction");
            if(status.equals("APPLIED"))net=dir.equals("IN")?net.add(n):net.subtract(n);
            if(status.equals("PREPARED"))issues.add(LocalText.tr("Невизначена операція зі злитками: ")+string(op,"id"));
            BigDecimal expected=status.equals("PREPARED") && dir.equals("OUT")?n:ZERO;
            if(goldBalance("q:"+string(op,"id")).compareTo(expected)!=0)issues.add(LocalText.tr("Порушено резервування видачі злитків: ")+string(op,"id"));
        }
        if(total.compareTo(net)!=0)issues.add(LocalText.tr("Золотий облік не відповідає внесеним і виданим злиткам."));return issues;
    }
}
