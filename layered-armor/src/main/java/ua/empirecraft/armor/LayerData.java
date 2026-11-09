package ua.empirecraft.armor;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

/** The equipped outer piece owns the complete item data of its inner pieces. No duplicate inventory. */
public final class LayerData {
    public static final String KEY="EmpirecraftArmorLayers";
    public static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private LayerData() {}
    public static EquipmentSlot slot(ItemStack stack) { return stack.getItem() instanceof ArmorItem armor?armor.getEquipmentSlot():null; }
    public static int rank(ItemStack stack) {
        if(slot(stack)==null)return -1;
        for(int rank=0;rank<3;rank++)if(stack.is(TagKey.create(Registries.ITEM,new ResourceLocation("empirecraft_armor",List.of("padded","mail","outer").get(rank)))))return rank;
        String id=Objects.toString(ForgeRegistries.ITEMS.getKey(stack.getItem()),""),name=id.substring(id.indexOf(':')+1);
        if(name.contains("gambeson")||name.contains("padded")||name.contains("quilted")||name.contains("arming_")
                ||id.startsWith("minecraft:leather_"))return 0;
        if(name.contains("chain")||name.contains("hauberk")||name.contains("haubergeon")||name.contains("mail_")||name.endsWith("_mail")||name.contains("coif"))return 1;
        return 2;
    }
    public static boolean layered(ItemStack stack) { return stack.hasTag()&&stack.getTag().contains(KEY,Tag.TAG_LIST)&&!stack.getTag().getList(KEY,Tag.TAG_COMPOUND).isEmpty(); }
    public static ItemStack plain(ItemStack stack) { var result=stack.copy();if(result.hasTag()){result.getTag().remove(KEY);if(result.getTag().isEmpty())result.setTag(null);}return result; }
    public static List<ItemStack> pieces(ItemStack outer) {
        var pieces=new ArrayList<ItemStack>();if(outer.isEmpty())return pieces;
        if(layered(outer))for(Tag raw:outer.getTag().getList(KEY,Tag.TAG_COMPOUND)) {
            var item=ItemStack.of((CompoundTag)raw);
            if(!item.isEmpty()&&slot(item)==slot(outer)&&pieces.size()<2)pieces.add(plain(item));
        }
        pieces.add(plain(outer));return pieces;
    }
    public static ItemStack wrap(List<ItemStack> pieces) {
        if(pieces.isEmpty())return ItemStack.EMPTY;
        var outer=plain(pieces.get(pieces.size()-1));outer.setCount(1);
        var inside=new ListTag();
        for(int i=0;i<pieces.size()-1;i++)if(!pieces.get(i).isEmpty())inside.add(plain(pieces.get(i)).save(new CompoundTag()));
        if(!inside.isEmpty())outer.getOrCreateTag().put(KEY,inside);
        return outer;
    }
    public static boolean canAdd(ItemStack worn,ItemStack incoming) {
        return !worn.isEmpty()&&slot(worn)!=null&&slot(worn)==slot(incoming)&&!layered(incoming)
            &&pieces(worn).size()<3&&rank(incoming)>rank(worn);
    }
    public static UUID modifier(EquipmentSlot slot,int layer,UUID original) {
        return UUID.nameUUIDFromBytes(("empirecraft.armor.layer/"+slot.name()+"/"+layer+"/"+original).getBytes(StandardCharsets.UTF_8));
    }
}
