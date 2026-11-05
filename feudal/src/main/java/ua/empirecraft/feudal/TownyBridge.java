package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.command.NationCommand;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.nation.*;
import com.palmergames.bukkit.towny.event.town.TownPreMergeEvent;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.utils.ProximityUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;

/** All Towny access stays on the Bukkit server thread. Never bypasses claim checks. */
public final class TownyBridge implements Listener {
    UUID famineNation;
    boolean famineAuthority(String estate){return FamineAuthority.allows(estate,famineTown,famineNation);}
    UUID famineTown; // Set only around the authenticated provision collapse on the main thread.
    private final FeudalPlugin plugin;
    private final FeudalService service;
    public java.util.function.Predicate<String> politicalObligations=id->false;
    public TownyBridge(FeudalPlugin plugin,FeudalService service) { this.plugin=plugin;this.service=service; }
    public static String townId(Town town) { return "t:"+town.getUUID(); }
    public static String nationId(Nation nation) { return "n:"+nation.getUUID(); }
    public Snapshot snapshot() {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Доступ до Towny дозволено лише в головному потоці."));
        var api=TownyAPI.getInstance(); var estates=new ArrayList<Estate>();var citizens=new HashSet<String>();
        for(Nation nation:api.getNations()) {
            if(!nation.hasKing()) throw new IllegalStateException(LocalText.tr("Держава без правителя: ")+nation.getName());
            String id=nationId(nation);
            estates.add(new Estate(id,Kind.KINGDOM,nation.getName(),id,nation.getKing().getUUID().toString(),true));
            for(Resident resident:nation.getResidents()) citizens.add(resident.getUUID()+"@"+id);
        }
        for(Town town:api.getTowns()) {
            if(!town.hasMayor()) throw new IllegalStateException(LocalText.tr("Місто без мера: ")+town.getName());
            Nation nation=town.getNationOrNull();
            for(Resident resident:town.getResidents())citizens.add(resident.getUUID()+"@"+townId(town));
            estates.add(new Estate(townId(town),Kind.BARONY,town.getName(),nation==null?null:nationId(nation),town.getMayor().getUUID().toString(),true));
        }
        return new Snapshot(estates,citizens);
    }
    Kingdoms kingdoms;
    public void sync() {
        if(kingdoms==null)service.sync(snapshot());
        else if(!kingdoms.applying){service.sync(kingdoms.snapshot());try{kingdoms.finishImport();}catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}kingdoms.reconcile();}
    }
    public void validateSuccessor(Estate e,String person) {
        Resident r=TownyAPI.getInstance().getResident(UUID.fromString(person));
        require(r!=null && e.active(),LocalText.tr("Спадкоємець більше не зареєстрований."));
        if(e.kind()==Kind.BARONY){
            Town t=TownyAPI.getInstance().getTown(UUID.fromString(e.id().substring(2)));
            require(t!=null && r.getTownOrNull()==t,LocalText.tr("Спадкоємець мера має жити в цьому місті."));
            try{require(!t.isCapital() || service.authority(e.id()).equals(e.id()),LocalText.tr("Столиця успадковується через державу: /t dynasty nominate держава:Назва гравець."));}catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}
        }else if(e.kind()==Kind.KINGDOM){
            try{
                Town capital=kingdoms==null?(r.getNationOrNull()==null?null:r.getNationOrNull().getCapital()):kingdoms.capital(e.id());
                require(capital!=null && r.getTownOrNull()==capital && service.citizen(person,e.nation()),LocalText.tr("Спадкоємець корони має жити в її столиці."));
            }catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}
        }
    }
    public void transferAuthority(Estate e,String successor) throws Exception {
        validateSuccessor(e,successor);Resident next=TownyAPI.getInstance().getResident(UUID.fromString(successor));
        if(e.kind()==Kind.BARONY){
            Town town=TownyAPI.getInstance().getTown(UUID.fromString(e.id().substring(2)));
            require(town.getMayor().getUUID().toString().equals(e.lord()),LocalText.tr("Мер уже змінився."));
            var event=new com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent(Bukkit.getConsoleSender(),town.getMayor(),next);
            Bukkit.getPluginManager().callEvent(event);require(!event.isCancelled(),LocalText.tr("Towny відхилив передачу міста: ")+event.getCancelMessage());
            town.setMayor(next);town.save();
        }else if(e.kind()==Kind.KINGDOM){
            Nation nation=TownyAPI.getInstance().getNation(UUID.fromString(e.id().substring(2)));
            Town capital=kingdoms==null?(nation==null?null:nation.getCapital()):kingdoms.capital(e.id());
            require(capital!=null && capital.getMayor().getUUID().toString().equals(e.lord()),LocalText.tr("Правитель уже змінився."));
            if(nation!=null){
                var event=new NationKingChangeEvent(capital.getMayor(),next);Bukkit.getPluginManager().callEvent(event);
                require(!event.isCancelled(),LocalText.tr("Towny відхилив передачу держави: ")+event.getCancelMessage());
            }
            var event=new com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent(Bukkit.getConsoleSender(),capital.getMayor(),next);
            Bukkit.getPluginManager().callEvent(event);require(!event.isCancelled(),LocalText.tr("Towny відхилив зміну мера столиці: ")+event.getCancelMessage());
            capital.setMayor(next);capital.save();if(nation!=null)nation.save();
        }
    }
    public String residentId(String name) {
        Resident resident=TownyAPI.getInstance().getResident(name);
        require(resident!=null,LocalText.tr("Гравця не знайдено в Towny: ")+name); return resident.getUUID().toString();
    }
    public void validateElected(Estate estate,String person) throws java.sql.SQLException {
        Resident next=TownyAPI.getInstance().getResident(UUID.fromString(person));
        require(next!=null && estate.active(),LocalText.tr("Вибраний кандидат більше не зареєстрований."));
        if(estate.id().startsWith("t:")){
            Town town=TownyAPI.getInstance().getTown(UUID.fromString(estate.id().substring(2)));
            require(town!=null && next.getTownOrNull()==town && (!town.isCapital() || service.authority(estate.id()).equals(estate.id())),LocalText.tr("Кандидат має бути жителем міста; влада столиці обирається через державу."));
        } else if(estate.id().startsWith("n:"))validateSuccessor(estate,person);
        else require(service.citizen(person,estate.nation()),LocalText.tr("Кандидат має бути громадянином держави."));
    }
    public void transferElected(Estate estate,String person) throws Exception {
        validateElected(estate,person);
        if(Objects.equals(estate.lord(),person))return;
        if(estate.id().startsWith("n:")){transferAuthority(estate,person);return;}
        if(estate.id().startsWith("t:")){
            Town town=TownyAPI.getInstance().getTown(UUID.fromString(estate.id().substring(2)));
            Resident next=TownyAPI.getInstance().getResident(UUID.fromString(person));
            require(Objects.equals(town.getMayor().getUUID().toString(),estate.lord()),LocalText.tr("Мер змінився після виборів."));
            var event=new com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent(Bukkit.getConsoleSender(),town.getMayor(),next);
            Bukkit.getPluginManager().callEvent(event);require(!event.isCancelled(),LocalText.tr("Towny відхилив виборну передачу: ")+event.getCancelMessage());town.setMayor(next);town.save();
        } else service.db.transaction(()->{service.db.execute("UPDATE estates SET lord=? WHERE id=? AND lord=?",person,estate.id(),estate.lord());return null;});
    }
    public String playerName(String uuid) {
        if(uuid==null)return LocalText.tr("Немає");
        try {Resident resident=TownyAPI.getInstance().getResident(UUID.fromString(uuid));return resident==null?uuid:resident.getName();}
        catch(IllegalArgumentException ignored){return uuid;}
    }
    public boolean currencyCitizen(String person,String estate){
        UUID uuid;try{uuid=UUID.fromString(person);}catch(IllegalArgumentException invalid){return false;}
        var resident=TownyAPI.getInstance().getResident(uuid);if(resident==null)return false;
        try{var e=service.estate(estate);
            if(estate.startsWith("t:"))return resident.getTownOrNull()!=null && townId(resident.getTownOrNull()).equals(estate);
            return kingdoms!=null?service.citizen(person,estate):resident.getNationOrNull()!=null && nationId(resident.getNationOrNull()).equals(e.nation());
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public boolean currencyPrintOffice(String person,String estate,String politicalCapital){
        UUID uuid;try{uuid=UUID.fromString(person);}catch(IllegalArgumentException invalid){return false;}
        var api=TownyAPI.getInstance();var resident=api.getResident(uuid);if(resident==null)return false;
        Town capital=null;
        String seat=service.kingdomCapitals.get(estate);if(seat!=null)capital=api.getTown(UUID.fromString(seat.substring(2)));
        if(capital==null && estate.startsWith("n:")){var nation=api.getNation(UUID.fromString(estate.substring(2)));if(nation!=null)capital=nation.getCapital();}
        else if(estate.startsWith("t:"))capital=api.getTown(UUID.fromString(estate.substring(2)));
        else if(politicalCapital!=null && politicalCapital.startsWith("t:"))capital=api.getTown(UUID.fromString(politicalCapital.substring(2)));
        return capital!=null && resident.getTownOrNull()==capital;
    }
    public String defaultEstate(Player player) throws Exception {
        return service.estates().stream().filter(Estate::active).filter(e->player.getUniqueId().toString().equals(e.lord()))
            .max(Comparator.comparingInt(e->e.kind().level)).orElseThrow(()->new IllegalArgumentException(LocalText.tr("Ви ще не керуєте володінням. Укажіть його назву."))).id();
    }
    public void preview(Player player,String name) throws Exception {
 try(var languageScope=LocalText.use(player)){

        var charter=service.charter(player.getUniqueId().toString());
        require(name.matches("[\\p{L}\\p{N}_-]{2,32}"),LocalText.tr("Назва міста: 2–32 літери, цифри, дефіси або підкреслення."));
        var api=TownyAPI.getInstance();
        require(api.getTown(name)==null,LocalText.tr("Місто з такою назвою вже існує."));
        Resident resident=api.getResident(player);
        require(resident!=null && !resident.hasTown(),LocalText.tr("Перед заснуванням вийдіть зі старого міста. Грамота зберігає державу на 3 доби."));
        require(player.hasPermission("towny.command.town.new"),LocalText.tr("Немає права Towny на заснування міста."));
        TownyWorld world=api.getTownyWorld(player.getWorld());
        require(world!=null && world.isUsingTowny() && world.isClaimable(),LocalText.tr("У цьому світі заснування заборонене."));
        require(api.isWilderness(player.getLocation()),LocalText.tr("Ділянку вже зайнято."));
        ProximityUtil.allowTownHomeBlockOrThrow(world,Coord.parseCoord(player),null,true);
        Nation nation=kingdoms==null?api.getNation(UUID.fromString(string(charter,"nation").substring(2))):kingdoms.nativeNation(string(charter,"nation"));
        require(nation!=null && !nation.hasReachedMaxTowns() && nation.canAddResidents(1),LocalText.tr("Держава не може прийняти нове місто."));
        require(service.children(string(charter,"parent")).size()<plugin.maxChildren(),LocalText.tr("Досягнуто ліміт васалів сюзерена."));
        double distance=plugin.getConfig().getDouble("founding.max-distance-blocks",2048);
        boolean nearby=distance<=0;
        for(Town town:nation.getTowns()) for(TownBlock block:town.getTownBlocks()) {
            var pos=block.getWorldCoord();
            if(pos.getWorldName().equals(player.getWorld().getName())) {
                double dx=player.getLocation().getX()-(pos.getX()+0.5)*Coord.getCellSize();
                double dz=player.getLocation().getZ()-(pos.getZ()+0.5)*Coord.getCellSize();
                if(dx*dx+dz*dz<=distance*distance)nearby=true;
            }
        }
        require(nearby,LocalText.tr("Місто має бути не далі ніж ")+(int)distance+LocalText.tr(" блоків від приватів держави."));
        plugin.message(player,LocalText.tr("Попередня перевірка успішна. Сюзерен: ")+service.estate(string(charter,"parent")).name()+LocalText.tr(". Towny повторно перевірить ціну, біом, відстані й ліміти під час заснування."));

 }
}
    public void found(Player player,String name) throws Exception {
        preview(player,name);
        service.db.transaction(()-> {
            service.db.execute("MERGE INTO pending_foundings KEY(person) VALUES(?,?,?)",player.getUniqueId().toString(),name,service.now());return null;
        });
        // Native command performs all current Towny permission, biome, proximity, money and confirmation checks.
        Bukkit.dispatchCommand(player,"towny:town new "+name);
    }
    public void complete(Player player) throws Exception {
 try(var languageScope=LocalText.use(player)){

        Town town=TownyAPI.getInstance().getTown(player);
        require(town!=null && town.isMayor(TownyAPI.getInstance().getResident(player)),LocalText.tr("Towny ще не створив ваше місто. За потреби підтвердьте його заснування командою Towny."));
        complete(town);

 }
}
    private void complete(Town town) throws Exception {
        String person=town.getMayor().getUUID().toString();
        var pending=service.db.one("SELECT * FROM pending_foundings WHERE person=?",person);
        require(pending!=null && string(pending,"name").equalsIgnoreCase(town.getName()),LocalText.tr("Немає погодженого заснування для цього міста."));
        var charter=service.charter(person);
        String nationId=string(charter,"nation");
        if(!town.hasNation()) {
            Nation nation=kingdoms==null?TownyAPI.getInstance().getNation(UUID.fromString(nationId.substring(2))):kingdoms.nativeNation(nationId);
            require(nation!=null,LocalText.tr("Держава більше не існує."));
            // Towny's static nationAdd checks limits but does not fire the cancellable pre-join event.
            // Honour other Towny add-ons before invoking the native membership procedure.
            var joining=new NationPreAddTownEvent(nation,town);
            boolean internal=kingdoms!=null;
            if(internal)kingdoms.applying=true;
            try{Bukkit.getPluginManager().callEvent(joining);}finally{if(internal)kingdoms.applying=false;}
            require(!joining.isCancelled(),LocalText.tr("Вступ скасовано правилами Towny: ")+joining.getCancelMessage());
            NationCommand.nationAdd(nation,town);
        }
        require(town.hasNation() && (kingdoms==null?nationId(town.getNationOrNull()).equals(nationId):town.getNationOrNull()==kingdoms.nativeNation(nationId)),LocalText.tr("Towny не дозволив вступ до держави. Місто збережено. Усуньте причину й виконайте /t vassal found confirm."));
        if(kingdoms==null)sync();
        else{
            service.db.execute("MERGE INTO kingdom_members KEY(town) VALUES(?,?)",townId(town),nationId);
            service.sync(kingdoms.snapshot());
        }
        service.completeFounding(new FeudalService.Actor(person,false),townId(town));
        if(kingdoms!=null)kingdoms.reconcile();
        service.db.transaction(()->{service.db.execute("DELETE FROM pending_foundings WHERE person=?",person);return null;});
        Player online=Bukkit.getPlayer(UUID.fromString(person));if(online!=null)plugin.message(online,LocalText.tr("Баронство ")+town.getName()+LocalText.tr(" засноване та підпорядковане погодженому сюзерену."));
    }
    public void resumeFoundings() throws Exception {
        for(var pending:service.db.query("SELECT * FROM pending_foundings")) {
            Town town=TownyAPI.getInstance().getTown(string(pending,"name"));
            if(town!=null && town.getMayor().getUUID().toString().equals(string(pending,"person"))) {
                try {complete(town);}catch(IllegalArgumentException ex){plugin.getLogger().info(LocalText.tr("Незавершене заснування ")+town.getName()+": "+ex.getMessage());}
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void onNewTown(NewTownEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{
            Town town=event.getTown();
            try {
                if(service.db.one("SELECT person FROM pending_foundings WHERE person=?",town.getMayor().getUUID().toString())==null)return;
                complete(town);
            }catch(IllegalArgumentException ex){
                Player online=Bukkit.getPlayer(town.getMayor().getUUID());if(online!=null)plugin.message(online,ex.getMessage());
            }catch(Exception ex){plugin.failure(LocalText.tr("Заснування"),ex);}
        });
    }
    private boolean obligations(String id) throws Exception {
        if(politicalObligations.test(id))return true;
        // A nation cannot evade obligations of its regional estates by deletion or merger.
        return !service.db.query("SELECT c.id FROM contracts c JOIN estates e ON c.child=e.id WHERE c.active=TRUE AND (c.child=? OR c.parent=? OR e.nation=?)",id,id,id).isEmpty()
            || !service.db.query("SELECT i.id FROM invoices i JOIN estates e ON i.child=e.id WHERE i.paid<i.amount AND (i.child=? OR i.parent=? OR e.nation=?)",id,id,id).isEmpty();
    }
    private void protect(CancellableTownyEvent event,String... ids) {
        try {
            if(kingdoms!=null && kingdoms.applying)return;
            sync();
            for(String id:ids) if(obligations(id)) {
                event.setCancelled(true);event.setCancelMessage(LocalText.tr("Спершу погодьте завершення васальних договорів і сплатіть борги: /t vassal contract."));return;
            }
        }catch(Exception ex){event.setCancelled(true);event.setCancelMessage(LocalText.tr("Не вдалося перевірити васальні зобов’язання. Зверніться до адміністратора."));plugin.failure(LocalText.tr("Перевірка договору"),ex);}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void leave(NationPreTownLeaveEvent e){protect(e,townId(e.getTown()));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void kick(NationPreTownKickEvent e){protect(e,townId(e.getTown()));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void delete(PreDeleteTownEvent e){if(!e.getTown().getUUID().equals(famineTown))protect(e,townId(e.getTown()));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void delete(PreDeleteNationEvent e){if(!famineAuthority(nationId(e.getNation())) || e.getNation().getTowns().size()>1)protect(e,nationId(e.getNation()));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void merge(TownPreMergeEvent e){protect(e,townId(e.getRemainingTown()),townId(e.getSuccumbingTown()));}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void merge(NationPreMergeEvent e){protect(e,nationId(e.getNation()),nationId(e.getRemainingNation()));}
}
