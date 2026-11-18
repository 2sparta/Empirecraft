package ua.empirecraft.townymap;

import ua.empirecraft.earth.Geography;

/** Descriptive climate bands for the geographic map, expressed in annual millimetres. */
final class EarthMapClimate {
    record Band(String id, String name, int color) {}
    record Land(Band climate, double annualRain) {}
    private static final Band HOT_DESERT = new Band("hot_desert","Спекотна пустеля",0xe7bf70);
    private static final Band COLD_DESERT = new Band("cold_desert","Холодна пустеля",0xc7ac86);
    private static final Band DRY_STEPPE = new Band("dry_steppe","Напівпосушливий степ",0xc8b268);
    private static final Band CONTINENTAL = new Band("temperate_continental","Помірний континентальний",0x8eae77);
    private static final Band OCEANIC = new Band("temperate_oceanic","Помірний океанічний",0x76a794);
    private static final Band MEDITERRANEAN = new Band("mediterranean","Середземноморський",0xb3bf69);
    private static final Band SUBTROPICAL = new Band("subtropical","Субтропічний",0x78ad7a);
    private static final Band TROPICAL = new Band("tropical_seasonal","Сезонно-вологі тропіки",0x8bb35f);
    private static final Band RAINFOREST = new Band("tropical_rainforest","Тропічний дощовий ліс",0x32866c);
    private static final Band TUNDRA = new Band("tundra","Тундра",0xa7c7bd);
    private static final Band POLAR = new Band("polar","Полярний",0xcce8f4);
    private static final Band SUBARCTIC = new Band("subarctic","Субарктичний",0x86a79e);
    private record Reference(double lon,double lat,double mean,double amplitude,double annualRain) {}
    // Approximate regional normals for the mainland, with separate coastal and inland reference points.
    private static final Reference[] AUSTRALIAN_NORMALS = {
        new Reference(114.2,-22.0,25,6,270), new Reference(119.0,-23.0,25,8,295),
        new Reference(122.2,-18.0,27,6,660), new Reference(126.0,-16.2,28,5,930),
        new Reference(130.9,-12.5,28,3,1710), new Reference(136.4,-13.0,27,4,1250),
        new Reference(139.0,-18.0,27,7,430), new Reference(144.0,-15.1,26,4,1280),
        new Reference(146.6,-18.0,25,4,1880), new Reference(149.2,-21.2,24,6,920),
        new Reference(151.0,-27.5,20,6,1050), new Reference(151.2,-33.8,18,6,1180),
        new Reference(145.0,-37.8,15,6,640), new Reference(147.2,-42.9,12,6,610),
        new Reference(138.6,-34.9,17,7,540), new Reference(134.0,-32.1,17,8,305),
        new Reference(121.9,-33.8,17,6,590), new Reference(115.9,-32.0,18,7,760),
        new Reference(118.5,-29.0,22,8,230), new Reference(129.0,-25.0,24,9,175),
        new Reference(133.9,-23.7,22,10,275), new Reference(137.8,-28.0,22,10,165),
        new Reference(145.0,-26.0,23,10,370), new Reference(148.5,-32.0,17,9,565)
    };
    private static final double[][] CASPIAN = {
        {47.0,47.4},{51.8,47.4},{52.5,45.4},{54.0,44.4},{55.0,41.0},{54.5,38.4},
        {54.4,36.8},{50.4,36.2},{48.3,38.1},{48.5,41.1},{46.8,44.7},{47.0,47.4}
    };

    private EarthMapClimate() {}

    static Land land(Geography data, Geography.Point point, int height) {
        var climate = data.climate(point);
        if (climate.id().equals("latitude_fallback") || climate.id().equals("central_australia")) {
            if (withinAustralia(point)) climate = australia(point);
            else if (eastCaspian(point)) climate = new Geography.Climate(16,15,140,"eastern_caspian",true);
        }
        double mean = climate.mean() - Math.max(0,height-data.seaLevel)*data.metresPerBlock*.0065;
        double annual = climate.rain(), warmest = mean+climate.amplitude(), coldest = mean-climate.amplitude();
        // Approximate aridity threshold with neutral seasonality. The map does not use TFC's 0–500 gameplay index here.
        double dry = Math.max(0,20*mean+140);
        Band band;
        if (warmest<=0) band=POLAR;
        else if (warmest<10) band=TUNDRA;
        else if (annual<dry/2) band=mean>=18?HOT_DESERT:COLD_DESERT;
        else if (annual<dry) band=DRY_STEPPE;
        else if (coldest>=18) band=annual>=1800?RAINFOREST:TROPICAL;
        else if (climate.id().equals("mediterranean")) band=MEDITERRANEAN;
        else if (mean>=17) band=SUBTROPICAL;
        else if (coldest<=-15 && warmest<22) band=SUBARCTIC;
        else band=coldest<0 || climate.amplitude()>=10?CONTINENTAL:OCEANIC;
        return new Land(band,annual);
    }

    static Band sea(Geography data, Geography.Point point, boolean water, boolean ocean, boolean salt, boolean freshwater) {
        if (!water || freshwater) return null;
        var basin=data.at("marine",point);
        // The pack has an explicit Caspian Sea observation. The outline avoids treating nearby lakes as the sea.
        if (!ocean && !salt && !insideCaspian(point)) return null;
        if (basin!=null && (!basin.id().equals("caspian") || insideCaspian(point)))
            return new Band(basin.id(),basin.label(),0x387eab);
        if (!ocean && !salt) return null;
        String name=point.lat()>=66?"Північний Льодовитий океан":point.lat()<=-60?"Південний океан"
            :point.lon()< -70||point.lon()>=100?"Тихий океан":point.lon()>=20?"Індійський океан":"Атлантичний океан";
        return new Band("ocean/"+name,name,0x387eab);
    }

    private static boolean withinAustralia(Geography.Point p) {
        return p.lon()>=112 && p.lon()<=155 && p.lat()>=-44 && p.lat()<=-10;
    }

    private static Geography.Climate australia(Geography.Point p) {
        double mass=0, mean=0, amplitude=0, annual=0;
        double longitudeScale=Math.cos(Math.toRadians(p.lat()));
        for (Reference reference:AUSTRALIAN_NORMALS) {
            double x=(reference.lon()-p.lon())*longitudeScale, z=reference.lat()-p.lat();
            double weight=Math.exp(-(x*x+z*z)/16);
            mass+=weight; mean+=weight*reference.mean();
            amplitude+=weight*reference.amplitude(); annual+=weight*reference.annualRain();
        }
        return new Geography.Climate(mean/mass,amplitude/mass,annual/mass,"australia_smooth",true);
    }

    private static boolean eastCaspian(Geography.Point p) {
        return p.lon()>=52.2 && p.lon()<=60 && p.lat()>=37.5 && p.lat()<=44.5;
    }

    private static boolean insideCaspian(Geography.Point p) {
        boolean inside=false;
        for (int i=1;i<CASPIAN.length;i++) {
            double[] a=CASPIAN[i-1], b=CASPIAN[i];
            if ((p.lat()<a[1])!=(p.lat()<b[1])
                && p.lon()<(b[0]-a[0])*(p.lat()-a[1])/(b[1]-a[1])+a[0]) inside=!inside;
        }
        return inside;
    }
}
