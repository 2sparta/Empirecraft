package ua.empirecraft.feudal;

import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Exact volumes and at most 24 actual-trade candles. Missing intervals remain missing. */
public final class MarketCharts {
    final CityMarketService m;
    MarketCharts(CityMarketService m)throws SQLException {
        this.m=m;m.db.execute("CREATE INDEX IF NOT EXISTS cm_fills_chart ON cm_fills(market,department,base,code,at_time)");
    }
    public List<String> pairs(String market,Place place)throws SQLException {
        m.access(market,Department.FX,place);var codes=m.db.query("SELECT code FROM cm_permits WHERE market=? AND host=TRUE AND issuer=TRUE ORDER BY code",market);
        var pairs=new ArrayList<String>();for(int i=0;i<codes.size();i++)for(int j=i+1;j<codes.size();j++)pairs.add(string(codes.get(i),"code")+"/"+string(codes.get(j),"code"));return pairs;
    }
    public Map<String,Object> chart(String market,Place place,String pair,long hours)throws SQLException {
        require(Set.of(1L,24L,168L,720L).contains(hours),"Період: 1, 24, 168 або 720 годин.");
        m.access(market,Department.FX,place);String[] codes=pair.split("/",-1);require(codes.length==2&&codes[0].compareTo(codes[1])<0,"Потрібна основна пара BASE/QUOTE.");m.fxPermit(market,codes[0]);m.fxPermit(market,codes[1]);
        long now=m.s.now(),start=now-hours*3600000,step=hours*3600000/24,volume=0;var points=new ArrayList<Map<String,Object>>();
        var candles=m.db.query("SELECT MIN(price) AS low,MAX(price) AS high,SUM(quantity) AS quantity,MAX(seq) AS last_seq,LEAST(23,FLOOR((at_time-?)/CAST(? AS DECIMAL(20,0)))) AS bucket FROM cm_fills WHERE market=? AND department='FX' AND base=? AND code=? AND at_time>=? AND at_time<=? GROUP BY bucket ORDER BY bucket",start,step,market,codes[0],codes[1],start,now);
        for(var candle:candles){var last=m.db.one("SELECT price,at_time FROM cm_fills WHERE seq=?",number(candle,"last_seq"));long n=number(candle,"quantity");volume=Math.addExact(volume,n);points.add(Map.of("bucket",Math.min(23,number(candle,"bucket")),"low",number(candle,"low"),"high",number(candle,"high"),"price",number(last,"price"),"quantity",n,"at",number(last,"at_time")));}
        var latest=m.db.one("SELECT price,quantity,at_time FROM cm_fills WHERE market=? AND department='FX' AND base=? AND code=? ORDER BY seq DESC LIMIT 1",market,codes[0],codes[1]);
        var data=new LinkedHashMap<String,Object>();data.put("pair",pair);data.put("base",codes[0]);data.put("code",codes[1]);data.put("hours",hours);data.put("start",start);data.put("now",now);data.put("volume",volume);data.put("latest",latest==null?Map.of():latest);data.put("points",points);return data;
    }
}
