package ua.empirecraft.chat;

import java.util.*;
import java.util.function.ToIntFunction;

/** Client presentation never changes the server's membership or delivery rules. */
public final class ChannelLayout {
    private ChannelLayout() {}
    public static List<Channel> tabs(boolean merged) {
        return merged?List.of(Channel.GLOBAL,Channel.LOCAL,Channel.TOWN,Channel.KINGDOM):List.of(Channel.values());
    }
    public static boolean matches(Channel tab,Channel incoming,boolean merged) {
        return tab==incoming||merged&&tab==Channel.GLOBAL&&incoming==Channel.SYSTEM;
    }
    public static Channel selection(Channel selected,boolean merged) {
        return merged&&selected==Channel.SYSTEM?Channel.GLOBAL:selected;
    }
    public static <T> ArrayList<T> history(List<T> global,List<T> system,ToIntFunction<T> time,int limit) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());var combined=new ArrayList<T>();
        for(var source:List.of(global,system))for(T message:source)if(seen.add(message))combined.add(message);
        combined.sort(Comparator.comparingInt(time));
        if(limit>0&&combined.size()>limit)return new ArrayList<>(combined.subList(combined.size()-limit,combined.size()));
        return combined;
    }
}
