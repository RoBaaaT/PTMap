package com.robertlasch.ptmap.app;

public class SplitCommand implements Runnable
{
    private VectorTileRendered tile;
    private VectorTileRenderer renderer;
    private ITileProvider tileProvider;

    public SplitCommand(VectorTileRendered tile, VectorTileRenderer renderer, ITileProvider tileProvider)
    {
        this.tile = tile;
        this.renderer = renderer;
        this.tileProvider = tileProvider;
    }

    @Override
    public void run()
    {
        System.out.println("Splitting Tile: " + tile.getType().toString() + " :x/y/zoom:" + tile.getX() + "/" + tile.getY() + "/" + tile.getZoomLevel());

        tile.split(
                new VectorTileRendered(tileProvider.getTile(tile.getX() * 2    , tile.getY() * 2    , tile.getZoomLevel(), tile.getType()), renderer, tile, true, true),  //Nord-West
                new VectorTileRendered(tileProvider.getTile(tile.getX() * 2 + 1, tile.getY() * 2    , tile.getZoomLevel(), tile.getType()), renderer, tile, true, false), //Nord-Ost
                new VectorTileRendered(tileProvider.getTile(tile.getX() * 2    , tile.getY() * 2 + 1, tile.getZoomLevel(), tile.getType()), renderer, tile, false, true), //Süd-West
                new VectorTileRendered(tileProvider.getTile(tile.getX() * 2 + 1, tile.getY() * 2 + 1, tile.getZoomLevel(), tile.getType()), renderer, tile, false, false) //Süd-Ost
        );
    }
}
