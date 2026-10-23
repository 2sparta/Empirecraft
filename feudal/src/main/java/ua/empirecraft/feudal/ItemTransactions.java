package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.*;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Plans preserve complete ItemStacks. There are no drops, cursor transfers or blind retries. */
public final class ItemTransactions implements Listener {
    private final FeudalPlugin plugin;
    private final CommerceService commerce;
    public ItemTransactions(FeudalPlugin plugin,CommerceService commerce){this.plugin=plugin;this.commerce=commerce;}
    static boolean empty(ItemStack item){return item==null || item.getType().isAir() || item.getAmount()==0;}
    static ItemStack[] copy(ItemStack[] original){ItemStack[] result=new ItemStack[original.length];for(int i=0;i<result.length;i++)if(!empty(original[i]))result[i]=original[i].clone();return result;}
    static boolean same(ItemStack[] a,ItemStack[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++){if(empty(a[i]) && empty(b[i]))continue;if(!Objects.equals(a[i],b[i]))return false;}return true;}
    static ItemStack[] decode(String data) throws IOException,ClassNotFoundException {
        require(data!=null && data.length()<=4_000_000,"Неправильний знімок інвентарю.");
        try(var in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))){
            int n=in.readInt();require(n==1 || n==36,"Неправильний розмір інвентарю.");ItemStack[] items=new ItemStack[n];for(int i=0;i<n;i++)items[i]=(ItemStack)in.readObject();return items;
        }
    }
    static ItemStack prototype(String encoded) throws Exception {ItemStack[] items=decode(encoded);require(items.length==1 && !empty(items[0]),"Не збережено предмет замовлення.");return items[0];}
    static ItemStack[] take(ItemStack[] original,ItemStack item,long count) {
        require(count>0 && count<=1_000_000,"Неправильна кількість.");ItemStack[] result=copy(original);long left=count;
        for(int i=0;i<result.length && left>0;i++)if(result[i]!=null && result[i].isSimilar(item)){
            int n=(int)Math.min(left,result[i].getAmount());left-=n;result[i].setAmount(result[i].getAmount()-n);if(result[i].getAmount()==0)result[i]=null;
        }
        require(left==0,"Недостатньо предметів із потрібними властивостями.");return result;
    }
    static ItemStack[] add(ItemStack[] original,ItemStack item,long count) {
        require(count>0 && count<=1_000_000,"Неправильна кількість.");ItemStack[] result=copy(original);long left=count;
        for(int pass=0;pass<2;pass++)for(int i=0;i<result.length && left>0;i++) {
            if(pass==0 && result[i]!=null && result[i].isSimilar(item)){
                int n=(int)Math.min(left,Math.max(0,result[i].getMaxStackSize()-result[i].getAmount()));result[i].setAmount(result[i].getAmount()+n);left-=n;
            } else if(pass==1 && result[i]==null){int n=(int)Math.min(left,item.getMaxStackSize());result[i]=item.clone();result[i].setAmount(n);left-=n;}
        }
        require(left==0,"Звільніть місце для всіх предметів. Передачу ще не розпочато.");return result;
    }
    static ItemStack[] removeSlots(ItemStack[] original,Map<Integer,ItemStack> selected) {
        ItemStack[] result=copy(original);
        for(var e:selected.entrySet()){int slot=e.getKey();require(slot>=0 && slot<result.length && Objects.equals(result[slot],e.getValue()),"Предмети пропозиції змінилися. Виберіть їх знову.");result[slot]=null;}return result;
    }
    public CommerceService.InventoryChange change(Player p,ItemStack[] before,ItemStack[] after) throws IOException {
        require(empty(p.getItemOnCursor()),"Спершу звільніть предмет на курсорі.");String old=GoldInventoryBridge.encode(before),next=GoldInventoryBridge.encode(after);
        require(old.length()<=4_000_000 && next.length()<=4_000_000,"Інвентар містить забагато даних для безпечної передачі.");return new CommerceService.InventoryChange(p.getUniqueId().toString(),old,next);
    }
    public void apply(String id) throws Exception {
        var rows=commerce.db.query("SELECT * FROM item_participants WHERE op=? ORDER BY person",id);
        // Validate every participant before applying even the first inventory.
        for(var row:rows){Player p=Bukkit.getPlayer(UUID.fromString(string(row,"person")));require(p!=null && p.isOnline(),"Учасник має бути на сервері для завершення.");require(empty(p.getItemOnCursor()) && same(p.getInventory().getStorageContents(),decode(string(row,"before_items"))),"Інвентар змінився після підготовки. Потрібна перевірка адміністратора.");}
        try {
            for(var row:rows){Player p=Bukkit.getPlayer(UUID.fromString(string(row,"person")));p.getInventory().setStorageContents(decode(string(row,"after_items")));p.saveData();}
            commerce.finish(id,true);
        }catch(Exception ex){plugin.getLogger().severe("Незавершена передача предметів "+id+"; потрібне /commerce inspect та recover/abort.");throw ex;}
    }
    /** Explicit admin action: reconcile each side independently, then complete forwards once. */
    public void recover(String id,boolean forward) throws Exception {
        var op=commerce.db.one("SELECT * FROM item_ops WHERE id=?",id);require(op!=null && "PREPARED".equals(string(op,"status")),"Немає незавершеної операції.");
        var rows=commerce.db.query("SELECT * FROM item_participants WHERE op=? ORDER BY person",id);
        Map<Player,ItemStack[]> writes=new LinkedHashMap<>();
        for(var row:rows){Player p=Bukkit.getPlayer(UUID.fromString(string(row,"person")));require(p!=null && empty(p.getItemOnCursor()),"Усі учасники мають бути онлайн із порожнім курсором.");
            ItemStack[] before=decode(string(row,"before_items")),after=decode(string(row,"after_items")),current=p.getInventory().getStorageContents();
            boolean old=same(current,before),done=same(current,after);require(old || forward && done,"Інвентар "+p.getName()+" не відповідає потрібному знімку. Спершу перевірте його вручну.");
            if(forward && !done)writes.put(p,after);
        }
        for(var e:writes.entrySet()){e.getKey().getInventory().setStorageContents(e.getValue());e.getKey().saveData();}
        commerce.finish(id,forward);
    }
    private boolean pending(Player p) {
        try{CommerceService.inventoryAvailable(commerce.db,p.getUniqueId().toString());return false;}catch(IllegalArgumentException ex){p.sendMessage("§cНезавершена передача: зверніться до адміністратора (/commerce pending).");return true;}catch(Exception ex){plugin.failure("Перевірка передачі",ex);return true;}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p && pending(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && pending(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p && pending(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void entity(PlayerInteractEntityEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void book(PlayerEditBookEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void wear(PlayerItemDamageEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent e){
        String command=e.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);
        if(!Set.of("commerce","empirecraftfeudal:commerce").contains(command) && pending(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void consume(PlayerItemConsumeEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void swap(PlayerSwapHandItemsEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void place(BlockPlaceEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void mine(BlockBreakEvent e){if(pending(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p && pending(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void attack(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player p && pending(p))e.setCancelled(true);
        if(e.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p && pending(p))e.setCancelled(true);
    }
}
