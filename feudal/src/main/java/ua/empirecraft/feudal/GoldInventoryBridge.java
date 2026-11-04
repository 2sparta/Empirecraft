package ua.empirecraft.feudal;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.*;
import java.io.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Inventory and SQL are separate stores. Persist intent, save inventory, then settle SQL once. */
public final class GoldInventoryBridge {
    private final CurrencyService ledger;
    public GoldInventoryBridge(CurrencyService ledger){this.ledger=ledger;}
    static ItemStack[] plan(ItemStack[] original,boolean deposit,int amount) {
        require(amount>0 && amount<=2304,LocalText.tr("Кількість злитків: від 1 до 2304."));
        ItemStack[] result=new ItemStack[original.length];
        for(int i=0;i<result.length;i++)if(original[i]!=null && original[i].getType()!=Material.AIR)result[i]=original[i].clone();
        int left=amount;
        for(int i=0;i<result.length && left>0;i++){
            ItemStack item=result[i];
            boolean plain=item!=null && item.getType()==Material.GOLD_INGOT && !item.hasItemMeta();
            if(deposit && plain){int n=Math.min(left,item.getAmount());left-=n;item.setAmount(item.getAmount()-n);if(item.getAmount()==0)result[i]=null;}
            if(!deposit && (item==null || plain)){
                int room=item==null?64:Math.max(0,item.getMaxStackSize()-item.getAmount());int n=Math.min(left,room);
                if(n>0){if(item==null)result[i]=new ItemStack(Material.GOLD_INGOT,n);else item.setAmount(item.getAmount()+n);left-=n;}
            }
        }
        require(left==0,deposit?LocalText.tr("Недостатньо звичайних золотих злитків у головному інвентарі."):LocalText.tr("Звільніть місце в головному інвентарі для всіх злитків."));return result;
    }
    static String encode(ItemStack[] items) throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new BukkitObjectOutputStream(bytes)){out.writeInt(items.length);for(ItemStack item:items)out.writeObject(item);}
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    public static boolean matches(Player player,String snapshot) throws IOException,ClassNotFoundException {
 try(var languageScope=LocalText.use(player)){

        try(var in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(snapshot)))){
            int size=in.readInt();require(size==36,LocalText.tr("Невірний розмір збереженого інвентарю."));
            ItemStack[] stored=new ItemStack[size];for(int i=0;i<size;i++)stored[i]=(ItemStack)in.readObject();
            ItemStack[] current=player.getInventory().getStorageContents();
            // Bukkit may represent an empty slot as null or AIR after a save/load cycle.
            for(int i=0;i<size;i++){
                ItemStack a=stored[i],b=current[i];boolean emptyA=a==null || a.getType()==Material.AIR,emptyB=b==null || b.getType()==Material.AIR;
                if(emptyA && emptyB)continue;if(emptyA!=emptyB || !a.equals(b))return false;
            }return true;
        }

 }
}
    public void exchange(Player player,boolean deposit,int amount,String target) throws Exception {
 try(var languageScope=LocalText.use(player)){

        require(ItemTransactions.empty(player.getItemOnCursor()),LocalText.tr("Спершу звільніть предмет на курсорі."));
        Actor actor=new Actor(player.getUniqueId().toString(),player.hasPermission("empirecraft.feudal.admin"));
        ItemStack[] before=player.getInventory().getStorageContents();ItemStack[] after=plan(before,deposit,amount);
        String id=ledger.prepareGold(actor,deposit?"IN":"OUT",target,amount,encode(before),encode(after));
        try {
            player.getInventory().setStorageContents(after);player.saveData();
            ledger.finishGold(actor,id,true);
        }catch(Exception ex){
            // Neither repeat item delivery nor guess whether saveData reached disk.
            player.sendMessage(LocalText.tr("§cОперація зі злитками потребує перевірки адміністратора: ")+id+LocalText.tr(". Не повторюйте видачу вручну."));throw ex;
        }

 }
}
    public void collect(Player player,Integer requested)throws Exception{
 try(var languageScope=LocalText.use(player)){

        String person=player.getUniqueId().toString();int available=ledger.payable(person).min(java.math.BigDecimal.valueOf(2304)).intValue();
        int space=0;for(var item:player.getInventory().getStorageContents())if(item==null || item.getType()==Material.AIR)space+=64;else if(item.getType()==Material.GOLD_INGOT && !item.hasItemMeta())space+=Math.max(0,64-item.getAmount());
        int n=requested==null?Math.min(available,space):requested;require(n>0,LocalText.tr("Немає цілих злитків до видачі або місця в інвентарі. Дробовий залишок зберігається до накопичення цілого злитка."));
        exchange(player,false,n,"d:"+person);

 }
}
    public void purchase(Player player,int amount,String code,java.math.BigDecimal minimum,String stamp)throws Exception{
 try(var languageScope=LocalText.use(player)){

        require(ItemTransactions.empty(player.getItemOnCursor()),LocalText.tr("Спершу звільніть предмет на курсорі."));
        Actor actor=new Actor(player.getUniqueId().toString(),player.hasPermission("empirecraft.feudal.admin"));
        ledger.goldCurrency(code);ItemStack[] before=player.getInventory().getStorageContents(),after=plan(before,true,amount);
        String id=ledger.prepareGold(actor,"IN","b:"+code,amount,encode(before),encode(after));
        // An uncertain inventory write remains PREPARED; admin reconciliation returns ingots as a payable.
        player.getInventory().setStorageContents(after);player.saveData();
        try{ledger.exchangePhysical(actor,id,minimum,stamp);}
        catch(Exception ex){ledger.finishGold(actor,id,true);throw new IllegalArgumentException(LocalText.tr("Купівлю не виконано; внесені злитки збережено до видачі: /cu collect. ")+ex.getMessage());}

 }
}

}
