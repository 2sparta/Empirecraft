package ua.empirecraft.earth.mixin;
import ua.empirecraft.tfc.MaterialPalette;
import ua.empirecraft.earth.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=MaterialPalette.class,remap=false)
public abstract class MaterialMixin {
    @Inject(method="plan",at=@At("RETURN"),cancellable=true)
    private static void earth$plan(long seed,int x,int z,float temperature,boolean wet,CallbackInfoReturnable<MaterialPalette.Plan> cir){
        if(!EarthTerrainContext.active())return;
        var d=EarthMod.data;var p=d.coordinates(x*16.0+8,z*16.0+8);var original=cir.getReturnValue();
        var geo=d.geologyRegion(p);
        String upper=original.upper(),middle=original.middle(),lower=original.lower(),sand=original.sand();
        if(geo!=null){var rocks=geo.values().getAsJsonArray("rocks");upper=rocks.get(0).getAsString();middle=rocks.get(1).getAsString();lower=rocks.get(2).getAsString();sand=geo.text("sand",sand);}
        else{
            // Transition band: blend the neighbouring strata - upper/middle swap by weight,
            // lower keeps the region's basement, so the border grades instead of stepping.
            double total=0,granite=0,upperVotes=0;String upperRock=null,middleRock=null;
            for(var vote:d.geologyVotes(p).entrySet()){
                if(vote.getKey()==null)continue;total+=vote.getValue();
                var rocks=vote.getKey().values().getAsJsonArray("rocks");
                if(rocks.get(0).getAsString().equals("granite")||rocks.get(1).getAsString().equals("granite"))granite+=vote.getValue();
            }
            if(total>0&&(granite/total)>=0.35){var geo2=d.at("geology",p);if(geo2!=null){var rocks=geo2.values().getAsJsonArray("rocks");upper=rocks.get(0).getAsString();middle=rocks.get(1).getAsString();}}
        }
        var ecology=d.at("ecology",p);String soil=ecology==null?original.soil():d.soil(p);
        var kind=original.surface();
        // Peat requires wet ground and a mapped wet/cold forest zone; kaolin requires a humid warm climate.
        if(kind==MaterialPalette.Surface.PEAT&&(!wet||!java.util.Set.of("taiga","mixed_forest","wetland").contains(d.zone(p))))kind=MaterialPalette.Surface.SOIL;
        var climate=d.climate(p);
        if(kind==MaterialPalette.Surface.KAOLIN&&(climate.mean()<18||climate.rain()<1200))kind=MaterialPalette.Surface.SOIL;
        cir.setReturnValue(new MaterialPalette.Plan(upper,middle,lower,sand,soil,kind,original.seed()));
    }
}
