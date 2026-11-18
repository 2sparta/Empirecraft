package ua.empirecraft.townymap;

import com.quintonc.vs_sails.wind.WindManager;
import net.minecraft.core.BlockPos;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import ua.empirecraft.earth.Geography;

final class SeasonalForecast {
    private static final String[] MONTHS = {"Січень","Лютий","Березень","Квітень","Травень","Червень",
        "Липень","Серпень","Вересень","Жовтень","Листопад","Грудень"};
    private static final String[] LEVELS = {"низький","середній","високий"};
    private static final String[] DIRECTIONS = {"схід","південний схід","південь","південний захід",
        "захід","північний захід","північ","північний схід"};
    private SeasonalForecast() {}

    static int month(ServerLevel level) {
        if (level == null || !ModList.get().isLoaded("sereneseasons")) return -1;
        if (!ModConfig.seasons.isDimensionWhitelisted(level.dimension())) return -1;
        var season = SeasonHelper.getSeasonState(level);
        return (Math.floorMod(season.getSeasonCycleTicks(), season.getCycleDuration()) / season.getSubSeasonDuration() + 2) % 12;
    }

    static ClaimKnowledge.Label observedTfc(String dimension, float temperature, float rain) {
        return new ClaimKnowledge.Label(MapMode.SEASONS, "tfc/"+Math.round(temperature)+"/"+Math.round(rain),
            "", dimension, 0, 16, "", 0);
    }

    static String wind(ServerLevel level, int x, int z) {
        if (level == null || !ModList.get().isLoaded("vs_sails")) return "";
        var position = new BlockPos(x,level.getSeaLevel(),z);
        if (!WindManager.isWindEnabled(level) || WindManager.getWindStrength(level,position)<0.05f) return "\nВітер · штиль";
        int direction = Math.floorMod(Math.round(WindManager.getWindDirection(level,Vec3.atCenterOf(position))),360);
        return "\nВітер дме на " + DIRECTIONS[((direction+22)/45)%8] + " (" + (direction+90)%360 + "°)";
    }

    static List<ClaimKnowledge.Label> at(ClaimKnowledge history, ClaimKnowledge.Chunk chunk, int x, int z, int month, Geography earth) {
        if (month < 0) return List.of();
        var seasonal = history.labelsAt(chunk, MapMode.SEASONS, x, z);
        if (!seasonal.isEmpty())
            return seasonal.stream().map(label -> project(label, x, z, month, earth)).filter(Objects::nonNull).toList();
        return history.labelsAt(chunk, MapMode.CLIMATE, x, z).stream()
                .map(label -> project(label, x, z, month, earth)).filter(Objects::nonNull).toList();
    }

    static List<Claims> claims(ClaimKnowledge.Snapshot snapshot, String dimension, int month, Geography earth) {
        if (month < 0) return List.of();
        Map<ClaimKnowledge.Label, Set<ClaimGeometry.Cell>> grouped = new HashMap<>();
        for (var observation : snapshot.observations()) {
            if (!observation.chunk().dimension().equals(dimension)) continue;
            var surveyed = new HashSet<ClaimGeometry.Cell>();
            for (var fragment : observation.fragments()) {
                if (fragment.label().mode() != MapMode.SEASONS) continue;
                for (var cell : fragment.cells()) {
                    surveyed.add(cell);
                    group(grouped, fragment.label(), cell, month, earth);
                }
            }
            for (var fragment : observation.fragments()) {
                var label = fragment.label();
                if (label.mode() != MapMode.CLIMATE) continue;
                for (var cell : fragment.cells()) if (!surveyed.contains(cell)) group(grouped,label,cell,month,earth);
            }
        }
        return grouped.entrySet().stream().map(entry -> {
            var label=entry.getKey();
            return new Claims(label.overlayId(),label.name(),label.dimension(),label.color(),
                label.cellSize(),entry.getValue(),label.detail(),label.layer(),"",label.profile());
        }).sorted(Comparator.comparing(Claims::id)).toList();
    }

    private static void group(Map<ClaimKnowledge.Label, Set<ClaimGeometry.Cell>> grouped,
                              ClaimKnowledge.Label label, ClaimGeometry.Cell cell, int month, Geography earth) {
        int size = label.cellSize();
        var forecast = project(label,cell.x()*size+size/2,cell.z()*size+size/2,month,earth);
        if (forecast != null) grouped.computeIfAbsent(forecast,ignored -> new HashSet<>()).add(cell);
    }

    private static ClaimKnowledge.Label project(ClaimKnowledge.Label label, int x, int z, int month, Geography earth) {
        String id = label.id();
        if (id.startsWith("sea/"))
            return new ClaimKnowledge.Label(MapMode.SEASONS,id,label.name(),label.dimension(),label.color(),label.cellSize(),
                "Морська акваторія",0);
        double temperature, rain;
        if (earth != null && (id.startsWith("earth/") || id.startsWith("terrain/"))) {
            var point=earth.coordinates(x,z);
            var normals=EarthMapClimate.normals(earth,point);
            double height=id.startsWith("terrain/")?Integer.parseInt(id.substring(8)):earth.seaLevel;
            temperature=normals.temperature(month,point.lat())-Math.max(0,height-earth.seaLevel)*earth.metresPerBlock*.0065;
            rain=normals.id().equals("australia_smooth")||normals.id().equals("eastern_caspian")
                ? normals.rain()/12 : earth.monthlyRainfall(point,month);
        } else if (id.startsWith("tfc/")) {
            String[] values=id.split("/");
            temperature=Integer.parseInt(values[1])+9*Math.cos((month-6)*Math.PI/6);
            rain=Integer.parseInt(values[2])/4.0;
        } else return null;
        var risk=risk(temperature,rain);
        var detail=MONTHS[month]+" · кліматична оцінка\nЗаморозки · "+LEVELS[risk.frost()]
                +"\nПосуха · "+LEVELS[risk.drought()]+"\nСніг · "+LEVELS[risk.snow()];
        return new ClaimKnowledge.Label(MapMode.SEASONS,"risk/"+risk.frost()+"/"+risk.drought()+"/"+risk.snow(),
            risk.title(),label.dimension(),risk.color(),label.cellSize(),detail,0);
    }

    record Risk(int frost,int drought,int snow) {
        String title() {
            if (snow==2) return "Високий ризик снігу";
            if (frost==2&&drought==2) return "Заморозки й посуха";
            if (frost==2) return "Високий ризик заморозків";
            if (drought==2) return "Високий ризик посухи";
            return frost+drought+snow==0 ? "Низькі сезонні ризики" : "Помірні сезонні ризики";
        }
        int color() {
            if (snow==2) return 0xc0e7f4;
            if (frost==2&&drought==2) return 0xa88ea8;
            if (frost==2) return 0x88a4e8;
            if (drought==2) return 0xe3a04a;
            return frost+drought+snow==0 ? 0x7eb382 : 0xaab577;
        }
    }

    static Risk risk(double temperature, double rain) {
        int frost=temperature<3?2:temperature<9?1:0;
        int snow=rain<8||temperature>5?0:temperature<=0&&rain>=20?2:1;
        double drying=Math.max(12, 17+1.7*temperature);
        int drought=rain<drying*.55?2:rain<drying?1:0;
        return new Risk(frost,drought,snow);
    }
}
