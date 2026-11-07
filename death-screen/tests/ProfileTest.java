import com.epicdeathscreen.core.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class ProfileTest {
    static int checks;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        for(String line:Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8)) {
            String[] p=line.split("\t",-1);String kind=p[0],id=p[1],cue=p[2],language=p[3],expected=p[4];
            var match=DeathProfiles.match(kind.equals("damage")?id:"minecraft:mob_attack",kind.equals("entity")?id:"", "",kind.equals("block")?id:"");
            check(match!=null&&match.cue().equals("empirecraft/"+cue),"cue: "+id);
            try(var stream=ProfileTest.class.getResourceAsStream("/assets/epicdeathscreen/phrases/"+language+".txt")) {
                var book=PhraseBook.read(new InputStreamReader(stream,StandardCharsets.UTF_8));
                var words=book.candidates("generic",match.damage(),match.entity());
                check(words.contains(expected),"exact cause phrase: "+id+" "+language);
                check(words.size()>=2,"phrase variation: "+id);
            }
        }
        check(DeathProfiles.match("minecraft:fall","minecraft:arrow","minecraft:player","")==null,"ordinary vanilla deaths keep original themes");
        check(DeathProfiles.match(null,null,null,null)==null,"missing source stays generic");
        check(DeathProfiles.match("othermod:bullet","othermod:recruit","minecraft:zombie","")==null,"no substring guesses");
        check(DeathProfiles.match("musketmod:bullet","siegemachines:giant_stone","recruits:horseman","").cue().equals("empirecraft/musket"),"damage outranks projectile and attacker");
        check(DeathProfiles.match("minecraft:thrown","siegemachines:giant_arrow","recruits:recruit","").entity().equals("siegemachines:giant_arrow"),"projectile outranks shooter");
        check(DeathProfiles.match("minecraft:arrow","minecraft:arrow","recruits:bowman","").entity().equals("recruits:bowman"),"recruit-owned vanilla arrow");
        check(DeathProfiles.match("minecraft:falling_block","minecraft:falling_block","","minecraft:sand")==null,"falling sand is not a coconut");
        var base=PhraseBook.read(new StringReader("[generic]\nBase\n[damage:musketmod:bullet]\nMusket\n"));
        var custom=PhraseBook.read(new StringReader("[damage:musketmod:bullet]\nMy phrase\n"));
        check(PhraseBook.choose(base,custom,true,"generic","musketmod:bullet","","",new Random(1)).equals("My phrase"),"custom replace remains supported");
        try {PhraseBook.read(new StringReader("[bad section]\nBad\n"));throw new AssertionError("invalid phrases accepted");}catch(IOException expected){checks++;}
        var clock=new SceneClock(120);clock.skip();check(clock.ready(),"skip finishes scene");
        check(!clock.requestAutoRespawn(true,true,true),"hardcore never auto-respawns");
        check(clock.requestAutoRespawn(true,false,true)&&!clock.requestAutoRespawn(true,false,true),"single respawn request");
        System.out.println("DEATH_PROFILE_TESTS_PASS checks="+checks);
    }
}
