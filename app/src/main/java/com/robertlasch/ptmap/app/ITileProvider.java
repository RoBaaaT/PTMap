package com.robertlasch.ptmap.app;

import com.vividsolutions.jts.geom.Geometry;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.zip.DataFormatException;

/**
 * Created by Robert on 14.06.2014.
 */
public interface ITileProvider
{
    public HashMap<Geometry, HashMap<String, String>> getTile(int x, int y, int zoomLevel, VectorTileRendered.TileType type);
}
