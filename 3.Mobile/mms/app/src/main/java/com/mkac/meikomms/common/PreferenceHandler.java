package com.mkac.meikomms.common;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class PreferenceHandler
{
//    private static final String PREF_NAME = "MyAppPreferences";
//    private SharedPreferences sharedPreferences;
//    private SharedPreferences.Editor editor;
//    private Context context;
//
//    public PreferenceHandler(Context context) {
//        this.context = context;
//        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
//        editor = sharedPreferences.edit();
//    }
//
//    // Lưu trạng thái boolean
//    public void setBoolean(String key, boolean value) {
//        editor.putBoolean(key, value);
//        editor.apply();
//    }
//
//    // Truy xuất trạng thái boolean
//    public boolean getBoolean(String key) {
//        return sharedPreferences.getBoolean(key, false);
//    }
//
//    // Lưu một chuỗi
//    public void setString(String key, String value) {
//        editor.putString(key, value);
//        editor.apply();
//    }
//
//    // Truy xuất một chuỗi
//    public String getString(String key) {
//        return sharedPreferences.getString(key, "");
//    }
//
//    // Lưu một đối tượng JSON
//    public void setJsonObject(String key, JSONObject jsonObject) {
//        if(jsonObject == null){
//            editor.remove(key).apply();
//        }else{
//            editor.putString(key, jsonObject.toString());
//            editor.apply();
//        }
//    }
//
//    public JSONObject getJsonObject(String key) {
//        String jsonString = sharedPreferences.getString(key, null);
//
//        if (jsonString == null || jsonString.isEmpty()) return null;
//
//        try {
//            return new JSONObject(jsonString);
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    public void remove(String key) {
//        editor.remove(key).apply();
//    }
//
//    public void clear() {
//        editor.clear().apply();
//    }

    private static final String PREF_NAME = "MyAppPreferences";
    private SharedPreferences sharedPreferences;

    public PreferenceHandler(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    public void setString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public String getString(String key) {
        return sharedPreferences.getString(key, "");
    }

    public void setJsonObject(String key, JSONObject jsonObject) {
        if (jsonObject == null) {
            remove(key);
        } else {
            sharedPreferences.edit().putString(key, jsonObject.toString()).apply();
        }
    }

//    public JSONObject getJsonObject(String key) {
//        String jsonString = sharedPreferences.getString(key, null);
//        if (jsonString == null || jsonString.isEmpty()) return null;
//        try {
//            return new JSONObject(jsonString);
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

    public JSONObject getJsonObject(String key) {
        try {
            String jsonString = sharedPreferences.getString(key, null);
            if (jsonString == null || jsonString.isEmpty()) return new JSONObject();
            return new JSONObject(jsonString);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void remove(String key) {
        sharedPreferences.edit().remove(key).apply();
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }

}
