package ua.empirecraft.tfc.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import net.minecraftforge.items.IItemHandler;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.foundation.block.IBE;
import net.dries007.tfc.common.capabilities.food.*;
import net.dries007.tfc.common.entities.predator.Predator;
import ua.empirecraft.tfc.*;
import com.dragn0007.dragnlivestock.entities.chicken.OChicken;

@Mod("empirecraft_tfc_test")
public final class Smoke {
    static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"empirecraft_tfc_test");
    static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"empirecraft_tfc_test");
    static final RegistryObject<Block> MOTOR=BLOCKS.register("motor",Motor::new);
    static final RegistryObject<BlockEntityType<MotorBE>> MOTOR_BE=BES.register("motor",()->BlockEntityType.Builder.of(MotorBE::new,MOTOR.get()).build(null));
    static float rpm=32;
    static float capacity=1024;
    static int assertions;
    static final BlockPos ORIGIN=new BlockPos(96,80,96);
    private MinecraftServer server;private int ticks;private boolean failed;private int beforeStop;
    private IItemHandler quern;
    private final int phase=Integer.getInteger("empirecraft.tfc.phase",1);
    public Smoke(){
        var mod=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(mod);BES.register(mod);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e)->server=e.getServer());
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static Item item(String id){var i=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));check(i!=null&&i!=Items.AIR,"item exists "+id);return i;}
    private static Block block(String id){var b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(b!=null&&b!=Blocks.AIR,"block exists "+id);return b;}
    private static Entity entity(String id,ServerLevel level){
        Entity actor=Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id))).create(level);
        // These actors exercise conversion/AI/breeding, independently of the separate
        // fauna admission suite. Restore them as existing animals while its census runs.
        actor.getPersistentData().putBoolean("EmpirecraftFaunaExisting",true);return actor;
    }
    private void tick(TickEvent.ServerTickEvent e){
        if(server==null||failed||e.phase!=TickEvent.Phase.END)return;
        try{
            ticks++;
            if(phase==2){if(ticks==20){restart();finish();}return;}
            if(ticks==20)setup();
            if(ticks==60){
                var drive=(QuernDriveBlockEntity)server.overworld().getBlockEntity(ORIGIN.above(2));
                var motor=(MotorBE)server.overworld().getBlockEntity(ORIGIN);
                System.out.println("TFC_CREATE_NETWORK source="+motor.getSpeed()+" drive="+drive.getSpeed()+" stress="+drive.isOverStressed()+" network="+drive.getOrCreateNetwork().sources.size());
                check(drive.getSpeed()!=0,"real Create network drives adapter");
            }
            if(ticks==200){
                check(!quern.getStackInSlot(2).isEmpty(),"powered native TFC quern produces flour");
                check(quern.getStackInSlot(0).getDamageValue()>0,"native handstone durability is consumed");
                var relay=server.overworld().getBlockEntity(ORIGIN.above(2)).getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.EAST)
                    .orElseThrow(()->new AssertionError("drive output relay"));
                boolean extraction=false;for(int slot=0;slot<relay.getSlots();slot++)if(!relay.extractItem(slot,1,true).isEmpty())extraction=true;
                check(extraction,"side extraction remains available with the drive below the quern");
                rpm=0;((MotorBE)server.overworld().getBlockEntity(ORIGIN)).updateGeneratedRotation();
                beforeStop=quern.getStackInSlot(2).getCount();
            }
            if(ticks==320){
                check(quern.getStackInSlot(2).getCount()==beforeStop&&!quern.getStackInSlot(1).isEmpty(),"unpowered quern pauses with input remaining");
                rpm=-32;((MotorBE)server.overworld().getBlockEntity(ORIGIN)).updateGeneratedRotation();
            }
            if(ticks==440){
                check(quern.getStackInSlot(2).getCount()>beforeStop,"reverse rotation also grinds");
                var source=(MotorBE)server.overworld().getBlockEntity(ORIGIN);capacity=0;source.getOrCreateNetwork().updateCapacityFor(source,0);
            }
            if(ticks==460){
                check(((QuernDriveBlockEntity)server.overworld().getBlockEntity(ORIGIN.above(2))).isOverStressed(),"drive consumes real Create stress capacity");
                terrain();husbandry();finish();
            }
        }catch(Throwable ex){failed=true;System.out.println("TFC_NATIVE_FAIL phase="+phase);ex.printStackTrace();server.halt(false);}
    }
    private void setup(){
        ServerLevel level=server.overworld();
        // The isolated test rig must tick even when the generated spawn is elsewhere.
        level.setChunkForced(ORIGIN.getX()>>4,ORIGIN.getZ()>>4,true);
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("92bafec8-c37b-4d14-bc96-d06418e57afa"),"TfcTest"));
        TFCFoodData.replaceFoodStats(player);check(!(player.getFoodData() instanceof TFCFoodData),"vanilla food handler survives login replacement call");
        var hydration=player.getCapability(sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstProvider.THIRST_CAPABILITY)
            .orElseThrow(()->new AssertionError("LSO thirst capability"));
        hydration.setHydrationLevel(5);
        net.dries007.tfc.util.Drinkable.get(net.minecraft.world.level.material.Fluids.WATER).onDrink(player,25);
        check(hydration.getHydrationLevel()>5,"TFC drinks refill the existing LSO thirst capability");
        check(player.getMaxHealth()==20,"vanilla maximum player health");
        check(level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION),"TFC does not disable vanilla regeneration");
        check(new ItemStack(Items.IRON_INGOT).getMaxStackSize()==64,"vanilla ingots stack to 64");
        check(new ItemStack(item("tfc:metal/ingot/copper")).getMaxStackSize()==64,"retained TFC compatibility ingots stack to 64");
        BlockPos soil=ORIGIN.offset(10,0,0);level.setBlock(soil,block("tfc:farmland/loam").defaultBlockState(),3);
        check(Blocks.WHEAT.defaultBlockState().canSurvive(level,soil.above()),"existing vanilla crops survive on TFC farmland");
        float temperature=net.dries007.tfc.util.climate.Climate.getTemperature(level,soil);
        check(Float.isFinite(temperature),"TFC agriculture reads finite temperature from LSO without recursion");
        var food=FoodCapability.get(new ItemStack(item("tfc:food/barley_flour")));
        check(food!=null&&!food.isRotten()&&food.getRottenDate()==Long.MAX_VALUE,"food never expires");
        for(String id:RecipePolicy.ALIASES.values())check(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))||ForgeRegistries.FLUIDS.containsKey(new ResourceLocation(id)),"canonical registry target "+id);
        Set<String> guns=Set.of("musketmod:musket","musketmod:blunderbuss","musketmod:pistol","musketmod:musket_with_scope","musketmod:musket_with_bayonet","medieval_boomsticks:arquebus","medieval_boomsticks:handgonne");
        Map<String,Integer> counts=new HashMap<>();int stone=0;
        for(var recipe:server.getRecipeManager().getRecipes()){
            String type=Objects.toString(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()),"");
            check(!Set.of("tfc:anvil","tfc:welding","tfc:collapse","tfc:landslide","overgeared:rock_knapping").contains(type),"recipe type allowed "+recipe.getId());
            ItemStack out=recipe.getResultItem(level.registryAccess());if(out.isEmpty())continue;
            String id=Integration.id(out.getItem());
            check(!RecipePolicy.forbidden(id),"recipe output allowed "+recipe.getId());
            check(!id.equals("minecraft:fishing_rod"),"no vanilla fishing rod craft "+recipe.getId());
            if(guns.contains(id)){counts.merge(id,1,Integer::sum);check(type.equals("overgeared:forging"),"firearm requires native forging "+recipe.getId());}
            if(recipe.getId().toString().equals("empirecraft_tfc:stone_pickaxe_head"))stone++;
        }
        check(guns.stream().allMatch(g->counts.getOrDefault(g,0)==1),"all seven firearms have exactly one complex crafting route");
        check(stone==1,"stone pickaxe uses TFC knapping");
        if(Boolean.getBoolean("empirecraft.current.update"))CurrentUpdateChecks.run(server,player);
        for(String id:RecipePolicy.overrideIds())check(server.getRecipeManager().byKey(new ResourceLocation(id)).isPresent(),"generated recipe loaded "+id);
        for(var deposit:OneTimeTerrain.DEPOSITS)block(deposit.block());
        Entity bear=entity("naturalist:bear",level);bear.moveTo(96,84,96,0,0);UUID id=bear.getUUID();level.addFreshEntity(bear);
        check(level.getEntity(id) instanceof Predator,"Naturalist bear becomes an actual TFC predator");
        Predator nativeBear=(Predator)level.getEntity(id);
        check(nativeBear.getMaxHealth()==40&&nativeBear.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==6,
            "migrated bear retains TFC health and attack attributes");
        check(nativeBear.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).isPresent(),"migrated bear has a native home memory");
        player.moveTo(96,84,97,0,0);check(((Predator)level.getEntity(id)).pinPlayer(player),"native bear pin mechanic");
        var cub=(AgeableMob)entity("naturalist:bear",level);cub.setBaby(true);UUID cubId=cub.getUUID();cub.moveTo(100,84,96,0,0);level.addFreshEntity(cub);
        check(((Predator)level.getEntity(cubId)).isBaby(),"bear migration preserves cub age");
        for(String old:List.of("naturalist:boar","naturalist:lion")){
            Entity a=entity(old,level);UUID uid=a.getUUID();a.moveTo(98,84,96,0,0);level.addFreshEntity(a);
            check(AnimalIntegration.id(level.getEntity(uid)).startsWith("tfc:"),"native animal replacement "+old);
        }
        for(int y=0;y<4;y++)level.setBlock(ORIGIN.above(y),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(ORIGIN,MOTOR.get().defaultBlockState(),3);
        level.setBlock(ORIGIN.above(),block("create:shaft").defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y),3);
        level.setBlock(ORIGIN.above(2),Integration.DRIVE.get().defaultBlockState(),3);
        level.setBlock(ORIGIN.above(3),block("tfc:quern").defaultBlockState(),3);
        quern=level.getBlockEntity(ORIGIN.above(3)).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("quern inventory"));
        check(quern.insertItem(0,new ItemStack(item("tfc:handstone")),false).isEmpty(),"insert handstone");
        check(quern.insertItem(1,new ItemStack(item("tfc:food/wheat_grain"),8),false).isEmpty(),"insert native TFC grain");
        ((MotorBE)level.getBlockEntity(ORIGIN)).updateGeneratedRotation();
        System.out.println("TFC_FIXTURE_INITIAL_SETUP_COMPLETE");
    }
    private void terrain(){
        var level=server.overworld();var chunk=level.getChunk(40,40);int ox=640,oz=640;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            for(int y=-60;y<64;y++)chunk.setBlockState(new BlockPos(ox+x,y,oz+z),Blocks.STONE.defaultBlockState(),false);
            chunk.setBlockState(new BlockPos(ox+x,64,oz+z),Blocks.GRASS_BLOCK.defaultBlockState(),false);
        }
        check(OneTimeTerrain.process(level,chunk),"first terrain pass runs");
        int stones=0;for(int x=0;x<16;x++)for(int z=0;z<16;z++)
            if(Objects.toString(ForgeRegistries.BLOCKS.getKey(chunk.getBlockState(new BlockPos(ox+x,65,oz+z)).getBlock()),"").equals("tfc:rock/loose/granite"))stones++;
        check(stones>0,"terrain pass places loose surface rocks on the ground");
        int found=count(chunk,true);check(found>0,"terrain pass places permitted deposits");
        check(!OneTimeTerrain.process(level,chunk),"completed terrain pass never refills mined ores");
        check(count(chunk,false)==0,"mined deposits remain absent");chunk.setUnsaved(true);
    }
    private static int count(LevelChunk chunk,boolean remove){
        Set<Block> ores=new HashSet<>();for(var d:OneTimeTerrain.DEPOSITS)ores.add(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(d.block())));
        int count=0;int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-60;y<64;y++){
            BlockPos p=new BlockPos(ox+x,y,oz+z);if(ores.contains(chunk.getBlockState(p).getBlock())){count++;if(remove)chunk.setBlockState(p,Blocks.STONE.defaultBlockState(),false);}
        }return count;
    }
    private void husbandry(){
        var level=server.overworld();Animal female=(Animal)entity("dragnlivestock:o_cow",level),male=(Animal)entity("dragnlivestock:o_cow",level);
        female.moveTo(120,80,120,0,0);male.moveTo(121,80,120,0,0);level.addFreshEntity(female);level.addFreshEntity(male);
        try{female.getClass().getMethod("setGender",int.class).invoke(female,0);male.getClass().getMethod("setGender",int.class).invoke(male,1);}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        var child=female.getBreedOffspring(level,male);check(child!=null,"DLO produces genetic child");
        var birth=new BabyEntitySpawnEvent(female,male,child);MinecraftForge.EVENT_BUS.post(birth);
        check(birth.isCanceled(),"instant DLO birth becomes TFC gestation");
        check(female.getPersistentData().getCompound(AnimalIntegration.STATE).contains("Pregnancy")||male.getPersistentData().getCompound(AnimalIntegration.STATE).contains("Pregnancy"),"pregnancy preserves serialized genetic children");
        BlockPos nest=new BlockPos(126,80,126);level.setBlock(nest,block("tfc:nest_box").defaultBlockState(),3);
        OChicken hen=(OChicken)entity("dragnlivestock:o_chicken",level),rooster=(OChicken)entity("dragnlivestock:o_chicken",level);
        hen.moveTo(126,80,126,0,0);rooster.moveTo(127,80,126,0,0);AnimalIntegration.layEgg(hen,rooster,level);
        var inv=level.getBlockEntity(nest).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("nest inventory"));
        boolean egg=false;for(int i=0;i<inv.getSlots();i++){var data=net.dries007.tfc.common.capabilities.egg.EggCapability.get(inv.getStackInSlot(i));if(data!=null&&data.isFertilized())egg=true;}
        check(egg,"DLO genetic egg is stored in a native TFC nest");
        equinePregnancy();
    }
    private void equinePregnancy(){
        var level=server.overworld();
        var mare=(net.minecraft.world.entity.animal.horse.AbstractHorse)entity("dragnlivestock:o_horse",level);
        var sire=(net.minecraft.world.entity.animal.horse.AbstractHorse)entity("dragnlivestock:o_horse",level);
        try{mare.getClass().getMethod("setGender",int.class).invoke(mare,0);sire.getClass().getMethod("setGender",int.class).invoke(sire,1);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
        mare.moveTo(132,80,132,0,0);sire.moveTo(133,80,132,0,0);level.addFreshEntity(mare);level.addFreshEntity(sire);
        var child=mare.getBreedOffspring(level,sire);check(child!=null,"DLO creates a genetic foal");
        var conception=new BabyEntitySpawnEvent(mare,sire,child);MinecraftForge.EVENT_BUS.post(conception);
        var state=mare.getPersistentData().getCompound(AnimalIntegration.STATE);
        check(conception.isCanceled()&&state.contains("Pregnancy"),"TFC owns the new equine pregnancy");
        var genes=ua.empirecraft.equine.EquineGenetics.data(mare);
        check(genes.getList("Children",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),"equine addon does not duplicate the TFC pregnancy");
        var saved=state.getCompound("Pregnancy").getList("Children",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        var inherited=saved.getCompound("ForgeData").getCompound("EmpirecraftEquine");
        check(inherited.hasUUID("Mother")&&inherited.getUUID("Mother").equals(mare.getUUID())
            &&inherited.hasUUID("Father")&&inherited.getUUID("Father").equals(sire.getUUID()),"foal keeps the equine pedigree");
        check(!inherited.getString("Genes").isEmpty(),"foal keeps inherited performance genes");
        UUID foal=saved.getUUID("UUID");state.getCompound("Pregnancy").putLong("Due",net.dries007.tfc.util.calendar.Calendars.SERVER.getTotalDays());
        mare.tickCount=20;MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mare));
        check(level.getEntity(foal) instanceof net.minecraft.world.entity.animal.horse.AbstractHorse,"TFC foal is born through the real fauna gate");
        check(!state.contains("Pregnancy")&&genes.getList("Children",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),"completed birth never becomes another pregnancy");
        var existing=new net.minecraft.nbt.ListTag();existing.add(saved.copy());genes.put("Children",existing);
        var next=mare.getBreedOffspring(level,sire);MinecraftForge.EVENT_BUS.post(new BabyEntitySpawnEvent(mare,sire,next));
        check(!state.contains("Pregnancy")&&genes.getList("Children",net.minecraft.nbt.Tag.TAG_COMPOUND).size()==1,"existing equine pregnancy is preserved without a second conception");
        genes.remove("Children");
    }
    private void restart(){
        var level=server.overworld();var chunk=level.getChunk(40,40);
        check(count(chunk,false)==0,"mined ores stay absent after restart");check(!OneTimeTerrain.process(level,chunk),"terrain completion survives disk reload");
        Entity old=entity("naturalist:bear",level);old.moveTo(ORIGIN.getX()+3,84,ORIGIN.getZ(),0,0);
        var loading=new net.minecraftforge.event.entity.EntityJoinLevelEvent(old,level,true);
        MinecraftForge.EVENT_BUS.post(loading);
        check(loading.isCanceled()&&level.getEntity(old.getUUID()) instanceof Predator,"saved bear loads through native migration");
        check(level.getEntity(old.getUUID()).getPersistentData().getBoolean("EmpirecraftFaunaExisting"),"saved migration preserves existing-animal admission");
    }
    private void finish(){System.out.println("TFC_NATIVE_PASS phase="+phase+" assertions="+assertions);server.halt(false);}
    public static final class Motor extends KineticBlock implements IBE<MotorBE>{
        Motor(){super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));}
        @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
        @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader l,BlockPos p,BlockState s,Direction f){return f==Direction.UP;}
        @Override public Class<MotorBE> getBlockEntityClass(){return MotorBE.class;}
        @Override public BlockEntityType<? extends MotorBE> getBlockEntityType(){return MOTOR_BE.get();}
    }
    public static final class MotorBE extends GeneratingKineticBlockEntity{
        MotorBE(BlockPos p,BlockState s){super(MOTOR_BE.get(),p,s);}
        @Override public float getGeneratedSpeed(){return rpm;}
        @Override public float calculateAddedStressCapacity(){lastCapacityProvided=Smoke.capacity;return Smoke.capacity;}
    }
}
