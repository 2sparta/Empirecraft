package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Match collision, navigation clearance and aiming height on both logical sides. */
@Mod.EventBusSubscriber(modid="townyrecruits",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AppearanceEvents {
    @SubscribeEvent public static void size(EntityEvent.Size e){
        if(!(e.getEntity() instanceof AbstractRecruitEntity r))return;
        EntityDimensions original=e.getOriginalSize();
        float factor=Appearance.scale(r.m_20148_(),r.m_6095_().m_20679_());
        float height=e.getPose()==Pose.STANDING?Appearance.height(r.m_20148_()):original.f_20378_*factor;
        e.setNewSize(EntityDimensions.m_20395_(original.f_20377_,height),false);
        e.setNewEyeHeight(Appearance.eyeHeight(e.getNewEyeHeight(),factor,height));
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e){
        // During construction a provisional UUID may precede the saved/network UUID.
        // Recalculate once when the actual identity is known; no per-tick resizing.
        if(e.getEntity() instanceof AbstractRecruitEntity r)r.m_6210_();
    }
}
