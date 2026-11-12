package ua.empirecraft.equine;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(EquineGenetics.ID)
public final class EquineGenetics {
    public static final String ID="empirecraft_equine";
    private static final Set<String> TYPES=Set.of("dragnlivestock:o_horse","dragnlivestock:o_donkey","dragnlivestock:o_mule");
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue PREGNANCY,FEMALE_COOLDOWN,MALE_COOLDOWN,GROWTH;
    private static final ForgeConfigSpec.DoubleValue MUTATION;
    static {
        var b=new ForgeConfigSpec.Builder();PREGNANCY=b.defineInRange("pregnancy-ticks",48000,1,24000000);
        FEMALE_COOLDOWN=b.defineInRange("female-cooldown-ticks",96000,1,24000000);MALE_COOLDOWN=b.defineInRange("male-cooldown-ticks",240,1,24000000);
        GROWTH=b.defineInRange("foal-growth-ticks",120000,1,24000000);MUTATION=b.defineInRange("mutation-chance",.0001,0,1);SPEC=b.build();
    }
    public EquineGenetics(){ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,SPEC);MinecraftForge.EVENT_BUS.register(this);}
    public static boolean supported(Entity entity){return TYPES.contains(Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()),""));}
    public static long now(Entity entity){return Objects.requireNonNull(entity.getServer()).overworld().getGameTime();}
    public static CompoundTag data(AbstractHorse horse) {
        var parent=horse.getPersistentData();if(!parent.contains(LegacyMigration.KEY,Tag.TAG_COMPOUND))parent.put(LegacyMigration.KEY,new CompoundTag());
        var d=parent.getCompound(LegacyMigration.KEY);
        if(!d.contains("Genes"))d.putString("Genes",EquineGenome.founder(new Random(horse.getUUID().getMostSignificantBits()^horse.getUUID().getLeastSignificantBits())).encode());
        if(horse.getType()==ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_mule")))d.putBoolean("Sterile",true);
        try{if((boolean)horse.getClass().getMethod("isSnipped").invoke(horse))d.putBoolean("Sterile",true);}catch(ReflectiveOperationException e){throw new IllegalStateException("DLO fertility API changed",e);}
        var genome=EquineGenome.decode(d.getString("Genes"));
        if(!d.contains("HealthScale"))d.putDouble("HealthScale",horse.getAttribute(Attributes.MAX_HEALTH).getBaseValue()/genome.health());
        if(!d.contains("SpeedScale"))d.putDouble("SpeedScale",horse.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()/genome.speed());
        if(!d.contains("JumpScale"))d.putDouble("JumpScale",horse.getAttribute(Attributes.JUMP_STRENGTH).getBaseValue()/genome.jump());
        return d;
    }
    private static int gender(AbstractHorse horse){try{return ((Number)horse.getClass().getMethod("getGender").invoke(horse)).intValue();}catch(ReflectiveOperationException e){throw new IllegalStateException("DLO gender API changed",e);}}
    public static void inherit(AbstractHorse mother,AbstractHorse father,AbstractHorse child) {
        var m=data(mother);var f=data(father);var c=data(child);
        var genes=EquineGenome.inherit(EquineGenome.decode(m.getString("Genes")),EquineGenome.decode(f.getString("Genes")),new Random(child.getUUID().getMostSignificantBits()),MUTATION.get());
        c.putString("Genes",genes.encode());c.putUUID("Mother",mother.getUUID());c.putUUID("Father",father.getUUID());
        for(String key:List.of("HealthScale","SpeedScale","JumpScale"))c.putDouble(key,Math.sqrt(m.getDouble(key)*f.getDouble(key)));
        apply(child,genes,c);
    }
    private static void apply(AbstractHorse child,EquineGenome genome,CompoundTag d) {
        child.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(4,Math.min(200,genome.health()*d.getDouble("HealthScale"))));
        child.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.max(.01,Math.min(1,genome.speed()*d.getDouble("SpeedScale"))));
        child.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(Math.max(.1,Math.min(2,genome.jump()*d.getDouble("JumpScale"))));
        child.setHealth(child.getMaxHealth());
    }
    @SubscribeEvent(priority=EventPriority.LOW) public void conceive(BabyEntitySpawnEvent event) {
        if(!(event.getParentA() instanceof AbstractHorse a)||!(event.getParentB() instanceof AbstractHorse b)||!supported(a)||!supported(b)||a.level().isClientSide)return;
        if(!(event.getChild() instanceof AbstractHorse child)||!supported(child))return;
        var mother=gender(a)==0?a:b;var father=mother==a?b:a;var d=data(mother);long time=now(mother);
        boolean sameConception=d.getLong("ConceivedAt")==time&&d.hasUUID("Mate")&&d.getUUID("Mate").equals(father.getUUID());
        var children=d.getList("Children",Tag.TAG_COMPOUND);
        if(gender(mother)!=0||gender(father)!=1||d.getBoolean("Sterile")||data(father).getBoolean("Sterile")||mother.getType()==father.getType()&&mother.getType()==ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragnlivestock:o_mule"))
                ||!children.isEmpty()&&!sameConception||children.size()>=16) {event.setCanceled(true);return;}
        inherit(mother,father,child);child.setAge(-GROWTH.get());
        child.getPersistentData().putBoolean("EmpirecraftFaunaProtected",true);
        if(!children.isEmpty()&&children.stream().anyMatch(t->((CompoundTag)t).hasUUID("UUID")&&((CompoundTag)t).getUUID("UUID").equals(child.getUUID()))){event.setCanceled(true);return;}
        var saved=new CompoundTag();if(!child.save(saved))return;
        children.add(saved);d.put("Children",children);d.putLong("ConceivedAt",time);d.putUUID("Mate",father.getUUID());d.putLong("DueAt",time+PREGNANCY.get());
        d.putLong("ReadyAt",time+Math.max(PREGNANCY.get(),FEMALE_COOLDOWN.get()));data(father).putLong("ReadyAt",time+MALE_COOLDOWN.get());
        var breeder=mother.getLoveCause()!=null?mother.getLoveCause():father.getLoveCause();
        if(breeder!=null){breeder.awardStat(Stats.ANIMALS_BRED);CriteriaTriggers.BRED_ANIMALS.trigger(breeder,mother,father,child);}
        event.setCanceled(true);
        mother.level().broadcastEntityEvent(mother,(byte)18);
    }
    @SubscribeEvent public void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof AbstractHorse horse)||horse.level().isClientSide||!supported(horse)||horse.tickCount%20!=0)return;
        var d=data(horse);long time=now(horse);
        if(d.contains("PendingPregnancyTicks")){d.putLong("DueAt",time+d.getLong("PendingPregnancyTicks"));d.remove("PendingPregnancyTicks");}
        if(d.contains("ReadyAt")&&horse.getAge()>=0) {
            long remaining=d.getLong("ReadyAt")-time;horse.setAge((int)Math.max(0,Math.min(Integer.MAX_VALUE,remaining)));
            if(remaining<=0)d.remove("ReadyAt");
        }
        var children=d.getList("Children",Tag.TAG_COMPOUND);
        if(children.isEmpty()||!d.contains("DueAt")||time<d.getLong("DueAt"))return;
        for(int i=children.size()-1;i>=0;i--) {
            var saved=children.getCompound(i);var level=(ServerLevel)horse.level();
            if(saved.hasUUID("UUID")&&level.getEntity(saved.getUUID("UUID"))!=null){children.remove(i);continue;}
            Entity born=EntityType.loadEntityRecursive(saved.copy(),level,e->e);
            if(!(born instanceof AbstractHorse foal)||!supported(foal))continue;
            foal.moveTo(horse.getX()+.5,horse.getY(),horse.getZ()+.5,horse.getYRot(),0);foal.setAge(-GROWTH.get());
            var foalData=data(foal);if(foalData.getBoolean("LegacyUnborn")){for(String key:List.of("HealthScale","SpeedScale","JumpScale"))foalData.putDouble(key,1);apply(foal,EquineGenome.decode(foalData.getString("Genes")),foalData);foalData.remove("LegacyUnborn");}
            if(level.addFreshEntity(foal)){children.remove(i);level.broadcastEntityEvent(horse,(byte)18);}
        }
        if(children.isEmpty()){d.remove("Children");d.remove("DueAt");d.remove("ConceivedAt");d.remove("Mate");}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof AbstractHorse horse&&supported(horse))data(horse);
        // DLO's own conversion is normally first. Handle any remaining fresh vanilla mounts.
        String id=Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()),"");
        if(!Set.of("minecraft:horse","minecraft:donkey","minecraft:mule").contains(id))return;
        var tag=new CompoundTag();if(!event.getEntity().save(tag)||!LegacyMigration.convert(tag))return;
        event.setCanceled(true);var level=(ServerLevel)event.getLevel();
        level.getServer().execute(()->{Entity converted=EntityType.loadEntityRecursive(tag,level,e->e);if(converted!=null)level.addFreshEntity(converted);});
    }
    @SubscribeEvent public void inspect(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getTarget() instanceof AbstractHorse horse)||!supported(horse)||!event.getItemStack().is(Items.BOOK))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(horse.level().isClientSide));
        if(horse.level().isClientSide)return;
        var d=data(horse);var genome=EquineGenome.decode(d.getString("Genes"));var book=new ItemStack(Items.WRITTEN_BOOK);var tag=book.getOrCreateTag();
        tag.putString("title","Племінна книга");tag.putString("author",event.getEntity().getGameProfile().getName());
        var pages=new ListTag();
        String status=d.getList("Children",Tag.TAG_COMPOUND).isEmpty()?"Вагітності немає":"До народження: "+Math.max(0,(d.getLong("DueAt")-now(horse))/1200)+" хв.";
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(horse.getName().getString()+"\n"+(gender(horse)==0?"Самка":"Самець")+"\n"+status+"\nЗдоров’я: "+String.format(Locale.ROOT,"%.1f",horse.getMaxHealth())+"\nШвидкість: "+String.format(Locale.ROOT,"%.3f",horse.getAttribute(Attributes.MOVEMENT_SPEED).getValue())+"\nСтрибок: "+String.format(Locale.ROOT,"%.3f",horse.getAttribute(Attributes.JUMP_STRENGTH).getValue())+"\nПороди й масті: DLO"))));
        if(d.hasUUID("Mother"))pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Мати: "+d.getUUID("Mother")+"\nБатько: "+(d.hasUUID("Father")?d.getUUID("Father"):"невідомий")))));
        String inherited=String.format(Locale.ROOT,"Спадкові значення\nЗдоров’я: %.1f\nШвидкість: %.3f\nСтрибок: %.3f\n\nДля розведення порівнюйте ці значення. Тренування DLO змінює поточні характеристики.",genome.health()*d.getDouble("HealthScale"),genome.speed()*d.getDouble("SpeedScale"),genome.jump()*d.getDouble("JumpScale"));
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(inherited))));
        for(String prefix:List.of("speed","jump","health")) {
            StringBuilder pairs=new StringBuilder(switch(prefix){case "speed"->"Гени швидкості";case "jump"->"Гени стрибка";default->"Гени здоров’я";});
            for(int n=0;n<12;n++){var gene=EquineGenome.Gene.valueOf(prefix+n);pairs.append("\n").append(n+1).append(": ").append(genome.allele(gene,0)).append(" | ").append(genome.allele(gene,1));}
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(pairs.toString()))));
        }
        tag.put("pages",pages);var player=event.getEntity();
        if(!player.getAbilities().instabuild)event.getItemStack().shrink(1);
        if(event.getItemStack().isEmpty())player.setItemInHand(event.getHand(),book);else if(!player.getInventory().add(book))player.drop(book,false);
    }
}
