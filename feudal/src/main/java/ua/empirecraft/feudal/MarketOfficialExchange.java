package ua.empirecraft.feudal;

import java.math.*;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Coin-to-coin exchange from a separately funded municipal pool. Never touches gold or supply. */
public final class MarketOfficialExchange {
    final CityMarketService m;
    MarketOfficialExchange(CityMarketService m)throws SQLException {
        this.m=m;
        m.db.execute("CREATE TABLE IF NOT EXISTS cm_official(market VARCHAR(36) PRIMARY KEY,enabled BOOLEAN DEFAULT FALSE)");
    }
    static String fund(String market){return "fx:"+market;}
    public String configure(Actor a,String req,String market,Place place,boolean enabled){return m.once(a,req,"official-config|"+market+enabled,()->{
        m.manage(a,market);m.access(market,Department.FX,place);
        m.db.execute("MERGE INTO cm_official KEY(market) VALUES(?,?)",market,enabled);
        m.event(a,market,"OFFICIAL_CONFIG",Boolean.toString(enabled));return market;
    });}
    public String capital(Actor a,String req,String market,Place place,String code,long amount,boolean deposit){return m.once(a,req,"official-capital|"+market+code+amount+deposit,()->{
        m.manage(a,market);String town=string(m.market(market),"town");m.s.controlTreasury(a,m.s.estate(town));
        require(amount>0 && amount<=CurrencyService.MAX_MONEY,"Потрібна додатна сума.");m.s.currencies.goldCurrency(code);
        if(deposit)m.access(market,Department.FX,place);
        m.move(deposit?"e:"+town:fund(market),deposit?fund(market):"e:"+town,code,amount);
        m.event(a,market,"OFFICIAL_FUND",code+" "+amount+" deposit="+deposit);return market;
    });}
    String eligibility(String market,String from,String to)throws SQLException {
        require(m.s.market!=null,"Політична й валютна системи ще недоступні.");
        String town=string(m.market(market),"town");var e=m.s.estate(town);
        String stateId=e.nation()==null || e.nation().isBlank()?town:e.nation();
        var state=m.s.market.politics.state(stateId);
        if(state.government()==Government.REPUBLIC && state.stage()==Stage.TRADE_REPUBLIC)
            return "republic|"+state+"|"+town;
        for(String code:List.of(from,to)){
            String issuer=string(m.s.currency(code),"issuer");var political=m.s.market.politics.state(issuer);
            String capital=political.capital();
            if(capital==null || capital.isBlank())capital=m.s.kingdomCapitals.get(issuer);
            if(town.equals(issuer) || town.equals(capital))return "capital|"+issuer+"|"+political+"|"+capital;
        }
        throw new IllegalArgumentException("Офіційний обмін: власна валюта столиці або допущені пари торговельної республіки максимального рівня.");
    }
    public record Quote(String market,String from,String to,long input,long output,long fee,int bps,long tariff,String stamp) {
        public Map<String,Object> view(){return Map.of("from",from,"to",to,"input",format(input),"output",format(output),"fee",format(fee),"bps",bps,"tariff",tariff,"stamp",stamp);}
    }
    public Quote quote(String market,Place p,String from,String to,long input)throws SQLException {
        m.access(market,Department.FX,p);require(!from.equals(to)&&input>0&&input<=CurrencyService.MAX_MONEY,"Оберіть дві різні валюти й додатну суму.");
        var enabled=m.db.one("SELECT enabled FROM cm_official WHERE market=?",market);
        require(enabled!=null && bool(enabled,"enabled"),"Обмінний пункт не увімкнено керівником ринку.");
        m.fxPermit(market,from);m.fxPermit(market,to);String status=eligibility(market,from,to);
        var ca=m.s.currencies.goldCurrency(from);var cb=m.s.currencies.goldCurrency(to);
        long sa=number(ca,"supply"),sb=number(cb,"supply");var ra=m.s.currencies.reserve(from);var rb=m.s.currencies.reserve(to);
        require(sa>0&&sb>0&&ra.signum()>0&&rb.signum()>0,"За нульового резерву або обігу офіційний обмін недоступний.");
        long output=BigDecimal.valueOf(input).multiply(ra).multiply(BigDecimal.valueOf(sb))
            .divide(rb.multiply(BigDecimal.valueOf(sa)),0,RoundingMode.DOWN).longValueExact();
        require(output>0 && output<=CurrencyService.MAX_MONEY,"Обсяг виходить за точність або ліміт валюти.");
        require(m.s.balance(fund(market),to)>=output,"Офіційний обмін тимчасово недоступний: бракує "+to);
        var tariff=m.tariff(market);int bps=(int)number(tariff,"buyer");long fee=fee(input,bps);Math.addExact(input,fee);
        String stamp=hash(market+"|"+from+"|"+to+"|"+input+"|"+ra+"|"+sa+"|"+rb+"|"+sb+"|"+tariff+"|"+status);
        return new Quote(market,from,to,input,output,fee,bps,number(tariff,"version"),stamp);
    }
    public String exchange(Actor a,String req,String market,Place place,String from,String to,long input,String expected){return m.once(a,req,"official|"+market+from+to+input+expected,()->{
        Quote q=quote(market,place,from,to,input);require(q.stamp.equals(expected),"Оцінка, статус або тариф змінилися. Перегляньте новий розрахунок.");
        require(m.s.balance(wallet(a),from)>=Math.addExact(input,q.fee),"Недостатньо коштів разом зі збором.");
        m.move(wallet(a),fund(market),from,input);
        if(q.fee>0)m.move(wallet(a),"e:"+string(m.market(market),"town"),from,q.fee);
        m.moneyAsset(market,Department.FX,wallet(a),to,q.output,fund(market),req,"OFFICIAL_EXCHANGE");
        m.event(a,market,"OFFICIAL_EXCHANGE",from+" "+input+" -> "+to+" "+q.output+" fee="+q.fee+" stamp="+q.stamp);return req;
    });}
}
