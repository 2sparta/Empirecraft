package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import com.palmergames.bukkit.towny.TownyAPI;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Existing /trade and physical /market entries, server-owned quotes and read-only lot/confirmation menus. */
public final class RecruitController implements CommandExecutor,TabCompleter,Listener {
    final FeudalPlugin plugin;final RecruitMarketService service;final RecruitWorld world;final TownyBridge towny;
    private final Map<String,Prepared> prepared=new HashMap<>();private long ticks;
    private record Prepared(String token,String request,List<String> args,String stamp,long until,String summary) {}
    private static final class Menu implements InventoryHolder {
        final UUID owner;final String market,mode,offer;final List<String> ids;final int page;final Inventory inventory;
        Menu(Player p,String market,String mode,String offer,List<String> ids,int page,String title){owner=p.getUniqueId();this.market=market;this.mode=mode;this.offer=offer;this.ids=List.copyOf(ids);this.page=page;inventory=Bukkit.createInventory(this,54,title);}
        public Inventory getInventory(){return inventory;}
    }
    RecruitController(FeudalPlugin plugin,CityMarketService market,TownyBridge towny,FleetWorld towns)throws Exception {
        this.plugin=plugin;this.towny=towny;world=new RecruitWorld(towns);service=new RecruitMarketService(market,world);
    }
    private static String key(CommandSender sender){return sender instanceof Player p?p.getUniqueId().toString():"console";}
    private Actor actor(CommandSender sender){return new Actor(key(sender),sender.hasPermission("empirecraft.feudal.army.admin"));}
    private static String arg(List<String> args,int at){require(at<args.size(),LocalText.tr("Недостатньо аргументів. /recruits help"));return args.get(at);}
    private String town(String input){var api=TownyAPI.getInstance();com.palmergames.bukkit.towny.object.Town town;
        try{town=api.getTown(UUID.fromString(input.replace("t:","")));}catch(IllegalArgumentException error){town=api.getTown(input);}require(town!=null,LocalText.tr("Місто не знайдено."));return TownyBridge.townId(town);}
    private String person(String input){var api=TownyAPI.getInstance();com.palmergames.bukkit.towny.object.Resident resident;
        try{resident=api.getResident(UUID.fromString(input));}catch(IllegalArgumentException error){resident=api.getResident(input);}require(resident!=null,LocalText.tr("Командира не знайдено."));return resident.getUUID().toString();}
    private String cityName(String city)throws Exception{return service.s.estate(city).name();}
    private Player player(CommandSender sender){require(sender instanceof Player,LocalText.tr("Ця дія доступна гравцю."));return (Player)sender;}
    private String home(Player p){String home=world.home(p.getUniqueId().toString());require(home!=null,LocalText.tr("Потрібно бути жителем міста Towny."));return home;}
    private String current(Player p)throws Exception {String market=plugin.cityMarketCommands().current(p);service.m.access(market,Department.GOODS,MarketCommands.place(p));return market;}
    private void access(Player p,Map<String,Object> offer)throws Exception {String market=string(offer,"market");if(!market.isBlank())service.m.access(market,Department.GOODS,MarketCommands.place(p));else require(Set.of(string(offer,"seller"),string(offer,"buyer")).contains(home(p)),LocalText.tr("Це трейд інших міст."));}
    private String stamp(CommandSender sender,List<String> args)throws Exception {
        String sub=arg(args,0);String base=key(sender)+"|"+(sender instanceof Player p?Objects.toString(world.home(key(sender)),"")+"|"+service.s.membershipStamp(key(sender)):"console");
        if(Set.of("sell","offer-hire","direct-sale","direct-hire").contains(sub)){var roster=world.roster(arg(args,1));require(roster!=null,LocalText.tr("Військо не знайдено."));String market=sub.startsWith("direct-")?"":current(player(sender));return hash(base+roster+"|"+market+(market.isEmpty()?"":service.m.tariff(market).toString()));}
        if(Set.of("buy","hire").contains(sub))return hash(base+service.stamp(arg(args,1))+(sub.equals("hire")?service.battle(arg(args,2)):""));
        if(Set.of("consent","decline","verdict").contains(sub))return hash(base+service.contract(arg(args,1)).toString()+service.offer(string(service.contract(arg(args,1)),"offer")));
        if(sub.equals("cancel")){var contract=service.db.one("SELECT * FROM rm_contracts WHERE id=?",arg(args,1));return hash(base+(contract==null?service.offer(arg(args,1)):contract).toString());}
        if(sub.equals("battle")&&!arg(args,1).equals("create"))return hash(base+service.battle(arg(args,2))+service.db.query("SELECT id,state,verdict FROM rm_contracts WHERE battle=? ORDER BY id",arg(args,2)));
        return hash(base+args);
    }
    private String execute(CommandSender sender,List<String> args,String request,boolean commit,String expected)throws Exception {
        Actor actor=actor(sender);String sub=arg(args,0);
        if(Set.of("sell","offer-hire","direct-sale","direct-hire").contains(sub)){
            Player p=player(sender);boolean direct=sub.startsWith("direct-"),hire=sub.endsWith("hire");String asset=arg(args,1);var roster=world.roster(asset);require(roster!=null&&roster.eligible(),LocalText.tr("Військо недоступне для продажу."));require(service.mayor(actor,roster.town()),LocalText.tr("Виставляє військо мер міста-власника."));
            String buyer=direct?town(arg(args,2)):"",market=direct?"":current(p),code=arg(args,direct?4:3).toUpperCase(Locale.ROOT);long price=money(arg(args,direct?3:2));int hours=args.size()>(direct?5:4)?Integer.parseInt(arg(args,direct?5:4)):24;
            if(commit)return service.list(actor,request,asset,hire?"HIRE":"SALE",market,MarketCommands.place(p),buyer,code,price,hours);
            int fee=market.isBlank()?0:(int)number(service.m.tariff(market),"seller");
            return (hire?LocalText.tr("Найм на одну битву: "):LocalText.tr("Постійний продаж полку: "))+roster.name()+LocalText.tr("; бійців: ")+roster.count()+LocalText.tr("; місто: ")+cityName(roster.town())+LocalText.tr("; ціна: ")+format(price)+" "+code+LocalText.tr("; збір продавця: ")+format(CityMarketService.fee(price,fee))+LocalText.tr("; строк оголошення: ")+(direct?LocalText.tr("5 хвилин"):hours+" "+LocalText.tr("годин"))+(direct?LocalText.tr("; покупець: ")+cityName(buyer):"");
        }
        if(sub.equals("buy")||sub.equals("hire")){
            Player p=player(sender);String id=arg(args,1);var offer=service.offer(id);access(p,offer);String buyer=home(p),commander=sub.equals("buy")?person(arg(args,2)):"",battle=sub.equals("hire")?arg(args,2):"";
            require(string(offer,"kind").equals(sub.equals("buy")?"SALE":"HIRE"),LocalText.tr("Спосіб купівлі не відповідає пропозиції."));require(service.mayor(actor,buyer),LocalText.tr("Купівлю від міста підтверджує мер."));
            if(commit)return service.buy(actor,request,id,buyer,commander,battle,MarketCommands.place(p),service.stamp(id));
            var quote=service.inspect(actor,id,buyer,commander,battle,MarketCommands.place(p),service.stamp(id));int bps=quote.buyerBps();long price=number(offer,"price"),fee=CityMarketService.fee(price,bps);
            return cityName(buyer)+LocalText.tr(" купує ")+string(offer,"name")+LocalText.tr("; бійців: ")+number(offer,"soldiers")+LocalText.tr("; оплата з казни: ")+format(price)+" + "+format(fee)+" = "+format(Math.addExact(price,fee))+" "+string(offer,"code")+(sub.equals("hire")?LocalText.tr(". Битва: ")+string(service.battle(battle),"name")+LocalText.tr(". Кошти залишаються в резерві до рішення адміністратора; командир має погодитися протягом 5 хвилин."):LocalText.tr(". Полк назавжди передається місту та обраному командиру: ")+towny.playerName(commander));
        }
        if(sub.equals("consent")||sub.equals("decline")){
            String id=arg(args,1);var contract=service.contract(id);require(actor.id().equals(string(contract,"commander")),LocalText.tr("Згоду на найм дає лише командир цього загону."));
            if(commit)return service.consent(actor,request,id,sub.equals("consent"));
            return (sub.equals("consent")?LocalText.tr("Погодити найм на битву: "):LocalText.tr("Відхилити найм на битву: "))+string(service.battle(string(contract,"battle")),"name")+LocalText.tr("; місто-наймач: ")+cityName(string(contract,"buyer"))+LocalText.tr(". На час битви місто зміниться, а накази й спорядження загону залишаться під вашим виключним контролем. Ділянки зберігаються; повернення автоматичне.");
        }
        if(sub.equals("cancel")){if(commit)return service.cancel(actor,request,arg(args,1));return LocalText.tr("Скасувати продаж або найм до початку битви: ")+arg(args,1)+LocalText.tr(". Зарезервовану оплату буде повернуто місту.");}
        if(sub.equals("battle")){
            require(actor.admin(),LocalText.tr("Потрібен адміністратор битв."));String action=arg(args,1);
            if(action.equals("create")){String a=town(arg(args,2)),b=town(arg(args,3));int minutes=Integer.parseInt(arg(args,4));String name=String.join(" ",args.subList(5,args.size()));if(commit)return service.createBattle(actor,request,name,a,b,minutes);return LocalText.tr("Створити битву: ")+name+"; "+cityName(a)+" / "+cityName(b)+LocalText.tr("; тривалість, хвилин: ")+minutes;}
            String id=arg(args,2);require(Set.of("start","end","cancel").contains(action),LocalText.tr("Дія битви: create, start, end або cancel."));
            String reason=args.size()>3?String.join(" ",args.subList(3,args.size())):"";
            if(commit)return action.equals("start")?service.startBattle(actor,request,id):service.endBattle(actor,request,id,action.equals("cancel"),reason);
            return LocalText.tr("Битва: ")+string(service.battle(id),"name")+"; "+action+(action.equals("start")?LocalText.tr(". Погоджені найманці тимчасово перейдуть до міст-наймачів."):LocalText.tr(". Командири повернуться додому; оплата залежить від рішення про участь. ")+reason);
        }
        if(sub.equals("verdict")){
            require(actor.admin(),LocalText.tr("Потрібен адміністратор битв."));String id=arg(args,1),decision=arg(args,2),reason=String.join(" ",args.subList(3,args.size()));require(Set.of("present","absent").contains(decision),LocalText.tr("Участь: present або absent."));
            if(commit)return service.verdict(actor,request,id,decision.equals("present"),reason);
            return LocalText.tr("Рішення адміністратора: ")+id+" · "+decision+" · "+reason+(decision.equals("present")?LocalText.tr(". Після битви оплату отримає місто-продавець."):LocalText.tr(". Після битви місто-покупець отримає повне повернення, включно зі зборами."));
        }
        throw new IllegalArgumentException(LocalText.tr("Невідома дія. /recruits help"));
    }
    private void prepare(CommandSender sender,List<String> args)throws Exception {
        String summary=execute(sender,args,"",false,"");String token=UUID.randomUUID().toString();prepared.put(key(sender),new Prepared(token,UUID.randomUUID().toString(),List.copyOf(args),stamp(sender,args),System.currentTimeMillis()+30_000,summary));
        plugin.message(sender,summary);plugin.message(sender,LocalText.tr("Підтвердити протягом 30 секунд: /recruits confirm ")+token);
        if(sender instanceof Player p)try(var menuLanguage=LocalText.useMenu(p)){summary=execute(sender,args,"",false,"");Menu menu=new Menu(p,"","confirm",token,List.of(),0,LocalText.tr("Підтвердити договір війська"));menu.inventory.setItem(13,icon(Material.WRITABLE_BOOK,LocalText.tr("Умови договору"),wrap(summary)));menu.inventory.setItem(30,icon(Material.LIME_DYE,LocalText.tr("Підтвердити")));menu.inventory.setItem(32,icon(Material.BARRIER,LocalText.tr("Скасувати")));p.openInventory(menu.inventory);}
    }
    private void confirm(CommandSender sender,String token)throws Exception {
        var quote=prepared.get(key(sender));require(quote!=null&&quote.token.equals(token)&&quote.until>System.currentTimeMillis(),LocalText.tr("Підтвердження прострочене або належить іншому гравцю."));require(quote.stamp.equals(stamp(sender,quote.args)),LocalText.tr("Умови змінилися. Перегляньте договір повторно."));
        String result=execute(sender,quote.args,quote.request,true,quote.stamp);prepared.remove(key(sender));plugin.message(sender,LocalText.tr("Договір збережено. ID: ")+result);
        try{reconcile();}catch(Exception pending){plugin.message(sender,LocalText.tr("Договір збережено. Зачекайте завершення передачі війська."));}
        try{notifyParties(result);}catch(Exception unavailable){plugin.getLogger().warning("Recruit notification waits: "+unavailable.getMessage());}
    }
    private void notifyParties(String id)throws Exception {
        var contract=service.db.one("SELECT * FROM rm_contracts WHERE id=?",id);
        if(contract!=null&&string(contract,"state").equals("RESERVED")){
            String captain=string(contract,"commander");Player player=Bukkit.getPlayer(UUID.fromString(captain));if(player!=null)try(var locale=LocalText.use(player)){plugin.message(player,LocalText.tr("Місто пропонує найм вашого загону на битву. Переглянути: /recruits info ")+id+LocalText.tr("; погодитися: /recruits consent ")+id+LocalText.tr("; відхилити: /recruits decline ")+id);}return;
        }
        var offer=service.db.one("SELECT * FROM rm_offers WHERE id=?",id);if(offer!=null&&string(offer,"market").isEmpty()&&string(offer,"state").equals("OPEN")){
            var town=FleetWorld.town(string(offer,"buyer"));Player mayor=town==null?null:Bukkit.getPlayer(town.getMayor().getUUID());if(mayor!=null)try(var locale=LocalText.use(mayor)){plugin.message(mayor,LocalText.tr("Нова пропозиція війська для вашого міста. Відкрити: /recruits trades"));}
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] input){
        try(var language=LocalText.use(sender)){try {
            require(sender.hasPermission("empirecraft.feudal.use"),LocalText.tr("Недостатньо прав."));require(input.length<=24&&String.join(" ",input).length()<=2000,LocalText.tr("Завеликий запит."));towny.sync();List<String> args=new ArrayList<>(List.of(input));if(!args.isEmpty())args.set(0,args.get(0).toLowerCase(Locale.ROOT));if(args.size()>1&&args.get(0).equals("battle"))args.set(1,args.get(1).toLowerCase(Locale.ROOT));if(args.size()>2&&args.get(0).equals("verdict"))args.set(2,args.get(2).toLowerCase(Locale.ROOT));String sub=args.isEmpty()?"list":args.get(0);
            if(sub.equals("confirm")){confirm(sender,arg(args,1));return true;}
            if(sub.equals("help")){help(sender);return true;}
            if(sub.equals("list")){Player p=player(sender);String city=home(p);for(var roster:world.all())if(city.equals(roster.town()))plugin.message(p,roster.asset()+" · "+roster.name()+" · "+roster.count()+LocalText.tr(" бійців; командир: ")+towny.playerName(roster.commander()));plugin.message(p,LocalText.tr("Ринок військ: /market regiments; договори: /recruits contracts; битви: /recruits battles"));return true;}
            if(sub.equals("offers")){openMarket(player(sender),current(player(sender)),0);return true;}
            if(sub.equals("trades")){openMarket(player(sender),"",0);return true;}
            if(sub.equals("contracts")){List<Map<String,Object>> rows;if(actor(sender).admin())rows=service.db.query("SELECT * FROM rm_contracts ORDER BY created DESC LIMIT 50");else {String city=world.home(key(sender));rows=service.db.query("SELECT c.* FROM rm_contracts c JOIN rm_offers o ON o.id=c.offer WHERE c.buyer=? OR o.seller=? OR c.commander=? ORDER BY c.created DESC LIMIT 50",city,city,key(sender));}for(var row:rows)showContract(sender,row,false);return true;}
            if(sub.equals("battles")){for(var row:service.db.query("SELECT * FROM rm_battles ORDER BY created DESC LIMIT 50"))plugin.message(sender,string(row,"id")+" · "+string(row,"name")+" · "+status(string(row,"status"))+" · "+cityName(string(row,"town_a"))+" / "+cityName(string(row,"town_b"))+LocalText.tr("; тривалість, хвилин: ")+number(row,"duration")+(number(row,"ends")>0?" · "+CityMarketService.time(number(row,"ends")):""));return true;}
            if(sub.equals("info")){String id=arg(args,1);var contract=service.db.one("SELECT * FROM rm_contracts WHERE id=?",id);if(contract!=null){var offer=service.offer(string(contract,"offer"));String city=sender instanceof Player?world.home(key(sender)):null;require(actor(sender).admin()||key(sender).equals(string(contract,"commander"))||Objects.equals(city,string(offer,"seller"))||Objects.equals(city,string(contract,"buyer")),LocalText.tr("Це договір інших міст."));showContract(sender,contract,true);}else {var offer=service.offer(id);if(!actor(sender).admin())access(player(sender),offer);showOffer(sender,offer);}return true;}
            prepare(sender,args);
        }catch(IllegalArgumentException e){plugin.message(sender,"§c"+e.getMessage());}catch(Exception e){plugin.failure("Recruit contracts",e);plugin.message(sender,LocalText.tr("Дію війська не завершено; збережені договори відновлюються автоматично."));}return true;}
    }
    boolean trade(Player p,String[] args)throws Exception {
        if(args.length==0||!Set.of("regiment","mercenary","полк","найманці").contains(args[0].toLowerCase(Locale.ROOT)))return false;
        require(args.length==5,LocalText.tr("/trade regiment|mercenary <R:UUID|C:UUID> <місто-покупець> <ціна> <валюта>"));
        List<String> command=new ArrayList<>(List.of(args));command.set(0,Set.of("mercenary","найманці").contains(args[0].toLowerCase(Locale.ROOT))?"direct-hire":"direct-sale");prepare(p,command);return true;
    }
    private void help(CommandSender p){for(String line:List.of("/recruits list | offers | trades | contracts | battles | info <ID>","/recruits sell <R:UUID> <ціна> <валюта> [години]","/recruits offer-hire <C:UUID> <ціна> <валюта> [години]","/recruits buy <продаж> <новий-командир>","/recruits hire <пропозиція> <битва>","/recruits consent|decline <договір>; cancel <продаж-або-договір>","/trade regiment|mercenary <військо> <місто> <ціна> <валюта>","/recruits battle create <місто-A> <місто-B> <хвилини> <назва>","/recruits battle start <ID>; battle end|cancel <ID> <причина>","/recruits verdict <договір> present|absent <обґрунтування>"))plugin.message(p,LocalText.tr(line));}
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){return args.length==1?List.of("help","list","offers","trades","contracts","battles","info","sell","offer-hire","buy","hire","consent","decline","cancel","battle","verdict","confirm").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
    private static ItemStack icon(Material material,String name,String... lore){ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));item.setItemMeta(meta);return item;}
    private static String status(String raw){return LocalText.tr(switch(raw){case "OPEN"->"Відкрито";case "RESERVED"->"Очікує згоди командира";case "WAITING"->"Очікує початку битви";case "ACTIVE"->"Битва триває";case "REVIEW"->"Очікує рішення адміністратора";case "PAID"->"Оплату отримано";case "REFUNDED"->"Оплату повернено";case "SOLD"->"Продано";case "ENDED","FINISHED"->"Завершено";case "CANCELLED"->"Скасовано";case "EXPIRED"->"Строк минув";case "PRESENT"->"Участь підтверджено";case "ABSENT"->"Участь не підтверджено";default->raw;});}
    private void showOffer(CommandSender sender,Map<String,Object> row)throws Exception {
        plugin.message(sender,string(row,"id")+" · "+string(row,"name")+" · "+status(string(row,"state"))+LocalText.tr("; бійців: ")+number(row,"soldiers"));
        plugin.message(sender,cityName(string(row,"seller"))+LocalText.tr("; ціна: ")+format(number(row,"price"))+" "+string(row,"code")+LocalText.tr("; до: ")+CityMarketService.time(number(row,"ends")));
    }
    private void showContract(CommandSender sender,Map<String,Object> row,boolean detail)throws Exception {
        var offer=service.offer(string(row,"offer"));var battle=service.battle(string(row,"battle"));
        plugin.message(sender,string(row,"id")+" · "+string(offer,"name")+" · "+status(string(row,"state"))+" · "+string(battle,"name"));
        plugin.message(sender,cityName(string(offer,"seller"))+" → "+cityName(string(row,"buyer"))+LocalText.tr("; командир: ")+towny.playerName(string(row,"commander"))+LocalText.tr("; оплата: ")+format(number(offer,"price"))+" "+string(offer,"code"));
        if(!string(row,"verdict").isBlank())plugin.message(sender,status(string(row,"verdict"))+" · "+string(row,"reason"));
        if(detail){if(number(battle,"ends")>0)plugin.message(sender,LocalText.tr("Кінець битви: ")+CityMarketService.time(number(battle,"ends")));if(string(row,"state").equals("RESERVED"))plugin.message(sender,LocalText.tr("Згода командира до: ")+CityMarketService.time(number(row,"consent_until"))+"; /recruits consent "+string(row,"id"));}
    }
    private static String[] wrap(String text){List<String> lines=new ArrayList<>();for(int i=0;i<text.length();i+=58)lines.add(text.substring(i,Math.min(text.length(),i+58)));return lines.toArray(String[]::new);}
    void openMarket(Player p,String market,int page)throws Exception {
        try(var menuLanguage=LocalText.useMenu(p)){
        String filter;List<Object> params=new ArrayList<>();if(market.isBlank()){String city=home(p);filter="market='' AND (seller=? OR buyer=?)";params.add(city);params.add(city);}else{service.m.access(market,Department.GOODS,MarketCommands.place(p));filter="market=?";params.add(market);}params.add(service.s.now());
        long count=number(service.db.one("SELECT COUNT(*) AS amount FROM rm_offers WHERE "+filter+" AND state='OPEN' AND ends>?",params.toArray()),"amount");int last=(int)Math.max(0,(count-1)/45);page=Math.max(0,Math.min(page,last));params.add(page*45);
        var offers=service.db.query("SELECT * FROM rm_offers WHERE "+filter+" AND state='OPEN' AND ends>? ORDER BY created,id LIMIT 45 OFFSET ?",params.toArray());
        var ids=offers.stream().map(row->string(row,"id")).toList();Menu menu=new Menu(p,market,"offers","",ids,page,LocalText.tr("Полки та найманці")+" · "+(page+1));
        for(int i=0;i<offers.size();i++){var row=offers.get(i);menu.inventory.setItem(i,icon(string(row,"kind").equals("SALE")?Material.WHITE_BANNER:Material.IRON_SWORD,string(row,"name"),string(row,"kind").equals("SALE")?LocalText.tr("Постійний продаж полку"):LocalText.tr("Найм загону на одну битву"),cityName(string(row,"seller")),number(row,"soldiers")+LocalText.tr(" бійців"),format(number(row,"price"))+" "+string(row,"code"),string(row,"id")));}
        menu.inventory.setItem(45,icon(Material.ARROW,LocalText.tr("Назад")));menu.inventory.setItem(49,icon(Material.BOOK,LocalText.tr("Як виставити військо"),"/recruits sell R:UUID ціна валюта","/recruits offer-hire C:UUID ціна валюта","/recruits list"));menu.inventory.setItem(53,icon(Material.ARROW,LocalText.tr("Далі")));p.openInventory(menu.inventory);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(menu.owner))return;
        try(var language=LocalText.useMenu(p)){try {
            if(menu.mode.equals("confirm")){if(event.getRawSlot()==30){p.closeInventory();confirm(p,menu.offer);}else if(event.getRawSlot()==32){prepared.remove(key(p));p.closeInventory();}return;}
            if(!menu.market.isBlank())service.m.access(menu.market,Department.GOODS,MarketCommands.place(p));int slot=event.getRawSlot();if(slot==45||slot==53){openMarket(p,menu.market,menu.page+(slot==45?-1:1));return;}
            if(slot<0||slot>=45||slot>=menu.ids.size())return;var offer=service.offer(menu.ids.get(slot));access(p,offer);p.closeInventory();
            plugin.message(p,string(offer,"name")+" · "+number(offer,"soldiers")+LocalText.tr(" бійців · ")+format(number(offer,"price"))+" "+string(offer,"code"));
            if(home(p).equals(string(offer,"seller"))){plugin.message(p,LocalText.tr("Ваша пропозиція. Зняти з продажу: /recruits cancel ")+string(offer,"id"));return;}
            if(string(offer,"kind").equals("SALE"))prepare(p,List.of("buy",string(offer,"id"),p.getUniqueId().toString()));else plugin.message(p,LocalText.tr("Оберіть битву через /recruits battles, потім /recruits hire ")+string(offer,"id")+" <ID-битви>");
        }catch(Exception error){plugin.message(p,"§c"+error.getMessage());}}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @SuppressWarnings("unchecked") void reconcile()throws Exception {
        var rows=service.citizenships();if(rows.isEmpty()&&service.db.one("SELECT seq FROM rm_jobs LIMIT 1")==null&&service.db.one("SELECT id FROM rm_offers WHERE state IN('OPEN','RESERVED','WAITING') LIMIT 1")==null)return;
        for(var row:rows){boolean restoring=!string(row,"state").equals("ACTIVE");world.move(string(row,"commander"),string(row,restoring?"seller":"buyer"),string(row,"citizenship"),restoring);}
        Map<String,Object> reply=world.call("sync",Map.of());Map<String,Number> versions=(Map<String,Number>)reply.get("versions");
        for(var row:rows)if(!string(row,"state").equals("ACTIVE")){var offer=service.offer(string(row,"offer"));var job=service.db.one("SELECT seq FROM rm_jobs WHERE asset=? AND kind='RETURN' AND contract=? ORDER BY seq DESC LIMIT 1",string(offer,"asset"),string(row,"id"));
            if(job!=null&&versions.getOrDefault(string(offer,"asset"),0).longValue()>=number(job,"seq")&&string(row,"seller").equals(world.home(string(row,"commander"))))service.restored(string(row,"id"));}
    }
    void tick(){ticks++;prepared.entrySet().removeIf(e->e.getValue().until<System.currentTimeMillis());try{service.tick();reconcile();}catch(Exception error){if(ticks%60==1)plugin.getLogger().warning("Recruit contracts wait for reconciliation: "+error.getMessage());}}
    Map<String,Object> state()throws Exception {return service.state();}
}
