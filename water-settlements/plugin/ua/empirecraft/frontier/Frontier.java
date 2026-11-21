package ua.empirecraft.frontier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.invoke.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Server-only onboarding, bounded safe-location search and daily handbook recovery. */
public final class Frontier extends JavaPlugin implements Listener,CommandExecutor {
    org.bukkit.configuration.file.YamlConfiguration requests;
    final Map<UUID,Search> searching=new HashMap<>();
    NamespacedKey first,pending,cooldown,deathWorld,deathX,deathZ,respawnPending;
    record Anchor(World world,double x,double z,double min,double max,UUID town) {}
    @Override public void onEnable(){
        requests=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"village-requests.yml"));
        respawnPending=new NamespacedKey(this,"respawn_pending");first=new NamespacedKey(this,"first_spawn");pending=new NamespacedKey(this,"pending_first_spawn");cooldown=new NamespacedKey(this,"guides_at");deathWorld=new NamespacedKey(this,"death_world");deathX=new NamespacedKey(this,"death_x");deathZ=new NamespacedKey(this,"death_z");
        getConfig().addDefault("first-spawn-radius",30000);getConfig().options().copyDefaults(true);saveConfig();
        getServer().getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("guides")).setExecutor(this);Objects.requireNonNull(getCommand("village")).setExecutor(this);
    }
    World main(){World world=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NORMAL).findFirst().orElse(null);if(world==null)throw new IllegalStateException("No overworld");return world;}
    static Object call(Object o,String name,Object... args)throws ReflectiveOperationException{
        for(var m:o.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){boolean fits=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isInstance(args[i]))fits=false;if(fits)return m.invoke(o,args);}throw new NoSuchMethodException(name);
    }
    Object townApi()throws ReflectiveOperationException{
        var plugin=Bukkit.getPluginManager().getPlugin("Towny");if(plugin==null)throw new IllegalStateException("Towny unavailable");return Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,plugin.getClass().getClassLoader()).getMethod("getInstance").invoke(null);
    }
    Anchor home(Player p){
        try{Object resident=call(townApi(),"getResident",p.getUniqueId());if(resident==null)return null;Object town=call(resident,"getTownOrNull");if(town==null)return null;Location at=(Location)call(town,"getSpawn");return new Anchor(at.getWorld(),at.getX(),at.getZ(),0,2000,(UUID)call(town,"getUUID"));}
        catch(ReflectiveOperationException|IllegalStateException e){return null;}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e){
        var p=e.getEntity();var data=p.getPersistentDataContainer();Location at=p.getLocation();data.set(deathWorld,PersistentDataType.STRING,at.getWorld().getUID().toString());data.set(deathX,PersistentDataType.DOUBLE,at.getX());data.set(deathZ,PersistentDataType.DOUBLE,at.getZ());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){
        if(e.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH)return;
        Player p=e.getPlayer();Anchor anchor=home(p);
        if(anchor==null){var d=p.getPersistentDataContainer();String name=d.get(deathWorld,PersistentDataType.STRING);World w=name==null?null:Bukkit.getWorld(UUID.fromString(name));if(w==null)w=main();
            anchor=new Anchor(w,d.getOrDefault(deathX,PersistentDataType.DOUBLE,p.getLocation().getX()),d.getOrDefault(deathZ,PersistentDataType.DOUBLE,p.getLocation().getZ()),1000,3000,null);}
        p.getPersistentDataContainer().set(respawnPending,PersistentDataType.BYTE,(byte)1);var chosen=anchor;e.setRespawnLocation(chosen.world().getSpawnLocation());Bukkit.getScheduler().runTask(this,()->begin(p,chosen,false));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){
        var p=e.getPlayer();var data=p.getPersistentDataContainer();
        if((!p.hasPlayedBefore()||data.has(pending,PersistentDataType.BYTE))&&!data.has(first,PersistentDataType.BYTE)){
            data.set(pending,PersistentDataType.BYTE,(byte)1);World w=main();Location center=w.getWorldBorder().getCenter();
            double radius=Math.max(100,Math.min(100000,getConfig().getDouble("first-spawn-radius",30000)));
            begin(p,new Anchor(w,center.getX(),center.getZ(),0,radius,null),true);
        }else if(data.has(respawnPending,PersistentDataType.BYTE)){
            Anchor town=home(p);if(town==null){String id=data.get(deathWorld,PersistentDataType.STRING);World w=id==null?null:Bukkit.getWorld(UUID.fromString(id));if(w==null)w=main();town=new Anchor(w,data.getOrDefault(deathX,PersistentDataType.DOUBLE,p.getLocation().getX()),data.getOrDefault(deathZ,PersistentDataType.DOUBLE,p.getLocation().getZ()),1000,3000,null);}begin(p,town,false);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){Search search=searching.remove(e.getPlayer().getUniqueId());if(search!=null)search.cancel();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(searching.containsKey(e.getPlayer().getUniqueId())&&(e.getTo()!=null && (!e.getFrom().getWorld().equals(e.getTo().getWorld())||e.getFrom().distanceSquared(e.getTo())>0)))e.setTo(e.getFrom());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&searching.containsKey(p.getUniqueId()))e.setCancelled(true);}
    void begin(Player p,Anchor anchor,boolean initial){
        if(!p.isOnline())return;Search old=searching.remove(p.getUniqueId());if(old!=null)old.cancel();
        Search task=new Search(p,anchor,initial);searching.put(p.getUniqueId(),task);p.sendMessage("§6Пошук безпечного місця появи…");task.runTaskTimer(this,1,10);
    }
    final class Search extends org.bukkit.scheduler.BukkitRunnable {
        final Player player;final Anchor anchor;final boolean initial;int tries;Location fallback;
        int candidateX,candidateZ;java.util.concurrent.CompletableFuture<?> loading;final long deadline=System.currentTimeMillis()+120000;
        Search(Player p,Anchor a,boolean first){player=p;anchor=a;initial=first;}
        @Override public void run(){
            boolean exhausted=tries>=96 && loading==null || System.currentTimeMillis()>deadline;
            if(player.isOnline()&&exhausted&&fallback!=null){finish(fallback,true);return;}
            if(!player.isOnline()||exhausted){searching.remove(player.getUniqueId(),this);cancel();if(player.isOnline()){getLogger().warning("Safe spawn search exhausted for "+player.getUniqueId());player.sendMessage("§eНе знайдено безпечного місця. Адміністратор має перевірити межі й привати; повторний вхід продовжить пошук.");}return;}
            World w=anchor.world();
            if(loading==null){
                tries++;double[] point=SpawnMath.point(anchor.x(),anchor.z(),anchor.min(),anchor.max(),ThreadLocalRandom.current().nextDouble(),ThreadLocalRandom.current().nextDouble());candidateX=(int)Math.floor(point[0]);candidateZ=(int)Math.floor(point[1]);
                double distance=Math.hypot(candidateX+.5-anchor.x(),candidateZ+.5-anchor.z());if(distance<anchor.min()||distance>anchor.max())return;
                Location probe=new Location(w,candidateX+.5,100,candidateZ+.5);if(!w.getWorldBorder().isInside(probe)||Math.abs(candidateX)>10000000||Math.abs(candidateZ)>10000000)return;
                try{loading=prepareChunk(w,candidateX>>4,candidateZ>>4);}catch(Throwable ex){getLogger().warning("Safe spawn chunk request failed: "+ex);return;}return;
            }
            if(!loading.isDone())return;
            try{loading.join();}catch(RuntimeException failed){loading=null;return;}loading=null;
            int x=candidateX,z=candidateZ;if(!w.isChunkLoaded(x>>4,z>>4))return;
            // Inspect only the completed chunk on the main thread. No blocking getChunkAt or region scan.
            int y=w.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);if(y<w.getMinHeight()||y+2>=w.getMaxHeight())return;
            Location candidate=new Location(w,x+.5,y+1,z+.5);
            try{Object claim=call(townApi(),"getTownBlock",candidate);Object town=claim==null?null:call(claim,"getTownOrNull");if(town!=null&&!Objects.equals(anchor.town(),call(town,"getUUID")))return;}catch(ReflectiveOperationException|IllegalStateException ex){return;}
            var floor=w.getBlockAt(x,y,z);
            if(floor.getType()==Material.WATER&&w.getBlockAt(x,y+1,z).isEmpty()&&w.getBlockAt(x,y+2,z).isEmpty())fallback=candidate;if(!floor.getType().isSolid()||floor.isLiquid()||hazard(floor.getType())||floor.getType().name().endsWith("LEAVES"))return;
            for(int h=1;h<=2;h++){var block=w.getBlockAt(x,y+h,z);if(!block.isPassable()||block.isLiquid()||hazard(block.getType()))return;}
            Location result=new Location(w,x+.5,y+1,z+.5);
            try{Object claim=call(townApi(),"getTownBlock",result);Object town=claim==null?null:call(claim,"getTownOrNull");if(town!=null&&!Objects.equals(anchor.town(),call(town,"getUUID")))return;}catch(ReflectiveOperationException|IllegalStateException ex){return;}
            finish(result,false);
        }
        void finish(Location result,boolean water){
            if(!player.teleport(result,PlayerTeleportEvent.TeleportCause.PLUGIN)){
                if(tries>=96){searching.remove(player.getUniqueId(),this);cancel();getLogger().warning("Safe spawn teleport was cancelled for "+player.getUniqueId());player.sendMessage("§eПереміщення заблоковано іншим плагіном. Звернися до адміністратора; повторний вхід продовжить пошук.");}
                return;
            }
            if(water){player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING,1200,0));player.sendMessage("§eСуші в межах пошуку не знайдено. Поява на поверхні води; дихання під водою захищене на 60 секунд.");}
            player.getPersistentDataContainer().remove(respawnPending);
            if(initial){var d=player.getPersistentDataContainer();d.set(first,PersistentDataType.BYTE,(byte)1);d.remove(pending);}
            searching.remove(player.getUniqueId(),this);cancel();player.sendMessage("§aЗнайдено безпечне місце. Довідники: /guides (раз на 24 години).");
        }
    }
    static boolean hazard(Material m){return Set.of(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.FIRE,Material.SOUL_FIRE,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE).contains(m);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("village"))return village(sender,args);
        if(!(sender instanceof Player p)){sender.sendMessage("Only players.");return true;}
        long now=System.currentTimeMillis(),last=p.getPersistentDataContainer().getOrDefault(cooldown,PersistentDataType.LONG,0L);
        if(now-last<86400000L){p.sendMessage("§eДовідники будуть доступні через "+((86400000L-(now-last)+59999)/60000)+" хв.");return true;}
        try{
            List<ItemStack> books=new ArrayList<>();
            for(String id:List.of("empirecraft_guidebook:handbook","tfc:field_guide","chineseweapons:forging_notes","dragnlivestock:livestock_overhaul_guide")){
                ItemStack book=modItem("patchouli:guide_book");books.add(Bukkit.getUnsafe().modifyItemStack(book,"{\"patchouli:book\":\""+id+"\"}"));
            }
            for(String id:List.of("starcatcher:starcatcher_guide","siegeworks:siege_manual"))books.add(modItem(id));
            long empty=Arrays.stream(p.getInventory().getStorageContents()).filter(s->s==null||s.getType().isAir()).count();if(empty<books.size()){p.sendMessage("§eЗвільни "+books.size()+" слотів для всіх довідників.");return true;}
            var left=p.getInventory().addItem(books.toArray(ItemStack[]::new));if(!left.isEmpty())throw new IllegalStateException("Inventory changed");
            p.getPersistentDataContainer().set(cooldown,PersistentDataType.LONG,now);p.saveData();p.sendMessage("§aВидано всі актуальні довідники. Наступна видача через 24 години.");
        }catch(Throwable ex){getLogger().warning("Guide delivery failed: "+ex);p.sendMessage("§cДовідники не видано: адміністратору слід перевірити встановлені моди.");}return true;
    }
    void saveRequests()throws java.io.IOException{
        java.nio.file.Path file=getDataFolder().toPath().resolve("village-requests.yml"),temp=file.resolveSibling("village-requests.yml.tmp");
        java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(temp,requests.saveToString());java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    boolean village(CommandSender sender,String[] args){
        try{
            if(args.length==3&&args[0].equalsIgnoreCase("apply")&&sender instanceof Player p){
                String id=args[1].toLowerCase(java.util.Locale.ROOT);if(!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("ID: a-z, 0-9, _ або -");int houses=Integer.parseInt(args[2]);if(houses<1||houses>10000)throw new IllegalArgumentException("Кількість домів: 1–10000");
                Anchor town=home(p);if(town==null)throw new IllegalArgumentException("Для заявки потрібне місто з визначеною точкою появи.");
                String owner=requests.getString(id+".owner");if(owner!=null&&!owner.equals(p.getUniqueId().toString()))throw new IllegalArgumentException("Цей ID уже використаний.");
                requests.set(id+".owner",p.getUniqueId().toString());requests.set(id+".town",town.town().toString());requests.set(id+".houses",houses);requests.set(id+".location",p.getLocation().getWorld().getName()+" "+p.getLocation().getBlockX()+" "+p.getLocation().getBlockY()+" "+p.getLocation().getBlockZ());requests.set(id+".state","PENDING");saveRequests();sender.sendMessage("§aЗаявку збережено. Адміністратор має оглянути житло та погодити кількість домів.");return true;
            }
            if(!sender.isOp()&&!sender.hasPermission("empirecraft.frontier.approve")){sender.sendMessage("/village apply ID кількість_домів");return true;}
            if(args.length==1&&args[0].equalsIgnoreCase("requests")){for(String id:requests.getKeys(false))sender.sendMessage(id+": "+requests.getString(id+".state")+" · домів "+requests.getInt(id+".houses")+" · "+requests.getString(id+".location"));return true;}
            if((args.length==3||args.length==4)&&args[0].equalsIgnoreCase("approve")){
                String id=args[1].toLowerCase(java.util.Locale.ROOT);if(!requests.isConfigurationSection(id))throw new IllegalArgumentException("Немає такої заявки.");int houses=Integer.parseInt(args[2]),hours=args.length==4?Integer.parseInt(args[3]):6;
                Object server=call(Bukkit.getServer(),"getServer");Class<?> api=Class.forName("ua.townyrecruits.VillageApi",true,server.getClass().getClassLoader());
                Object result=api.getMethod("approve",String.class,UUID.class,int.class,int.class).invoke(null,id,UUID.fromString(requests.getString(id+".town")),houses,hours);
                requests.set(id+".state","APPROVED");requests.set(id+".approved-houses",houses);saveRequests();sender.sendMessage("§aПогоджено "+id+": "+result);return true;
            }
            sender.sendMessage("/village requests | approve ID домів [годин_відновлення]; /tarmyadmin villages");
        }catch(Exception e){sender.sendMessage("§cЗаявку не завершено: "+e.getMessage());}return true;
    }
    static java.util.concurrent.CompletableFuture<?> prepareChunk(World world,int x,int z)throws Throwable{
        var lookup=MethodHandles.publicLookup();Class<?> level=Class.forName("net.minecraft.server.level.ServerLevel"),cache=Class.forName("net.minecraft.server.level.ServerChunkCache"),status=Class.forName("net.minecraft.world.level.chunk.ChunkStatus");
        Object handle=lookup.findVirtual(world.getClass(),"getHandle",MethodType.methodType(level)).invoke(world);
        Object source=lookup.findVirtual(level,"m_7726_",MethodType.methodType(cache)).invoke(handle);
        Object full=lookup.findStaticGetter(status,"f_62326_",status).invoke();
        return (java.util.concurrent.CompletableFuture<?>)lookup.findVirtual(cache,"m_8431_",MethodType.methodType(java.util.concurrent.CompletableFuture.class,int.class,int.class,status,boolean.class)).invoke(source,x,z,full,true);
    }
    static ItemStack modItem(String id)throws Throwable{
        var lookup=MethodHandles.publicLookup();Class<?> registry=Class.forName("net.minecraftforge.registries.IForgeRegistry"),rl=Class.forName("net.minecraft.resources.ResourceLocation"),stack=Class.forName("net.minecraft.world.item.ItemStack"),itemLike=Class.forName("net.minecraft.world.level.ItemLike");
        Object items=lookup.unreflectGetter(Class.forName("net.minecraftforge.registries.ForgeRegistries").getField("ITEMS")).invoke();Object key=lookup.findConstructor(rl,MethodType.methodType(void.class,String.class)).invoke(id);Object item=lookup.findVirtual(registry,"getValue",MethodType.methodType(Object.class,rl)).invoke(items,key);
        Object actual=lookup.findVirtual(registry,"getKey",MethodType.methodType(rl,Object.class)).invoke(items,item);if(!key.equals(actual))throw new IllegalArgumentException("Missing guide "+id);
        Object nativeStack=lookup.findConstructor(stack,MethodType.methodType(void.class,itemLike)).invoke(item);Class<?> craft=Class.forName(Bukkit.getServer().getClass().getPackageName()+".inventory.CraftItemStack");return (ItemStack)lookup.findStatic(craft,"asBukkitCopy",MethodType.methodType(ItemStack.class,stack)).invoke(nativeStack);
    }
    @Override public void onDisable(){for(Search s:searching.values())s.cancel();searching.clear();}
}
