package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Inventory forms with version-bound actions. Chat is only a text field; all mutations run on main. */
public final class ContractMenus implements Listener,CommandExecutor,TabCompleter {
    final FeudalPlugin plugin;final ContractService contracts;final FeudalService s;final TownyBridge towny;final CommerceService commerce;final ItemTransactions items;
    @FunctionalInterface interface Action {void run(Player p)throws Exception;}
    @FunctionalInterface interface Input {void run(Player p,String text)throws Exception;}
    record Prompt(long expires,Input action){}
    final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();
    static final class View implements InventoryHolder {
        final UUID owner;final Inventory inventory;final Map<Integer,Action> actions=new HashMap<>();
        View(Player p,String title){owner=p.getUniqueId();inventory=Bukkit.createInventory(this,54,title);}
        public Inventory getInventory(){return inventory;}
    }
    public ContractMenus(FeudalPlugin plugin,ContractService contracts,TownyBridge towny,CommerceService commerce,ItemTransactions items)throws Exception{
        this.plugin=plugin;this.contracts=contracts;s=contracts.s;this.towny=towny;this.commerce=commerce;this.items=items;
        s.db.execute("CREATE TABLE IF NOT EXISTS vc_book(contract BIGINT,rev INT,person VARCHAR(36),PRIMARY KEY(contract,rev,person))");
    }
    Actor actor(Player p){return new Actor(p.getUniqueId().toString(),false);}
    static String label(String key){return switch(key){
        case "DRAFT"->LocalText.tr("Проєкт");case "ACTIVE"->LocalText.tr("Чинний");case "NOTICE"->LocalText.tr("Попередження про вихід");case "EXPIRED"->LocalText.tr("Строк завершено");case "ENDED"->LocalText.tr("Припинено");case "BREACHED"->LocalText.tr("Припинено через порушення");case "REJECTED"->LocalText.tr("Проєкт відхилено");case "ARCHIVED"->LocalText.tr("Володіння архівовано");case "AGREED"->LocalText.tr("Погоджено");case "SIGNED"->LocalText.tr("Підписано");
        case "CREATE"->LocalText.tr("Створено проєкт");case "EDIT"->LocalText.tr("Змінено умови");case "AMEND"->LocalText.tr("Нова редакція");case "REJECT"->LocalText.tr("Редакцію відхилено");case "VOTE"->LocalText.tr("Голос ради");case "AGREE"->LocalText.tr("Погодження редакції");case "SIGN"->LocalText.tr("Підпис");case "PAY"->LocalText.tr("Сплачено");case "AID"->LocalText.tr("Одноразова допомога");case "OVERDUE"->LocalText.tr("Прострочення");case "SUPPLY"->LocalText.tr("Поставка ресурсів");case "PERMIT"->LocalText.tr("Дозвіл сюзерена");case "EXIT_AGREE"->LocalText.tr("Згода на припинення");default->key;};}
    void say(Player p,String text){plugin.message(p,text);}
    void run(Player p,Action action){
 try(var languageScope=LocalText.use(p)){
try{require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає права користуватися договорами."));towny.sync();contracts.tick();action.run(p);}catch(IllegalArgumentException e){say(p,"§c"+e.getMessage());}catch(Exception e){plugin.failure(LocalText.tr("Васальний договір"),e);say(p,LocalText.tr("§cДію не завершено; подробиці в журналі сервера."));}
 }
}
    void button(View v,int slot,Material material,String title,Action action,String... lore){
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName("§e"+title);var lines=new ArrayList<String>();
        for(String line:lore){while(line.length()>48){lines.add("§7"+line.substring(0,48));line=line.substring(48);}lines.add("§7"+line);}meta.setLore(lines);item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);
    }
    void prompt(Player p,String label,Input input){
 try(var languageScope=LocalText.use(p)){
p.closeInventory();prompts.put(p.getUniqueId(),new Prompt(System.currentTimeMillis()+180000,input));say(p,label+LocalText.tr(". Введіть у чат; «скасувати» — вихід; «-» — порожній текст."));
 }
}
    String estate(String value)throws Exception{return s.authority(s.resolve(value).id());}
    public void open(Player p)throws Exception{prompts.remove(p.getUniqueId());home(p,0);}
    void home(Player p,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var v=new View(p,LocalText.tr("Канцелярія · васальні договори"));var rows=contracts.list();int start=page*36;
        for(int i=start;i<Math.min(rows.size(),start+36);i++){
            var c=rows.get(i);long id=number(c,"id");button(v,i-start,Material.WRITABLE_BOOK,LocalText.tr("Договір №")+id,q->show(q,id),s.estate(string(c,"child")).name()+" → "+s.estate(string(c,"parent")).name(),LocalText.tr("Статус: ")+label(string(c,"status")),LocalText.tr("Редакція ")+number(c,"revision"));
        }
        button(v,45,Material.EMERALD,LocalText.tr("Створити договір"),q->choose(q,true,null,0),LocalText.tr("Оберіть володіння васала й сюзерена."));
        if(page>0)button(v,48,Material.ARROW,LocalText.tr("Назад"),q->home(q,page-1));if(start+36<rows.size())button(v,50,Material.ARROW,LocalText.tr("Далі"),q->home(q,page+1));p.openInventory(v.inventory);

 }
}
    void choose(Player p,boolean child,String selected,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var rows=s.estates().stream().filter(Model.Estate::active).toList();var v=new View(p,child?LocalText.tr("Володіння васала"):LocalText.tr("Володіння сюзерена"));int start=page*36;
        for(int i=start;i<Math.min(rows.size(),start+36);i++){var e=rows.get(i);button(v,i-start,Material.FILLED_MAP,e.name(),q->{if(child)choose(q,false,e.id(),0);else show(q,contracts.create(actor(q),s.authority(selected),s.authority(e.id())));},e.id(),plugin.politicalLabel(e));}
        if(page>0)button(v,48,Material.ARROW,LocalText.tr("Назад"),q->choose(q,child,selected,page-1));if(start+36<rows.size())button(v,50,Material.ARROW,LocalText.tr("Далі"),q->choose(q,child,selected,page+1));p.openInventory(v.inventory);

 }
}
    void sameRevision(Map<String,Object> shown,int rev)throws Exception{var live=contracts.get(number(shown,"id"));require(contracts.rev(live)==rev && number(live,"active_rev")==number(shown,"active_rev"),LocalText.tr("Редакція змінилася. Перегляньте актуальні умови."));}
    void show(Player p,long id)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var c=contracts.get(id);int rev=contracts.rev(c);var v=new View(p,LocalText.tr("Договір №")+id+LocalText.tr(" · ред. ")+rev);
        button(v,4,Material.PAPER,s.estate(string(c,"child")).name()+" → "+s.estate(string(c,"parent")).name(),null,LocalText.tr("Статус: ")+label(string(c,"status")),LocalText.tr("Чинна редакція: ")+number(c,"active_rev"),LocalText.tr("Наступна данина: ")+ContractBook.when(number(c,"next_due")),LocalText.tr("Наступна субсидія: ")+ContractBook.when(number(c,"sub_due")),LocalText.tr("Закінчення: ")+ContractBook.when(number(c,"ends")));
        button(v,10,Material.WRITABLE_BOOK,LocalText.tr("Усі умови / редагування"),q->editMenu(q,id,rev,0),LocalText.tr("Кожна зміна скидає погодження, підписи й голоси."));
        button(v,11,Material.BOOK,LocalText.tr("Прочитати всю редакцію"),q->q.openBook(book(id,rev)),LocalText.tr("Усі вибрані умови, включно з рольовими."));
        button(v,12,Material.LIME_DYE,LocalText.tr("Погодити цю редакцію"),q->{contracts.agree(actor(q),id,rev);show(q,id);},LocalText.tr("Після читання всіх умов. Це ще не підпис."));
        button(v,13,Material.FEATHER,LocalText.tr("Переглянути й підписати"),q->systemView(q,id,1),LocalText.tr("Усі механічні сторінки та персональний токен. Потрібні обидва погодження та рішення рад."));
        button(v,14,Material.RED_DYE,LocalText.tr("Відхилити проєкт"),q->{contracts.reject(actor(q),id,rev);show(q,id);},LocalText.tr("Чинна редакція, якщо є, зберігається."));
        button(v,15,Material.ANVIL,LocalText.tr("Нова редакція / продовження"),q->{contracts.amend(actor(q),id,rev);show(q,id);},LocalText.tr("Умови чинні до двох нових підписів."));
        button(v,19,Material.LECTERN,LocalText.tr("Голосування ради"),q->council(q,id,rev),LocalText.tr("Більшість чинного складу за точну редакцію."));
        button(v,20,Material.CHEST,LocalText.tr("Борги та постачання"),q->debts(q,id,0),LocalText.tr("Ресурси: передача зі свого інвентарю."));
        button(v,21,Material.BARREL,LocalText.tr("Отримати поставки"),q->warehouse(q,id,0));
        button(v,22,Material.CLOCK,LocalText.tr("Історія обох сторін"),q->history(q,id,0));
        button(v,23,Material.WRITTEN_BOOK,LocalText.tr("Копія чинного договору"),q->{require(number(c,"active_rev")>0,LocalText.tr("Договір ще не підписаний."));give(q,book(id,(int)number(c,"active_rev")));},LocalText.tr("Книга не надає прав і не змінює чинність."));
        button(v,28,Material.OAK_DOOR,LocalText.tr("Вихід за згодою"),q->{sameRevision(c,rev);contracts.exit(actor(q),id,false);show(q,id);},LocalText.tr("Потрібне підтвердження обох правителів."));
        button(v,29,Material.IRON_DOOR,LocalText.tr("Односторонній вихід"),q->{sameRevision(c,rev);contracts.exit(actor(q),id,true);show(q,id);},LocalText.tr("Лише якщо дозволено; викуп зі скарбниці й строк попередження."));
        for(int i=0;i<4;i++){String action=List.of("alliance","diplomacy","vassals","war").get(i);button(v,32+i,Material.NAME_TAG,LocalText.tr("Погодження: ")+LocalText.tr(ContractTerms.LABELS.get(action)),q->{sameRevision(c,rev);contracts.permit(actor(q),id,action);show(q,id);},LocalText.tr("Сюзерен дає дозвіл на одну інтегровану дію."));}
        int slot=38;for(var a:s.db.query("SELECT * FROM vc_accept WHERE contract=? AND rev=?",id,rev))button(v,slot++,Material.PLAYER_HEAD,s.estate(string(a,"estate")).name(),null,LocalText.tr("Погодження: ")+label(string(a,"phase")),LocalText.tr("Правитель: ")+towny.playerName(string(a,"ruler")),LocalText.tr("Дійсність повноважень повторно перевіряється перед підписом."));
        button(v,49,Material.ARROW,LocalText.tr("До списку"),q->home(q,0));p.openInventory(v.inventory);

 }
}
    void editMenu(Player p,long id,int rev,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var c=contracts.get(id);require(contracts.rev(c)==rev,LocalText.tr("Редакція змінилася."));var t=contracts.terms(id,rev);var v=new View(p,LocalText.tr("Умови №")+id+LocalText.tr(" · ред. ")+rev);var fields=new ArrayList<>(ContractTerms.LABELS.keySet());
        for(int i=page*21;i<Math.min(fields.size(),page*21+21);i++){String key=fields.get(i);button(v,i-page*21,Material.PAPER,LocalText.tr(ContractTerms.LABELS.get(key)),q->{contracts.party(actor(q),contracts.get(id));prompt(q,LocalText.tr(ContractTerms.LABELS.get(key))+" ["+hint(key)+"]",(r,value)->{validateItemField(key,value);contracts.edit(actor(r),id,rev,key,(Set.of("code","subcode").contains(key) && value.isEmpty())?"-":value);show(r,id);});},LocalText.tr("Значення: ")+t.get(key),hint(key));}
        if(page>0)button(v,45,Material.ARROW,LocalText.tr("Назад"),q->editMenu(q,id,rev,page-1));if((page+1)*21<fields.size())button(v,53,Material.ARROW,LocalText.tr("Далі"),q->editMenu(q,id,rev,page+1));button(v,49,Material.ARROW,LocalText.tr("До договору"),q->show(q,id));p.openInventory(v.inventory);

 }
}
    String hint(String key){return switch(key){case "renew","defend","unilateral"->"true / false";case "alliance","diplomacy","war","vassals"->LocalText.tr("ALLOW — дозволено; CONSENT — погодження; DENY — заборона");case "military"->"NONE / DEFENSIVE / ALL";case "breach"->LocalText.tr("RECORD — запис; TERMINATE — розірвання");case "extra","war_money","war_gear"->LocalText.tr("Рольовий текст; без автоматичного виконання");default->LocalText.tr("Значення вводиться в чаті; дні — реальні");};}
    void validateItemField(String key,String value){if(!Set.of("resources","provisions").contains(key))return;var t=new ContractTerms().with(key,value);for(String m:t.items(key).keySet()){Material material=Material.matchMaterial(m);require(material!=null && material.isItem() && !material.isAir(),LocalText.tr("Невідомий предмет: ")+m);}}
    void council(Player p,long id,int rev)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var c=contracts.get(id);var v=new View(p,LocalText.tr("Ради · договір №")+id);int slot=10;
        for(String side:List.of("child","parent")){String estate=string(c,side);boolean needed=contracts.council(estate);button(v,slot,Material.LECTERN,s.estate(estate).name(),null,needed?LocalText.tr("Потрібна більшість виборців"):LocalText.tr("Рішення ради не потрібне"),needed && contracts.mandate(id,rev,estate)?LocalText.tr("Редакцію схвалено"):LocalText.tr("Погодження перевіряється перед підписом"));
            if(needed){button(v,slot+9,Material.LIME_DYE,LocalText.tr("Голос ЗА"),q->{contracts.vote(actor(q),id,rev,estate,true);council(q,id,rev);});button(v,slot+18,Material.RED_DYE,LocalText.tr("Голос ПРОТИ"),q->{contracts.vote(actor(q),id,rev,estate,false);council(q,id,rev);});}slot+=4;
        }button(v,49,Material.ARROW,LocalText.tr("До договору"),q->show(q,id));p.openInventory(v.inventory);

 }
}
    void debts(Player p,long id,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var v=new View(p,LocalText.tr("Борги / постачання №")+id);var rows=contracts.debts(id);
        for(int i=page*36;i<Math.min(rows.size(),page*36+36);i++){var o=rows.get(i);String asset=string(o,"asset");long owed=number(o,"amount")-number(o,"paid"),oid=number(o,"id");boolean item=asset.startsWith("item:");
            button(v,i-page*36,item?Material.CHEST:Material.GOLD_INGOT,LocalText.tr("Зобов’язання №")+oid,item?q->{String material=asset.substring(5);var prototype=new ItemStack(Objects.requireNonNull(Material.matchMaterial(material)));prompt(q,LocalText.tr("Скільки передати ")+material+" (1–"+owed+")",(r,text)->{long n=Long.parseLong(text);var before=r.getInventory().getStorageContents();var after=ItemTransactions.take(before,prototype,n);String op=contracts.supply(actor(r),oid,n,commerce,items.change(r,before,after));items.apply(op);debts(r,id,page);});}:null,
                s.estate(string(o,"source")).name()+" → "+s.estate(string(o,"target")).name(),asset+": "+(item?owed:format(owed)),LocalText.tr("Строк: ")+ContractBook.when(number(o,"due")),item?LocalText.tr("Натисніть: ввести кількість звичайних предметів без особливих властивостей"):LocalText.tr("Автоматично зі скарбниці, щойно є вся сума"));
        }
        pager(v,page,rows.size(),q->debts(q,id,page-1),q->debts(q,id,page+1));button(v,49,Material.ARROW,LocalText.tr("До договору"),q->show(q,id));p.openInventory(v.inventory);

 }
}
    void warehouse(Player p,long id,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){

        var c=contracts.get(id);String estate=contracts.party(actor(p),c);var rows=s.db.query("SELECT * FROM vc_goods WHERE estate=? AND quantity>0 ORDER BY material",estate);var v=new View(p,LocalText.tr("Склад поставок володіння"));
        for(int i=page*36;i<Math.min(rows.size(),page*36+36);i++){var row=rows.get(i);String m=string(row,"material");long n=Math.min(64,number(row,"quantity"));button(v,i-page*36,Material.CHEST,m+" × "+number(row,"quantity"),q->{var prototype=new ItemStack(Objects.requireNonNull(Material.matchMaterial(m)));prompt(q,LocalText.tr("Скільки отримати ")+m+LocalText.tr(" (на складі ")+number(row,"quantity")+")",(r,text)->{long amount=Long.parseLong(text);var before=r.getInventory().getStorageContents();var after=ItemTransactions.add(before,prototype,amount);String op=contracts.collect(actor(r),estate,m,amount,commerce,items.change(r,before,after));items.apply(op);warehouse(r,id,page);});},LocalText.tr("Натисніть: вибрати кількість."));}
        pager(v,page,rows.size(),q->warehouse(q,id,page-1),q->warehouse(q,id,page+1));button(v,49,Material.ARROW,LocalText.tr("До договору"),q->show(q,id));p.openInventory(v.inventory);

 }
}
    void history(Player p,long id,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){
var v=new View(p,LocalText.tr("Історія №")+id+" · "+(page+1));var rows=contracts.history(id,page);int i=0;for(var row:rows)button(v,i++,Material.PAPER,label(string(row,"event")),null,ContractBook.when(number(row,"at_time")),string(row,"actor"),string(row,"detail"));if(page>0)button(v,45,Material.ARROW,LocalText.tr("Новіші"),q->history(q,id,page-1));if(rows.size()==30)button(v,53,Material.ARROW,LocalText.tr("Давніші"),q->history(q,id,page+1));button(v,49,Material.ARROW,LocalText.tr("До договору"),q->show(q,id));p.openInventory(v.inventory);
 }
}
    void pager(View v,int page,int total,Action back,Action next){if(page>0)button(v,45,Material.ARROW,LocalText.tr("Назад"),back);if((page+1)*36<total)button(v,53,Material.ARROW,LocalText.tr("Далі"),next);}
    ItemStack book(long id,int rev)throws Exception{
        var c=contracts.get(id);var r=contracts.revision(id,rev);String child=string(r,"child_name"),parent=string(r,"parent_name");
        var pages=ContractBook.pages(ContractBook.chapters(id,rev,r,contracts.terms(id,rev),child==null?s.estate(string(c,"child")).name():child,parent==null?s.estate(string(c,"parent")).name():parent,towny::playerName));
        var book=new ItemStack(Material.WRITTEN_BOOK);var meta=(BookMeta)book.getItemMeta();meta.setTitle(LocalText.tr("Васальний договір №")+id);meta.setAuthor(LocalText.tr("Канцелярія EmpireCraft"));meta.setPages(pages);
        var check=new TextComponent(LocalText.tr("Перевірити договір"));check.setColor(net.md_5.bungee.api.ChatColor.DARK_BLUE);check.setUnderlined(true);check.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/t treaty show "+id));meta.spigot().addPage(new BaseComponent[]{check});book.setItemMeta(meta);return book;
    }
    void give(Player p,ItemStack book)throws Exception{
 try(var languageScope=LocalText.use(p)){
CommerceService.inventoryAvailable(s.db,p.getUniqueId().toString());require(s.db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",p.getUniqueId().toString())==null,LocalText.tr("Спершу завершіть операцію золотого банку."));require(p.getInventory().firstEmpty()>=0,LocalText.tr("Звільніть місце для книги. Копію можна отримати в меню договору."));p.getInventory().addItem(book);p.saveData();
 }
}
    void deliver(Player p)throws Exception{
 try(var languageScope=LocalText.use(p)){

        String person=p.getUniqueId().toString();if(s.db.one("SELECT p.op FROM item_participants p JOIN item_ops o ON o.id=p.op WHERE p.person=? AND o.status='PREPARED'",person)!=null || s.db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",person)!=null)return;for(var r:s.db.query("SELECT * FROM vc_revision WHERE signed_at>0 AND (child_sign=? OR parent_sign=?) ORDER BY contract,rev",person,person)){
            long id=number(r,"contract");int rev=(int)number(r,"rev");if(s.db.one("SELECT person FROM vc_book WHERE contract=? AND rev=? AND person=?",id,rev,person)!=null)continue;
            if(p.getInventory().firstEmpty()<0){say(p,LocalText.tr("Для книги договору №")+id+LocalText.tr(" звільніть місце й відкрийте /t treaty claim."));break;}give(p,book(id,rev));s.db.execute("INSERT INTO vc_book VALUES(?,?,?)",id,rev,person);
        }

 }
}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void nativeAlly(com.palmergames.bukkit.towny.event.nation.NationPreAddAllyEvent e){
        try{s.db.transaction(()->{contracts.autonomy(TownyBridge.nationId(e.getNation()),"alliance");return null;});}catch(Exception ex){e.setCancelled(true);e.setCancelMessage(ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void nativeAcceptAlly(com.palmergames.bukkit.towny.event.NationAcceptAllyRequestEvent e){
        try{s.db.transaction(()->{contracts.autonomy(TownyBridge.nationId(e.getInvitedNation()),"alliance");return null;});}catch(Exception ex){e.setCancelled(true);e.setCancelMessage(ex.getMessage());}
    }
    @EventHandler public void townMenu(InventoryOpenEvent e){
        if(e.getInventory().getHolder()==null || !e.getInventory().getHolder().getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View") || e.getInventory().getSize()!=54 || e.getInventory().getItem(47)!=null)return;
        var item=new ItemStack(Material.WRITABLE_BOOK);var meta=item.getItemMeta();meta.setDisplayName(LocalText.tr("§6Конструктор васального договору"));meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"contract_menu"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);e.getInventory().setItem(47,item);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void townEntry(InventoryClickEvent e){
        if(e.getRawSlot()!=47 || e.getView().getTopInventory().getHolder()==null || !e.getView().getTopInventory().getHolder().getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View"))return;
        var item=e.getCurrentItem();if(item==null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"contract_menu"),org.bukkit.persistence.PersistentDataType.BYTE))return;
        e.setCancelled(true);if(e.getWhoClicked() instanceof Player p)Bukkit.getScheduler().runTask(plugin,()->run(p,this::open));
    }
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(plugin,()->run(e.getPlayer(),this::deliver));}
    @EventHandler public void quit(PlayerQuitEvent e){prompts.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){var prompt=prompts.remove(e.getPlayer().getUniqueId());if(prompt==null)return;e.setCancelled(true);String input=e.getMessage();Bukkit.getScheduler().runTask(plugin,()->run(e.getPlayer(),p->{require(prompt.expires()>=System.currentTimeMillis(),LocalText.tr("Строк введення минув; відкрийте поле знову."));if(LocalText.cancel(input))return;prompt.action().run(p,input.equals("-")?"":input);}));}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p && v.owner.equals(p.getUniqueId()) && e.getRawSlot()>=0 && e.getRawSlot()<54){var a=v.actions.get(e.getRawSlot());if(a!=null)run(p,a);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    void systemView(Player p,long id,int page)throws Exception{
 try(var languageScope=LocalText.use(p)){
require(s.cityMarkets!=null,LocalText.tr("Системний перегляд недоступний."));p.closeInventory();plugin.cityMarketCommands().extensions.showDocument(p,s.cityMarkets.documents.view(actor(p),s.cityMarkets.documents.vassalBuilder(id),page),"/t treaty",""+id);
 }
}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
 try(var languageScope=LocalText.use(sender)){
if(!(sender instanceof Player p)){sender.sendMessage(LocalText.tr("Договори доступні гравцю в грі."));return true;}run(p,q->{
        String action=args.length==0?"menu":args[0];switch(action){
            case "view"->{require(args.length>=2,"/t treaty view ID [page]");systemView(q,Long.parseLong(args[1]),args.length>2?Integer.parseInt(args[2]):1);}
            case "accept"->{require(args.length==4,"/t treaty accept ID VERSION TOKEN");say(q,LocalText.tr("Підпис збережено: ")+s.cityMarkets.documents.acceptBuilder(actor(q),Long.parseLong(args[1]),Long.parseLong(args[2]),args[3]));for(Player online:Bukkit.getOnlinePlayers())deliver(online);}
            case "reject"->{long id=Long.parseLong(args[1]);contracts.reject(actor(q),id,contracts.rev(contracts.get(id)));}
            case "menu"->open(q);case "show"->{require(args.length==2,LocalText.tr("/t treaty show <номер>"));show(q,Long.parseLong(args[1]));}case "claim"->deliver(q);
            case "create"->{require(args.length==3,LocalText.tr("/t treaty create <васал> <сюзерен>"));show(q,contracts.create(actor(q),estate(args[1]),estate(args[2])));}
            case "permit"->{require(args.length==3,LocalText.tr("/t treaty permit <номер> alliance|diplomacy|vassals|war"));contracts.permit(actor(q),Long.parseLong(args[1]),args[2]);}
            default->say(q,LocalText.tr("/t treaty — конструктор; /t treaty show <номер>; /t treaty claim — отримати книги."));
        }});return true;
 }
}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){
 try(var languageScope=LocalText.use(s)){
return a.length==1?List.of("menu","show","create","claim","permit"):List.of();
 }
}
    public void shutdown(){prompts.clear();for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();}
}
