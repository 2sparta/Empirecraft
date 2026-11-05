package ua.empirecraft.feudal;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import static ua.empirecraft.feudal.Model.require;
import static ua.empirecraft.feudal.PoliticalModel.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Shared by /t state debug and the console-safe /statedebug command. */
final class StateDebugCommands implements CommandExecutor,TabCompleter {
    private final FeudalPlugin plugin;
    private final FeudalService s;
    private final TownyBridge towny;
    private final PoliticalCommands politics;
    private final PoliticsDebug debug;
    private static final List<String> OPERATIONS=List.of("help","info","next","setstage","government","territory","ready");
    StateDebugCommands(FeudalPlugin plugin,FeudalService s,TownyBridge towny,PoliticalCommands politics){
        this.plugin=plugin;this.s=s;this.towny=towny;this.politics=politics;debug=new PoliticsDebug(politics.p);
    }
    private void say(CommandSender sender,String message){
 try(var languageScope=LocalText.use(sender)){
sender.sendMessage(LocalText.tr("§6[Відлагодження] §f")+message);
 }
}
    private static void arity(String[] args,int min,int max,String usage){require(args.length>=min && args.length<=max,LocalText.tr("Використання: /t state debug ")+usage);}
    private String id(CommandSender sender,String[] args) throws Exception {
 try(var languageScope=LocalText.use(sender)){

        if(args.length>1)return s.resolve(args[1]).id();
        require(sender instanceof Player,LocalText.tr("У консолі вкажіть володіння: statedebug info <володіння>."));
        return towny.defaultEstate((Player)sender);

 }
}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
 try(var languageScope=LocalText.use(sender)){

        try{
            require(sender.hasPermission(PoliticsDebug.PERMISSION),LocalText.tr("Потрібен дозвіл ")+PoliticsDebug.PERMISSION+".");
            args=CommandLanguage.normalize("statedebug",args);String operation=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            if(operation.equals("help")){
                say(sender,LocalText.tr("/t state debug info [володіння]; next <володіння> [статус]; setstage <володіння> <статус>"));
                say(sender,LocalText.tr("/t state debug government <володіння> <форма>; territory <володіння> <устрій>; ready <володіння> <статус>"));
                say(sender,LocalText.tr("next і setstage пропускають вимоги та очікування; ready — лише очікування. Консоль: statedebug <команда>."));return true;
            }
            require(OPERATIONS.contains(operation),LocalText.tr("Невідома команда. Довідка: /t state debug help"));
            if(operation.equals("info"))arity(args,1,2,LocalText.tr("info [володіння]"));
            else if(operation.equals("next"))arity(args,2,3,LocalText.tr("next <володіння> [статус]"));
            else arity(args,3,3,operation+LocalText.tr(" <володіння> <значення>"));
            towny.sync();politics.refresh();String id=id(sender,args);
            Actor actor=new Actor(sender instanceof Player player?player.getUniqueId().toString():"console",true);
            switch(operation){
                case "info" -> {
                    State st=politics.p.state(id);
                    say(sender,s.estate(id).name()+" ("+id+"): "+st.stage()+"; "+st.government()+"; "+st.territory());
                    say(sender,LocalText.tr("Наступні статуси: ")+debug.nextStages(id)+LocalText.tr("; ревізія: ")+st.revision());
                }
                case "next" -> say(sender,LocalText.tr("DEBUG: наступний статус — ")+debug.next(actor,id,args.length==3?PoliticalModel.parse(Stage.class,args[2]):null)+"; "+id);
                case "setstage" -> {debug.stage(actor,id,PoliticalModel.parse(Stage.class,args[2]));say(sender,LocalText.tr("DEBUG: статус установлено для ")+id+": "+politics.p.state(id).stage());}
                case "government" -> {debug.government(actor,id,PoliticalModel.parse(Government.class,args[2]));say(sender,LocalText.tr("DEBUG: форму правління змінено для ")+id+": "+politics.p.state(id).government());}
                case "territory" -> {debug.territory(actor,id,PoliticalModel.parse(Territory.class,args[2]));say(sender,LocalText.tr("DEBUG: устрій змінено для ")+id+": "+politics.p.state(id).territory());}
                case "ready" -> {debug.ready(actor,id,PoliticalModel.parse(Stage.class,args[2]));say(sender,LocalText.tr("Строк стабільності пропущено. Правитель може подати /t state advance submit ")+id+" "+args[2]);}
            }
        }catch(IllegalArgumentException e){say(sender,e.getMessage());}
        catch(Exception e){plugin.failure(LocalText.tr("Відлагодження держави"),e);say(sender,LocalText.tr("Не вдалося завершити команду. Перевірте журнал сервера."));}
        return true;

 }
}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
 try(var languageScope=LocalText.use(sender)){

        if(!sender.hasPermission(PoliticsDebug.PERMISSION) || args.length==0)return List.of();
        args=CommandLanguage.normalize("statedebug",args);
        var choices=new TreeSet<String>();
        try{
            if(args.length==1)choices.addAll(OPERATIONS);
            else if(args.length==2 && !args[0].equalsIgnoreCase("help")){
                for(var estate:s.estates())if(estate.active() && politics.p.state(estate.id()).mergedInto()==null)choices.add(estate.id());
            }else if(args.length==3)switch(args[0].toLowerCase(Locale.ROOT)){
                case "next","ready" -> {for(Stage stage:debug.nextStages(s.resolve(args[1]).id()))choices.add(stage.name().toLowerCase(Locale.ROOT));}
                case "setstage" -> {for(Stage stage:Stage.values())choices.add(stage.name().toLowerCase(Locale.ROOT));}
                case "government" -> {for(Government government:Government.values())choices.add(government.name().toLowerCase(Locale.ROOT));}
                case "territory" -> {for(Territory territory:Territory.values())choices.add(territory.name().toLowerCase(Locale.ROOT));}
            }
        }catch(Exception ignored){return List.of();}
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(50).toList();

 }
}
}
