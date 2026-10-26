package ua.empirecraft.leaves;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
final class PlantCatalog {
    record Profile(int seasons,String climate,boolean renewal) {}
    static final TagKey<Block> EXTRA=tag("additional_vegetation"),KEEP=tag("evergreen_vegetation");
    private static final Map<String,Profile> EXPLICIT=new HashMap<>();
    private static final List<TagKey<Block>> SEASONS=List.of("spring","summer","autumn","winter").stream()
        .map(s->TagKey.create(Registries.BLOCK,new ResourceLocation("sereneseasons",s+"_crops"))).toList();
    static {
        try(var input=PlantCatalog.class.getResourceAsStream("/vegetation-profiles.json")) {
            if(input==null)throw new IOException("Missing vegetation profiles");
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var value:root.getAsJsonArray("renewal_plants")) {
                var row=value.getAsJsonObject(); int mask=0;
                for(var name:row.getAsJsonArray("seasons")) mask|=1<<List.of("spring","summer","autumn","winter").indexOf(name.getAsString());
                EXPLICIT.put(row.get("id").getAsString(),new Profile(mask,row.get("climate").getAsString(),true));
            }
        } catch(IOException e) {throw new IllegalStateException(e);}
    }
    static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,new ResourceLocation(SeasonalLeaves.ID,name));}
    static String id(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    static Profile profile(BlockState state) {
        if(state.hasBlockEntity() || state.is(KEEP))return null;
        String id=id(state); var explicit=EXPLICIT.get(id);
        if(explicit!=null)return explicit;
        Block block=state.getBlock();
        String path=BuiltInRegistries.BLOCK.getKey(block).getPath();
        boolean generic=block instanceof BushBlock && !(block instanceof CropBlock) && !(block instanceof SaplingBlock)
            && !(block instanceof DeadBushBlock) && !path.contains("azalea") && !path.contains("sapling")
            && !path.contains("crop") && !path.contains("potted") && !path.contains("leaves") && !path.contains("seedling");
        if(!generic && !state.is(EXTRA))return null;
        int mask=0;for(int i=0;i<SEASONS.size();i++)if(state.is(SEASONS.get(i)))mask|=1<<i;
        return new Profile(mask==0?7:mask,"any",false);
    }
    private PlantCatalog(){}
}
