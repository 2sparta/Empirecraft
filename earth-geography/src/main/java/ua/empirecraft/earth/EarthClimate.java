package ua.empirecraft.earth;

import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.*;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec2;

/** Uses caller-supplied TFC calendar ticks, including forecasts. Other model behaviour is delegated. */
public record EarthClimate(ClimateModel delegate) implements ClimateModel {
    public ClimateModelType type(){return delegate.type();}
    public float getTemperature(LevelReader r,BlockPos p,long ticks,int days){
        if(!(r instanceof Level l)||!EarthMod.enabled(l))return delegate.getTemperature(r,p,ticks,days);
        double month=ICalendar.getMonthOfYear(ticks,days).ordinal()+ICalendar.getFractionOfMonth(ticks,days);
        return (float)EarthMod.data.temperature(p.getX(),p.getY(),p.getZ(),month);
    }
    public float getAverageTemperature(LevelReader r,BlockPos p){
        if(!(r instanceof Level l)||!EarthMod.enabled(l))return delegate.getAverageTemperature(r,p);
        var d=EarthMod.data;return (float)(d.climate(d.coordinates(p.getX(),p.getZ())).mean()-Math.max(0,p.getY()-d.seaLevel)*d.metresPerBlock*.0065);
    }
    public float getRainfall(LevelReader r,BlockPos p){
        if(!(r instanceof Level l)||!EarthMod.enabled(l))return delegate.getRainfall(r,p);
        // TFC 0..500 is a gameplay climate scale; it is not annual precipitation in mm.
        return (float)Math.min(500,EarthMod.data.monthlyRainfall(EarthMod.data.coordinates(p.getX(),p.getZ()),EarthMod.fractionalMonth(l))*3);
    }
    public float getFogginess(LevelReader l,BlockPos p,long ticks){return delegate.getFogginess(l,p,ticks);}
    public float getWaterFogginess(LevelReader l,BlockPos p,long ticks){return delegate.getWaterFogginess(l,p,ticks);}
    public Vec2 getWindVector(Level l,BlockPos p,long ticks){return delegate.getWindVector(l,p,ticks);}
    public void onChunkLoad(WorldGenLevel l,ChunkAccess c,ChunkData d){delegate.onChunkLoad(l,c,d);}
    public void onWorldLoad(ServerLevel l){delegate.onWorldLoad(l);}
    public void onSyncToClient(FriendlyByteBuf b){delegate.onSyncToClient(b);}
    public void onReceiveOnClient(FriendlyByteBuf b){delegate.onReceiveOnClient(b);}
}
