package com.robertlasch.ptmap.app;

import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * Created by Robert on 29.05.2014.
 */
public class ShaderProgram
{
    private int program;

    public ShaderProgram()
    {
        program = GLES20.glCreateProgram();
    }

    public void link()
    {
        GLES20.glLinkProgram(program);
    }

    public void attachShader(Shader shader)
    {
        GLES20.glAttachShader(program, shader.getId());
    }

    public int getId()
    {
        return program;
    }

    public void setUniform4fv(String uniform, float[] value)
    {
        int handle = GLES20.glGetUniformLocation(program, uniform);
        GLES20.glUniform4fv(handle, 1, value, 0);
    }

    public void setUniformMatrix4fv(String uniform, float[] value)
    {
        int handle = GLES20.glGetUniformLocation(program, uniform);
        GLES20.glUniformMatrix4fv(handle, 1, false, value, 0);
    }

    public void setUniform4f(String uniform, float val1, float val2, float val3, float val4)
    {
        int handle = GLES20.glGetUniformLocation(program, uniform);
        GLES20.glUniform4f(handle, val1, val2, val3, val4);
    }

    public void setUniformTexture(String uniform, int textureRegister)
    {
        int handle = GLES20.glGetUniformLocation(program, uniform);
        GLES20.glUniform1i(handle, textureRegister);
    }
}
