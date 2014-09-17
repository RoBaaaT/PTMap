package com.robertlasch.ptmap.app;

import android.app.Activity;
import android.content.res.Resources;
import android.location.Location;
import android.opengl.*;
import android.content.*;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.support.v4.view.*;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.a;
import com.google.android.gms.location.LocationClient;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Created by Robert on 13.05.2014.
 */
public class MapGLSurfaceView extends GLSurfaceView implements
        ScaleGestureDetector.OnScaleGestureListener,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener
{
    private static final int INVALID_POINTER_ID = -1;

    private ScaleGestureDetector scaleDetector;
    private VectorTileRenderer renderer;
    private float mPreviousX;
    private float mPreviousY;
    private int mActivePointerId = INVALID_POINTER_ID;
    private float scale = 1500f;

    private LocationClient locationClient;

    public MapGLSurfaceView(Context context, Activity activity)
    {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new VectorTileRenderer(new MVTTileProvider(new String[]{
                "http://tile.openstreetmap.us/vectiles-highroad/{z}/{x}/{y}.mvt",
                "http://tile.openstreetmap.us/vectiles-skeletron/{z}/{x}/{y}.mvt",
                "http://tile.openstreetmap.us/vectiles-buildings/{z}/{x}/{y}.mvt",
                "http://tile.openstreetmap.us/vectiles-pois/{z}/{x}/{y}.mvt",
                "http://tile.openstreetmap.us/vectiles-land-usages/{z}/{x}/{y}.mvt",
                "http://tile.openstreetmap.us/vectiles-water-areas/{z}/{x}/{y}.mvt"}), context, this);
        setRenderer(renderer);

        scaleDetector = new ScaleGestureDetector(context, this);

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS)
        {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
            {
                GooglePlayServicesUtil.getErrorDialog(resultCode, activity, 1).show();
            }
        }

        locationClient = new LocationClient(context, this, this);
        locationClient.connect();
    }

    @Override
    public void onDisconnected()
    {

    }

    @Override
    public void onConnected(Bundle bundle)
    {
        Log.i("Location Services", "Connected");

        Location currentLocation = locationClient.getLastLocation();
        locationClient.disconnect();

        renderer.setCameraX((float)SphericalMercator.lonToX(currentLocation.getLongitude()));
        renderer.setCameraY((float)SphericalMercator.latToY(currentLocation.getLatitude()));
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult)
    {
        Log.e("Location Service", "Connection Failed:" + connectionResult.getErrorCode() + " " + connectionResult.hasResolution());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        scaleDetector.onTouchEvent(e);

        final int action = e.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = e.getX();
                final float y = e.getY();

                mPreviousX = x;
                mPreviousY = y;
                mActivePointerId = e.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = e.findPointerIndex(mActivePointerId);
                final float x = e.getX(pointerIndex);
                final float y = e.getY(pointerIndex);

                // Only move if the ScaleGestureDetector isn't processing a gesture.
                if (!scaleDetector.isInProgress()) {
                    final float dx = x - mPreviousX;
                    final float dy = y - mPreviousY;

                    renderer.addCameraPosition(-dx, dy);

                    invalidate();
                }

                mPreviousX = x;
                mPreviousY = y;

                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = (e.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = e.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mPreviousX = e.getX(newPointerIndex);
                    mPreviousY = e.getY(newPointerIndex);
                    mActivePointerId = e.getPointerId(newPointerIndex);
                }
                break;
            }
        }

        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector)
    {
        scale *= detector.getScaleFactor();
        scale = Math.max(0.1f, Math.min(scale, (float)Math.pow(2, 20 - 1)));
        renderer.setScale(scale);
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) { }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
}