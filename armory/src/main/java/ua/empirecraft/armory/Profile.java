package ua.empirecraft.armory;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.crafting.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** An explicit bill of materials per construction, shared by recipes, repair and JEI. */
public record Profile(String metal,String construction,List<String> forms) {
    public static Profile of(ItemStack result,Recipe<?> original){
        var armor=(ArmorItem)result.getItem();String id=ForgeRegistries.ITEMS.getKey(armor).toString().toLowerCase(Locale.ROOT);
        String metal=id.contains("black_steel")?"black_steel":id.contains("bronze")?"bronze":id.contains("copper")?"copper":id.contains("silver")?"silver":id.contains("golden_")?"gold":id.startsWith("minecraft:iron_")?"iron":"steel";
        // Antique iron mail/lamellar pieces retain their actual material.
        if(id.contains("iron_")||id.contains("rusted")||armor.getMaterial()==ArmorMaterials.IRON||armor.getMaterial()==ArmorMaterials.CHAIN)metal="iron";
        boolean textiles=false,metalInput=false;
        for(Ingredient ingredient:original.getIngredients())for(ItemStack s:ingredient.getItems()){
            String key=Objects.toString(ForgeRegistries.ITEMS.getKey(s.getItem()),"");
            if(key.contains("bronze")&&!id.contains("steel"))metal="bronze";
            textiles|=key.contains("wool")||key.contains("leather")||key.contains("fabric")||key.endsWith(":string");
            metalInput|=key.contains("ingot")||key.contains("plate")||key.contains("chainmail")||key.contains("lamellar_rows");
        }
        boolean cloth=armor.getMaterial()==ArmorMaterials.LEATHER||armor.getMaterial()==ArmorMaterials.TURTLE||!metalInput&&textiles;
        cloth|=id.startsWith(OttomanCombat.NS)&&!id.equals(OttomanCombat.NS+"shishak_helmet");
        for(String word:new String[]{"gambeson","padded","cloth","doublet","breeches","ruff","tunic","robe","tabard","felt_hat","renaissance_cap","jacket"})cloth|=id.contains(word);
        String construction=id.contains("chain")||id.contains("mail")?"mail":id.contains("lamell")||id.contains("scale")?"lamellar":id.contains("brigand")||id.contains("coat_of_plates")?"brigandine":id.contains("maxim")?"fluted":cloth?"padded":"plate";
        EquipmentSlot slot=armor.getEquipmentSlot();List<String> forms;
        if(construction.equals("padded"))forms=switch(slot){case CHEST->List.of("leather_panel","leather_panel","padded_lining");case LEGS->List.of("leather_panel","padded_lining");default->List.of("leather_panel");};
        else if(construction.equals("mail"))forms=switch(slot){case CHEST->List.of("mail_panel","mail_panel","mail_panel","mail_sleeves");case LEGS->List.of("mail_panel","mail_panel","mail_panel");default->List.of("mail_panel","mail_panel");};
        else if(construction.equals("lamellar")||construction.equals("brigandine")){String panel=construction.equals("lamellar")?"lamellar_panel":"brigandine_panel";forms=switch(slot){case CHEST->List.of(panel,panel,panel,"shoulders");case LEGS->List.of(panel,panel,"knees");default->List.of(panel,"ankles");};}
        else forms=switch(slot){case CHEST->construction.equals("fluted")?List.of("fluted_front","fluted_back","shoulders","arms","waist","joints"):List.of("front","back","shoulders","arms","waist");case LEGS->List.of("thighs","knees","greaves");case HEAD->id.contains("armet")||id.contains("closed")||id.contains("greathelm")||id.contains("maxim")?List.of("dome","nape","visor"):List.of("dome","nape");default->List.of("feet","ankles");};
        return new Profile(metal,construction,forms);
    }
    public Item item(String form){return Armory.PARTS.containsKey(form)?Armory.PARTS.get(form).get():Armory.part(metal,form);}
    public List<Item> items(){return forms.stream().map(this::item).toList();}
    public int metalCost(){return forms.stream().mapToInt(f->Armory.FORMS.getOrDefault(f,0)).sum();}
}
