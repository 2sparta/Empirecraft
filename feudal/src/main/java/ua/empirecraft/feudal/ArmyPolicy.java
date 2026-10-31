package ua.empirecraft.feudal;

import java.util.*;

/** Immutable political view. Safe for Recruits' asynchronous target searches: no Bukkit or SQL. */
public final class ArmyPolicy {
    private ArmyPolicy(){}
    public record Land(String id,String nation,String root,String ruler){}
    public record Soldier(String owner,String estate,String home,boolean alive){}
    public record Result(boolean allow,String reason,String attacker,String target){}
    public record View(long updated,Map<String,Land> lands,Map<String,String> citizens,Map<String,Soldier> units,
                       Set<String> allies,Set<String> pacts,Set<String> wars,Set<String> officers){
        public View{lands=Map.copyOf(lands);citizens=Map.copyOf(citizens);units=Map.copyOf(units);allies=Set.copyOf(allies);pacts=Set.copyOf(pacts);wars=Set.copyOf(wars);officers=Set.copyOf(officers);}
        public String estate(String entity,String owner,boolean recruit){
            if(recruit){var u=units.get(entity);if(u!=null&&u.estate()!=null)return u.estate();return citizens.get(owner==null?"":owner);}return citizens.get(entity==null?"":entity);
        }
        public boolean commands(String actor,String unit){
            var u=units.get(unit);if(u==null||!u.alive())return false;if(u.estate()==null)return Objects.equals(actor,u.owner());
            var land=lands.get(u.estate());return land!=null&&(Objects.equals(actor,land.ruler())||officers.contains(u.estate()+"|"+actor));
        }
        public boolean friendly(String a,String b){
            if(a==null||b==null)return false;if(a.equals(b)||allies.contains(pair(a,b)))return true;var aa=lands.get(a);var bb=lands.get(b);if(aa==null||bb==null)return false;
            return Objects.equals(aa.root(),bb.root()) || aa.nation()!=null&&aa.nation().equals(bb.nation()) || aa.nation()!=null&&bb.nation()!=null&&allies.contains(pair(aa.nation(),bb.nation()));
        }
        boolean related(Set<String> relations,String a,String b){
            if(a==null||b==null)return false;if(relations.contains(pair(a,b)))return true;var aa=lands.get(a);var bb=lands.get(b);return aa!=null&&bb!=null&&aa.nation()!=null&&bb.nation()!=null&&relations.contains(pair(aa.nation(),bb.nation()));
        }
        public Result decide(String attacker,String ownerA,boolean recruitA,String target,String ownerB,boolean recruitB,boolean civilian,boolean monster,boolean pvp,boolean retaliation,long now){
            String a=estate(attacker,ownerA,recruitA),b=estate(target,ownerB,recruitB);String reason;
            if(now-updated>5000||now<updated)return new Result(false,"Дані Towny ще не готові або застаріли",a,b);
            if(!recruitA&&!recruitB)return new Result(true,"Поза правилами рекрутів",a,b);
            if(Objects.equals(attacker,target)||ownerA!=null&&ownerA.equals(target)||ownerB!=null&&ownerB.equals(attacker)||ownerA!=null&&ownerA.equals(ownerB))reason="Власник або його власний загін";
            else if(recruitA&&monster&&!recruitB)return new Result(true,"Ворожий монстр",a,b);
            else if(recruitA&&civilian&&!recruitB)reason="Цивільний мешканець або тварина";
            else if(!pvp)reason="На території заборонено PvP";
            else if(friendly(a,b))reason="Спільне володіння, васальний дім або прямий союз";
            else if(related(pacts,a,b))reason="Діє договір про ненапад";
            else if(recruitA&&retaliation)return new Result(true,"Тимчасова самооборона проти фактичного нападника",a,b);
            else if(!recruitA&&b!=null)return new Result(true,"Напад на нейтрального рекрута дозволений територіальними правилами; можлива самооборона",a,b);
            else if(a==null||b==null)reason="Не визначено місто або володіння сторони";
            else if(related(wars,a,b))return new Result(true,"Офіційна війна",a,b);
            else if(retaliation)return new Result(true,"Тимчасова самооборона проти фактичного нападника",a,b);
            else reason="Нейтральна сторона: напад першими заборонено";
            return new Result(false,reason,a,b);
        }
    }
    public static String pair(String a,String b){return a.compareTo(b)<=0?a+"|"+b:b+"|"+a;}
    public static View empty(){return new View(0,Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of());}
}
