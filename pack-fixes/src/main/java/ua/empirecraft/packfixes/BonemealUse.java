package ua.empirecraft.packfixes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Bone meal grows plants; interaction callbacks must not manufacture steel ammunition. */
@Mod.EventBusSubscriber(modid=PackFixes.ID)
public final class BonemealUse {
    private record Context(Level level,Vec3 center) {}
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    private BonemealUse() {}
    public static Scope enter(Player player,ItemStack stack,Vec3 center) {
        Context previous=CURRENT.get();
        if(stack.is(Items.BONE_MEAL))CURRENT.set(new Context(player.level(),center));
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Context previous;private boolean closed;
        private Scope(Context previous){this.previous=previous;}
        @Override public void close(){if(!closed){closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void spawned(EntityJoinLevelEvent event) {
        Context context=CURRENT.get();
        if(context==null||event.getLevel()!=context.level||!(event.getEntity() instanceof ItemEntity item)
            ||item.position().distanceToSqr(context.center)>64)return;
        var id=ForgeRegistries.ITEMS.getKey(item.getItem().getItem());
        if(id!=null&&id.toString().equals("overgeared:steel_arrow"))event.setCanceled(true);
    }
}
