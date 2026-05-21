package com.mkac.meikomms.common;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public  class ConfigManager
{
    private Properties properties;

    public ConfigManager(Context context) {
        // Load the properties file from the assets folder
        AssetManager assetManager = context.getAssets();
        properties = new Properties();

        try {
            InputStream inputStream = assetManager.open("config.properties");
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//    public String getProperty(String key)
//    {
//        return properties.getProperty(key);
//    }

    public String getProperty(String key) {
        if (properties == null) {
            return "";
        }
        String value = properties.getProperty(key);
        return (value != null) ? value.trim() : "";
    }

    public Boolean setProperty(String key,String value)
    {
        try
        {
            properties.setProperty(key,value);
            return true;
        }
        catch(Exception ex)
        {
            return false;
        }

    }


    public void writeToPropertiesFile(Context context) throws IOException {
        properties = new Properties();
        AssetManager assetManager = context.getAssets();
        // Add key-value pairs
        properties.setProperty("schema_core", "MES_CORE");
        properties.setProperty("schema_data", "MES_DATA");

        // Writing to internal storage
        String fileName = "config.properties";
        FileOutputStream fileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);

        properties.store(fileOutputStream, "Android Configuration Properties");
        fileOutputStream.close();
    }



}
