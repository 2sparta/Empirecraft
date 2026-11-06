package ua.empirecraft.feudal;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.time.Clock;
import java.util.logging.Level;

public final class FeudalPlugin extends JavaPlugin {
    private Database database;
    private TownMenu townMenu;
    private ContractMenus contractMenus;
    private ArmyController armyController;
    private FeudalService service;
    private TownyBridge towny;
    private TradeCommands trades;
    private TownCommands townCommands;
    private RealmCommands realms;
    private ProvisionController provisions;
    private PoliticsService politics;
    private PoliticalCommands politicalCommands;
    private CurrencyMarket currencyMarket;
    private CurrencyScreens currencyScreens;
    private MarketCommands cityMarketCommands;
    private MarketScreens marketScreens;
    private FleetController fleet;
    private RecruitController recruits;
    private EngagementController engagement;
    EngagementController engagement(){if(engagement==null)throw new IllegalStateException("Бойова система ще готується.");return engagement;}
    public java.util.Map<String,Object> engagementRequest(String action,java.util.Map<String,Object> data)throws Exception{return engagement().request(action,data);}
    public java.util.Map<String,Object> recruitState()throws Exception{return recruits==null?java.util.Map.of("ready",false):recruits.state();}
    boolean recruitTrade(org.bukkit.entity.Player player,String[] args)throws Exception{return recruits!=null&&recruits.trade(player,args);}
    void recruitMarket(org.bukkit.entity.Player player)throws Exception{if(recruits==null)throw new IllegalStateException("Договори рекрутів ще готуються.");recruits.openMarket(player,cityMarketCommands.current(player),0);}
    public java.util.Map<String,Object> fleetRequest(String action,java.util.Map<String,Object> data)throws Exception{if(fleet==null)throw new IllegalStateException("Флот ще готується.");return fleet.request(action,data);}
    public java.util.Map<String,Object> fleetUi(java.util.UUID player,String session,String action,String section,String form,java.util.List<String> values,String token,int page){if(fleet==null)return java.util.Map.of("error","Флот ще готується.");return fleet.ui(player,session,action,section,form,values,token,page);}
    public java.util.List<java.util.Map<String,Object>> fleetPoll(){return fleet==null?java.util.List.of():fleet.poll();}
    public java.util.List<java.util.Map<String,Object>> fleetMarks(java.util.UUID player)throws Exception{return fleet==null?java.util.List.of():fleet.marks(player);}
    boolean fleetTrade(org.bukkit.entity.Player player,String[] args)throws Exception{return fleet!=null&&fleet.trade(player,args);}
    void fleetMarket(org.bukkit.entity.Player player)throws Exception{if(fleet==null)throw new IllegalStateException("Флот ще готується.");fleet.openMarket(player);}
    public Object marketItem(java.util.UUID player,String session,String spec)throws Exception{return marketScreens.item(player,session,spec);}
    MarketCommands cityMarketCommands(){return cityMarketCommands;}
    public java.util.List<java.util.Map<String,Object>> marketPoll(){return marketScreens.poll();}
    public void contactMailGateway(java.util.function.Function<java.util.Map<String,Object>,java.util.Map<String,Object>> gateway)throws Exception {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Only the server thread may install Contact");
        var mail=cityMarketCommands.m.fairs.mail;
        mail.paperSpec=cityMarketCommands.m.registerSpec(GoldInventoryBridge.encode(new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER)}),false);
        mail.gateway=request->{var response=new java.util.LinkedHashMap<String,Object>(gateway.apply(request));
            if(response.get("item") instanceof org.bukkit.inventory.ItemStack item)try{response.put("item",GoldInventoryBridge.encode(new org.bukkit.inventory.ItemStack[]{item}));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}return response;};
    }
    public java.util.Map<String,Object> marketUi(java.util.UUID player,String session,String action,String section,String form,java.util.List<String> values,String token,int page){return marketScreens.request(player,session,action,section,form,values,token,page);}
    @Override public void onEnable() {
        try {
            saveDefaultConfig(); Files.createDirectories(getDataFolder().toPath());
            Class.forName("org.h2.Driver",true,getClassLoader());
            var backups=getDataFolder().toPath().resolve("backups");Files.createDirectories(backups);
            database=new Database("jdbc:h2:"+getDataFolder().toPath().resolve("feudal").toAbsolutePath()+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",backups.resolve("before-currency-v2-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            if(database.one("SELECT code FROM currencies WHERE code='BASE' OR backing='LEGACY_BASE' LIMIT 1")!=null)
                database.backup(backups.resolve("before-retiring-base-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            database.retireBase();
            service=new FeudalService(database,Clock.systemUTC(),7*FeudalService.DAY,
                getConfig().getLong("contracts.proposal-hours",48)*3_600_000L,maxChildren(),
                getConfig().getInt("hierarchy.max-depth",12),getConfig().getInt("contracts.max-unpaid-periods",2));
            towny=new TownyBridge(this,service);
            if(database.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='kingdom_seats'")==null && database.one("SELECT id FROM estates LIMIT 1")!=null)
                database.backup(backups.resolve("before-derived-kingdoms-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            towny.kingdoms=new Kingdoms(this,service);towny.sync();
            Bukkit.getPluginManager().registerEvents(towny.kingdoms,this);
            if(database.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='political_states'")==null)
                database.backup(backups.resolve("before-politics-1.4-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            politics=new PoliticsService(service,new PoliticsRules(key->getConfig().contains("politics."+key)?getConfig().getLong("politics."+key):-1));
            towny.politicalObligations=id->{try{return politics.obligations(id);}catch(Exception e){throw new IllegalStateException(e);}};
            Bukkit.getPluginManager().registerEvents(new PoliticalNativeGuard(this,politics,towny),this);
            service.appointmentPermission=politics::hereditary;
            politicalCommands=new PoliticalCommands(this,service,towny,politics);
            getCommand("statedebug").setExecutor(politicalCommands.debug);
            getCommand("statedebug").setTabCompleter(politicalCommands.debug);
            politicalCommands.refresh();
            if(database.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='currency_print_jobs'")==null)database.backup(backups.resolve("before-currency-1.5-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            currencyMarket=new CurrencyMarket(service,politics);
            if(database.one("SELECT owner FROM gold_accounts WHERE owner LIKE 'p:%' AND balance>0 LIMIT 1")!=null)
                database.backup(backups.resolve("before-physical-gold-1.7-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            service.currencies.migratePersonalGold();
            currencyMarket.citizen=towny::currencyCitizen;
            currencyMarket.printingOffice=(person,estate)->{try{return towny.currencyPrintOffice(person,estate,politics.state(estate).capital());}catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}};
            currencyScreens=new CurrencyScreens(this,service,towny,currencyMarket);
            var realm=new RealmService(service);realm.hereditary=politics::hereditary;
            var commerce=new CommerceService(service);
            var items=new ItemTransactions(this,commerce);
            if(database.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='cm_markets'")==null)
                database.backup(backups.resolve("before-city-markets-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            else if(database.one("SELECT table_name FROM information_schema.tables WHERE LOWER(table_name)='cm_mail_samples'")==null)
                database.backup(backups.resolve("before-contact-fairs-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            var cityMarkets=new CityMarketService(service,commerce);
            cityMarketCommands=new MarketCommands(this,cityMarkets,items,towny);
            marketScreens=new MarketScreens(cityMarketCommands);new TownTravelGuard(this).register();
            cityMarkets.migrate();
            getCommand("market").setExecutor(cityMarketCommands);getCommand("market").setTabCompleter(cityMarketCommands);
            Bukkit.getPluginManager().registerEvents(cityMarketCommands,this);
            Bukkit.getScheduler().runTaskTimer(this,()->{try{cityMarketCommands.tick();}catch(Exception ex){failure(LocalText.tr("Міські ринки"),ex);}},20,20);
            provisions=new ProvisionController(this,new ProvisionService(service,commerce),items,towny);provisions.register();
            trades=new TradeCommands(this,commerce,items);
            realms=new RealmCommands(this,service,towny,realm,commerce,items,trades);
            getCommand("trade").setExecutor(trades);getCommand("trade").setTabCompleter(trades);
            for(String name:new String[]{"order","commerce"}){getCommand(name).setExecutor(realms);getCommand(name).setTabCompleter(realms);}
            Bukkit.getPluginManager().registerEvents(trades,this);Bukkit.getPluginManager().registerEvents(realms,this);Bukkit.getPluginManager().registerEvents(items,this);
            var moneyCommands=new CurrencyCommands(this,service,towny);
            var commands=new FeudalCommands(this,service,towny,new VaultBridge(service),moneyCommands);
            for(String command:new String[]{"currency"}){
                var registered=getCommand(command);if(registered==null)throw new IllegalStateException(LocalText.tr("Не зареєстровано команду ")+command);
                registered.setExecutor(moneyCommands);registered.setTabCompleter(moneyCommands);
            }
            Bukkit.getPluginManager().registerEvents(moneyCommands,this);
            townCommands=new TownCommands(service);
            townMenu=new TownMenu(this,service,towny,politicalCommands,realms);
            townCommands.register("menu",townMenu,"меню","gui");
            Bukkit.getPluginManager().registerEvents(townMenu,this);
            service.contractBuilder=new ContractService(service,politics);
            service.contractBuilder.names=towny::playerName;service.contractBuilder.estateNames=e->politicalLabel(e)+" "+e.name();
            contractMenus=new ContractMenus(this,service.contractBuilder,towny,commerce,items);
            townCommands.register("treaty",contractMenus,"договір");
            Bukkit.getPluginManager().registerEvents(contractMenus,this);
            armyController=new ArmyController(this,new ArmyService(service,new ProvisionService(service,commerce),politics),provisions,towny);
            realm.changed=armyController::invalidate;armyController.start();
            fleet=new FleetController(this,cityMarkets,towny,politics,()->armyController.view);
            getCommand("fleet").setExecutor(fleet);getCommand("fleet").setTabCompleter(fleet);
            Bukkit.getPluginManager().registerEvents(fleet,this);townCommands.register("fleet",fleet,"флот");
            Bukkit.getScheduler().runTaskTimer(this,()->{try{fleet.tick();}catch(Exception ex){failure("Флот",ex);}},20,20);
            recruits=new RecruitController(this,cityMarkets,towny,fleet.world);
            engagement=new EngagementController(fleet);
            Bukkit.getPluginManager().registerEvents(new EngagementTownyGuard(engagement),this);
            getCommand("recruits").setExecutor(recruits);getCommand("recruits").setTabCompleter(recruits);
            Bukkit.getPluginManager().registerEvents(recruits,this);Bukkit.getPluginManager().registerEvents(new RecruitTownyGuard(recruits),this);
            Bukkit.getScheduler().runTaskTimer(this,recruits::tick,20,20);
            townCommands.register("army",armyController,"рекрути","гарнізон");
            townCommands.register("treasury",new TreasuryCommands(this,service,towny),"казна","bank","банк");
            townCommands.register("state",politicalCommands,"держава");
            townCommands.register("fief",politicalCommands,"вотчина");
            townCommands.register("castle",politicalCommands,"замок");
            townCommands.register("union",politicalCommands,"унія");
            townCommands.register("confed",politicalCommands);
            townCommands.register("federation",politicalCommands);
            townCommands.register("vassal",commands,"васал","feudal");
            townCommands.register("title",commands,"титул");
            townCommands.register("diplomacy",realms,"дипломатія");
            townCommands.register("dynasty",realms,"династія");
            townCommands.register("estateinfo",realms,"володіння");
            townCommands.register("provision",provisions,"провізія");
            new CommandLanguage(this,new CommandPreferences(database)).register();
            Bukkit.getPluginManager().registerEvents(towny,this);
            Bukkit.getPluginManager().registerEvents(commands,this);
            Bukkit.getScheduler().runTaskTimer(this,()->{try{towny.sync();currencyMarket.tick();currencyMarket.exchange.tick();currencyScreens.expire();}catch(Exception ex){failure(LocalText.tr("Друк валюти"),ex);}},20,20);
            long interval=Math.max(100,getConfig().getLong("sync-ticks",600));
            Bukkit.getScheduler().runTaskTimer(this,()->{
                try {towny.sync();service.tick();service.contractBuilder.tick();realms.tick();politicalCommands.tick();}catch(Exception ex){failure(LocalText.tr("Синхронізація"),ex);}
            },interval,interval);
            Bukkit.getScheduler().runTask(this,()->{try{towny.resumeFoundings();realms.reconcileSuccession();service.tick();service.contractBuilder.tick();realms.tick();politicalCommands.tick();}catch(Exception ex){failure(LocalText.tr("Відновлення"),ex);}});
            var issues=service.check();for(String issue:issues)getLogger().warning(issue);
            getLogger().info(LocalText.tr("Васальна система Empirecraft увімкнена. Українська локалізація. Імпортовано володінь: ")+service.estates().size());
        }catch(Exception ex){failure(LocalText.tr("Запуск"),ex);Bukkit.getPluginManager().disablePlugin(this);}
    }
    public java.util.Map<String,Object> armyRequest(String action,java.util.Map<String,Object> request)throws Exception{if(armyController==null)throw new IllegalStateException(LocalText.tr("Система рекрутів ще не готова"));return armyController.request(action,request);}
    public boolean armyCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){return armyController.onCommand(sender,command,label,args);}
    public void townMenuOpen(org.bukkit.entity.Player player)throws Exception{townMenu.open(player);}
    public void contractMenuOpen(org.bukkit.entity.Player player)throws Exception{contractMenus.open(player);}
    public void currencyOpen(org.bukkit.entity.Player player,String mode,String from,String to){currencyScreens.open(player,mode,from,to);}
    public String currencyEstate(org.bukkit.entity.Player player)throws Exception{return currencyScreens.estate(player);}
    public String currencyOwned(org.bukkit.entity.Player player)throws Exception{return currencyScreens.own(player);}
    public java.util.List<java.util.Map<String,Object>> currencyPoll(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Server thread required");return currencyScreens.poll();}
    public java.util.Map<String,Object> currencyUi(java.util.UUID player,String action,String from,String to,String amount,String minimum,String settings,String period,String token){return currencyScreens.request(player,action,from,to,amount,minimum,settings,period,token);}
    public String politicalLabel(Model.Estate estate){try{return LocalText.tr(politics==null?estate.kind().label:politics.state(estate.id()).stage().label);}catch(Exception e){return LocalText.tr(estate.kind().label);}}
    public String politicalTitle(Model.Estate estate){try{var st=politics.state(estate.id());return LocalText.tr(st.government()==PoliticalModel.Government.REPUBLIC?"Виборний голова":st.government()==PoliticalModel.Government.SULTANATE?"Султан":st.government()==PoliticalModel.Government.MILITARY_ORDER?"Командор":st.stage().title);}catch(Exception e){return LocalText.tr(estate.kind().title);}}
    public String chatTitle(java.util.UUID person) throws java.sql.SQLException {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Chat identity must be read on the server thread");
        var api=com.palmergames.bukkit.towny.TownyAPI.getInstance();
        var resident=api.getResident(person);
        var nation=resident==null?null:resident.getNationOrNull();
        String nationId=nation==null?null:TownyBridge.nationId(nation);
        return ChatTitleService.resolve(service,person,nationId,estate->{
            if(estate.id().startsWith("t:")) {
                var town=api.getTown(java.util.UUID.fromString(estate.id().substring(2)));
                return town!=null && town.getMayor()!=null && person.equals(town.getMayor().getUUID());
            }
            if(estate.id().startsWith("n:")) {
                var state=api.getNation(java.util.UUID.fromString(estate.id().substring(2)));
                return state!=null && state.getKing()!=null && person.equals(state.getKing().getUUID());
            }
            return nationId!=null && nationId.equals(estate.nation());
        },estate->{try{return politics.state(estate.id()).stage().rank;}catch(Exception e){return estate.kind().level;}},this::politicalTitle);
    }
    public int maxChildren(){return getConfig().getInt("hierarchy.max-children",32);}
    public void message(CommandSender sender,String message){
 try(var languageScope=LocalText.use(sender)){
sender.sendMessage(LocalText.tr("§6[Володіння] §f")+message);
 }
}
    public void failure(String stage,Exception ex){getLogger().log(Level.SEVERE,stage+": "+ex.getMessage(),ex);}
    public java.util.List<java.util.Map<String,Object>> mapEstates() throws Exception {return realms.mapEstates();}
    public java.util.List<java.util.Map<String,Object>> mapPoints() throws Exception {return realms.mapPoints();}
    @Override public void onDisable(){CommandLanguage.reset();if(contractMenus!=null)contractMenus.shutdown();if(townMenu!=null)townMenu.close();if(townCommands!=null)townCommands.close();if(provisions!=null)provisions.shutdown();if(trades!=null)trades.shutdown();if(database!=null)try{database.close();}catch(Exception ex){failure(LocalText.tr("Закриття бази"),ex);}}
}
