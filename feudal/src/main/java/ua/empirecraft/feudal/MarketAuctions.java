package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** One indivisible deposited lot and one fully reserved bid; serialized settlement survives restarts. */
public final class MarketAuctions {
    final CityMarketService m;
    MarketAuctions(CityMarketService m)throws SQLException {this.m=m;m.db.execute("CREATE TABLE IF NOT EXISTS cm_auctions(id VARCHAR(36) PRIMARY KEY,market VARCHAR(36),seller VARCHAR(100),spec VARCHAR(64),quantity BIGINT,code VARCHAR(8),start_price BIGINT,step BIGINT,buyout BIGINT,created BIGINT,ends BIGINT,maximum_end BIGINT,state VARCHAR(20),seller_bps INT,tariff BIGINT,bidder VARCHAR(100) DEFAULT '',bid BIGINT DEFAULT 0,buyer_bps INT DEFAULT 0)");m.db.execute("ALTER TABLE cm_auctions ADD COLUMN IF NOT EXISTS bid_reservation VARCHAR(36) DEFAULT ''");}
    public Map<String,Object> auction(String id)throws SQLException {var r=m.db.one("SELECT * FROM cm_auctions WHERE id=?",id);require(r!=null,LocalText.tr("Аукціон не знайдено."));return r;}
    void own(Actor a,String owner)throws SQLException {require(owner.equals(wallet(a))||owner.startsWith("e:")&&m.s.controlsTreasury(a,m.s.estate(owner.substring(2))),LocalText.tr("Це не ваш бюджет."));}
    public String create(Actor a,String req,String market,Place p,String spec,long quantity,String code,long start,long step,long buyout,int hours,CommerceService.InventoryChange change){return m.once(a,req,"auction-create|"+market+spec+quantity+code+start+step+buyout+hours,()->{
        require(change!=null&&change.person().equals(a.id()),LocalText.tr("Потрібне фізичне внесення точного лота."));
        String key=prepare(a,wallet(a),market,p,spec,quantity,code,start,step,buyout,hours,"PREPARED");
        String op=m.commerce.intent(a,"CM_IN",key,quantity,List.of(change));m.db.execute("INSERT INTO cm_transfers VALUES(?,'AUCTION',?,?)",op,key,quantity);m.event(a,market,"AUCTION_PREPARE",key);return op;
    });}
    public String createAsset(Actor a,String req,String asset,Place p,long quantity,String code,long start,long step,long buyout,int hours){return m.once(a,req,"auction-asset|"+asset+quantity+code+start+step+buyout+hours,()->{
        var r=m.ownAsset(a,asset);String owner=string(r,"owner"),market=string(r,"market");
        require(owner.startsWith("e:")&&string(r,"department").equals("GOODS")&&string(r,"code").isEmpty()&&quantity>0&&quantity<=number(r,"quantity"),LocalText.tr("Потрібен вільний предметний актив міста у Товарному відділі."));
        String key=prepare(a,owner,market,p,string(r,"spec"),quantity,code,start,step,buyout,hours,"OPEN");
        m.db.execute("UPDATE cm_assets SET quantity=quantity-? WHERE id=?",quantity,asset);m.event(a,market,"AUCTION_CITY_PUBLISH",key+" owner="+owner);return key;
    });}
    String prepare(Actor a,String owner,String market,Place p,String spec,long quantity,String code,long start,long step,long buyout,int hours,String state)throws SQLException {
        m.access(market,Department.GOODS,p);m.payment(market,code);own(a,owner);var item=m.spec(spec);
        require(m.db.query("SELECT id FROM cm_assets WHERE owner=? AND market=? AND quantity>0",owner,market).size()<m.maxDeposits,LocalText.tr("Ліміт нових депозитів; спершу отримайте активи."));
        require(quantity>0&&quantity<=1000000&&(!bool(item,"unique_lot")||quantity==1),LocalText.tr("Неправильна кількість лота."));
        require(start>0&&step>0&&start<=CurrencyService.MAX_MONEY&&step<=CurrencyService.MAX_MONEY&&(buyout==0||buyout>=start&&buyout<=CurrencyService.MAX_MONEY)&&Set.of(1,24,72).contains(hours),LocalText.tr("Перевірте стартову ціну, крок, викуп і строк 1/24/72 години."));
        require(m.db.query("SELECT id FROM cm_auctions WHERE market=? AND seller=? AND state IN('OPEN','PREPARED')",market,owner).size()<5,LocalText.tr("До 5 активних аукціонів на ринку."));
        String key=id();long ends=m.s.now()+hours*3600000L;var rate=sellerRate(a,owner,market,spec,start,buyout,ends+1800000,code);
        require(rate.ends()>=ends+1800000,LocalText.tr("Пільговий аукціон із можливим продовженням має завершитися в межах пільги."));
        m.db.execute("INSERT INTO cm_auctions(id,market,seller,spec,quantity,code,start_price,step,buyout,created,ends,maximum_end,state,seller_bps,tariff) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",key,market,owner,spec,quantity,code,start,step,buyout,m.s.now(),ends,ends+1800000,state,rate.bps(),number(m.tariff(market),"version"));
        m.treaties.reserve(key,rate.treaty(),buyout>0?buyout:start,code);return key;
    }
    Privilege sellerRate(Actor a,String owner,String market,String spec,long start,long buyout,long ends,String code)throws SQLException {
        Actor beneficiary=owner.startsWith("e:")?new Actor(owner,false):a;
        var rate=m.privilege(beneficiary,market,Department.GOODS,spec,"seller",buyout==0?start:buyout,ends,code);
        return rate.treaty()!=null&&buyout==0?m.privilege(new Actor("uncapped-auction",false),market,Department.GOODS,spec,"seller",start,ends,code):rate;
    }
    void finish(String id,boolean applied)throws SQLException {var r=auction(id);require("PREPARED".equals(string(r,"state")),LocalText.tr("Передачу вже завершено."));m.db.execute("UPDATE cm_auctions SET state=? WHERE id=?",applied?"OPEN":"FAILED",id);if(!applied)m.treaties.settle(id,0,0);if(applied&&(number(r,"ends")<=m.s.now()||Set.of("CLOSED","ARCHIVED").contains(string(m.market(string(r,"market")),"state"))))settle(id);}
    Privilege rate(Actor a,String owner,Map<String,Object> r,long amount)throws SQLException {
        Actor beneficiary=owner.startsWith("e:")?new Actor(owner,false):a;
        return m.privilege(beneficiary,string(r,"market"),Department.GOODS,string(r,"spec"),"buyer",amount,number(r,"maximum_end"),string(r,"code"),(int)number(m.tariff(string(r,"market")),"buyer"),string(r,"bid_reservation"));
    }
    public String stamp(Actor a,String id,String owner,long amount)throws SQLException {
        var r=auction(id);return hash(r+"|"+owner+"|"+amount+"|"+m.tariff(string(r,"market"))+"|"+rate(a,owner,r,amount)+"|"+m.s.membershipStamp(a.id()));
    }
    public String bid(Actor a,String req,String id,Place p,String owner,long amount,boolean buyout,String expected){return m.once(a,req,"auction-bid|"+id+owner+amount+buyout+expected,()->{
        var r=auction(id);require(stamp(a,id,owner,amount).equals(expected),LocalText.tr("Аукціон змінився; перегляньте нові умови."));
        String market=string(r,"market"),code=string(r,"code");m.access(market,Department.GOODS,p);m.payment(market,code);own(a,owner);
        require("OPEN".equals(string(r,"state"))&&number(r,"ends")>m.s.now(),LocalText.tr("Аукціон завершено."));require(!owner.equals(string(r,"seller")),LocalText.tr("Власник коштів не може купувати власний лот."));
        long current=number(r,"bid");
        if(buyout){require(number(r,"buyout")>0&&amount==number(r,"buyout"),LocalText.tr("Підтвердьте точну оголошену ціну викупу."));}
        else {require(amount>0&&amount<=CurrencyService.MAX_MONEY&&amount>=(current==0?number(r,"start_price"):Math.addExact(current,number(r,"step"))),LocalText.tr("Ставка нижча за мінімальну наступну."));require(number(r,"buyout")==0||amount<number(r,"buyout"),LocalText.tr("Ця сума досягає викупу; явно підтвердьте викуп за оголошеною ціною."));}
        var rate=rate(a,owner,r,amount);
        if(!string(r,"bid_reservation").isEmpty())m.treaties.settle(string(r,"bid_reservation"),0,0);
        String reservation=id();m.treaties.reserve(reservation,rate.treaty(),amount,code);
        long reserve=Math.addExact(amount,fee(amount,rate.bps()));String old=string(r,"bidder");
        if(!old.isEmpty()&&!old.equals(owner))m.moneyAsset(market,Department.GOODS,old,code,m.s.balance(account(id),code),account(id),id,"OUTBID_REFUND");
        long held=old.equals(owner)?m.s.balance(account(id),code):0;
        if(reserve>held)m.move(owner,account(id),code,reserve-held);else if(reserve<held)m.moneyAsset(market,Department.GOODS,owner,code,held-reserve,account(id),id,"BID_ADJUSTMENT");
        long ends=Math.min(number(r,"maximum_end"),Math.max(number(r,"ends"),m.s.now()+120000));
        m.db.execute("UPDATE cm_auctions SET bidder=?,bid=?,buyer_bps=?,ends=?,bid_reservation=? WHERE id=?",owner,amount,rate.bps(),ends,reservation,id);m.event(a,market,buyout?"AUCTION_BUYOUT":"AUCTION_BID",id+" "+owner+" amount="+amount+" bps="+rate.bps());
        if(buyout)settle(id);return id;
    });}
    void settle(String id)throws SQLException {
        var r=auction(id);if(!"OPEN".equals(string(r,"state")))return;
        String market=string(r,"market"),code=string(r,"code"),bidder=string(r,"bidder"),seller=string(r,"seller");long bid=number(r,"bid");
        if(bidder.isEmpty())m.asset(market,Department.GOODS,seller,string(r,"spec"),"",number(r,"quantity"),id,"AUCTION_RETURN");
        else {long sf=fee(bid,(int)number(r,"seller_bps")),bf=fee(bid,(int)number(r,"buyer_bps"));
            m.moneyAsset(market,Department.GOODS,seller,code,bid-sf,account(id),id,"AUCTION_PROCEEDS");
            if(sf+bf>0)m.move(account(id),"e:"+string(m.market(market),"town"),code,sf+bf);
            m.asset(market,Department.GOODS,bidder,string(r,"spec"),"",number(r,"quantity"),id,"AUCTION_PURCHASE");
            require(m.s.balance(account(id),code)==0,LocalText.tr("Пошкоджений аукціонний резерв."));
        }
        m.treaties.settle(id,0,bidder.isEmpty()?0:bid);if(!string(r,"bid_reservation").isEmpty())m.treaties.settle(string(r,"bid_reservation"),0,bid);
        m.db.execute("UPDATE cm_auctions SET state='SETTLED' WHERE id=?",id);m.event(new Actor("auction-settlement",true),market,"AUCTION_SETTLED",id+" winner="+bidder);
    }
    public String cancel(Actor a,String req,String id){return m.once(a,req,"auction-cancel|"+id,()->{var r=auction(id);own(a,string(r,"seller"));require("OPEN".equals(string(r,"state"))&&string(r,"bidder").isEmpty(),LocalText.tr("Скасування лише до першої ставки."));settle(id);return id;});}
    void tick()throws SQLException {for(var r:m.db.query("SELECT a.id FROM cm_auctions a JOIN cm_markets m ON m.id=a.market WHERE a.state='OPEN' AND (a.ends<=? OR m.state IN('CLOSED','ARCHIVED') AND a.bidder='')",m.s.now()))settle(string(r,"id"));}
}
