package ua.empirecraft.feudal;

import java.util.*;
import static ua.empirecraft.feudal.Database.*;

/** Political rank, government, administration and international membership are independent axes. */
public final class PoliticalModel {
    private PoliticalModel() {}
    public enum Stage {
        SETTLEMENT(0,"Поселення","Голова"), BARONY(1,"Баронство","Барон"), COUNTY(2,"Графство","Граф"),
        DUCHY(3,"Герцогство","Герцог"), KINGDOM(4,"Королівство","Король"), EMPIRE(5,"Імперія","Імператор"),
        TRIBE(1,"Плем’я","Вождь"), PRINCIPALITY(2,"Князівство","Князь"), HORDE(2,"Орда","Верховний вождь"),
        KHANATE(3,"Ханство","Хан"), KHAGANATE(5,"Каганат","Каган"), CENTRAL_STATE(2,"Централізована держава","Правитель"),
        CENTRAL_KINGDOM(4,"Централізоване королівство","Король"), CENTRAL_EMPIRE(5,"Централізована імперія","Імператор"),
        FREE_CITY(1,"Вольне місто","Голова ради"), TRADE_REPUBLIC(3,"Торговельна республіка","Президент");
        public final int rank; public final String label,title;
        Stage(int rank,String label,String title){this.rank=rank;this.label=label;this.title=title;}
    }
    public enum Government { MONARCHY, TRIBAL_COUNCIL, REPUBLIC, ABSOLUTE_MONARCHY, SULTANATE, MILITARY_ORDER }
    public enum Territory { FEUDAL, DIRECT, AUTONOMOUS, FEDERAL }
    public enum UnionType { PERSONAL, REAL, MERGE, CONFEDERATION, FEDERATION, TRADE_LEAGUE }
    public record State(String id,Stage stage,Government government,Territory territory,String capital,String charter,
                        String mergedInto,long revision,long termEnd) {
        static State from(Map<String,Object> r){return new State(string(r,"estate"),Stage.valueOf(string(r,"stage")),
            Government.valueOf(string(r,"government")),Territory.valueOf(string(r,"territory")),string(r,"capital"),
            string(r,"charter"),string(r,"merged_into"),number(r,"revision"),number(r,"term_end"));}
    }
    public record Check(String name,boolean met,String detail) {}
    public record Progress(Stage target,List<Check> checks,long readySince,long holdMillis) {
        public boolean met(){return checks.stream().allMatch(Check::met);}
        public boolean ready(long now){return met() && readySince>0 && now-readySince>=holdMillis;}
    }
    public static <E extends Enum<E>> E parse(Class<E> type,String value){
        try{return Enum.valueOf(type,value.toUpperCase(Locale.ROOT).replace('-','_'));}
        catch(RuntimeException e){throw new IllegalArgumentException("Значення: "+Arrays.toString(type.getEnumConstants()));}
    }
}
