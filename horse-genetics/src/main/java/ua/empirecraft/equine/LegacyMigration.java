package ua.empirecraft.equine;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;

/** Runs before entity registry lookup, so retired RHG IDs remain readable without that mod. */
public final class LegacyMigration {
    public static final String KEY="EmpirecraftEquine";
    private static final Map<String,String> TYPES=Map.of(
        "horse_colors:horse_felinoid","dragnlivestock:o_horse","horse_colors:donkey","dragnlivestock:o_donkey",
        "horse_colors:mule","dragnlivestock:o_mule","horse_colors:hinny","dragnlivestock:o_mule",
        "minecraft:horse","dragnlivestock:o_horse","minecraft:donkey","dragnlivestock:o_donkey","minecraft:mule","dragnlivestock:o_mule","minecraft:skeleton_horse","dragnlivestock:o_horse","minecraft:zombie_horse","dragnlivestock:o_horse");
    private LegacyMigration(){}
    public static boolean convert(CompoundTag tag) {
        String old=tag.getString("id"),target=TYPES.get(old);if(target==null)return false;
        UUID uuid=tag.hasUUID("UUID")?tag.getUUID("UUID"):UUID.randomUUID();
        CompoundTag original=tag.copy(),forge=tag.getCompound("ForgeData"),data=forge.getCompound(KEY);
        tag.putUUID("UUID",uuid);data.putInt("Version",1);data.putString("OriginalType",old);data.put("OriginalEntity",original);
        if(tag.contains("Genes",Tag.TAG_STRING)) {
            // Validate before replacing the ID; the original byte-for-byte gene string is retained.
            data.putString("Genes",EquineGenome.decode(tag.getString("Genes")).encode());
        }
        if(tag.contains("gender"))tag.putInt("Gender",tag.getBoolean("gender")?1:0);
        else if(!tag.contains("Gender"))tag.putInt("Gender",Math.floorMod(uuid.hashCode(),2));
        boolean sterile=old.endsWith(":mule")||old.endsWith(":hinny")||tag.contains("fertile")&&!tag.getBoolean("fertile");
        tag.putBoolean("IsSnipped",sterile);data.putBoolean("Sterile",sterile);
        if(original.hasUUID("MotherUUID"))data.putUUID("Mother",original.getUUID("MotherUUID"));
        if(original.hasUUID("FatherUUID"))data.putUUID("Father",original.getUUID("FatherUUID"));
        if(old.equals("minecraft:skeleton_horse")||old.equals("minecraft:zombie_horse"))tag.putBoolean("Undead",true);
        tag.putString("id",target);tag.putInt("Breed",0);tag.putInt("Overlay",0);
        // Existing vanilla horse Variant packs coat + markings; DLO uses a different palette.
        if(old.equals("minecraft:horse"))tag.putInt("Variant",new int[]{24,11,10,6,2,15,12}[Math.floorMod(original.getInt("Variant")&255,7)]);
        else if(target.endsWith(":o_horse")&&data.contains("Genes")) {
            var g=EquineGenome.decode(data.getString("Genes"));
            tag.putInt("Variant",g.allele(EquineGenome.Gene.gray,0)>0||g.allele(EquineGenome.Gene.gray,1)>0?15:
                g.allele(EquineGenome.Gene.extension,0)==0&&g.allele(EquineGenome.Gene.extension,1)==0?10:
                Math.max(g.allele(EquineGenome.Gene.agouti,0),g.allele(EquineGenome.Gene.agouti,1))>=4?0:2);
        }else tag.putInt("Variant",0);
        if(target.endsWith(":o_horse")){tag.putInt("Mane",3);tag.putInt("Tail",2);}
        // DLO reserves slot 2 for decor; keep every chest item and move legacy slot 2 to storage.
        if(tag.getBoolean("ChestedHorse")&&tag.contains("Items",Tag.TAG_LIST)) {
            var items=tag.getList("Items",Tag.TAG_COMPOUND);
            for(Tag entry:items){var item=(CompoundTag)entry;item.putByte("Slot",(byte)(Byte.toUnsignedInt(item.getByte("Slot"))+1));}
        }
        var children=new ListTag();int i=0;
        for(Tag entry:original.getList("unborn_children",Tag.TAG_COMPOUND)) {
            var child=(CompoundTag)entry;String species=child.getString("species").toLowerCase(Locale.ROOT);
            var unborn=new CompoundTag();unborn.putString("id",species.contains("donkey")?"dragnlivestock:o_donkey":species.contains("mule")||species.contains("hinny")?"dragnlivestock:o_mule":"dragnlivestock:o_horse");
            unborn.putUUID("UUID",UUID.nameUUIDFromBytes((uuid+"/"+original.getInt("pregnant_since")+"/"+i++).getBytes(StandardCharsets.UTF_8)));
            String hex=child.getString("genes");boolean male=hex.startsWith("M");if(hex.startsWith("M")||hex.startsWith("F"))hex=hex.substring(1);
            String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";StringBuilder genes=new StringBuilder();
            if(hex.length()%2!=0)throw new IllegalArgumentException("Invalid unborn RHG genome");
            for(int n=0;n<hex.length();n+=2)genes.append(alphabet.charAt(Integer.parseInt(hex.substring(n,n+2),16)));
            var childData=new CompoundTag();childData.putBoolean("LegacyUnborn",true);childData.put("OriginalUnborn",child.copy());childData.putString("Genes",EquineGenome.decode(genes.toString()).encode());
            if(child.hasUUID("MotherUUID"))childData.putUUID("Mother",child.getUUID("MotherUUID"));
            if(child.hasUUID("FatherUUID"))childData.putUUID("Father",child.getUUID("FatherUUID"));
            var childForge=new CompoundTag();childForge.put(KEY,childData);unborn.put("ForgeData",childForge);unborn.putInt("Gender",male?1:0);
            unborn.putInt("Age",-120000);unborn.putInt("Breed",0);unborn.putInt("Mane",3);unborn.putInt("Tail",2);
            if(original.hasUUID("Owner")){unborn.putUUID("Owner",original.getUUID("Owner"));unborn.putBoolean("Tame",original.getBoolean("Tame"));}
            children.add(unborn);
        }
        if(!children.isEmpty()) {
            data.put("Children",children);data.putLong("PendingPregnancyTicks",Math.max(0,48000L-(original.getInt("true_age")-original.getInt("pregnant_since"))));
        }
        forge.put(KEY,data);tag.put("ForgeData",forge);return true;
    }
    public static void item(CompoundTag tag) {
        String id=tag.getString("id");if(!id.startsWith("horse_colors:"))return;
        var meta=tag.getCompound("tag");meta.putString("EmpirecraftLegacyItem",id);
        tag.putString("id",id.endsWith("netherite_horse_armor")?"minecraft:iron_horse_armor":id.endsWith("gene_book")?"minecraft:book":"minecraft:glass_bottle");tag.put("tag",meta);
    }
}
