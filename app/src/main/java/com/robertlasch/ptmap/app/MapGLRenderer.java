package com.robertlasch.ptmap.app;

/**
 * Created by Robert on 13.05.2014.
 */

import android.opengl.*;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;
import java.nio.*;

public class MapGLRenderer implements GLSurfaceView.Renderer
{
    public float aspectRatio, width, height;
    private FloatBuffer vertexBuffer;
    private float[] mProjectionMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mMVPMatrix = new float[16];
    static final int COORDS_PER_VERTEX = 3;
    private float triangleCoords[] = {   // in counterclockwise order:
            0f,  180f, 0.0f, // top
            0f, 0f, 0.0f, // bottom left
            360f, 0f, 0.0f  // bottom right
    };

    float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 1.0f };

    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "uniform mat4 uMVPMatrix;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private float cameraX = 0;
    private float cameraY = 0;
    private float cameraScale = 1f / 180f;

    private Shader vertexShader;
    private Shader fragmentShader;
    private ShaderProgram shaderProgram;

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);

        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                triangleCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        vertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        vertexBuffer.put(triangleCoords);
        // set the buffer to read the first coordinate
        vertexBuffer.position(0);

        //Shader erstellen
        vertexShader = new Shader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        fragmentShader = new Shader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        shaderProgram = new ShaderProgram();
        shaderProgram.attachShader(vertexShader);
        shaderProgram.attachShader(fragmentShader);
        shaderProgram.link();
    }

    public void addCameraPosition(float x, float y)
    {
        cameraX += x;
        cameraY += y;
        adjustCameraBounds();
    }

    public void setScale(float scale)
    {
        cameraScale = scale;
        adjustCameraBounds();
    }

    private void adjustCameraBounds()
    {
        float horizontalDegreesOnScreen = 1f / cameraScale;
        float verticalDegreesOnScreen = 1f * aspectRatio / cameraScale;

        cameraX = Math.max(Math.min(cameraX, 360f - verticalDegreesOnScreen), 0f);
        cameraY = Math.max(Math.min(cameraY, 180f - horizontalDegreesOnScreen), 0f);
    }

    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(shaderProgram.getId());
            //Vertices
            int mPositionHandle = GLES20.glGetAttribLocation(shaderProgram.getId(), "vPosition");
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX,
                    GLES20.GL_FLOAT, false,
                    0, vertexBuffer);
            //Color
            shaderProgram.setUniform4fv("vColor", color);
            //Matrix
            Matrix.setIdentityM(mViewMatrix, 0);
            Matrix.scaleM(mViewMatrix, 0, cameraScale, cameraScale, 0f);
            Matrix.translateM(mViewMatrix, 0, -cameraX, -cameraY, 0f);
            Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
            shaderProgram.setUniformMatrix4fv("uMVPMatrix", mMVPMatrix);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangleCoords.length / 3);
            GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glUseProgram(0);
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        // make adjustments for screen ratio
        aspectRatio = (float) width / height;
        this.width = width;
        this.height = height;
        Matrix.orthoM(mProjectionMatrix, 0, 0, aspectRatio, 0, 1, 0f, 5f);
    }
}
