package guardtest;
import ua.empirecraft.forging.GunsmithRules;
public class GunsmithRulesTest {
    public static void main(String[] args){
        assert GunsmithRules.steps(3,false)==24;
        assert GunsmithRules.steps(3,true)==6;
        assert GunsmithRules.steps(4,true)==18;
        assert GunsmithRules.steps(6,false)==24;
        assert GunsmithRules.steps(6,true)==6;
        assert GunsmithRules.steps(7,false)==18;
        assert GunsmithRules.operation(5,99,100)==5 : "rapid clicking cannot advance";
        assert GunsmithRules.operation(5,100,100)==6;
        assert GunsmithRules.operation(5,140,100)==6;
        assert GunsmithRules.operation(5,141,100)==4 : "late operation loses exactly one step";
        assert GunsmithRules.operation(0,1000,100)==1 : "paused initial work remains recoverable";
        assert GunsmithRules.bullet("medieval_boomsticks:round_ball","arrow");
        assert !GunsmithRules.bullet("medieval_boomsticks:heavy_bolt","arrow");
        assert !GunsmithRules.bullet("minecraft:arrow","arrow");
        assert GunsmithRules.bullet("thirtyyearswar:bullet","bullet");
        assert GunsmithRules.tier("medieval_boomsticks:round_ball")<GunsmithRules.tier("musketmod:bullet");
        assert GunsmithRules.tier("musketmod:bullet")<GunsmithRules.tier("thirtyyearswar:bullet");
        for(String name:new String[]{"baroque_cuirassier_armour","baroque_dark_cuirassier_armour"})assert GunsmithRules.profile(name).equals("proof");
        assert GunsmithRules.profile("haubergeon").equals("mail");
        assert GunsmithRules.profile("quilted_coat").equals("padded");
        assert GunsmithRules.modifier("proof",0)<GunsmithRules.modifier("plate",0);
        assert GunsmithRules.modifier("proof",2)>0 : "proof armor remains vulnerable to maces";
        assert GunsmithRules.modifier("mail",0)>0 && GunsmithRules.modifier("mail",1)<0;
        assert GunsmithRules.modifier("padded",0)>0 && GunsmithRules.modifier("padded",2)<0;
        for(String profile:new String[]{"proof","plate","mail","padded"})for(int kind=0;kind<3;kind++)assert 1+GunsmithRules.modifier(profile,kind)>0;
        System.out.println("PASS workshop timing, research advantage, all firearm families and armor strengths/weaknesses");
    }
}
