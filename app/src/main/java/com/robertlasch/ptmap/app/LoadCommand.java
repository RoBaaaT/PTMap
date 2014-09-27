package com.robertlasch.ptmap.app;

public class LoadCommand implements Runnable
{
    private VectorTileRendered tile;

    public LoadCommand(VectorTileRendered tile)
    {
        this.tile = tile;
    }

    @Override
    public void run()
    {
        System.out.println("Loading Tile: " + tile.getType().toString() + " :x/y/zoom:" + tile.getX() + "/" + tile.getY() + "/" + tile.getZoomLevel());
        tile.load();
    }
}
