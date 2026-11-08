package ua.empirecraft.leaves;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;

/** Weather timers live in vanilla level data; phase metadata survives a restart. */
@Mod.EventBusSubscriber(modid=SeasonalLeaves.ID)
public final class SeasonWeather {
    private SeasonWeather() {}
    public static final class State extends SavedData {
        int season=-1;boolean raining,manual;long end;
        static State load(CompoundTag n) {var s=new State();s.season=n.getInt("season");s.raining=n.getBoolean("raining");s.manual=n.getBoolean("manual");s.end=n.getLong("end");return s;}
        @Override public CompoundTag save(CompoundTag n) {n.putInt("season",season);n.putBoolean("raining",raining);n.putBoolean("manual",manual);n.putLong("end",end);return n;}
    }
    public static State state(ServerLevel level) {return level.getDataStorage().computeIfAbsent(State::load,State::new,"empirecraft_season_weather");}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("seasonweather").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("auto").executes(c->{
                var level=c.getSource().getLevel();var s=state(level);s.season=-1;s.manual=false;s.setDirty();
                ((ServerLevelData)level.getLevelData()).setClearWeatherTime(0);
                c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Автоматичну сезонну погоду відновлено."),false);return 1;
            }))
            .then(net.minecraft.commands.Commands.literal("status").executes(c->{
                var level=c.getSource().getLevel();var s=state(level);var data=(ServerLevelData)level.getLevelData();
                double day=Math.max(1,SeasonHelper.getSeasonState(level).getDayDuration());
                String mode=level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)?(s.manual?"ручний":"сезонний"):"цикл вимкнено";
                String text=String.format(java.util.Locale.ROOT,"Погода: %s; %s; залишилося %.2f дня календаря.",data.isRaining()?"опади":"сухо",mode,Math.max(data.getRainTime(),data.getClearWeatherTime())/day);
                c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(text),false);return 1;
            })));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
        if(!AtmosphereConfig.WEATHER.get()||!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)||!level.dimensionType().hasSkyLight()||!sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(level.dimension()))return;
        var season=SeasonHelper.getSeasonState(level);double phase=SeasonVisualPolicy.phase(season.getSeasonCycleTicks(),season.getSubSeasonDuration());int major=(int)phase/3;
        var s=state(level);
        var data=(ServerLevelData)level.getLevelData();long now=level.getGameTime();boolean raining=data.isRaining();
        int remaining=Math.max(data.getRainTime(),data.getClearWeatherTime());long nativeEnd=now+Math.max(0,remaining);
        boolean initialized=s.season>=0;
        // /weather clear and early changes of a native timer are administrative overrides.
        if(data.getClearWeatherTime()>0||(initialized&&now<s.end-40&&Math.abs(nativeEnd-s.end)>40)) {
            if(!s.manual||s.end!=nativeEnd||s.raining!=raining||s.season!=major) {
                s.manual=true;s.end=nativeEnd;s.raining=raining;s.season=major;s.setDirty();
            }
            return;
        }
        if(s.manual&&now<s.end-20)return;
        if(!initialized||s.season!=major||s.raining!=raining||now>=s.end) {
            int duration=WeatherPolicy.duration(phase,raining,Math.max(1,season.getDayDuration()),level.random.nextLong());
            boolean thunder=raining&&major!=3&&level.random.nextFloat()<(major==1?0.18f:0.06f);
            level.setWeatherParameters(0,duration,raining,thunder);
            data.setThunderTime(duration);
            s.season=major;s.raining=raining;s.manual=false;s.end=now+duration;s.setDirty();
        }
        if(major==3&&level.isThundering()) {data.setThundering(false);data.setThunderTime(Math.max(40,data.getRainTime()));}
    }
}
