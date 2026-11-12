package ua.empirecraft.tfc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Makes new crops obtainable in the imported map, without replacing terrain. */
public final class SeedLoot extends LootModifier {
    public static final Codec<SeedLoot> CODEC=RecordCodecBuilder.create(instance->codecStart(instance).apply(instance,SeedLoot::new));
    public SeedLoot(LootItemCondition[] conditions){super(conditions);}
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context){
        var seeds=ForgeRegistries.ITEMS.getValues().stream().filter(item->{var id=ForgeRegistries.ITEMS.getKey(item);
            return id!=null&&id.getNamespace().equals("tfc")&&id.getPath().startsWith("seeds/");}).toList();
        if(!seeds.isEmpty())loot.add(new ItemStack(seeds.get(context.getRandom().nextInt(seeds.size()))));
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec(){return CODEC;}
}
