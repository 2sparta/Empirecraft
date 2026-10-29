package ua.empirecraft.townymap.client;

import java.util.*;

/** Background refreshes never lock controls; a user action supersedes their response revision. */
final class CurrencyViewState {
    private boolean waiting,background;
    private int sentTick;
    private long revision;
    boolean begin(boolean quiet,int tick){
        if(waiting && (!background || quiet))return false;
        waiting=true;background=quiet;sentTick=tick;revision++;return true;
    }
    boolean waiting(){return waiting;}
    boolean background(){return background;}
    boolean busy(){return waiting&&!background;}
    long revision(){return revision;}
    void complete(){waiting=false;background=false;}
    boolean timedOut(int tick){return waiting&&tick-sentTick>=200;}
    static List<String> choices(List<String> codes,String other){return codes.stream().filter(c->!c.isEmpty()&&!c.equals("@GOLD")&&!c.equals(other)).distinct().toList();}
    static List<String> tabs(boolean canPrint){return canPrint?List.of("market","print","policy","log","redeem","reserve"):List.of("market","policy","log","redeem","reserve");}
}

