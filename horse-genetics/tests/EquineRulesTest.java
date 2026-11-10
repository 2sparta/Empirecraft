package ua.empirecraft.equine;
import java.util.*;
import net.minecraft.nbt.*;
public final class EquineRulesTest {
    static int checks;
    static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    public static void main(String[] args) {
        var m=EquineGenome.founder(new Random(1));var f=EquineGenome.founder(new Random(2));
        String original=m.encode();check(original.equals(EquineGenome.decode(original).encode()),"RHG allele encoding roundtrip");
        for(int seed=0;seed<40;seed++) {
            var c=EquineGenome.inherit(m,f,new Random(seed),0);
            for(var g:EquineGenome.Gene.values()) {
                check(c.allele(g,0)==m.allele(g,0)||c.allele(g,0)==m.allele(g,1),"maternal allele");
                check(c.allele(g,1)==f.allele(g,0)||c.allele(g,1)==f.allele(g,1),"paternal allele");
            }
            check(c.speed()>=.1125&&c.speed()<=.3375,"RHG speed bounds");check(c.jump()>=.4&&c.jump()<=1.,"RHG jump bounds");check(c.health()>=15&&c.health()<=31,"RHG health bounds");
            check(EquineGenome.inherit(m,f,new Random(seed),1).encode().length()==original.length(),"mutation keeps valid genome length");
        }
        check(m.encode().equals(original),"inheritance never mutates parents");
        var old=new CompoundTag();old.putString("id","horse_colors:horse_felinoid");UUID id=UUID.randomUUID(),owner=UUID.randomUUID();old.putUUID("UUID",id);old.putUUID("Owner",owner);
        old.putString("CustomName","{\"text\":\"Руда\"}");old.putString("Genes",original);old.putBoolean("gender",true);old.putBoolean("fertile",true);old.putDouble("Health",26);
        var armor=new CompoundTag();armor.putString("id","minecraft:iron_horse_armor");armor.putByte("Count",(byte)1);old.put("ArmorItem",armor);old.put("SaddleItem",armor.copy());
        var before=old.copy();check(LegacyMigration.convert(old),"legacy horse migrates before registry lookup");
        check(old.getString("id").equals("dragnlivestock:o_horse")&&old.getUUID("UUID").equals(id),"species changes, UUID survives");
        check(old.getUUID("Owner").equals(owner)&&old.getString("CustomName").equals(before.getString("CustomName")),"owner/name preserved");
        check(old.getCompound("ArmorItem").equals(before.getCompound("ArmorItem"))&&old.getCompound("SaddleItem").equals(before.getCompound("SaddleItem")),"equipment preserved");
        var data=old.getCompound("ForgeData").getCompound(LegacyMigration.KEY);
        check(data.getString("Genes").equals(original)&&data.getCompound("OriginalEntity").equals(before),"full original NBT and genes retained");
        var once=old.copy();check(!LegacyMigration.convert(old)&&old.equals(once),"migration is idempotent");
        for(String type:List.of("minecraft:horse","minecraft:donkey","minecraft:mule","horse_colors:donkey","horse_colors:mule","horse_colors:hinny")) {
            var t=new CompoundTag();t.putString("id",type);check(LegacyMigration.convert(t)&&t.hasUUID("UUID"),"spawn tags without UUID migrate safely");
            check(t.getString("id").startsWith("dragnlivestock:"),"only DLO mount IDs survive");
        }
        var item=new CompoundTag();item.putString("id","horse_colors:gene_book");item.putByte("Count",(byte)1);LegacyMigration.item(item);
        check(item.getString("id").equals("minecraft:book")&&item.getCompound("tag").getString("EmpirecraftLegacyItem").equals("horse_colors:gene_book"),"retired item IDs have valid replacements and provenance");
        var chest=new CompoundTag();chest.putString("id","horse_colors:donkey");chest.putBoolean("ChestedHorse",true);var items=new ListTag();var storage=new CompoundTag();storage.putByte("Slot",(byte)2);storage.putString("id","minecraft:diamond");storage.putByte("Count",(byte)8);items.add(storage);chest.put("Items",items);
        LegacyMigration.convert(chest);check(chest.getList("Items",Tag.TAG_COMPOUND).getCompound(0).getByte("Slot")==3,"chest storage avoids DLO decor slot");
        var pregnant=new CompoundTag();pregnant.putString("id","horse_colors:horse_felinoid");pregnant.putUUID("UUID",id);pregnant.putInt("true_age",10000);pregnant.putInt("pregnant_since",2000);
        var embryos=new ListTag();var embryo=new CompoundTag();StringBuilder hex=new StringBuilder("M");String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for(char allele:original.toCharArray())hex.append(String.format("%02X",alphabet.indexOf(allele)));
        embryo.putString("species","HORSE");embryo.putString("genes",hex.toString());embryo.putUUID("MotherUUID",id);embryos.add(embryo);pregnant.put("unborn_children",embryos);
        var same=pregnant.copy();LegacyMigration.convert(pregnant);LegacyMigration.convert(same);
        var pd=pregnant.getCompound("ForgeData").getCompound(LegacyMigration.KEY);
        check(pd.getLong("PendingPregnancyTicks")==40000,"legacy gestation keeps remaining time");
        var unborn=pd.getList("Children",Tag.TAG_COMPOUND).getCompound(0);
        check(unborn.getCompound("ForgeData").getCompound(LegacyMigration.KEY).getString("Genes").equals(original),"unborn RHG hex genome becomes the same complete DLO genome");
        check(unborn.getCompound("ForgeData").getCompound(LegacyMigration.KEY).getUUID("Mother").equals(id),"unborn pedigree preserved");
        check(unborn.getUUID("UUID").equals(same.getCompound("ForgeData").getCompound(LegacyMigration.KEY).getList("Children",Tag.TAG_COMPOUND).getCompound(0).getUUID("UUID")),"legacy unborn UUID is deterministic across retries");
        System.out.println("EQUINE_RULES_PASS assertions="+checks);
    }
}
