package ua.empirecraft.feudal;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;

/** Inventory planning checks against the Bukkit item API; these do not boot a server. */
public final class GoldInventoryTests {
    static final class Stack extends ItemStack {
        private final boolean decorated;
        Stack(Material material,int amount,boolean decorated){super(material,amount);this.decorated=decorated;}
        @Override public boolean hasItemMeta(){return decorated;}
        @Override public int getMaxStackSize(){return 64;}
        @Override public Stack clone(){return new Stack(getType(),getAmount(),decorated);}
    }
    static ItemStack[] slots(){return new ItemStack[36];}
    public static void main(String[] args) throws Exception {
        count=0;
        test("Внесок з кількох стеків: оригінал не змінено до збереження наміру",()->{
            var before=slots();before[0]=new Stack(Material.GOLD_INGOT,64,false);before[1]=new Stack(Material.GOLD_INGOT,3,false);
            var after=GoldInventoryBridge.plan(before,true,65);eq(after[0],null);eq(after[1].getAmount(),2);eq(before[0].getAmount(),64);eq(before[1].getAmount(),3);
        });
        test("Нестача злитків і повний інвентар не спричиняють часткових змін",()->{
            var before=slots();before[0]=new Stack(Material.GOLD_INGOT,2,false);no(()->GoldInventoryBridge.plan(before,true,3));eq(before[0].getAmount(),2);
            var full=slots();Arrays.setAll(full,i->new Stack(Material.STONE,64,false));no(()->GoldInventoryBridge.plan(full,false,1));eq(full[0].getType(),Material.STONE);
        });
        test("Іменоване золото та інші предмети не приймаються як звичайні злитки",()->{
            var before=slots();before[0]=new Stack(Material.GOLD_INGOT,64,true);before[1]=new Stack(Material.GOLD_NUGGET,64,false);
            no(()->GoldInventoryBridge.plan(before,true,1));eq(before[0].getAmount(),64);
        });
        test("Видача заповнює частковий стек і порожню клітинку, не кидає предметів",()->{
            var before=slots();before[0]=new Stack(Material.GOLD_INGOT,63,false);
            var after=GoldInventoryBridge.plan(before,false,3);eq(after[0].getAmount(),64);eq(after[1].getType(),Material.GOLD_INGOT);eq(after[1].getAmount(),2);eq(before[0].getAmount(),63);
            no(()->GoldInventoryBridge.plan(before,false,0));no(()->GoldInventoryBridge.plan(before,false,2305));
        });
        System.out.println("Перевірок планування інвентарю: "+count);
    }
}
