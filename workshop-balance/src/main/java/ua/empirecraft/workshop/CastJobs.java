package ua.empirecraft.workshop;

import java.util.*;
import me.mss1r.siegeworks.item.crafting.CastingRecipe;
import me.mss1r.siegeworks.registry.SiegeworksBlocks;
import me.mss1r.siegeworks.gameplay.advancement.SiegeAdvancement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.ItemHandlerHelper;

/** Paid batches belong to the table, survive restarts and never tick unloaded chunks.
 * The native table has no block entity; dimension SavedData also supports already placed tables.
 */
public final class CastJobs extends SavedData {
    public static final String NAME="empirecraft_casting_jobs";
    public final Map<BlockPos,Job> jobs=new HashMap<>();
    public static final class Job {
        public final ItemStack result;
        public final List<ItemStack> metal;
        public final int duration,diameter;
        public int progress;
        Job(ItemStack result,List<ItemStack> metal,int duration,int diameter){this.result=result;this.metal=metal;this.duration=duration;this.diameter=diameter;}
        public boolean ready(){return progress>=duration;}
    }
    public static CastJobs get(ServerLevel level){return level.m_8895_().m_164861_(CastJobs::load,CastJobs::new,NAME);}
    public static CastJobs load(CompoundTag nbt){
        var data=new CastJobs();
        for(var raw:nbt.m_128437_("jobs",10)){
            var tag=(CompoundTag)raw;var metal=new ArrayList<ItemStack>();
            for(var item:tag.m_128437_("metal",10)){var stack=ItemStack.m_41712_((CompoundTag)item);if(!stack.m_41619_())metal.add(stack);}
            var result=ItemStack.m_41712_(tag.m_128469_("result"));int ticks=tag.m_128451_("duration");
            if(result.m_41619_()||ticks<200||ticks>12000||metal.isEmpty())continue;
            int diameter=tag.m_128451_("diameter");if(!CastRules.validDiameter(diameter))diameter=120;
            var job=new Job(result,metal,ticks,diameter);job.progress=Math.max(0,Math.min(ticks,tag.m_128451_("progress")));
            data.jobs.put(BlockPos.m_122022_(tag.m_128454_("pos")),job);
        }
        return data;
    }
    @Override public CompoundTag m_7176_(CompoundTag nbt){
        var entries=new ListTag();
        jobs.forEach((pos,job)->{var tag=new CompoundTag();tag.m_128356_("pos",pos.m_121878_());tag.m_128365_("result",job.result.m_41739_(new CompoundTag()));tag.m_128405_("duration",job.duration);tag.m_128405_("progress",job.progress);tag.m_128405_("diameter",job.diameter);var metal=new ListTag();job.metal.forEach(s->metal.add(s.m_41739_(new CompoundTag())));tag.m_128365_("metal",metal);entries.add(tag);});
        nbt.m_128365_("jobs",entries);return nbt;
    }
    public void tick(ServerLevel level){
        var entries=jobs.entrySet().iterator();boolean changed=false;
        while(entries.hasNext()){
            var entry=entries.next();var pos=entry.getKey();var job=entry.getValue();
            if(!level.m_46805_(pos))continue;
            if(!level.m_8055_(pos).m_60713_(SiegeworksBlocks.CASTING_TABLE.get())){
                var drops=job.ready()?List.of(job.result):job.metal;
                for(var stack:drops)Containers.m_18992_(level,pos.m_123341_()+.5,pos.m_123342_()+.5,pos.m_123343_()+.5,stack.m_41777_());
                entries.remove();changed=true;
            }else if(!job.ready()){job.progress++;changed=true;}
        }
        if(changed)m_77762_();
    }
    public static boolean materials(Player player,CastingRecipe recipe,int diameter){return plan(player,recipe,diameter,false)!=null;}
    private record Plan(List<ItemStack> next,List<ItemStack> metal,List<ItemStack> remainders,int duration){}
    private static Plan plan(Player player,CastingRecipe recipe,int diameter,boolean fuel){
        if(recipe==null||!CastRules.validDiameter(diameter))return null;
        int cost=recipe.batch(diameter).cost();if(cost<1||cost>100000)return null;
        var next=new ArrayList<ItemStack>();for(int slot=0;slot<36;slot++)next.add(player.m_150109_().m_8020_(slot).m_41777_());
        var paid=new ArrayList<ItemStack>();int remaining=cost;
        for(var stack:next)if(remaining>0&&recipe.ingredient.test(stack)){int count=Math.min(remaining,stack.m_41613_());paid.add(stack.m_255036_(count));stack.m_41774_(count);remaining-=count;}
        if(remaining>0)return null;
        int duration=CastRules.duration(cost),heat=duration;var remainders=new ArrayList<ItemStack>();
        if(fuel)for(var stack:next){
            if(stack.m_41619_())continue;
            int burn=ForgeHooks.getBurnTime(stack,RecipeType.f_44107_);if(burn<=0)continue;
            while(heat>0&&!stack.m_41619_()){
                var rest=stack.getCraftingRemainingItem();if(!rest.m_41619_())remainders.add(rest);
                stack.m_41774_(1);heat-=burn;
            }
            if(heat<=0)break;
        }
        if(fuel&&heat>0)return null;
        return new Plan(next,paid,remainders,duration);
    }
    public boolean canStart(Player player,CastingRecipe recipe,int diameter){return plan(player,recipe,diameter,true)!=null;}
    public boolean start(ServerLevel level,BlockPos pos,Player player,CastingRecipe recipe,int diameter){
        if(jobs.containsKey(pos)||player.m_5833_())return false;
        var plan=plan(player,recipe,diameter,true);if(plan==null)return false;
        var result=recipe.output(diameter);if(result.m_41619_())return false;
        for(int i=0;i<36;i++)player.m_150109_().m_6836_(i,plan.next.get(i));
        for(var rest:plan.remainders)ItemHandlerHelper.giveItemToPlayer(player,rest);
        player.m_150109_().m_6596_();jobs.put(pos.m_7949_(),new Job(result.m_41777_(),plan.metal,plan.duration,diameter));m_77762_();return true;
    }
    public boolean collect(BlockPos pos,Player player){
        var job=jobs.get(pos);if(job==null||!job.ready())return false;
        // Inventory.add mutates the remainder, so a full inventory leaves the batch safely in the table.
        var rest=job.result.m_41777_();player.m_150109_().m_36054_(rest);
        if(rest.m_41613_()==job.result.m_41613_())return false;
        if(rest.m_41619_())jobs.remove(pos);else job.result.m_41764_(rest.m_41613_());
        player.m_150109_().m_6596_();m_77762_();SiegeAdvancement.CAST_TO_SIZE.award(player);return true;
    }
}
