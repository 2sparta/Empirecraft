package ua.empirecraft.feudal;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.time.Clock;
import java.util.logging.Level;

public final class FeudalPlugin extends JavaPlugin {
    private Database database;
    private FeudalService service;
    private TownyBridge towny;
    @Override public void onEnable() {
        try {
            saveDefaultConfig(); Files.createDirectories(getDataFolder().toPath());
            Class.forName("org.h2.Driver",true,getClassLoader());
            var backups=getDataFolder().toPath().resolve("backups");Files.createDirectories(backups);
            database=new Database("jdbc:h2:"+getDataFolder().toPath().resolve("feudal").toAbsolutePath()+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",backups.resolve("before-currency-v2-"+System.currentTimeMillis()+".zip").toAbsolutePath().toString());
            service=new FeudalService(database,Clock.systemUTC(),7*FeudalService.DAY,
                getConfig().getLong("contracts.proposal-hours",48)*3_600_000L,maxChildren(),
                getConfig().getInt("hierarchy.max-depth",12),getConfig().getInt("contracts.max-unpaid-periods",2));
            towny=new TownyBridge(this,service); towny.sync();
            var moneyCommands=new CurrencyCommands(this,service,towny);
            var commands=new FeudalCommands(this,service,towny,new VaultBridge(service),moneyCommands);
            for(String command:new String[]{"currency","bank","exchange"}){
                var registered=getCommand(command);if(registered==null)throw new IllegalStateException("Не зареєстровано команду "+command);
                registered.setExecutor(moneyCommands);registered.setTabCompleter(moneyCommands);
            }
            Bukkit.getPluginManager().registerEvents(moneyCommands,this);
            for(String command:new String[]{"vassal","title","wallet"}) {
                var registered=getCommand(command);if(registered==null)throw new IllegalStateException("Не зареєстровано команду "+command);
                registered.setExecutor(commands); registered.setTabCompleter(commands);
            }
            Bukkit.getPluginManager().registerEvents(towny,this);
            Bukkit.getPluginManager().registerEvents(commands,this);
            long interval=Math.max(100,getConfig().getLong("sync-ticks",600));
            Bukkit.getScheduler().runTaskTimer(this,()->{
                try {towny.sync();service.tick();}catch(Exception ex){failure("Синхронізація",ex);}
            },interval,interval);
            Bukkit.getScheduler().runTask(this,()->{try{towny.resumeFoundings();service.tick();}catch(Exception ex){failure("Відновлення",ex);}});
            var issues=service.check();for(String issue:issues)getLogger().warning(issue);
            getLogger().info("Васальна система Empirecraft увімкнена. Українська локалізація. Імпортовано володінь: "+service.estates().size());
        }catch(Exception ex){failure("Запуск",ex);Bukkit.getPluginManager().disablePlugin(this);}
    }
    public int maxChildren(){return getConfig().getInt("hierarchy.max-children",32);}
    public void message(CommandSender sender,String message){sender.sendMessage("§6[Володіння] §f"+message);}
    public void failure(String stage,Exception ex){getLogger().log(Level.SEVERE,stage+": "+ex.getMessage(),ex);}
    @Override public void onDisable(){if(database!=null)try{database.close();}catch(Exception ex){failure("Закриття бази",ex);}}
}

