package com.robertlasch.ptmap.app;

import android.graphics.RectF;
import android.opengl.GLES20;
import android.os.Debug;
import android.util.Log;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Triangle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VectorTileRendered
{
    enum TileType
    {
        RoadLines(0),
        RoadLabels(1),
        Buildings(2),
        PointsOfInterest(3),
        LandUsages(4),
        WaterAreas(5),
        Default(6);

        public int id;
        private TileType(int id)
        {
            this.id = id;
        }
    }

    private int zoomLevel = 1; //Default root tile
    private int x, y; //The coordinates of the tile itself

    private TileType type;
    private VectorTileRenderer renderer;
    private VectorTileRendered parent;
    private VectorTileRendered northWestChild;
    private VectorTileRendered northEastChild;
    private VectorTileRendered southWestChild;
    private VectorTileRendered southEastChild;
    private boolean isSplit = false;
    private boolean isSplitting = false;

    private double xWest, xEast, yNorth, ySouth;

    private FloatBuffer vertexBuffer;
    private ArrayList<Float> triangleCoords = new ArrayList<Float>();
    private ByteBuffer colorBuffer;
    private ArrayList<Byte> colorValues = new ArrayList<Byte>();
    private EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private int triangleCoordsSize = 0;
    private int colorValuesSize = 0;


    public VectorTileRendered(HashMap<Geometry, HashMap<String, String>> data,
                              VectorTileRenderer renderer,
                              VectorTileRendered parent,
                              boolean northChild, boolean westChild)
    {
        this(data, parent.getType(), renderer, parent, northChild, westChild);
    }

    public VectorTileRendered(HashMap<Geometry, HashMap<String, String>> data,
                              TileType type,
                              VectorTileRenderer renderer,
                              VectorTileRendered parent,
                              boolean northChild, boolean westChild)
    {
        this.type = type;
        this.renderer = renderer;
        this.parent = parent;

        if (parent == null)
        {
            this.xWest = renderer.getXWest();
            this.xEast = renderer.getXEast();
            this.yNorth = renderer.getYNorth();
            this.ySouth = renderer.getYSouth();
            this.zoomLevel = 1;
            this.x = 0;
            this.y = 0;
        }
        else
        {
            this.zoomLevel = parent.getZoomLevel() + 1;

            if (northChild)
            {
                if (westChild)
                {
                    this.x = parent.getX() * 2;
                    this.y = parent.getY() * 2;

                    this.xWest = parent.xWest;
                    this.yNorth = parent.yNorth;
                }
                else
                {
                    this.x = parent.getX() * 2 + 1;
                    this.y = parent.getY() * 2;

                    this.xWest = parent.xWest + renderer.getTileSizeX(this.zoomLevel);
                    this.yNorth = parent.yNorth;
                }
            }
            else
            {
                if (westChild)
                {
                    this.x = parent.getX() * 2;
                    this.y = parent.getY() * 2 + 1;

                    this.xWest = parent.xWest;
                    this.yNorth = parent.yNorth + renderer.getTileSizeY(this.zoomLevel);
                }
                else
                {
                    this.x = parent.getX() * 2 + 1;
                    this.y = parent.getY() * 2 + 1;

                    this.xWest = parent.xWest + renderer.getTileSizeX(this.zoomLevel);
                    this.yNorth = parent.yNorth + renderer.getTileSizeY(this.zoomLevel);
                }
            }

            this.xEast = this.xWest + renderer.getTileSizeX(this.zoomLevel);
            this.ySouth = this.yNorth + renderer.getTileSizeY(this.zoomLevel);
        }

        if (data != null)
        {
            if (type == TileType.WaterAreas)
            {
                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    float[] polyColor = new float[4];

                    polyColor[0] = 0.70196078431f;
                    polyColor[1] = 0.81960784313f;
                    polyColor[2] = 1.0f;
                    polyColor[3] = 1.0f;

                    Geometry geom = entry.getKey();

                    if (geom.getGeometryType().equals("Polygon"))
                    {
                        addPolygon((Polygon) geom, polyColor, -15000f);
                    }
                    else if (geom.getGeometryType().equals("MultiPolygon"))
                    {
                        MultiPolygon mPoly = (MultiPolygon)geom;

                        for (int i = 0; i < mPoly.getNumGeometries(); i++)
                        {
                            addPolygon((Polygon) mPoly.getGeometryN(i), polyColor, -15000f);
                        }
                    }
                }
            }
            else if (type == TileType.LandUsages)
            {
                HashMap<String, int[]> colors = new HashMap<String, int[]>();
                colors.put("residential", new int[] { 233, 229, 220 }); //Wohngebiet
                colors.put("pedestrian", new int[] { 233, 229, 220 }); //Fusgängerzone
                colors.put("playground", new int[] { 233, 229, 220 }); //Spielplatz
                colors.put("urban area", new int[] { 233, 229, 220 }); //Großstadt
                colors.put("farmland", new int[] { 240, 237, 229 });
                colors.put("forest", new int[] { 210, 228, 200 });
                colors.put("scrub", new int[] { 210, 228, 200 });
                colors.put("grass", new int[] { 210, 228, 200 });
                colors.put("meadow", new int[] { 210, 228, 200 });
                colors.put("allotments", new int[] { 210, 228, 200 }); //Kleingärten
                colors.put("garden", new int[] { 210, 228, 200 });
                colors.put("wood", new int[] { 210, 228, 200 }); //Wald
                colors.put("miniature_golf", new int[] { 210, 228, 200 });
                colors.put("commercial", new int[] { 223, 219, 212 });
                colors.put("industrial", new int[] { 223, 219, 212 });
                colors.put("railway", new int[] { 182, 182, 182 });
                colors.put("park", new int[] { 168, 226, 133 });
                colors.put("parking", new int[] { 237, 227, 208 });
                colors.put("cemetery", new int[] { 237, 227, 208 });
                colors.put("golf_course", new int[] { 200, 220, 200 });
                colors.put("cinema", new int[] { 235, 210, 207 });
                colors.put("school", new int[] { 235, 210, 207 });
                colors.put("university", new int[] { 235, 210, 207 });
                colors.put("hospital", new int[] { 235, 210, 207 });
                colors.put("pitch", new int[] { 237, 227, 208 });
                colors.put("farmyard", new int[] { 237, 227, 208 });
                colors.put("farm", new int[] { 237, 227, 208 });
                colors.put("stadium", new int[] { 234, 157, 157 });
                colors.put("place_of_worship", new int[] { 244, 243, 236 });
                colors.put("quarry", new int[] { 233, 219, 212 }); //Steinbruch
                colors.put("sports_centre", new int[] { 237, 227, 208 });
                colors.put("landuse", new int[] { 238, 228, 207 });
                colors.put("village_green", new int[] { 238, 228, 207 });
                colors.put("leisure", new int[] { 221, 230, 213 });
                colors.put("recreation_ground", new int[] { 221, 230, 213 });
                colors.put("nature_reserve", new int[] { 221, 230, 213 });
                colors.put("retail", new int[] { 230, 230, 230 });
                colors.put("common", new int[] { 240, 237, 229 });

                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    String kind = "unknown";
                    if (entry.getValue().containsKey("kind"))
                        kind = entry.getValue().get("kind");

                    float[] polyColor;

                    if (colors.containsKey(kind))
                    {
                        polyColor = new float[4];
                        polyColor[0] = (float)colors.get(kind)[0] / 255f;
                        polyColor[1] = (float)colors.get(kind)[1] / 255f;
                        polyColor[2] = (float)colors.get(kind)[2] / 255f;
                        polyColor[3] = 1.0f;
                    }
                    else
                    {
                        polyColor = new float[4];
                        polyColor[0] = 0.0f;
                        polyColor[1] = 1.0f;
                        polyColor[2] = 0.0f;
                        polyColor[3] = 1.0f;

                        Log.e("Unknown LandUsage", kind);
                    }

                    if (entry.getKey().getGeometryType().equals("Polygon"))
                    {
                        addPolygon((Polygon)entry.getKey(), polyColor, -19999f);
                    }
                    else if (entry.getKey().getGeometryType().equals("MultiPolygon"))
                    {
                        MultiPolygon mPoly = (MultiPolygon) entry.getKey();

                        for (int i = 0; i < mPoly.getNumGeometries(); i++)
                        {
                            addPolygon((Polygon)mPoly.getGeometryN(i), polyColor, -19999f);
                        }
                    }
                }
            }
            else if (type == TileType.RoadLines)
            {
                float[] polyColor = new float[4];

                //Get scaling and offset for the layer
                float layerOffset;
                float layerScaling;
                float highestValue = Float.NaN;
                float lowestValue = Float.NaN;

                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    HashMap<String, String> props = entry.getValue();
                    float layer = highwayLayer(props.get("highway"), props.get("sort_key"), props.get("is_bridge"), props.get("is_tunnel"));

                    if (Float.isNaN(highestValue) || layer > highestValue)
                        highestValue = layer;
                    if (Float.isNaN(lowestValue) || layer < lowestValue)
                        lowestValue = layer;
                }

                if (Float.isNaN(highestValue))
                    highestValue = 0f;

                layerOffset = -highestValue;
                lowestValue += layerOffset;

                if (lowestValue < -10000f)
                    layerScaling = -10000f / lowestValue;
                else
                    layerScaling = 1f;

                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    HashMap<String, String> props = entry.getValue();

                    float pixel = renderer.getPixelSize(zoomLevel);//(2f * (float)Math.PI / (float)Math.pow(2, zoomLevel + 8)) * 6378137.0f; //(float)renderer.getTileSizeX(zoomLevel) / 1000f; //2f * (float)Math.PI / (1 << (zoomLevel + 8));
                    float[] widths = highwayWidths(props.get("highway"), props.get("kind"), zoomLevel, pixel);
                    float inner = widths[0];
                    float outer = widths[1];

                    //Get layer and apply offset and scaling
                    float layer = highwayLayer(props.get("highway"), props.get("sort_key"), props.get("is_bridge"), props.get("is_tunnel"));
                    layer += layerOffset;
                    layer *= layerScaling;

                    String highway = props.get("highway");

                    if (props.get("kind").equals("rail"))
                        highway = "rail";

                    polyColor[0] = highwayColors.containsKey(highway) ? (float)highwayColors.get(highway)[0] / 255f : 1f;
                    polyColor[1] = highwayColors.containsKey(highway) ? (float)highwayColors.get(highway)[1] / 255f : 0f;
                    polyColor[2] = highwayColors.containsKey(highway) ? (float)highwayColors.get(highway)[2] / 255f : 1f;
                    polyColor[3] = (props.get("is_tunnel").equals("yes")) ? 0.4f : 1.0f;

                    if (!highwayColors.containsKey(highway))
                        Log.e("Unknown Highway", highway);

                    addTriangles(Triangulator.lineToTriangles(entry.getKey(), Triangulator.LineCap.Round, inner), polyColor[0], polyColor[1], polyColor[2], polyColor[3], layer);
                    //if (props.get("is_bridge").equals("yes") && zoomLevel >= 15)
                    addTriangles(Triangulator.lineToTriangles(entry.getKey(), Triangulator.LineCap.Butt, outer), polyColor[0] * 0.9f, polyColor[1] * 0.9f, polyColor[2] * 0.9f, polyColor[3], layer - 10);
                }
            }
            else if (type == TileType.Buildings)
            {
                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    float[] polyColor;

                    polyColor = new float[4];
                    polyColor[0] = 200 / 255f;
                    polyColor[1] = 154 / 255f;
                    polyColor[2] = 149 / 255f;
                    polyColor[3] = 1.0f;

                    if (entry.getKey().getGeometryType().equals("Polygon"))
                    {
                        addPolygon((Polygon)entry.getKey(), polyColor, -12999f);
                    }
                    else if (entry.getKey().getGeometryType().equals("MultiPolygon"))
                    {
                        MultiPolygon mPoly = (MultiPolygon) entry.getKey();

                        for (int i = 0; i < mPoly.getNumGeometries(); i++)
                        {
                            addPolygon((Polygon)mPoly.getGeometryN(i), polyColor, -12999f);
                        }
                    }
                }
            }
            else if (type == TileType.RoadLabels)
            {
                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    /*Log.w("RoadLabels", "############### ENTRY ###############");
                    for (Map.Entry<String, String> e : entry.getValue().entrySet())
                    {
                        Log.w("RoadLabels", e.getKey() + ": " + e.getValue());
                    }
                    Log.w("RoadLabels", "Geometry Type: " + entry.getKey().getGeometryType());
                    Log.w("RoadLabels", "###############  END  ###############");

                    addTriangles(Triangulator.lineToTriangles(entry.getKey(), Triangulator.LineCap.Butt, highwayWidths(entry.getValue().get("highway"), "", zoomLevel, renderer.getPixelSize(zoomLevel))[0]), 0f, 0f, 0f, 1f, 0f);*/
                }
            }
            else if (type == TileType.PointsOfInterest)
            {
                for (Map.Entry<Geometry, HashMap<String, String>> entry : data.entrySet())
                {
                    /*Log.w("RoadLabels", "############### ENTRY ###############");
                    for (Map.Entry<String, String> e : entry.getValue().entrySet())
                    {
                        Log.w("RoadLabels", e.getKey() + ": " + e.getValue());
                    }
                    Log.w("RoadLabels", "Geometry Type: " + entry.getKey().getGeometryType());
                    Log.w("RoadLabels", "###############  END  ###############");*/

                    Coordinate prevPoint = null;
                    Triangle[] dot = new Triangle[10];
                    int arrayIndex = -1;
                    for (int i = 0; i <= 10; i++)
                    {
                        double angle = (Math.PI * 2 / 10) * i;
                        float radius = renderer.getPixelSize(zoomLevel) * 4;
                        Coordinate point = new Coordinate(entry.getKey().getCoordinate().x + Math.cos(angle) * radius, entry.getKey().getCoordinate().y + Math.sin(angle) * radius);

                        if (prevPoint != null)
                            dot[arrayIndex += 1] = new Triangle(prevPoint, point, entry.getKey().getCoordinate());

                        prevPoint = point;
                    }
                    addTriangles(dot, 1f, 1f, 1f, 1f, 0f);

                    prevPoint = null;
                    arrayIndex = -1;
                    for (int i = 0; i <= 10; i++)
                    {
                        double angle = (Math.PI * 2 / 10) * i;
                        float radius = renderer.getPixelSize(zoomLevel) * 5.5f;
                        Coordinate point = new Coordinate(entry.getKey().getCoordinate().x + Math.cos(angle) * radius, entry.getKey().getCoordinate().y + Math.sin(angle) * radius);

                        if (prevPoint != null)
                            dot[arrayIndex += 1] = new Triangle(prevPoint, point, entry.getKey().getCoordinate());

                        prevPoint = point;
                    }
                    addTriangles(dot, 0.75f, 0.75f, 0.75f, 1f, 0f);
                }
            }
        }

        buildBuffers();

        //If zoom to small => split
        if (renderer.getZoomLevel() > this.getZoomLevel())
            renderer.splitTile(this);
    }

    private final static HashMap<String, int[]> highwayColors = new HashMap<String, int[]>();
    private final static HashMap<String, Double> highwayPriorities = new HashMap<String, Double>();
    private final static HashMap<String, Double> highwayCoefficients = new HashMap<String, Double>();
    static
    {
        highwayColors.put("motorway", new int[] { 250, 158, 37 });
        highwayColors.put("motorway_link", new int[] { 255, 225, 153 });
        highwayColors.put("trunk", new int[] { 250, 225, 104 });
        highwayColors.put("trunk_link", new int[] { 255, 225, 153 });
        highwayColors.put("primary", new int[] { 250, 225, 104 });
        highwayColors.put("primary_link", new int[] { 255, 255, 255 });
        highwayColors.put("secondary_link", new int[] { 255, 255, 255 });
        highwayColors.put("tertiary_link", new int[] { 255, 255, 255 });
        highwayColors.put("track", new int[] { 255, 255, 255 });
        highwayColors.put("residential", new int[] { 248, 246, 242 });
        highwayColors.put("footway", new int[] { 225, 220, 214 });
        highwayColors.put("steps", new int[] { 225, 220, 214 });
        highwayColors.put("pedestrian", new int[] { 225, 220, 214 });
        highwayColors.put("cycleway", new int[] { 225, 220, 214 });
        highwayColors.put("path", new int[] { 225, 220, 214 });
        highwayColors.put("secondary", new int[] { 255, 255, 255 });
        highwayColors.put("tertiary", new int[] { 255, 255, 255 });
        highwayColors.put("rail", new int[] { 192, 192, 192 });
        highwayColors.put("unclassified", new int[] { 248, 246, 242 });
        highwayColors.put("road", new int[] { 255, 255, 255 });
        highwayColors.put("service", new int[] { 226, 221, 214 });

        highwayCoefficients.put("motorway", 0.6);
        highwayCoefficients.put("trunk", 0.6);
        highwayCoefficients.put("primary", 0.6);
        highwayCoefficients.put("secondary", 0.6);
        highwayCoefficients.put("tertiary", 0.6);
        highwayCoefficients.put("motorway_link", 0.7);
        highwayCoefficients.put("trunk_link", 0.7);
        highwayCoefficients.put("primary_link", 0.7);
        highwayCoefficients.put("secondary_link", 0.7);
        highwayCoefficients.put("tertiary_link", 0.7);

        highwayPriorities.put("motorway", 0.0);
        highwayPriorities.put("trunk", 1.0);
        highwayPriorities.put("primary", 2.0);
        highwayPriorities.put("secondary", 3.0);
        highwayPriorities.put("tertiary", 4.0);
        highwayPriorities.put("motorway_link", 5.0);
        highwayPriorities.put("trunk_link", 5.0);
        highwayPriorities.put("primary_link", 5.0);
        highwayPriorities.put("secondary_link", 5.0);
        highwayPriorities.put("tertiary_link", 5.0);
        highwayPriorities.put("residential", 6.0);
        highwayPriorities.put("unclassified", 6.0);
        highwayPriorities.put("road", 6.0);
        highwayPriorities.put("service", 7.0);
        highwayPriorities.put("minor", 7.0);
    };

    private static Coordinate lerp(Coordinate pA, Coordinate pB, double amt)
    {
        return new Coordinate(pA.x * (1f - amt) + pB.x * amt, pA.y * (1f - amt) + pB.y * amt);
    }

    private static float[] highwayWidths(String highway, String kind, int zoomLevel, float pixel)
    {
        double coeff = highwayCoefficients.containsKey(highway) ? highwayCoefficients.get(highway) : 0.8;
        coeff = kind.equals("path") ? 0.9 : coeff;
        double scale = Math.pow(2, coeff * (zoomLevel - 18)) * 10.0;

        double inner;

        if (highway.equals("motorway"))
        {
            inner = 20;
        }
        else if (highway.equals("trunk"))
        {
            inner = 20;
        }
        else if (highway.equals("primary"))
        {
            inner = 16;
        }
        else if (highway.equals("tertiary"))
        {
            inner = 10;
        }
        else if (highway.equals("residential") || highway.equals("unclassified"))
        {
            inner = 4;
        }
        else if (highway.equals("track"))
        {
            inner = 6;
        }
        else if (kind.equals("path") || kind.equals("rail") || highway.equals("service") || highway.equals("pedestrian"))
        {
            inner = 3;
        }
        else
        {
            inner = 6;
        }

        return new float[] { (float)(inner * pixel), (float)((inner + 3) * pixel) };
        //return new float[] { (float)(inner * pixel * scale), (float)((inner + 4) * pixel * scale) };
    }

    private static float highwayLayer(String highway, String explicit_layer, String is_bridge, String is_tunnel)
    {
        float sort_key = (explicit_layer == null || explicit_layer.isEmpty()) ? 0 : Float.parseFloat(explicit_layer);

        // explicit layering mostly wins
        float layer = sort_key * 1000f;

        // implicit layering less important.
        if(is_bridge.equals("yes"))
        {
            layer += 100f;
        }

        if(is_tunnel.equals("yes"))
        {
            layer -= 100f;
        }

        // leave the +/-10 order of magnitude open for bridge casings.

        // adjust slightly based on priority derived from highway type
        layer -= (highwayPriorities.containsKey(highway)) ? highwayPriorities.get(highway) : 9f;

        //if (layer < -20000f || layer > 150f)
        //    Log.e("HighwayLayer", layer + "," + highway + "," + explicit_layer + "," + is_bridge + "," + is_tunnel);

        return layer;
    }

    private void addPolygon(Polygon poly, float[] polyColor, float z)
    {
        addTriangles(triangulator.triangulate(poly, false), polyColor[0], polyColor[1], polyColor[2], polyColor[3], z);

        GeometryFactory factory = new GeometryFactory();
        for(int i = 0; i < poly.getNumInteriorRing(); i++)
        {
            LineString ls = poly.getInteriorRingN(i);
            addTriangles(triangulator.triangulate(factory.createPolygon(ls.getCoordinates()), false), VectorTileRenderer.BackgroundR, VectorTileRenderer.BackgroundG, VectorTileRenderer.BackgroundB, 1f, z + 9f);
        }
    }

    private void addTriangles(Triangle[] triangles, float colorR, float colorG, float colorB, float colorA, float depth)
    {
        for(Triangle t : triangles)
        {
            triangleCoords.add((float)t.p0.x); triangleCoords.add((float)t.p0.y); triangleCoords.add(depth);
            triangleCoords.add((float)t.p1.x); triangleCoords.add((float)t.p1.y); triangleCoords.add(depth);
            triangleCoords.add((float)t.p2.x); triangleCoords.add((float)t.p2.y); triangleCoords.add(depth);

            colorValues.add((byte)(colorR * 255)); colorValues.add((byte)(colorG * 255)); colorValues.add((byte)(colorB * 255)); colorValues.add((byte)(colorA * 255));
            colorValues.add((byte)(colorR * 255)); colorValues.add((byte)(colorG * 255)); colorValues.add((byte)(colorB * 255)); colorValues.add((byte)(colorA * 255));
            colorValues.add((byte)(colorR * 255)); colorValues.add((byte)(colorG * 255)); colorValues.add((byte)(colorB * 255)); colorValues.add((byte)(colorA * 255));
        }
    }

    private int[] buffers = new int[2];

    private void buildBuffers()
    {
        ByteBuffer bbv = ByteBuffer.allocateDirect(triangleCoords.size() * 4);
        bbv.order(ByteOrder.nativeOrder());
        vertexBuffer = bbv.asFloatBuffer();
        float[] triangleArray = new float[triangleCoords.size()];
        int i = 0;
        for (Float f : triangleCoords) { triangleArray[i++] = (f != null ? f : Float.NaN); }
        vertexBuffer.put(triangleArray);
        vertexBuffer.position(0);

        colorBuffer = ByteBuffer.allocateDirect(colorValues.size());
        colorBuffer.order(ByteOrder.nativeOrder());
        byte[] colorArray = new byte[colorValues.size()];
        i = 0;
        for (Byte b : colorValues) { colorArray[i++] = (b != null ? b : Byte.MIN_VALUE); }
        colorBuffer.put(colorArray);
        colorBuffer.position(0);

        renderer.getSurfaceView().queueEvent(new Runnable()
        {
            @Override
            public void run()
            {
                GLES20.glGenBuffers(2, buffers, 0);

                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, colorBuffer.capacity(), colorBuffer, GLES20.GL_STATIC_DRAW);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            }
        });

        triangleCoordsSize = triangleCoords.size();
        colorValuesSize = colorValues.size();
        triangleCoords = null;
        colorValues = null;
    }

    public void split(VectorTileRendered northWestChild,
                      VectorTileRendered northEastChild,
                      VectorTileRendered southWestChild,
                      VectorTileRendered southEastChild)
    {
        this.northWestChild = northWestChild;
        this.northEastChild = northEastChild;
        this.southWestChild = southWestChild;
        this.southEastChild = southEastChild;
        isSplit = true;

        //If zoom to small => split
        if (renderer.getZoomLevel() > this.getZoomLevel())
            renderer.splitTile(this);
    }

    public int getZoomLevel()
    {
        return zoomLevel;
    }

    public void render(ShaderProgram shaderProgram, RectF renderBounds)
    {
        if (RectF.intersects(getBounds(), renderBounds) || renderBounds.contains(getBounds()))
        {
            if (isSplit && renderer.getZoomLevel() > zoomLevel)
            {
                northWestChild.render(shaderProgram, renderBounds);
                northEastChild.render(shaderProgram, renderBounds);
                southWestChild.render(shaderProgram, renderBounds);
                southEastChild.render(shaderProgram, renderBounds);
            }
            else
            {
                //Vertices
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
                int positionHandle = GLES20.glGetAttribLocation(shaderProgram.getId(), "vPosition");
                GLES20.glEnableVertexAttribArray(positionHandle);
                GLES20.glVertexAttribPointer(positionHandle, 3,
                        GLES20.GL_FLOAT, false,
                        0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                //Color
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
                int colorHandle = GLES20.glGetAttribLocation(shaderProgram.getId(), "vColor");
                GLES20.glEnableVertexAttribArray(colorHandle);
                GLES20.glVertexAttribPointer(colorHandle, 4,
                        GLES20.GL_UNSIGNED_BYTE, true,
                        0, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

                //Render
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangleCoordsSize / 3);
                GLES20.glDisableVertexAttribArray(positionHandle);
                GLES20.glDisableVertexAttribArray(colorHandle);
            }
        }
    }

    public double getXWest()
    {
        return xWest;
    }

    public double getXEast()
    {
        return xEast;
    }

    public double getYNorth()
    {
        return yNorth;
    }

    public double getYSouth()
    {
        return ySouth;
    }

    public int getX()
    {
        return x;
    }

    public int getY()
    {
        return y;
    }

    public boolean isSplit()
    {
        return isSplit;
    }

    public boolean isSplitting()
    {
        return isSplitting;
    }

    public void setIsSplitting() { isSplitting = true; }

    public VectorTileRendered getNorthWestChild()
    {
        return northWestChild;
    }

    public VectorTileRendered getNorthEastChild()
    {
        return northEastChild;
    }

    public VectorTileRendered getSouthWestChild()
    {
        return southWestChild;
    }

    public VectorTileRendered getSouthEastChild()
    {
        return southEastChild;
    }

    public RectF getBounds()
    {
        return new RectF((float)getXWest(), (float)getYSouth(), (float)getXEast(), (float)getYNorth());
    }

    public TileType getType()
    {
        return type;
    }
}
