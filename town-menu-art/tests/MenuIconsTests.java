package ua.empirecraft.feudal;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.empirecraft.menuart.IconCatalog;

/** Real Bukkit interfaces: protect footer routing metadata and keep stored items untouched. */
public final class MenuIconsTests {
    static class Stack extends ItemStack {
        final Material material;ItemMeta metadata;int amount;boolean accept=true;
        Stack(Material material,ItemMeta metadata,int amount){super(Material.PAPER);this.material=material;this.metadata=metadata;this.amount=amount;}
        @Override public Material getType(){return material;}
        @Override public ItemMeta getItemMeta(){return metadata;}
        @Override public boolean setItemMeta(ItemMeta value){if(!accept)return false;metadata=value;return true;}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int value){amount=value;}
    }
    static ItemMeta meta(String title){
        return (ItemMeta)Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),new Class<?>[]{ItemMeta.class},(o,m,a)->{
            if(m.getName().equals("getDisplayName"))return title;
            if(m.getName().equals("toString"))return "Meta("+title+") with original PDC";
            throw new AssertionError("Unexpected metadata access/mutation "+m.getName());
        });
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        var names=new HashSet<>(IconCatalog.NAMES);check(names.size()==46);
        for(Material material:Material.values())for(int slot:new int[]{-1,4,30,46,50,51,52,53})for(String section:List.of("town","provision")){
            String id=MenuIcons.id(material,slot,section);
            check(id.startsWith(MenuIcons.NAMESPACE)&&names.contains(id.substring(MenuIcons.NAMESPACE.length())));
        }
        check(MenuIcons.id(Material.ARROW,52,"town").endsWith(":next"));
        check(MenuIcons.id(Material.ARROW,45,"town").endsWith(":back"));
        check(MenuIcons.id(Material.PAPER,53,"provision").endsWith(":refresh"));
        check(MenuIcons.id(Material.BREAD,49,"provision").endsWith(":satisfied"));
        check(MenuIcons.id(Material.BREAD,32,"town").endsWith(":provisions"));
        check(MenuIcons.footerIcon(Material.IRON_SWORD).equals("recruitment"));
        check(MenuIcons.footerIcon(Material.IRON_HELMET).equals("garrison"));
        check(MenuIcons.footerIcon(Material.PAPER)==null);
        ItemStack[] content=new ItemStack[54];
        var food=new Stack(Material.BREAD,meta("Збережена їжа"),16);content[0]=food;
        var ordinary=new Stack(Material.IRON_SWORD,meta("Показник у вмісті"),1);content[1]=ordinary;
        var recruit=new Stack(Material.IRON_SWORD,meta("Наймання рекрутів; action=entry"),1);content[50]=recruit;
        var garrison=new Stack(Material.IRON_HELMET,meta("Гарнізон; army_menu=1"),1);content[51]=garrison;
        var page=new Stack(Material.PAPER,meta("Сторінка 2"),2);content[49]=page;
        Inventory inventory=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(o,m,a)->switch(m.getName()){
            case "getSize"->54;case "getItem"->content[(Integer)a[0]];
            case "setItem"->{content[(Integer)a[0]]=(ItemStack)a[1];yield null;}
            default->throw new AssertionError(m.getName());
        });
        var selected=new ArrayList<String>();
        MenuIcons.finishTownMenu(inventory,(id,material)->{selected.add(id);return new Stack(Material.PAPER,null,1);});
        check(selected.equals(List.of(MenuIcons.NAMESPACE+"recruitment",MenuIcons.NAMESPACE+"garrison")));
        check(content[50]!=recruit&&content[50].getItemMeta()==recruit.getItemMeta());
        check(content[51]!=garrison&&content[51].getItemMeta()==garrison.getItemMeta());
        check(content[0]==food&&food.getAmount()==16&&content[1]==ordinary&&content[49]==page);
        MenuIcons.finishTownMenu(inventory,(id,material)->{throw new AssertionError("Already decorated control");});
        content[50]=recruit;
        MenuIcons.finishTownMenu(inventory,(id,material)->{var failed=new Stack(Material.PAPER,null,1);failed.accept=false;return failed;});
        check(content[50]==recruit);
        System.out.println("NATIVE_MENU_ICONS_PASS registry mapping, arrows, provision states, late recruitment controls, preserved click metadata, storage isolation, failed conversion");
    }
}
