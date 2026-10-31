package ua.posters;
import java.util.*;import com.google.gson.*;import org.joml.Matrix4d;
/** Vanilla/Forge JSON model elements, inherited textures, variants, multipart and element rotations. */
public final class JsonModels {
    private record Model(JsonArray elements,Map<String,String> textures){}
    private final ModelAssets assets;
    private final Map<String,Model> models=new LinkedHashMap<>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Model> e){return size()>512;}};
    public JsonModels(ModelAssets assets){this.assets=assets;}
    public List<Mesh.Triangle> block(String name,Map<String,String> properties,int fallbackColor){
        try{
            JsonObject state=assets.json(name,"blockstates");if(state==null)return List.of();List<Mesh.Triangle> out=new ArrayList<>();
            if(state.has("variants"))for(var entry:state.getAsJsonObject("variants").entrySet())if(variant(entry.getKey(),properties)){apply(out,entry.getValue(),fallbackColor);break;}
            if(state.has("multipart"))for(JsonElement e:state.getAsJsonArray("multipart")){JsonObject part=e.getAsJsonObject();if(!part.has("when")||matches(part.get("when"),properties))apply(out,part.get("apply"),fallbackColor);}
            if(out.size()>4096)throw new IllegalArgumentException("model complexity");return List.copyOf(out);
        }catch(RuntimeException ex){return List.of();}
    }
    public List<Mesh.Triangle> model(String id,int fallbackColor){
        try{return elements(resolve(ModelAssets.id(id),new HashSet<>()),fallbackColor);}catch(RuntimeException ex){return List.of();}
    }
    private void apply(List<Mesh.Triangle> out,JsonElement element,int color){
        if(element==null)return;if(element.isJsonArray()){if(element.getAsJsonArray().isEmpty())return;element=element.getAsJsonArray().get(0);}
        JsonObject entry=element.getAsJsonObject();if(!entry.has("model"))return;
        List<Mesh.Triangle> triangles=model(entry.get("model").getAsString(),color);
        Matrix4d rotation=new Matrix4d().translate(.5,.5,.5).rotateY(Math.toRadians(-number(entry,"y",0))).rotateX(Math.toRadians(-number(entry,"x",0))).translate(-.5,-.5,-.5);
        out.addAll(Mesh.transform(triangles,rotation,0,Integer.MIN_VALUE));
    }
    private Model resolve(String id,Set<String> seen){
        Model cached=models.get(id);if(cached!=null)return cached;if(seen.size()>24||!seen.add(id))throw new IllegalArgumentException("cyclic model");
        JsonObject json=assets.json(id,"models");if(json==null){Model builtin=builtin(id);if(builtin!=null){models.put(id,builtin);return builtin;}return new Model(new JsonArray(),Map.of());}
        Model parent=json.has("parent")?resolve(ModelAssets.id(json.get("parent").getAsString()),seen):new Model(new JsonArray(),Map.of());
        Map<String,String> textures=new HashMap<>(parent.textures);if(json.has("textures"))json.getAsJsonObject("textures").entrySet().forEach(e->textures.put(e.getKey(),e.getValue().getAsString()));
        Model value=new Model(json.has("elements")?json.getAsJsonArray("elements"):parent.elements,Map.copyOf(textures));models.put(id,value);return value;
    }
    private List<Mesh.Triangle> elements(Model model,int color){
        List<Mesh.Triangle> out=new ArrayList<>();if(model.elements.size()>512)return out;
        for(JsonElement el:model.elements){
            JsonObject e=el.getAsJsonObject();double[] f=vec(e.get("from")),t=vec(e.get("to"));if(!e.has("faces"))continue;
            Matrix4d matrix=new Matrix4d();
            if(e.has("rotation")){
                JsonObject r=e.getAsJsonObject("rotation");double[] origin=vec(r.get("origin"));double angle=Math.toRadians(number(r,"angle",0));String axis=r.get("axis").getAsString();if(Math.abs(angle)>Math.PI/4+.00001)throw new IllegalArgumentException("element angle");
                matrix.translate(origin[0]/16,origin[1]/16,origin[2]/16);
                switch(axis){case "x"->matrix.rotateX(angle);case "y"->matrix.rotateY(angle);case "z"->matrix.rotateZ(angle);default->throw new IllegalArgumentException("axis");}
                if(r.has("rescale")&&r.get("rescale").getAsBoolean()){double s=1/Math.cos(angle);matrix.scale(axis.equals("x")?1:s,axis.equals("y")?1:s,axis.equals("z")?1:s);}
                matrix.translate(-origin[0]/16,-origin[1]/16,-origin[2]/16);
            }
            for(var face:e.getAsJsonObject("faces").entrySet()){
                JsonObject data=face.getValue().getAsJsonObject();String texture=texture(data.has("texture")?data.get("texture").getAsString():null,model.textures);
                ModelAssets.Texture tex=texture==null?null:assets.texture(texture);int rgb=tex==null||data.has("tintindex")&&data.get("tintindex").getAsInt()>=0?color:tex.rgb();
                double[] uv=data.has("uv")?array(data.getAsJsonArray("uv"),4):uv(face.getKey(),f,t);
                var triangles=Mesh.face(face.getKey(),f[0]/16,f[1]/16,f[2]/16,t[0]/16,t[1]/16,t[2]/16,uv,(int)number(data,"rotation",0),0,rgb,tex==null?null:tex.mask());
                out.addAll(Mesh.transform(triangles,matrix,0,Integer.MIN_VALUE));
            }
        }return out;
    }
    /** Common unit-cube templates are generated because dedicated servers omit vanilla client assets. */
    private static Model builtin(String id){
        if(!id.startsWith("minecraft:block/"))return null;String name=id.substring("minecraft:block/".length());
        if(!Set.of("cube","cube_all","cube_column","cube_column_horizontal","cube_bottom_top","cube_top","orientable","orientable_with_bottom").contains(name))return null;
        JsonObject element=new JsonObject();JsonArray from=new JsonArray(),to=new JsonArray();for(int i=0;i<3;i++){from.add(0);to.add(16);}element.add("from",from);element.add("to",to);JsonObject faces=new JsonObject();
        for(String face:List.of("north","east","south","west","up","down")){
            String texture=switch(name){
                case "cube_all"->"all";
                case "cube_column","cube_column_horizontal"->face.equals("up")||face.equals("down")?"end":"side";
                case "cube_bottom_top"->face.equals("up")?"top":face.equals("down")?"bottom":"side";
                case "cube_top"->face.equals("up")?"top":"side";
                case "orientable","orientable_with_bottom"->face.equals("north")?"front":face.equals("up")?"top":face.equals("down")&&name.equals("orientable_with_bottom")?"bottom":"side";
                default->face;
            };
            JsonObject data=new JsonObject();data.addProperty("texture","#"+texture);faces.add(face,data);
        }
        element.add("faces",faces);JsonArray elements=new JsonArray();elements.add(element);return new Model(elements,Map.of());
    }
    private static String texture(String ref,Map<String,String> textures){Set<String> seen=new HashSet<>();while(ref!=null&&ref.startsWith("#")){if(!seen.add(ref)||seen.size()>24)return null;ref=textures.get(ref.substring(1));}try{return ref==null?null:ModelAssets.id(ref);}catch(IllegalArgumentException ex){return null;}}
    private static double[] uv(String face,double[] f,double[] t){return switch(face){case "up"->new double[]{f[0],f[2],t[0],t[2]};case "down"->new double[]{f[0],16-t[2],t[0],16-f[2]};case "north"->new double[]{16-t[0],16-t[1],16-f[0],16-f[1]};case "south"->new double[]{f[0],16-t[1],t[0],16-f[1]};case "west"->new double[]{f[2],16-t[1],t[2],16-f[1]};case "east"->new double[]{16-t[2],16-t[1],16-f[2],16-f[1]};default->throw new IllegalArgumentException("face");};}
    private static double number(JsonObject j,String key,double fallback){return j.has(key)?j.get(key).getAsDouble():fallback;}
    private static double[] vec(JsonElement j){return array(j.getAsJsonArray(),3);}
    private static double[] array(JsonArray j,int n){if(j.size()!=n)throw new IllegalArgumentException("vector");double[] v=new double[n];for(int i=0;i<n;i++){v[i]=j.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>512)throw new IllegalArgumentException("coordinate");}return v;}
    public static boolean variant(String condition,Map<String,String> properties){if(condition.isEmpty())return true;for(String pair:condition.split(",")){String[] p=pair.split("=",2);if(p.length!=2||!value(p[1],properties.get(p[0])))return false;}return true;}
    public static boolean matches(JsonElement condition,Map<String,String> properties){
        if(!condition.isJsonObject())return false;JsonObject obj=condition.getAsJsonObject();
        for(var e:obj.entrySet()){
            if(e.getKey().equals("OR")){boolean any=false;for(var c:e.getValue().getAsJsonArray())any|=matches(c,properties);if(!any)return false;}
            else if(e.getKey().equals("AND")){for(var c:e.getValue().getAsJsonArray())if(!matches(c,properties))return false;}
            else if(!value(e.getValue().getAsString(),properties.get(e.getKey())))return false;
        }return true;
    }
    private static boolean value(String condition,String actual){if(actual==null)return false;for(String c:condition.split("\\|"))if(c.equals(actual)||c.startsWith("!")&&!c.substring(1).equals(actual))return true;return false;}
}
