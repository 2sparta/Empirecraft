package ua.empirecraft.feudal;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;
import ua.empirecraft.feudal.ShipRegistrationService.Anchor;

/** Human approval, real wand assembly and final city/name assignment are distinct steps. */
final class ShipRegistrationController implements CommandExecutor,TabCompleter {
    static final String ADMIN="empirecraft.feudal.ships.admin";
    final FeudalPlugin plugin;final FleetController controller;final ShipRegistrationService service;
    private final Set<String> opened=new HashSet<>();
    ShipRegistrationController(FeudalPlugin plugin,FleetController controller)throws Exception {
        this.plugin=plugin;this.controller=controller;service=new ShipRegistrationService(controller.fleet.m,controller.fleet,this::valid);
        for(String name:List.of("shiprequest","shipadmin")){plugin.getCommand(name).setExecutor(this);plugin.getCommand(name).setTabCompleter(this);}
    }
    Actor actor(CommandSender sender){return new Actor(sender instanceof Player p?p.getUniqueId().toString():"console",sender.hasPermission(ADMIN));}
    boolean valid(Anchor anchor){
        try{var world=Bukkit.getWorld(UUID.fromString(anchor.world()));return world!=null&&world.isChunkLoaded(anchor.x()>>4,anchor.z()>>4)&&anchor.y()>=world.getMinHeight()&&anchor.y()<world.getMaxHeight()&&!world.getBlockAt(anchor.x(),anchor.y(),anchor.z()).isEmpty()
            &&!controller.world.call("at",Map.of("world",anchor.world(),"x",anchor.x(),"y",anchor.y(),"z",anchor.z())).containsKey("ship");}catch(RuntimeException unavailable){return false;}
    }
    Anchor target(Player player){var block=player.getTargetBlockExact(8);require(block!=null,LocalText.tr("Наведіться на блок майбутнього корабля."));return new Anchor(block.getWorld().getUID().toString(),block.getX(),block.getY(),block.getZ());}
    void nearby(Player player,Anchor anchor){require(player.getWorld().getUID().toString().equals(anchor.world())&&player.getLocation().distanceSquared(new Location(player.getWorld(),anchor.x()+.5,anchor.y()+.5,anchor.z()+.5))<=64,LocalText.tr("Підійдіть до блока заявки на корабель."));}
    void openRequest(Player player,Anchor anchor){require(player.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу."));nearby(player,anchor);require(valid(anchor),LocalText.tr("Цей блок уже має фізику або недоступний."));controller.openShipRequest(player,anchor);}
    void openAssignment(Player player,String application)throws Exception {service.administrator(actor(player));require(string(service.application(application),"state").equals("ASSEMBLED"),LocalText.tr("Корабель ще не зібраний або вже оформлений."));controller.ensure(number(service.application(application),"ship"));controller.openShipAssignment(player,application);}
    Map<String,Object> nativeRequest(String action,Map<String,Object> data)throws Exception {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Заявки на кораблі обробляються в серверному потоці."));
        if(action.equals("ship-complete")){var row=service.complete(data.get("permit").toString(),((Number)data.get("ship")).longValue());return Map.of("ok",true,"assigned",string(row,"state").equals("ASSIGNED"));}
        if(action.equals("ship-status")){var row=service.db.one("SELECT state FROM naval_applications WHERE permit=? AND ship=?",data.get("permit").toString(),((Number)data.get("ship")).longValue());return Map.of("assigned",row!=null&&string(row,"state").equals("ASSIGNED"));}
        if(action.equals("ship-abort")){service.abort(data.get("player").toString(),data.get("permit").toString());return Map.of("ok",true);}
        Player player=Bukkit.getPlayer(UUID.fromString(data.get("player").toString()));require(player!=null,LocalText.tr("Гравець вийшов із сервера."));
        try(var locale=LocalText.useMenu(player)){
            if(action.equals("ship-tool-admin"))return Map.of("admin",player.hasPermission(ADMIN));
            Anchor anchor=new Anchor(data.get("world").toString(),((Number)data.get("x")).intValue(),((Number)data.get("y")).intValue(),((Number)data.get("z")).intValue());nearby(player,anchor);
            if(action.equals("ship-request-open")){openRequest(player,anchor);return Map.of("ok",true);}
            if(action.equals("ship-begin"))return service.begin(actor(player),anchor);
            throw new IllegalArgumentException("Unknown ship registration action");
        }
    }
    List<String> canonical(Player player,String form,List<String> values)throws Exception {
        var result=new ArrayList<>(values);
        if(form.equals("ship-submit")){require(result.size()==1,"Назва корабля");result.set(0,result.get(0).strip());}
        else if(form.equals("ship-finalize")){require(result.size()==2,"Місто та назва");result.set(0,controller.town(result.get(0)));result.set(1,result.get(1).strip());}
        else if(form.equals("ship-owner")){require(result.size()==2,"Корабель та місто");Long.parseLong(result.get(0));result.set(1,controller.town(result.get(1)));}
        return List.copyOf(result);
    }
    String stamp(String form,String application,Anchor anchor,List<String> values)throws Exception {
        if(form.equals("ship-submit"))return anchor.toString()+valid(anchor);
        if(form.equals("ship-owner"))return controller.fleet.ship(Long.parseLong(values.get(0))).toString();
        return CityMarketService.hash(service.application(application).toString());
    }
    String perform(Player player,String request,String form,List<String> values,boolean commit,String application,Anchor anchor)throws Exception {
        var actor=actor(player);
        if(form.equals("ship-submit")){
            require(anchor!=null,LocalText.tr("Наведіться на блок майбутнього корабля."));nearby(player,anchor);ShipRegistrationService.name(values.get(0));require(valid(anchor),LocalText.tr("Цей блок уже має фізику або недоступний."));
            if(commit)return service.submit(actor,request,values.get(0),anchor);return LocalText.tr("Подати заявку на корабель «")+values.get(0)+LocalText.tr("». Адміністратор перевірить і збере його VS-паличкою.");
        }
        service.administrator(actor);
        if(form.equals("ship-finalize")){var row=service.application(application);require(string(row,"state").equals("ASSEMBLED"),LocalText.tr("Спочатку зберіть схвалений корабель VS-паличкою."));ShipRegistrationService.name(values.get(1));
            if(commit){service.finalizeShip(actor,request,application,values.get(0),values.get(1));controller.project(number(row,"ship"));return application;}
            return LocalText.tr("Корабель «")+values.get(1)+LocalText.tr("» буде оформлений як власність міста ")+controller.cityName(values.get(0));
        }
        if(form.equals("ship-owner")){long ship=Long.parseLong(values.get(0));controller.ensure(ship);if(commit)owner(actor,request,ship,values.get(1));return LocalText.tr("Змінити місто-власника корабля #")+ship+" → "+controller.cityName(values.get(1));}
        throw new IllegalArgumentException("Unknown ship registration form");
    }
    void owner(Actor actor,String request,long ship,String town)throws Exception {
        service.administrator(actor);controller.ensure(ship);var pending=service.db.one("SELECT id,name FROM naval_applications WHERE ship=? AND state='ASSEMBLED'",ship);
        if(pending==null)controller.fleet.assign(actor,request,ship,town);else service.finalizeShip(actor,request,string(pending,"id"),town,string(pending,"name"));controller.project(ship);
    }
    void tick()throws Exception {
        for(var row:service.db.query("SELECT id,assembler,ship FROM naval_applications WHERE state='ASSEMBLED'")){
            String id=string(row,"id");if(opened.contains(id))continue;Player player=Bukkit.getPlayer(UUID.fromString(string(row,"assembler")));
            if(player!=null&&player.hasPermission(ADMIN)&&controller.world.hull(number(row,"ship"))!=null){openAssignment(player,id);opened.add(id);}
        }
    }
    private void info(CommandSender sender,Map<String,Object> row){plugin.message(sender,string(row,"id")+" · «"+string(row,"name")+"» · "+string(row,"state")+" · "+string(row,"requested_town")+" · "+string(row,"world")+": "+number(row,"x")+" "+number(row,"y")+" "+number(row,"z")+(row.get("ship")==null?"":" · #"+number(row,"ship")));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try(var locale=LocalText.use(sender)){
        require(sender.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає дозволу."));var actor=actor(sender);boolean admin=command.getName().equals("shipadmin");String action=args.length==0?"list":args[0].toLowerCase(Locale.ROOT);
        if(!admin){require(sender instanceof Player,LocalText.tr("Заявку на корабель подає гравець у грі."));var player=(Player)sender;
            if(action.equals("list")||action.equals("status")){for(var row:service.db.query("SELECT * FROM naval_applications WHERE applicant=? ORDER BY created DESC LIMIT 30",actor.id()))info(sender,row);}
            else if(action.equals("withdraw")){require(args.length==2,"/shiprequest withdraw <ID>");service.withdraw(actor,UUID.randomUUID().toString(),args[1]);}
            else {String name=action.equals("submit")?String.join(" ",Arrays.copyOfRange(args,1,args.length)):String.join(" ",args);String id=service.submit(actor,UUID.randomUUID().toString(),name,target(player));plugin.message(sender,LocalText.tr("Заявку на корабель подано: ")+id);}
            return true;
        }
        service.administrator(actor);
        switch(action){
            case "list" -> {for(var row:service.db.query("SELECT * FROM naval_applications WHERE state IN('PENDING','APPROVED','ASSEMBLING','ASSEMBLED') ORDER BY created LIMIT 100"))info(sender,row);}
            case "approve","reject" -> {require(args.length>=2,"/shipadmin approve|reject <ID> [примітка]");service.review(actor,UUID.randomUUID().toString(),args[1],action.equals("approve"),String.join(" ",Arrays.copyOfRange(args,2,args.length)));info(sender,service.application(args[1]));}
            case "finish" -> {require(sender instanceof Player&&args.length==2,"/shipadmin finish <ID>");openAssignment((Player)sender,args[1]);}
            case "owner" -> {require(args.length>=3,"/shipadmin owner <ID корабля> <місто>");owner(actor,UUID.randomUUID().toString(),Long.parseLong(args[1]),controller.town(String.join(" ",Arrays.copyOfRange(args,2,args.length))));plugin.message(sender,LocalText.tr("Місто-власника корабля оновлено."));}
            default -> plugin.message(sender,"/shipadmin list | approve|reject <ID> [примітка] | finish <ID> | owner <ID корабля> <місто>");
        }
    }catch(IllegalArgumentException error){plugin.message(sender,error.getMessage());}catch(Exception error){plugin.failure("Ship registrations",error);plugin.message(sender,LocalText.tr("Заявку не вдалося обробити. Перевірте журнал сервера."));}return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return args.length==1?(command.getName().equals("shipadmin")?List.of("list","approve","reject","finish","owner"):List.of("list","submit","withdraw")):List.of();}
}
