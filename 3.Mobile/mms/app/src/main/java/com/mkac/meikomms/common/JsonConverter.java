package com.mkac.meikomms.common;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JsonConverter
{
    public static List<JSONObject> jsonArrayToList(JSONArray jsonArray) {
        return IntStream.range(0, jsonArray.length())
                .mapToObj(i -> {
                    try {
                        return jsonArray.getJSONObject(i);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(obj -> obj != null)
                .collect(Collectors.toList());
    }
}
