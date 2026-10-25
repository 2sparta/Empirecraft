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
        for(String material:new String[]{"iron","steel","diamond"}) {
            CompoundTag arrow=new CompoundTag();
            arrow.m_128359_("Potion","minecraft:strong_poison");arrow.m_128379_("LingeringPotion",true);
            arrow.m_128365_("CustomPotionEffects",new ListTag());arrow.m_128405_("CustomPotionColor",123);
            arrow.m_128405_("Quality",5);arrow.m_128359_("Name",material+" arrow");
            TagPolicy.clean(arrow,false,true);
            check(!arrow.m_128441_("Potion") && !arrow.m_128441_("LingeringPotion") && !arrow.m_128441_("CustomPotionEffects") && !arrow.m_128441_("CustomPotionColor"),material+" tipped/lingering metadata removed");
            check(arrow.m_128451_("Quality")==5 && arrow.m_128461_("Name").equals(material+" arrow"),material+" arrow quality and name retained");
            check(TagPolicy.clean(arrow,false,true)==arrow,"arrow cleanup is idempotent");
        }
        for(String door:new String[]{"andesite_door","brass_door","copper_door","framed_glass_door","framed_glass_trapdoor","train_door","train_trapdoor"})
            check(ContentPolicy.createDoor("create:"+door),"Create door blocked: "+door);
        for(String allowed:new String[]{"minecraft:oak_door","minecraft:iron_trapdoor","supplementaries:iron_gate","create:cuckoo_clock","create:framed_glass","create:door_controller"})
            check(!ContentPolicy.createDoor(allowed),"other content retained: "+allowed);
        check(!ContentPolicy.createDoor(null),"unregistered state is safe");
        check(TagPolicy.clean(null,false,false)==null,"ordinary untagged item stays untagged");
        check(TagPolicy.clean(null,true,false).m_128461_("Potion").equals("minecraft:water"),"untagged potion becomes water");
        check(RenewalPolicy.space(true,false,true,false,true),"two-block lemon/sunflower fits unobstructed air");
        check(!RenewalPolicy.space(true,false,false,false,true),"tall plant cannot overwrite a roof or another plant");
        check(RenewalPolicy.space(true,false,false,false,false),"one-block berry bush does not need two air blocks");
        check(RenewalPolicy.space(false,true,true,true,true),"wild rice occupies existing source water with air above");
        check(!RenewalPolicy.space(true,false,true,true,true),"rice cannot be planted on dry land");
        check(!RenewalPolicy.space(false,true,false,true,true),"rice cannot replace the upper water of a deep pond");
        check(!RenewalPolicy.space(false,true,true,false,false),"land plants cannot replace water");
        check(!RenewalPolicy.space(false,false,true,false,false),"existing blocks are protected");
        check(RenewalPolicy.plantSubstrate("minecraft:cactus"),"prickly pear can use existing cactus");
        check(RenewalPolicy.naturalSoil("minecraft:sand"),"coconut/date can use native sandy habitat");
        check(!RenewalPolicy.naturalSoil("minecraft:farmland"),"player crop beds are protected");
        check(!RenewalPolicy.plantSubstrate("minecraft:oak_planks"),"buildings are not substrates");
        check(RenewalPolicy.climate("cold",.39f) && !RenewalPolicy.climate("cold",.4f),"taiga grape boundary");
        check(RenewalPolicy.climate("hot",.9f) && !RenewalPolicy.climate("hot",.89f),"tropical fruit boundary");
        check(RenewalPolicy.climate("temperate",.2f) && !RenewalPolicy.climate("temperate",1f),"temperate species boundary");
        var data=new RenewalData();long chunk=-4294967297L;
        data.planted(chunk,50000);
        check(data.treeReady(chunk,50000,2400),"planting a crop does not delay tree renewal");
        data.treePlanted(chunk,51000);
        var restored=RenewalData.load(data.m_7176_(new CompoundTag()));
        check(!restored.plantReady(chunk,51199,1200),"crop cooldown survives real NBT roundtrip");
        check(restored.plantReady(chunk,51200,1200),"crop renewal resumes at the one-minute boundary");
        check(!restored.treeReady(chunk,53399,2400) && restored.treeReady(chunk,53400,2400),"independent two-minute tree cooldown survives restart");
        check(!restored.plantReady(chunk,49000,1200) && !restored.treeReady(chunk,49000,2400),"time rollback cannot reset cooldowns");
        check(restored.treeReady(1,51000,2400) && restored.plantReady(1,51000,1200),"chunks retain independent cooldowns");
        var legacy=new CompoundTag();legacy.m_128388_("SaltChunks",new long[]{chunk});
        var oldTimes=new ListTag();var oldTime=new CompoundTag();
        oldTime.m_128356_("Chunk",chunk);oldTime.m_128356_("Time",50000);oldTimes.add(oldTime);
        legacy.m_128365_("PlantTimes",oldTimes);
        var migrated=RenewalData.load(legacy);
        check(!migrated.plantReady(chunk,50000,1200),"1.0.0 crop history remains compatible");
        check(migrated.treeReady(chunk,50000,2400),"old saves immediately permit a first tree");
        check(!migrated.m_7176_(legacy).m_128441_("SaltChunks"),"legacy salt quota is discarded when saving");
        System.out.println("PASS world rules policies and real NBT persistence: "+checks+" assertions");
    }
}

