package guardtest;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import ua.empirecraft.worldrules.*;

public final class RulesPolicyTest {
    private static int checks;
    private static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    public static void main(String[] args) {
        CompoundTag item=new CompoundTag();
        item.m_128365_("Enchantments",new ListTag());item.m_128365_("StoredEnchantments",new ListTag());
        item.m_128405_("Damage",37);item.m_128359_("Name","named sword");item.m_128405_("Quality",5);
        check(TagPolicy.clean(item,false,false)==item,"same metadata object");
        check(!item.m_128441_("Enchantments") && !item.m_128441_("StoredEnchantments"),"all enchantments removed");
        check(item.m_128451_("Damage")==37 && item.m_128451_("Quality")==5 && item.m_128461_("Name").equals("named sword"),"durability/quality/custom name preserved");
        CompoundTag wine=new CompoundTag();wine.m_128365_("CustomPotionEffects",new ListTag());wine.m_128405_("WineAge",10);
        check(TagPolicy.clean(wine,false,false).m_128441_("CustomPotionEffects") && wine.m_128451_("WineAge")==10,"mod wine effects preserved");
        CompoundTag magic=new CompoundTag();magic.m_128359_("Potion","minecraft:strong_strength");magic.m_128365_("CustomPotionEffects",new ListTag());magic.m_128405_("CustomPotionColor",123);
        TagPolicy.clean(magic,true,false);
        check(magic.m_128461_("Potion").equals("minecraft:water") && !magic.m_128441_("CustomPotionEffects") && !magic.m_128441_("CustomPotionColor"),"vanilla potion becomes harmless water");
        TagPolicy.clean(magic,false,true);check(!magic.m_128441_("Potion"),"throwable/arrow potion metadata removed");
        check(TagPolicy.clean(null,false,false)==null,"ordinary untagged item stays untagged");
        check(TagPolicy.clean(null,true,false).m_128461_("Potion").equals("minecraft:water"),"untagged potion becomes water");
        for(int size=1;size<=50;size++) {
            Set<RenewalPolicy.Pos> vein=new HashSet<>();
            for(int i=0;i<size;i++)vein.add(new RenewalPolicy.Pos(i-25,-24,-1));
            boolean changed;
            do {
                changed=false;
                for(var pos:new ArrayList<>(vein)) if(RenewalPolicy.leavesReserve(pos,p->true,vein::contains)) {
                    vein.remove(pos);changed=true;
                    check(vein.size()>=2,"a repeated conversion never exhausts the original ore vein");
                }
            } while(changed);
            check(vein.size()>=Math.min(size,2),"one/two-block deposits preserved; larger deposits retain a reserve");
            for(var pos:vein)check(!RenewalPolicy.leavesReserve(pos,p->true,vein::contains),"small remaining deposit fragments are protected");
        }
        Set<RenewalPolicy.Pos> boundary=new HashSet<>(List.of(new RenewalPolicy.Pos(15,0,0),new RenewalPolicy.Pos(16,0,0),new RenewalPolicy.Pos(17,0,0)));
        check(!RenewalPolicy.leavesReserve(new RenewalPolicy.Pos(15,0,0),p->p.x()<16,boundary::contains),"unloaded neighbor does not authorize a conversion");
        check(RenewalPolicy.leavesReserve(new RenewalPolicy.Pos(15,0,0),p->true,boundary::contains),"loaded cross-chunk deposit counted");
        int eligible=0;
        for(int x=-50;x<50;x++)for(int z=-50;z<50;z++) {
            boolean value=RenewalPolicy.eligible(42,x,z,25);
            check(value==RenewalPolicy.eligible(42,x,z,25),"chunk eligibility stable across restart");
            if(value)eligible++;
        }
        check(eligible>2200 && eligible<2800,"eligible chunk density remains near configured 25 percent");
        var data=new RenewalData();long chunk=-4294967297L;
        data.spend(chunk);data.planted(chunk,50000);
        var restored=RenewalData.load(data.m_7176_(new CompoundTag()));
        check(restored.spent(chunk),"salt quota survives NBT roundtrip and negative coordinates");
        check(!restored.plantReady(chunk,50000,24000),"crop cooldown survives NBT roundtrip");
        check(restored.plantReady(chunk,74000,24000),"crop regrowth resumes after cooldown");
        check(!restored.plantReady(chunk,49000,24000),"time rollback cannot reset cooldown");
        check(!restored.spent(1),"another chunk retains independent quota");
        System.out.println("PASS world rules policies and real NBT persistence: "+checks+" assertions");
    }
}
