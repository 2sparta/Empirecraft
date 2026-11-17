package ua.empirecraft.siegeworks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="siegeworks")
public final class Explosions {
 public static boolean siege(Entity source){if(source==null)return false;for(Class<?> c=source.getClass();c!=null;c=c.getSuperclass())if(c.getName().startsWith("me.mss1r.siegeworks.")||c.getName().startsWith("me.mss1r.axiomata."))return true;return false;}
 public static void filter(Explosion blast,Level level){if(!level.isClientSide&&(siege(blast.getDirectSourceEntity())||siege(blast.getIndirectSourceEntity())))blast.getToBlow().removeIf(p->!Blocks.allowed(level,p));}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void detonate(ExplosionEvent.Detonate event){filter(event.getExplosion(),event.getLevel());}
}
