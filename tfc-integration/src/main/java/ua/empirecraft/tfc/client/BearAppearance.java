package ua.empirecraft.tfc.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starfish_studios.naturalist.client.model.BearModel;
import com.starfish_studios.naturalist.client.renderer.BearRenderer;
import com.starfish_studios.naturalist.common.entity.Bear;
import net.dries007.tfc.common.entities.TFCEntities;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.tfc.Integration;

/** The server entity stays an actual TFC predator. Only the visual model is borrowed. */
@Mod.EventBusSubscriber(modid=Integration.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BearAppearance {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(TFCEntities.GRIZZLY_BEAR.get(),Adapter::new);
        e.registerEntityRenderer(TFCEntities.BLACK_BEAR.get(),Adapter::new);
        e.registerEntityRenderer(TFCEntities.POLAR_BEAR.get(),Adapter::new);
    }
    private static final class Adapter extends EntityRenderer<Predator> {
        private final BearRenderer renderer;
        private final BearModel model=new BearModel();
        private final Map<Predator,Bear> views=new WeakHashMap<>();
        Adapter(EntityRendererProvider.Context context){super(context);renderer=new BearRenderer(context);shadowRadius=0.7f;}
        private Bear view(Predator entity){
            return views.computeIfAbsent(entity,e->{
                var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("naturalist:bear"));
                Bear visual=(Bear)Objects.requireNonNull(Objects.requireNonNull(type).create(e.level()));
                visual.setUUID(e.getUUID());return visual;
            });
        }
        @Override public ResourceLocation getTextureLocation(Predator entity){return model.getTextureResource(view(entity));}
        @Override public void render(Predator entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            Bear visual=view(entity);
            visual.tickCount=entity.tickCount;visual.setPos(entity.position());
            visual.xo=entity.xo;visual.yo=entity.yo;visual.zo=entity.zo;
            visual.setYRot(entity.getYRot());visual.yRotO=entity.yRotO;visual.setXRot(entity.getXRot());visual.xRotO=entity.xRotO;
            visual.yBodyRot=entity.yBodyRot;visual.yBodyRotO=entity.yBodyRotO;
            visual.yHeadRot=entity.yHeadRot;visual.yHeadRotO=entity.yHeadRotO;
            visual.setDeltaMovement(entity.getDeltaMovement());visual.setBaby(entity.isBaby());
            visual.setSleeping(entity.isSleeping());visual.setRemainingPersistentAngerTime(entity.getTarget()!=null?100:0);
            visual.hurtTime=entity.hurtTime;visual.deathTime=entity.deathTime;visual.swinging=entity.swinging;
            visual.walkAnimation.setSpeed(entity.walkAnimation.speed());
            visual.setCustomName(entity.getCustomName());visual.setCustomNameVisible(entity.isCustomNameVisible());
            renderer.render(visual,yaw,partial,pose,buffers,light);
        }
    }
}
