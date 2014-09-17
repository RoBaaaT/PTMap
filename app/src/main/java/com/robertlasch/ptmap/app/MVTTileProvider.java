package com.robertlasch.ptmap.app;

import android.util.Log;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;

import java.text.ParseException;
import java.util.Arrays;
import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class MVTTileProvider implements ITileProvider
{
    private static String[] urlTemplates;

    public MVTTileProvider(String[] urlTemplates)
    {
        this.urlTemplates = urlTemplates;
    }

    @Override
    public HashMap<Geometry, HashMap<String, String>> getTile(int x, int y, int zoomLevel, VectorTileRendered.TileType type)
    {
        try
        {
            String url = urlTemplates[type.id];
            url = url.replace("{x}", ((Integer)x).toString());
            url = url.replace("{y}", ((Integer)y).toString());
            url = url.replace("{z}", ((Integer)zoomLevel).toString());
            URL host = new URL(url);
            URLConnection connection = host.openConnection();

            InputStream stream = connection.getInputStream();

            byte[] data = getBytesFromInputStream(stream);

            if (!(data[0] == -119 && ((char)data[1]) == 'M'  && ((char)data[2]) == 'V'  && ((char)data[3]) == 'T'))
                return null;

            int zipLength = (data[4] << 24 | (data[5] & 0xFF) << 16 | (data[6] & 0xFF) << 8 | (data[7] & 0xFF));

            Inflater decompresser = new Inflater();
            decompresser.setInput(data, 8, zipLength);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            while (!decompresser.finished())
            {
                int count = decompresser.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            decompresser.end();
            outputStream.close();
            byte[] result = outputStream.toByteArray();

            int featureCount = (result[0] << 24 | (result[1] & 0xFF) << 16 | (result[2] & 0xFF) << 8 | (result[3] & 0xFF));
            result = Arrays.copyOfRange(result, 4, result.length);

            HashMap<Geometry, HashMap<String, String>> out = new HashMap<Geometry, HashMap<String, String>>();

            int featureStart = 0;

            for(int i = 0; i < featureCount; i++)
            {
                int wkbLength = (result[featureStart] << 24 | (result[featureStart + 1] & 0xFF) << 16 | (result[featureStart + 2] & 0xFF) << 8 | (result[featureStart + 3] & 0xFF));
                byte[] wkbData = Arrays.copyOfRange(result, featureStart + 4, featureStart + 4 + wkbLength);
                WKBReader geomReader = new WKBReader();
                Geometry wkb = geomReader.read(wkbData);

                int jsonLength = (result[featureStart + 4 + wkbLength] << 24 | (result[featureStart + 5 + wkbLength] & 0xFF) << 16 | (result[featureStart + 6 + wkbLength] & 0xFF) << 8 | (result[featureStart + 7 + wkbLength] & 0xFF));
                String jsonString = new String(result, featureStart + 8 + wkbLength, jsonLength);
                HashMap<String, String> jsonDict = new HashMap<String, String>();
                jsonString = jsonString.replace("{", "").replace("}", "");
                //Replace special characters
                jsonString = jsonString.replace("\\u00df", "ß");
                jsonString = jsonString.replace("\\u00e4", "ä");
                jsonString = jsonString.replace("\\u00f6", "ö");
                jsonString = jsonString.replace("\\u00fC", "ü");
                jsonString = jsonString.replace("\\u00c4", "Ä");
                jsonString = jsonString.replace("\\u00d6", "Ö");
                jsonString = jsonString.replace("\\u00dc", "Ü");
                if (!jsonString.trim().equals(""))
                {
                    String[] dict = jsonString.split(",\\s?\"");

                    try
                    {
                        for (String s : dict)
                        {
                            String[] keyVal = s.replace("\"", "").split(":");
                            jsonDict.put(keyVal[0].trim(), keyVal[1].trim());
                        }
                    } catch (Exception e)
                    {
                        Log.e("Unparsable JSON", jsonString);
                    }
                }

                out.put(wkb, jsonDict);

                featureStart += 8 + wkbLength + jsonLength;
            }

            return out;
        }
        catch (Exception e)
        {
            System.out.println(e.getLocalizedMessage());
            e.printStackTrace();
        }

        return null;
    }

    public static byte[] getBytesFromInputStream(InputStream is)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try
        {
            byte[] buffer = new byte[1024];

            for (int len; (len = is.read(buffer)) != -1;)
                os.write(buffer, 0, len);

            os.flush();
            byte[] result = os.toByteArray();
            os.close();

            return result;
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
