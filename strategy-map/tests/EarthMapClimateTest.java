package ua.empirecraft.townymap;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.empirecraft.earth.Geography;
import static org.junit.jupiter.api.Assertions.*;

class EarthMapClimateTest {
    private static final Geography EARTH = new Geography(new InputStreamReader(
        Objects.requireNonNull(Geography.class.getResourceAsStream("/earth-defaults.json")),StandardCharsets.UTF_8));

    @Test void reportedUkrainianLandIsTemperateAndUsesAnnualPrecipitation() {
        var land=EarthMapClimate.land(EARTH,EARTH.coordinates(7879.48,-9660.19),64);
        assertEquals("temperate_continental",land.climate().id());
        assertEquals(550,land.annualRain());
    }

    @Test void reportedCaspianShoreIsAridAndTheWaterHasItsOwnName() {
        var point=EARTH.coordinates(11136,-7871);
        var land=EarthMapClimate.land(EARTH,point,63);
        assertEquals("cold_desert",land.climate().id());
        assertEquals(140,land.annualRain());
        assertEquals("Каспійське море",EarthMapClimate.sea(EARTH,point,true,false,false,false).name());
        assertNull(EarthMapClimate.sea(EARTH,point,false,true,true,false));
        assertNull(EarthMapClimate.sea(EARTH,point,true,false,false,true));
    }

    @Test void ordinaryLakesAndRiversKeepTheirLandClimate() {
        assertNull(EarthMapClimate.sea(EARTH,new Geography.Point(-88,47.7),true,false,false,false));
        assertNull(EarthMapClimate.sea(EARTH,new Geography.Point(55.1,38.6),true,false,false,false));
        assertNull(EarthMapClimate.sea(EARTH,new Geography.Point(-65,1),true,false,false,true));
    }

    @Test void australiaBlendsAcrossTheOldRectangleAndDistinguishesCoastFromInterior() {
        for (double lon : new double[]{119,123,129,134,139}) {
            for (double latitude : new double[]{-32,-20}) {
                var south=EarthMapClimate.land(EARTH,new Geography.Point(lon,latitude-.005),63);
                var north=EarthMapClimate.land(EARTH,new Geography.Point(lon,latitude+.005),63);
                assertEquals(south.annualRain(),north.annualRain(),3);
            }
        }
        double inland=EarthMapClimate.land(EARTH,new Geography.Point(129,-25),72).annualRain();
        double coast=EarthMapClimate.land(EARTH,new Geography.Point(146.6,-18),63).annualRain();
        assertTrue(inland<350);
        assertTrue(coast>1200);
    }

    @Test void obsoleteEarthLayersDisappearButLegacyTfcInOtherDimensionsRemains() {
        String earth="minecraft:overworld", other="minecraft:the_nether";
        var old=new ClaimKnowledge.Label(MapMode.CLIMATE,"climate/HOT_DESERT","Old",earth,1,16,"",0);
        var fresh=new ClaimKnowledge.Label(MapMode.CLIMATE,"earth/temperate_continental","New",earth,1,8,"",0);
        var tfc=new ClaimKnowledge.Label(MapMode.RAINFALL,"rainfall/1","TFC",other,1,16,"",0);
        assertFalse(ClimateObservations.displayable(old,java.util.Set.of(earth)));
        assertTrue(ClimateObservations.displayable(fresh,java.util.Set.of(earth)));
        assertTrue(ClimateObservations.displayable(tfc,java.util.Set.of(earth)));
    }

    @Test void tropicalWatersUseTheNamedBasin() {
        var point=new Geography.Point(-75,16);
        assertEquals("Карибське море",EarthMapClimate.sea(EARTH,point,true,true,false,false).name());
        assertEquals("Карибське море",EarthMapClimate.sea(EARTH,point,true,false,true,false).name());
    }
}
