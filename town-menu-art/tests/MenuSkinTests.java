package ua.empirecraft.feudal;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/** Decoration must never rewrite item identities, names, lore, amounts or food data. */
public final class MenuSkinTests {
    public static void main(String[] args) {
        Map<NamespacedKey,Object> tags=new HashMap<>();
        int[] model={-1};Set<ItemFlag> flags=new HashSet<>();
        PersistentDataContainer pdc=(PersistentDataContainer)Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),new Class<?>[]{PersistentDataContainer.class},(o,m,a)->{
            if(m.getName().equals("set")){tags.put((NamespacedKey)a[0],a[2]);return null;}
            throw new AssertionError("Unexpected persistent data operation "+m.getName());
        });
        ItemMeta meta=(ItemMeta)Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),new Class<?>[]{ItemMeta.class},(o,m,a)->{
            switch(m.getName()){
                case "setCustomModelData":model[0]=(Integer)a[0];return null;
                case "getPersistentDataContainer":return pdc;
                case "addItemFlags":flags.addAll(Arrays.asList((ItemFlag[])a[0]));return null;
                default:throw new AssertionError("Decoration tried to change "+m.getName());
            }
        });
        for(Material material:Material.values()){
            int icon=MenuSkin.model(material,10,"town")-MenuSkin.BASE;
            if(icon<0||icon>=44)throw new AssertionError(material);
        }
        check(MenuSkin.model(Material.ARROW,46,"town")==742112);
        check(MenuSkin.model(Material.ARROW,52,"town")==742113);
        check(MenuSkin.model(Material.BARRIER,53,"town")==742115);
        check(MenuSkin.model(Material.PAPER,-1,"provision")==742114);
        check(MenuSkin.model(Material.ROTTEN_FLESH,-1,"provision")==742138);
        check(MenuSkin.model(Material.BREAD,-1,"provision")==742139);
        MenuSkin.apply(meta,Material.BELL,4,"town");
        check(model[0]==742100&&tags.get(MenuSkin.KEY).equals("town/1"));
        MenuSkin.apply(meta,Material.CHEST,-1,"provision");
        check(model[0]==742136&&tags.get(MenuSkin.KEY).equals("provision/1"));
        check(flags.equals(Set.of(ItemFlag.HIDE_ATTRIBUTES)));
        System.out.println("MENU_SKIN_PASS: every material, directional arrows, anchors, hunger state, metadata-only writes");
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
}
