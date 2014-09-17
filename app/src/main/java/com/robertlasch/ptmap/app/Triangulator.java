package com.robertlasch.ptmap.app;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Triangle;

import java.util.List;

/**
 * Created by Robert on 09.08.2014.
 */
public class Triangulator
{
    private final static int ROUNDCAPSEGMENTS = 10;

    public enum LineCap
    {
        Butt,
        Round,
        Square
    }

    public static Triangle[] lineToTriangles(Geometry line, LineCap cap, float lineWidth)
    {
        if (cap == LineCap.Square)
            throw new UnsupportedOperationException("This LineCap was not implemented yet!");

        if (line.getGeometryType().equals("MultiLineString"))
        {
            if (line.getNumGeometries() < 1) return new Triangle[] {};

            Triangle[] result = lineToTriangles(line.getGeometryN(0), cap, lineWidth);

            for (int i = 1; i < line.getNumGeometries(); i++)
                result = combine(result, lineToTriangles(line.getGeometryN(i), cap, lineWidth));

            return result;
        }
        else if (line.getGeometryType().equals("LineString"))
        {
            LineString ls = (LineString)line;

            Coordinate[] coordinates = ls.getCoordinates();

            int triangleCount = (coordinates.length - 1) * 2;
            if (cap == LineCap.Round)
                triangleCount += 2 * ROUNDCAPSEGMENTS; //Round cap on both sides
            int arrayIndex = -1;
            Triangle[] result = new Triangle[triangleCount];
            float widthHalf = lineWidth / 2;

            Coordinate lastS0 = null, lastS1 = null;
            double lastLength12 = Double.NaN, lastOffset12x = Double.NaN, lastOffset12y = Double.NaN;
            double lastSlope12 = Double.NaN;

            for(int k = 0; k < coordinates.length - 1; k++)
            {
                Coordinate p0 = coordinates[k];
                Coordinate p1 = coordinates[k + 1];
                Coordinate p2 = (k + 2) != coordinates.length ? coordinates[k + 2] : null;

                if (p2 != null)
                {
                    if (Double.isNaN(lastLength12) || Double.isNaN(lastOffset12x) || Double.isNaN(lastOffset12y))
                    {
                        lastLength12 = Math.sqrt(Math.pow(p1.x - p0.x, 2) + Math.pow(p1.y - p0.y, 2));
                        lastOffset12x =  (p1.y - p0.y) / lastLength12;
                        lastOffset12y = -(p1.x - p0.x) / lastLength12;
                    }

                    //Calculate length of the segments
                    double length12 = Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
                    //Calculate offsets for new linear funtions left and right of the line
                    double offset12x, offset12y;
                    offset12x =  (p2.y - p1.y) / length12;
                    offset12y = -(p2.x - p1.x) / length12;
                    //Calculate slopes
                    if (Double.isNaN(lastSlope12))
                    {
                        lastSlope12 = (p1.y - p0.y) / (p1.x - p0.x);
                    }
                    double slope12 = (p2.y - p1.y) / (p2.x - p1.x);
                    //Calculate points of the four equations
                    double pointEquation0x = p0.x + lastOffset12x * widthHalf;
                    double pointEquation0y = p0.y + lastOffset12y * widthHalf;
                    double pointEquation1x = p0.x + lastOffset12x * -widthHalf;
                    double pointEquation1y = p0.y + lastOffset12y * -widthHalf;
                    double pointEquation2x = p2.x + offset12x * widthHalf;
                    double pointEquation2y = p2.y + offset12y * widthHalf;
                    double pointEquation3x = p2.x + offset12x * -widthHalf;
                    double pointEquation3y = p2.y + offset12y * -widthHalf;
                    //Calculate offsets of the four equations
                    double a0 = pointEquation0y - lastSlope12 * pointEquation0x;  //Equation0: f:x = slope01 * x + a0
                    double a1 = pointEquation1y - lastSlope12 * pointEquation1x;  //Equation1: f:x = slope01 * x + a1
                    double a2 = pointEquation2y - slope12 * pointEquation2x;  //Equation2: f:x = slope12 * x + a2
                    double a3 = pointEquation3y - slope12 * pointEquation3x;  //Equation3: f:x = slope12 * x + a3

                    if (lastS0 == null || lastS1 == null)
                    {
                        lastS0 = new Coordinate(pointEquation0x, pointEquation0y);
                        lastS1 = new Coordinate(pointEquation1x, pointEquation1y);

                        if (cap == LineCap.Round)
                        {
                            Coordinate prevPoint = null;

                            double alpha = Math.atan(lastSlope12);
                            for (int i = 0; i <= ROUNDCAPSEGMENTS; i++)
                            {
                                double angle = alpha + Math.PI / 2 + (Math.PI * 2 / ROUNDCAPSEGMENTS) * i;
                                Coordinate point = new Coordinate(p0.x + Math.cos(angle) * widthHalf, p0.y + Math.sin(angle) * widthHalf);

                                if (prevPoint != null)
                                    result[arrayIndex += 1] = new Triangle(prevPoint, point, p0);

                                prevPoint = point;
                            }
                        }
                    }

                    double s0x = (a2 - a0) / (lastSlope12 - slope12);
                    double s1x = (a3 - a1) / (lastSlope12 - slope12);
                    double s0y = lastSlope12 * s0x + a0;
                    double s1y = lastSlope12 * s1x + a1;

                    Coordinate s0 = new Coordinate(s0x, s0y);
                    Coordinate s1 = new Coordinate(s1x, s1y);

                    result[arrayIndex += 1] = new Triangle(lastS0, lastS1, s0);
                    result[arrayIndex += 1] = new Triangle(lastS1, s1, s0);

                    lastLength12 = length12;
                    lastOffset12x = offset12x;
                    lastOffset12y = offset12y;
                    lastSlope12 = slope12;
                    lastS0 = s0;
                    lastS1 = s1;
                }
                else
                {
                    double length01 = Math.sqrt(Math.pow(p1.x - p0.x, 2) + Math.pow(p1.y - p0.y, 2));

                    double offset01x =  (p1.y - p0.y) / length01;
                    double offset01y = -(p1.x - p0.x) / length01;

                    double s0x = p1.x + offset01x *  widthHalf;
                    double s0y = p1.y + offset01y *  widthHalf;
                    double s1x = p1.x + offset01x * -widthHalf;
                    double s1y = p1.y + offset01y * -widthHalf;

                    double slope01 = (p1.y - p0.y) / (p1.x - p0.x);

                    if (lastS0 == null || lastS1 == null)
                    {
                        double pointEquation0x = p0.x + offset01x *  widthHalf;
                        double pointEquation0y = p0.y + offset01y *  widthHalf;
                        double pointEquation1x = p0.x + offset01x * -widthHalf;
                        double pointEquation1y = p0.y + offset01y * -widthHalf;

                        lastS0 = new Coordinate(pointEquation0x, pointEquation0y);
                        lastS1 = new Coordinate(pointEquation1x, pointEquation1y);

                        if (cap == LineCap.Round)
                        {
                            Coordinate prevPoint = null;

                            double alpha = Math.atan(slope01);
                            for (int i = 0; i <= ROUNDCAPSEGMENTS; i++)
                            {
                                double angle = alpha + Math.PI / 2 + (Math.PI * 2 / ROUNDCAPSEGMENTS) * i;
                                Coordinate point = new Coordinate(p0.x + Math.cos(angle) * widthHalf, p0.y + Math.sin(angle) * widthHalf);

                                if (prevPoint != null)
                                    result[arrayIndex += 1] = new Triangle(prevPoint, point, p0);

                                prevPoint = point;
                            }
                        }
                    }

                    Coordinate s0 = new Coordinate(s0x, s0y);
                    Coordinate s1 = new Coordinate(s1x, s1y);

                    result[arrayIndex += 1] = new Triangle(lastS0, lastS1, s0);
                    result[arrayIndex += 1] = new Triangle(lastS1, s1, s0);

                    if (cap == LineCap.Round)
                    {
                        Coordinate prevPoint = null;

                        double alpha = Math.atan(slope01);
                        for (int i = 0; i <= ROUNDCAPSEGMENTS; i++)
                        {
                            double angle = alpha + Math.PI / 2 + (Math.PI * 2 / ROUNDCAPSEGMENTS) * i;
                            Coordinate point = new Coordinate(p1.x + Math.cos(angle) * widthHalf, p1.y + Math.sin(angle) * widthHalf);

                            if (prevPoint != null)
                                result[arrayIndex += 1] = new Triangle(prevPoint, point, p1);

                            prevPoint = point;
                        }
                    }
                }
            }

            return result;
        }
        else
            return new Triangle[] {};
    }

    public static Triangle[] combine(Triangle[] a, Triangle[] b){
        int length = a.length + b.length;
        Triangle[] result = new Triangle[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
