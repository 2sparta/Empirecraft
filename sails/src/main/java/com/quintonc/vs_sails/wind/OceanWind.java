package com.quintonc.vs_sails.wind;

/** Large-scale winds in geographic coordinates. Wind angles indicate where the air is going. */
public final class OceanWind {
    private static final double YEAR = 2 * Math.PI;

    private OceanWind() {}

    public static double direction(double longitude, double latitude, double month, double weather) {
        double lon = Math.toRadians(longitude);
        double lat = Math.max(-90, Math.min(90, latitude));
        double seasonal = Math.cos(YEAR * (month - 6) / 12);
        double convergence = 5 * seasonal + 2 * Math.sin(lon + 0.4);
        double belt = lat - convergence;
        double absolute = Math.abs(belt);
        double hemisphere = Math.tanh(belt / 8);
        double trades = 180 - 28 * hemisphere;
        double westerlies = 180 + 156 * hemisphere;
        double polar = 180 - 38 * hemisphere;
        double wind = blend(trades, westerlies, smooth(23, 38, absolute));
        wind = blend(wind, polar, smooth(59, 77, absolute));

        // A seasonal reversal over the northern Indian Ocean with a broad offshore transition.
        double indian = Math.exp(4.4 * (Math.cos(lon - Math.toRadians(76)) - 1))
                * Math.exp(-Math.pow((lat - 14) / 14, 2));
        wind += 162 * indian * smooth(-0.45, 0.95, seasonal);
        // Broad zonal trades over the central Pacific blend into the other basins.
        double pacific = Math.exp(2.2 * (Math.cos(lon + Math.toRadians(145)) - 1));
        wind += 32 * pacific * (1 - smooth(25, 58, absolute));
        wind += 19 * Math.sin(lon + Math.toRadians(lat * 2.6))
              + 12 * Math.sin(2 * lon - Math.toRadians(lat));
        wind += 11 * Math.sin(Math.toRadians(weather + lat * 3) + 3 * lon);
        return (wind + 720) % 360;
    }

    private static double blend(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double start, double end, double value) {
        double t = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }
}
