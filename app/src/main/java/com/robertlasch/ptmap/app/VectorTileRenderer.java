package com.robertlasch.ptmap.app;

import android.app.Activity;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.graphics.*;
import java.util.concurrent.*;
import android.content.Context;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VectorTileRenderer implements GLSurfaceView.Renderer
{
    public static final float BackgroundR = 0.94117647058f;
    public static final float BackgroundG = 0.9294117647f;
    public static final float BackgroundB = 0.89803921568f;

    private double scale = 1500;
    private double xWest;
    private double xEast;
    private double yNorth;
    private double ySouth;
    private int width;
    private int height;
    private double topTileSizeX;
    private double topTileSizeY;
    private float cameraX = (float)SphericalMercator.lonToX(8.881389);
    private float cameraY = (float)SphericalMercator.latToY(50.243056);
    private float aspectRatio;

    private int previousZoomLevel = getZoomLevel();

    private VectorTileRendered[] rootTiles = new VectorTileRendered[] {};

    private ITileProvider tileProvider;

    private ExecutorService splitExecutor = Executors.newCachedThreadPool();

    private AssetManager assets;

    //Matrizen
    private float[] projectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] mvpMatrix = new float[16];

    private static final String vertexShaderCode =
            "attribute vec4 vPosition;" +
            "attribute vec4 vColor;" +
            "varying vec4 vertexColor;" +
                    "uniform mat4 uMVPMatrix;" +
                    "void main() {" +
                    "  vertexColor = vColor;" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private static final String fragmentShaderCode =
            "precision mediump float;" +
            "varying vec4 vertexColor;" +
                    "void main() {" +
                    "  gl_FragColor = vertexColor;" +
                    "}";

    private Shader vertexShader;
    private Shader fragmentShader;
    private ShaderProgram shaderProgram;
    private Context context;
    private GLSurfaceView surfaceView;

    public VectorTileRenderer(ITileProvider tileProvider, Context context, GLSurfaceView surfaceView)
    {
        //Begrenzung:
        //Meridiane/Längengrade nach Mercator-Projection
        xWest = SphericalMercator.lonToX(-180);
        xEast = SphericalMercator.lonToX(180);
        //Breitengrade nach Mercator-Projektion
        //yNorth = SphericalMercator.latToY(MinLatitude);
        //ySouth = SphericalMercator.latToY(MaxLatitude);
        yNorth = xEast; //+
        ySouth = xWest; //-

        topTileSizeX = xEast - xWest;
        topTileSizeY = ySouth - yNorth;

        this.tileProvider = tileProvider;
        this.context = context;
        this.surfaceView = surfaceView;
        this.assets = context.getAssets();
    }

    public GLSurfaceView getSurfaceView()
    {
        return surfaceView;
    }

    public int getZoomLevel()
    {
        return (int)Math.floor(Math.log(scale) / Math.log(2) + 1);
    }

    public float getPixelSize(int zoomLevel)
    {
        return (float)(getTileSizeX(zoomLevel) / (double)width);
    }

    public double getTileSizeX(int zoomLevel)
    {
        return topTileSizeX / Math.pow(2, zoomLevel - 1);
    }

    public double getTileSizeY(int zoomLevel)
    {
        return topTileSizeY / Math.pow(2, zoomLevel - 1);
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

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public double getLeftBounds()
    {
        return (xWest * aspectRatio) / scale + cameraX;
    }

    public double getRightBounds()
    {
        return getLeftBounds() + xEast * aspectRatio * 2 / scale;
    }

    public double getTopBounds()
    {
        return yNorth / scale + cameraY;
    }

    public double getBottomBounds()
    {
        return getTopBounds() + ySouth * 2 / scale;
    }

    public void setCameraX(float x)
    {
        cameraX = x;
    }

    public void setCameraY(float y)
    {
        cameraY = y;
    }

    public RectF getBounds()
    {
        return new RectF((float)getLeftBounds(), (float)getBottomBounds(), (float)getRightBounds(), (float)getTopBounds());
    }

    public void setScale(float scale)
    {
        this.scale = scale;
        adjustCameraBounds();

        if (getZoomLevel() > previousZoomLevel)
        {
            for (VectorTileRendered tile : rootTiles)
                splitTile(tile);
        }

        previousZoomLevel = getZoomLevel();
    }

    public void splitTile(VectorTileRendered tile)
    {
        if (tile.getZoomLevel() < getZoomLevel() && RectF.intersects(tile.getBounds(), getBounds()))
        {
            if (!tile.isSplit() && !tile.isSplitting())
            {
                tile.setIsSplitting();
                SplitCommand split = new SplitCommand(tile, this, tileProvider);
                splitExecutor.execute(split);
            }

            if (tile.isSplit())
            {
                splitTile(tile.getNorthEastChild());
                splitTile(tile.getNorthWestChild());
                splitTile(tile.getSouthEastChild());
                splitTile(tile.getSouthWestChild());
            }
        }
    }

    private void adjustCameraBounds()
    {
        //cameraX = (float)Math.max(Math.min(cameraX, 0), 0);
        //cameraY = (float)Math.max(Math.min(cameraY, 0), 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig)
    {
        GLES20.glClearColor(BackgroundR, BackgroundG, BackgroundB, 1.0f);

        GLES20.glDisable(GLES20.GL_CULL_FACE);
        //GLES20.glDepthFunc(GLES20.GL_EQUAL);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        vertexShader = new Shader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        fragmentShader = new Shader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        shaderProgram = new ShaderProgram();
        shaderProgram.attachShader(vertexShader);
        shaderProgram.attachShader(fragmentShader);
        shaderProgram.link();

        rootTiles = new VectorTileRendered[]{
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.WaterAreas), VectorTileRendered.TileType.WaterAreas, this, null, false, false),
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.LandUsages), VectorTileRendered.TileType.LandUsages, this, null, false, false),
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.RoadLines), VectorTileRendered.TileType.RoadLines, this, null, false, false),
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.Buildings), VectorTileRendered.TileType.Buildings, this, null, false, false),
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.RoadLabels), VectorTileRendered.TileType.RoadLabels, this, null, false, false),
                new VectorTileRendered(tileProvider.getTile(0, 0, 0, VectorTileRendered.TileType.PointsOfInterest), VectorTileRendered.TileType.PointsOfInterest, this, null, false, false)
        };
    }

    public void addCameraPosition(float x, float y)
    {
        cameraX += (x / width * xEast * 2 * aspectRatio) / scale;
        cameraY += (y / height * yNorth * 2) / scale;
        adjustCameraBounds();
        for (VectorTileRendered tile : rootTiles)
            splitTile(tile);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        this.width = width;
        this.height = height;

        GLES20.glViewport(0, 0, width, height);

        //make adjustments for screen ratio
        aspectRatio = (float)width / height;
        Matrix.orthoM(projectionMatrix, 0, (float)xWest * aspectRatio, (float)xEast * aspectRatio, (float)ySouth, (float)yNorth, -1f, 20000f);
    }

    @Override
    public void onDrawFrame(GL10 gl)
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(shaderProgram.getId());

        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.scaleM(viewMatrix, 0, (float)scale, (float)scale, 1f);
        Matrix.translateM(viewMatrix, 0, -cameraX, -cameraY, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        shaderProgram.setUniformMatrix4fv("uMVPMatrix", mvpMatrix);

        for (VectorTileRendered tile : rootTiles)
            tile.render(shaderProgram, getBounds());

        GLES20.glUseProgram(0);

        float[] identity = new float[16];
        Matrix.setIdentityM(identity, 0);
    }
}
