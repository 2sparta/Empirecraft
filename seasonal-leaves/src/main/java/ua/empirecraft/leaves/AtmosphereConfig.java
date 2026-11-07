package ua.empirecraft.leaves;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AtmosphereConfig {
    public static ForgeConfigSpec.BooleanValue WEATHER,PROTECT,COLORS,SKY;
    private AtmosphereConfig() {}
    static void define(ForgeConfigSpec.Builder b) {
        b.push("atmosphere");
        WEATHER=b.comment("Season-specific weather; winter wet periods last 2-5 configured calendar days. Honors doWeatherCycle and manual weather overrides.").define("seasonalWeather",true);
        PROTECT=b.comment("Keep leaf supports intact while a player stands or jumps above them; resume seasonal fall after leaving.").define("protectPlayersOnLeaves",true);
        COLORS=b.comment("Client: mixed per-block autumn palettes without extra textures.").define("autumnColors",true);
        SKY=b.comment("Client: season-dependent sky and fog colours; requires this mod on the client.").define("seasonalSky",true);
        b.pop();
    }
}
