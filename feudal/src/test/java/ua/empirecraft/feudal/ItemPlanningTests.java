package ua.empirecraft.feudal;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;

/** Complete item identity is represented by a test tag; no mocked database or server is used. */
public final class ItemPlanningTests {
    static final class Stack extends ItemStack {
        final String tag;final int limit;
        Stack(Material type,int amount,String tag,int limit){super(type,amount);this.tag=tag;this.limit=limit;}
        @Override public Stack clone(){return new Stack(getType(),getAmount(),tag,limit);}
        @Override public boolean isSimilar(ItemStack o){return o instanceof Stack s && getType()==s.getType() && tag.equals(s.tag);}
        @Override public boolean equals(Object o){return o instanceof Stack s && isSimilar(s) && getAmount()==s.getAmount();}
        @Override public int hashCode(){return Objects.hash(getType(),getAmount(),tag);}
        @Override public int getMaxStackSize(){return limit;}
    }
    static Stack stone(int amount,String tag){return new Stack(Material.STONE,amount,tag,64);}
    public static void main(String[] args) throws Exception {
        count=0;
        test("Поставка з кількох стеків не змінює оригінал та зберігає властивості",()->{
            ItemStack[] before=new ItemStack[36];before[0]=stone(64,"ore");before[1]=stone(8,"ore");before[2]=stone(64,"named");var after=ItemTransactions.take(before,stone(1,"ore"),70);eq(after[0],null);eq(after[1].getAmount(),2);eq(after[2],before[2]);eq(before[0].getAmount(),64);no(()->ItemTransactions.take(before,stone(1,"other"),1));
        });
        test("Видача зберігає теги й заповнює часткові стеки до порожніх",()->{
            ItemStack[] before=new ItemStack[36];before[10]=stone(63,"custom");var after=ItemTransactions.add(before,stone(1,"custom"),3);eq(after[10].getAmount(),64);eq(after[0],stone(2,"custom"));eq(before[10].getAmount(),63);eq(before[0],null);
        });
        test("Повний інвентар і нестача предметів не створюють часткових змін",()->{
            ItemStack[] before=new ItemStack[36];Arrays.setAll(before,i->stone(64,"plain"));no(()->ItemTransactions.add(before,stone(1,"other"),1));no(()->ItemTransactions.take(before,stone(1,"plain"),2305));eq(before[0].getAmount(),64);
        });
        test("Два повні інвентарі можуть обмінятися звільненими клітинками",()->{
            ItemStack[] a=new ItemStack[36],b=new ItemStack[36];Arrays.setAll(a,i->stone(64,"left"));Arrays.setAll(b,i->stone(64,"right"));
            var aa=ItemTransactions.removeSlots(a,Map.of(0,a[0].clone()));var bb=ItemTransactions.removeSlots(b,Map.of(1,b[1].clone()));aa=ItemTransactions.add(aa,b[1],64);bb=ItemTransactions.add(bb,a[0],64);eq(aa[0],stone(64,"right"));eq(bb[1],stone(64,"left"));eq(a[0],stone(64,"left"));
        });
        test("Підміна запропонованого предмета, кількості або клітинки відхиляється",()->{
            ItemStack[] a=new ItemStack[36];a[0]=stone(64,"plain");no(()->ItemTransactions.removeSlots(a,Map.of(0,stone(64,"named"))));no(()->ItemTransactions.removeSlots(a,Map.of(0,stone(63,"plain"))));no(()->ItemTransactions.removeSlots(a,Map.of(36,stone(64,"plain"))));
        });
        test("Нестековані предмети та null/AIR порівнюються коректно",()->{
            ItemStack[] a=new ItemStack[36];var item=new Stack(Material.DIAMOND_SWORD,1,"enchanted",1);var b=ItemTransactions.add(a,item,2);eq(b[0],item);eq(b[1],item);eq(b[2],null);ItemStack[] air=new ItemStack[36];air[0]=new ItemStack(Material.AIR);eq(ItemTransactions.same(a,air),true);no(()->ItemTransactions.add(a,item,0));
        });
        System.out.println("Перевірок предметних планів: "+count);
    }
}
