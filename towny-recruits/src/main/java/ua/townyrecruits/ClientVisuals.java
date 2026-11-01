package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Install the same JAR on clients to guarantee no name labels are rendered. */
@Mod.EventBusSubscriber(modid="townyrecruits",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientVisuals {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void nametag(RenderNameTagEvent e){if(e.getEntity() instanceof AbstractRecruitEntity)e.setResult(Event.Result.DENY);}
}
