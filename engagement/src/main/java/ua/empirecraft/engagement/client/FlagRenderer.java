package ua.empirecraft.engagement.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.engagement.*;

@Mod.EventBusSubscriber(modid=EngagementMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FlagRenderer implements BlockEntityRenderer<NavalFlags.FlagEntity> {
    private static final ResourceLocation WHITE=new ResourceLocation(EngagementMod.ID,"textures/flag/white.png");
    public FlagRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(NavalFlags.TYPE.get(),FlagRenderer::new);}
    @Override public boolean shouldRenderOffScreen(NavalFlags.FlagEntity flag){return true;}
    @Override public int getViewDistance(){return 192;}
    @Override public void render(NavalFlags.FlagEntity flag,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-flag.getBlockState().getValue(NavalFlags.FlagBlock.FACING).toYRot()));
        float time=(flag.getLevel()==null?0:flag.getLevel().getGameTime())+partial;
        var mode=flag.effective;
        ResourceLocation picture=mode==CombatPolicy.Flag.STATE&&flag.kind.equals("painting")?painting(flag.payload):null;
        if(picture!=null)cloth(pose,buffers,picture,flag.width,time,light,1,1,1,0,1,0,1,0);
        else if(mode==CombatPolicy.Flag.STATE&&flag.kind.equals("banner")){
            try {
                var data=TagParser.parseTag(flag.payload);float[] base=DyeColor.byId(data.getInt("Base")).getTextureDiffuseColors();cloth(pose,buffers,WHITE,flag.width,time,light,base[0],base[1],base[2],0,1,0,1,0);
                int layer=0;for(Tag item:data.getList("Layers",10)){var row=(CompoundTag)item;var id=ResourceLocation.tryParse(row.getString("Pattern"));if(id==null)continue;float[] color=DyeColor.byId(row.getInt("Color")).getTextureDiffuseColors();
                    cloth(pose,buffers,new ResourceLocation(id.getNamespace(),"textures/entity/banner/"+id.getPath()+".png"),flag.width,time,light,color[0],color[1],color[2],0,20f/64,0,40f/64,++layer*.0004f);
                }
            }catch(Exception invalid){cloth(pose,buffers,new ResourceLocation(EngagementMod.ID,"textures/flag/pirate.png"),flag.width,time,light,1,1,1,0,1,0,1,0);}
        }else cloth(pose,buffers,new ResourceLocation(EngagementMod.ID,mode==CombatPolicy.Flag.PEACEFUL?"textures/flag/peaceful.png":"textures/flag/pirate.png"),flag.width,time,light,1,1,1,0,1,0,1,0);
        // Short finial; the player supplies the visible mast beneath this block.
        var pole=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));var matrix=pose.last();
        vertex(pole,matrix,-.06f,0,0,0,0,.35f,.23f,.12f,light);vertex(pole,matrix,.06f,0,0,1,0,.35f,.23f,.12f,light);vertex(pole,matrix,.06f,1.1f,0,1,1,.35f,.23f,.12f,light);vertex(pole,matrix,-.06f,1.1f,0,0,1,.35f,.23f,.12f,light);
        pose.popPose();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static ResourceLocation painting(String id){try{
        var type=(Class<? extends Enum>)Class.forName("immersive_paintings.resources.Painting$Type");Object texture=Class.forName("immersive_paintings.resources.ClientPaintingManager").getMethod("getPaintingTexture",ResourceLocation.class,type).invoke(null,new ResourceLocation(id),Enum.valueOf(type,"FULL"));
        return texture==null?null:(ResourceLocation)texture.getClass().getField("textureIdentifier").get(texture);
    }catch(ReflectiveOperationException|LinkageError invalid){return null;}}
    private static void cloth(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,int width,float time,int light,float r,float g,float b,float u0,float u1,float v0,float v1,float layer){
        var out=buffers.getBuffer(RenderType.entityTranslucent(texture));var matrix=pose.last();int strips=16;
        for(int i=0;i<strips;i++){float a=i/(float)strips,c=(i+1)/(float)strips,za=(float)Math.sin(time*.1-a*7)*.12f*a+layer,zc=(float)Math.sin(time*.1-c*7)*.12f*c+layer;
            float x0=.15f+a*width,x1=.15f+c*width,top=.8f,bottom=top-width*.5f;
            vertex(out,matrix,x0,top,za,u0+(u1-u0)*a,v0,r,g,b,light);vertex(out,matrix,x0,bottom,za,u0+(u1-u0)*a,v1,r,g,b,light);vertex(out,matrix,x1,bottom,zc,u0+(u1-u0)*c,v1,r,g,b,light);vertex(out,matrix,x1,top,zc,u0+(u1-u0)*c,v0,r,g,b,light);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose matrix,float x,float y,float z,float u,float v,float r,float g,float b,int light){out.vertex(matrix.pose(),x,y,z).color(r,g,b,1).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix.normal(),0,0,1).endVertex();}
}
