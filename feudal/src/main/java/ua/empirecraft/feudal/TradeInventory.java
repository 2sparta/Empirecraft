package ua.empirecraft.feudal;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;

/** Non-mutating inventory plans; all items must fit before either inventory changes. */
final class TradeInventory {
    static boolean empty(ItemStack x){return x==null||x.getType().isAir()||x.getAmount()<=0;}
    static ItemStack[] copy(ItemStack[] a){ItemStack[] b=new ItemStack[a.length];for(int i=0;i<a.length;i++)if(!empty(a[i]))b[i]=a[i].clone();return b;}
    static boolean same(ItemStack[] a,ItemStack[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(!(empty(a[i])&&empty(b[i]))&&(empty(a[i])||empty(b[i])||!a[i].equals(b[i])))return false;return true;}
    static boolean matches(ItemStack[] a,Map<Integer,ItemStack> offer){for(var e:offer.entrySet())if(e.getKey()<0||e.getKey()>=a.length||empty(e.getValue())||empty(a[e.getKey()])||!e.getValue().equals(a[e.getKey()]))return false;return true;}
    static ItemStack[] plan(ItemStack[] a,Map<Integer,ItemStack> outgoing,Collection<ItemStack> incoming,int max){
        require(a.length==36&&max>0,"Неправильний інвентар.");require(matches(a,outgoing),"Запропонований предмет змінився.");
        var b=copy(a);for(int slot:outgoing.keySet())b[slot]=null;
        for(var source:incoming){require(!empty(source),"Порожня пропозиція.");int left=source.getAmount(),limit=Math.min(max,source.getMaxStackSize());require(limit>0,"Неправильний розмір стосу.");
            for(int i=0;i<b.length&&left>0;i++)if(!empty(b[i])&&b[i].isSimilar(source)){int n=Math.min(left,Math.max(0,limit-b[i].getAmount()));b[i].setAmount(b[i].getAmount()+n);left-=n;}
            for(int i=0;i<b.length&&left>0;i++)if(empty(b[i])){int n=Math.min(left,limit);b[i]=source.clone();b[i].setAmount(n);left-=n;}
            require(left==0,"Не вистачає місця для всіх предметів. Звільніть інвентар і повторіть обмін.");
        }return b;
    }
    static String encode(ItemStack[] items) throws IOException,ClassNotFoundException {String data=GoldInventoryBridge.encode(items);require(same(items,decode(data)),"Не вдалося точно зберегти дані предмета; операцію скасовано.");return data;}
    static ItemStack[] decode(String data) throws IOException,ClassNotFoundException {try(var in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))){int n=in.readInt();require(n==36,"Неправильний розмір знімка.");var items=new ItemStack[n];for(int i=0;i<n;i++)items[i]=(ItemStack)in.readObject();return items;}}
}
