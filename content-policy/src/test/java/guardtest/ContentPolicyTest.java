package guardtest;
import net.minecraft.nbt.*;
import ua.empirecraft.content.*;

public final class ContentPolicyTest {
    public static void main(String[] args) {
        for(String id:new String[]{"medieval_boomsticks:raw_magnitite","minecraft:diamond_sword","minecraft:netherite_sword","magistuarmory:diamond_guisarme","overgeared:diamond_arrow"})
            if(!Policy.blocked(id))throw new AssertionError(id);
        for(String id:new String[]{"spawn:date_seed","spawn:booby_egg_shard","spawn:anthill","minecraft:diamond_pickaxe","minecraft:diamond_axe","minecraft:diamond_chestplate","magistuarmory:diamond_kiteshield","magistuarmory:steel_guisarme"})
            if(Policy.blocked(id))throw new AssertionError("Unrelated content blocked: "+id);
        if(Policy.damage(500,1000,300)!=150 || Policy.damage(3000,1000,300)!=299 || Policy.damage(-1,1000,300)!=0)throw new AssertionError("Damage migration");
        var tag=new CompoundTag();tag.m_128405_("Damage",500);tag.m_128405_("Quality",5);tag.m_128359_("CustomName","family sword");tag.m_128365_("AttributeModifiers",new ListTag());
        Stacks.clean(tag,1000,300);
        if(tag.m_128451_("Damage")!=150 || tag.m_128451_("Quality")!=5 || !tag.m_128461_("CustomName").equals("family sword") || tag.m_128441_("AttributeModifiers"))throw new AssertionError("NBT migration must preserve identity/quality and remove retired combat modifiers");
        if(!Policy.BLOCKS.contains("medieval_boomsticks:pyrite_ore_deepslate") || !Policy.ENTITIES.isEmpty())throw new AssertionError("Generation/spawn coverage");
        if(Policy.REPLACEMENTS.keySet().stream().anyMatch(k->k.startsWith("spawn:")))throw new AssertionError("Spawn must not be substituted");
        System.out.println("PASS retired items, retained tools/armor/shields, damage boundaries and real NBT migration");
    }
}
