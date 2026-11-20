package ua.empirecraft.feudal;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
/** New controls must not trigger the retired chest overlay or overwrite a mod model. */
public final class MenuSkinTests {
 public static void main(String[] args) {
  Map<NamespacedKey,Object> tags=new HashMap<>();tags.put(MenuSkin.KEY,"town/1");Set<ItemFlag> flags=new HashSet<>();
  PersistentDataContainer pdc=(PersistentDataContainer)Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),new Class<?>[]{PersistentDataContainer.class},(o,m,a)->{if(m.getName().equals("remove")){tags.remove((NamespacedKey)a[0]);return null;}throw new AssertionError(m.getName());});
  ItemMeta meta=(ItemMeta)Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),new Class<?>[]{ItemMeta.class},(o,m,a)->{switch(m.getName()){case "getPersistentDataContainer":return pdc;case "addItemFlags":flags.addAll(Arrays.asList((ItemFlag[])a[0]));return null;default:throw new AssertionError("Changed item model/name/lore: "+m.getName());}});
  MenuSkin.apply(meta,Material.BELL,4,"town");MenuSkin.apply(meta,Material.CHEST,-1,"provision");
  if(!tags.isEmpty()||!flags.equals(Set.of(ItemFlag.HIDE_ATTRIBUTES)))throw new AssertionError();
  System.out.println("MENU_VANILLA_CHEST_PASS noOverlayMarker=true modModelPreserved=true namesAndLoreUntouched=true");
 }
}
