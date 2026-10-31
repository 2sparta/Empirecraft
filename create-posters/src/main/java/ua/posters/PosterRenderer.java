package ua.posters;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public final class PosterRenderer extends EntityRenderer<PosterEntity> {
    public PosterRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public ResourceLocation m_5478_(PosterEntity entity){return PosterTextures.texture(entity.drawing());}
    @Override public void m_7392_(PosterEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(!DrawingData.headerValid(e.drawing()))return;
        pose.m_85836_();pose.m_252781_(new Quaternionf().rotationY((float)Math.toRadians(180-e.m_6350_().m_122435_())));
        VertexConsumer v=buffers.m_6299_(RenderType.m_110458_(m_5478_(e)));var p=pose.m_85850_();
        float hw=e.m_7076_()/32f,hh=e.m_7068_()/32f;
        vertex(v,p,-hw,hh,0,0,light);vertex(v,p,-hw,-hh,0,1,light);vertex(v,p,hw,-hh,1,1,light);vertex(v,p,hw,hh,1,0,light);
        pose.m_85849_();super.m_7392_(e,yaw,partial,pose,buffers,light);
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,float x,float y,float u,float vv,int light){
        v.m_252986_(pose.m_252922_(),x,y,-.02f).m_6122_(255,255,255,255).m_7421_(u,vv).m_86008_(655360).m_85969_(light).m_252939_(pose.m_252943_(),0,0,-1).m_5752_();
    }
}
