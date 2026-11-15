package ua.townyrecruits;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** All entry points run on the server thread. One ledger owns limits, development and purchases. */
public final class Recruitment {
    final Bridge bridge;public final RecruitmentConfig config;public final RecruitmentState state;
    UUID purchasing;TownyRecruitMenu menu;boolean recovered;
    Recruitment(Bridge bridge)throws IOException {
        this.bridge=bridge;
        Path root=bridge.server.m_129843_(net.minecraft.world.level.storage.LevelResource.f_78182_);
        config=new RecruitmentConfig(Path.of("config/townyrecruits.properties"));
        Path file=root.resolve("data/townyrecruits-recruitment.json");
        if(!Files.exists(file))backup(root);
        state=new RecruitmentState(file);
        state.nations.forEach((id,n)->Policy.bindTeam(id,n.faction));
    }
    void backup(Path world)throws IOException {
        Path dest=world.resolve("townyrecruits-backup-2.0.0");if(Files.exists(dest.resolve("COMPLETE")))return;
        Files.createDirectories(dest);
        Path data=world.resolve("data");if(Files.isDirectory(data))try(var files=Files.list(data)){
            for(Path file:files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".dat")||p.getFileName().toString().endsWith(".dat_old")).toList())Files.copy(file,dest.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
        }
        Path old=Path.of("plugins/TownyRecruitsBridge/config.yml");if(Files.exists(old))Files.copy(old,dest.resolve("TownyRecruitsBridge-config.yml"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dest.resolve("COMPLETE"),"Before TownyRecruits 2.0.0 migration. Restore only with the server stopped.\n");
    }
    Set<UUID> cities=Set.of();
    void synchronize()throws Exception {
        if(menu==null)menu=new TownyRecruitMenu(this);
        var manager=FactionEvents.recruitsFactionManager;if(manager==null)return;
        Map<UUID,RecruitmentState.Nation> added=new LinkedHashMap<>();
        Set<String> used=new HashSet<>();state.nations.values().forEach(n->used.add(n.faction));
        for(var nation:bridge.snapshot.nations().values())if(!state.nations.containsKey(nation.id())){
            String modern=Policy.team(nation.id()),legacy="tn_"+nation.id().toString().replace("-","").substring(0,8);
            var found=manager.getFactionByStringID(modern);
            if(found==null && !used.contains(legacy))found=manager.getFactionByStringID(legacy);
            if(found==null && config.adopt){
                var candidates=manager.getFactions().stream().filter(f->!used.contains(f.getStringID())&&!f.getStringID().startsWith("tn_")).sorted(Comparator.comparing(f->f.getStringID())).toList();
                var exact=candidates.stream().filter(f->f.getStringID().equalsIgnoreCase(nation.name())||nation.name().equalsIgnoreCase(f.getTeamDisplayName())).toList();
                var king=candidates.stream().filter(f->nation.king().equals(f.getTeamLeaderUUID())).toList();
                if(exact.size()==1)found=exact.get(0);else if(exact.isEmpty()&&king.size()==1)found=king.get(0);
                else if(exact.size()>1||king.size()>1)throw new IllegalStateException("Неоднозначні старі фракції для нації "+nation.name()+". Потрібно перевірити їхні назви та лідерів.");
            }
            String id=found==null?modern:found.getStringID();
            int total=found==null?config.total:found.getMaxNPCs(),per=found==null?config.perPlayer:found.getMaxNPCsPerPlayer();
            if(bridge.data.overrides.containsKey(nation.id()))total=bridge.data.overrides.get(nation.id());
            var migrated=new RecruitmentState.Nation(id,total,per);migrated.unlimited=found!=null&&total==0&&!bridge.data.overrides.containsKey(nation.id());added.put(nation.id(),migrated);used.add(id);
            TownyRecruits.LOG.info("Nation migration {} -> {}, caps {}/{} (existing={})",nation.name(),id,total,per,found!=null);
        }
        Set<UUID> removed=new HashSet<>();
        for(var e:state.nations.entrySet())if(!e.getValue().deleted&&!bridge.snapshot.nations().containsKey(e.getKey()))removed.add(e.getKey());
        Set<UUID> currentCities=new HashSet<>(),newCities=new HashSet<>();
        for(Object town:(Collection<?>)bridge.towny.call(bridge.towny.api,"getTowns")){UUID id=bridge.towny.id(town);currentCities.add(id);if(!state.development.containsKey(id))newCities.add(id);}
        cities=Set.copyOf(currentCities);
        Map<UUID,UUID> homes=new HashMap<>(),owners=new HashMap<>();
        if(bridge.censusReady())for(var e:bridge.data.units.entrySet())if(!e.getValue().retired()&&!state.homes.containsKey(e.getKey())){
            UUID owner=e.getValue().owner();if(!owners.containsKey(owner))owners.put(owner,bridge.towny.city(owner));UUID home=owners.get(owner);if(home!=null)homes.put(e.getKey(),home);
        }
        if(!added.isEmpty()||!removed.isEmpty()||!newCities.isEmpty()||!homes.isEmpty()){
            state.change(()->{state.nations.putAll(added);removed.forEach(id->state.nations.get(id).deleted=true);newCities.forEach(id->state.development.put(id,config.initialDevelopment));state.homes.putAll(homes);});
            added.forEach((id,n)->Policy.bindTeam(id,n.faction));bridge.data.overrides.clear();bridge.data.m_77762_();bridge.forceSync=true;
        }
        if(!recovered&&bridge.censusReady()){
            Set<UUID> loaded=new HashSet<>();bridge.loaded.forEach(r->loaded.add(r.m_20148_()));
            for(UUID id:List.copyOf(bridge.data.units.keySet()))if(!bridge.data.diskUnits.contains(id)&&!loaded.contains(id))bridge.data.remove(id);
            recovered=true;for(var player:bridge.server.m_6846_().m_11314_())recover(player);
        }
    }
    boolean homeDeleted(AbstractRecruitEntity recruit){UUID city=state.homes.get(recruit.m_20148_());return city!=null&&!cities.contains(city);}
    public int limit(UUID nation){var n=state.nations.get(nation);return n==null||n.deleted?0:n.unlimited?Integer.MAX_VALUE:n.total;}
    public int perPlayer(UUID nation,UUID player){var n=state.nations.get(nation);var info=bridge.snapshot.nations().get(nation);return n==null||n.deleted?0:info!=null&&player.equals(info.king())?limit(nation):n.perPlayer;}
    public int ownerCount(UUID nation,UUID player){return (int)bridge.data.units.values().stream().filter(u->!u.retired()&&nation.equals(u.nation())&&player.equals(u.owner())).count()+reserved(nation,player);}
    public int reserved(UUID nation,UUID player){return (int)state.purchases.values().stream().filter(RecruitmentState::pending).filter(p->nation.equals(p.nation)&&(player==null||player.equals(p.player))&&!bridge.data.units.containsKey(p.unit)).count();}
    public boolean slots(UUID nation,UUID player){return nation!=null&&bridge.count(nation)<limit(nation)&&ownerCount(nation,player)<perPlayer(nation,player);}
    public void limits(UUID nation,int total,int per){limits(nation,total,per,false);}
    public void limits(UUID nation,int total,int per,boolean unlimited){if(total<0||per<0||total>1000000||per>1000000)throw new IllegalArgumentException("Ліміти: 0–1000000.");state.change(()->{var n=state.nations.get(nation);n.total=total;n.perPlayer=per;n.unlimited=unlimited;});bridge.syncFactions();}
    public void hired(ServerPlayer player,AbstractRecruitEntity recruit)throws Exception {
        UUID home=bridge.towny.city(player.m_20148_());if(home!=null){recruit.getPersistentData().m_128362_("TRHomeTown",home);state.change(()->state.homes.put(recruit.m_20148_(),home));}
    }
    public UUID home(AbstractRecruitEntity recruit)throws Exception {
        UUID unit=recruit.m_20148_(),home=state.homes.get(unit);
        if(home==null){var tag=recruit.getPersistentData();home=tag.m_128403_("TRHomeTown")?tag.m_128342_("TRHomeTown"):bridge.towny.city(recruit.getOwnerUUID());if(home!=null){UUID fixed=home;state.change(()->state.homes.put(unit,fixed));}}
        if(home!=null)recruit.getPersistentData().m_128362_("TRHomeTown",home);return home;
    }
    void feed(AbstractRecruitEntity recruit){
        if(recruit.getHunger()>70 || !recruit.getIsOwned())return;
        try{
            UUID home=home(recruit);if(home==null)return;
            Object feudal=bridge.towny.feudal();if(feudal==null)return;
            Object controller=field(feudal,"armyController"),army=field(controller,"army");
            // Calling the existing public service with the fixed home prevents owner changes
            // and garrison supply commands from redirecting this recruit's food to another town.
            army.getClass().getMethod("observe",String.class,String.class,String.class,double.class).invoke(army,recruit.m_20148_().toString(),recruit.getOwnerUUID().toString(),"t:"+home,(double)recruit.getHunger());
            Object db=field(army,"db");
            db.getClass().getMethod("execute",String.class,Object[].class).invoke(db,"UPDATE army_units SET home=? WHERE id=?",new Object[]{"t:"+home,recruit.m_20148_().toString()});
            @SuppressWarnings("unchecked") var row=(Map<String,Object>)army.getClass().getMethod("unit",String.class).invoke(army,recruit.m_20148_().toString());
            long seq=((Number)row.get("feed_seq")).longValue()+1;
            @SuppressWarnings("unchecked") var receipt=(Map<String,Object>)feudal.getClass().getMethod("armyRequest",String.class,Map.class).invoke(feudal,"feed",Map.of("unit",recruit.m_20148_().toString(),"seq",seq));
            int amount=((Number)receipt.getOrDefault("amount",0)).intValue();if(amount>0)recruit.setHunger(Math.min(100,recruit.getHunger()+amount));
        }catch(Exception ex){if(bridge.ticks%1200<40)TownyRecruits.LOG.warn("Town provision unavailable: {}",Bridge.errorText(ex));}
    }
    static Object field(Object obj,String name)throws Exception {Class<?> type=obj.getClass();while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    static String inventory(ServerPlayer player){return player.m_150109_().m_36026_(new ListTag()).toString();}
    static void inventory(ServerPlayer player,String text)throws Exception {player.m_150109_().m_36035_(TagParser.m_129359_("{items:"+text+"}").m_128437_("items",10));player.m_150109_().m_6596_();player.f_36095_.m_38946_();}
    static String debit(String before,int gold)throws Exception {
        ListTag list=TagParser.m_129359_("{items:"+before+"}").m_128437_("items",10);int left=gold;
        for(int i=list.size()-1;i>=0&&left>0;i--){CompoundTag item=list.m_128728_(i);int slot=item.m_128445_("Slot")&255;if(slot>=36||!item.m_128461_("id").equals("minecraft:gold_ingot"))continue;int count=item.m_128445_("Count")&255,take=Math.min(left,count);left-=take;if(take==count)list.remove(i);else item.m_128344_("Count",(byte)(count-take));}
        return left==0?list.toString():null;
    }
    static void savePlayer(ServerPlayer player)throws Exception {Object bp=player.getClass().getMethod("getBukkitEntity").invoke(player);bp.getClass().getMethod("saveData").invoke(bp);}
    /** Shared advisory check for GUI and purchase. Purchase still validates and reserves atomically. */
    String refusal(Player player,String type)throws Exception {
        if(!bridge.ready||!bridge.censusReady()||config.dryRun)return "Облік війська ще не готовий або ввімкнено dry-run.";
        UUID town=bridge.towny.city(player.getUniqueId()),nation=bridge.snapshot.residents().get(player.getUniqueId());
        if(town==null||nation==null)return "Ваше місто має входити до нації Towny.";
        if(!menu.hirePermission(player))return config.message("permission");
        var limits=state.nations.get(nation);
        if(limits==null||limits.deleted||FactionEvents.recruitsFactionManager==null||FactionEvents.recruitsFactionManager.getFactionByStringID(limits.faction)==null)return "Фракція ще не готова.";
        if(!Set.of("recruit","shieldman").contains(type))return "Невідомий тип рекрута.";
        if(type.equals("shieldman")&&!limits.shieldman)return config.message("locked");
        if(!slots(nation,player.getUniqueId()))return config.message("limit");
        if(state.blocked(town,player.getUniqueId()))return "Попередня покупка очікує перевірки: /tarmyadmin pending.";
        if(state.balance(town).compareTo(config.cost(type))<0)return config.message("development");
        int gold=0;
        for(var item:player.getInventory().getStorageContents())if(item!=null&&item.getType()==org.bukkit.Material.GOLD_INGOT)gold+=item.getAmount();
        return gold<config.gold(type)?config.message("gold"):null;
    }
    Location placement(Location home,AbstractRecruitEntity recruit,UUID town)throws Exception {
        org.bukkit.World world=home.getWorld();
        if(world==null)throw new IllegalStateException("Світ міста недоступний.");
        ServerLevel level=(ServerLevel)world.getClass().getMethod("getHandle").invoke(world);
        // Bounded search on the city's spawn platform; never spawn in a player or another recruit.
        world.getChunkAt(home.getBlockX()>>4,home.getBlockZ()>>4);
        int[] heights={0,1,-1,2,-2};
        for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=home.getBlockX()+dx,z=home.getBlockZ()+dz;
            if(!world.isChunkLoaded(x>>4,z>>4))continue;
            for(int dy:heights){
                int y=home.getBlockY()+dy;
                if(y<=world.getMinHeight()||y+3>=world.getMaxHeight())continue;
                Location at=new Location(world,x+0.5,y,z+0.5,home.getYaw(),home.getPitch());
                if(!world.getWorldBorder().isInside(at)||!world.getBlockAt(x,y-1,z).getType().isSolid())continue;
                Object claim=bridge.towny.api.getClass().getMethod("getTownBlock",Location.class).invoke(bridge.towny.api,at);
                Object owner=claim==null?null:bridge.towny.call(claim,"getTownOrNull");
                if(owner==null||!town.equals(bridge.towny.id(owner)))continue;
                boolean clear=true;
                for(int h=0;h<3;h++){var block=world.getBlockAt(x,y+h,z);if(!block.isPassable()||block.isLiquid()){clear=false;break;}}
                if(!clear)continue;
                var hazards=java.util.Set.of(org.bukkit.Material.MAGMA_BLOCK,org.bukkit.Material.CACTUS,org.bukkit.Material.CAMPFIRE,org.bukkit.Material.SOUL_CAMPFIRE,org.bukkit.Material.POWDER_SNOW,org.bukkit.Material.FIRE,org.bukkit.Material.SOUL_FIRE,org.bukkit.Material.WITHER_ROSE,org.bukkit.Material.SWEET_BERRY_BUSH);
                if(hazards.contains(world.getBlockAt(x,y-1,z).getType())||hazards.contains(world.getBlockAt(x,y,z).getType()))continue;
                recruit.m_7678_(at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());
                if(!level.m_45786_(recruit))continue;
                var handle=(org.bukkit.entity.Entity)recruit.getClass().getMethod("getBukkitEntity").invoke(recruit);
                var box=handle.getBoundingBox().expand(0.2,0.1,0.2);
                if(world.getNearbyEntities(box).stream().anyMatch(e->e instanceof org.bukkit.entity.LivingEntity&&!e.getUniqueId().equals(recruit.m_20148_())))continue;
                return at;
            }
        }
        throw new IllegalStateException("Біля точки міста немає вільного безпечного місця. Звільніть майданчик.");
    }
    public void dismiss(Player player,UUID unit)throws Exception {
        bridge.refreshNow();
        if(!bridge.ready||!bridge.censusReady()||config.dryRun)throw new IllegalStateException("Облік війська ще не готовий.");
        var recruit=bridge.loaded.stream().filter(r->r.m_20148_().equals(unit)).findFirst().orElseThrow(()->new IllegalArgumentException("Рекрут не завантажений. Підійдіть до нього й відкрийте список знову."));
        var record=bridge.data.units.get(unit);UUID owner=player.getUniqueId();
        if(record==null||record.retired()||!owner.equals(record.owner())||!owner.equals(recruit.getOwnerUUID())||!recruit.getIsOwned()||!recruit.m_6084_()
            ||!Objects.equals(record.nation(),bridge.snapshot.residents().get(owner)))throw new IllegalArgumentException("Розпускати можна лише власного рекрута своєї нації.");
        if(RegimentExchange.locked(recruit.getGroup())||RegimentExchange.captain(owner))throw new IllegalArgumentException("Рекрут або командир бере участь у захищеному договорі. Спочатку завершіть договір.");
        // Persist retirement before changing the live entity. It also survives a crash before entity save.
        state.change(()->{
            var purchase=state.purchases.get(unit);
            if(purchase!=null&&RecruitmentState.pending(purchase))throw new IllegalStateException("Покупку ще не завершено.");
            if(purchase!=null)purchase.phase="VOID";
            state.homes.remove(unit);state.dismissed.add(unit);
        });
        bridge.data.put(unit,new ArmyData.Unit(record.owner(),record.nation(),true));
        recruit.disband(null,false,false);
        recruit.setAggroState(3);recruit.m_6710_(null);
        var team=recruit.m_5647_();if(team instanceof net.minecraft.world.scores.PlayerTeam old)bridge.server.m_129896_().m_6519_(recruit.m_20149_(),old);
        bridge.military.forget(recruit);
        // Retire the existing Feudal mirror as well; no additional food store or refund is created.
        try{Object feudal=bridge.towny.feudal();if(feudal!=null){Object army=field(field(feudal,"armyController"),"army");army.getClass().getMethod("retire",String.class).invoke(army,unit.toString());}}
        catch(Exception ex){TownyRecruits.LOG.warn("Feudal dismissal mirror unavailable: {}",Bridge.errorText(ex));}
        bridge.syncFactions();
        player.sendMessage("Рекрута розпущено; місце звільнено. Оплата за найм не повертається.");
    }
    public void purchase(Player player,String type){
        if(!org.bukkit.Bukkit.isPrimaryThread()){bridge.server.execute(()->purchase(player,type));return;}
        ServerPlayer nativePlayer=bridge.server.m_6846_().m_11259_(player.getUniqueId());if(nativePlayer==null)return;
        RecruitmentState.Purchase operation=null;AbstractRecruitEntity recruit=null;boolean added=false;
        try{
            bridge.refreshNow();if(!bridge.ready||!bridge.censusReady()||config.dryRun)throw new IllegalStateException("Облік війська ще не готовий або ввімкнено dry-run.");
            String refused=refusal(player,type);if(refused!=null){player.sendMessage(refused);return;}
            UUID town=bridge.towny.city(player.getUniqueId()),nation=bridge.snapshot.residents().get(player.getUniqueId());
            if(town==null||nation==null)throw new IllegalArgumentException("Ваше місто має входити до нації Towny.");
            if(!menu.hirePermission(player)){menu.message(player,"permission");return;}
            var limits=state.nations.get(nation);if(limits==null||FactionEvents.recruitsFactionManager.getFactionByStringID(limits.faction)==null)throw new IllegalStateException("Фракція ще не готова.");
            if(!Set.of("recruit","shieldman").contains(type))return;
            if(type.equals("shieldman")&&!limits.shieldman){menu.message(player,"locked");return;}
            if(!slots(nation,player.getUniqueId())){menu.message(player,"limit");return;}
            if(state.blocked(town,player.getUniqueId())){player.sendMessage("Попередня покупка очікує перевірки адміністратора: /tarmyadmin pending.");return;}
            var cost=config.cost(type);if(state.balance(town).compareTo(cost)<0){menu.message(player,"development");return;}
            String before=inventory(nativePlayer),after=debit(before,config.gold(type));if(after==null){menu.message(player,"gold");return;}
            Object city=bridge.towny.api.getClass().getMethod("getTown",UUID.class).invoke(bridge.towny.api,town);Location spawn=(Location)bridge.towny.call(city,"getSpawn");
            ServerLevel level=(ServerLevel)spawn.getWorld().getClass().getMethod("getHandle").invoke(spawn.getWorld());
            EntityType<?> entityType=type.equals("shieldman")?com.talhanation.recruits.init.ModEntityTypes.RECRUIT_SHIELDMAN.get():com.talhanation.recruits.init.ModEntityTypes.RECRUIT.get();
            if(entityType==null||!(entityType.m_20615_(level) instanceof AbstractRecruitEntity real))throw new IllegalStateException("Немає типу рекрута.");recruit=real;
            recruit.m_7678_(spawn.getX(),spawn.getY(),spawn.getZ(),spawn.getYaw(),spawn.getPitch());
            recruit.m_6518_(level,level.m_6436_(recruit.m_20097_()),MobSpawnType.COMMAND,null,null);
            spawn=placement(spawn,recruit,town);
            purchasing=player.getUniqueId();try{if(!recruit.hire(nativePlayer,null,false))throw new IllegalStateException("Найм скасовано.");}finally{purchasing=null;}
            recruit.m_7678_(spawn.getX(),spawn.getY(),spawn.getZ(),spawn.getYaw(),spawn.getPitch());
            operation=new RecruitmentState.Purchase();operation.unit=recruit.m_20148_();operation.player=player.getUniqueId();operation.town=town;operation.nation=nation;operation.type=type;operation.phase="PREPARED";operation.before=before;operation.after=after;operation.gold=config.gold(type);operation.development=cost;
            operation.world=level.m_46472_().m_135782_().toString();operation.x=spawn.getX();operation.y=spawn.getY();operation.z=spawn.getZ();
            state.reserve(operation);inventory(nativePlayer,after);savePlayer(nativePlayer);state.phase(operation.unit,"PAID");
            if(!level.m_7967_(recruit))throw new IllegalStateException("Створення рекрута скасовано.");added=true;
            state.phase(operation.unit,"COMMITTED");menu.message(player,"success");
            player.sendMessage("Витрачено: "+operation.gold+" золотих злитків, "+RecruitmentConfig.number(operation.development)+" розвитку. "
                +"Розвиток міста: "+RecruitmentConfig.number(state.balance(town))+". Війська нації: "+bridge.count(nation)+"/"+(limit(nation)==Integer.MAX_VALUE?"∞":limit(nation))
                +"; ваші: "+ownerCount(nation,player.getUniqueId())+"/"+(perPlayer(nation,player.getUniqueId())==Integer.MAX_VALUE?"∞":perPlayer(nation,player.getUniqueId()))+".");
            player.sendMessage("Місце появи: "+spawn.getWorld().getName()+" "+spawn.getBlockX()+", "+spawn.getBlockY()+", "+spawn.getBlockZ()+".");
        }catch(Exception ex){
            TownyRecruits.LOG.error("Recruit purchase failed: {}",Bridge.errorText(ex));
            try{
                if(recruit!=null){if(recruit.getIsOwned())recruit.disband(null,false,false);if(added)recruit.m_146870_();bridge.loaded.remove(recruit);bridge.data.remove(recruit.m_20148_());}
                if(operation!=null&&state.purchases.containsKey(operation.unit)){
                    operation=state.purchases.get(operation.unit);
                    String current=inventory(nativePlayer);
                    if(current.equals(operation.after)){inventory(nativePlayer,operation.before);savePlayer(nativePlayer);state.refund(operation.unit);}
                    else if(current.equals(operation.before))state.refund(operation.unit);
                    else{state.phase(operation.unit,"REVIEW");player.sendMessage("Покупку призупинено для перевірки; її ресурси зарезервовано. Адміністратор: /tarmyadmin pending.");return;}
                }
                menu.message(player,"failure");
            }catch(Exception rollback){TownyRecruits.LOG.error("Purchase needs recovery; original inventory is in the journal",rollback);player.sendMessage("Покупку призупинено для перевірки; адміністратор: /tarmyadmin pending.");}
        }
    }
    public void retire(UUID unit){var p=state.purchases.get(unit);if(p!=null&&Set.of("COMMITTED","SETTLED").contains(p.phase))state.phase(unit,"VOID");}
    boolean present(UUID unit){return bridge.data.diskUnits.contains(unit)||bridge.loaded.stream().anyMatch(r->r.m_20148_().equals(unit)&&r.getIsOwned()&&r.m_6084_());}
    void refundGold(ServerPlayer player,RecruitmentState.Purchase p)throws Exception {
        String before=inventory(player);var gold=new net.minecraft.world.item.ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:gold_ingot")),p.gold);
        player.m_150109_().m_36054_(gold);String after=inventory(player);inventory(player,before);
        if(!gold.m_41619_())throw new IllegalStateException("Для повернення золота звільніть місце в інвентарі.");
        state.change(()->{p.before=before;p.after=after;p.phase="REFUNDING";p.refunding=true;});inventory(player,after);savePlayer(player);state.refund(p.unit);
    }
    public void recover(ServerPlayer player){
        if(!bridge.censusReady())return;
        for(var p:List.copyOf(state.purchases.values()))if(p.player.equals(player.m_20148_())&&(RecruitmentState.pending(p)||p.phase.equals("COMMITTED")))try{
            String now=inventory(player);boolean exists=present(p.unit);
            if(p.phase.equals("COMMITTED")){if(exists)state.phase(p.unit,"SETTLED");else refundGold(player,p);continue;}
            if(p.refunding){if(now.equals(p.before)){inventory(player,p.after);savePlayer(player);}else if(!now.equals(p.after)){state.phase(p.unit,"REVIEW");continue;}state.refund(p.unit);continue;}
            if(exists){if(now.equals(p.before)){inventory(player,p.after);savePlayer(player);}else if(!now.equals(p.after)){state.phase(p.unit,"REVIEW");continue;}state.phase(p.unit,"COMMITTED");}
            else if(now.equals(p.after)){inventory(player,p.before);savePlayer(player);state.refund(p.unit);}
            else if(now.equals(p.before))state.refund(p.unit);
            else state.phase(p.unit,"REVIEW");
        }catch(Exception ex){TownyRecruits.LOG.error("Cannot recover purchase {}",p.unit,ex);}
    }
    void close(){if(menu!=null)menu.close();Policy.clearBindings();}
}
