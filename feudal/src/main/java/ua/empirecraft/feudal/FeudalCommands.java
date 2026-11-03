package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

public final class FeudalCommands implements CommandExecutor,TabCompleter,Listener {
    private final FeudalPlugin plugin;private final FeudalService s;private final TownyBridge towny;private final VaultBridge vault;private final CurrencyCommands moneyCommands;
    private final DateTimeFormatter date=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Kyiv"));
    public FeudalCommands(FeudalPlugin plugin,FeudalService service,TownyBridge towny,VaultBridge vault,CurrencyCommands moneyCommands){this.plugin=plugin;this.s=service;this.towny=towny;this.vault=vault;this.moneyCommands=moneyCommands;}
    static String[] prepend(String name,String[] args){String[] out=new String[args.length+1];out[0]=name;System.arraycopy(args,0,out,1,args.length);return out;}
    private Actor actor(CommandSender sender){return new Actor(sender instanceof Player p?p.getUniqueId().toString():"консоль",sender.hasPermission("empirecraft.feudal.admin"));}
    private Player player(CommandSender sender){require(sender instanceof Player,"Ця команда доступна гравцю в грі.");return (Player)sender;}
    private void say(CommandSender sender,String text){plugin.message(sender,text);}
    private String arg(String[] a,int i){require(a.length>i,"Недостатньо аргументів. Довідка: /t vassal help, /t title help або /cu help.");return a[i];}
    private String code(String[] a,int i){return arg(a,i).toUpperCase(Locale.ROOT);}
    private String selected(CommandSender sender,String[] a,int i) throws Exception {return a.length>i?s.resolve(a[i]).id():towny.defaultEstate(player(sender));}
    private String when(long value){return value==0?"безстроково":date.format(Instant.ofEpochMilli(value))+" (Київ)";}
    private String name(String id) throws Exception{return id==null?"незалежне":s.estate(id).name();}
    private String word(String input){return switch(input.toLowerCase(Locale.ROOT)){
        case "допомога"->"help";case "інфо"->"info";case "дерево"->"tree";case "список"->"list";case "меню"->"menu";
        case "створити"->"create";case "призначити"->"appoint";case "запропонувати"->"offer";case "проситися"->"petition";
        case "пропозиції"->"offers";case "прийняти"->"accept";case "відхилити","скасувати","cancel","reject"->"reject";
        case "договір","terms"->"contract";case "звільнити"->"release";case "незалежність"->"independence";
        case "сюзерен"->"liege";case "історія"->"history";case "данина"->"tribute";case "грамота"->"charter";
        case "перевірка"->"preview";case "заснувати"->"found";case "видати"->"grant";case "відкликати"->"revoke";
        case "баланс"->"balance";case "переказ"->"pay";case "поповнити"->"deposit";case "вивести"->"withdraw";
        case "скарбниця"->"treasury";case "валюта"->"currency";case "випуск"->"mint";case "погасити"->"redeem";
        case "адмін"->"admin";case "перевірити"->"check";case "імпорт"->"import";case "копія"->"backup";
        case "очікування"->"pending";case "встановити_сюзерена"->"setliege";case "обмін"->"vault";
        default->input.toLowerCase(Locale.ROOT);
    };}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] a){
        try {
            a=CommandLanguage.normalize("town",prepend(command.getName(),a));a=Arrays.copyOfRange(a,1,a.length);towny.sync();s.tick();
            String sub=a.length==0?"help":word(a[0]);
            switch(command.getName().toLowerCase(Locale.ROOT)) {
                case "vassal"->vassal(sender,sub,a);
                case "title"->title(sender,sub,a);
                
            }
        }catch(NumberFormatException ex){say(sender,"§cНомер сторінки та строк договору мають бути цілими числами.");}
        catch(IllegalArgumentException ex){say(sender,"§c"+ex.getMessage());}
        catch(Exception ex){plugin.failure("Команда "+command.getName(),ex);say(sender,"§cОперацію не завершено. Зверніться до адміністратора; подробиці є в журналі сервера.");}
        return true;
    }
    private void vassal(CommandSender sender,String sub,String[] a) throws Exception {
        Actor actor=actor(sender);
        switch(sub) {
            case "help" -> {
                say(sender,"§eВасальні володіння — усі назви з пробілами вводьте через підкреслення.");
                say(sender,"/t vassal info|tree|list|liege|contract|history [володіння]");
                say(sender,"/t vassal create <сюзерен> <герцогство|графство> <назва>");
                say(sender,"/t vassal appoint <володіння> <гравець>");
                say(sender,"/t vassal offer <васал> <сюзерен> <данина> <валюта> [висока|обмежена] [добровільно|оборона|повний] [дні:0|7–365]");
                say(sender,"/t vassal petition <васал> <сюзерен> <данина> <валюта> — таке саме двостороннє прошення");
                say(sender,"Договір без данини: сума 0, валюта -.");
                say(sender,"/t vassal offers; view ID [сторінка]; accept ID версія токен; reject ID; book auto|manual ID");
                say(sender,"/t vassal release|independence <васал>");
                say(sender,"/t vassal tribute status <володіння>; /t vassal tribute pay <володіння> <сума> <валюта>");
                say(sender,"/t vassal charter <сюзерен> <гравець>; /t vassal preview|found <назва>");
                say(sender,"/t vassal found confirm — завершення вже створеного міста; /t vassal menu");
                say(sender,"Українські відповідники: /васал допомога, інфо, дерево, створити, запропонувати, прийняти, грамота, заснувати.");
            }
            case "info","domain" -> info(sender,selected(sender,a,1));
            case "liege" -> {String id=selected(sender,a,1);say(sender,"Сюзерен "+name(id)+": "+name(s.parent(id)));}
            case "tree" -> {
                String id=selected(sender,a,1);List<String> lines=new ArrayList<>();tree(id,0,new HashSet<>(),lines);
                int page=a.length>2?Integer.parseInt(a[2]):1;require(page>0,"Сторінка має бути додатною.");
                say(sender,"Дерево "+name(id)+", сторінка "+page+"/"+Math.max(1,(lines.size()+19)/20));
                for(String line:lines.stream().skip((long)(page-1)*20).limit(20).toList())say(sender,line);
            }
            case "list" -> {String id=selected(sender,a,1);say(sender,"Прямі васали "+name(id)+":");for(Estate child:s.children(id))say(sender,plugin.politicalLabel(child)+" "+child.name()+" — "+towny.playerName(child.lord()));}
            case "create" -> {String id=s.create(actor,s.resolve(arg(a,1)).id(),Kind.parse(arg(a,2)),arg(a,3));say(sender,"Створено "+name(id)+". Для передачі посади: /t vassal appoint "+id+" <гравець>.");}
            case "appoint" -> proposal(sender,s.nominate(actor,s.resolve(arg(a,1)).id(),towny.residentId(arg(a,2))));
            case "offer","petition" -> {
                String child=s.resolve(arg(a,1)).id(),parent=s.resolve(arg(a,2)).id();
                if(sub.equals("petition"))s.control(actor,s.estate(child));
                proposal(sender,s.offer(actor,child,parent,money(arg(a,3)),(arg(a,4).equals("-")?"":code(a,4)),a.length>5?a[5]:"висока",a.length>6?a[6]:"оборона",a.length>7?Integer.parseInt(a[7]):0));
            }
            case "view" -> {require(s.cityMarkets!=null,"Ринки ще недоступні.");var doc=s.cityMarkets.documents.vassal(arg(a,1));var page=s.cityMarkets.documents.view(actor,doc,a.length>2?Integer.parseInt(a[2]):1);plugin.cityMarketCommands().extensions.showDocument(player(sender),page,"/t vassal",arg(a,1));}
            case "book" -> {require(s.cityMarkets!=null,"Ринки ще недоступні.");String mode=arg(a,1).toUpperCase(Locale.ROOT),id=arg(a,2),body=MarketExtensionCommands.manual(player(sender),mode);s.cityMarkets.documents.vassalBook(actor,UUID.randomUUID().toString(),id,mode,body);MarketExtensionCommands.writeBook(player(sender),s.cityMarkets.documents.vassal(id),mode,body);}
            case "accept" -> {if(s.cityMarkets==null)s.accept(actor,arg(a,1));else s.cityMarkets.documents.acceptVassal(actor,arg(a,1),Long.parseLong(arg(a,2)),arg(a,3));say(sender,"Ваш підпис збережено. Договір набуває чинності після підписів усіх сторін.");}
            case "reject" -> {s.reject(actor,arg(a,1));say(sender,"Пропозицію скасовано.");}
            case "offers" -> offers(sender);
            case "contract" -> contract(sender,selected(sender,a,1));
            case "release" -> {String id=s.authority(s.resolve(arg(a,1)).id());s.release(actor,id);say(sender,"Договір завершено. Поточний сюзерен: "+name(s.parent(id))+". Попередні борги збережені.");}
            case "independence" -> proposal(sender,s.independence(actor,selected(sender,a,1)));
            case "tribute" -> {
                String mode=word(arg(a,1));String id=s.resolve(arg(a,2)).id();
                if(mode.equals("pay") || mode.equals("сплатити"))say(sender,"Сплачено "+format(s.payDebt(actor,id,code(a,4),money(arg(a,3))))+" "+code(a,4));
                else {require(mode.equals("status")||mode.equals("статус"),"Доступно tribute status або tribute pay.");debt(sender,id);}
            }
            case "history" -> history(sender,selected(sender,a,1));
            case "charter" -> {s.charter(actor,s.resolve(arg(a,1)).id(),towny.residentId(arg(a,2)));say(sender,"Грамота видана на 3 доби. Після виходу зі старого міста: /t vassal preview <назва>, потім /t vassal found <назва>.");}
            case "preview" -> towny.preview(player(sender),arg(a,1));
            case "found" -> {if(Set.of("confirm","підтвердити").contains(arg(a,1)))towny.complete(player(sender));else towny.found(player(sender),a[1]);}
            case "menu" -> menu(player(sender));
            case "admin" -> admin(sender,a);
            default -> throw new IllegalArgumentException("Невідома команда. /t vassal help");
        }
    }
    private void info(CommandSender sender,String id) throws Exception {
        Estate e=s.estate(id);say(sender,"§e"+plugin.politicalLabel(e)+" "+e.name()+(e.active()?"":" §c(призупинене)"));
        say(sender,plugin.politicalTitle(e)+": "+towny.playerName(e.lord())+". Держава: "+name(e.nation()));
        say(sender,"Сюзерен: "+name(s.parent(id))+". Прямі васали: "+s.children(id).size()+". ID: "+id);
        if(s.controlsTreasury(actor(sender),e)){balances(sender,"e:"+id);debt(sender,id);}
    }
    private void tree(String id,int depth,Set<String> seen,List<String> lines) throws Exception {
        if(!seen.add(id)){lines.add("Помилка: цикл");return;}
        Estate e=s.estate(id);lines.add("  ".repeat(Math.min(depth,12))+"• "+plugin.politicalLabel(e)+" "+e.name()+" — "+towny.playerName(e.lord()));
        for(Estate child:s.children(id))tree(child.id(),depth+1,seen,lines);
    }
    private void proposal(CommandSender sender,String id) throws Exception {
        var p=s.db.one("SELECT * FROM proposals WHERE id=?",id);
        if(p==null){say(sender,"Усі необхідні підписи вже наявні; рішення набуло чинності.");return;}
        say(sender,"Пропозиція: "+id+". Строк: "+when(number(p,"expires"))+". /t vassal view "+id);
        for(String person:string(p,"required").split(",")) {
            try {Player online=Bukkit.getPlayer(UUID.fromString(person));if(online!=null && !online.equals(sender))say(online,"Нова феодальна пропозиція для "+name(string(p,"child"))+". /t vassal offers");}catch(IllegalArgumentException ignored){}
        }
    }
    private void offers(CommandSender sender) throws Exception {
        Actor actor=actor(sender);int count=0;
        for(var p:s.db.query("SELECT * FROM proposals WHERE expires>? ORDER BY expires",s.now())) {
            if(!actor.admin() && !Arrays.asList(string(p,"required").split(",")).contains(actor.id()))continue;
            say(sender,"§e"+string(p,"id")+" §f"+name(string(p,"child"))+" → "+name(string(p,"parent"))+". Дія: "+action(string(p,"action")));
            say(sender,"Данина: "+format(number(p,"amount"))+" "+string(p,"code")+" / 7 діб. Автономія: "+string(p,"autonomy")+". Військовий обов’язок: "+string(p,"military"));
            say(sender,"Строк договору: "+(number(p,"duration_days")==0?"безстроково":number(p,"duration_days")+" діб")+". Пропозиція до: "+when(number(p,"expires")));
            if(string(p,"nominee")!=null)say(sender,"Кандидат: "+towny.playerName(string(p,"nominee")));
            Set<String> signed=new HashSet<>(Arrays.asList(string(p,"signed").split(",")));
            for(String person:string(p,"required").split(","))say(sender,(signed.contains(person)?"§aПідписано: ":"§cОчікується: ")+towny.playerName(person));
            count++;
        }
        if(count==0)say(sender,"Чинних пропозицій немає.");
    }
    private String action(String action){return switch(action){case "LINK"->"васальний договір";case "APPOINT"->"передача посади";case "END"->"мирна незалежність";default->action;};}
    private void contract(CommandSender sender,String id) throws Exception {
        var c=s.contract(id);
        if(c==null){say(sender,"Підписаного договору немає. Сюзерен за належністю або грамотою: "+name(s.parent(id)));return;}
        say(sender,name(id)+" → "+name(string(c,"parent"))+". Договір "+string(c,"id"));
        if(s.market!=null){var paused=s.db.one("SELECT reason FROM currency_tribute_pauses WHERE contract=?",string(c,"id"));if(paused!=null)say(sender,"§c"+string(paused,"reason"));}
        say(sender,"Данина: "+format(number(c,"amount"))+" "+string(c,"code")+" раз на 7 реальних діб. Наступна: "+when(number(c,"next_due")));
        say(sender,"Автономія: "+string(c,"autonomy")+". Військовий обов’язок: "+string(c,"military")+". Завершення: "+when(number(c,"ends")));
        say(sender,"Військові обов’язки є умовою договору для відіграшу; плагін не телепортує гравців і не оголошує війну.");
        if(s.controls(actor(sender),s.estate(id)))debt(sender,id);
    }
    private void debt(CommandSender sender,String id) throws Exception {
        require(s.controlsTreasury(actor(sender),s.estate(id)) || s.parent(id)!=null && s.controls(actor(sender),s.estate(s.parent(id))),"Борг доступний сторонам договору.");
        var rows=s.db.query("SELECT code,SUM(amount-paid) AS owed FROM invoices WHERE child=? AND paid<amount GROUP BY code",id);
        if(rows.isEmpty())say(sender,"Несплаченої данини немає.");
        else for(var row:rows)say(sender,"Борг: "+format(number(row,"owed"))+" "+string(row,"code")+". /t vassal tribute pay "+id+" <сума> "+string(row,"code"));
    }
    private void history(CommandSender sender,String id) throws Exception {
        require(s.controlsTreasury(actor(sender),s.estate(id)),"Журнал доступний правителю або адміністратору.");
        for(var row:s.db.query("SELECT * FROM audit WHERE detail LIKE ? ORDER BY seq DESC LIMIT 20","%"+id+"%"))say(sender,when(number(row,"at_time"))+" "+event(string(row,"event"))+" — "+string(row,"detail"));
    }
    private String event(String value){return switch(value){case "TRANSFER"->"Переказ";case "INVOICE"->"Рахунок";case "TRIBUTE"->"Данина";case "AGREEMENT"->"Угода";case "PROPOSE"->"Пропозиція";case "SIGN"->"Підпис";case "RELEASE"->"Звільнення";case "GRANT"->"Титул";case "REVOKE"->"Відкликання";case "CREATE"->"Створення";case "IMPORT"->"Імпорт";case "SUSPEND"->"Призупинення";case "RESTORE"->"Відновлення";case "EXPIRE"->"Завершення";case "DEBT_CAP"->"Ліміт боргу";case "MINT"->"Емісія";case "REDEEM"->"Погашення";case "CHARTER"->"Грамота";case "FOUND"->"Заснування";default->"Запис журналу ("+value+")";};}
    private void title(CommandSender sender,String sub,String[] a) throws Exception {
        Actor actor=actor(sender);
        switch(sub) {
            case "help","list" -> {say(sender,"Титули посад: Король, Герцог, Граф, Барон. Посада дає владу лише над відповідним володінням.");say(sender,"/t title info <гравець>; /t title grant <держава> <гравець> <барон|граф|герцог>; /t title revoke <держава> <гравець>");say(sender,"Наданий дворянський титул сам по собі не змінює мера, короля чи права на чужу землю. Для нової посади: /t vassal appoint; для заснування: /t vassal charter.");}
            case "info","domain" -> {
                String person=a.length>1?towny.residentId(a[1]):player(sender).getUniqueId().toString();
                for(Estate e:s.estates())if(e.active() && person.equals(e.lord()))say(sender,plugin.politicalTitle(e)+" — "+e.name());
                for(var row:s.db.query("SELECT * FROM grants WHERE person=?",person))say(sender,"Наданий титул: "+Kind.valueOf(string(row,"rank")).title+" у "+name(string(row,"nation")));
            }
            case "grant" -> {s.grant(actor,s.resolve(arg(a,1)).id(),towny.residentId(arg(a,2)),Kind.parse(arg(a,3)));say(sender,"Дворянський титул надано. Для земельної посади потрібне призначення або грамота.");}
            case "revoke" -> {s.revoke(actor,s.resolve(arg(a,1)).id(),towny.residentId(arg(a,2)));say(sender,"Наданий титул відкликано. Володіння, договори й землі збережені.");}
            default -> throw new IllegalArgumentException("Невідома команда. /t title help");
        }
    }
    private void balances(CommandSender sender,String owner) throws Exception {
        var rows=s.db.query("SELECT * FROM accounts WHERE owner=? AND balance>0 ORDER BY code",owner);
        if(rows.isEmpty())say(sender,"Гаманець порожній: монет міст поки немає.");else for(var r:rows)say(sender,format(number(r,"balance"))+" "+string(r,"code"));
    }
    private void admin(CommandSender sender,String[] a) throws Exception {
        Actor actor=actor(sender);require(actor.admin(),"Потрібне право empirecraft.feudal.admin.");
        switch(word(arg(a,1))) {
            case "help" -> say(sender,"/t vassal admin preview|import|check|backup|pending|setliege <васал> <сюзерен>|release <васал>");
            case "preview" -> {var snapshot=towny.snapshot();say(sender,"Towny: "+snapshot.estates().stream().filter(e->e.kind()==Kind.KINGDOM).count()+" держав, "+snapshot.estates().stream().filter(e->e.kind()==Kind.BARONY).count()+" міст. Ідентифікація за UUID.");}
            case "import" -> {towny.sync();say(sender,"Ідемпотентний імпорт виконано; володінь у базі: "+s.estates().size());}
            case "check" -> {var issues=s.check();if(issues.isEmpty())say(sender,"Порушень дерева, випуску й резервів не виявлено.");else for(String issue:issues)say(sender,"§c"+issue);}
            case "backup" -> {
                var folder=plugin.getDataFolder().toPath().resolve("backups");java.nio.file.Files.createDirectories(folder);
                var file=folder.resolve("feudal-"+System.currentTimeMillis()+".zip").toAbsolutePath();s.db.backup(file.toString());say(sender,"Резервну копію збережено: "+file.getFileName());
            }
            case "setliege" -> {s.forceParent(actor,s.resolve(arg(a,2)).id(),s.resolve(arg(a,3)).id());say(sender,"Адміністративну зміну записано в журнал.");}
            case "release" -> {s.release(actor,s.resolve(arg(a,2)).id());say(sender,"Володіння звільнено; борги збережено.");}
            case "pending" -> {for(var op:s.db.query("SELECT * FROM vault_ops WHERE status='PREPARED'"))say(sender,string(op,"id")+" "+towny.playerName(string(op,"actor"))+" "+("IN".equals(string(op,"direction"))?"вхід":"вихід")+" "+format(number(op,"amount"))+" BASE, "+when(number(op,"created")));}
            case "vault" -> {String status=arg(a,3);require(Set.of("applied","failed").contains(status),"Після перевірки журналу економіки вкажіть applied (операція відбулася) або failed (не відбулася).");s.finishVault(actor,arg(a,2),status.equals("applied"));say(sender,"Підтверджений результат збережено. Зовнішній виклик не повторювався.");}
            default -> throw new IllegalArgumentException("Довідка: /t vassal admin help");
        }
    }
    static final class Menu implements InventoryHolder {
        Inventory inventory;final Map<Integer,String> ids=new HashMap<>();
        @Override public Inventory getInventory(){return inventory;}
    }
    private void menu(Player player) throws Exception {
        Menu menu=new Menu();menu.inventory=Bukkit.createInventory(menu,54,"Володіння та скарбниці");
        int slot=0;
        for(Estate e:s.estates())if(e.active() && s.controls(actor(player),e) && slot<52) {
            ItemStack item=new ItemStack(e.kind()==Kind.KINGDOM?Material.GOLDEN_HELMET:Material.MAP);var meta=item.getItemMeta();
            meta.setDisplayName("§e"+plugin.politicalLabel(e)+" "+e.name());meta.setLore(List.of("§7Сюзерен: "+name(s.parent(e.id())),"§7Натисніть для інформації"));item.setItemMeta(meta);
            menu.inventory.setItem(slot,item);menu.ids.put(slot++,e.id());
        }
        ItemStack wallet=new ItemStack(Material.GOLD_INGOT);var meta=wallet.getItemMeta();meta.setDisplayName("§6Особистий гаманець");wallet.setItemMeta(meta);menu.inventory.setItem(53,wallet);player.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return;
        try {
            if(event.getRawSlot()==53){player.closeInventory();plugin.currencyOpen(player,"market","@GOLD","");}
            else if(menu.ids.containsKey(event.getRawSlot())){String id=menu.ids.get(event.getRawSlot());player.closeInventory();towny.sync();info(player,id);}
        }catch(Exception ex){say(player,"Не вдалося відкрити дані володіння.");plugin.failure("Меню",ex);}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent event){Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())try{offers(event.getPlayer());}catch(Exception ex){plugin.failure("Пропозиції при вході",ex);}},40);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] a) {
        try {
            List<String> values=new ArrayList<>();String root=command.getName();
            if(a.length==1) {
                values.addAll(root.equals("vassal")?List.of("допомога","інфо","дерево","список","меню","сюзерен","створити","призначити","запропонувати","проситися","пропозиції","прийняти","відхилити","договір","звільнити","незалежність","данина","історія","грамота","перевірка","заснувати"):root.equals("title")?List.of("допомога","інфо","список","видати","відкликати"):List.of("допомога","баланс","переказ","поповнити","вивести","скарбниця","валюта"));
                values.addAll(root.equals("vassal")?List.of("help","info","tree","list","menu","liege","create","appoint","offer","petition","offers","accept","reject","contract","release","independence","tribute","history","charter","preview","found"):root.equals("title")?List.of("help","info","list","grant","revoke"):List.of("help","balance","pay","deposit","withdraw","treasury","currency"));
                if(root.equals("vassal") && actor(sender).admin())values.addAll(List.of("адмін","admin"));
            }
            else if(Set.of("accept","reject","прийняти","відхилити").contains(a[0]))for(var p:s.db.query("SELECT * FROM proposals WHERE expires>?",s.now())) {
                if(Arrays.asList(string(p,"required").split(",")).contains(actor(sender).id()))values.add(string(p,"id"));
            }
            else {
                for(Estate e:s.estates())if(e.active())values.add((e.kind()==Kind.KINGDOM?"держава:":e.kind()==Kind.BARONY?"місто:":"")+e.name().replace(' ','_'));
                for(var row:s.db.query("SELECT code FROM currencies"))values.add(string(row,"code"));
                for(Player online:Bukkit.getOnlinePlayers())values.add(online.getName());
                values.addAll(List.of("висока","обмежена","добровільно","оборона","повний","барон","графство","герцогство"));
            }
            String prefix=a.length==0?"":a[a.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().limit(60).toList();
        }catch(Exception ignored){return List.of();}
    }
}




