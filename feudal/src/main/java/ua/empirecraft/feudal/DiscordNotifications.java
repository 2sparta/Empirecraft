package ua.empirecraft.feudal;

import java.util.*;
import java.util.concurrent.Executors;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.scheduler.BukkitTask;

/** Bukkit lifecycle/configuration only. Credentials never enter the queue or logs. */
final class DiscordNotifications implements CommandExecutor,AutoCloseable {
    static final String PERMISSION="empirecraft.feudal.discord";
    private final FeudalPlugin plugin;private final RegistrationOutbox outbox;
    private DiscordBot bot;private BukkitTask task;private boolean enabled,tokenPresent;private Map<String,String> channels=Map.of();
    DiscordNotifications(FeudalPlugin plugin,Database db){this.plugin=plugin;outbox=new RegistrationOutbox(db);}
    void start(){
        try{configure();}catch(IllegalArgumentException invalid){close();plugin.getLogger().warning("Discord bot disabled: "+invalid.getMessage()+"; registration notices remain queued");}
        plugin.getCommand("feudaldiscord").setExecutor(this);
    }
    private void configure(){
        var config=plugin.getConfig();enabled=config.getBoolean("discord.notifications.enabled",false);
        String environment=config.getString("discord.notifications.token-env","EMPIRECRAFT_DISCORD_BOT_TOKEN");
        if(environment==null||!environment.matches("[A-Z_][A-Z0-9_]*"))throw new IllegalArgumentException("Invalid Discord token environment variable name");
        String token=System.getenv(environment);tokenPresent=token!=null&&!token.isBlank();
        String fallback=config.getString("discord.notifications.channel-id","");
        if(fallback==null||fallback.isBlank())fallback=Objects.toString(System.getenv("EMPIRECRAFT_DISCORD_ADMIN_CHANNEL_ID"),"");
        var routing=new LinkedHashMap<String,String>();for(var entry:Map.of("CASTLE","castles","MARKET","markets","SHIP","ships").entrySet()){
            String id=config.getString("discord.notifications.channels."+entry.getValue(),"");if(id==null||id.isBlank())id=fallback;
            if(!id.isBlank()&&!id.matches("[0-9]{17,20}"))throw new IllegalArgumentException("Invalid Discord channel ID for "+entry.getValue());routing.put(entry.getKey(),id);
        }channels=Map.copyOf(routing);outbox.recover();
        if(enabled&&tokenPresent&&channels.values().stream().anyMatch(id->!id.isBlank())){
            var executor=Executors.newSingleThreadExecutor(run->{var thread=new Thread(run,"Empirecraft-Discord");thread.setDaemon(true);return thread;});
            bot=new DiscordBot(outbox,channels::get,new DiscordBot.HttpTransport(token.strip()),executor,System::currentTimeMillis,plugin.getLogger()::warning);
            task=Bukkit.getScheduler().runTaskTimer(plugin,()->{try{bot.tick();}catch(Exception error){plugin.failure("Discord notification queue",error);}},20,20);
            plugin.getLogger().info("Discord registration bot started inside the Minecraft server");
        }else if(enabled)plugin.getLogger().warning("Discord bot is waiting for its token/channel configuration; registration notices remain queued");
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission(PERMISSION)){sender.sendMessage(LocalText.tr("Немає дозволу."));return true;}
        try(var locale=LocalText.use(sender)){
            String action=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("reload")){close();plugin.reloadConfig();start();}
            else if(action.equals("retry"))outbox.retryNow(System.currentTimeMillis());
            else if(!action.equals("status"))throw new IllegalArgumentException("/feudaldiscord status|reload|retry");
            sender.sendMessage("Discord: enabled="+enabled+", tokenConfigured="+tokenPresent+", running="+(bot!=null)+", channels="+channels+", queue="+outbox.counts());
        }catch(Exception error){plugin.getLogger().warning("Discord configuration/queue operation failed ("+error.getClass().getSimpleName()+")");sender.sendMessage(LocalText.tr("Не вдалося оновити Discord-бота. Перевірте налаштування та журнал сервера."));}return true;
    }
    @Override public void close(){if(task!=null){task.cancel();task=null;}if(bot!=null){bot.close();bot=null;}}
}
