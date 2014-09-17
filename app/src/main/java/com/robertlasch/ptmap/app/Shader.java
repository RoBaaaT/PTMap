package com.robertlasch.ptmap.app;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Created by Robert on 29.05.2014.
 */
public class Shader
{
    private int shader;

    public Shader(int type, String shaderCode)
    {
        shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        String res = GLES20.glGetShaderInfoLog(shader);
        Log.i("Shader compiler", res);
    }

    public int getId()
    {
        return shader;
    }
}
