package ua.empirecraft.townymap.client;

import com.google.gson.JsonObject;
import java.util.*;

/** Keep a notice, leaderboard or selected page open while the server refreshes its contents. */
public final class CivicReadState {
    public record Reading(String id,String kind) {}
    public record Rows(int offset,int count) {}
    public static Optional<Reading> reading(JsonObject data){
        if(data.has("error")||!data.has("readId")||!data.has("readKind"))return Optional.empty();
        try{String id=data.get("readId").getAsString(),kind=data.get("readKind").getAsString();
            return !id.isBlank()&&id.length()<=36&&Set.of("NOTICE","EVENT","FISHING","PAGE","REVIEW").contains(kind)?Optional.of(new Reading(id,kind)):Optional.empty();
        }catch(RuntimeException malformed){return Optional.empty();}
    }
    public static boolean leaveForm(String action,JsonObject data){return action.equals("prepare")&&(data.has("confirmation")||reading(data).isPresent());}
    public static Rows rows(int size,int offset,int height){int count=Math.max(1,height/21);return new Rows(Math.max(0,Math.min(offset,Math.max(0,size-count))),count);}
    private CivicReadState(){}
}
