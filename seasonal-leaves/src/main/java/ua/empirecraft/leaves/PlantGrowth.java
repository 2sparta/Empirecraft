package ua.empirecraft.leaves;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.LoggerFactory;

/** One persisted server switch shared by every growth entry point and every dimension. */
@Mod.EventBusSubscriber(modid=SeasonalLeaves.ID)
public final class PlantGrowth {
    private PlantGrowth() {}
    public static boolean enabled() {return VegetationConfig.GROWTH.get();}
    public static boolean blocked(ServerLevel level,BlockPos pos,BlockState state) {
        return !enabled() && (PlantCatalog.profile(state)!=null || SeasonalVegetation.managedTree(level,pos,state));
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("plantgrowth").requires(source->source.hasPermission(2))
                .executes(ctx->status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx->status(ctx.getSource())))
                .then(Commands.literal("off").executes(ctx->set(ctx.getSource(),false)))
                .then(Commands.literal("on").executes(ctx->set(ctx.getSource(),true))));
    }
    private static int status(CommandSourceStack source) {
        source.sendSuccess(()->Component.literal("Ріст рослин зараз "+(enabled()?"увімкнено":"вимкнено")),false);return 1;
    }
    private static int set(CommandSourceStack source,boolean enabled) {
        boolean previous=enabled();
        try {
            VegetationConfig.GROWTH.set(enabled);
            VegetationConfig.GROWTH.save();
        }catch(RuntimeException failure) {
            VegetationConfig.GROWTH.set(previous);
            LoggerFactory.getLogger(SeasonalLeaves.ID).error("Could not save the global plant growth setting",failure);
            source.sendFailure(Component.literal("Не вдалося зберегти налаштування росту рослин."));return 0;
        }
        source.sendSuccess(()->Component.literal("Ріст рослин успішно "+(enabled?"увімкнено":"вимкнено")),false);return 1;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void bonemeal(BonemealEvent event) {
        if(event.getLevel() instanceof ServerLevel level && blocked(level,event.getPos(),event.getBlock()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void crop(BlockEvent.CropGrowEvent.Pre event) {
        if(event.getLevel() instanceof ServerLevel level && blocked(level,event.getPos(),event.getState()))event.setResult(Event.Result.DENY);
    }
}
