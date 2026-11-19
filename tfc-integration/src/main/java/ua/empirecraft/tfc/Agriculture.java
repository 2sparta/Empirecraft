package ua.empirecraft.tfc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.server.level.ServerLevel;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.util.events.SelectClimateModelEvent;
import net.dries007.tfc.util.climate.BiomeBasedClimateModel;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateModelType;
import net.minecraft.world.level.LevelReader;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

/** Native TFC soil for new fields; existing crop identities are retained. */
public final class Agriculture {
    private static final ClimateModelType TYPE=Climate.register(new net.minecraft.resources.ResourceLocation(Integration.ID,"existing_climate"),PackClimate::new);
    public Agriculture() {
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::till);
        bus.addListener(this::climate);
        bus.addListener(EventPriority.LOWEST, CropAgronomy::interact);
        bus.addListener(EventPriority.LOWEST, CropAgronomy::place);
        bus.addListener(EventPriority.LOWEST, CropAgronomy::bonemeal);
        bus.addListener(EventPriority.LOWEST, CropAgronomy::breakSoil);
        bus.addListener((net.minecraftforge.event.level.LevelEvent.Load e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)Climate.onWorldLoad(level);
        });
        bus.addListener((net.minecraftforge.event.village.WandererTradesEvent e)->{
            for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS){
                var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                if(id==null||!id.getNamespace().equals("tfc")||!(item instanceof net.minecraft.world.item.BlockItem block))continue;
                String type=block.getBlock().getClass().getSimpleName();
                if(!type.contains("BerryBush")&&!type.contains("FruitTreeSapling")&&!type.contains("BananaSapling"))continue;
                e.getRareTrades().add((trader,random)->new net.minecraft.world.item.trading.MerchantOffer(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,2),new net.minecraft.world.item.ItemStack(item),4,1,0.05f));
            }
        });
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,(net.minecraftforge.event.TickEvent.LevelTickEvent e)->{
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.level instanceof net.minecraft.server.level.ServerLevel level)
                    ||level.dimension()!=Level.OVERWORLD)return;
            var current=ua.empirecraft.calendar.WallClockCalendar.current(level);if(current==null)return;
            long day=(current.year()-1)*360L+ua.empirecraft.calendar.CalendarMath.monthIndex(current.seasonTicks())*30L
                +ua.empirecraft.calendar.CalendarMath.dayOfMonth(current.seasonTicks())-1;
            var calendar=(ua.empirecraft.tfc.mixin.CalendarAccess)(Object)net.dries007.tfc.util.calendar.Calendars.SERVER;
            calendar.empirecraft$monthDays(30);
            calendar.empirecraft$calendarTicks(day*24000L+Math.floorMod(current.sunTicks()+6000,24000));
        });
    }
    private void climate(SelectClimateModelEvent e) { e.setModel(new PackClimate()); }
    public static final class PackClimate extends BiomeBasedClimateModel {
        @Override public ClimateModelType type(){return TYPE;}
        @Override public float getTemperature(LevelReader reader,BlockPos pos) {
            // LSO iterates live modifier registries; chunk mesh workers must never enter it.
            // Keep LSO for crops and client UI on their game thread; tint workers use biome climate.
            return reader instanceof Level level && (!level.isClientSide || net.minecraftforge.common.util.LogicalSidedProvider.WORKQUEUE.get(net.minecraftforge.fml.LogicalSide.CLIENT).isSameThread())
                ? TemperatureUtil.getWorldTemperature(level,pos) : super.getTemperature(reader,pos);
        }
    }
    private void till(BlockEvent.BlockToolModificationEvent e) {
        if(e.isCanceled() || e.isSimulated() || e.getToolAction()!=ToolActions.HOE_TILL) return;
        if(e.getLevel() instanceof ServerLevel level && e.getFinalState()!=null && e.getFinalState().is(Blocks.FARMLAND)) {
            var soil = RegionalSoils.at(level, e.getPos());
            e.setFinalState(Chernozem.block(SoilBlockType.FARMLAND, soil).defaultBlockState());
        }
    }
}
