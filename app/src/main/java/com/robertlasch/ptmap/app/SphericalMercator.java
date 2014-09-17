package com.robertlasch.ptmap.app;

/**
 * Created by Robert on 29.05.2014.
 */
public class SphericalMercator
{
    final private static double R_MAJOR = 6378137.0;

    public static double lonToX(double lon)
    {
        return R_MAJOR * Math.toRadians(lon);
    }

    public static double latToY(double lat) { return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2.0)) * R_MAJOR; }

    public static double xToLon(double x)
    {
        return Math.toDegrees(x / R_MAJOR);
    }

    public static double yToLat(double y) { return Math.toDegrees(2.0 * Math.atan(Math.exp(y / R_MAJOR)) - Math.PI / 2); }
}