package ua.empirecraft.engagement;

import java.util.*;
import static ua.empirecraft.engagement.CombatPolicy.*;

public final class CombatPolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Side ship(Flag flag,Water water,boolean active){return new Side(Kind.SHIP,flag,water,active,true);}
    public static void main(String[] args){
        var player=new Side(Kind.PERSON,Flag.NONE,Water.OWN,false,true);
        var recruit=new Side(Kind.RECRUIT,Flag.NONE,Water.OWN,false,true);
        var protectedHit=decide(player,recruit,false);check(!protectedHit.allow()&&protectedHit.tagAttacker()&&!protectedHit.tagVictim(),"Protected recruits reject damage and do not force the victim into combat");
        check(decide(recruit,new Side(Kind.PERSON,Flag.NONE,Water.OWN,true,true),false).allow(),"The protected defender may retaliate against an aggressor");
        for(Water water:Water.values()){
            for(Flag flag:List.of(Flag.PEACEFUL,Flag.STATE,Flag.PIRATE))check(decide(ship(flag,water,false),ship(Flag.PIRATE,water,false),false).allow(),"Every flag may attack pirates");
            check(decide(ship(Flag.PIRATE,water,false),ship(Flag.STATE,Water.OWN,false),false).allow(),"Pirates may attack a state ensign in protected waters");
        }
        check(!decide(ship(Flag.PIRATE,Water.WILDERNESS,false),ship(Flag.PEACEFUL,Water.OWN,false),false).allow(),"Pirates cannot force a peaceful sheltered victim into combat");
        check(ship(Flag.PEACEFUL,Water.NEUTRAL,false).sheltered(),"Peaceful flags protect neutral claimed waters");
        check(!ship(Flag.STATE,Water.NEUTRAL,false).sheltered(),"State flags do not claim neutral-water immunity");
        check(!decide(ship(Flag.STATE,Water.ALLIED,false),ship(Flag.PEACEFUL,Water.WILDERNESS,false),false).allow(),"State neutrality forbids firing out of sheltered waters");
        check(decide(ship(Flag.STATE,Water.ALLIED,true),ship(Flag.PEACEFUL,Water.WILDERNESS,false),false).allow(),"Existing combat cannot hide behind a state ensign");
        List<Side> sides=new ArrayList<>();for(Kind kind:Kind.values())for(Flag flag:Flag.values())for(Water water:Water.values())for(boolean active:new boolean[]{false,true})for(boolean ready:new boolean[]{false,true})sides.add(new Side(kind,flag,water,active,ready));
        for(Side a:sides)for(Side b:sides){var result=decide(a,b,false);
            check(result.allow()||!result.tagVictim(),"A denied attack never force-tags its victim");
            if(a.asset()&&!a.ready()||b.asset()&&!b.ready())check(!result.allow(),"Unavailable authority fails closed for assets");
            var self=decide(a,b,true);check(self.allow()&&!self.tagAttacker()&&!self.tagVictim(),"Self collisions never start a war");
        }
        check(flagWidth(5,2)==2&&flagWidth(24,7)==4&&flagWidth(52,12)==6&&flagWidth(201,3)==8,"Ensign size follows hull dimensions");
        check(flagWidth(Double.NaN,1)==8&&flagWidth(Double.POSITIVE_INFINITY,1)==8,"Invalid hull sizes never earn a small concealed flag");
        System.out.println("COMBAT_POLICY_PASS checks="+checks);
    }
}
