package com.mkac.meikomms.common;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.core.net.ParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpClient
{

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    static String token = "";
    static OkHttpClient client = new OkHttpClient();

//    public static void initToken(String newToken) {
//        token = newToken;
//    }
    public static synchronized void initToken(String newToken) {
        token = newToken;
    }
    public static synchronized void clearToken() {
        token = null;
    }
    public static synchronized String getToken() {
        return token == null ? "" : token;
    }
    public static APIReturn post(String url, String json)
    {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = buildRequest(url, "POST",body);
        return executeRequest(request);
    }

    public static APIReturn postFile(String url, String json,ArrayList<Uri> selectedFileUris,Context context)
    {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        builder.addFormDataPart("data", json.toString());
        for (Uri uri : selectedFileUris)
        {
            if(!uri.toString().contains("http"))
            {
                String fileName = getFileNameFromUri(uri,context);
                byte[] fileBytes = readFileFromUri(uri,context);
                if (fileBytes != null)
                {
                    builder.addFormDataPart("files", fileName,
                            RequestBody.create(MediaType.parse(getMimeType(fileName)), fileBytes));
                }
            }

        }

        // Tạo request
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        // Gửi request không đồng bộ
        return executeRequest(request);
    }
    public static APIReturn uploadWorkOrderFile(Context context, String server_url, String taskId, Uri fileUri) {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");
        try {
            String finalUrl = server_url;
            if (finalUrl.contains("://")) {
                String protocol = finalUrl.split("://")[0];
                String addressWithPort = finalUrl.split("://")[1];
                if (addressWithPort.contains(":")) {
                    finalUrl = protocol + "://" + addressWithPort.split(":")[0];
                } else {
                    finalUrl = protocol + "://" + addressWithPort;
                }
            }
            if (finalUrl.endsWith("/")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
            finalUrl = finalUrl + ":9101/api/v1/mms_file-img/upload-file-task";

            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            builder.addFormDataPart("taskId", taskId);

            String fileName = getFileNameFromUri(fileUri, context);
            byte[] fileBytes = readFileFromUri(fileUri, context);
            if (fileBytes != null) {
                builder.addFormDataPart("files", fileName,
                        RequestBody.create(MediaType.parse(getMimeType(fileName)), fileBytes));
            }

            RequestBody requestBody = builder.build();
            Request request = new Request.Builder()
                    .url(finalUrl)
                    .header("Authorization", "Bearer " + token)
                    .post(requestBody)
                    .build();

            APIReturn apiReturn = executeRequest(request);
            // Extra debug: log upload response details for easier troubleshooting
            try {
                Log.d("UPLOAD_IMAGE_DBG", "uploadPreventiveImage -> response code=" + (apiReturn == null ? "null" : apiReturn.code) + ", msg=" + (apiReturn == null ? "null" : apiReturn.message) + ", data=" + (apiReturn == null || apiReturn.data == null ? "null" : apiReturn.data.toString()));
            } catch (Exception ignored) {}
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn postNoAuth(String url, String json) {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = buildRequestNoAuth(url, "POST", body);
        return executeRequest(request);
    }

    public static APIReturn get(String url)
    {

        Request request = buildRequest(url, "GET", null);
        return executeRequest(request);

    }

    private static Request buildRequest(String url,String method,RequestBody body)
    {

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .method(method, body);
        return builder.build();
    }

    private static Request buildRequestNoAuth(String url, String method, RequestBody body)
    {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .method(method, body);
        return builder.build();
    }

    private static APIReturn executeRequest(Request request) {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String responseBody = response.body().string();
            Log.d("WO_WORK_ORDER", "HTTP response code=" + response.code() + ", body=" + responseBody);

            JSONObject jsonObject = new JSONObject(responseBody);
            int code = jsonObject.optInt("code", 400);
            String message = jsonObject.optString("message", "No message");

            Object rawData = jsonObject.opt("data");

            List<JSONObject> dataList = new ArrayList<>();

            if (rawData instanceof JSONObject) {
                dataList.add((JSONObject) rawData);
            } else if (rawData instanceof JSONArray) {
                JSONArray dataArray = (JSONArray) rawData;
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject item = dataArray.optJSONObject(i);
                    if (item != null) {
                        dataList.add(item);
                    } else {
                        String arr = dataArray.optString(i, null);
                        if (arr != null) {
                            dataList.add(new JSONObject("{ \"value\": \"" + arr + "\"}"));
                        }
                    }
                }
            }
            return new APIReturn(code, message, dataList);

        } catch (IOException e) {
            return new APIReturn(403, "IOException|| " + e.getMessage(), null);
        } catch (JSONException e) {
            return new APIReturn(404, "JSONException|| " + e.getMessage(), null);
        }
    }

    // Debug helper: fetch raw URL with Authorization header and log basic response info
    public static APIReturn fetchUrlDebug(String url) {
        OkHttpClient debugClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + (token == null ? "" : token))
                    .get()
                    .build();
            try (Response response = debugClient.newCall(request).execute()) {
                int code = response.code();
                String ct = response.header("Content-Type", "");
                long len = -1;
                if (response.body() != null) {
                    len = response.body().contentLength();
                    // read small prefix
                    byte[] prefix = new byte[0];
                    try (InputStream is = response.body().byteStream()) {
                        int max = 512;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[256];
                        int read;
                        int total = 0;
                        while ((read = is.read(buffer)) != -1 && total < max) {
                            int toWrite = Math.min(read, max - total);
                            out.write(buffer, 0, toWrite);
                            total += toWrite;
                        }
                        prefix = out.toByteArray();
                    } catch (Exception ignored) {}
                    String prefixStr = "";
                    try { prefixStr = new String(prefix); } catch (Exception ignored) {}
                    Log.d("PREVIEW_DEBUG", "fetchUrlDebug url=" + url + " code=" + code + " contentType=" + ct + " len=" + len + " prefix=" + prefixStr);
                } else {
                    Log.d("PREVIEW_DEBUG", "fetchUrlDebug url=" + url + " code=" + code + " no body");
                }
                return new APIReturn(code, "OK", null);
            }
        } catch (Exception e) {
            Log.e("PREVIEW_DEBUG", "fetchUrlDebug error for url=" + url, e);
            return new APIReturn(500, "Exception||" + e.getMessage(), null);
        }
    }

    // Lấy tên file từ Uri
    private static String getFileNameFromUri(Uri uri,Context context) {
        String fileName = "unknown_file";
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    // Đọc nội dung file trực tiếp từ Uri
    private static byte[] readFileFromUri(Uri uri,Context context) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Lấy MIME type từ tên file
    private static String getMimeType(String fileName) {
        String type = null;
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (extension != null) {
            type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type != null ? type : "application/octet-stream";
    }

    public static APIReturn login(Context context,String server_url ,String userName, String password)
    {
        try {
            String EndPoint = "/user/signin";
            String bodyRequest = "{\n" +
                    "  \"username\": \"" + userName + "\",\n" +
                    "  \"password\": \"" + password + "\"\n" +
                    "}";
            HttpClient httpClient = new HttpClient();
//            String response = null;
            APIReturn apiReturn = httpClient.postNoAuth(server_url + EndPoint, bodyRequest);

            ColorConsole.d("login",server_url + EndPoint);
            ColorConsole.d("login",bodyRequest);

            if (apiReturn.code == 200 && apiReturn.data != null && !apiReturn.data.isEmpty())
            {
                PreferenceHandler appPreferences = new PreferenceHandler(context);
                appPreferences.setBoolean("isLoggedIn", true);

                JSONObject user = apiReturn.data.get(0);

                if (user.has("user")) {
                    JSONObject userObj = user.getJSONObject("user");

                    String token = user.optString("token");
                    userObj.put("token", token);

                    appPreferences.setJsonObject("user", userObj);
                }
            }
            else
            {
                PreferenceHandler appPreferences = new PreferenceHandler(context);
                appPreferences.setBoolean("isLoggedIn", false);
                appPreferences.setJsonObject("user", new JSONObject());
            }
            ColorConsole.d("login",apiReturn.code + "||" + apiReturn.message);
            return apiReturn;
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }



    public static APIReturn get_list_warehouse_data(Context context, String server_url,String User_Id)
    {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");
        // Lấy danh sách kho vật tư
        try {

            String EndPoint = "/api/v1/mms_mobile/warehouse";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.get(server_url + EndPoint);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_list_warehouse_and_material_by_task_data(Context context,String Task_Id ,String server_url,String User_Id)
    {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");
        // Lấy danh sách vật tư và danh sách kho vật tư
        try {

            String EndPoint = "/api/v1/mms_mobile/listallmaterial";

            String bodyRequest = "{\n" +

                    "        \"Task_Id\": \""+Task_Id+"\"\n" +

                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_list_task_data(Context context, String server_url,String User_Id)
    {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");
        // Lấy danh sách hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/listtask";

            String bodyRequest = "{\n" +

                    "        \"User_Id\": \""+User_Id+"\",\n" +
                    "        \"Status\": \"'0','4','5'\"\n" +

                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_list_history(Context context,String From_Date,String To_Date, String server_url,String User_Id)
    {
        // Lấy danh sách hạng mục đã hoàn thành
        try {

            String EndPoint = "/api/v1/mms_mobile/history";

            String bodyRequest = "{\n" +

                    "        \"User_Id\": \""+User_Id+"\",\n" +
                    "         \"From_Date\": \""+From_Date+"\",\n" +
                    "        \"To_Date\": \""+ getNextDate(To_Date)+"\",\n" +
                    "        \"Status\": \"\"\n" +

                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }


    public static APIReturn get_list_history_by_machine(Context context,String Machine_Id,String From_Date,String To_Date,String Task_Type, String server_url,String User_Id)
    {
        // Lấy danh sách hạng mục đã hoàn thành
        try {

            String EndPoint = "/api/v1/mms_mobile/historymachine";

            String bodyRequest = "{\n" +

                    "        \"User_Id\": \""+User_Id+"\",\n" +
                    "        \"Machine_Id\": \""+Machine_Id+"\",\n" +
                    "         \"From_Date\": \""+From_Date+"\",\n" +
                    "        \"To_Date\": \""+To_Date+"\",\n" +
                    "        \"Task_Type\": \""+Task_Type+"\"\n" +

                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_list_instruction_data(Context context,String Task_Id ,String Category_Name,String server_url,String User_Id)
    {
        // Lấy danh sách các bước bảo trì
        try {

            String EndPoint = "/api/mms_mobile/access";

            String bodyRequest = "{\n" +
                    "    \"Action_Name\": \"MES_MMS_MOBILE_GET_INTRUCTION\",\n" +
                    "    \"Data\": {\n" +
                    "        \"Task_Id\": \""+Task_Id+"\",\n" +
                    "         \"Category_Name\": \""+Category_Name+"\"\n" +
                    "    }\n" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_list_material_data(Context context,List<JSONObject> list_task, String server_url,String User_Id)
    {

        // Lấy danh sách vật tư hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/material";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Image_List\": \"\"," +
                    "\"Image_List_Delete\": \"\"," +
                    "\"Note\": \"\"," +
                    "\"Current_Situation\": \"\"," +
                    "\"Root_Cause\": \"\"," +
                    "\"Action_Taken\": \"\"," +
                    "\"Countermeasure\": \"\"," +
                    "\"DataListTask\":"+ list_task +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }


    public static APIReturn get_list_material_warehouse_data(Context context,List<JSONObject> list_task, String server_url,String User_Id)
    {

        // Lấy danh sách vật tư và danh sách kho
        try {

            String EndPoint = "/api/v1/mms_mobile/materialwarehouse";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Image_List\": \"\"," +
                    "\"Image_List_Delete\": \"\"," +
                    "\"Note\": \"\"," +
                    "\"Current_Situation\": \"\"," +
                    "\"Root_Cause\": \"\"," +
                    "\"Action_Taken\": \"\"," +
                    "\"Countermeasure\": \"\"," +
                    "\"DataListTask\":"+ list_task +"," +
                    "\"Premaintenance\":[]," +
                    "\"Postemaintenance\":[]" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn get_list_error_data(Context context, String server_url,String User_Id)
    {
        // Lấy danh sách lỗi đã báo của user
        try {

            String EndPoint = "/api/v1/mms_mobile/listerror";

            String bodyRequest = "{\n" +

                    "        \"User_Id\": \""+User_Id+"\",\n" +
                    "        \"Status\": \"\"\n" +

                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn delete_error_by_user(Context context,String Issue_Id, String server_url,String User_Id)
    {
        // Xóa lỗi đã báo của user
        try {

            String EndPoint = "/api/v1/mms_mobile/deleteerror";

            String bodyRequest = "{\n" +

                    "        \"Issue_Id\": \""+Issue_Id+"\"\n" +


                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }


    public static APIReturn select_task_by_user(Context context,List<JSONObject> list_task, String server_url,String User_Id)
    {

        // Xác nhận task bảo trì đã chọn cho user
        try {

            String EndPoint = "/api/v1/mms_mobile/selecttaskbyuser/";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Image_List\": \"\"," +
                    "\"Image_List_Delete\": \"\"," +
                    "\"Note\": \"\"," +
                    "\"Current_Situation\": \"\"," +
                    "\"Root_Cause\": \"\"," +
                    "\"Action_Taken\": \"\"," +
                    "\"Countermeasure\": \"\"," +
                    "\"DataListTask\":"+ list_task +"," +
                    "\"Premaintenance\":[]," +
                    "\"Postemaintenance\":[]" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn save_end_task_data(Context context,JSONArray list_pre_step,JSONArray list_post_step,List<JSONObject> list_task ,String Note,String Current_Situation,String Root_Cause,String Action_Taken,String Countermeasure,String server_url,String User_Id)
    {
        // Xác nhận hoàn thành task bảo trì đã chọn cho user
        try {
            String EndPoint = "/api/v1/mms_mobile/endtaskbyuser/";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Image_List\": \"\"," +
                    "\"Image_List_Delete\": \"\"," +
                    "\"Note\": \""+Note+"\"," +
                    "\"Current_Situation\": \""+Current_Situation+"\"," +
                    "\"Root_Cause\": \""+Root_Cause+"\"," +
                    "\"Action_Taken\": \""+Action_Taken+"\"," +
                    "\"Countermeasure\": \""+Countermeasure+"\"," +
                    "\"DataListTask\":"+ list_task +"," +
                    "\"Premaintenance\":"+ list_pre_step +"," +
                    "\"Postemaintenance\":"+ list_post_step +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        }
        catch (Exception e)
        {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn save_end_task_file_data(Context context,JSONArray list_pre_step,JSONArray list_post_step,List<JSONObject> list_task,ArrayList<Uri> selectedFileUris,String list_image_name,String list_image_delete,String Note,String Current_Situation,String Root_Cause,String Action_Taken,String Countermeasure,String server_url,String User_Id)
    {

        // Hoàn thành bảo trì kèm file
        try {

            String EndPoint = "/api/v1/mms_mobile/file/end";

            String bodyRequest = "{\n" +
                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Image_List\":\""+list_image_name+"\"," +
                    "\"Image_List_Delete\":\""+list_image_delete+"\"," +
                    "\"Note\": \""+Note+"\"," +
                    "\"Current_Situation\": \""+Current_Situation+"\"," +
                    "\"Root_Cause\": \""+Root_Cause+"\"," +
                    "\"Action_Taken\": \""+Action_Taken+"\"," +
                    "\"Countermeasure\": \""+Countermeasure+"\"," +
                    "\"DataListTask\":"+ list_task +"," +
                    "\"Premaintenance\":"+ list_pre_step +"," +
                    "\"Postemaintenance\":"+ list_post_step +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.postFile(server_url + EndPoint, bodyRequest,selectedFileUris,context);
            return apiReturn;
        }
        catch (Exception e)
        {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn get_list_machine_data(Context context, String server_url,String User_Id)
    {
        // Lấy danh sách máy
        try {

            String EndPoint = "/api/v1/mms_mobile/machine";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.get(server_url + EndPoint);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn get_task_infor_data(Context context, String server_url,String Task_Id,String User_Id)
    {
        // Lấy thông tin task
        try {

            String EndPoint = "/api/v1/mms_mobile/detail";

            String bodyRequest = "{\n" +


                    " \"User_Id\": \""+User_Id+"\",\n" +
                    "  \"Progess\": \"\",\n" +
                    "  \"Task_Id\": \""+Task_Id+"\",\n" +
                    "  \"Image_List\": \"\",\n" +
                    "  \"Image_List_Delete\": \"\",\n" +
                    "  \"Note\": \"\"\n"+

                    "}";


            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn save_new_task_data(Context context,String Issue_Type,String Issue_User_Id,String Machine_Id,String Note ,String server_url)
    {
        // Thêm lỗi mới
        try {

            String EndPoint = "/api/v1/mms_mobile/add";

            String bodyRequest = "{\n" +
                    "        \"Issue_Type\": \""+Issue_Type+"\",\n" +
                    "         \"Issue_User_Id\": \""+Issue_User_Id+"\",\n" +
                    "         \"Machine_Id\": \""+Machine_Id+"\",\n" +
                    "        \"Note\": \""+Note+"\"\n" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn save_new_task_file_data(Context context,String Issue_Type,String Issue_User_Id,String Machine_Id,ArrayList<Uri> selectedFileUris,String Note ,String server_url)
    {
        // Thêm lỗi mới kèm file
        try {

            String EndPoint = "/api/v1/mms_mobile/file/add";

            String bodyRequest = "{\n" +
                    "        \"Issue_Type\": \""+Issue_Type+"\",\n" +
                    "         \"Issue_User_Id\": \""+Issue_User_Id+"\",\n" +
                    "         \"Machine_Id\": \""+Machine_Id+"\",\n" +
                    "        \"Note\": \""+Note+"\"\n" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.postFile(server_url + EndPoint, bodyRequest,selectedFileUris,context);

            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn pause_task_by_user(Context context,JSONArray list_pre_step,JSONArray list_post_step,String Task_Id,String Progess ,String Note,String Current_Situation,String Root_Cause,String Action_Taken,String Countermeasure, String server_url,String User_Id)
    {

        // Tạm dừng hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/pausetask";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Progess\": \""+Progess+"\"," +
                    "\"Task_Id\": \""+Task_Id+"\"," +
                    "\"Image_List\":\"\"," +
                    "\"Image_List_Delete\":\"\"," +
                    "\"Note\": \""+Note+"\"," +
                    "\"Current_Situation\": \""+Current_Situation+"\"," +
                    "\"Root_Cause\": \""+Root_Cause+"\"," +
                    "\"Action_Taken\": \""+Action_Taken+"\"," +
                    "\"Countermeasure\": \""+Countermeasure+"\"," +
                    "\"Premaintenance\":"+ list_pre_step +"," +
                    "\"Postemaintenance\":"+ list_post_step +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn pause_task_file_by_user(Context context,JSONArray list_pre_step,JSONArray list_post_step,String Task_Id,String Progess,String list_image_name,String list_image_delete,ArrayList<Uri> selectedFileUris,String Note,String Current_Situation,String Root_Cause,String Action_Taken,String Countermeasure,String server_url,String User_Id)
    {

        // Tạm dừng hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/file/pause";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Progess\": \""+Progess+"\"," +
                    "\"Task_Id\": \""+Task_Id+"\"," +
                    "\"Image_List\":\""+list_image_name+"\"," +
                    "\"Image_List_Delete\":\""+list_image_delete+"\"," +
                    "\"Note\": \""+Note+"\"," +
                    "\"Current_Situation\": \""+Current_Situation+"\"," +
                    "\"Root_Cause\": \""+Root_Cause+"\"," +
                    "\"Action_Taken\": \""+Action_Taken+"\"," +
                    "\"Countermeasure\": \""+Countermeasure+"\"," +
                    "\"Premaintenance\":"+ list_pre_step +"," +
                    "\"Postemaintenance\":"+ list_post_step +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.postFile(server_url + EndPoint, bodyRequest,selectedFileUris,context);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn save_request_material(Context context,String Request_Date_Unix,String WH_ID,String Note,List<JSONObject> list_material, String server_url,String User_Id, String Request_Purpose, String Machine_Id)
    {

        // Tạo yêu cầu xuất kho
        try {
            String finalUrl = server_url;
            if (finalUrl.contains("://")) {
                String protocol = finalUrl.split("://")[0];
                String addressWithPort = finalUrl.split("://")[1];
                if (addressWithPort.contains(":")) {
                    finalUrl = protocol + "://" + addressWithPort.split(":")[0];
                } else {
                    finalUrl = protocol + "://" + addressWithPort;
                }
            }
            if (finalUrl.endsWith("/")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
            finalUrl = finalUrl + ":3500/api/v1/WMS_FE/import";

            String bodyRequest = "{\n" +
                    "\"Type\": \"Add\", "+
                    "\"Trans_Code\": \"001\", "+
                    "\"Request_Id\": \"mobile\", "+
                    "\"Request_Note\": \""+Note+"\","+
                    "\"Request_Purpose\": \""+Request_Purpose+"\","+
                    "\"Machine_Id\": \""+Machine_Id+"\","+
                    "\"User_Id\": \""+User_Id+"\","+
                    "\"Request_Date_Unix\": "+ Request_Date_Unix +","+
                    "\"Wh_Id\": \""+WH_ID+"\","+
                    "\"AddMaterials\":"+ list_material +","+
                    "\"UpdateMaterials\": [], "+
                    "\"DeleteMaterials\":[]"+
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(finalUrl, bodyRequest);
            return apiReturn;
        }
        catch (Exception e)
        {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn update_material_status(Context context,List<JSONObject> list_task,String server_url,String User_Id)
    {

        // Tạm dừng hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/updatematerial";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Material_Export\": \"1\"," +
                    "\"DataListTask\":"+ list_task +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn add_material_by_task(Context context,String Task_Id,List<JSONObject> list_material,String server_url,String User_Id)
    {

        // Thêm vật tư hạng mục cần bảo trì
        try {

            String EndPoint = "/api/v1/mms_mobile/addmaterial";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"Task_Id\": \""+Task_Id+"\"," +
                    "\"DataListMaterial\":"+ list_material +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }

    }

    public static APIReturn save_app_rove_task (Context context,List<JSONObject> list_task,String Approve_value, String server_url,String User_Id)
    {
        // Lấy danh sách hạng mục đã hoàn thành
        try {

            String EndPoint = "/api/v1/mms_mobile/approve";

            String bodyRequest = "{\n" +

                    "\"User_Id\": \""+User_Id+"\"," +
                    "\"DataListTask\":"+ list_task +","+
                    "\"Approve_value\": \""+Approve_value+"\"" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);
            return apiReturn;
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }



    public static APIReturn get_version_app(String System_Name,String Screen_Id, String server_url)
    {
        // Lấy thông tin cập nhật app
        try {

            String EndPoint = "/api/v1/mms_mobile/versioninfor";

            String bodyRequest = "{\n" +

                    "        \"System_Name\": \""+System_Name+"\",\n" +
                    "         \"Screen_Id\": \""+Screen_Id+"\"\n" +
                    "}";

            HttpClient httpClient = new HttpClient();
            String response = null;
            HttpClient.APIReturn apiReturn = httpClient.post(server_url + EndPoint, bodyRequest);

            return apiReturn;
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }


    public static String getNextDate(String dateStr) {
        try {
            // Định dạng đầu vào
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false); // Không cho phép ngày không hợp lệ

            // Chuyển chuỗi thành Date
            Date date = sdf.parse(dateStr);

            // Tạo Calendar instance
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);

            // Thêm 1 ngày
            calendar.add(Calendar.DATE, 1);

            // Chuyển lại thành chuỗi và trả về
            return sdf.format(calendar.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            return null; // Hoặc xử lý lỗi theo cách bạn muốn
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static APIReturn getDataTable(Context context, String serverDynamicUrl, String SchemaData, String SchemaCore, String dsaCondition)
    {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");

        try {
            String baseUrl = serverDynamicUrl;
            if (baseUrl != null && baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String endPoint = "/api/dynamics";

            JSONObject conditionObj = new JSONObject();
            conditionObj.put("dsa", dsaCondition);
            conditionObj.put("Offset", 0);
            conditionObj.put("Limit", 100);
            conditionObj.put("Schema_Core", SchemaCore);
            conditionObj.put("Schema_MMS", SchemaData);

            JSONObject bodyObj = new JSONObject();
            bodyObj.put("ServiceName", "mes_mms");
            bodyObj.put("ActionName", "MES_MMS_GET_ALL_REPORT_PROBLEM_SEARCH");
            bodyObj.put("Condition", conditionObj);

            String bodyRequest = bodyObj.toString();
            HttpClient httpClient = new HttpClient();

            ColorConsole.d("getDataTable Body Sent", bodyRequest);

            return httpClient.post(baseUrl + endPoint, bodyRequest);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static LocalDateTime convertStringToLocalDateTime(String dateString)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(dateString, formatter);
    }

    private static String ConvertUnixTimeStampToDateTime(String input_time)
    {
        String T1_out = "";
        if(!input_time.isEmpty() && !input_time.equals("") && !input_time.equals("0") && !input_time.equals("null"))
        {
            long T1 = Long.valueOf(input_time);
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(T1), ZoneId.of("UTC"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            T1_out = dateTime.format(formatter);
        }
        return T1_out;
    }

    //TODO: function call api
    public static APIReturn callDynamics(Context context, String server_url, String ServiceName, String ActionName, JSONObject Condition) {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");

        try {
            String finalUrl = server_url;
            if (finalUrl.endsWith("/")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
            if (!finalUrl.toLowerCase().contains("/api/dynamics")) {
                finalUrl += "/api/dynamics";
            }

            JSONObject bodyObj = new JSONObject();
            bodyObj.put("ServiceName", ServiceName);
            bodyObj.put("ActionName", ActionName);
            bodyObj.put("Condition", Condition);

            String bodyRequest = bodyObj.toString();

            ColorConsole.d("Calling API: " + finalUrl);
            ColorConsole.d("Body: " + bodyRequest);

            HttpClient httpClient = new HttpClient();
            return httpClient.post(finalUrl, bodyRequest);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    public static APIReturn callPostRaw(Context context, String url, JSONObject payload){
        try{
            ColorConsole.d("Calling API: " + url);
            ColorConsole.d("Body: " + payload.toString());
            HttpClient httpClient = new HttpClient();
            return httpClient.post(url, payload.toString());
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }


    //TODO: Get machine detail list
    public static APIReturn getMachineDetailList(Context context, String server_url, String Schema_Core, String Schema_MMS, String Schema_Data, String Where_Clause) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);
            conditionObj.put("Schema_Data", Schema_Data);
            conditionObj.put("where", Where_Clause);

            return callDynamics(context, server_url, "mes_mms", "COR_MA_00_GET_MACHINE_ID_01", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: Get work order count
    public static APIReturn getWorkOrderCount(Context context, String server_url, String Schema_MMS, String Schema_Core, String condition) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("dsa", condition);
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);

            return callDynamics(context, server_url, "mes_mms", "MES_MMS_GET_ALL_WORK_ORDER_COUNT", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }





    //TODO: Work Order
    //TODO: lấy mã W/O mới nhất trong ngày
    public static APIReturn getLastWoCodeToday(Context context, String server_url, String Schema_MMS, String today){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_MMS", Schema_MMS);
            conditionObj.put("TODAY", "'" + today + "'");
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_GET_LAST_WO_CODE_TODAY", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: lấy danh sách máy - OK
    public static APIReturn getMachineIdList(Context context, String server_url, String Schema_Core, String Schema_MMS, String Schema_Data){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);
            conditionObj.put("Schema_Data", Schema_Data);
            conditionObj.put("where", "1=1");
            return callDynamics(context, server_url, "mes_mms","COR_MA_00_GET_MACHINE_ID_01", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: lấy danh sách người yêu cầu
    public static APIReturn getUserList(Context context, String server_url, String Schema_Core){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Core", Schema_Core);
            return callDynamics(context, server_url, "mes_mms","MES_MMS_GET_USER_NAME_LIST_WO", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: lấy thông tin tất cả workorder
    public static APIReturn getAllWorkOrder(Context context, String server_url, String Schema_MMS, String Schema_Core, String dsaCondition, int offset, int limit){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("dsa", dsaCondition);
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);
            conditionObj.put("Offset", offset);
            conditionObj.put("Limit", limit);
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_GET_ALL_WORK_ORDER", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: lấy thông tin tất cả workorder theo mã máy
    public static APIReturn getAllWorkOrderByMachineId(Context context, String server_url, String Schema_MMS, String Schema_Core, String machineId, int offset, int limit){
        try{
            JSONObject conditionObj = new JSONObject();
            String normalizedMachineId = normalizeMachineId(machineId);
            conditionObj.put("machine_id", normalizedMachineId);
            conditionObj.put("Offset", offset);
            conditionObj.put("Limit", limit);
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);
            Log.d("WO_WORK_ORDER", "getAllWorkOrderByMachineId condition=" + conditionObj);
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_GET_ALL_WORK_ORDER_APP", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    private static String normalizeMachineId(String machineId) {
        if (machineId == null) return "";
        String value = machineId.trim();
        if (value.isEmpty()) return "";

        value = value.replace("\r", "").replace("\n", "").trim();
        if (value.contains(" - ")) {
            value = value.split(" - ")[0].trim();
        }
        if (value.contains(":")) {
            String[] parts = value.split(":");
            value = parts[parts.length - 1].trim();
        }
        return value;
    }


    //TODO: api insert work order
    //TODO: thêm Work Order
    public static APIReturn addMtWorkOrder(Context context, String server_url, JSONObject data){
        try{
            return callDynamics(context, server_url, "mes_mms",  "MES_MMS_ADD_MT_WORK_ORDER", data);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: cập nhật Work Order
    public static APIReturn updateMtWorkOrder(Context context, String server_url, JSONObject data){
        try{
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_UPDATE_MT_WORK_ORDER", data);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: xóa Work Order
    public static APIReturn deleteMtWorkOrder(Context context, String server_url, JSONObject data){
        try{
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_DELETE_MT_WORK_ORDER", data);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: cập nhật trạng thái máy sau khi thêm 1 work order mới
//    public static APIReturn updateMachineStatus(Context context, String server_url, String Schema_Core, String Schema_MMS, String Schema_Data, String machineId){
    public static APIReturn updateMachineStatus(Context context, String server_url, String Schema_Core, String machineId, int feStatus){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Core", Schema_Core);
//            conditionObj.put("Schema_MMS", Schema_MMS);
//            conditionObj.put("Schema_Data", Schema_Data);
//            conditionObj.put("machineId", "'" + machineId + "'");
            conditionObj.put("machineId", machineId);
            conditionObj.put("feStatus", feStatus);
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_UPDATE_MACHINE_STATUS", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: sau khi thêm work order mới thì đồng thời thêm task mới
    public static APIReturn addMtTask(Context context, String server_url, JSONObject data){
        try {
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_ADD_MT_TASK", data);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: sau khi thêm task mới xong thì gửi mail
    public static APIReturn sendMailAfterAssign(Context context, String serverurl, JSONObject payload){
        try{
            String finalBaseUrl = serverurl;
            if(finalBaseUrl.contains("://")){
                String protocol = finalBaseUrl.split("://")[0];
                String addressWithPort = finalBaseUrl.split("://")[1];
                if(addressWithPort.contains(":")){
                    finalBaseUrl = protocol + "://" + addressWithPort.split(":")[0];
                }
            }
            String mailUrl = finalBaseUrl + ":9103/api/v1/sendMail/after-assign";
            return callPostRaw(context, mailUrl, payload);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: gửi mail notification cho Work Order
    public static APIReturn sendWoMailNotification(Context context, String serverurl, JSONObject payload){
        try{
            String finalBaseUrl = serverurl;
            if(finalBaseUrl.contains("://")){
                String protocol = finalBaseUrl.split("://")[0];
                String addressWithPort = finalBaseUrl.split("://")[1];
                if(addressWithPort.contains(":")){
                    finalBaseUrl = protocol + "://" + addressWithPort.split(":")[0];
                }
            }
            String mailUrl = finalBaseUrl + ":9101/api/v1/sendMail/wo-notification";
            return callPostRaw(context, mailUrl, payload);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: đếm số task
    public static APIReturn countTaskProblem01(Context context, String server_url, String Schema_MMS){
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_MMS", Schema_MMS);
            return callDynamics(context, server_url, "mes_mms", "MES_MMS_COUNT_TASK_PROBLEM_01", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: lấy thông tin người phụ trách máy
    public static APIReturn getPersonInCharge(Context context, String server_url, String machineId, String Schema_Core, String Schema_MMS, String Schema_Data ){
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("MachineId", machineId);
            conditionObj.put("Schema_Core", Schema_Core);
            conditionObj.put("Schema_MMS", Schema_MMS);
            conditionObj.put("Schema_Data", Schema_Data);
            return callDynamics(context, server_url, "mes_mms", "GET_PERSONAL_IN_CHARGE_BY MACHINE_ID", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception PIC: " + e.getMessage(), null);
        }
    }

    //TODO: lấy thông tin user login
    public static APIReturn getUserInfo(Context context, String server_url, String userId, String Schema_Core){
        try{
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Id", userId);
            conditionObj.put("Schema_Core", Schema_Core);

            return callDynamics(context, server_url, "mes_mms", "MMS_GET_USER_INFO", conditionObj);
        }catch (Exception e){
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    //TODO: api return
    public static class APIReturn
    {
        public int code;
        public String message;
        public List<JSONObject> data;

        public APIReturn(int code, String message, List<JSONObject> data)
        {
            this.code = code;
            this.message = message;
            this.data = data;
        }
    }

    // =========================================================================
    // TODO: MODULE BẢO DƯỠNG ĐỊNH KỲ (PREVENTIVE MAINTENANCE)
    // =========================================================================

    // TODO: Lấy danh sách kế hoạch bảo dưỡng định kỳ (ActionName: MMS_GET_MAINTAIN_PLAN_225)
    public static APIReturn getMaintenancePlanList(Context context, String server_url, String schemaCore, String schemaMms,
                                                   String whereTask, String whereMachine, long fromUnix, long toUnix) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("WhereTask", whereTask);
            conditionObj.put("WhereMachine", whereMachine);
            conditionObj.put("WhereProcess", "1=1");
            conditionObj.put("WhereFeTeam", "1=1");
            conditionObj.put("FROM_DATE", fromUnix);
            conditionObj.put("TO_DATE", toUnix);
            conditionObj.put("Schema_Core", schemaCore);
            conditionObj.put("Schema_Mms", schemaMms);

            return callDynamics(context, server_url, "mes_mms", "MMS_GET_MAINTAIN_PLAN_225", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Lấy danh sách hạng mục kiểm tra - BẢNG CHA (ActionName: GET_PARRENT_225_test)
    public static APIReturn getParentMaintenanceItems(Context context, String server_url, String schemaCore, String schemaMms,
                                                      String categoryId, String taskId, String machineId) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Core", schemaCore);
            conditionObj.put("Schema_Mms", schemaMms);
            conditionObj.put("categoryId", categoryId);
            conditionObj.put("taskId", taskId);
            conditionObj.put("machine_id", machineId);

            return callDynamics(context, server_url, "mes_mms", "GET_PARRENT_225_test", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Lấy danh sách task bảo dưỡng định kỳ chi tiết (ActionName: MMS_GET_MAINTAIN_TASK_225)
    public static APIReturn getMaintainTaskList(Context context, String server_url, String schemaMms, String schemaCore,
                                                String whereClause, int limit, int offset, String feTeamId) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_MMS", schemaMms);
            conditionObj.put("Schema_Core", schemaCore);
            conditionObj.put("where", whereClause);
            conditionObj.put("limit", limit);
            conditionObj.put("offset", offset);
            conditionObj.put("feTeamId", feTeamId == null ? "" : feTeamId.trim());

            return callDynamics(context, server_url, "mes_mms", "MMS_GET_MAINTAIN_TASK_225", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Lấy danh sách hạng mục kiểm tra con - BẢNG CON (ActionName: GET_CHILD_225_test)
    public static APIReturn getChildMaintenanceItems(Context context, String server_url, String schemaMms,
                                                     String parentCheckId, String taskId) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Mms", schemaMms);
            conditionObj.put("parrent", parentCheckId);
            conditionObj.put("taskId", taskId);

            return callDynamics(context, server_url, "mes_mms", "GET_CHILD_225_test", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Lưu kết quả chi tiết của từng hạng mục kiểm tra (Duyệt vòng lặp gửi từng item) (ActionName: MMS_ADD_BEFORE_DETAIL_CHECK_PREVENT_225)
    public static APIReturn saveMaintenanceItemDetail(Context context, String server_url, String schemaMms,
                                                      String taskId, String checkId, String checkValue,
                                                      String checkValue2, String historyJsonArray, String comment,
                                                      String imageList, String imageListDelete) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("schemaMMS", schemaMms);
            conditionObj.put("Task_Id", taskId);
            conditionObj.put("Check_Id", checkId);
            conditionObj.put("Check_Value", checkValue);
            conditionObj.put("Check_Value2", checkValue2);
            conditionObj.put("History", historyJsonArray); // Dữ liệu chuỗi JSON Array lịch sử đã append
            conditionObj.put("Comment", comment);
            conditionObj.put("Image_List", imageList == null ? "" : imageList);
            conditionObj.put("Image_List_Delete", imageListDelete == null ? "" : imageListDelete);

            return callDynamics(context, server_url, "mes_mms", "MMS_ADD_BEFORE_DETAIL_CHECK_PREVENT_225", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Cập nhật trạng thái tổng của Task (Endpoint: /api/v1/mms/task (Gửi raw payload array dạng POST gốc))
    public static APIReturn updateOverallTaskStatus(Context context, String server_url, String taskId, String machineId,
                                                    long taskDateUnix, String maintainerId, String categoryId, String status) {
        try {
            String finalUrl = server_url;
            if (finalUrl.endsWith("/")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
            finalUrl += "/api/v1/mms/task";

            // Tạo Object cập nhật
            JSONObject taskUpdateObj = new JSONObject();
            taskUpdateObj.put("Task_Id", taskId);
            taskUpdateObj.put("Machine_Id", machineId);
            // Server validation expects Task_Date_Unix as string (not numeric)
            taskUpdateObj.put("Task_Date_Unix", String.valueOf(taskDateUnix));
            taskUpdateObj.put("Task_Type", "1");
            taskUpdateObj.put("Maintainer_Id", maintainerId);
            taskUpdateObj.put("Category_Id", categoryId);
            taskUpdateObj.put("Status", status); // "2" (OK) hoặc "3" (NG)

            JSONArray updateTasksArray = new JSONArray();
            updateTasksArray.put(taskUpdateObj);

            // Bọc payload gốc theo cấu trúc tài liệu
            JSONObject payload = new JSONObject();
            payload.put("addTasks", new JSONArray());
            payload.put("updateTasks", updateTasksArray);
            payload.put("deleteTasks", new JSONArray());

            return callPostRaw(context, finalUrl, payload);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Xem lịch sử của mục kiểm tra con (ActionName: GET_HISTORY_CHILD)
    public static APIReturn getHistoryChildItems(Context context, String server_url, String schemaMms,
                                                 String checkId, String taskId) {
        try {
            JSONObject conditionObj = new JSONObject();
            conditionObj.put("Schema_Mms", schemaMms);
            conditionObj.put("checkId", checkId);
            conditionObj.put("task_id", taskId);

            return callDynamics(context, server_url, "mes_mms", "GET_HISTORY_CHILD", conditionObj);
        } catch (Exception e) {
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }

    // TODO: Upload hình ảnh cho từng hạng mục kiểm tra bảo dưỡng (Endpoint: :9101/api/v1/mms_file-img/uploadImagesComponentForPreventive)
    public static APIReturn uploadPreventiveImage(Context context, String server_url, String taskId, String checkId, Uri fileUri) {
        PreferenceHandler handler = new PreferenceHandler(context);
        token = handler.getString("api_key");
        try {
            String finalUrl = server_url;
            if (finalUrl.contains("://")) {
                String protocol = finalUrl.split("://")[0];
                String addressWithPort = finalUrl.split("://")[1];
                if (addressWithPort.contains(":")) {
                    finalUrl = protocol + "://" + addressWithPort.split(":")[0];
                } else {
                    finalUrl = protocol + "://" + addressWithPort;
                }
            }
            if (finalUrl.endsWith("/")) {
                finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
            }
            // Trỏ đúng port và endpoint upload hình ảnh bảo dưỡng
            finalUrl = finalUrl + ":9101/api/v1/mms_file-img/uploadImagesComponentForPreventive";

            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            // Send both camelCase and underscore variants to satisfy different server expectations
            String tId = taskId == null ? "" : taskId;
            String cId = checkId == null ? "" : checkId;
            builder.addFormDataPart("taskId", tId);
            builder.addFormDataPart("checkId", cId);
            builder.addFormDataPart("Task_Id", tId);
            builder.addFormDataPart("Check_Id", cId);

            String fileName = getFileNameFromUri(fileUri, context);
            byte[] fileBytes = readFileFromUri(fileUri, context);

            // Debug logs to inspect what is being sent
            Log.d("UPLOAD_IMAGE_DBG", "uploadPreventiveImage -> finalUrl=" + finalUrl);
            Log.d("UPLOAD_IMAGE_DBG", "uploadPreventiveImage -> Task_Id=" + tId + ", Check_Id=" + cId);
            Log.d("UPLOAD_IMAGE_DBG", "uploadPreventiveImage -> fileName=" + fileName + ", fileBytes=" + (fileBytes == null ? 0 : fileBytes.length));
            Log.d("UPLOAD_IMAGE_DBG", "uploadPreventiveImage -> tokenPresent=" + (token != null && !token.trim().isEmpty()));

            if (fileBytes != null) {
                RequestBody fileBody = RequestBody.create(MediaType.parse(getMimeType(fileName)), fileBytes);
                // Send both 'file' and 'files' form names to maximize compatibility with backend
                builder.addFormDataPart("file", fileName, fileBody);
                builder.addFormDataPart("files", fileName, fileBody);
            }

            // Some backend variants expect a 'param' form field — include empty param to be safe
            builder.addFormDataPart("param", "");

            RequestBody requestBody = builder.build();
            Request request = new Request.Builder()
                    .url(finalUrl)
                    .header("Authorization", "Bearer " + token)
                    .post(requestBody)
                    .build();

            return executeRequest(request);
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
            return new APIReturn(400, "Exception|| " + e.getMessage(), null);
        }
    }
}
