package ua.empirecraft.engagement;

/** No world access: the same rules govern melee, swept projectiles, explosions and boarding. */
public final class CombatPolicy {
    public enum Flag { PEACEFUL, STATE, PIRATE, NONE }
    public enum Water { OWN, ALLIED, NEUTRAL, ENEMY, WILDERNESS, UNKNOWN }
    public enum Kind { PERSON, RECRUIT, SHIP, ENVIRONMENT }
    public record Side(Kind kind,Flag flag,Water water,boolean active,boolean ready) {
        public Side {if(kind==Kind.SHIP&&flag==Flag.NONE)flag=Flag.PIRATE;}
        public boolean asset(){return kind==Kind.RECRUIT||kind==Kind.SHIP;}
        public boolean sheltered(){
            if(active)return false;
            if(kind==Kind.SHIP&&flag==Flag.PIRATE)return false;
            if(water==Water.UNKNOWN)return asset();
            if(kind==Kind.RECRUIT)return water==Water.OWN||water==Water.ALLIED;
            if(kind!=Kind.SHIP||flag==Flag.PIRATE)return false;
            return water==Water.OWN||water==Water.ALLIED||flag==Flag.PEACEFUL&&water==Water.NEUTRAL;
        }
    }
    public record Decision(boolean allow,boolean tagAttacker,boolean tagVictim,String reason) {}
    private CombatPolicy(){}
    public static Decision decide(Side attacker,Side victim,boolean self){
        if(self)return new Decision(true,false,false,"self");
        if((attacker.asset()&&!attacker.ready())||(victim.asset()&&!victim.ready()))return new Decision(false,false,false,"unavailable");
        if(attacker.kind()==Kind.ENVIRONMENT)return new Decision(!victim.sheltered(),false,false,"environment");
        if(!attacker.asset()&&!victim.asset()&&!attacker.active()&&!victim.active())return new Decision(true,false,false,"ordinary");
        boolean pirate=victim.kind()==Kind.SHIP&&victim.flag()==Flag.PIRATE;
        if(attacker.kind()==Kind.SHIP&&attacker.flag()==Flag.STATE&&attacker.sheltered()&&!pirate)
            return new Decision(false,false,false,"state-neutrality");
        boolean pirateException=victim.kind()==Kind.SHIP&&victim.flag()==Flag.STATE&&attacker.kind()==Kind.SHIP&&attacker.flag()==Flag.PIRATE;
        if(victim.sheltered()&&!pirateException)return new Decision(false,true,false,"protected");
        return new Decision(true,true,true,"combat");
    }
    public static int flagWidth(double length,double beam){
        double size=Math.max(length,beam);if(!Double.isFinite(size)||size<0)return 8;
        return size<12?2:size<32?4:size<64?6:8;
    }
}
