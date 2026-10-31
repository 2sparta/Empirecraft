package ua.posters;
import java.io.*;import java.nio.file.*;import java.util.*;
import javax.imageio.ImageIO;import java.awt.image.BufferedImage;
import com.google.gson.*;
import net.minecraftforge.fml.ModList;
/** Read installed mod assets at runtime. Models/textures are not copied into this addon. */
public final class ModelAssets {
    @FunctionalInterface public interface Source {byte[] read(String namespace,String path)throws IOException;}
    public record Texture(int rgb,Mesh.Mask mask){}
    private final Source source;
    private final Map<String,Optional<JsonObject>> json=new LinkedHashMap<>(512,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Optional<JsonObject>> e){return size()>1024;}};
    private final Map<String,Optional<Texture>> textures=new LinkedHashMap<>(64,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Optional<Texture>> e){return size()>64;}};
    public ModelAssets(Source source){this.source=source;}
    public static ModelAssets installed(){return new ModelAssets((namespace,path)->{
        try{var list=ModList.get();var file=list==null?null:list.getModFileById(namespace);
            if(file!=null){Path p=file.getFile().findResource(path);if(Files.isRegularFile(p)&&Files.size(p)<=4*1024*1024)return Files.readAllBytes(p);}
        }catch(RuntimeException ignored){}
        try(var in=ModelAssets.class.getClassLoader().getResourceAsStream(path)){if(in==null)return null;return in.readNBytes(4*1024*1024+1);}
    });}
    public byte[] resource(String namespace,String path){try{if(path.contains("..")||path.startsWith("/"))return null;byte[] b=source.read(namespace,path);return b==null||b.length>4*1024*1024?null:b;}catch(IOException|RuntimeException ex){return null;}}
    public static String id(String value){if(!value.contains(":"))value="minecraft:"+value;if(!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||value.contains(".."))throw new IllegalArgumentException("model id");return value;}
    public JsonObject json(String id,String directory){
        id=id(id);String key=directory+"/"+id;var old=json.get(key);if(old!=null)return old.orElse(null);JsonObject obj=null;
        byte[] b=resource(id.split(":",2)[0],"assets/"+id.split(":",2)[0]+"/"+directory+"/"+id.split(":",2)[1]+".json");
        try{if(b!=null&&b.length<=2*1024*1024){JsonElement e=JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8));if(e.isJsonObject())obj=e.getAsJsonObject();}}catch(RuntimeException ignored){}
        json.put(key,Optional.ofNullable(obj));return obj;
    }
    public Texture texture(String name){
        if(name==null)return null;name=id(name);var old=textures.get(name);if(old!=null)return old.orElse(null);Texture value=null;
        byte[] b=resource(name.split(":",2)[0],"assets/"+name.split(":",2)[0]+"/textures/"+name.split(":",2)[1]+".png");
        try{if(b!=null&&b.length>=24){int w=readInt(b,16),h=readInt(b,20);if(w>0&&h>0&&w<=2048&&h<=2048&&(long)w*h<=4_194_304){
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(b));if(image!=null){int originalW=image.getWidth(),originalH=image.getHeight();w=Math.min(128,originalW);h=Math.min(128,originalH);int[] colors=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)colors[x+y*w]=image.getRGB(x*originalW/w,y*originalH/h);long r=0,g=0,bl=0,n=0;
                for(int c:colors){if((c>>>24)<128)continue;r+=(c>>16)&255;g+=(c>>8)&255;bl+=c&255;n++;}
                int rgb=n==0?0x898989:((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(bl/n);value=new Texture(rgb,new Mesh.Mask(w,h,colors,true,Mesh.Mask.luma(rgb)));
            }
        }}}catch(IOException|RuntimeException|LinkageError ignored){}
        textures.put(name,Optional.ofNullable(value));
        while(maskPixels()>8_388_608){var it=textures.entrySet().iterator();it.next();it.remove();}
        return value;
    }
    private long maskPixels(){long n=0;for(var value:textures.values())if(value.isPresent()&&value.get().mask()!=null)n+=(long)value.get().mask().width()*value.get().mask().height();return n;}
    private static int readInt(byte[] b,int at){return (b[at]&255)<<24|(b[at+1]&255)<<16|(b[at+2]&255)<<8|(b[at+3]&255);}
}
