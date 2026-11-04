package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Fully funded supply and staged work; elapsed review time creates a dispute, never a payout. */
public final class MarketContracts {
    final CityMarketService m;final Database db;final FeudalService s;
    MarketContracts(CityMarketService m)throws SQLException {this.m=m;db=m.db;s=m.s;
        db.execute("CREATE TABLE IF NOT EXISTS cm_contracts(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),customer VARCHAR(36),worker VARCHAR(36),kind VARCHAR(10),description VARCHAR(4000),code VARCHAR(8),reward BIGINT,paid BIGINT DEFAULT 0,bps INT,fees BIGINT DEFAULT 0,tariff BIGINT,spec VARCHAR(64),quantity BIGINT,delivered BIGINT DEFAULT 0,partial BOOLEAN,ends BIGINT,state VARCHAR(20))");
        db.execute("ALTER TABLE cm_contracts ADD COLUMN IF NOT EXISTS fee_model VARCHAR(12) DEFAULT 'LEGACY'");
        db.execute("CREATE TABLE IF NOT EXISTS cm_service_rates(op VARCHAR(36) PRIMARY KEY,bps INT)");
        db.execute("CREATE TABLE IF NOT EXISTS cm_contract_fees(contract VARCHAR(36),worker VARCHAR(36),bps INT,volume BIGINT,fees BIGINT,PRIMARY KEY(contract,worker,bps))");
        db.execute("CREATE TABLE IF NOT EXISTS cm_stages(contract VARCHAR(36),stage INT,amount BIGINT,criteria VARCHAR(2000),evidence VARCHAR(4000),state VARCHAR(20),submitted BIGINT,PRIMARY KEY(contract,stage))");
    }
    public Map<String,Object> contract(String id)throws SQLException {var r=db.one("SELECT * FROM cm_contracts WHERE id=?",id);require(r!=null,"Контракту немає.");return r;}
    public String create(Actor a,String req,String market,Place p,String description,String code,long reward,int days,String spec,long quantity,boolean partial,List<Long> stages,List<String> criteria,long tariff){return m.once(a,req,"contract|"+market+description+code+reward+","+days+spec+quantity+partial+stages+criteria+tariff,()->{
        m.access(market,Department.JOBS,p);m.payment(market,code);m.depositLimit(a,market);require(description.length()>0&&description.length()<=4000&&reward>0&&reward<=CurrencyService.MAX_MONEY&&days>=1&&days<=90,"Некоректні умови контракту.");var t=m.tariff(market);require(number(t,"version")==tariff,"Тариф змінився.");
        boolean supply=!spec.isEmpty();if(supply){m.spec(spec);require(quantity>0&&quantity<=1_000_000,"Некоректна кількість.");}else {require(stages.size()>0&&stages.size()<=20&&stages.size()==criteria.size(),"Потрібні 1–20 етапів з критеріями.");long total=0;for(int i=0;i<stages.size();i++){require(stages.get(i)>0 && !criteria.get(i).isBlank()&&criteria.get(i).length()<=2000,"Потрібні додатна оплата й критерії етапу.");total=Math.addExact(total,stages.get(i));}require(total==reward,"Сума етапів має дорівнювати винагороді.");}
        require(db.query("SELECT id FROM cm_contracts WHERE customer=? AND state IN('FUNDED','WORKING','REVIEW','DISPUTE')",a.id()).size()<50,"Ліміт активних контрактів.");String key=id();m.move(wallet(a),account(key),code,reward);
        db.execute("INSERT INTO cm_contracts(id,market,customer,worker,kind,description,code,reward,bps,tariff,spec,quantity,partial,ends,state) VALUES(?,?,?,'',?,?,?,?,?,?,?,?,?,?,'FUNDED')",key,market,a.id(),supply?"SUPPLY":"WORK",description,code,reward,number(t,"jobs"),tariff,spec,quantity,partial,s.now()+days*FeudalService.DAY);
        db.execute("UPDATE cm_contracts SET fee_model='PARTY' WHERE id=?",key);
        if(!supply)for(int i=0;i<stages.size();i++)db.execute("INSERT INTO cm_stages VALUES(?,?,?,?,?,'FUNDED',0)",key,i+1,stages.get(i),criteria.get(i),"");m.event(a,market,"CONTRACT",key+" "+description+" tariff="+tariff);return key;
    });}
    void pending(String id)throws SQLException {require(db.one("SELECT op FROM cm_transfers t JOIN item_ops o ON o.id=t.op WHERE t.related=? AND o.status='PREPARED'",id)==null,"Спершу завершіть передачу предметів контракту.");}
    public String deliver(Actor a,String req,String id,long count,Place p,boolean recovery,CommerceService.InventoryChange change){return m.once(a,req,"deliver|"+id+"|"+count+recovery,()->{
        var r=contract(id);pending(id);String market=string(r,"market");
        if(!recovery)m.access(market,Department.JOBS,p);else {require(!m.open(market,Department.JOBS)&&m.inMarket(market,p)&&db.one("SELECT seq FROM cm_events WHERE market=? AND event='RECOVERY_POINT'",market)!=null,"Потрібен пункт повернення початкового ринку.");}
        require("SUPPLY".equals(string(r,"kind"))&&"FUNDED".equals(string(r,"state"))&&number(r,"ends")>s.now()&&!a.id().equals(string(r,"customer")),"Постачання недоступне.");long left=number(r,"quantity")-number(r,"delivered");require(count>0&&count<=left&&(bool(r,"partial")||count==left),"Неприпустима часткова поставка.");require(change.person().equals(a.id()),"Неправильний інвентар.");String op=m.commerce.intent(a,"CM_IN",id,count,List.of(change));db.execute("INSERT INTO cm_transfers VALUES(?,'SUPPLY',?,?)",op,id,count);
        if("PARTY".equals(string(r,"fee_model"))){long gross=CommerceService.cumulative(number(r,"reward"),number(r,"delivered")+count,number(r,"quantity"))-number(r,"paid");var rate=serviceRate(a,r,gross);db.execute("INSERT INTO cm_service_rates VALUES(?,?)",op,rate.bps());m.treaties.reserve(op,rate.treaty(),gross,string(r,"code"));}return op;
    });}
    void finishSupply(Map<String,Object> t,boolean applied)throws SQLException {
        if(!applied){m.treaties.settle(string(t,"op"),0,0);return;}String id=string(t,"related");var r=contract(id);long n=number(t,"quantity"),delivered=number(r,"delivered")+n;
        var op=db.one("SELECT actor FROM item_ops WHERE id=?",string(t,"op"));long cumulative=CommerceService.cumulative(number(r,"reward"),delivered,number(r,"quantity")),payment=cumulative-number(r,"paid");var rate=db.one("SELECT bps FROM cm_service_rates WHERE op=?",string(t,"op"));pay(r,string(op,"actor"),payment,rate==null?(int)number(r,"bps"):(int)number(rate,"bps"));m.treaties.settle(string(t,"op"),0,payment);
        m.asset(string(r,"market"),Department.JOBS,"p:"+string(r,"customer"),string(r,"spec"),"",n,id,"SUPPLY");db.execute("UPDATE cm_contracts SET delivered=?,state=? WHERE id=?",delivered,delivered==number(r,"quantity")?"COMPLETED":"FUNDED",id);m.event(new Actor(string(op,"actor"),false),string(r,"market"),"SUPPLY",id+" "+n+" payment="+payment);
    }
    Privilege serviceRate(Actor a,Map<String,Object> r,long gross)throws SQLException {
        return m.privilege(a,string(r,"market"),Department.JOBS,string(r,"spec"),"jobs",gross,number(r,"ends"),string(r,"code"),(int)number(r,"bps"),"");
    }
    void pay(Map<String,Object> r,String worker,long amount)throws SQLException {pay(r,worker,amount,(int)number(r,"bps"));}
    void pay(Map<String,Object> r,String worker,long amount,int bps)throws SQLException {
        if(amount==0)return;String market=string(r,"market"),id=string(r,"id"),code=string(r,"code");long f;
        if("PARTY".equals(string(r,"fee_model"))){var group=db.one("SELECT * FROM cm_contract_fees WHERE contract=? AND worker=? AND bps=?",id,worker,bps);long volume=group==null?0:number(group,"volume"),charged=group==null?0:number(group,"fees");long total=Math.addExact(volume,amount),fees=fee(total,bps);f=fees-charged;db.execute("MERGE INTO cm_contract_fees KEY(contract,worker,bps) VALUES(?,?,?,?,?)",id,worker,bps,total,fees);}
        else f=fee(number(r,"paid")+amount,(int)number(r,"bps"))-number(r,"fees");
        require(f>=0&&f<=amount,"Пошкоджений збір контракту.");m.moneyAsset(market,Department.JOBS,"p:"+worker,code,amount-f,account(id),id,"WORK_PAYMENT");if(f>0)m.move(account(id),"e:"+string(m.market(market),"town"),code,f);
        db.execute("UPDATE cm_contracts SET paid=paid+?,fees=fees+? WHERE id=?",amount,f,id);
        if("WORK".equals(string(r,"kind")))m.treaties.settle(id,Math.max(0,number(r,"reward")-number(r,"paid")-amount),amount);
    }
    public String accept(Actor a,String req,String id,Place p){return m.once(a,req,"acceptContract|"+id,()->{
        var r=contract(id);m.access(string(r,"market"),Department.JOBS,p);require("WORK".equals(string(r,"kind"))&&"FUNDED".equals(string(r,"state"))&&number(r,"ends")>s.now()&&!a.id().equals(string(r,"customer")),"Контракт уже прийнятий або недоступний.");int bps=(int)number(r,"bps");if("PARTY".equals(string(r,"fee_model"))){var rate=serviceRate(a,r,number(r,"reward")-number(r,"paid"));bps=rate.bps();m.treaties.reserve(id,rate.treaty(),number(r,"reward")-number(r,"paid"),string(r,"code"));}db.execute("UPDATE cm_contracts SET worker=?,state='WORKING',bps=? WHERE id=?",a.id(),bps,id);m.event(a,string(r,"market"),"CONTRACT_ACCEPT",id);return id;
    });}
    public String submit(Actor a,String req,String id,int stage,String evidence){return m.once(a,req,"submitStage|"+id+stage+evidence,()->{
        var r=contract(id);require(a.id().equals(string(r,"worker"))&&"WORKING".equals(string(r,"state"))&&!evidence.isBlank()&&evidence.length()<=4000,"Подати етап може виконавець роботи.");var st=db.one("SELECT * FROM cm_stages WHERE contract=? AND state='FUNDED' ORDER BY stage LIMIT 1",id);require(st!=null&&number(st,"stage")==stage,"Подавайте етапи послідовно.");db.execute("UPDATE cm_stages SET state='REVIEW',evidence=?,submitted=? WHERE contract=? AND stage=?",evidence,s.now(),id,stage);db.execute("UPDATE cm_contracts SET state='REVIEW' WHERE id=?",id);m.event(a,string(r,"market"),"STAGE_SUBMIT",id+" "+stage+" "+evidence);return id;
    });}
    public String review(Actor a,String req,String id,boolean accept,String reason){return m.once(a,req,"reviewStage|"+id+accept+reason,()->{
        var r=contract(id);require(a.id().equals(string(r,"customer"))&&"REVIEW".equals(string(r,"state")),"Етап приймає замовник до завершення строку огляду.");var st=db.one("SELECT * FROM cm_stages WHERE contract=? AND state='REVIEW'",id);require(s.now()-number(st,"submitted")<2*FeudalService.DAY,"Строк приймання минув; потрібен арбітраж.");
        if(accept){pay(r,string(r,"worker"),number(st,"amount"));db.execute("UPDATE cm_stages SET state='PAID' WHERE contract=? AND stage=?",id,number(st,"stage"));boolean done=db.one("SELECT stage FROM cm_stages WHERE contract=? AND state='FUNDED'",id)==null;db.execute("UPDATE cm_contracts SET state=? WHERE id=?",done?"COMPLETED":"WORKING",id);}
        else {require(!reason.isBlank(),"Укажіть причину спору.");db.execute("UPDATE cm_contracts SET state='DISPUTE' WHERE id=?",id);db.execute("UPDATE cm_stages SET state='DISPUTE' WHERE contract=? AND state='REVIEW'",id);}
        m.event(a,string(r,"market"),accept?"STAGE_ACCEPT":"DISPUTE",id+" "+reason);return id;
    });}
    public String dispute(Actor a,String req,String id,String reason){return m.once(a,req,"dispute|"+id+reason,()->{
        var r=contract(id);require(Set.of(string(r,"customer"),string(r,"worker")).contains(a.id())&&Set.of("WORKING","REVIEW").contains(string(r,"state"))&&!reason.isBlank(),"Спір відкриває сторона чинної роботи.");db.execute("UPDATE cm_contracts SET state='DISPUTE' WHERE id=?",id);m.event(a,string(r,"market"),"DISPUTE",id+" "+reason);return id;
    });}
    public String arbitrate(Actor a,String req,String id,long workerAmount,String reason){return m.once(a,req,"arbitrate|"+id+workerAmount+reason,()->{
        m.server(a);var r=contract(id);require(!a.id().equals(string(r,"customer"))&&!a.id().equals(string(r,"worker")),"Арбітр має бути незалежним від сторін.");require("DISPUTE".equals(string(r,"state"))&&!reason.isBlank(),"Потрібні спір і причина рішення.");long remaining=s.balance(account(id),string(r,"code"));require(workerAmount>=0&&workerAmount<=remaining,"Рішення може розподілити лише наявний резерв.");if(workerAmount>0)pay(r,string(r,"worker"),workerAmount);refund(r);db.execute("UPDATE cm_contracts SET state='ARBITRATED' WHERE id=?",id);m.event(a,string(r,"market"),"ARBITRATION",id+" workerGross="+workerAmount+" "+reason);return id;
    });}
    void refund(Map<String,Object> r)throws SQLException {String key=string(r,"id"),code=string(r,"code");long n=s.balance(account(key),code);m.treaties.settle(key,0,0);m.moneyAsset(string(r,"market"),Department.JOBS,"p:"+string(r,"customer"),code,n,account(key),key,"CONTRACT_REFUND");}
    public String cancel(Actor a,String req,String id){return m.once(a,req,"cancelContract|"+id,()->{var r=contract(id);pending(id);require(a.id().equals(string(r,"customer"))&&"FUNDED".equals(string(r,"state")),"Одностороннє скасування після прийняття недоступне.");refund(r);db.execute("UPDATE cm_contracts SET state='CANCELLED' WHERE id=?",id);m.event(a,string(r,"market"),"CONTRACT_CANCEL",id);return id;});}
    void tick()throws SQLException {
        for(var r:db.query("SELECT * FROM cm_contracts WHERE state='FUNDED' AND ends<=?",s.now())){if(db.one("SELECT op FROM cm_transfers t JOIN item_ops o ON o.id=t.op WHERE related=? AND o.status='PREPARED'",string(r,"id"))!=null)continue;refund(r);db.execute("UPDATE cm_contracts SET state='EXPIRED' WHERE id=?",string(r,"id"));}
        for(var r:db.query("SELECT * FROM cm_contracts WHERE (state='REVIEW' AND id IN(SELECT contract FROM cm_stages WHERE state='REVIEW' AND submitted<=?)) OR (state='WORKING' AND ends<=?)",s.now()-2*FeudalService.DAY,s.now())){db.execute("UPDATE cm_contracts SET state='DISPUTE' WHERE id=?",string(r,"id"));m.event(new Actor("deadlines",true),string(r,"market"),"DISPUTE",string(r,"id")+" строк минув; резерв збережено");}
    }
    boolean obligations(String market)throws SQLException {return db.one("SELECT id FROM cm_contracts WHERE market=? AND state IN('FUNDED','WORKING','REVIEW','DISPUTE')",market)!=null;}
}
