package ua.empirecraft.interactionstest;

import com.mctabletop.neoforge.block.GameTableBlockEntity;
import com.quintonc.vs_sails.wind.OceanWind;
import com.quintonc.vs_sails.wind.ServerWindManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armory.AssemblyRecipe;
import ua.empirecraft.armory.Parts;
import ua.empirecraft.armory.WorkshopEntity;
import ua.empirecraft.earth.EarthMod;
import ua.empirecraft.earth.Geography;
import ua.empirecraft.interactions.TableAccess;

public final class SeasonalProbe {
    private SeasonalProbe() {}

    public static void run(ServerLevel level,ServerPlayer user) throws Exception {
        int failures=0;
        for (var scenario:List.<Scenario>of(() -> wind(level), () -> forecast(level), () -> wildlife(level,user),
            () -> table(level,user), () -> provenance(level,user))) {
            try { scenario.run(); }
            catch (Throwable error) { failures++; error.printStackTrace(); }
        }
        check(failures==0,"Seasonal feature groups failing: "+failures);
        System.out.println("SEASONAL_NATIVE_PASS time and spatial wind, forecasts, wildlife, table permissions, crafted provenance");
    }

    @FunctionalInterface private interface Scenario { void run() throws Exception; }

    private static void check(boolean ok,String detail) {
        if (!ok) throw new AssertionError(detail);
    }

    private static double gap(double a,double b) {
        return Math.abs(Math.IEEEremainder(a-b,360));
    }

    private static Method method(Class<?> type,String name,Class<?>... args) throws Exception {
        var method=type.getDeclaredMethod(name,args);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Object target,Class<?> type,String name) throws Exception {
        var field=type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void wind(ServerLevel level) throws Exception {
        check(gap(OceanWind.direction(76,14,1,0),OceanWind.direction(76,14,7,0))>100,"Indian monsoon reverses seasonally");
        check(gap(OceanWind.direction(-40,15,6,0),OceanWind.direction(75,15,6,0))>60,"Different wind across oceans");
        for (int latitude=-89;latitude<=89;latitude++) for (int longitude=-180;longitude<=180;longitude++) {
            double wind=OceanWind.direction(longitude,latitude,6,70);
            double neighbour=OceanWind.direction(longitude+.01,latitude+.01,6,70);
            check(gap(wind,neighbour)<1,"Wind remains continuous across cells");
        }
        ServerWindManager.InitializeWind();
        method(ServerWindManager.class,"onWorldTick",ServerLevel.class).invoke(null,level);
        var earth=EarthMod.data;
        Vec3 atlantic=new Vec3(earth.x(-40),63,earth.z(15));
        Vec3 pacific=new Vec3(earth.x(-155),63,earth.z(15));
        float a=ServerWindManager.getWindDirectionAtPosition(level,atlantic);
        float b=ServerWindManager.getWindDirectionAtPosition(level,pacific);
        check(gap(a,b)>12,"Live Sails manager is spatially varying: "+a+", "+b);
        Object state=method(ServerWindManager.class,"getStateIfPresent",ServerLevel.class).invoke(null,level);
        var strength=state.getClass().getDeclaredField("strength");
        strength.setAccessible(true);
        strength.setFloat(state,0.8f);
        int seaX=(int)atlantic.x,seaZ=(int)atlantic.z;
        Class<?> forecast=Class.forName("ua.empirecraft.townymap.SeasonalForecast");
        String details=(String)method(forecast,"wind",ServerLevel.class,int.class,int.class).invoke(null,level,seaX,seaZ);
        int bearing=Math.floorMod(Math.round(ServerWindManager.getWindDirectionAtPosition(level,new Vec3(seaX+.5,63,seaZ+.5)))+90,360);
        check(details.contains("Вітер дме на")&&details.contains("("+bearing+"°)"),"Map sea card uses the exact Sails wind bearing");
    }

    private static void forecast(ServerLevel level) throws Exception {
        Class<?> forecasts=Class.forName("ua.empirecraft.townymap.SeasonalForecast");
        Class<?> knowledge=Class.forName("ua.empirecraft.townymap.ClaimKnowledge");
        Class<?> chunkType=Class.forName("ua.empirecraft.townymap.ClaimKnowledge$Chunk");
        var newKnowledge=knowledge.getDeclaredConstructor();
        newKnowledge.setAccessible(true);
        Object history=newKnowledge.newInstance();
        int x=7879,z=-9661;
        var chunk=level.getChunk(x>>4,z>>4);
        for (int dx:List.of(4,12)) for (int dz:List.of(4,12)) {
            var pos=new BlockPos((x&~15)+dx,63,(z&~15)+dz);
            chunk.setBlockState(pos.below(),Blocks.STONE.defaultBlockState(),false);
            chunk.setBlockState(pos,Blocks.GRASS_BLOCK.defaultBlockState(),false);
        }
        Object chunkId=method(chunkType,"at",String.class,int.class,int.class).invoke(null,"minecraft:overworld",x,z);
        var fragments=(List<?>) method(Class.forName("ua.empirecraft.townymap.ClimateObservations"),"sample",
            ServerLevel.class,LevelChunk.class,chunkType).invoke(null,level,chunk,chunkId);
        method(knowledge,"survey",chunkType,List.class,List.class,long.class).invoke(history,chunkId,List.of(),fragments,0L);
        var at=method(forecasts,"at",knowledge,chunkType,int.class,int.class,int.class,Geography.class);
        var january=(List<?>)at.invoke(null,history,chunkId,x,z,0,EarthMod.data);
        var july=(List<?>)at.invoke(null,history,chunkId,x,z,6,EarthMod.data);
        check(january.size()==1 && july.size()==1 && !january.equals(july),"Observed land has a month-dependent forecast");
        var winterDetail=(String)method(january.get(0).getClass(),"detail").invoke(january.get(0));
        check(winterDetail.contains("Заморозки")&&winterDetail.contains("Посуха")&&winterDetail.contains("Сніг"),"All three risks appear in the card");
        check(((List<?>)at.invoke(null,history,chunkId,x+128,z,0,EarthMod.data)).isEmpty(),"A click cannot get a forecast for unknown terrain");
    }

    private static Mob cow(ServerLevel level,double x,double z) {
        var mob=(Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_cow")).create(level);
        mob.setPos(x,76,z);
        mob.getPersistentData().putBoolean("EmpirecraftFaunaExisting",true);
        check(level.addFreshEntity(mob),"Existing herd animal enters test world");
        return mob;
    }

    private static net.minecraft.world.entity.ai.goal.Goal reaction(Mob animal) throws Exception {
        var goals=(GoalSelector)field(animal,Mob.class,"f_21345_");
        return goals.getAvailableGoals().stream().map(g -> g.getGoal())
            .filter(goal -> goal.getClass().getName().endsWith("AnimalThreats$Reaction")).findFirst().orElseThrow();
    }

    private static void wildlife(ServerLevel level,ServerPlayer user) throws Exception {
        user.getServer().setPvpAllowed(true);
        user.setPos(57.5,76,36.5);
        var cow=cow(level,57.5,41.5);
        var herd=cow(level,60.5,41.5);
        var study=Class.forName("ua.empirecraft.interactions.AnimalFamiliarity");
        var observe=method(study,"observe",Mob.class,UUID.class,long.class);
        var score=method(study,"score",Mob.class,UUID.class,long.class);
        long now=level.getGameTime();
        var stranger=UUID.randomUUID();
        for(int n=0;n<8;n++) observe.invoke(null,cow,user.getUUID(),now+n*120);
        check((int)score.invoke(null,cow,user.getUUID(),now+840)==8,"Familiarity grows gradually");
        check((int)score.invoke(null,cow,stranger,now+840)==0,"Trust is personal");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(cow,level.damageSources().playerAttack(user),2));
        observe.invoke(null,cow,user.getUUID(),now+1500);
        check((int)score.invoke(null,cow,user.getUUID(),now+1500)==0,"Attack clears trust and pauses recovery");
        check(reaction(herd).canUse(),"An injured animal warns the herd");
        check(!herd.getPersistentData().hasUUID("EmpirecraftThreatAttacker"),"Herd alarm does not write a personal attack");
        cow.discard();
        herd.discard();

        var dog=EntityType.WOLF.create(level);
        dog.tame(user);
        dog.setPos(57.5,76,39.5);
        dog.getPersistentData().putBoolean("EmpirecraftFaunaExisting",true);
        check(level.addFreshEntity(dog),"Owned wolf enters the test world");
        user.lookAt(EntityAnchorArgument.Anchor.EYES,dog.getEyePosition());
        var commands=level.getServer().getCommands();
        check(commands.performPrefixedCommand(user.createCommandSourceStack().withSuppressedOutput(),"animal pen")==1,"Owner can mark a shelter");
        var pen=BlockPos.of(dog.getPersistentData().getLong("EmpirecraftShelter"));
        check(pen.equals(dog.blockPosition()),"Shelter persisted at the pet's position");
        dog.setPos(63.5,76,39.5);
        level.explode(null,72,76,39.5,1,Level.ExplosionInteraction.NONE);
        var reaction=reaction(dog);
        check(reaction.canUse() && field(reaction,reaction.getClass(),"refuge").equals(Vec3.atBottomCenterOf(pen)),
            "Frightened pet selects the owner's recorded shelter");
        dog.discard();
    }

    private static void table(ServerLevel level,ServerPlayer user) throws Exception {
        var pos=new BlockPos(37,74,40);
        var table=(GameTableBlockEntity)level.getBlockEntity(pos);
        var guest=GameplayProbe.player(level.getServer(),level,"GuestProbe");
        check(table!=null && table.isPlayable(),"Public game installed on an ordinary player's table");
        var rules=(TableAccess)table;
        var seats=com.mctabletop.core.game.Player.values();
        user.setPos(37.5,75.5,38.5);
        user.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(pos));
        var commands=level.getServer().getCommands();
        check(commands.performPrefixedCommand(user.createCommandSourceStack().withSuppressedOutput(),"table access private")==1,
            "Ordinary owner can change table access: "+user.pick(6,1,false)+" at "+pos);
        var saved=table.saveWithoutMetadata();
        check(rules.empirecraft$isPrivate() && !table.canSit(guest.getUUID(),seats[0]),"Private table denies a new guest");
        check(commands.performPrefixedCommand(user.createCommandSourceStack().withSuppressedOutput(),"table access public")==1
            && !rules.empirecraft$isPrivate(),"Owner can open the table");
        guest.setPos(37.5,75.5,38.5);
        guest.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(pos));
        check(commands.performPrefixedCommand(guest.createCommandSourceStack().withSuppressedOutput(),"table access private")==0
            && !rules.empirecraft$isPrivate(),"Guest cannot change the table setting");
        var transition=GameTableBlockEntity.class.getDeclaredField("boardTransitionStart");
        transition.setAccessible(true);
        transition.setLong(table,level.getGameTime()-100);
        check(table.sit(guest,seats[0]),"Visitor can take a chair at a public table");
        check("owner".equals(table.removeBoard(guest)),"Guest cannot take the board");
        table.load(saved);
        check(rules.empirecraft$isPrivate(),"Private setting survives block-entity serialization");
    }

    private static void provenance(ServerLevel level,ServerPlayer user) throws Exception {
        var candidate=AssemblyRecipe.all(level).stream().filter(recipe -> {
            var key=ForgeRegistries.ITEMS.getKey(recipe.result.getItem());
            return key!=null && key.getPath().equals("dark_maximilian_chestplate");
        }).findFirst().orElseThrow();
        var bench=new BlockPos(43,75,43);
        level.setBlockAndUpdate(bench,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("empirecraft_armory:armorer_table")).defaultBlockState());
        var table=(WorkshopEntity)level.getBlockEntity(bench);
        check(table.select(AssemblyRecipe.all(level).indexOf(candidate)),"Set dark armor workshop recipe");
        var towny=new GameplayProbe.TownyFixture(level);
        var plot=towny.claim(towny.newTown("WorkshopTown"),bench.getX(),bench.getY(),bench.getZ());
        try {
            for(int stage=0;stage<4;stage++) {
                int slot=WorkshopEntity.FIRST;
                for(var ingredient:candidate.stage(stage))
                    table.inventory.setStackInSlot(slot++,Parts.sample(ingredient.getItems()[0].getItem()));
                check(table.perform(0,user),"Assembly stage "+stage);
            }
        } finally { towny.remove(plot); }
        var armor=table.inventory.getStackInSlot(WorkshopEntity.OUTPUT);
        var history=armor.getTag().getCompound(Parts.DATA).getCompound("provenance");
        check(history.getUUID("makerId").equals(user.getUUID()) && history.getString("metal").equals("black_steel"),
            "Armor records the actual smith and black steel");
        check(history.getString("town").equals("WorkshopTown"),"Armor records the Towny plot of the workbench");
        armor.setDamageValue(armor.getMaxDamage()/2);
        var tooltip=new ArrayList<Component>();
        Parts.tooltip(armor,tooltip);
        check(tooltip.size()==4,"Damaged armor tooltip includes origin and a replacement");
        table.inventory.setStackInSlot(WorkshopEntity.OUTPUT,ItemStack.EMPTY);
        table.inventory.setStackInSlot(WorkshopEntity.WORK,armor);
        var replacement=Parts.sample(candidate.profile.items().get(0));
        table.inventory.setStackInSlot(WorkshopEntity.FIRST,replacement);
        var rivets=ForgeRegistries.ITEMS.getValue(new ResourceLocation("empirecraft_armory:rivets"));
        table.inventory.setStackInSlot(WorkshopEntity.FIRST+1,new ItemStack(rivets,2));
        check(table.perform(4,user),"Replacement repairs provenance-bearing armor");
        var repaired=table.inventory.getStackInSlot(WorkshopEntity.OUTPUT).getTag().getCompound(Parts.DATA);
        check(repaired.getCompound("provenance").equals(history),"Repair retains the original smith, town and metal");
    }
}
