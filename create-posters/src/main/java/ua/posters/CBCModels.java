package ua.posters;
import java.util.*;import org.joml.Matrix4d;import org.joml.Quaterniond;
/** CBC's visible mount consists of the static block and separate moving partial models. */
public final class CBCModels {
    private final JsonModels models;
    public CBCModels(JsonModels models){this.models=models;}
    public List<Mesh.Triangle> mount(Map<String,String> state,double yaw,double pitch,int color){
        if(!Double.isFinite(yaw)||!Double.isFinite(pitch))throw new IllegalArgumentException("mount angles");
        boolean flipped=state.getOrDefault("vertical_direction","down").equals("up");
        Quaterniond body=flipped?new Quaterniond().rotationZ(Math.PI).rotateY(Math.toRadians(yaw)):new Quaterniond().rotationY(Math.toRadians(-yaw));
        List<Mesh.Triangle> out=new ArrayList<>(models.block("createbigcannons:cannon_mount",state,color));
        Matrix4d frame=new Matrix4d().translate(0,flipped?-1:1,0).translate(.5,.5,.5).rotate(body).translate(-.5,-.5,-.5);
        out.addAll(Mesh.transform(models.model("createbigcannons:block/cannon_mount/rotating_mount",color),frame,0,Integer.MIN_VALUE));
        Quaterniond axle=new Quaterniond(body).rotateX(Math.toRadians(flipped?pitch:-pitch));
        Matrix4d axlePose=new Matrix4d().translate(0,flipped?-2:2,0).translate(.5,.5,.5).rotate(axle).rotateY(Math.PI).translate(-.5,-.5,-.5);
        out.addAll(Mesh.transform(models.model("createbigcannons:block/cannon_carriage/carriage_axle",color),axlePose,0,Integer.MIN_VALUE));
        String facing=state.getOrDefault("facing","north"),axis=facing.equals("east")||facing.equals("west")?"z":"x";
        out.addAll(models.block("create:shaft",Map.of("axis",axis),color));
        double x=flipped?-Math.PI/2:Math.PI/2;
        Matrix4d yawShaft=new Matrix4d().translate(.5,.5,.5).rotateX(x).translate(-.5,-.5,-.5);
        out.addAll(Mesh.transform(models.model("create:block/shaft_half",color),yawShaft,0,Integer.MIN_VALUE));
        return List.copyOf(out);
    }
    public static double defaultYaw(String facing){return switch(facing){case "south"->0;case "west"->90;case "north"->180;case "east"->270;default->0;};}
}
