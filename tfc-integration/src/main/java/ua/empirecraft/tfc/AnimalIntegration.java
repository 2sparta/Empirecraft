package ua.empirecraft.tfc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.blockentities.NestBoxBlockEntity;
import net.dries007.tfc.common.capabilities.egg.EggCapability;
import net.dries007.tfc.common.entities.predator.Predator;
import net.dries007.tfc.common.entities.prey.WildAnimal;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.config.animals.*;
import net.dries007.tfc.util.calendar.Calendars;
import ua.empirecraft.tfc.mixin.MobGoals;
import com.dragn0007.dragnlivestock.entities.chicken.OChicken;
import ua.empirecraft.equine.EquineGenetics;

/** Adapts TFC husbandry rules while retaining DLO's entity identity and genes. */
public final class AnimalIntegration {
    public static final String STATE="EmpirecraftTfcAnimal";
    public static final Map<String,String> MIGRATIONS=Map.ofEntries(
        Map.entry("naturalist:bear","tfc:grizzly_bear"),Map.entry("minecraft:polar_bear","tfc:polar_bear"),
        Map.entry("naturalist:boar","tfc:boar"),Map.entry("naturalist:lion","tfc:lion"),
        Map.entry("tfc:deer","naturalist:deer"),Map.entry("tfc:caribou","dragnlivestock:caribou"),
        Map.entry("tfc:duck","naturalist:duck"),Map.entry("tfc:penguin","ecologics:penguin"),
        Map.entry("tfc:fox","minecraft:fox"),Map.entry("tfc:wolf","minecraft:wolf"),
        Map.entry("tfc:rabbit","dragnlivestock:o_rabbit"),Map.entry("tfc:chicken","dragnlivestock:o_chicken"),
        Map.entry("tfc:cow","dragnlivestock:o_cow"),Map.entry("tfc:pig","dragnlivestock:o_pig"),
        Map.entry("tfc:sheep","dragnlivestock:o_sheep"),Map.entry("tfc:goat","dragnlivestock:o_goat"),
        Map.entry("tfc:horse","dragnlivestock:o_horse"),Map.entry("tfc:donkey","dragnlivestock:o_donkey"),
        Map.entry("tfc:mule","dragnlivestock:o_mule"),Map.entry("tfc:cod","dragnlivestock:o_cod"),
        Map.entry("tfc:salmon","dragnlivestock:o_salmon"),Map.entry("tfc:frog","dragnlivestock:o_frog"),
        Map.entry("tfc:turtle","minecraft:turtle"),Map.entry("tfc:manatee","spawn:sea_cow"),
        Map.entry("tfc:largemouth_bass","naturalist:bass"),Map.entry("tfc:smallmouth_bass","naturalist:bass"),
        Map.entry("tfc:cat","minecraft:cat"),Map.entry("tfc:dog","minecraft:wolf"),
        Map.entry("tfc:panda","minecraft:panda"),Map.entry("tfc:ocelot","minecraft:ocelot"),
        Map.entry("tfc:dolphin","minecraft:dolphin"),Map.entry("tfc:squid","minecraft:squid"),
        Map.entry("tfc:pufferfish","minecraft:pufferfish"),Map.entry("tfc:tropical_fish","minecraft:tropical_fish"));
    private static final Set<String> DEFENDERS=Set.of("naturalist:elephant","naturalist:rhino","naturalist:hippo");
    private final Set<Mob> configured=Collections.newSetFromMap(new WeakHashMap<>());
    public AnimalIntegration(){
        var bus=MinecraftForge.EVENT_BUS;bus.addListener(EventPriority.LOW,this::join);
        bus.addListener(this::feed);bus.addListener(this::birth);bus.addListener(this::tick);bus.addListener(this::hurt);
        bus.addGenericListener(ItemStack.class,this::eggCapability);
    }
    public static String id(Entity e){return Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()),"");}
    private static boolean domestic(Animal a){return id(a).startsWith("dragnlivestock:o_")&&!id(a).matches(".*(?:cod|salmon|frog|bee)$");}
    public static void attributes(EntityAttributeModificationEvent e){
        for(String id:DEFENDERS){var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
            if(type!=null){@SuppressWarnings("unchecked") var living=(EntityType<? extends LivingEntity>)type;
                if(!e.has(living,Attributes.ATTACK_DAMAGE))e.add(living,Attributes.ATTACK_DAMAGE,6);}}
    }
    public static void spawnPlacements(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e){
        for(var entry:ForgeRegistries.ENTITY_TYPES.getEntries()){
            var id=entry.getKey().location();var type=entry.getValue();
            if(!id.getNamespace().equals("tfc")||MIGRATIONS.containsKey(id.toString())||type.getCategory()==MobCategory.MISC)continue;
            @SuppressWarnings("unchecked") EntityType<Mob> mob=(EntityType<Mob>)type;
            boolean water=type.getCategory()==MobCategory.WATER_CREATURE||type.getCategory()==MobCategory.WATER_AMBIENT||type.getCategory()==MobCategory.UNDERGROUND_WATER_CREATURE;
            e.register(mob,water?SpawnPlacements.Type.IN_WATER:SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t,level,reason,pos,random)->water?level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                    &&(!id.getPath().equals("octopoteuthis")||pos.getY()<level.getLevel().getSeaLevel()-20)
                    :level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.VALID_SPAWN),
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
    private void join(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide)return;
        String target=MIGRATIONS.get(id(e.getEntity()));
        if(target!=null&&e.getLevel() instanceof ServerLevel level){
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(target));
            if(type==null)throw new IllegalStateException("Missing canonical animal "+target);
            var replacement=type.create(level);if(replacement==null)return;
            Entity original=e.getEntity();
            CompoundTag nbt=original.saveWithoutId(new CompoundTag());
            boolean nativeWild=replacement instanceof WildAnimal;
            float health=original instanceof LivingEntity living?living.getHealth()/living.getMaxHealth():1;
            boolean baby=original instanceof AgeableMob ageable&&ageable.isBaby();
            // Naturalist's saved attributes and brain must not replace TFC's stats/AI.
            if(nativeWild){nbt.remove("Attributes");nbt.remove("Brain");}
            replacement.load(nbt);
            if(replacement instanceof WildAnimal wild){wild.setBaby(baby);wild.setHealth(wild.getMaxHealth()*health);}
            if(replacement instanceof Predator predator)
                predator.getBrain().setMemory(MemoryModuleType.HOME,GlobalPos.of(level.dimension(),predator.blockPosition()));
            // Loading a saved animal is a migration, not a new birth for the fauna cap.
            if(e.loadedFromDisk())replacement.getPersistentData().putBoolean("EmpirecraftFaunaExisting",true);
            e.setCanceled(true);level.addFreshEntity(replacement);return;
        }
        if(!(e.getEntity() instanceof Animal animal)||id(animal).startsWith("tfc:")||!configured.add(animal))return;
        if(domestic(animal))initialize(animal,e.loadedFromDisk());
        if(animal.getType().getCategory()!=MobCategory.CREATURE)return;
        var goals=((MobGoals)animal).empirecraft$goals();
        goals.addGoal(0,new AvoidEntityGoal<Predator>(animal,Predator.class,16,1.3,1.6){
            @Override public boolean canUse(){return !(animal instanceof TamableAnimal pet&&pet.isTame())
                &&(!DEFENDERS.contains(id(animal))||animal.isBaby())&&super.canUse();}
        });
        goals.addGoal(6,new FollowParentGoal(animal,1.1));goals.addGoal(7,new RestGoal(animal));
        if(animal instanceof OChicken hen)goals.addGoal(3,new NestGoal(hen));
        if(DEFENDERS.contains(id(animal))){
            goals.addGoal(1,new MeleeAttackGoal(animal,1.2,true));
            ((MobGoals)animal).empirecraft$targets().addGoal(1,new HurtByTargetGoal(animal).setAlertOthers());
        }
    }
    private static void initialize(Animal a,boolean old){
        CompoundTag root=a.getPersistentData();if(root.contains(STATE))return;
        CompoundTag n=new CompoundTag();long day=Calendars.SERVER.getTotalDays();
        n.putLong("Birthday",day-(a.isBaby()?0:animalConfig(a).adulthoodDays().get()+1));
        n.putFloat("Familiarity",old?0.3f:0);n.putLong("LastFed",-1);n.putLong("DecayDay",day);n.putLong("Mated",-24000);
        root.put(STATE,n);
    }
    private static MammalConfig mammal(Animal a){
        String id=id(a);
        if(id.endsWith("o_cow")||id.endsWith("o_mooshroom"))return TFCConfig.SERVER.cowConfig.inner();
        if(id.endsWith("o_sheep"))return TFCConfig.SERVER.sheepConfig.inner();
        if(id.endsWith("o_goat"))return TFCConfig.SERVER.goatConfig.inner();
        if(id.endsWith("o_horse"))return TFCConfig.SERVER.horseConfig;
        if(id.endsWith("o_donkey"))return TFCConfig.SERVER.donkeyConfig;
        if(id.endsWith("o_mule"))return TFCConfig.SERVER.muleConfig;
        if(id.endsWith("o_rabbit"))return TFCConfig.SERVER.rabbitConfig;
        if(id.endsWith("o_llama")||id.endsWith("o_camel"))return TFCConfig.SERVER.alpacaConfig.inner();
        return TFCConfig.SERVER.pigConfig;
    }
    private static AnimalConfig animalConfig(Animal a){return a instanceof OChicken?TFCConfig.SERVER.chickenConfig.inner().inner():mammal(a).inner();}
    private static boolean female(Animal a){
        try{return (boolean)a.getClass().getMethod("isFemale").invoke(a);}catch(NoSuchMethodException ex){
            try{return ((Number)a.getClass().getMethod("getGender").invoke(a)).intValue()==0;}
            catch(NoSuchMethodException missing){return true;}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot read DLO sex for "+id(a),error);}
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot read DLO sex for "+id(a),ex);}
    }
    private static boolean equineReady(Animal a){
        if(!(a instanceof AbstractHorse horse)||!EquineGenetics.supported(horse))return true;
        var genes=EquineGenetics.data(horse);
        return !genes.getBoolean("Sterile")&&genes.getList("Children",Tag.TAG_COMPOUND).isEmpty()
            &&(!genes.contains("ReadyAt")||EquineGenetics.now(horse)>=genes.getLong("ReadyAt"));
    }
    private void feed(PlayerInteractEvent.EntityInteract e){
        if(!(e.getTarget() instanceof Animal a)||!domestic(a)||a.level().isClientSide)return;
        initialize(a,true);CompoundTag n=a.getPersistentData().getCompound(STATE);ItemStack food=e.getItemStack();
        if(e.getEntity().isShiftKeyDown()&&food.isEmpty()){
            e.getEntity().displayClientMessage(Component.translatable("message.empirecraft_tfc.familiarity",Math.round(100*n.getFloat("Familiarity"))),true);return;
        }
        if((food.is(Items.BUCKET)||food.is(Items.SHEARS))&&(a.isBaby()||n.getFloat("Familiarity")<0.15f)){
            e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);return;
        }
        if(!a.isFood(food))return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);long day=Calendars.SERVER.getTotalDays();
        if(n.getLong("LastFed")==day)return;
        float cap=a.isBaby()?1f:animalConfig(a).familiarityCap().get().floatValue();
        n.putFloat("Familiarity",Math.min(cap,n.getFloat("Familiarity")+0.06f));n.putLong("LastFed",day);n.putLong("DecayDay",day);a.heal(1);
        if(!e.getEntity().getAbilities().instabuild)food.shrink(1);
        if(!a.isBaby()&&a.getAge()==0&&n.getFloat("Familiarity")>=0.3f&&!n.contains("Pregnancy")&&equineReady(a)
                &&Calendars.SERVER.getTicks()-n.getLong("Mated")>=24000)a.setInLove(e.getEntity());
    }
    private void birth(BabyEntitySpawnEvent e){
        if(!(e.getParentA() instanceof Animal a)||!(e.getParentB() instanceof Animal b)||!domestic(a)||a instanceof OChicken)return;
        if(e.getChild()!=null&&e.getChild().getPersistentData().getBoolean("EmpirecraftTfcBirthInProgress"))return;
        Animal mother=female(a)?a:b;Animal father=mother==a?b:a;
        initialize(mother,true);initialize(father,true);var n=mother.getPersistentData().getCompound(STATE);
        e.setCanceled(true);if(n.contains("Pregnancy")||!equineReady(mother)||!equineReady(father))return;
        if(mother instanceof AbstractHorse horse&&EquineGenetics.supported(horse)&&(!female(mother)||female(father)))return;
        CompoundTag pregnancy=new CompoundTag();ListTag children=new ListTag();
        int count=1+mother.getRandom().nextInt(Math.max(1,mammal(mother).childCount().get()));
        for(int i=0;i<count;i++){
            AgeableMob child=i==0?e.getChild():mother.getBreedOffspring((ServerLevel)mother.level(),father);
            if(child==null)continue;
            if(mother instanceof AbstractHorse mare&&father instanceof AbstractHorse sire&&child instanceof AbstractHorse foal
                    &&EquineGenetics.supported(mare))EquineGenetics.inherit(mare,sire,foal);
            child.setAge(-24000);CompoundTag tag=child.serializeNBT();
            children.add(tag);
        }
        pregnancy.put("Children",children);pregnancy.putLong("Due",Calendars.SERVER.getTotalDays()+mammal(mother).gestationDays().get());
        n.put("Pregnancy",pregnancy);n.putLong("Mated",Calendars.SERVER.getTicks());
        father.getPersistentData().getCompound(STATE).putLong("Mated",Calendars.SERVER.getTicks());
    }
    private void tick(LivingEvent.LivingTickEvent e){
        if(!(e.getEntity() instanceof Animal a)||a.level().isClientSide||a.tickCount%20!=0||!domestic(a))return;
        initialize(a,true);var n=a.getPersistentData().getCompound(STATE);long day=Calendars.SERVER.getTotalDays();
        float familiarity=n.getFloat("Familiarity");long missed=day-n.getLong("DecayDay");
        if(missed>1&&familiarity<0.3f){n.putFloat("Familiarity",Math.max(0,familiarity-0.02f*(missed-1)));n.putLong("DecayDay",day);}
        if(day-n.getLong("Birthday")<animalConfig(a).adulthoodDays().get())a.setAge(-24000);
        else if(a.isBaby())a.setAge(0);
        if(n.contains("Pregnancy")&&day>=n.getCompound("Pregnancy").getLong("Due")){
            var list=n.getCompound("Pregnancy").getList("Children",Tag.TAG_COMPOUND);
            ListTag waiting=new ListTag();
            for(Tag t:list){Entity child=EntityType.loadEntityRecursive((CompoundTag)t,a.level(),v->v);
                if(child==null)continue;child.moveTo(a.getX(),a.getY(),a.getZ(),a.getYRot(),0);
                if(child instanceof Animal baby){initialize(baby,false);baby.getPersistentData().getCompound(STATE).putFloat("Familiarity",familiarity<0.9f?familiarity/2:familiarity*0.9f);}
                if(child instanceof AgeableMob baby){
                    child.getPersistentData().putBoolean("EmpirecraftTfcBirthInProgress",true);
                    var event=new BabyEntitySpawnEvent(a,a,baby);boolean denied=MinecraftForge.EVENT_BUS.post(event);
                    child.getPersistentData().remove("EmpirecraftTfcBirthInProgress");
                    if(denied){waiting.add(t.copy());continue;}
                }
                if(!a.level().addFreshEntity(child))waiting.add(t.copy());}
            if(waiting.isEmpty())n.remove("Pregnancy");else n.getCompound("Pregnancy").put("Children",waiting);
        }
    }
    private void hurt(LivingHurtEvent e){
        if(!(e.getEntity() instanceof Animal a)||!DEFENDERS.contains(id(a))||!(e.getSource().getEntity() instanceof LivingEntity attacker))return;
        if(attacker instanceof Player p&&(p.isCreative()||p.isSpectator()))return;
        for(Animal nearby:a.level().getEntitiesOfClass(Animal.class,a.getBoundingBox().inflate(16),v->v.getType()==a.getType()&&!v.isBaby()))nearby.setTarget(attacker);
    }
    public static void layEgg(OChicken hen,OChicken mate,ServerLevel level){
        AgeableMob child=hen.getBreedOffspring(level,mate);if(child==null)return;child.setAge(-24000);
        ItemStack egg=new ItemStack(Items.EGG);var data=EggCapability.get(egg);
        if(data==null)throw new IllegalStateException("TFC egg capability is unavailable");
        data.setFertilized(child,Calendars.SERVER.getTotalDays()+TFCConfig.SERVER.chickenConfig.hatchDays().get());
        for(BlockPos pos:BlockPos.betweenClosed(hen.blockPosition().offset(-8,-2,-8),hen.blockPosition().offset(8,2,8))){
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NestBoxBlockEntity nest))continue;
            var inventory=nest.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);if(inventory==null)continue;
            egg=ItemHandlerHelper.insertItemStacked(inventory,egg,false);if(egg.isEmpty()){nest.setChanged();return;}
        }
        hen.spawnAtLocation(egg);
    }
    private void eggCapability(net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack> e){
        String id=Integration.id(e.getObject().getItem());
        if(id.startsWith("dragnlivestock:")&&(id.endsWith("_egg")||id.equals("dragnlivestock:egg")))
            e.addCapability(new ResourceLocation(Integration.ID,"egg"),new net.dries007.tfc.common.capabilities.egg.EggHandler(e.getObject()));
    }
    public static net.minecraft.world.entity.item.ItemEntity layRegularEgg(OChicken hen,ItemStack stack){
        if(hen.level().isClientSide)return null;
        initialize(hen,true);var n=hen.getPersistentData().getCompound(STATE);
        if(n.getFloat("Familiarity")<TFCConfig.SERVER.chickenConfig.inner().produceFamiliarity().get()
                ||n.getLong("LastFed")!=Calendars.SERVER.getTotalDays())return null;
        long now=Calendars.SERVER.getTicks();
        if(n.contains("LastEgg")&&now!=n.getLong("LastEgg")&&now-n.getLong("LastEgg")<TFCConfig.SERVER.chickenConfig.inner().produceTicks().get())return null;
        n.putLong("LastEgg",now);
        for(BlockPos pos:BlockPos.betweenClosed(hen.blockPosition().offset(-8,-2,-8),hen.blockPosition().offset(8,2,8))){
            if(!hen.level().hasChunkAt(pos)||!(hen.level().getBlockEntity(pos) instanceof NestBoxBlockEntity nest))continue;
            var inventory=nest.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);if(inventory==null)continue;
            stack=ItemHandlerHelper.insertItemStacked(inventory,stack,false);if(stack.isEmpty()){nest.setChanged();return null;}
        }
        return hen.spawnAtLocation(stack);
    }
    /** A full fauna cap leaves a ready egg in its nest instead of deleting it. */
    public static void hatchNest(NestBoxBlockEntity nest){
        if(!(nest.getLevel() instanceof ServerLevel level))return;
        var inventory=nest.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if(!(inventory instanceof net.minecraftforge.items.IItemHandlerModifiable mutable))return;
        for(int slot=0;slot<inventory.getSlots();slot++){
            var egg=EggCapability.get(inventory.getStackInSlot(slot));
            if(egg==null||!egg.isFertilized()||egg.getHatchDay()<=0||egg.getHatchDay()>Calendars.SERVER.getTotalDays())continue;
            Entity child=egg.getEntity(level).orElse(null);if(child==null)continue;
            child.moveTo(nest.getBlockPos(),0,0);
            child.getPersistentData().putBoolean("EmpirecraftFaunaProtected",true);
            if(level.addFreshEntity(child)){mutable.setStackInSlot(slot,ItemStack.EMPTY);nest.setChanged();}
        }
    }
    private static final class RestGoal extends Goal {
        private final Animal animal;
        RestGoal(Animal a){animal=a;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
        @Override public boolean canUse(){return !animal.level().isDay()&&!animal.isVehicle()&&!animal.isInWater()&&animal.getTarget()==null
                &&(animal.getLastHurtByMob()==null||animal.tickCount-animal.getLastHurtByMobTimestamp()>200);}
        @Override public void tick(){animal.getNavigation().stop();}
    }
    private static final class NestGoal extends Goal {
        private final OChicken hen;private BlockPos destination;
        NestGoal(OChicken hen){this.hen=hen;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            if(hen.isBaby()||!hen.isFemale()||hen.eggTime>200||hen.tickCount%40!=0)return false;
            for(BlockPos pos:BlockPos.betweenClosed(hen.blockPosition().offset(-8,-2,-8),hen.blockPosition().offset(8,2,8))){
                if(hen.level().hasChunkAt(pos)&&hen.level().getBlockEntity(pos) instanceof NestBoxBlockEntity){destination=pos.immutable();return true;}}
            return false;
        }
        @Override public boolean canContinueToUse(){return destination!=null&&hen.eggTime<200&&hen.distanceToSqr(destination.getX()+0.5,destination.getY()+0.8,destination.getZ()+0.5)>1;}
        @Override public void start(){hen.getNavigation().moveTo(destination.getX()+0.5,destination.getY()+0.8,destination.getZ()+0.5,1.1);}
        @Override public void stop(){destination=null;hen.getNavigation().stop();}
    }
}
