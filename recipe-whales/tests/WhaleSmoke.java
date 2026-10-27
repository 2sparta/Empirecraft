package ua.empirecraft.nativepatch.test;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_whale_test")
public class WhaleSmoke {
    public WhaleSmoke() { MinecraftForge.EVENT_BUS.addListener(this::started); }
    static void require(boolean b,String message) { if(!b) throw new AssertionError(message); }
    static ResourceLocation id(String value) { return new ResourceLocation(value); }
    void started(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            JsonObject profiles=JsonParser.parseString(Files.readString(Path.of("whale-loot.json"))).getAsJsonObject();
            var harvest=Class.forName("net.mcreator.wonderoussea.empirecraft.WhaleHarvest").getMethod("harvest",Entity.class);
            AABB area=new AABB(-20,70,-20,20,120,20);
            int tested=0;
            for(var profile:profiles.entrySet()) {
                String species=profile.getKey();JsonObject spec=profile.getValue().getAsJsonObject();
                var ids=new ArrayList<String>();
                if(spec.get("corpse").getAsBoolean()) {
                    String base=(species.equals("humpback_whale")?"humpback":species)+"_corpse";
                    ids.add(base);ids.add(base+"_2");ids.add(base+"_3");
                } else {
                    ids.add(species);
                    if(spec.has("variants")) for(var v:spec.getAsJsonArray("variants"))ids.add(v.getAsString());
                }
                for(String name:ids) {
                    for(var item:level.getEntitiesOfClass(ItemEntity.class,area))item.discard();
                    var type=ForgeRegistries.ENTITY_TYPES.getValue(id("wonderoussea:"+name));
                    require(type!=null,"Registered entity "+name);
                    var entity=type.create(level);entity.moveTo(0,100,0,0,0);level.addFreshEntity(entity);
                    if(spec.get("corpse").getAsBoolean()) {
                        // Exercise the patched original procedure, not only the new helper.
                        String procedure=switch(species) {
                            case "bowhead_whale"->"BowheadWhaleCorpseRightClickProcedure";
                            case "gray_whale"->"GrayWhaleCoprseRightClickProcedure";
                            case "humpback_whale"->"HumpbackCorpseRightClickedOnEntityProcedure";
                            default->"RightWhaleCorpseRightClickProcedure";
                        };
                        var method=Class.forName("net.mcreator.wonderoussea.procedures."+procedure).getMethod("execute",net.minecraft.world.level.LevelAccessor.class,double.class,double.class,double.class,Entity.class);
                        method.invoke(null,level,0d,100d,0d,entity);
                        require(entity.isRemoved(),"Consumed corpse "+name);
                        harvest.invoke(null,entity);
                        // A serialized and restored claimed carcass must not pay again.
                        CompoundTag save=entity.saveWithoutId(new CompoundTag());
                        var restored=type.create(level);restored.load(save);restored.moveTo(0,100,0,0,0);
                        harvest.invoke(null,restored);
                    } else {
                        ((LivingEntity)entity).die(level.damageSources().generic());
                    }
                    var totals=new HashMap<String,Integer>();
                    for(var item:level.getEntitiesOfClass(ItemEntity.class,area)) {
                        ItemStack stack=item.getItem();
                        require(stack.getCount()>0 && stack.getCount()<=stack.getMaxStackSize(),"Legal stack "+name);
                        totals.merge(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
                    }
                    for(var loot:spec.getAsJsonObject("loot").entrySet()) {
                        int amount=totals.getOrDefault(loot.getKey(),0);var range=loot.getValue().getAsJsonArray();
                        require(amount>=range.get(0).getAsInt() && amount<=range.get(1).getAsInt(),name+" "+loot.getKey()+" total="+amount);
                    }
                    entity.discard();tested++;
                }
            }
            for(var item:level.getEntitiesOfClass(ItemEntity.class,area))item.discard();
            level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,server);
            var corpse=ForgeRegistries.ENTITY_TYPES.getValue(id("wonderoussea:bowhead_whale_corpse")).create(level);
            corpse.moveTo(0,100,0,0,0);level.addFreshEntity(corpse);harvest.invoke(null,corpse);
            require(level.getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"doMobLoot false");
            require(corpse.isRemoved(),"No empty corpse farming");
            Files.writeString(Path.of("smoke-pass.txt"),"PASS real Forge: "+tested+" whale/corpse variants; counts, stacks, repeat packets, NBT reload and doMobLoot\n");
            System.out.println(Files.readString(Path.of("smoke-pass.txt")));
        } catch(Throwable t) { t.printStackTrace();try {Files.writeString(Path.of("smoke-fail.txt"),t.toString());}catch(Exception ignored){} }
        server.halt(false);
    }
}

