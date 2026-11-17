package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public final class MixinTest {
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(args[0].equals("CLIENT")?MixinEnvironment.Side.CLIENT:MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft-earth.mixins.json");var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        String[] names={"net.dries007.tfc.util.climate.Climate","ua.empirecraft.tfc.MaterialPalette","ua.empirecraft.tfc.NaturalMaterials","ua.empirecraft.tfc.OneTimeTerrain","ua.empirecraft.ores.OreRetrogen","ua.empirecraft.calendar.SeasonalPlants","ua.empirecraft.calendar.CalendarMod","net.minecraft.world.level.chunk.ChunkStatus","ua.empirecraft.fauna.NearbyFauna","sereneseasons.season.SeasonalCropGrowthHandler"};
        for(String name:names){
            if(args[0].equals("CLIENT")&&(name.equals("ua.empirecraft.ores.OreRetrogen")||name.equals("ua.empirecraft.fauna.NearbyFauna")))continue;
            var node=service.getClassNode(name);
            if(!transformer.transformClass(env,name,node))throw new AssertionError("No transformation "+name);
            for(MethodNode method:node.methods)if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
            System.out.println("PASS "+args[0]+" Mixin transform + ASM verifier: "+name);
        }
    }
}
