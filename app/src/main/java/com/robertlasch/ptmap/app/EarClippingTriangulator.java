package com.robertlasch.ptmap.app;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Triangle;
import com.vividsolutions.jts.operation.valid.IsValidOp;
import com.vividsolutions.jts.operation.valid.TopologyValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

public class EarClippingTriangulator
{
    private enum LineStringOrder
    {
        Clockwise,
        CounterClockwise,
        Unknown
    }

    private LinkedList<Coordinate> exteriorCoords;
    private ArrayList<Coordinate> convexVertices;
    private ArrayList<Coordinate> reflexVertices;
    private ArrayList<Coordinate> ears;
    private LineStringOrder exteriorOrder;

    public Triangle[] triangulate(Geometry geometry, boolean holes)
    {
        //Cast to polygon

        Polygon poly = null;
        if (geometry.getGeometryType().equals("Polygon"))
        {
            poly = (Polygon)geometry;
        }


        //Find out order of exterior ring
        exteriorOrder = determineLineStringOrder(poly.getExteriorRing());
        if (exteriorOrder == LineStringOrder.Unknown)
            return new Triangle[] {};

        //Put exterior ring in linked list
        LineString exteriorRing = poly.getExteriorRing();
        exteriorCoords = new LinkedList<Coordinate>();
        Collections.addAll(exteriorCoords, exteriorRing.getCoordinates());

        if (exteriorCoords.getFirst().compareTo(exteriorCoords.getLast()) == 0)
            exteriorCoords.removeLast();

        if (exteriorOrder == LineStringOrder.Clockwise) {
            exteriorOrder = LineStringOrder.CounterClockwise;
            exteriorCoords = new LinkedList<Coordinate>(Lists.reverse(exteriorCoords));
        }

        if (holes) {
            //Create list of interior rings and order them countered to the exterior ring
            int i, n;
            n = poly.getNumInteriorRing();
            ArrayList<LineString> interiorRings = new ArrayList<LineString>(n);
            for (i = 0; i < n; i++) {
                LineString ls = poly.getInteriorRingN(i);
                LineStringOrder lso = determineLineStringOrder(ls);
                if (lso == exteriorOrder) {
                    Coordinate[] reversed = new Coordinate[ls.getCoordinates().length];

                    for (int z = 0; z < ls.getCoordinates().length; z++) {
                        reversed[z] = ls.getCoordinates()[ls.getCoordinates().length - z - 1];
                    }

                    ls = new GeometryFactory().createLineString(reversed);
                }

                interiorRings.add(ls);
            }

            findConvexReflex();

            //Merge interior rings
            while (interiorRings.size() > 0) {
                //Find rightmost interior ring
                LineString rightMost = null;
                for (LineString ls : interiorRings)
                    if (rightMost == null || ls.getEnvelopeInternal().getMaxX() > rightMost.getEnvelopeInternal().getMaxX())
                        rightMost = ls;

                //Find its rightmost coordinate
                LinkedList<Coordinate> rightMostCoords = new LinkedList<Coordinate>();
                Coordinate rightMostCoord = null;
                rightMostCoords.add(rightMost.getCoordinateN(0));
                for (Coordinate co : rightMost.getCoordinates()) {
                    if (rightMost.getCoordinates()[0].compareTo(co) != 0)
                        rightMostCoords.add(co);
                    if (rightMostCoord == null || co.x > rightMostCoord.x)
                        rightMostCoord = co;
                }

                //Get mutually visible coordinate of the exterior ring
                Coordinate mVisible;
                mVisible = getMutuallyVisible(rightMostCoord);

                //Merge with the exterior ring
                //Duplicate the mutually visible coordinate of the exterior ring
                int mVisibleIndex = exteriorCoords.indexOf(mVisible);
                exteriorCoords.add(mVisibleIndex, new Coordinate(mVisible.x, mVisible.y));
                int rightMostIndex = rightMostCoords.indexOf(rightMostCoord);
                int offset = 1;
                for (int j = rightMostIndex; j < rightMostCoords.size(); j++) {
                    exteriorCoords.add(mVisibleIndex + offset, rightMostCoords.get(j));
                    offset++;
                }
                for (int j = 0; j <= rightMostIndex; j++) {
                    if (j == rightMostIndex)
                        exteriorCoords.add(mVisibleIndex + offset, new Coordinate(rightMostCoords.get(j).x, rightMostCoords.get(j).y));
                    else
                        exteriorCoords.add(mVisibleIndex + offset, rightMostCoords.get(j));
                    offset++;
                }

                //Remove interior ring from collection
                interiorRings.remove(rightMost);

                findConvexReflex();
            }
        }

        findConvexReflex();

        ArrayList<Triangle> result = new ArrayList<Triangle>(exteriorCoords.size() - 2);
        ears = new ArrayList<Coordinate>(exteriorCoords.size());

        addEars();

        boolean error = false;

        while (exteriorCoords.size() > 3)
        {
            if (ears.size() == 0)
            {
                error = true;
                System.out.println("Triangulation Error");
                break;
            }

            Coordinate current = ears.get(0);

            int index = exteriorCoords.indexOf(current);
            int prevIndex = (index - 1) % exteriorCoords.size();
            if (prevIndex < 0) prevIndex += exteriorCoords.size();
            int nextIndex = (index + 1) % exteriorCoords.size();
            Coordinate prev = exteriorCoords.get(prevIndex);
            Coordinate next = exteriorCoords.get(nextIndex);

            ears.remove(current);
            convexVertices.remove(current);
            exteriorCoords.remove(current);
            result.add(new Triangle(prev, current, next));

            checkChanges(index != 0 ? prevIndex : exteriorCoords.size() - 1);
            checkChanges(exteriorCoords.indexOf(next));
        }

        //add last
        result.add(new Triangle(exteriorCoords.get(0), exteriorCoords.get(1), exteriorCoords.get(2)));

        Triangle[] arr = new Triangle[result.size()];
        for (int i = 0; i < result.size(); i++)
            arr[i] = result.get(i);

        return arr; //new Triangle[] { new Triangle(new Coordinate(xWest, yNorth, 0.0), new Coordinate(xEast, yNorth, 0.0), new Coordinate(xWest, ySouth, 0.0)) };
    }

    private void findConvexReflex()
    {
        convexVertices = new ArrayList<Coordinate>(exteriorCoords.size());
        reflexVertices = new ArrayList<Coordinate>(exteriorCoords.size());

        //Find convex/reflex vertices
        for (int i = 0; i < exteriorCoords.size(); i++)
        {
            Coordinate c = exteriorCoords.get(i);

            if (isConvex(i))
                convexVertices.add(c);
            else
                reflexVertices.add(c);
        }
    }

    private void addEars()
    {
        for (Coordinate current : convexVertices)
        {
            int index = exteriorCoords.indexOf(current);
            int prevIndex = (index - 1) % exteriorCoords.size();
            if (prevIndex < 0) prevIndex += exteriorCoords.size();

            Coordinate prev = exteriorCoords.get(prevIndex);
            Coordinate next = exteriorCoords.get((index + 1) % exteriorCoords.size());

            boolean pointInside = false;

            for(Coordinate c : reflexVertices)
            {
                pointInside = pointInTriangle(c, prev, current, next);
                if (pointInside)
                    break;
            }

            if (!pointInside)
                ears.add(current);
        }
    }

    private void checkChanges(int index)
    {
        Coordinate c = exteriorCoords.get(index);
        int prevIndex = (index - 1) % exteriorCoords.size();
        if (prevIndex < 0) prevIndex += exteriorCoords.size();

        Coordinate prev = exteriorCoords.get(prevIndex);
        Coordinate next = exteriorCoords.get((index + 1) % exteriorCoords.size());

        if (convexVertices.contains(c))
        {
            boolean pointInside = false;

            for(Coordinate co : reflexVertices)
            {
                pointInside = pointInTriangle(co, prev, c, next);
                if (pointInside)
                    break;
            }

            if (!pointInside && !ears.contains(c))
                ears.add(c);
            else if (pointInside && ears.contains(c))
                ears.remove(c);
        }
        else
        {
            if (isConvex(index))
            {
                reflexVertices.remove(c);
                convexVertices.add(c);

                boolean pointInside = false;

                for(Coordinate co : reflexVertices)
                {
                    pointInside = pointInTriangle(co, prev, c, next);
                    if (pointInside)
                        break;
                }

                if (!pointInside)
                    ears.add(c);
            }
        }
    }

    private boolean isConvex(int index)
    {
        Coordinate c = exteriorCoords.get(index);
        int prevIndex = (index - 1) % exteriorCoords.size();
        if (prevIndex < 0) prevIndex += exteriorCoords.size();

        Coordinate prev = exteriorCoords.get(prevIndex);
        Coordinate next = exteriorCoords.get((index + 1) % exteriorCoords.size());

        double t = 0;

        t = (c.x - prev.x) * (next.y - c.y) - (c.y - prev.y) * (next.x - c.x);

        if (exteriorOrder == LineStringOrder.CounterClockwise)
        {
            return t >= 0;
        }
        else
        {
            return t < 0;
        }
    }

    private boolean pointInTriangle(Coordinate pt, Coordinate v1, Coordinate v2, Coordinate v3)
    {
        double v13x = v1.x - v3.x;
        double v13y = v1.y - v3.y;
        double v12x = v1.x - v2.x;

        double dot00 = v13x * v13x + v13y * v13y;
        double dot01 = v13x * v12x + v13y * (v1.y - v2.y);
        double dot02 = v13x * (v1.x - pt.x) + v13y * (v1.y - pt.y);
        double dot11 = v12x * v12x + (v1.y - v2.y) * (v1.y - v2.y);
        double dot12 = v12x * (v1.x - pt.x) + (v1.y - v2.y) * (v1.y - pt.y);

        double denominator = dot00 * dot11 - dot01 * dot01;
        double u = (dot11 * dot02 - dot01 * dot12) / denominator;
        double v = (dot00 * dot12 - dot01 * dot02) / denominator;

        return u > 0 && v > 0 && (u + v <= 1);
    }

    private Coordinate getMutuallyVisible(Coordinate m)
    {
        LineSegment ray = new LineSegment(m, new Coordinate(Double.MAX_VALUE, m.y));
        Coordinate leftmostIntersection = null;
        LineSegment leftmost = null;
        for (int i = 0; i < exteriorCoords.size(); i++)
        {
            int j = (i + 1) % exteriorCoords.size();
            LineSegment ls = new LineSegment(exteriorCoords.get(i), exteriorCoords.get(j));
            Coordinate intersection = ls.intersection(ray);

            if (intersection != null && (leftmostIntersection == null || leftmostIntersection.x > intersection.x))
            {
                leftmostIntersection = intersection;
                leftmost = ls;
            }
        }

        Coordinate p = leftmost.p0.x > leftmost.p1.x ? leftmost.p0 : leftmost.p1;

        LinkedList<Coordinate> reflexesInside = new LinkedList<Coordinate>();

        for (Coordinate coord : reflexVertices)
        {
            if (coord.compareTo(p) != 0 && pointInTriangle(coord, m, leftmostIntersection, p))
                reflexesInside.add(coord);
        }

        if (reflexesInside.size() == 0)
            return p;
        else
        {
            double nearestTan = Double.MAX_VALUE;
            Coordinate res = null;

            for (Coordinate coord : reflexesInside)
            {
                double tan = Math.abs(coord.y - m.y) / (m.x - coord.x);

                if (tan < nearestTan)
                {
                    nearestTan = tan;
                    res = coord;
                }
                else if (tan == nearestTan)
                {
                    if ((Math.pow(coord.x - m.x,2) + Math.pow(coord.y - m.y,2)) < (Math.pow(res.x - m.x,2) + Math.pow(res.y - m.y,2)))
                    {
                        res = coord;
                    }
                }
            }

            return res;
        }
    }

    private LineStringOrder determineLineStringOrder(LineString ls)
    {
        int i, j;
        int n = ls.getCoordinates().length;

        if(n < 3)
            return LineStringOrder.Unknown;

        //If first and last point are equal remove last point
        if (ls.getCoordinateN(0).compareTo(ls.getCoordinateN(n - 1)) == 0)
            n--;

        //If shape is invalid return
        if (!ls.isClosed())
            return LineStringOrder.Unknown;

        double area = 0;
        j = n - 1;

        for (i = 0; i < n; i++)
        {
            area += (ls.getCoordinateN(j).x + ls.getCoordinateN(i).x) * (ls.getCoordinateN(j).y - ls.getCoordinateN(i).y);
            j = i;
        }

        area /= 2;

        if (area < 0)
            return LineStringOrder.CounterClockwise;
        else if (area > 0)
            return LineStringOrder.Clockwise;
        else
            return LineStringOrder.Unknown;
    }
}
