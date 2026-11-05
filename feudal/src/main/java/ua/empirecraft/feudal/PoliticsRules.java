package ua.empirecraft.feudal;

import java.util.*;
import java.util.function.ToLongFunction;
import static ua.empirecraft.feudal.Model.require;
import static ua.empirecraft.feudal.PoliticalModel.*;

/** Every numeric gameplay threshold is read through one validated configuration boundary. */
public final class PoliticsRules {
    private final ToLongFunction<String> values;
    public PoliticsRules(ToLongFunction<String> values){this.values=values;}
    public PoliticsRules(){this(k->-1);}
    public long value(String key,long fallback){long n=values.applyAsLong(key);return n<0?fallback:n;}
    public int count(String key,int fallback){long n=value(key,fallback);require(n<=1_000_000,LocalText.tr("Завелике налаштування: ")+key);return (int)n;}
    public long days(String key,int fallback){return Math.multiplyExact(value(key,fallback),FeudalService.DAY);}
    public long hold(){return days("hold-days",7);}
    public long proposal(){return Math.multiplyExact(value("proposal-hours",48),3_600_000L);}
    public long election(){return Math.multiplyExact(value("election-hours",48),3_600_000L);}
    public int population(Stage target){return count("population."+target.name().toLowerCase(Locale.ROOT),switch(target){
        case SETTLEMENT -> 0; case TRIBE -> 3; case BARONY -> 5; case COUNTY,PRINCIPALITY,HORDE,CENTRAL_STATE,FREE_CITY -> 10;
        case DUCHY,KHANATE,TRADE_REPUBLIC -> 20;case KHAGANATE -> 40;case EMPIRE,CENTRAL_EMPIRE -> 50;default -> 20;});}
    public int threshold(String key,int fallback){return count("requirements."+key,fallback);}
    public boolean transition(Stage from,Stage to){return switch(from){
        case SETTLEMENT -> Set.of(Stage.BARONY,Stage.TRIBE,Stage.CENTRAL_STATE,Stage.FREE_CITY).contains(to);
        case BARONY -> to==Stage.COUNTY;
        case COUNTY -> to==Stage.DUCHY || to==Stage.KINGDOM;
        case DUCHY,PRINCIPALITY -> to==Stage.KINGDOM;
        case KINGDOM -> to==Stage.EMPIRE || to==Stage.CENTRAL_KINGDOM;
        case TRIBE -> to==Stage.PRINCIPALITY || to==Stage.HORDE;
        case HORDE -> to==Stage.KHANATE;
        case KHANATE -> to==Stage.KHAGANATE;
        case CENTRAL_STATE -> to==Stage.CENTRAL_KINGDOM;
        case CENTRAL_KINGDOM -> to==Stage.CENTRAL_EMPIRE;
        case FREE_CITY -> to==Stage.TRADE_REPUBLIC;
        default -> false;};}
}
