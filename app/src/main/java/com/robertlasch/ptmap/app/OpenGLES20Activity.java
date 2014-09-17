package com.robertlasch.ptmap.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import java.sql.*;
import android.opengl.*;
import android.util.Log;
import android.widget.DialerFilter;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationClient;

import java.sql.DriverManager;

public class OpenGLES20Activity extends Activity
{
    private GLSurfaceView glView;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity.
        glView = new MapGLSurfaceView(this, this);
        setContentView(glView);
    }
}
