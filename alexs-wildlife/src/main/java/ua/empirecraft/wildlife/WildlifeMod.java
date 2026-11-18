package ua.empirecraft.wildlife;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry IDs stay stable for old saves; excluded creatures never enter a level. */
@Mod(WildlifeMod.ID)
public final class WildlifeMod {
    public static final String ID="empirecraft_wildlife";
    private static final String EXEMPT="EmpirecraftWildlifeCaptive",NATURAL="EmpirecraftWildlifeNatural",EXISTED="EmpirecraftWildlifeAdmitted";
    public static volatile Policy policy=defaults();
    public WildlifeMod(){MinecraftForge.EVENT_BUS.register(this);FMLJavaModLoadingContext.get().getModEventBus().addListener(this::creative);}
    static Policy defaults(){return new Policy(Policy.resource("wildlife-policy.json"),Policy.resource("earth-defaults.json"));}
    @SubscribeEvent public void load(ServerAboutToStartEvent e)throws Exception{
        Path config=FMLPaths.CONFIGDIR.get().resolve("empirecraft-earth.json");
        try(var geography=Files.exists(config)?Files.newBufferedReader(config):Policy.resource("earth-defaults.json");var rules=Policy.resource("wildlife-policy.json")){policy=new Policy(rules,geography);}
    }
    public static String id(Entity entity){return Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(entity.m_6095_()),"");}
    static String dimension(ServerLevel level){return level.m_46472_().m_135782_().toString();}
    static boolean natural(MobSpawnType reason){return reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION||reason==MobSpawnType.EVENT;}
    /** Reads only existing block/biome data; never searches or loads surrounding chunks. */
    public static String habitat(ServerLevel level,BlockPos pos){
        var biome=level.m_204166_(pos);
        if(level.m_6425_(pos).m_205070_(FluidTags.f_13131_)) {
            if(biome.m_203656_(BiomeTags.f_207605_))return "fresh";
            var point=policy.earth.coordinates(pos.m_123341_(),pos.m_123343_());
            int surface=level.m_6924_(Heightmap.Types.WORLD_SURFACE,pos.m_123341_(),pos.m_123343_())-1;
            return policy.earth.at("marine",point)!=null&&Math.abs(surface-policy.earth.seaLevel)<=3?"marine":"fresh";
        }
        if(biome.m_203656_(BiomeTags.f_207605_))return "riverbank";
        String name=biome.m_203543_().map(k->k.m_135782_().toString()).orElse("");
        if(name.contains("swamp"))return "wetland";
        if(name.contains("beach")||name.contains("shore"))return "coast";
        return "land";
    }
    public static boolean allowed(String id,ServerLevel level,BlockPos pos){
        if(policy.removed(id))return false;
        if(!policy.managed(id))return true;
        int surface=level.m_6924_(Heightmap.Types.WORLD_SURFACE,pos.m_123341_(),pos.m_123343_())-1;
        return policy.allowed(id,dimension(level),pos.m_123341_(),pos.m_123342_(),pos.m_123343_(),habitat(level,pos),Math.max(0,surface-pos.m_123342_()));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void position(MobSpawnEvent.PositionCheck e){
        String id=id(e.getEntity());
        if(policy.removed(id)||natural(e.getSpawnType())&&!allowed(id,e.getLevel().m_6018_(),e.getEntity().m_20183_()))e.setResult(Event.Result.DENY);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void finalizeSpawn(MobSpawnEvent.FinalizeSpawn e){
        var mob=e.getEntity();String id=id(mob);
        if(policy.removed(id)||natural(e.getSpawnType())&&!allowed(id,e.getLevel().m_6018_(),mob.m_20183_())){e.setSpawnCancelled(true);return;}
        if(policy.managed(id))mob.getPersistentData().m_128379_(natural(e.getSpawnType())?NATURAL:EXEMPT,true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void born(BabyEntitySpawnEvent e){
        if(e.getChild()!=null&&policy.managed(id(e.getChild())))e.getChild().getPersistentData().m_128379_(EXEMPT,true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void join(EntityJoinLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        String id=id(e.getEntity());
        // Also catches commands, spawners, transformations and saved Spawn ants.
        if(policy.removed(id)){e.setCanceled(true);return;}
        if(!policy.managed(id))return;
        var tag=e.getEntity().getPersistentData();
        if(!e.loadedFromDisk()&&!tag.m_128471_(EXISTED)&&!tag.m_128471_(EXEMPT)&&!allowed(id,level,e.getEntity().m_20183_())){e.setCanceled(true);return;}
        tag.m_128379_(EXISTED,true); // Future transport/reload is not a new natural spawn.
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void candidates(LevelEvent.PotentialSpawns e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        for(var entry:new ArrayList<>(e.getSpawnerDataList())) {
            String id=Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(entry.f_48404_),"");
            if(!allowed(id,level,e.getPos()))e.removeSpawnerData(entry);
        }
        if(!policy.earth.dimensions.contains(dimension(level)))return;
        // Earth's imported biomes need a positive source of region candidates, not only a veto.
        for(var entry:policy.animals().entrySet()) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entry.getKey()));
            if(type==null||type.m_20674_()!=e.getMobCategory()||!allowed(entry.getKey(),level,e.getPos())||e.getSpawnerDataList().stream().anyMatch(row->row.f_48404_==type))continue;
            var row=entry.getValue().getAsJsonObject();
            e.addSpawnerData(new MobSpawnSettings.SpawnerData(type,row.get("weight").getAsInt(),row.get("min").getAsInt(),row.get("max").getAsInt()));
        }
    }
    public void creative(BuildCreativeModeTabContentsEvent e){
        var remove=new ArrayList<ItemStack>();
        net.minecraftforge.common.util.MutableHashedLinkedMap entries=e.getEntries();
        for(Object value:entries){var entry=(Map.Entry<?,?>)value;if(hidden((ItemStack)entry.getKey()))remove.add((ItemStack)entry.getKey());}
        for(var stack:remove)entries.remove(stack);
    }
    public static boolean hidden(ItemStack stack){return policy.hidden.contains(Objects.toString(ForgeRegistries.ITEMS.getKey(stack.m_41720_()),""));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent e){
        var table=policy.json.getAsJsonObject("whale_loot");String id=id(e.getEntity());
        if(!table.has(id)||!(e.getEntity().m_9236_() instanceof ServerLevel level))return;
        if(!level.m_46469_().m_46207_(GameRules.f_46135_)||e.getEntity() instanceof Animal animal&&animal.m_6162_())return;
        e.getDrops().clear();
        for(var entry:table.getAsJsonObject(id).entrySet()) {
            var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey()));
            if(item==null)throw new IllegalStateException("Missing whale loot "+entry.getKey());
            var range=entry.getValue().getAsJsonArray();int min=range.get(0).getAsInt(),max=range.get(1).getAsInt();
            int amount=min+level.f_46441_.m_188503_(max-min+1);
            while(amount>0){var stack=new ItemStack(item,Math.min(amount,item.m_41459_()));amount-=stack.m_41613_();e.getDrops().add(new ItemEntity(level,e.getEntity().m_20185_(),e.getEntity().m_20186_(),e.getEntity().m_20189_(),stack));}
        }
    }
}
