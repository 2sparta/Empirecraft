package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.AddonCommand;
import org.bukkit.command.*;
import java.util.*;
import static com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType.TOWN;

/** Towny's own addon dispatcher strips only the first subcommand and preserves native /t. */
final class TownCommands implements AutoCloseable {
    private final List<AddonCommand> registered=new ArrayList<>();
    void register(String name,CommandExecutor handler,String... aliases){
        var command=new AddonCommand(TOWN,name,handler);
        command.setCommandExecutor(new Route(command,handler));add(command);
        for(String alias:aliases)add(new AddonCommand(TOWN,alias,new Route(command,handler)));
    }
    private void add(AddonCommand command){
        if(!TownyCommandAddonAPI.addSubCommand(command))throw new IllegalStateException("Команда /t "+command.getName()+" вже зайнята іншим плагіном.");
        registered.add(command);
    }
    private record Route(Command canonical,CommandExecutor handler) implements CommandExecutor,TabCompleter {
        public boolean onCommand(CommandSender sender,Command ignored,String label,String[] args){
            if(!sender.hasPermission("empirecraft.feudal.use")){sender.sendMessage("§cНемає права користуватися командами володінь.");return true;}
            return handler.onCommand(sender,canonical,"t "+canonical.getName(),args);
        }
        public List<String> onTabComplete(CommandSender sender,Command ignored,String label,String[] args){
            if(!sender.hasPermission("empirecraft.feudal.use") || !(handler instanceof TabCompleter completer))return List.of();
            var result=completer.onTabComplete(sender,canonical,"t "+canonical.getName(),args);return result==null?List.of():result;
        }
    }
    public void close(){
        for(var command:registered)if(TownyCommandAddonAPI.getAddonCommand(TOWN,command.getName())==command)TownyCommandAddonAPI.removeSubCommand(command);
        registered.clear();
    }
}
