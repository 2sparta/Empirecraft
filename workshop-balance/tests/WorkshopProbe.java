package ua.empirecraft.workshoptest;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import me.mss1r.siegeworks.menu.CastingMenu;
import ua.empirecraft.workshop.*;
import ua.empirecraft.armor.LayerData;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.IItemHandlerModifiable;
import melonslise.locks.common.init.LocksItems;
import melonslise.locks.common.item.*;
import melonslise.locks.common.config.LocksServerConfig;
import melonslise.locks.common.util.LocksUtil;

@Mod("empirecraft_workshop_test")
public final class WorkshopProbe {
    private static int checks;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Item item(String id){var key=new ResourceLocation(id);check(ForgeRegistries.ITEMS.containsKey(key),"Registered "+id);return ForgeRegistries.ITEMS.getValue(key);}
    static ItemStack stack(String id,int count){return new ItemStack(item(id),count);}
    static Block block(String id){return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)));}
    static int count(Player p,String id){int result=0;for(int i=0;i<36;i++){var s=p.m_150109_().m_8020_(i);if(Objects.equals(ForgeRegistries.ITEMS.getKey(s.m_41720_()),new ResourceLocation(id)))result+=s.m_41613_();}return result;}
    public WorkshopProbe(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    void started(ServerStartedEvent event){
        var server=event.getServer();
        try{
            ServerLevel level=server.m_129783_();var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("99000000-0000-0000-0000-000000000001"),"WorkshopTest"));
            player.m_7678_(.5,71,.5,0,0);
            research(level,player);casting(level,player);chests(level,player);
            // This Forge callback precedes Better Combat's bridged Fabric callback.
            // Invoke its exact normal resource/fallback initialization before inspecting the registry.
            WeaponRegistry.loadAttributes(server.m_177941_());
            if(net.bettercombat.BetterCombat.config.fallback_compatibility_enabled)net.bettercombat.logic.WeaponAttributesFallback.initialize();
            armorAndAttacks();
            report.put("checks",checks);report.put("status","passed");Files.writeString(Path.of("workshop-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("WORKSHOP_NATIVE_PASS checks="+checks);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("WORKSHOP_NATIVE_FAIL "+failure);}
        finally{server.m_7570_(false);}
    }
    static void research(ServerLevel level,FakePlayer player)throws Exception{
        BlockPos pos=new BlockPos(0,70,0);level.m_7731_(pos,block("empirecraft_forging:gunsmith_table").m_49966_(),3);
        var use=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredMethod("use",PlayerInteractEvent.RightClickBlock.class);use.setAccessible(true);
        var hit=new BlockHitResult(new Vec3(.5,71,.5),Direction.UP,pos,false);
        for(int selection:new int[]{4,7}){
            player.m_150109_().m_6211_();player.getPersistentData().m_128405_("ecGunRecipe",selection);
            player.m_150109_().m_6836_(0,stack("minecraft:paper",1));player.m_150109_().m_6836_(1,stack("minecraft:paper",2));player.m_150109_().m_6836_(2,stack("minecraft:ink_sac",1));player.m_150109_().m_6836_(3,stack("empirecraft_forging:lockwork",1));
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(count(player,"minecraft:paper")==3&&count(player,"empirecraft_forging:lockwork")==1,"Research without a sample consumes nothing");
            String lock=selection==4?"flintlock":"wheellock";player.m_150109_().m_6836_(4,stack("empirecraft_forging:"+lock,1));
            use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(count(player,"empirecraft_forging:"+lock)==0,"Research consumes matching sample exactly once");check(count(player,"minecraft:paper")==0,"Research consumes three papers including starting sheet");
            var work=player.m_21205_();check(work.m_41784_().m_128451_("Steps")==18,"Research takes 18 operations");
            String[] tools={"tfc:metal/hammer/wrought_iron","tfc:metal/chisel/wrought_iron","create:sand_paper"};
            for(int n=0;n<18;n++){player.m_21008_(InteractionHand.OFF_HAND,stack(tools[n%3],1));work.m_41784_().m_128356_("Next",level.m_46467_());use.invoke(null,new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));}
            check(count(player,"empirecraft_forging:"+lock+"_blueprint")==1,"Completed research delivers corresponding blueprint");
        }
        var manuals=Class.forName("ua.empirecraft.armory.GunsmithManual").getMethod("all").invoke(null);check(((List<?>)manuals).size()==10,"JEI covers eight operations and both researched variants");
        report.put("research","both sample-consuming routes passed");
    }
    static void casting(ServerLevel level,FakePlayer player)throws Exception{
        BlockPos pos=new BlockPos(0,70,0);level.m_7731_(pos,block("siegeworks:casting_table").m_49966_(),3);
        player.m_150109_().m_6211_();player.m_150109_().m_6836_(1,stack("minecraft:iron_ingot",64));
        var menu=new CastingMenu(4,player.m_150109_(),pos);player.f_36096_=menu;var access=(CastAccess)(Object)menu;
        access.workshop$diameter(125);check(menu.caliber()==125,"Direct diameter updates use exact valid value");access.workshop$diameter(126);check(menu.caliber()==125,"Non-multiple of five is rejected");access.workshop$diameter(500);check(menu.caliber()==125,"Out of range diameter is rejected");
        check(!menu.m_6366_(player,2)&&count(player,"minecraft:iron_ingot")==64,"Casting without fuel cannot spend metal");
        player.m_150109_().m_6836_(2,stack("minecraft:coal",4));int cost=menu.recipe().batch(125).cost();
        check(menu.m_6366_(player,2),"Fueled casting starts");var jobs=CastJobs.get(level);var job=jobs.jobs.get(pos);check(job!=null&&!job.ready(),"Casting never delivers instantly");check(count(player,"minecraft:iron_ingot")==64-cost&&count(player,"minecraft:coal")<4,"Materials and fuel paid once");
        check(!menu.m_6366_(player,2)&&jobs.jobs.size()==1,"Repeated casting cannot replace or duplicate paid batch");
        access.workshop$diameter(130);check(menu.caliber()==125,"Busy batch diameter stays fixed");
        player.f_36096_=player.f_36095_;jobs.tick(level);check(job.progress==1,"Casting advances after menu is closed");
        var serialized=jobs.m_7176_(new CompoundTag());var restored=CastJobs.load(serialized);check(restored.jobs.get(pos).progress==1,"Paid batch persists through NBT round trip");
        for(int i=1;i<job.duration;i++)jobs.tick(level);check(job.ready(),"Exactly the paid duration completes the batch");
        for(int slot=0;slot<36;slot++)player.m_150109_().m_6836_(slot,stack("minecraft:stone",64));
        check(!jobs.collect(pos,player)&&jobs.jobs.containsKey(pos),"Full inventory leaves output safely stored");
        player.m_150109_().m_6211_();check(jobs.collect(pos,player)&&!jobs.jobs.containsKey(pos),"Ready batch collected once");check(!jobs.collect(pos,player),"Second collection cannot duplicate output");
        player.m_150109_().m_6211_();player.m_150109_().m_6836_(1,stack("minecraft:iron_ingot",64));player.m_150109_().m_6836_(2,stack("minecraft:coal",4));
        check(jobs.start(level,pos,player,menu.recipe(),125),"Second job starts");level.m_7731_(pos,Blocks.f_50016_.m_49966_(),3);jobs.tick(level);check(!jobs.jobs.containsKey(pos),"Breaking a busy table removes its job and returns raw materials");
        report.put("casting","fuel, time, persistence, collection, invalid requests and break handling passed");
    }
    static void chests(ServerLevel level,FakePlayer player)throws Exception{
        int tested=0;var ids=ForgeRegistries.BLOCKS.getKeys().stream().filter(id->id.toString().matches("tfc:wood/(trapped_)?chest/.*")).sorted().toList();
        for(var id:ids){
            var pos=new BlockPos(10+(tested%8)*3,70,(tested/8)*3);var state=block(id.toString()).m_49966_();level.m_7731_(pos,state,3);var be=level.m_7702_(pos);var inv=(Container)be;
            check(inv.m_6643_()==27,"TFC single chest has 27 slots: "+id);inv.m_6836_(26,stack("minecraft:stone",7));
            var saved=be.serializeNBT();var restored=BlockEntity.m_155241_(pos,state,saved);check(((Container)restored).m_8020_(26).m_41613_()==7,"Last added chest row survives native NBT: "+id);
            var screen=((MenuProvider)be).m_7208_(20,player.m_150109_(),player);check(screen instanceof ChestMenu chest&&chest.m_39265_()==3&&screen.f_38839_.size()==63,"Three-row server menu: "+id);
            check(LocksServerConfig.canLock(level,pos),"TFC chest is lockable: "+id);tested++;
        }
        var pos=new BlockPos(10,70,20);var chest=(ChestBlock)block("tfc:wood/chest/oak");var left=chest.m_49966_().m_61124_(ChestBlock.f_51478_,Direction.NORTH).m_61124_(ChestBlock.f_51479_,ChestType.LEFT);var right=left.m_61124_(ChestBlock.f_51479_,ChestType.RIGHT);
        level.m_7731_(pos,left,3);level.m_7731_(new BlockPos(11,70,20),right,3);
        var provider=chest.m_7246_(left,level,pos);check(provider!=null,"Double chest provider exists");var menu=provider.m_7208_(30,player.m_150109_(),player);check(menu instanceof ChestMenu doubleChest&&doubleChest.m_39265_()==6&&menu.f_38839_.size()==90,"Double TFC chest has 54 accessible slots");
        var be=level.m_7702_(pos);var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow(IllegalStateException::new);check(handler.getSlots()==54,"Double chest automation has 54 slots");
        handler.insertItem(0,stack("minecraft:stone",4),false);player.m_7678_(10.5,71,20.5,0,0);
        var lock=new ItemStack(LocksItems.STEEL_LOCK.get());lock.m_41784_().m_128405_(LockingItem.KEY_ID,3030);player.m_21008_(InteractionHand.MAIN_HAND,lock);
        var hit=new BlockHitResult(new Vec3(10.5,70.5,20),Direction.NORTH,pos,false);var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);((LockItem)lock.m_41720_()).m_6225_(context);
        check(LocksUtil.locked(level,pos),"Real steel lock attaches to TFC chest");check(handler.extractItem(0,1,true).m_41619_()&&handler.extractItem(0,1,false).m_41619_(),"Cached TFC automation cannot extract from locked chest");check(handler.insertItem(0,stack("minecraft:stone",1),false).m_41613_()==1,"Cached TFC automation cannot insert into locked chest");
        if(handler instanceof IItemHandlerModifiable mod){var before=handler.getStackInSlot(0).m_41777_();mod.setStackInSlot(0,ItemStack.f_41583_);check(handler.getStackInSlot(0).m_41613_()==before.m_41613_(),"Direct TFC wrapper replacement is guarded");}
        var key=new ItemStack(LocksItems.KEY.get());key.m_41784_().m_128405_(LockingItem.KEY_ID,3030);player.m_21008_(InteractionHand.MAIN_HAND,key);((KeyItem)key.m_41720_()).m_6225_(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        check(!LocksUtil.locked(level,pos)&&handler.extractItem(0,1,false).m_41613_()==1,"Cached automation resumes after correct key unlocks");
        report.put("tfc_chest_variants",tested);
    }
    static void armorAndAttacks(){
        int armor=0,weapons=0;var missing=new ArrayList<String>();var tools=new ArrayList<String>();
        for(var entry:ForgeRegistries.ITEMS.getEntries()){
            String id=entry.getKey().m_135782_().toString();var item=entry.getValue();
            if(item instanceof ArmorItem&&id.startsWith("thirtyyearswar:")){
                String name=id.substring(id.indexOf(':')+1);int expected=name.contains("armour")||name.contains("cuirass")||name.contains("helmet")||name.contains("burgonet")||name.contains("decoration")?2:0;check(LayerData.rank(new ItemStack(item))==expected,"Thirty Years War layer: "+id);armor++;
            }
            if(item instanceof SwordItem||item instanceof AxeItem||item instanceof TridentItem){var attrs=WeaponRegistry.getAttributes(new ItemStack(item));if(attrs==null||attrs.attacks()==null||attrs.attacks().length==0){if(id.startsWith("tfc:metal/saw/"))tools.add(id);else missing.add(id);}else weapons++;}
            if(id.startsWith("chineseweapons:diamond_"))check(false,"Diamond Chinese item remains registered: "+id);
        }
        report.put("melee_without_attacks",missing);report.put("non_weapon_tools_without_combat_profile",tools);
        check(missing.isEmpty(),"All registered melee weapons have attacks: "+missing);
        for(String weapon:List.of("yatagan","kilij","topuz","devrish_axe","nakkareci")){var attrs=WeaponRegistry.getAttributes(stack("hamzabus_ottoman_mod:"+weapon,1));check(attrs!=null&&attrs.attacks().length>0,"Ottoman weapon has real attack animations: "+weapon);}
        var cloth=stack("thirtyyearswar:baroque_shirt",1);var mail=stack("minecraft:chainmail_chestplate",1);var chinese=stack("chineseweapons:iron_early_tang_dynasty_ming_guang_armor_chestplate",1);
        check(LayerData.canAdd(cloth,mail),"Thirty Years War clothing can be worn under mail");var pieces=new ArrayList<>(List.of(cloth,mail));var combined=LayerData.wrap(pieces);check(LayerData.canAdd(combined,chinese),"Chinese armor fits over clothing and mail");pieces.add(chinese);check(LayerData.pieces(LayerData.wrap(pieces)).size()==3,"Mixed three-layer armor retains all pieces");
        report.put("thirtyyearswar_armor_checked",armor);report.put("melee_weapons_with_attacks",weapons);report.put("melee_without_attacks",missing);
    }
}
