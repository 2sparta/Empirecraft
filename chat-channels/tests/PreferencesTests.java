package ua.empirecraft.chat;

import java.nio.file.*;
import java.util.*;

public final class PreferencesTests {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private record Message(Channel channel,int tick,String text){}
    public static void main(String[] args)throws Exception {
        for(boolean merged:List.of(false,true)){
            var tabs=ChannelLayout.tabs(merged);
            check(tabs.size()==(merged?4:5),"Tab count");
            check(tabs.contains(Channel.SYSTEM)!=merged,"Separate system tab visibility");
            for(Channel incoming:Channel.values()){
                check(tabs.stream().filter(tab->ChannelLayout.matches(tab,incoming,merged)).count()==1,"Every message has exactly one destination: "+incoming);
                if(incoming==Channel.LOCAL||incoming==Channel.TOWN||incoming==Channel.KINGDOM)
                    check(!ChannelLayout.matches(Channel.GLOBAL,incoming,merged),"Private and local messages stay outside global");
            }
            check(ChannelLayout.selection(Channel.TOWN,merged)==Channel.TOWN,"Keep selected town tab");
            check(ChannelLayout.selection(Channel.SYSTEM,merged)==(merged?Channel.GLOBAL:Channel.SYSTEM),"Retarget removed system tab");
        }
        var early=new Message(Channel.GLOBAL,10,"first");var system=new Message(Channel.SYSTEM,15,"service");var late=new Message(Channel.GLOBAL,20,"last");
        var merged=ChannelLayout.history(List.of(early,late),List.of(system),Message::tick,5000);
        check(merged.equals(List.of(early,system,late)),"Merge histories chronologically");
        check(ChannelLayout.history(merged,List.of(system),Message::tick,5000).equals(merged),"A reused history object is not duplicated");
        var sameText=new Message(Channel.SYSTEM,15,"service");
        check(ChannelLayout.history(List.of(system),List.of(sameText),Message::tick,5000).size()==2,"Identical text from different messages is retained");
        check(ChannelLayout.history(List.of(early,late),List.of(system),Message::tick,2).equals(List.of(system,late)),"Keep the latest messages at the native history cap");
        check(ChannelLayout.history(List.of(early,late),List.of(system),Message::tick,0).size()==3,"Unlimited migration permits independent split-tab caps");
        var folder=Files.createTempDirectory("chatplus-preferences-");var path=folder.resolve("chatplus/empirecraft-channels.json");
        try {
            var store=new ChannelPreferencesStore(path);store.load();check(!store.merged(),"Preserve separate tabs by default");
            store.save(true);var reloaded=new ChannelPreferencesStore(path);reloaded.load();check(reloaded.merged(),"Merged preference survives a fresh instance");
            reloaded.save(false);store.load();check(!store.merged(),"Split preference survives a fresh load");
            Files.writeString(path,"{\"mergeGlobalAndSystem\":\"true\"}");
            try{store.load();throw new AssertionError("Accepted a string as a boolean");}catch(java.io.IOException expected){checks++;}
            var bad=new ChannelPreferencesStore(path.resolve("not-a-directory/preferences.json"));
            try{bad.save(true);throw new AssertionError("Saved through a file parent");}catch(java.io.IOException expected){check(!bad.merged(),"Failed write does not change active preferences");}
            try(var files=Files.walk(folder)){check(files.noneMatch(p->p.toString().endsWith(".tmp")),"Atomic preference writes leave no temporary files");}
        }finally{try(var files=Files.walk(folder)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
        System.out.println("CHAT_PREFERENCES_PASS assertions="+checks+"; routing, selections, history order/caps/deduplication, reload and atomic writes");
    }
}
