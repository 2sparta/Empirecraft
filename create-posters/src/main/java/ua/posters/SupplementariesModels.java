package ua.posters;
import java.util.*;import org.joml.Matrix4d;import org.joml.Quaterniond;import org.joml.Vector3d;
/** Reads the installed cannon's real model definition and its saved global yaw/pitch. */
public final class SupplementariesModels {
    private final ModelAssets assets;private JavaModelReader.Node cannon;private boolean loaded;
    public SupplementariesModels(ModelAssets assets){this.assets=assets;}
    public List<Mesh.Triangle> cannon(String facing,double yaw,double pitch){
        try{
            if(!loaded){loaded=true;cannon=JavaModelReader.read(assets.resource("supplementaries","net/mehvahdjukaar/supplementaries/client/renderers/tiles/CannonBlockTileRenderer.class"),"createMesh");}
            if(cannon==null)return List.of();if(!Double.isFinite(yaw)||!Double.isFinite(pitch))return List.of();
            Quaterniond base=new Quaterniond(net.minecraft.core.Direction.valueOf(facing.toUpperCase(Locale.ROOT)).m_122424_().m_253075_());Vector3d axis=new Vector3d(0,0,1).rotateX(Math.PI-Math.toRadians(pitch)).rotateY(Math.PI-Math.toRadians(yaw)).rotate(new Quaterniond(base).invert());
            double y=Math.atan2(axis.x,axis.z),x=Math.atan2(-axis.y,Math.hypot(axis.x,axis.z));
            var pivot=cannon.find("head_pivot");if(pivot==null)return List.of();var p=pivot.pose;
            Map<String,JavaModelReader.Pose> pose=Map.of("legs",new JavaModelReader.Pose(0,0,0,0,y,0),"head_pivot",new JavaModelReader.Pose(p.x(),p.y(),p.z(),x,0,0));
            var tex=assets.texture("supplementaries:block/cannon");int rgb=tex==null?0x44484e:tex.rgb();
            return cannon.geometry(new Matrix4d().translate(.5,.5,.5).rotate(base),pose,rgb,tex);
        }catch(RuntimeException ex){return List.of();}
    }
}
