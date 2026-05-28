package com.mkac.meikomms.common;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.mkac.meikomms.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class LanguageAPIUtils
{
    private static final Map<String, JSONObject> languageMap = new HashMap<>();
    private static final Map<String, JSONObject> builtInLanguageMap = new HashMap<>();
    private static String languageCode = "vi";
    public static final String FORM_ID = "DPT_00";
    private static boolean isLoadedFromApi = false;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    public static String getLanguageCode() {
        return languageCode;
    }

    public static String getLanguageName() {
        switch (languageCode) {
            case "vi":
                return "Tiếng Việt";
            case "en":
                return "English";
            case "ja":
                return "日本";
            case "ch":
                return "Chinese";
        }
        return "unknown_language";
    }

    public static String i18n(String text)
    {
        JSONObject builtInJson = getBuiltInLanguageJson(languageCode);
        JSONObject langJson = languageMap.get(languageCode);
        if (langJson != null) {
            String translated = langJson.optString(text);
            if (!translated.isEmpty()) {
                // If API/cached JSON returns the same source text, prefer built-in translation when available.
                if (translated.equals(text) && builtInJson != null) {
                    String builtInTranslated = builtInJson.optString(text);
                    if (!builtInTranslated.isEmpty() && !builtInTranslated.equals(text)) {
                        return builtInTranslated;
                    }
                }
                return translated;
            }
        }

        if (builtInJson != null) {
            String fallbackTranslated = builtInJson.optString(text);
            if (!fallbackTranslated.isEmpty()) return fallbackTranslated;
        }

        return text;
    }

    public static String i18n(Context context, @StringRes int resId) {
        return i18n(context.getString(resId));
    }

    private static JSONObject getBuiltInLanguageJson(String langCode) {
        JSONObject cached = builtInLanguageMap.get(langCode);
        if (cached != null) return cached;

        String jsonString;
        switch (langCode) {
            case "en":
                jsonString = english;
                break;
            case "ja":
                jsonString = japanese;
                break;
            case "ch":
                jsonString = chinese;
                break;
            case "vi":
            default:
                jsonString = vietnamese;
                break;
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            builtInLanguageMap.put(langCode, json);
            return json;
        } catch (JSONException ignored) {
            return null;
        }
    }

    public static void setLanguageCode(String outlanguageCode)
    {
        languageCode = outlanguageCode;
    }


    private static void fetchLanguagesFromAPI(Context context) {
        isLoadedFromApi = true;
        ConfigManager configManager = new ConfigManager(context);
        String baseUrl = configManager.getProperty("server_url"); // lấy URL từ config
        String apiPath = "/api/v1/mms_mobile/language?keywords=MES_MMS_MOBILE_&removeKeywordFieldInResult=true";
        String languageApiUrl = baseUrl + apiPath;
        executorService.submit(() -> {
            try {
                URL url = new URL(languageApiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject root = new JSONObject(response.toString());
                    JSONObject data = root.getJSONObject("data");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();

                        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                            String lang = it.next();
                            try {
                                JSONObject langJson = data.getJSONObject(lang);
                                languageMap.put(lang, langJson);
                                editor.putString("lang_json_" + lang, langJson.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        editor.apply();
                    });

                } else {
                    fallbackToMainThread();
                }
            } catch (Exception e) {
                e.printStackTrace();
                fallbackToMainThread();
            }
        });
    }

    private static void fallbackToMainThread() {
        new Handler(Looper.getMainLooper()).post(LanguageAPIUtils::fallbackToBuiltInJson);
    }

    public static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        int position = prefs.getInt("languageSettingPosition", 2);

        switch (position) {
            case 0: languageCode = "ja"; break;
            case 1: languageCode = "en"; break;
            case 2: languageCode = "vi"; break;
            case 3: languageCode = "ch"; break;
            default: languageCode = "vi";
        }

        // Load từ cache
        for (String lang : new String[]{"vi", "en", "ja", "ch"}) {
            if (!languageMap.containsKey(lang)) {
                String cachedJson = prefs.getString("lang_json_" + lang, null);
                if (cachedJson != null) {
                    try {
                        languageMap.put(lang, new JSONObject(cachedJson));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Nếu thiếu ngôn ngữ hoặc chưa load API → gọi API
        if (!isLoadedFromApi) {
            fetchLanguagesFromAPI(context);
        }

        // Fallback nếu vẫn thiếu ngôn ngữ
        if (!languageMap.containsKey("vi")) try { languageMap.put("vi", new JSONObject(vietnamese)); } catch (JSONException ignored) {}
        if (!languageMap.containsKey("en")) try { languageMap.put("en", new JSONObject(english)); } catch (JSONException ignored) {}
        if (!languageMap.containsKey("ja")) try { languageMap.put("ja", new JSONObject(japanese)); } catch (JSONException ignored) {}
        if (!languageMap.containsKey("ch")) try { languageMap.put("ch", new JSONObject(chinese)); } catch (JSONException ignored) {}

    }

    private static void fallbackToBuiltInJson() {
        try { if (!languageMap.containsKey("vi")) languageMap.put("vi", new JSONObject(vietnamese)); } catch (JSONException ignored) {}
        try { if (!languageMap.containsKey("en")) languageMap.put("en", new JSONObject(english)); } catch (JSONException ignored) {}
        try { if (!languageMap.containsKey("ja")) languageMap.put("ja", new JSONObject(japanese)); } catch (JSONException ignored) {}
        try { if (!languageMap.containsKey("ch")) languageMap.put("ch", new JSONObject(chinese)); } catch (JSONException ignored) {}
    }

    public static void reloadLanguage(Context context) {
        isLoadedFromApi = false;
        languageMap.clear(); // Xóa dữ liệu cũ để nạp lại
        init(context);       // Gọi lại toàn bộ logic khởi tạo
    }

    private static final String english =
            "{\n" +
                    "  \"All\": \"All\",\n" +
                    "  \"Periodic Maintenance Task List\": \"Periodic Maintenance Task List\",\n" +
                    "  \"MMS System FE Subsystem\": \"MMS System FE Subsystem\",\n" +
                    "  \"Pending\": \"Pending\",\n" +
                    "  \"Category Name\": \"Category Name\",\n" +
                    "  \"Person in charge\": \"Person in charge\",\n" +
                    "  \"Execute task\": \"Execute task\",\n" +
                    "  \"Plan\": \"Plan\",\n" +
                    "  \"Done\": \"Done\",\n" +
                    "  \"Proceed with inspection\": \"Proceed with inspection\",\n" +
                    "  \"Check item name\": \"Check item name\",\n" +
                    "  \"Standard / Range\": \"Standard / Range\",\n" +
                    "  \"Display\": \"Display\",\n" +
                    "  \"Actual\": \"Actual\",\n" +
                    "  \"Enter a note or cause if any...\": \"Enter a note or cause if any...\",\n" +
                    "  \"W/O code\": \"W/O code\",\n" +
                    "  \"Work type\": \"Work type\",\n" +
                    "  \"Machine Code\": \"Machine Code\",\n" +
                    "  \"Quantity\": \"Quantity\",\n" +
                    "  \"Work Order\": \"Work Order\",\n" +
                    "  \"Scan\": \"Scan\",\n" +
                    "  \"Preventive Maintenance Plan (Tablet)\": \"Preventive Maintenance Plan (Tablet)\",\n" +
                    "  \"Search by name or machine code...\": \"Search by name or machine code...\",\n" +
                    "  \"W/O Management\": \"W/O Management Adn Maintenance\",\n" +
                    "  \"Select function\": \"Select function\",\n" +
                    "  \"Create a work order or enter work order and maintenance data\": \"Create a work order or enter work order and maintenance data\",\n" +
                    "  \"Add Work Order\": \"Add Work Order and Maintenance\",\n" +
                    "  \"ADD Work Order\": \"Add Work Order\",\n" +
                    "  \"Create a new work order\": \"Create a new work order\",\n" +
                    "  \"Enter Work Order Data\": \"Enter Work Order Data And Maintenance\",\n" +
                    "  \"Manage work orders and perform maintenance\": \"Manage work orders and perform maintenance\",\n" +
                    "  \"Language\": \"Language\",\n" +
                    "  \"Navigation to Login screen failed!\": \"Navigation to Login screen failed!\",\n" +
                    "  \"Missing Server URL configuration, cannot check for updates\": \"Missing Server URL configuration, cannot check for updates\",\n" +
                    "  \"Checking version on the system...\": \"Checking version on the system...\",\n" +
                    "  \"System found version\": \"System found version\",\n" +
                    "  \"Current version on Tablet is\": \"Current version on Tablet is\",\n" +
                    "  \"Do you want to proceed with automatic upgrade now?\": \"Do you want to proceed with automatic upgrade now?\",\n" +
                    "  \"New version available!\": \"New version available!\",\n" +
                    "  \"Update now\": \"Update now\",\n" +
                    "  \"Later\": \"Later\",\n" +
                    "  \"The application is at the latest version\": \"The application is at the latest version\",\n" +
                    "  \"Server responded with error code\": \"Server responded with error code\",\n" +
                    "  \"Cannot connect to check version\": \"Cannot connect to check version\",\n" +
                    "  \"Downloading update\": \"Downloading update\",\n" +
                    "  \"Please maintain a stable network connection...\": \"Please maintain a stable network connection...\",\n" +
                    "  \"Server response code error\": \"Server response code error\",\n" +
                    "  \"Failed to download update\": \"Failed to download update\",\n" +
                    "  \"Please grant permission to install apps from unknown sources!\": \"Please grant permission to install apps from unknown sources!\",\n" +
                    "  \"No attachment file to download\": \"No attachment file to download\",\n" +
                    "  \"Select attachment document\": \"Select attachment document\",\n" +
                    "  \"Please select a file before uploading\": \"Please select a file before uploading\",\n" +
                    "  \"Work Order code not found for upload\": \"Work Order code not found for upload\",\n" +
                    "  \"Uploading document...\": \"Uploading document...\",\n" +
                    "  \"Document uploaded successfully\": \"Document uploaded successfully\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"Uploaded successfully but failed to get file name\",\n" +
                    "  \"No response from server\": \"No response from server\",\n" +
                    "  \"Upload failed\": \"Upload failed\",\n" +
                    "  \"User login information not found\": \"User login information not found\",\n" +
                    "  \"No materials available to create warehouse request\": \"No materials available to create warehouse request\",\n" +
                    "  \"Error parsing material list\": \"Error parsing material list\",\n" +
                    "  \"No valid materials for warehouse release\": \"No valid materials for warehouse release\",\n" +
                    "  \"Warehouse request for Work Order\": \"Warehouse request for Work Order\",\n" +
                    "  \"Creating warehouse release request...\": \"Creating warehouse release request...\",\n" +
                    "  \"Warehouse release request created successfully!\": \"Warehouse release request created successfully!\",\n" +
                    "  \"Enter data\": \"Enter data\",\n" +
                    "  \"Work Order TASK_ID not found\": \"Work Order TASK_ID not found\",\n" +
                    "  \"Saving...\": \"Saving...\",\n" +
                    "  \"Data saved successfully\": \"Data saved successfully\",\n" +
                    "  \"Save failed\": \"Save failed\",\n" +
                    "  \"Download file\": \"Download file\",\n" +
                    "  \"Downloading attachment...\": \"Downloading attachment...\",\n" +
                    "  \"Starting file download...\": \"Starting file download...\",\n" +
                    "  \"Cannot initialize Download Manager\": \"Cannot initialize Download Manager\",\n" +
                    "  \"File download error\": \"File download error\",\n" +
                    "  \"Cannot open link\": \"Cannot open link\",\n" +
                    "  \"Machine\": \"Machine\",\n" +
                    "  \"Request date\": \"Request date\",\n" +
                    "  \"Process\": \"Process\",\n" +
                    "  \"Type\": \"Type\",\n" +
                    "  \"Creator\": \"Creator\",\n" +
                    "  \"Requester\": \"Requester\",\n" +
                    "  \"Elapsed days\": \"Elapsed days\",\n" +
                    "  \"Day\": \"Day\",\n" +
                    "  \"Request reason\": \"Request reason\",\n" +
                    "  \"Unable to read machine code\": \"Unable to read machine code\",\n" +
                    "  \"Machine not scanned\": \"Machine not scanned\",\n" +
                    "  \"Lost connection to maintenance API server\": \"Lost connection to maintenance API server\",\n" +
                    "  \"No maintenance plan found for machine\": \"No maintenance plan found for machine\",\n" +
                    "  \"Time\": \"Time\",\n" +
                    "  \"On hold\": \"On hold\",\n" +
                    "  \"Approve\": \"Approve\",\n" +
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"MAINTENANCE MANAGEMENT SYSTEM\",\n" +
                    "  \"Maintenance management system\": \"Maintenance management system\",\n" +
                    "  \"User name\": \"User name\",\n" +
                    "  \"Password\": \"Password\",\n" +
                    "  \"LOGOUT\": \"LOGOUT\",\n" +
                    "  \"Login_Singin\": \"LOGIN\",\n" +
                    "  \"CLOSE\": \"CLOSE\",\n" +
                    "  \"EXIT\": \"EXIT\",\n" +
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"YES\",\n" +
                    "  \"NO\": \"NO\",\n" +
                    "  \"CANCEL\": \"CANCEL\",\n" +
                    "  \"CONTINUE\": \"CONTINUE\",\n" +
                    "  \"BACK\": \"BACK\",\n" +
                    "  \"STOP\": \"STOP\",\n" +
                    "  \"SAVE\": \"SAVE\",\n" +
                    "  \"DELETE\": \"DELETE\",\n" +
                    "  \"Agree\": \"Agree\",\n" +
                    "  \"Deny\": \"Deny\",\n" +
                    "  \"Select\": \"Select\",\n" +
                    "  \"Enter work order data and maintenance\": \"Enter work order data and maintenance\",\n" +
                    "  \"Fill in the login information\": \"Fill in the login information\",\n" +
                    "  \"Login failed\": \"Login failed\",\n" +
                    "  \"Please check your username or password\": \"Please check your username or password\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Maintenance and Repair Schedule\",\n" +
                    "  \"Category Code\": \"Category Code\",\n" +
                    "  \"Status\": \"Status\",\n" +
                    "  \"Current situation\": \"Current situation\",\n" +
                    "  \"Root cause\": \"Root cause\",\n" +
                    "  \"Action taken\": \"Action taken\",\n" +
                    "  \"Countermeasure\": \"Countermeasure\",\n" +
                    "  \"Fill in the current situation information\": \"Fill in the current situation information\",\n" +
                    "  \"Fill in the reason information\": \"Fill in the reason information\",\n" +
                    "  \"Fill in the action taken information\": \"Fill in the action taken information\",\n" +
                    "  \"Fill in countermeasure information\": \"Fill in countermeasure information\",\n" +
                    "  \"Do you want to log out?\": \"Do you want to log out?\",\n" +
                    "  \"Product of Meiko Automation\": \"Product of Meiko Automation\",\n" +
                    "  \"Alarm setting\": \"Alarm setting\",\n" +
                    "  \"Deadline\": \"Deadline\",\n" +
                    "  \"Work Order Content\": \"Work Order Content\",\n" +
                    "  \"Nhập dữ liệu\": \"Enter Work Order Data\",\n" +
                    "  \"Maintenance\": \"Maintenance\",\n" +
                    "  \"Chưa quét mã máy\": \"Machine not scanned\",\n" +
                    "  \"Quét\": \"Scan\",\n" +
                    "  \"Không đọc được mã máy\": \"Unable to read machine code\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng\": \"Maintenance Plan\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng Định Kỳ (Tablet)\": \"Preventive Maintenance Plan (Tablet)\",\n" +
                    "  \"Tìm kiếm theo tên hoặc mã máy...\": \"Search by machine name or code...\",\n" +
                    "  \"Thời gian\": \"Time\",\n" +
                    "  \"Mã máy\": \"Machine code\",\n" +
                    "  \"Số lượng\": \"Quantity\",\n" +
                    "  \"Machine code: -- | Quantity: 0\": \"Machine code: -- | Quantity: 0\",\n" +
                    "  \"Nhập dữ liệu Work Order\": \"Enter Work Order Data\",\n" +
                    "  \"1.Thông tin\": \"1. Info\",\n" +
                    "  \"2.Vật tư\": \"2. Materials\",\n" +
                    "  \"Thời gian phát sinh:\": \"Occurrence time:\",\n" +
                    "  \"Thời gian bắt đầu thực hiện:\": \"Start time:\",\n" +
                    "  \"Thời gian kết thúc thực hiện:\": \"End time:\",\n" +
                    "  \"Hiện trạng:\": \"Current status:\",\n" +
                    "  \"Phân loại nguyên nhân:\": \"Cause category:\",\n" +
                    "  \"Nguyên nhân:\": \"Cause:\",\n" +
                    "  \"Ảnh hưởng (Sản phẩm/Thiết bị):\": \"Impact (Product/Equipment):\",\n" +
                    "  \"Nội dung xử lý yêu cầu:\": \"Requested action:\",\n" +
                    "  \"Ghi chú:\": \"Note:\",\n" +
                    "  \"Trạng thái:\": \"Status:\",\n" +
                    "  \"Chọn file\": \"Choose file\",\n" +
                    "  \"Tải lên\": \"Upload\",\n" +
                    "  \"Tài liệu đính kèm:\": \"Attachment:\",\n" +
                    "  \"Chưa có tài liệu đính kèm\": \"No attachment yet\",\n" +
                    "  \"Tạo yêu cầu xuất kho\": \"Create warehouse request\",\n" +
                    "  \"Không có vật tư đi kèm\": \"No materials attached\",\n" +
                    "  \"Chưa có vật tư đi kèm\": \"No materials attached\",\n" +
                    "  \"Đóng\": \"Close\",\n" +
                    "  \"Lưu\": \"Save\",\n" +
                    "  \"Hạng mục\": \"Category\",\n" +
                    "  \"Phụ trách\": \"Assignee\",\n" +
                    "  \"Thực hiện\": \"Executor\",\n" +
                    "  \"Dự kiến\": \"Planned\",\n" +
                    "  \"Xong\": \"Completed\",\n" +
                    "  \"Tạm hoãn\": \"On hold\",\n" +
                    "  \"Tiến hành kiểm tra\": \"Enter the results\",\n" +
                    "  \"Chưa làm\": \"Pending\",\n" +
                    "  \"Đã làm\": \"Done\",\n" +
                    "  \"Quá hạn\": \"Overdue\",\n" +
                    "  \"Phiên đăng nhập hết hạn\": \"Login session expired\",\n" +
                    "  \"Không tìm thấy dữ liệu cho máy\": \"No data found for machine\",\n" +
                    "  \"Loại hình\": \"Type\",\n" +
                    "  \"Đã lưu tạm thành công\": \"Temporarily saved successfully\",\n" +
                    "  \"hạng mục chi tiết\": \"detailed items\",\n" +
                    "  \"hạng mục\": \"items\",\n" +
                    "  \"và cập nhật trạng thái Task\": \"and updated the Task status\",\n" +
                    "  \"Không nhận được phản hồi kết luận từ Server\": \"No conclusion response from Server\",\n" +
                    "  \"Lưu chi tiết OK nhưng cập nhật trạng thái Task lỗi\": \"Detail save succeeded but Task status update failed\",\n" +
                    "  \"Hệ thống gặp lỗi khi lưu\": \"System error while saving\",\n" +
                    "  \"Đang tải lên hình ảnh...\": \"Uploading image...\",\n" +
                    "  \"Đã tải lên 1 ảnh cho\": \"Uploaded 1 image for\",\n" +
                    "  \"Lỗi tải ảnh\": \"Image upload failed\",\n" +
                    "  \"không nhận được dữ liệu ảnh từ server\": \"no image data returned from server\",\n" +
                    "  \"Đang tiến hành lưu dữ liệu bảo dưỡng...\": \"Saving maintenance data...\",\n" +
                    "  \"Lưu chi tiết thất bại tại mục con\": \"Failed to save child item detail\",\n" +
                    "  \"Lưu chi tiết thất bại tại mục cha\": \"Failed to save parent item detail\",\n" +
                    "  \"Lưu thông tin chi tiết thất bại. Vui lòng thử lại!\": \"Failed to save detail information. Please try again!\",\n" +
                    "  \"Đang tải lên tài liệu...\": \"Uploading attachment...\",\n" +
                    "  \"Tải lên tài liệu thành công\": \"Attachment uploaded successfully\",\n" +
                    "  \"Tải lên thành công nhưng không lấy được tên file\": \"Uploaded successfully but could not get the file name\",\n" +
                    "  \"Tải lên thất bại\": \"Upload failed\",\n" +
                    "  \"Không phản hồi từ máy chủ\": \"No response from server\",\n" +
                    "  \"Không phản hồi\": \"No response\",\n" +
                    "  \"Không tìm thấy mã Work Order để tải lên\": \"Work Order code not found for upload\",\n" +
                    "  \"Không có tệp đính kèm để tải về\": \"No attachment available to download\",\n" +
                    "  \"Chọn tài liệu đính kèm\": \"Choose attachment\",\n" +
                    "  \"Vui lòng chọn file trước khi tải lên\": \"Please choose a file before uploading\",\n" +
                    "  \"Không có vật tư nào để tạo yêu cầu xuất kho\": \"No materials available to create a warehouse request\",\n" +
                    "  \"Lỗi phân tích danh sách vật tư\": \"Failed to parse materials list\",\n" +
                    "  \"Không có vật tư hợp lệ để xuất kho\": \"No valid materials available for warehouse request\",\n" +
                    "  \"Yêu cầu xuất kho cho Work Order\": \"Warehouse request for Work Order\",\n" +
                    "  \"Máy\": \"Machine\",\n" +
                    "  \"Đang tạo yêu cầu xuất kho...\": \"Creating warehouse request...\",\n" +
                    "  \"Tạo yêu cầu xuất kho thành công!\": \"Warehouse request created successfully!\",\n" +
                    "  \"Lỗi tạo yêu cầu xuất kho\": \"Failed to create warehouse request\",\n" +
                    "  \"Thông tin gốc\": \"Info\",\n" +
                    "  \"Hạng mục gốc\": \"Parent item\",\n" +
                    "  \"Minimum\": \"Minimum\",\n" +
                    "  \"Maximum\": \"Maximum\",\n" +
                    "  \"Has\": \"Has\",\n" +
                    "  \"child items\": \"child items\",\n" +
                    "  \"Visual inspection\": \"Visual inspection\",\n" +
                    "  \"Enter a note or cause (if any)...\": \"Enter a note or cause (if any)...\",\n" +
                    "  \"Tối đa\": \"Maximum\",\n" +
                    "  \"Có\": \"Has\",\n" +
                    "  \"mục con\": \"child items\",\n" +
                    "  \"Kiểm tra ngoại quan\": \"Visual inspection\",\n" +
                    "  \"Tên hạng mục kiểm tra\": \"Check item name\",\n" +
                    "  \"Tiêu chuẩn / Dải đo\": \"Standard / Range\",\n" +
                    "  \"Display\": \"Display\",\n" +
                    "  \"Actual\": \"Actual\",\n" +
                    "  \"Nhập ghi chú hoặc nguyên nhân (nếu có)...\": \"Enter a note or cause (if any)...\",\n" +
                    "  \"Min\": \"Min\",\n" +
                    "  \"Max\": \"Max\",\n" +
                    "  \"Lịch sử\": \"History\",\n" +
                    "  \"Cập nhật\": \"Update\",\n" +
                    "  \"Nhập Dữ Liệu Bảo Dưỡng\": \"Enter Maintenance Data\",\n" +
                    "  \"Sửa\": \"Edit\",\n" +
                    "  \"In Progress\": \"In Progress\",\n" +
                    "  \"WO status\": \"WO status\",\n" +
                    "  \"MA status\": \"MA status\",\n" +
                    "  \"Add W/O\": \"Add W/O\",\n" +
                    "  \"Week\": \"Week\",\n" +
                    "  \"Month\": \"Month\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"You do not have permission to edit this Work Order or it is already Done\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"You do not have permission to delete this Work Order or it is already Done\",\n" +
                    "  \"Delete Work Order\": \"Delete Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"Do you confirm deleting WorkOrder %s?\",\n" +
                    "  \"Deleting Work Order...\": \"Deleting Work Order...\",\n" +
                    "  \"Delete successful\": \"Delete successful\",\n" +
                    "  \"Delete failed: %s\": \"Delete failed: %s\",\n" +
                    "  \"Delete error: %s\": \"Delete error: %s\",\n" +
                    "  \"Storage permission denied\": \"Storage permission denied\",\n" +
                    "  \"Unknown sources permission denied\": \"Unknown sources permission denied\",\n" +
                    "  \"Load completed\": \"Load completed\",\n" +
                    "  \"Please enter Machine and Requester\": \"Please enter Machine and Requester\",\n" +
                    "  \"Processing data...\": \"Processing data...\",\n" +
                    "  \"Add Work Order successful\": \"Add Work Order successful\",\n" +
                    "  \"Updating...\": \"Updating...\",\n" +
                    "  \"Update successful\": \"Update successful\",\n" +
                    "  \"Update failed: %s\": \"Update failed: %s\",\n" +
                    "  \"Update error: %s\": \"Update error: %s\",\n" +
                    "  \"Request reason is required\": \"Request reason is required\",\n" +
                    "  \"Update Work Order\": \"Update Work Order\",\n" +
                    "  \"Edit Work Order\": \"Edit Work Order\",\n" +
                    "  \"Details\": \"Details\",\n" +
                    "  \"Required fields missing\": \"Required fields missing\",\n" +
                    "  \"status update not sent\": \"status update not sent\",\n" +
                    "  \"System encountered an error while saving\": \"System encountered an error while saving\",\n" +
                    "  \"Item save error\": \"Item save error\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"Is there a device lock feature on the MES system?\",\n" +
                    "  \"W/O Code\": \"W/O Code\",\n" +
                    "  \"Request Date\": \"Request Date\",\n" +
                    "  \"Select an item\": \"Select an item\",\n" +
                    "  \"Enter process\": \"Enter process\",\n" +
                    "  \"Select type\": \"Select type\",\n" +
                    "  \"Passed Date\": \"Passed Date\",\n" +
                    "  \"Enter passed date\": \"Enter passed date\",\n" +
                    "  \"Request Reason\": \"Request Reason\",\n" +
                    "  \"Enter request reason\": \"Enter request reason\",\n" +
                    "  \"Enter work order content\": \"Enter work order content\",\n" +
                    "  \"MA Status\": \"MA Status\",\n" +
                    "  \"Completed\": \"Completed\",\n" +
                    "  \"Incomplete\": \"Incomplete\",\n" +
                    "  \"Canceled\": \"Canceled\",\n" +
                    "  \"Overdure\": \"Overdure\",\n" +
                    "  \"Machine Breakdown\": \"Machine Breakdown\",\n" +
                    "  \"Machine broken\": \"Machine broken\",\n" +
                    "  \"Preparing operation\": \"Preparing operation\",\n" +
                    "  \"Stop due to shortage\": \"Stop due to shortage\",\n" +
                    "  \"Stop by production plan\": \"Stop by production plan\",\n" +
                    "  \"Maintenance and repair\": \"Maintenance and repair\",\n" +
                    "  \"Add\": \"Add\",\n" +
                    "  \"Time arises\": \"Time arises\",\n" +
                    "  \"Overdue\": \"Overdue\",\n" +
                    "  \"Vật tư\": \"Material\",\n" +
                    "  \"Action\": \"Action\"\n" +
                    "}\n";


    private static final String vietnamese =
            "{\n" +
                    "  \"Model\": \"Mã thiết bị\",\n" +
                    "  \"Periodic Maintenance Task List\": \"DANH SÁCH HẠNG MỤC BẢO DƯỠNG ĐỊNH KỲ\",\n" +
                    "  \"MMS System FE Subsystem\": \"Hệ thống MMS phân hệ FE\",\n" +
                    "  \"Category Name\": \"Tên hạng mục\",\n" +
                    "  \"Person in charge\": \"Người phụ trách\",\n" +
                    "  \"Execute task\": \"Người thực hiện\",\n" +
                    "  \"Plan\": \"Kế hoạch\",\n" +
                    "  \"Done\": \"Hoàn thành\",\n" +
                    "  \"Proceed with inspection\": \"Tiến hành nhập kết quả\",\n" +
                    "  \"Check item name\": \"Tên hạng mục kiểm tra\",\n" +
                    "  \"Standard / Range\": \"Tiêu chuẩn / Dải đo\",\n" +
                    "  \"Display\": \"Display\",\n" +
                    "  \"Actual\": \"Actual\",\n" +
                    "  \"Enter a note or cause if any...\": \"Nhập ghi chú\",\n" +
                    "  \"W/O code\": \"Mã Work Order\",\n" +
                    "  \"Work type\": \"Loại hình công việc\",\n" +
                    "  \"Machine Code\": \"Mã máy\",\n" +
                    "  \"Quantity\": \"Số lượng\",\n" +
                    "  \"Work Order\": \"Work Order\",\n" +
                    "  \"Scan\": \"Quét\",\n" +
                    "  \"Preventive Maintenance Plan (Tablet)\": \"Kế Hoạch Bảo Dưỡng Định Kỳ (Tablet)\",\n" +
                    "  \"Search by name or machine code...\": \"Tìm kiếm theo tên hoặc mã máy...\",\n" +
                    "  \"W/O Management\": \"Quản lý Work Order và Hạng mục bảo dưỡng\",\n" +
                    "  \"Select function\": \"Chọn chức năng\",\n" +
                    "  \"Create a work order or enter work order and maintenance data\": \"Tạo một Work Order hoặc nhập dữ liệu Work Order và bảo dưỡng\",\n" +
                    "  \"Add Work Order\": \"Thêm Work Order\",\n" +
                    "  \"ADD Work Order\": \"Nhập thông tin Work Order\",\n" +
                    "  \"Create a new work order\": \"Tạo mới một Work Order\",\n" +
                    "  \"Enter Work Order Data\": \"Nhập kết quả Work Order và hạng mục bảo dưỡng\",\n" +
                    "  \"Manage work orders and perform maintenance\": \"Quản lý các Work Order và tiến hành bảo dưỡng\",\n" +
                    "  \"Language\": \"Ngôn ngữ\",\n" +
                    "  \"Navigation to Login screen failed!\": \"Hệ thống lỗi điều hướng màn hình Đăng nhập!\",\n" +
                    "  \"Missing Server URL configuration, cannot check for updates\": \"Thiếu cấu hình URL Server, không thể kiểm tra bản cập nhật\",\n" +
                    "  \"Checking version on the system...\": \"Đang kiểm tra phiên bản trên hệ thống...\",\n" +
                    "  \"System found version\": \"Hệ thống tìm thấy phiên bản\",\n" +
                    "  \"Current version on Tablet is\": \"Phiên bản hiện tại trên Tablet là\",\n" +
                    "  \"Do you want to proceed with automatic upgrade now?\": \"Bạn có muốn tiến hành nâng cấp tự động ngay không?\",\n" +
                    "  \"New version available!\": \"Phát hiện phiên bản mới!\",\n" +
                    "  \"Update now\": \"Cập nhật ngay\",\n" +
                    "  \"Later\": \"Để sau\",\n" +
                    "  \"The application is at the latest version\": \"Ứng dụng đang ở phiên bản mới nhất\",\n" +
                    "  \"Server responded with error code\": \"Server phản hồi mã lỗi\",\n" +
                    "  \"Cannot connect to check version\": \"Không thể kết nối kiểm tra phiên bản\",\n" +
                    "  \"Downloading update\": \"Đang tải bản cập nhật\",\n" +
                    "  \"Please maintain a stable network connection...\": \"Vui lòng giữ kết nối mạng ổn định...\",\n" +
                    "  \"Server response code error\": \"Mã phản hồi Server lỗi\",\n" +
                    "  \"Failed to download update\": \"Tải bản cập nhật thất bại\",\n" +
                    "  \"Please grant permission to install apps from unknown sources!\": \"Vui lòng cấp quyền cài đặt ứng dụng từ nguồn không xác định cho App!\",\n" +
                    "  \"No attachment file to download\": \"Không có tệp đính kèm để tải về\",\n" +
                    "  \"Select attachment document\": \"Chọn tài liệu đính kèm\",\n" +
                    "  \"Please select a file before uploading\": \"Vui lòng chọn file trước khi tải lên\",\n" +
                    "  \"Work Order code not found for upload\": \"Không tìm thấy mã Work Order để tải lên\",\n" +
                    "  \"Uploading document...\": \"Đang tải lên tài liệu...\",\n" +
                    "  \"Document uploaded successfully\": \"Tải lên tài liệu thành công\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"Tải lên thành công nhưng không lấy được tên file\",\n" +
                    "  \"No response from server\": \"Không phản hồi từ máy chủ\",\n" +
                    "  \"Upload failed\": \"Tải lên thất bại\",\n" +
                    "  \"User login information not found\": \"Không tìm thấy thông tin đăng nhập người dùng\",\n" +
                    "  \"No materials available to create warehouse request\": \"Không có vật tư nào để tạo yêu cầu xuất kho\",\n" +
                    "  \"Error parsing material list\": \"Lỗi phân tích danh sách vật tư\",\n" +
                    "  \"No valid materials for warehouse release\": \"Không có vật tư hợp lệ để xuất kho\",\n" +
                    "  \"Warehouse request for Work Order\": \"Yêu cầu xuất kho cho Work Order\",\n" +
                    "  \"Creating warehouse release request...\": \"Đang tạo yêu cầu xuất kho...\",\n" +
                    "  \"Warehouse release request created successfully!\": \"Tạo yêu cầu xuất kho thành công!\",\n" +
                    "  \"Enter data\": \"Nhập dữ liệu\",\n" +
                    "  \"Work Order TASK_ID not found\": \"Không tìm thấy TASK_ID của Work Order\",\n" +
                    "  \"Saving...\": \"Đang lưu...\",\n" +
                    "  \"Data saved successfully\": \"Lưu dữ liệu thành công\",\n" +
                    "  \"Save failed\": \"Lưu thất bại\",\n" +
                    "  \"Download file\": \"Tải file\",\n" +
                    "  \"Downloading attachment...\": \"Đang tải tài liệu đính kèm...\",\n" +
                    "  \"Starting file download...\": \"Bắt đầu tải xuống file...\",\n" +
                    "  \"Cannot initialize Download Manager\": \"Không thể khởi chạy Download Manager\",\n" +
                    "  \"File download error\": \"Lỗi tải file\",\n" +
                    "  \"Cannot open link\": \"Không thể mở liên kết\",\n" +
                    "  \"Machine\": \"Máy\",\n" +
                    "  \"Request date\": \"Ngày tạo\",\n" +
                    "  \"Process\": \"Công đoạn\",\n" +
                    "  \"Type\": \"Loại hình\",\n" +
                    "  \"Creator\": \"Người tạo\",\n" +
                    "  \"Requester\": \"Người yêu cầu\",\n" +
                    "  \"Elapsed days\": \"Số ngày trôi qua\",\n" +
                    "  \"Day\": \"Ngày\",\n" +
                    "  \"Request reason\": \"Lý do yêu cầu\",\n" +
                    "  \"Unable to read machine code\": \"Không thể đọc mã máy\",\n" +
                    "  \"Machine not scanned\": \"Chưa quét mã máy\",\n" +
                    "  \"Lost connection to maintenance API server\": \"Mất kết nối với máy chủ API bảo dưỡng\",\n" +
                    "  \"No maintenance plan found for machine\": \"Không tìm thấy kế hoạch bảo dưỡng cho máy\",\n" +
                    "  \"Time\": \"Thời gian\",\n" +
                    "  \"On hold\": \"Tạm hoãn\",\n" +
                    "  \"Approve\": \"Phê duyệt\",\n" +
                    "  \"Manufacturer\": \"Hãng sản xuất\",\n" +
                    "  \"Dots Per Inch\": \"Mật độ điểm ảnh (DPI)\",\n" +
                    "  \"Guest\": \"Khách\",\n" +
                    "  \"Enter maintenance data\": \"Nhập dữ liệu bảo trì\",\n" +
                    "  \"Checklist\": \"Danh mục kiểm tra\",\n" +
                    "  \"Parent\": \"Hạng mục gốc\",\n" +
                    "  \"Parent Item\": \"Hạng mục gốc\",\n" +
                    "  \"Edit\": \"Sửa\",\n" +
                    "  \"Back\": \"Quay lại\",\n" +
                    "  \"Save\": \"Lưu\",\n" +
                    "  \"Uploading image...\": \"Đang tải lên hình ảnh...\",\n" +
                    "  \"Uploaded 1 image for\": \"Đã tải lên 1 ảnh cho\",\n" +
                    "  \"Image upload error\": \"Lỗi tải ảnh\",\n" +
                    "  \"No image data received from server\": \"Không nhận được dữ liệu ảnh từ server\",\n" +
                    "  \"Saving maintenance data...\": \"Đang tiến hành lưu dữ liệu bảo dưỡng...\",\n" +
                    "  \"Save details failed at child item\": \"Lưu chi tiết thất bại tại mục con\",\n" +
                    "  \"Save details failed at parent item\": \"Lưu chi tiết thất bại tại mục cha\",\n" +
                    "  \"Save detailed info failed. Please try again!\": \"Lưu thông tin chi tiết thất bại. Vui lòng thử lại!\",\n" +
                    "  \"Temporarily saved successfully\": \"Đã lưu tạm thành công\",\n" +
                    "  \"detailed items\": \"hạng mục chi tiết\",\n" +
                    "  \"Missing required fields\": \"Thiếu trường bắt buộc\",\n" +
                    "  \"Do not send status update\": \"Không gửi cập nhật trạng thái\",\n" +
                    "  \"Saved successfully\": \"Đã bảo lưu thành công\",\n" +
                    "  \"items\": \"hạng mục\",\n" +
                    "  \"and updated Task status\": \"và cập nhật trạng thái Task\",\n" +
                    "  \"No conclusion response from Server\": \"Không nhận được phản hồi kết luận từ Server\",\n" +
                    "  \"Details\": \"Chi tiết\",\n" +
                    "  \"Save details OK but update Task status error\": \"Lưu chi tiết OK nhưng cập nhật trạng thái Task lỗi\",\n" +
                    "  \"System error while saving\": \"Hệ thống gặp lỗi khi lưu\",\n" +
                    "  \"Error saving item\": \"Lỗi lưu mục\",\n" +
                    "  \"No response from server\": \"Không nhận được phản hồi từ server\",\n" +
                    "  \"Minimum\": \"Cận dưới\",\n" +
                    "  \"Maximum\": \"Cận trên\",\n" +
                    "  \"Has\": \"Có\",\n" +
                    "  \"child items\": \"mục con\",\n" +
                    "  \"Visual inspection\": \"Tiêu chuẩn ngoại quan\",\n" +
                    "  \"History\": \"Lịch sử\",\n" +
                    "  \"Checksheet OK\": \"Checksheet OK\",\n" +
                    "  \"Checksheet NG\": \"Checksheet NG\",\n" +
                    "  \"Device information\": \"Thông tin thiết bị\",\n" +
                    "  \"Operating system version\": \"Phiên bản OS\",\n" +
                    "  \"Machine code: -- | Quantity: 0\": \"Mã máy: -- | Số lượng: 0\",\n" +
                    "  \"API level\": \"Cấp độ API\",\n" +
                    "  \"Error creating warehouse release request\": \"Lỗi khi tạo yêu cầu xuất kho\",\n" +
                    "  \"Error while getting list of materials\": \"Lỗi khi lấy danh sách vật tư\",\n" +
                    "  \"Maintenance management system\": \"Hệ thống quản lý bảo trì\",\n" +
                    "  \"User name\": \"Tên đăng nhập\",\n" +
                    "  \"Password\": \"Mật khẩu\",\n" +
                    "  \"Login_Singin\": \"ĐĂNG NHẬP\",\n" +
                    "  \"CLOSE\": \"ĐÓNG\",\n" +
                    "  \"Close\": \"Đóng\",\n" +
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"CÓ\",\n" +
                    "  \"Yes\": \"Có\",\n" +
                    "  \"NO\": \"KHÔNG\",\n" +
                    "  \"No\": \"Không\",\n" +
                    "  \"CANCEL\": \"THOÁT\",\n" +
                    "  \"BACK\": \"TRỞ LẠI\",\n" +
                    "  \"STOP\": \"DỪNG\",\n" +
                    "  \"ERROR\": \"LỖI\",\n" +
                    "  \"Reset\": \"Đặt lại\",\n" +
                    "  \"SAVE\": \"LƯU\",\n" +
                    "  \"Agree\": \"Đồng ý\",\n" +
                    "  \"Overdue\": \"Quá hạn\",\n" +
                    "  \"Deny\": \"Từ chối\",\n" +
                    "  \"Time arises\": \"Thời gian phát sinh\",\n" +
                    "  \"Select\": \"Chọn\",\n" +
                    "  \"Logout\": \"Đăng xuất\",\n" +
                    "  \"Confirm\": \"Xác nhận\",\n" +
                    "  \"Home\": \"Trang chủ\",\n" +
                    "  \"Website\": \"Trang web\",\n" +
                    "  \"Success\": \"Thành công\",\n" +
                    "  \"Completed\": \"Đã thực hiện\",\n" +
                    "  \"Login failed\": \"Đăng nhập không thành công\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Lịch bảo trì - sửa chữa\",\n" +
                    "  \"Report new error\": \"Yêu cầu công việc\",\n" +
                    "  \"To-Do List\": \"Công việc cần làm\",\n" +
                    "  \"Completed To Do List\": \"Đã hoàn thành\",\n" +
                    "  \"In progress\": \"Đang thực hiện\",\n" +
                    "  \"Machine code\": \"Mã máy\",\n" +
                    "  \"Machine name\": \"Tên máy\",\n" +
                    "  \"Materials\": \"Vật tư\",\n" +
                    "  \"Progress\": \"Tiến độ\",\n" +
                    "  \"Date\": \"Ngày\",\n" +
                    "  \"Complete task\": \"Hoàn thành công việc\",\n" +
                    "  \"Search\": \"Tìm kiếm\",\n" +
                    "  \"From\": \"Từ\",\n" +
                    "  \"To\": \"Đến\",\n" +
                    "  \"Error type\": \"Loại lỗi\",\n" +
                    "  \"Error content\": \"Nội dung lỗi\",\n" +
                    "  \"Min\": \"Min\",\n" +
                    "  \"Max\": \"Max\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"Có lock thiết bị trên hệ thống MES không?\",\n" +
                    "  \"Maintenance photo\": \"Ảnh\",\n" +
                    "  \"Impact on production\": \"Ảnh hưởng đến sản xuất\",\n" +
                    "  \"Impact on safety\": \"Ảnh hưởng đến an toàn\",\n" +
                    "  \"Select information\": \"Chọn thông tin\",\n" +
                    "  \"Upload file\": \"Tải lên file\",\n" +
                    "  \"Fill in note information\": \"Điền thông tin ghi chú\",\n" +
                    "  \"Materials list\": \"Danh sách vật tư\",\n" +
                    "  \"Collapse\": \"Thu gọn\",\n" +
                    "  \"Requests arise\": \"Yêu cầu phát sinh\",\n" +
                    "  \"Note\": \"Ghi chú\",\n" +
                    "  \"to upload image files\": \"để tải lên tập tin hình ảnh\",\n" +
                    "  \"No.\": \"Stt\",\n" +
                    "  \"Material name\": \"Tên vật tư\",\n" +
                    "  \"Unit\": \"Đơn vị\",\n" +
                    "  \"Pause task\": \"Tạm dừng công việc\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"Bạn muốn hoàn thành các hạng mục bảo trì ?\",\n" +
                    "  \"Do you want to save the incident information ?\": \"Bạn muốn lưu thông tin sự cố ?\",\n" +
                    "  \"Complete\": \"Hoàn thành\",\n" +
                    "  \"Could not save data\": \"Không thể lưu dữ liệu\",\n" +
                    "  \"Repair\": \"Sửa chữa\",\n" +
                    "  \"Machine history\": \"Lịch sử máy\",\n" +
                    "  \"Job type\": \"Loại công việc\",\n" +
                    "  \"List of tasks\": \"Danh sách công việc\",\n" +
                    "  \"Machine type\": \"Loại máy\",\n" +
                    "  \"DELETE ERROR\": \"XÓA LỖI\",\n" +
                    "  \"Do you want to delete this error?\": \"Bạn muốn xóa lỗi này?\",\n" +
                    "  \"Clear error failed\": \"Xóa lỗi không thành công\",\n" +
                    "  \"Please select machine\": \"Vui lòng chọn máy\",\n" +
                    "  \"Please select error code\": \"Vui lòng chọn mã lỗi\",\n" +
                    "  \"Please enter error content\": \"Vui lòng nhập nội dung lỗi\",\n" +
                    "  \"Maintenance not scheduled yet\": \"Chưa đến lịch bảo trì\",\n" +
                    "  \"Add material\": \"Thêm vật tư\",\n" +
                    "  \"Required Completion Date\": \"Ngày yêu cầu hoàn thành\",\n" +
                    "  \"Select materials\": \"Chọn vật tư\",\n" +
                    "  \"Warehouse Dispatch Information\": \"Thông tin xuất kho\",\n" +
                    "  \"Warehouse Release Request\": \"Yêu cầu xuất kho\",\n" +
                    "  \"Version\": \"Phiên bản\",\n" +
                    "  \"Server\": \"Máy chủ\",\n" +
                    "  \"Release date\": \"Ngày phát hành\",\n" +
                    "  \"Only notification\": \"Chỉ thông báo\",\n" +
                    "  \"Notification and vibrate\": \"Thông báo và rung\",\n" +
                    "  \"Please select maintenance category\": \"Vui lòng chọn hạng mục bảo trì\",\n" +
                    "  \"Blank material list\": \"Danh sách vật tư trống\",\n" +
                    "  \"No completion date selected\": \"Chưa chọn ngày yêu cầu hoàn thành\",\n" +
                    "  \"Enter the note requesting the release of stock\": \"Nhập ghi chú yêu cầu xuất kho\",\n" +
                    "  \"Please select material\": \"Vui lòng chọn vật tư\",\n" +
                    "  \"Invalid quantity\": \"Số lượng không hợp lệ\",\n" +
                    "  \"Please select warehouse\": \"Vui lòng kho\",\n" +
                    "  \"Creating a warehouse release request failed\": \"Tạo yêu cầu xuất kho không thành công\",\n" +
                    "  \"Save materials list failed\": \"Lưu danh sách vật tư không thành công\",\n" +
                    "  \"The new version\": \"Phiên bản mới\",\n" +
                    "  \"is now available\": \"đã sẵn sàng\",\n" +
                    "  \"Downloading\": \"Đang tải\",\n" +
                    "  \"Please wait...\": \"Xin vui lòng đợi...\",\n" +
                    "  \"Countermeasure\": \"Đối sách\",\n" +
                    "  \"Alarm setting\": \"Cài đặt cảnh báo\",\n" +
                    "  \"W/O Code\": \"Mã W/O\",\n" +
                    "  \"Request Date\": \"Ngày tạo\",\n" +
                    "  \"Work Order Content\": \"Nội dung Work Order\",\n" +
                    "  \"Week\": \"Tuần\",\n" +
                    "  \"Month\": \"Tháng\",\n" +
                    "  \"Delete Work Order\": \"Xóa Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"Bạn có xác nhận xóa WorkOrder %s?\",\n" +
                    "  \"Deleting Work Order...\": \"Đang xóa Work Order...\",\n" +
                    "  \"Delete successful\": \"Xóa thành công\",\n" +
                    "  \"Delete failed: %s\": \"Xóa thất bại: %s\",\n" +
                    "  \"Delete error: %s\": \"Lỗi xóa: %s\",\n" +
                    "  \"Storage permission denied\": \"Từ chối quyền lưu trữ\",\n" +
                    "  \"Unknown sources permission denied\": \"Từ chối quyền cài đặt từ nguồn không xác định\",\n" +
                    "  \"Load completed\": \"Load xong dữ liệu\",\n" +
                    "  \"Please enter Machine and Requester\": \"Vui lòng nhập đầy đủ Máy và Người yêu cầu!\",\n" +
                    "  \"Processing data...\": \"Đang xử lý dữ liệu...\",\n" +
                    "  \"Add Work Order successful\": \"Thêm Work Order thành công!\",\n" +
                    "  \"Updating...\": \"Đang cập nhật...\",\n" +
                    "  \"Update successful\": \"Update thành công\",\n" +
                    "  \"Update failed: %s\": \"Update thất bại: %s\",\n" +
                    "  \"Update error: %s\": \"Lỗi update: %s\",\n" +
                    "  \"Request reason is required\": \"Bắt buộc nhập lý do yêu cầu\",\n" +
                    "  \"Update Work Order\": \"Cập nhật Work Order\",\n" +
                    "  \"Edit Work Order\": \"Sửa Work Order\",\n" +
                    "  \"Select an item\": \"Chọn một mục\",\n" +
                    "  \"Enter process\": \"Nhập process\",\n" +
                    "  \"Select type\": \"Chọn Loại\",\n" +
                    "  \"Enter passed date\": \"Nhập ngày trải qua\",\n" +
                    "  \"Enter request reason\": \"Nhập lý do yêu cầu\",\n" +
                    "  \"Enter work order content\": \"Nhập nội dung Work Order\",\n" +
                    "  \"Incomplete\": \"Chưa hoàn thành\",\n" +
                    "  \"Canceled\": \"Đã hủy\",\n" +
                    "  \"Overdure\": \"Quá hạn\",\n" +
                    "  \"Machine Breakdown\": \"Máy hỏng\",\n" +
                    "  \"Machine broken\": \"Máy hỏng\",\n" +
                    "  \"Preparing operation\": \"Chuẩn bị thao tác\",\n" +
                    "  \"Stop due to shortage\": \"Dừng thiếu tồn\",\n" +
                    "  \"Stop by production plan\": \"Dừng theo kế hoạch sản xuất\",\n" +
                    "  \"Maintenance and repair\": \"Bảo dưỡng, sửa chữa\",\n" +
                    "  \"Maintenance steps\": \"Các bước thực hiện\",\n" +
                    "  \"Click here\": \"Nhấn vào đây\",\n" +
                    "  \"Material code\": \"Mã vật tư\",\n" +
                    "  \"Retry\": \"Thử lại\",\n" +
                    "  \"Start maintenance\": \"Bắt đầu thực hiện\",\n" +
                    "  \"Create a warehouse request\": \"Tạo yêu cầu xuất kho\",\n" +
                    "  \"Enter complete information\": \"Nhập đầy đủ thông tin\",\n" +
                    "  \"Account not authorized to delete\": \"Tài khoản không được phân quyền xóa\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"Bạn đã hoàn thành được bao nhiêu (%) khối lượng công việc ?\",\n" +
                    "  \"Do you want to create this warehouse request ?\": \"Bạn muốn tạo yêu cầu xuất kho này không ?\",\n" +
                    "  \"Machine repair and maintenance history\": \"Lịch sử sửa chữa và bảo trì máy\",\n" +
                    "  \"List of errors\": \"Danh sách lỗi\",\n" +
                    "  \"Select warehouse supplies\": \"Chọn kho vật tư\",\n" +
                    "  \"Notification, vibrate and ring\": \"Thông báo, rung và nhạc chuông\",\n" +
                    "  \"Please select at least one item that has not created a material export request.\": \"Vui lòng chọn tối thiểu một hạng mục chưa tạo yêu cầu xuất vật tư\",\n" +
                    "  \"Update failed warehouse release request status\": \"Cập nhật trạng thái yêu cầu xuất kho không thành công\",\n" +
                    "  \"The application has been updated to the latest version\": \"Ứng dụng đã được cập nhật lên phiên bản mới nhất\",\n" +
                    "  \"System settings\": \"Cài đặt hệ thống\",\n" +
                    "  \"Required fields missing\": \"Thiếu trường bắt buộc\",\n" +
                    "  \"status update not sent\": \"Không gửi cập nhật trạng thái\",\n" +
                    "  \"System encountered an error while saving\": \"Hệ thống gặp lỗi khi lưu\",\n" +
                    "  \"Item save error\": \"Lỗi lưu mục\",\n" +
                    "  \"Enter Work Order Data\": \"Nhập kết quả Work Order và hạng mục bảo dưỡng\",\n" +
                    "  \"Nhập dữ liệu\": \"Nhập dữ liệu\",\n" +
                    "  \"Bảo dưỡng\": \"Bảo dưỡng\",\n" +
                    "  \"Chưa quét mã máy\": \"Chưa quét mã máy\",\n" +
                    "  \"Quét\": \"Quét\",\n" +
                    "  \"Maintenance\": \"Bảo dưỡng\",\n" +
                    "  \"Không đọc được mã máy\": \"Không đọc được mã máy\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng\": \"Kế Hoạch Bảo Dưỡng\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng Định Kỳ (Tablet)\": \"Kế Hoạch Bảo Dưỡng Định Kỳ (Tablet)\",\n" +
                    "  \"Tìm kiếm theo tên hoặc mã máy...\": \"Tìm kiếm theo tên hoặc mã máy...\",\n" +
                    "  \"Thời gian\": \"Thời gian\",\n" +
                    "  \"Mã máy\": \"Mã máy\",\n" +
                    "  \"Số lượng\": \"Số lượng\",\n" +
                    "  \"Nhập dữ liệu Work Order\": \"Nhập dữ liệu Work Order\",\n" +
                    "  \"1.Thông tin\": \"1.Thông tin\",\n" +
                    "  \"2.Vật tư\": \"2.Vật tư\",\n" +
                    "  \"Hạng mục\": \"Hạng mục\",\n" +
                    "  \"Phụ trách\": \"Phụ trách\",\n" +
                    "  \"Thực hiện\": \"Thực hiện\",\n" +
                    "  \"Dự kiến\": \"Dự kiến\",\n" +
                    "  \"Xong\": \"Xong\",\n" +
                    "  \"Tạm hoãn\": \"Tạm hoãn\",\n" +
                    "  \"Tiến hành kiểm tra\": \"Tiến hành nhập kết quả\",\n" +
                    "  \"Chưa làm\": \"Chưa làm\",\n" +
                    "  \"Đã làm\": \"Đã làm\",\n" +
                    "  \"Quá hạn\": \"Quá hạn\",\n" +
                    "  \"Hạng mục gốc\": \"Hạng mục gốc\",\n" +
                    "  \"Lịch sử\": \"Lịch sử\",\n" +
                    "  \"Vật tư\": \"Vật tư\",\n" +
                    "  \"Không có vật tư đi kèm\": \"Không có vật tư đi kèm\",\n" +
                    "  \"Đóng\": \"Đóng\",\n" +
                    "  \"Sửa\": \"Sửa\"\n" +
                    "}\n";

    private static final String japanese =
            "{\n" +
                    "  \"All\": \"すべて\",\n" +
                    "  \"Canceled\": \"キャンセル\",\n" +
                    "  \"Periodic Maintenance Task List\": \"定期保守タスク一覧\",\n" +
                    "  \"MMS System FE Subsystem\": \"MMSシステム FEサブシステム\",\n" +
                    "  \"Pending\": \"保留中\",\n" +
                    "  \"Category Name\": \"カテゴリー名\",\n" +
                    "  \"Person in charge\": \"担当者\",\n" +
                    "  \"Execute task\": \"作業者\",\n" +
                    "  \"Plan\": \"計画\",\n" +
                    "  \"Done\": \"完了\",\n" +
                    "  \"Proceed with inspection\": \"検査を進める\",\n" +
                    "  \"Check item name\": \"点検項目名\",\n" +
                    "  \"Standard / Range\": \"基準 / 範囲\",\n" +
                    "  \"Display\": \"表示\",\n" +
                    "  \"Actual\": \"実績\",\n" +
                    "  \"Enter a note or cause if any...\": \"メモや原因を入力（あれば）...\",\n" +
                    "  \"W/O code\": \"指示書コード\",\n" +
                    "  \"Work type\": \"作業種別\",\n" +
                    "  \"Machine Code\": \"機械コード\",\n" +
                    "  \"Quantity\": \"数量\",\n" +
                    "  \"Work Order\": \"作業オーダー\",\n" +
                    "  \"Scan\": \"スキャン\",\n" +
                    "  \"Preventive Maintenance Plan (Tablet)\": \"定期保守計画 (タブレット)\",\n" +
                    "  \"Search by name or machine code...\": \"名前または機械コードで検索...\",\n" +
                    "  \"W/O Management\": \"ワークオーダーおよび保守項目の管理\",\n" +
                    "  \"Select function\": \"機能を選択\",\n" +
                    "  \"Create a work order or enter work order and maintenance data\": \"Work Orderを作成するか、Work Orderと保全データを入力します\",\n" +
                    "  \"Add Work Order\": \"新しいWork Orderを作成\",\n" +
                    "  \"ADD Work Order\": \"ワークオーダー情報入力\",\n" +
                    "  \"Create a new work order\": \"新しいWork Orderを作成\",\n" +
                    "  \"Enter Work Order Data\": \"作業データ入力\",\n" +
                    "  \"Manage work orders and perform maintenance\": \"Work Orderを管理して保全を実施\",\n" +
                    "  \"Language\": \"言語設定\",\n" +
                    "  \"Navigation to Login screen failed!\": \"ログイン画面への遷移に失敗しました！\",\n" +
                    "  \"Missing Server URL configuration, cannot check for updates\": \"サーバーのURL構成がありません。アップデートを確認できません\",\n" +
                    "  \"Checking version on the system...\": \"システム上でバージョンを確認しています...\",\n" +
                    "  \"System found version\": \"システムがバージョンを見つけました\",\n" +
                    "  \"Current version on Tablet is\": \"タブレットの現在のバージョンは\",\n" +
                    "  \"Do you want to proceed with automatic upgrade now?\": \"今すぐ自動アップグレードを実行しますか？\",\n" +
                    "  \"New version available!\": \"新しいバージョンが検出されました！\",\n" +
                    "  \"Update now\": \"今すぐ更新\",\n" +
                    "  \"Later\": \"後で\",\n" +
                    "  \"The application is at the latest version\": \"アプリはすでに最新バージョンです\",\n" +
                    "  \"Server responded with error code\": \"サーバーがエラーコードを返しました\",\n" +
                    "  \"Cannot connect to check version\": \"バージョン確認に接続できません\",\n" +
                    "  \"Downloading update\": \"アップデートをダウンロード中\",\n" +
                    "  \"Please maintain a stable network connection...\": \"安定したネットワーク接続を維持してください...\",\n" +
                    "  \"Server response code error\": \"サーバーの応答コードエラー\",\n" +
                    "  \"Failed to download update\": \"アップデートのダウンロードに失敗しました\",\n" +
                    "  \"Please grant permission to install apps from unknown sources!\": \"アプリに提供元不明のアプリのインストール権限を付与してください！\",\n" +
                    "  \"No attachment file to download\": \"ダウンロードする添付ファイルがありません\",\n" +
                    "  \"Select attachment document\": \"添付書類を選択してください\",\n" +
                    "  \"Please select a file before uploading\": \"アップロードする前にファイルを選択してください\",\n" +
                    "  \"Work Order code not found for upload\": \"アップロードするワークオーダーコードが見つかりません\",\n" +
                    "  \"Uploading document...\": \"書類をアップロード中...\",\n" +
                    "  \"Document uploaded successfully\": \"書類のアップロードが成功しました\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"アップロードは成功しましたがファイル名が取得できません\",\n" +
                    "  \"No response from server\": \"サーバーから応答がありません\",\n" +
                    "  \"Upload failed\": \"アップロードに失敗しました\",\n" +
                    "  \"User login information not found\": \"ユーザーログイン情報が見つかりません\",\n" +
                    "  \"No materials available to create warehouse request\": \"出庫要求を作成する資材がありません\",\n" +
                    "  \"Error parsing material list\": \"資材リストの解析エラー\",\n" +
                    "  \"No valid materials for warehouse release\": \"出庫に有効な資材がありません\",\n" +
                    "  \"Warehouse request for Work Order\": \"ワークオーダーの出庫要求\",\n" +
                    "  \"Creating warehouse release request...\": \"出庫要求を作成中...\",\n" +
                    "  \"Warehouse release request created successfully!\": \"出庫要求が正常に作成されました！\",\n" +
                    "  \"Enter data\": \"データ入力\",\n" +
                    "  \"Work Order TASK_ID not found\": \"ワークオーダーのTASK_IDが見つかりません\",\n" +
                    "  \"Saving...\": \"保存中...\",\n" +
                    "  \"Data saved successfully\": \"データを正常に保存しました\",\n" +
                    "  \"Save failed\": \"保存に失敗しました\",\n" +
                    "  \"Download file\": \"ファイルをダウンロード\",\n" +
                    "  \"Downloading attachment...\": \"添付ファイルをダウンロード中...\",\n" +
                    "  \"Starting file download...\": \"ファイルのダウンロードを開始します...\",\n" +
                    "  \"Cannot initialize Download Manager\": \"Download Managerを起動できません\",\n" +
                    "  \"File download error\": \"ファイルのダウンロードエラー\",\n" +
                    "  \"Cannot open link\": \"リンクを開くことができません\",\n" +
                    "  \"Machine\": \"設備\",\n" +
                    "  \"Request date\": \"依頼日\",\n" +
                    "  \"Process\": \"工程\",\n" +
                    "  \"Type\": \"種類\",\n" +
                    "  \"Creator\": \"作成者\",\n" +
                    "  \"Requester\": \"依頼者\",\n" +
                    "  \"Elapsed days\": \"経過日数\",\n" +
                    "  \"Day\": \"日\",\n" +
                    "  \"Request reason\": \"依頼理由\",\n" +
                    "  \"Unable to read machine code\": \"設備コードを読み取れませんでした\",\n" +
                    "  \"Machine not scanned\": \"設備コード未スキャン\",\n" +
                    "  \"Lost connection to maintenance API server\": \"保守APIサーバーとの接続が切断されました\",\n" +
                    "  \"No maintenance plan found for machine\": \"機械の保守計画が見つかりません\",\n" +
                    "  \"Time\": \"時間\",\n" +
                    "  \"On hold\": \"保留中\",\n" +
                    "  \"Approve\": \"承認\",\n" +
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"メンテナンス管理システム\",\n" +
                    "  \"Maintenance management system\": \"メンテナンス管理システム\",\n" +
                    "  \"User name\": \"ユーザー名\",\n" +
                    "  \"Password\": \"パスワード\",\n" +
                    "  \"LOGOUT\": \"ログアウト\",\n" +
                    "  \"Login_Singin\": \"ログイン\",\n" +
                    "  \"CLOSE\": \"閉じる\",\n" +
                    "  \"Close\": \"閉じる\",\n" +
                    "  \"Time arises\": \"発生時間\",\n" +
                    "  \"EXIT\": \"終了\",\n" +
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"はい\",\n" +
                    "  \"Yes\": \"はい\",\n" +
                    "  \"NO\": \"いいえ\",\n" +
                    "  \"No\": \"いいえ\",\n" +
                    "  \"CANCEL\": \"キャンセル\",\n" +
                    "  \"CONTINUE\": \"続ける\",\n" +
                    "  \"BACK\": \"戻る\",\n" +
                    "  \"Back\": \"戻る\",\n" +
                    "  \"STOP\": \"停止\",\n" +
                    "  \"ERROR\": \"エラー\",\n" +
                    "  \"SAVE\": \"保存\",\n" +
                    "  \"DELETE\": \"削除\",\n" +
                    "  \"MATERIALS LIST\": \"資材リスト\",\n" +
                    "  \"LOGIN FAIL\": \"ログイン失敗\",\n" +
                    "  \"Save\": \"保存\",\n" +
                    "  \"Agree\": \"同意する\",\n" +
                    "  \"Deny\": \"拒否する\",\n" +
                    "  \"Select\": \"選択\",\n" +
                    "  \"Logout\": \"ログアウト\",\n" +
                    "  \"Confirm\": \"確認\",\n" +
                    "  \"Cancel\": \"キャンセル\",\n" +
                    "  \"Home\": \"ホーム\",\n" +
                    "  \"Website\": \"ウェブサイト\",\n" +
                    "  \"Enter work order data and maintenance\": \"Work Orderデータと保全を入力\",\n" +
                    "  \"Success\": \"成功\",\n" +
                    "  \"Fill in the login information\": \"ログイン情報を入力してください\",\n" +
                    "  \"Login failed\": \"ログインに失敗しました\",\n" +
                    "  \"Please check your username or password\": \"ユーザー名またはパスワードを確認してください\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"メンテナンス項目リスト\",\n" +
                    "  \"Report new error\": \"新しいエラーを報告\",\n" +
                    "  \"To-Do List\": \"やることリスト\",\n" +
                    "  \"Completed To Do List\": \"完了リスト\",\n" +
                    "  \"In progress\": \"進行中\",\n" +
                    "  \"Overdue\": \"期限切れ\",\n" +
                    "  \"Machine name\": \"機械名\",\n" +
                    "  \"Materials\": \"資材\",\n" +
                    "  \"Progress\": \"進捗\",\n" +
                    "  \"Date\": \"日付\",\n" +
                    "  \"Complete task\": \"作業完了\",\n" +
                    "  \"Execute task\": \"作業実行\",\n" +
                    "  \"Search\": \"検索\",\n" +
                    "  \"Checksheet OK\": \"チェックシートOK\",\n" +
                    "  \"Checksheet NG\": \"チェックシートNG\",\n" +
                    "  \"Min\": \"最小\",\n" +
                    "  \"Max\": \"最大\",\n" +
                    "  \"Thiếu trường bắt buộc\": \"必須項目が不足しています\",\n" +
                    "  \"Không gửi cập nhật trạng thái\": \"ステータス更新を送信しません\",\n" +
                    "  \"Hệ thống gặp lỗi khi lưu\": \"保存中にシステムエラーが発生しました\",\n" +
                    "  \"Lỗi lưu mục\": \"項目の保存エラー\",\n" +
                    "  \"Có\": \"あり\",\n" +
                    "  \"mục con\": \"子項目\",\n" +
                    "  \"Kiểm tra ngoại quan\": \"外観確認\",\n" +
                    "  \"Nhập Dữ Liệu Bảo Dưỡng\": \"保全データ入力\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng\": \"保全計画\",\n" +
                    "  \"Kế Hoạch Bảo Dưỡng Định Kỳ (Tablet)\": \"定期保全計画 (タブレット)\",\n" +
                    "  \"Tìm kiếm theo tên hoặc mã máy...\": \"機械名またはコードで検索...\",\n" +
                    "  \"Thời gian\": \"時間\",\n" +
                    "  \"Mã máy\": \"機械コード\",\n" +
                    "  \"Số lượng\": \"数量\",\n" +
                    "  \"Machine code: -- | Quantity: 0\": \"機械コード: -- | 数量: 0\",\n" +
                    "  \"Nhập dữ liệu Work Order\": \"Work Orderデータ入力\",\n" +
                    "  \"1.Thông tin\": \"1. 情報\",\n" +
                    "  \"2.Vật tư\": \"2. 資材\",\n" +
                    "  \"Minimum\": \"最小\",\n" +
                    "  \"Maximum\": \"最大\",\n" +
                    "  \"Has\": \"あり\",\n" +
                    "  \"child items\": \"子項目\",\n" +
                    "  \"Visual inspection\": \"外観確認\",\n" +
                    "  \"Standard / Range\": \"基準 / 測定範囲\",\n" +
                    "  \"Enter a note or cause (if any)...\": \"備考または原因を入力してください（必要な場合）...\",\n" +
                    "  \"Nhập ghi chú hoặc nguyên nhân (nếu có)...\": \"備考または原因を入力してください（必要な場合）...\",\n" +
                    "  \"Tên hạng mục kiểm tra\": \"点検項目名\",\n" +
                    "  \"Tiêu chuẩn / Dải đo\": \"基準 / 測定範囲\",\n" +
                    "  \"Hạng mục\": \"項目\",\n" +
                    "  \"Phụ trách\": \"担当\",\n" +
                    "  \"Thực hiện\": \"実施\",\n" +
                    "  \"Dự kiến\": \"予定\",\n" +
                    "  \"Xong\": \"完了\",\n" +
                    "  \"Tạm hoãn\": \"保留\",\n" +
                    "  \"Tiến hành kiểm tra\": \"結果を入力する\",\n" +
                    "  \"Chưa làm\": \"未実施\",\n" +
                    "  \"Đã làm\": \"実施済み\",\n" +
                    "  \"Quá hạn\": \"期限切れ\",\n" +
                    "  \"Hạng mục gốc\": \"親項目\",\n" +
                    "  \"Lịch sử\": \"履歴\",\n" +
                    "  \"Thực tế\": \"実測\",\n" +
                    "  \"Hiển thị\": \"表示\",\n" +
                    "  \"Không có vật tư đi kèm\": \"添付資材なし\",\n" +
                    "  \"From\": \"開始日\",\n" +
                    "  \"To\": \"終了日\",\n" +
                    "  \"Error content\": \"エラー内容\",\n" +
                    "  \"Condition photo\": \"現状写真\",\n" +
                    "  \"Maintenance photo\": \"メンテナンス写真\",\n" +
                    "  \"Impact on production\": \"生産への影響\",\n" +
                    "  \"Impact on safety\": \"安全性への影響\",\n" +
                    "  \"Select information\": \"情報を選択\",\n" +
                    "  \"Upload file\": \"ファイルをアップロード\",\n" +
                    "  \"Fill in note information\": \"メモ情報を入力\",\n" +
                    "  \"Maintenance steps\": \"メンテナンス手順\",\n" +
                    "  \"Collapse\": \"折りたたみ\",\n" +
                    "  \"See more\": \"もっと見る\",\n" +
                    "  \"Requests arise\": \"発生要求\",\n" +
                    "  \"Note\": \"メモ\",\n" +
                    "  \"Click here\": \"ここをクリック\",\n" +
                    "  \"to upload image files\": \"画像ファイルをアップロードする\",\n" +
                    "  \"No.\": \"番号\",\n" +
                    "  \"Material code\": \"資材コード\",\n" +
                    "  \"Material name\": \"資材名\",\n" +
                    "  \"Unit\": \"単位\",\n" +
                    "  \"Retry\": \"再試行\",\n" +
                    "  \"Account not authorized to delete\": \"削除の権限がありません\",\n" +
                    "  \"Start maintenance\": \"メンテナンス開始\",\n" +
                    "  \"Pause task\": \"作業一時停止\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"メンテナンス項目を完了しますか？\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"作業の進捗はどのくらいですか（％）？\",\n" +
                    "  \"Do you want to save the incident information ?\": \"インシデント情報を保存しますか？\",\n" +
                    "  \"Complete\": \"完了\",\n" +
                    "  \"Create a warehouse request\": \"倉庫出庫依頼を作成\",\n" +
                    "  \"Do you want to create this warehouse request ?\": \"この出庫依頼を作成しますか？\",\n" +
                    "  \"Enter complete information\": \"すべての情報を入力してください\",\n" +
                    "  \"Could not save data\": \"データを保存できませんでした\",\n" +
                    "  \"Repair\": \"修理\",\n" +
                    "  \"Approved OK\": \"承認済み（OK）\",\n" +
                    "  \"Approved NG\": \"承認済み（NG）\",\n" +
                    "  \"Job type\": \"作業種別\",\n" +
                    "  \"List of tasks\": \"作業一覧\",\n" +
                    "  \"Machine repair and maintenance history\": \"機械の修理・保守履歴\",\n" +
                    "  \"Machine type\": \"機械タイプ\",\n" +
                    "  \"DELETE ERROR\": \"エラー削除\",\n" +
                    "  \"Do you want to delete this error?\": \"このエラーを削除しますか？\",\n" +
                    "  \"Clear error failed\": \"エラー削除に失敗しました\",\n" +
                    "  \"List of errors\": \"エラーリスト\",\n" +
                    "  \"Please select machine\": \"機械を選択してください\",\n" +
                    "  \"Please select error code\": \"エラーコードを選択してください\",\n" +
                    "  \"Please enter error content\": \"エラー内容を入力してください\",\n" +
                    "  \"Maintenance not scheduled yet\": \"メンテナンスはまだ予定されていません\",\n" +
                    "  \"Add material\": \"資材を追加\",\n" +
                    "  \"Select warehouse supplies\": \"倉庫資材を選択\",\n" +
                    "  \"Required Completion Date\": \"完了予定日\",\n" +
                    "  \"Select materials\": \"資材を選択\",\n" +
                    "  \"Warehouse Dispatch Information\": \"倉庫出庫情報\",\n" +
                    "  \"Warehouse Release Request\": \"出庫依頼\",\n" +
                    "  \"Error code\": \"エラーコード\",\n" +
                    "  \"Release date\": \"発売日\",\n" +
                    "  \"Only notification\": \"通知のみ\",\n" +
                    "  \"Notification and vibrate\": \"通知とバイブ\",\n" +
                    "  \"Notification, vibrate and ring\": \"通知、バイブ、着信音\",\n" +
                    "  \"Product of Meiko Automation\": \"Meiko Automationの製品\",\n" +
                    "  \"Please select maintenance category\": \"保守項目を選択してください。\",\n" +
                    "  \"Please select at least one item that has not created a material export request.\": \"マテリアルエクスポートリクエストを作成していないアイテムを少なくとも 1 つ選択してください。\",\n" +
                    "  \"Blank material list\": \"空白の材料リスト\",\n" +
                    "  \"No completion date selected\": \"完了日が選択されていません\",\n" +
                    "  \"Enter the note requesting the release of stock\": \"在庫の解放を要求するメモを入力します\",\n" +
                    "  \"Please select material\": \"素材を選択してください\",\n" +
                    "  \"Invalid quantity\": \"数量が無効です\",\n" +
                    "  \"Please select warehouse\": \"倉庫を選択してください\",\n" +
                    "  \"Update failed warehouse release request status\": \"失敗した倉庫ース要求ステータスの更新\",\n" +
                    "  \"Creating a warehouse release request failed\": \"倉庫リリースリクエストの作成に失敗しました\",\n" +
                    "  \"Save materials list failed\": \"材料リスト of 保存に失敗しました\",\n" +
                    "  \"Update\": \"アップデート\",\n" +
                    "  \"The application has been updated to the latest version\": \"アプリケーションは最新バージョンに更新されました。\",\n" +
                    "  \"The new version\": \"新しいバージョン\",\n" +
                    "  \"is now available\": \"が利用可能になりました\",\n" +
                    "  \"Downloading\": \"ダウンロード中\",\n" +
                    "  \"Please wait...\": \"お待ちください...\",\n" +
                    "  \"Current situation\": \"現状\",\n" +
                    "  \"Root cause\": \"原因\",\n" +
                    "  \"Action taken\": \"対処内容\",\n" +
                    "  \"Countermeasure\": \"対策\",\n" +
                    "  \"Fill in the current situation information\": \"現状の情報を入力してください\",\n" +
                    "  \"Fill in the reason information\": \"原因の情報を入力してください\",\n" +
                    "  \"Fill in the action taken information\": \"対処内容を入力してください\",\n" +
                    "  \"Fill in countermeasure information\": \"対策の情報を入力してください\",\n" +
                    "  \"Alarm setting\": \"アラーム設定\",\n" +
                    "  \"W/O Management\": \"W/O管理\",\n" +
                    "  \"W/O code\": \"W/Oコード\",\n" +
                    "  \"Request date\": \"依頼日\",\n" +
                    "  \"Machine\": \"設備\",\n" +
                    "  \"Process\": \"工程\",\n" +
                    "  \"Work type\": \"作業種別\",\n" +
                    "  \"Creator\": \"作成者\",\n" +
                    "  \"Requester\": \"依頼者\",\n" +
                    "  \"Elapsed days\": \"経過日数\",\n" +
                    "  \"Deadline\": \"期限\",\n" +
                    "  \"Request reason\": \"依頼理由\",\n" +
                    "  \"Work Order Content\": \"W/O内容\",\n" +
                    "  \"WO status\": \"WO状態\",\n" +
                    "  \"MA status\": \"MA状態\",\n" +
                    "  \"Add W/O\": \"W/O追加\",\n" +
                    "  \"Day\": \"日\",\n" +
                    "  \"Week\": \"週\",\n" +
                    "  \"Month\": \"月\",\n" +
                    "  \"Nhập dữ liệu\": \"作業データ入力\",\n" +
                    "  \"Work Order\": \"作業指示\",\n" +
                    "  \"Bảo dưỡng\": \"メンテナンス\",\n" +
                    "  \"Chưa quét mã máy\": \"設備コード未スキャン\",\n" +
                    "  \"Quét\": \"スキャン\",\n" +
                    "  \"Không đọc được mã máy\": \"設備コードを読み取れませんでした\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"このWork Orderを編集する権限がないか、すでに完了しています\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"このWork Orderを削除する権限がないか、すでに完了しています\",\n" +
                    "  \"Delete Work Order\": \"Work Order削除\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"WorkOrder %s を削除してもよろしいですか？\",\n" +
                    "  \"Deleting Work Order...\": \"Work Orderを削除中...\",\n" +
                    "  \"Delete successful\": \"削除成功\",\n" +
                    "  \"Delete failed: %s\": \"削除失敗: %s\",\n" +
                    "  \"Delete error: %s\": \"削除エラー: %s\",\n" +
                    "  \"Storage permission denied\": \"ストレージ権限が拒否されました\",\n" +
                    "  \"Unknown sources permission denied\": \"提供元不明アプリの権限が拒否されました\",\n" +
                    "  \"Load completed\": \"データの読み込みが完了しました\",\n" +
                    "  \"Please enter Machine and Requester\": \"設備と依頼者を入力してください\",\n" +
                    "  \"Processing data...\": \"データ処理中...\",\n" +
                    "  \"Add Work Order successful\": \"Work Orderの追加に成功しました\",\n" +
                    "  \"Updating...\": \"更新中...\",\n" +
                    "  \"Update successful\": \"更新成功\",\n" +
                    "  \"Update failed: %s\": \"更新失敗: %s\",\n" +
                    "  \"Update error: %s\": \"更新エラー: %s\",\n" +
                    "  \"Request reason is required\": \"依頼理由は必須です\",\n" +
                    "  \"Add Work Order\": \"Work Orderを追加\",\n" +
                    "  \"Update Work Order\": \"Work Orderを更新\",\n" +
                    "  \"Edit Work Order\": \"Work Orderを編集\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"MESシステムに設備のロック機能はありますか？\",\n" +
                    "  \"W/O Code\": \"W/Oコード\",\n" +
                    "  \"Request Date\": \"依頼日\",\n" +
                    "  \"Select an item\": \"項目を選択\",\n" +
                    "  \"Enter process\": \"工程を入力\",\n" +
                    "  \"Select type\": \"種別を選択\",\n" +
                    "  \"Passed Date\": \"経過日\",\n" +
                    "  \"Enter passed date\": \"経過日を入力\",\n" +
                    "  \"Request Reason\": \"依頼理由\",\n" +
                    "  \"Enter request reason\": \"依頼理由を入力\",\n" +
                    "  \"Enter work order content\": \"W/O内容を入力\",\n" +
                    "  \"MA Status\": \"MA報告状態\",\n" +
                    "  \"Completed\": \"完了\",\n" +
                    "  \"Incomplete\": \"未完了\",\n" +
                    "  \"Overdure\": \"期限切れ\",\n" +
                    "  \"Machine Breakdown\": \"設備故障\",\n" +
                    "  \"Machine broken\": \"設備故障\",\n" +
                    "  \"Preparing operation\": \"作業準備\",\n" +
                    "  \"Stop due to shortage\": \"不足による停止\",\n" +
                    "  \"Stop by production plan\": \"生産計画による停止\",\n" +
                    "  \"Maintenance and repair\": \"保全・修理\",\n" +
                    "  \"Maintenance\": \"メンテナンス\",\n" +
                    "  \"Language changed successfully\": \"言語が正常に変更されました\",\n" +
                    "  \"Action\": \"操作\"\n" +
                    "}\n";


    private static final String chinese =
            "{\n" +
                    "  \"All\": \"全部\",\n" +
                    "  \"Periodic Maintenance Task List\": \"定期保养任务列表\",\n" +
                    "  \"MMS System FE Subsystem\": \"MMS系统 FE子系统\",\n" +
                    "  \"Pending\": \"待处理\",\n" +
                    "  \"Category Name\": \"类别名称\",\n" +
                    "  \"Person in charge\": \"负责人\",\n" +
                    "  \"Execute task\": \"执行任务\",\n" +
                    "  \"Plan\": \"计划\",\n" +
                    "  \"Done\": \"已完成\",\n" +
                    "  \"Proceed with inspection\": \"进行检查\",\n" +
                    "  \"Check item name\": \"检查项目名称\",\n" +
                    "  \"Standard / Range\": \"标准 / 范围\",\n" +
                    "  \"Display\": \"显示\",\n" +
                    "  \"Actual\": \"实际\",\n" +
                    "  \"Machine code: -- | Quantity: 0\": \"机器代码: -- | 数量: 0\",\n" +
                    "  \"Enter a note or cause if any...\": \"输入备注或原因（如有）...\",\n" +
                    "  \"W/O code\": \"工单代码\",\n" +
                    "  \"Work type\": \"工单类型\",\n" +
                    "  \"Machine Code\": \"设备代码\",\n" +
                    "  \"Quantity\": \"数量\",\n" +
                    "  \"Work Order\": \"工单\",\n" +
                    "  \"Scan\": \"扫描\",\n" +
                    "  \"Preventive Maintenance Plan (Tablet)\": \"定期保养计划 (平板)\",\n" +
                    "  \"Search by name or machine code...\": \"按名称或设备代码搜索...\",\n" +
                    "  \"W/O Management\": \"工单与保养项目管理\",\n" +
                    "  \"Select function\": \"选择功能\",\n" +
                    "  \"Create a work order or enter work order and maintenance data\": \"创建工单或输入工单及保养数据\",\n" +
                    "  \"Add Work Order\": \"新增工单\",\n" +
                    "  \"ADD Work Order\": \"录入工单信息\",\n" +
                    "  \"Create a new work order\": \"创建一个新工单\",\n" +
                    "  \"Enter Work Order Data\": \"录入工单与保养项目结果\",\n" +
                    "  \"Manage work orders and perform maintenance\": \"管理工单并执行保养\",\n" +
                    "  \"Language\": \"语言选择\",\n" +
                    "  \"Navigation to Login screen failed!\": \"系统导航至登录界面失败！\",\n" +
                    "  \"Missing Server URL configuration, cannot check for updates\": \"缺少服务器URL配置，无法检查更新\",\n" +
                    "  \"Checking version on the system...\": \"正在检查系统版本...\",\n" +
                    "  \"System found version\": \"系统检测到新版本\",\n" +
                    "  \"Current version on Tablet is\": \"平板当前版本为\",\n" +
                    "  \"Do you want to proceed with automatic upgrade now?\": \"您想现在进行自动升级吗？\",\n" +
                    "  \"New version available!\": \"检测到新版本！\",\n" +
                    "  \"Update now\": \"立即更新\",\n" +
                    "  \"Later\": \"稍后\",\n" +
                    "  \"The application is at the latest version\": \"应用已是最新版本\",\n" +
                    "  \"Server responded with error code\": \"服务器返回错误码\",\n" +
                    "  \"Cannot connect to check version\": \"无法连接检查版本\",\n" +
                    "  \"Downloading update\": \"正在下载更新\",\n" +
                    "  \"Please maintain a stable network connection...\": \"请保持网络连接稳定...\",\n" +
                    "  \"Server response code error\": \"服务器响应错误码\",\n" +
                    "  \"Failed to download update\": \"下载更新失败\",\n" +
                    "  \"Please grant permission to install apps from unknown sources!\": \"请允许应用安装来自未知来源的应用程序！\",\n" +
                    "  \"No attachment file to download\": \"没有可下载的附件\",\n" +
                    "  \"Select attachment document\": \"选择附加文件\",\n" +
                    "  \"Please select a file before uploading\": \"请在上传前选择文件\",\n" +
                    "  \"Work Order code not found for upload\": \"找不到用于上传工单代码\",\n" +
                    "  \"Uploading document...\": \"正在上传文件...\",\n" +
                    "  \"Document uploaded successfully\": \"文件上传成功\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"上传成功但未能获取文件名\",\n" +
                    "  \"No response from server\": \"服务器未响应\",\n" +
                    "  \"Upload failed\": \"上传失败\",\n" +
                    "  \"User login information not found\": \"找不到用户登录信息处理\",\n" +
                    "  \"No materials available to create warehouse request\": \"没有物料可用于创建出库申请\",\n" +
                    "  \"Error parsing material list\": \"物料列表解析错误\",\n" +
                    "  \"No valid materials for warehouse release\": \"没有有效的出库物料\",\n" +
                    "  \"Warehouse request for Work Order\": \"工单出库申请\",\n" +
                    "  \"Creating warehouse release request...\": \"正在创建出库申请...\",\n" +
                    "  \"Warehouse release request created successfully!\": \"出库申请创建成功！\",\n" +
                    "  \"Enter data\": \"输入数据\",\n" +
                    "  \"Work Order TASK_ID not found\": \"找不到工单的TASK_ID\",\n" +
                    "  \"Saving...\": \"正在保存...\",\n" +
                    "  \"Data saved successfully\": \"数据保存成功\",\n" +
                    "  \"Save failed\": \"保存失败\",\n" +
                    "  \"Download file\": \"下载文件\",\n" +
                    "  \"Downloading attachment...\": \"正在下载附件...\",\n" +
                    "  \"Starting file download...\": \"开始下载文件...\",\n" +
                    "  \"Cannot initialize Download Manager\": \"无法初始化下载管理器\",\n" +
                    "  \"File download error\": \"文件下载错误\",\n" +
                    "  \"Cannot open link\": \"无法打开链接\",\n" +
                    "  \"Machine\": \"设备\",\n" +
                    "  \"Request date\": \"请求日期\",\n" +
                    "  \"Process\": \"工序\",\n" +
                    "  \"Type\": \"类型\",\n" +
                    "  \"Creator\": \"创建者\",\n" +
                    "  \"Requester\": \"请求者\",\n" +
                    "  \"Elapsed days\": \"经过天数\",\n" +
                    "  \"Day\": \"天\",\n" +
                    "  \"Request reason\": \"请求原因\",\n" +
                    "  \"Unable to read machine code\": \"无法读取设备码\",\n" +
                    "  \"Machine not scanned\": \"未扫描设备码\",\n" +
                    "  \"Lost connection to maintenance API server\": \"与保养API服务器断开连接\",\n" +
                    "  \"No maintenance plan found for machine\": \"未找到该设备的保养计划\",\n" +
                    "  \"Time\": \"时间\",\n" +
                    "  \"On hold\": \"暂缓\",\n" +
                    "  \"Approve\": \"审批\",\n" +
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"维护管理系统\",\n" +
                    "  \"Maintenance management system\": \"维护管理系统\",\n" +
                    "  \"User name\": \"用户名\",\n" +
                    "  \"Password\": \"密码\",\n" +
                    "  \"LOGOUT\": \"登出\",\n" +
                    "  \"Login_Singin\": \"登录\",\n" +
                    "  \"CLOSE\": \"关闭\",\n" +
                    "  \"Close\": \"关闭\",\n" +
                    "  \"EXIT\": \"退出\",\n" +
                    "  \"OK\": \"确定\",\n" +
                    "  \"Time arises\": \"发生时间\",\n" +
                    "  \"YES\": \"是\",\n" +
                    "  \"Yes\": \"是\",\n" +
                    "  \"NO\": \"否\",\n" +
                    "  \"No\": \"否\",\n" +
                    "  \"CANCEL\": \"取消\",\n" +
                    "  \"CONTINUE\": \"继续\",\n" +
                    "  \"BACK\": \"返回\",\n" +
                    "  \"Back\": \"返回\",\n" +
                    "  \"STOP\": \"停止\",\n" +
                    "  \"ERROR\": \"错误\",\n" +
                    "  \"SAVE\": \"保存\",\n" +
                    "  \"DELETELarge\": \"删除\",\n" +
                    "  \"MATERIALS LIST\": \"材料清单\",\n" +
                    "  \"LOGIN FAIL\": \"登录失败\",\n" +
                    "  \"Agree\": \"同意\",\n" +
                    "  \"Deny\": \"拒绝\",\n" +
                    "  \"Select\": \"选择\",\n" +
                    "  \"Logout\": \"登出\",\n" +
                    "  \"Confirm\": \"确认\",\n" +
                    "  \"Cancel\": \"取消\",\n" +
                    "  \"Home\": \"主页\",\n" +
                    "  \"Website\": \"网站\",\n" +
                    "  \"System settings\": \"系统设置\",\n" +
                    "  \"Success\": \"成功\",\n" +
                    "  \"Fill in the login information\": \"请填写登录信息\",\n" +
                    "  \"Login failed\": \"登录失败\",\n" +
                    "  \"Please check your username or password\": \"请检查您的用户名或密码\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"维护与修理计划\",\n" +
                    "  \"Report new error\": \"报告新错误\",\n" +
                    "  \"To-Do List\": \"待办事项列表\",\n" +
                    "  \"Completed To Do List\": \"已完成事项列表\",\n" +
                    "  \"In progress\": \"进行中\",\n" +
                    "  \"Overdue\": \"已逾期\",\n" +
                    "  \"Machine code\": \"机器代码\",\n" +
                    "  \"Machine name\": \"机器名称\",\n" +
                    "  \"Materials\": \"材料\",\n" +
                    "  \"Progress\": \"进度\",\n" +
                    "  \"Date\": \"日期\",\n" +
                    "  \"Complete task\": \"完成任务\",\n" +
                    "  \"Search\": \"搜索\",\n" +
                    "  \"Enter maintenance data\": \"输入保养数据\",\n" +
                    "  \"Checklist\": \"检查表\",\n" +
                    "  \"Parent\": \"父项目\",\n" +
                    "  \"Edit\": \"编辑\",\n" +
                    "  \"From\": \"开始日期\",\n" +
                    "  \"To\": \"结束日期\",\n" +
                    "  \"Error type\": \"错误类型\",\n" +
                    "  \"Error content\": \"错误内容\",\n" +
                    "  \"Condition photo\": \"现状照片\",\n" +
                    "  \"Maintenance photo\": \"维护照片\",\n" +
                    "  \"Materials list\": \"材料清单\",\n" +
                    "  \"Impact on production\": \"对生产的影响\",\n" +
                    "  \"Impact on safety\": \"对安全的影响\",\n" +
                    "  \"Select information\": \"选择信息\",\n" +
                    "  \"Upload file\": \"上传文件\",\n" +
                    "  \"Fill in note information\": \"填写备注信息\",\n" +
                    "  \"Maintenance steps\": \"维护步骤\",\n" +
                    "  \"Collapse\": \"折叠\",\n" +
                    "  \"See more\": \"查看更多\",\n" +
                    "  \"Requests arise\": \"请求产生\",\n" +
                    "  \"Note\": \"备注\",\n" +
                    "  \"Click here\": \"点击这里\",\n" +
                    "  \"to upload image files\": \"上传图片文件\",\n" +
                    "  \"No.\": \"编号\",\n" +
                    "  \"Material code\": \"材料代码\",\n" +
                    "  \"Material name\": \"材料名称\",\n" +
                    "  \"Unit\": \"单位\",\n" +
                    "  \"Retry\": \"重试\",\n" +
                    "  \"Account not authorized to delete\": \"账户无权删除\",\n" +
                    "  \"Start maintenance\": \"开始维护\",\n" +
                    "  \"Pause task\": \"暂停任务\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"您想完成这些维护项目吗？\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"您完成了多少（%）的工作？\",\n" +
                    "  \"Do you want to save the incident information ?\": \"您想保存事件信息吗？\",\n" +
                    "  \"Could not save data\": \"无法保存数据\",\n" +
                    "  \"Repair\": \"修理\",\n" +
                    "  \"Approved OK\": \"批准通过\",\n" +
                    "  \"Approved NG\": \"批准未通过\",\n" +
                    "  \"Machine history\": \"机器历史记录\",\n" +
                    "  \"List of tasks\": \"任务列表\",\n" +
                    "  \"Machine repair and maintenance history\": \"机器修理与维护历史\",\n" +
                    "  \"Machine type\": \"机器类型\",\n" +
                    "  \"DELETE ERROR\": \"删除错误\",\n" +
                    "  \"Do you want to delete this error?\": \"您想删除此错误吗？\",\n" +
                    "  \"Clear error failed\": \"清除错误失败\",\n" +
                    "  \"List of errors\": \"错误列表\",\n" +
                    "  \"Please select machine\": \"请选择机器\",\n" +
                    "  \"Please select error code\": \"请选择错误代码\",\n" +
                    "  \"Please enter error content\": \"请输入错误内容\",\n" +
                    "  \"Maintenance not scheduled yet\": \"维护尚未安排\",\n" +
                    "  \"Add material\": \"添加材料\",\n" +
                    "  \"Select warehouse supplies\": \"选择仓库物资\",\n" +
                    "  \"Required Completion Date\": \"要求完成日期\",\n" +
                    "  \"Select materials\": \"选择材料\",\n" +
                    "  \"Warehouse Dispatch Information\": \"仓库调拨信息\",\n" +
                    "  \"Warehouse Release Request\": \"仓库出库请求\",\n" +
                    "  \"Release date\": \"发布日期\",\n" +
                    "  \"Only notification\": \"仅通知\",\n" +
                    "  \"Notification and vibrate\": \"通知和振动\",\n" +
                    "  \"Notification, vibrate and ring\": \"通知、振动和铃声\",\n" +
                    "  \"Product of Meiko Automation\": \"Meiko Automation的产品\",\n" +
                    "  \"Please select maintenance category\": \"请选择维护类别\",\n" +
                    "  \"Please select at least one item that has not created a material export request.\": \"请至少选择一个尚未创建材料导出请求的项。\",\n" +
                    "  \"Blank material list\": \"空白材料清单\",\n" +
                    "  \"No completion date selected\": \"未选择完成日期\",\n" +
                    "  \"Enter the note requesting the release of stock\": \"输入请求释放库存的备注\",\n" +
                    "  \"Please select material\": \"请选择材料\",\n" +
                    "  \"Invalid quantity\": \"无效数量\",\n" +
                    "  \"Please select warehouse\": \"请选择仓库\",\n" +
                    "  \"Update failed warehouse release request status\": \"更新仓库出库请求状态失败\",\n" +
                    "  \"Creating a warehouse release request failed\": \"创建仓库出库请求失败\",\n" +
                    "  \"Save materials list failed\": \"保存材料清单失败\",\n" +
                    "  \"The application has been updated to the latest version\": \"应用程序已更新到最新版本\",\n" +
                    "  \"The new version\": \"新版本\",\n" +
                    "  \"is now available\": \"现已可用\",\n" +
                    "  \"Downloading\": \"下载中\",\n" +
                    "  \"Please wait...\": \"请稍候...\",\n" +
                    "  \"Current situation\": \"当前状况\",\n" +
                    "  \"Root cause\": \"根本原因\",\n" +
                    "  \"Countermeasure\": \"对策\",\n" +
                    "  \"Alarm setting\": \"警报设置\",\n" +
                    "  \"W/O Code\": \"W/O编码\",\n" +
                    "  \"Request Date\": \"申请日期\",\n" +
                    "  \"Select an item\": \"请选择一项\",\n" +
                    "  \"Enter process\": \"输入工序\",\n" +
                    "  \"Select type\": \"选择类型\",\n" +
                    "  \"Enter passed date\": \"输入经过天数\",\n" +
                    "  \"Enter request reason\": \"输入申请原因\",\n" +
                    "  \"Enter work order content\": \"输入工单内容\",\n" +
                    "  \"MA Status\": \"MA报告状态\",\n" +
                    "  \"Incomplete\": \"未完成\",\n" +
                    "  \"Completed\": \"完了\",\n" +
                    "  \"Overdure\": \"逾期\",\n" +
                    "  \"Canceled\": \"已取消\",\n" +
                    "  \"Machine Breakdown\": \"设备故障\",\n" +
                    "  \"Machine broken\": \"设备故障\",\n" +
                    "  \"Preparing operation\": \"准备作业\",\n" +
                    "  \"Stop due to shortage\": \"缺料停机\",\n" +
                    "  \"Stop by production plan\": \"按生产计划停机\",\n" +
                    "  \"Maintenance and repair\": \"保养与维修\",\n" +
                    "  \"Maintenance steps\": \"维护步骤\",\n" +
                    "  \"Click here\": \"点击这里\",\n" +
                    "  \"Material code\": \"材料代码\",\n" +
                    "  \"Material name\": \"材料名称\",\n" +
                    "  \"Account not authorized to delete\": \"账户无权删除\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"您完成了多少（%）的工作？\",\n" +
                    "  \"Do you want to create this warehouse request ?\": \"您想创建此仓库请求吗？\",\n" +
                    "  \"Machine repair and maintenance history\": \"机器修理与维护历史\",\n" +
                    "  \"Select warehouse supplies\": \"选择仓库物资\",\n" +
                    "  \"Notification, vibrate and ring\": \"通知、振动和铃声\",\n" +
                    "  \"Please select at least one item that has not created a material export request.\": \"请至少选择一个尚未创建材料导出请求的项。\",\n" +
                    "  \"Update failed warehouse release request status\": \"更新仓库出库请求状态失败\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"MES系统是否具有设备锁定功能？\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"您无权编辑此Work Order，或该工单已完成\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"您无权删除此Work Order，或该工单已完成\",\n" +
                    "  \"Delete Work Order\": \"删除Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"您确认删除WorkOrder %s 吗？\",\n" +
                    "  \"Deleting Work Order...\": \"正在删除Work Order...\",\n" +
                    "  \"Delete successful\": \"删除成功\",\n" +
                    "  \"Delete failed: %s\": \"删除失败: %s\",\n" +
                    "  \"Delete error: %s\": \"删除错误: %s\",\n" +
                    "  \"Storage permission denied\": \"存储权限被拒绝\",\n" +
                    "  \"Unknown sources permission denied\": \"未知来源安装权限被拒绝\",\n" +
                    "  \"Please enter Machine and Requester\": \"请输入完整的机器和申请人信息\",\n" +
                    "  \"Processing data...\": \"正在处理数据...\",\n" +
                    "  \"Add Work Order successful\": \"新增Work Order成功！\",\n" +
                    "  \"Updating...\": \"正在更新...\",\n" +
                    "  \"Update successful\": \"更新成功\",\n" +
                    "  \"Update failed: %s\": \"更新失败: %s\",\n" +
                    "  \"Update error: %s\": \"更新错误: %s\",\n" +
                    "  \"Request reason is required\": \"必须输入申请原因\",\n" +
                    "  \"Update Work Order\": \"更新Work Order\",\n" +
                    "  \"Edit Work Order\": \"编辑Work Order\",\n" +
                    "  \"Required fields missing\": \"缺少必填字段\",\n" +
                    "  \"status update not sent\": \"不发送状态更新\",\n" +
                    "  \"System encountered an error while saving\": \"保存时系统错误\",\n" +
                    "  \"Item save error\": \"保存项目错误\",\n" +
                    "  \"Maintenance\": \"定期保守\",\n" +
                    "  \"Language changed successfully\": \"语言修改成功\",\n" +
                    "  \"Action\": \"操作\"\n" +
                    "}\n";



    public static void setLang(View rootView)
    {
        init(rootView.getContext());
        applyLangRecursive(rootView);
    }

    private static void applyLangRecursive(View rootView) {
        if (rootView instanceof TextView) {
            TextView textView = (TextView) rootView;
            String currentText = textView.getText() != null ? textView.getText().toString() : "";
            String sourceText = resolveSourceText(
                    textView,
                    R.id.tag_i18n_text_key,
                    currentText
            );
            if (!sourceText.isEmpty()) {
                textView.setText(i18n(sourceText));
            }

            CharSequence hint = textView.getHint();
            String currentHint = hint != null ? hint.toString() : "";
            String sourceHint = resolveSourceText(
                    textView,
                    R.id.tag_i18n_hint_key,
                    currentHint
            );
            if (!sourceHint.isEmpty()) {
                textView.setHint(i18n(sourceHint));
            }
        } else if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                applyLangRecursive(child);
            }
        }
    }

    private static String resolveSourceText(View view, int keyTagResId, String currentValue) {
        if (currentValue == null) {
            currentValue = "";
        }

        Object tagObj = view.getTag(keyTagResId);
        String taggedSource = tagObj instanceof String ? (String) tagObj : "";

        if (taggedSource.isEmpty()) {
            if (!currentValue.isEmpty()) {
                view.setTag(keyTagResId, currentValue);
            }
            return currentValue;
        }

        String expectedRendered = i18n(taggedSource);
        // If current value no longer matches what this key should render,
        // treat current value as a new canonical key set by business logic.
        if (!currentValue.isEmpty()
                && !currentValue.equals(expectedRendered)
                && !currentValue.equals(taggedSource)) {
            view.setTag(keyTagResId, currentValue);
            return currentValue;
        }

        return taggedSource;
    }
}
