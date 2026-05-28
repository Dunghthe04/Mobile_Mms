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
            case "vi": return "Tiếng Việt";
            case "en": return "English";
            case "ja": return "日本";
            case "ch": return "Chinese";
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
            case "en":  jsonString = english;   break;
            case "ja":  jsonString = japanese;  break;
            case "ch":  jsonString = chinese;   break;
            case "vi":
            default:    jsonString = vietnamese; break;
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            builtInLanguageMap.put(langCode, json);
            return json;
        } catch (JSONException ignored) {
            return null;
        }
    }

    public static void setLanguageCode(String outlanguageCode) {
        languageCode = outlanguageCode;
    }

    private static void fetchLanguagesFromAPI(Context context) {
        isLoadedFromApi = true;
        ConfigManager configManager = new ConfigManager(context);
        String baseUrl = configManager.getProperty("server_url");
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
                    while ((line = in.readLine()) != null) response.append(line);
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

        for (String lang : new String[]{"vi", "en", "ja", "ch"}) {
            if (!languageMap.containsKey(lang)) {
                String cachedJson = prefs.getString("lang_json_" + lang, null);
                if (cachedJson != null) {
                    try { languageMap.put(lang, new JSONObject(cachedJson)); }
                    catch (JSONException e) { e.printStackTrace(); }
                }
            }
        }

        if (!isLoadedFromApi) fetchLanguagesFromAPI(context);

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
        languageMap.clear();
        init(context);
    }

    // ============================================================
    // ENGLISH
    // ============================================================
    private static final String english =
            "{\n" +
                    // ── Keys mới bổ sung cho WorkOrderEntryDialog ──────────────
                    "  \"Occurrence Time\": \"Occurrence Time\",\n" +
                    "  \"Modification Time\": \"Modification Time\",\n" +
                    "  \"Editor\": \"Editor\",\n" +
                    "  \"Result\": \"Result\",\n" +
                    "  \"Select Cause\": \"Select Cause\",\n" +
                    "  \"Component Wear\": \"Component Wear\",\n" +
                    "  \"Electrical Control Failure\": \"Electrical Control Failure\",\n" +
                    "  \"Operator Error\": \"Operator Error\",\n" +
                    "  \"Initial Installation\": \"Initial Installation\",\n" +
                    "  \"Setting Change\": \"Setting Change\",\n" +
                    "  \"Misalignment\": \"Misalignment\",\n" +
                    "  \"Unknown Cause\": \"Unknown Cause\",\n" +
                    "  \"Other Cause\": \"Other Cause\",\n" +
                    "  \"Start Time\": \"Start Time\",\n" +
                    "  \"End Time\": \"End Time\",\n" +
                    "  \"Current Status\": \"Current Status\",\n" +
                    "  \"Cause Category\": \"Cause Category\",\n" +
                    "  \"Root Cause\": \"Root Cause\",\n" +
                    "  \"Impact (Product/Equipment)\": \"Impact (Product/Equipment)\",\n" +
                    "  \"Action Taken\": \"Action Taken\",\n" +
                    "  \"Status\": \"Status\",\n" +
                    "  \"Choose File\": \"Choose File\",\n" +
                    "  \"Upload\": \"Upload\",\n" +
                    "  \"Attachment\": \"Attachment\",\n" +
                    "  \"No attachment file\": \"No attachment file\",\n" +
                    "  \"Create WMS Request\": \"Create WMS Request\",\n" +
                    "  \"Materials\": \"Materials\",\n" +
                    "  \"No materials available\": \"No materials available\",\n" +
                    "  \"Close\": \"Close\",\n" +
                    "  \"Save\": \"Save\",\n" +
                    "  \"1.Information\": \"1. Information\",\n" +
                    "  \"2.Materials\": \"2. Materials\",\n" +
                    "  \"Creating warehouse release request...\": \"Creating warehouse release request...\",\n" +
                    "  \"Error creating warehouse release request\": \"Error creating warehouse release request\",\n" +
                    "  \"No response from server\": \"No response from server\",\n" +
                    // ── Keys cũ giữ nguyên ─────────────────────────────────────
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
                    "  \"W/O Management\": \"W/O Management And Maintenance\",\n" +
                    "  \"Select function\": \"Select function\",\n" +
                    "  \"Create a work order or enter work order and maintenance data\": \"Create a work order or enter work order and maintenance data\",\n" +
                    "  \"Add Work Order\": \"Add Work Order And Maintenance\",\n" +
                    "  \"ADD Work Order\": \"Add Work Order\",\n" +
                    "  \"Create a new work order\": \"Create a new work order\",\n" +
                    "  \"Enter Work Order Data\": \"Enter Work Order Data And Maintenance\",\n" +
                    "  \"Manage work orders and perform maintenance\": \"Manage work orders and perform maintenance\",\n" +
                    "  \"Language\": \"Language\",\n" +
                    "  \"No attachment file to download\": \"No attachment file to download\",\n" +
                    "  \"Select attachment document\": \"Select attachment document\",\n" +
                    "  \"Please select a file before uploading\": \"Please select a file before uploading\",\n" +
                    "  \"Work Order code not found for upload\": \"Work Order code not found for upload\",\n" +
                    "  \"Uploading document...\": \"Uploading document...\",\n" +
                    "  \"Document uploaded successfully\": \"Document uploaded successfully\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"Uploaded successfully but failed to get file name\",\n" +
                    "  \"Upload failed\": \"Upload failed\",\n" +
                    "  \"User login information not found\": \"User login information not found\",\n" +
                    "  \"No materials available to create warehouse request\": \"No materials available to create warehouse request\",\n" +
                    "  \"Error parsing material list\": \"Error parsing material list\",\n" +
                    "  \"No valid materials for warehouse release\": \"No valid materials for warehouse release\",\n" +
                    "  \"Warehouse request for Work Order\": \"Warehouse request for Work Order\",\n" +
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
                    "  \"Login failed\": \"Login failed\",\n" +
                    "  \"Please check your username or password\": \"Please check your username or password\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Maintenance and Repair Schedule\",\n" +
                    "  \"Category Code\": \"Category Code\",\n" +
                    "  \"Current situation\": \"Current situation\",\n" +
                    "  \"Root cause\": \"Root cause\",\n" +
                    "  \"Action taken\": \"Action taken\",\n" +
                    "  \"Countermeasure\": \"Countermeasure\",\n" +
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
                    "  \"Minimum\": \"Minimum\",\n" +
                    "  \"Maximum\": \"Maximum\",\n" +
                    "  \"Has\": \"Has\",\n" +
                    "  \"child items\": \"child items\",\n" +
                    "  \"Visual inspection\": \"Visual inspection\",\n" +
                    "  \"Lịch sử\": \"History\",\n" +
                    "  \"Vật tư\": \"Material\",\n" +
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
                    "  \"System error while saving\": \"System error while saving\",\n" +
                    "  \"W/O Code\": \"W/O Code\",\n" +
                    "  \"Request Date\": \"Request Date\",\n" +
                    "  \"Select an item\": \"Select an item\",\n" +
                    "  \"Enter process\": \"Enter process\",\n" +
                    "  \"Select type\": \"Select type\",\n" +
                    "  \"Enter request reason\": \"Enter request reason\",\n" +
                    "  \"Enter work order content\": \"Enter work order content\",\n" +
                    "  \"MA Status\": \"MA Status\",\n" +
                    "  \"Completed\": \"Completed\",\n" +
                    "  \"Incomplete\": \"Incomplete\",\n" +
                    "  \"Canceled\": \"Canceled\",\n" +
                    "  \"Machine Breakdown\": \"Machine Breakdown\",\n" +
                    "  \"Maintenance and repair\": \"Maintenance and repair\",\n" +
                    "  \"Add\": \"Add\",\n" +
                    "  \"Time arises\": \"Time arises\",\n" +
                    "  \"Overdue\": \"Overdue\",\n" +
                    "  \"Action\": \"Action\"\n" +
                    "}\n";

    // ============================================================
    // VIETNAMESE
    // ============================================================
    private static final String vietnamese =
            "{\n" +
                    // ── Keys mới bổ sung ────────────────────────────────────────
                    "  \"Occurrence Time\": \"Thời gian phát sinh\",\n" +
                    "  \"Modification Time\": \"Thời gian chỉnh sửa\",\n" +
                    "  \"Editor\": \"Người chỉnh sửa\",\n" +
                    "  \"Result\": \"Kết quả\",\n" +
                    "  \"Select Cause\": \"Chọn nguyên nhân\",\n" +
                    "  \"Component Wear\": \"Mòn linh kiện\",\n" +
                    "  \"Electrical Control Failure\": \"Lỗi điều khiển điện\",\n" +
                    "  \"Operator Error\": \"Lỗi vận hành\",\n" +
                    "  \"Initial Installation\": \"Lắp đặt ban đầu\",\n" +
                    "  \"Setting Change\": \"Thay đổi cài đặt\",\n" +
                    "  \"Misalignment\": \"Lệch căn chỉnh\",\n" +
                    "  \"Unknown Cause\": \"Nguyên nhân không xác định\",\n" +
                    "  \"Other Cause\": \"Nguyên nhân khác\",\n" +
                    "  \"Start Time\": \"Thời gian bắt đầu thực hiện\",\n" +
                    "  \"End Time\": \"Thời gian kết thúc thực hiện\",\n" +
                    "  \"Current Status\": \"Hiện trạng\",\n" +
                    "  \"Cause Category\": \"Phân loại nguyên nhân\",\n" +
                    "  \"Root Cause\": \"Nguyên nhân\",\n" +
                    "  \"Impact (Product/Equipment)\": \"Ảnh hưởng (Sản phẩm/Thiết bị)\",\n" +
                    "  \"Action Taken\": \"Nội dung xử lý\",\n" +
                    "  \"Status\": \"Trạng thái\",\n" +
                    "  \"Choose File\": \"Chọn file\",\n" +
                    "  \"Upload\": \"Tải lên\",\n" +
                    "  \"Attachment\": \"Tài liệu đính kèm\",\n" +
                    "  \"No attachment file\": \"Chưa có tài liệu đính kèm\",\n" +
                    "  \"Create WMS Request\": \"Tạo yêu cầu xuất kho\",\n" +
                    "  \"Materials\": \"Vật tư\",\n" +
                    "  \"No materials available\": \"Không có vật tư đi kèm\",\n" +
                    "  \"Close\": \"Đóng\",\n" +
                    "  \"Save\": \"Lưu\",\n" +
                    "  \"1.Information\": \"1.Thông tin\",\n" +
                    "  \"2.Materials\": \"2.Vật tư\",\n" +
                    "  \"Creating warehouse release request...\": \"Đang tạo yêu cầu xuất kho...\",\n" +
                    "  \"Error creating warehouse release request\": \"Lỗi khi tạo yêu cầu xuất kho\",\n" +
                    "  \"No response from server\": \"Không phản hồi từ máy chủ\",\n" +
                    // ── Keys cũ ─────────────────────────────────────────────────
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
                    "  \"Add Work Order\": \"Thêm Work Order\",\n" +
                    "  \"ADD Work Order\": \"Nhập thông tin Work Order\",\n" +
                    "  \"Create a new work order\": \"Tạo mới một Work Order\",\n" +
                    "  \"Enter Work Order Data\": \"Nhập kết quả Work Order và hạng mục bảo dưỡng\",\n" +
                    "  \"Language\": \"Ngôn ngữ\",\n" +
                    "  \"No attachment file to download\": \"Không có tệp đính kèm để tải về\",\n" +
                    "  \"Select attachment document\": \"Chọn tài liệu đính kèm\",\n" +
                    "  \"Please select a file before uploading\": \"Vui lòng chọn file trước khi tải lên\",\n" +
                    "  \"Work Order code not found for upload\": \"Không tìm thấy mã Work Order để tải lên\",\n" +
                    "  \"Uploading document...\": \"Đang tải lên tài liệu...\",\n" +
                    "  \"Document uploaded successfully\": \"Tải lên tài liệu thành công\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"Tải lên thành công nhưng không lấy được tên file\",\n" +
                    "  \"Upload failed\": \"Tải lên thất bại\",\n" +
                    "  \"User login information not found\": \"Không tìm thấy thông tin đăng nhập người dùng\",\n" +
                    "  \"No materials available to create warehouse request\": \"Không có vật tư nào để tạo yêu cầu xuất kho\",\n" +
                    "  \"Error parsing material list\": \"Lỗi phân tích danh sách vật tư\",\n" +
                    "  \"No valid materials for warehouse release\": \"Không có vật tư hợp lệ để xuất kho\",\n" +
                    "  \"Warehouse request for Work Order\": \"Yêu cầu xuất kho cho Work Order\",\n" +
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
                    "  \"Time\": \"Thời gian\",\n" +
                    "  \"On hold\": \"Tạm hoãn\",\n" +
                    "  \"Approve\": \"Phê duyệt\",\n" +
                    "  \"Maintenance management system\": \"Hệ thống quản lý bảo trì\",\n" +
                    "  \"User name\": \"Tên đăng nhập\",\n" +
                    "  \"Password\": \"Mật khẩu\",\n" +
                    "  \"Login_Singin\": \"ĐĂNG NHẬP\",\n" +
                    "  \"CLOSE\": \"ĐÓNG\",\n" +
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"CÓ\",\n" +
                    "  \"NO\": \"KHÔNG\",\n" +
                    "  \"CANCEL\": \"THOÁT\",\n" +
                    "  \"BACK\": \"TRỞ LẠI\",\n" +
                    "  \"STOP\": \"DỪNG\",\n" +
                    "  \"SAVE\": \"LƯU\",\n" +
                    "  \"Agree\": \"Đồng ý\",\n" +
                    "  \"Deny\": \"Từ chối\",\n" +
                    "  \"Select\": \"Chọn\",\n" +
                    "  \"Login failed\": \"Đăng nhập không thành công\",\n" +
                    "  \"System error while saving\": \"Hệ thống gặp lỗi khi lưu\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Lịch bảo trì - sửa chữa\",\n" +
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
                    "  \"Enter request reason\": \"Nhập lý do yêu cầu\",\n" +
                    "  \"Enter work order content\": \"Nhập nội dung Work Order\",\n" +
                    "  \"Incomplete\": \"Chưa hoàn thành\",\n" +
                    "  \"Canceled\": \"Đã hủy\",\n" +
                    "  \"Machine Breakdown\": \"Máy hỏng\",\n" +
                    "  \"Maintenance and repair\": \"Bảo dưỡng, sửa chữa\",\n" +
                    "  \"Maintenance\": \"Bảo dưỡng\",\n" +
                    "  \"Completed\": \"Đã thực hiện\",\n" +
                    "  \"Overdue\": \"Quá hạn\",\n" +
                    "  \"Minimum\": \"Cận dưới\",\n" +
                    "  \"Maximum\": \"Cận trên\",\n" +
                    "  \"Has\": \"Có\",\n" +
                    "  \"child items\": \"mục con\",\n" +
                    "  \"Visual inspection\": \"Tiêu chuẩn ngoại quan\",\n" +
                    "  \"Nhập dữ liệu\": \"Nhập dữ liệu\",\n" +
                    "  \"Đóng\": \"Đóng\",\n" +
                    "  \"Lưu\": \"Lưu\",\n" +
                    "  \"Không có vật tư đi kèm\": \"Không có vật tư đi kèm\",\n" +
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
                    "  \"Lịch sử\": \"Lịch sử\",\n" +
                    "  \"Vật tư\": \"Vật tư\",\n" +
                    "  \"Action\": \"Thao tác\"\n" +
                    "}\n";

    // ============================================================
    // JAPANESE
    // ============================================================
    private static final String japanese =
            "{\n" +
                    // ── Keys mới bổ sung ────────────────────────────────────────
                    "  \"Occurrence Time\": \"発生時間\",\n" +
                    "  \"Modification Time\": \"修正時間\",\n" +
                    "  \"Editor\": \"修正者\",\n" +
                    "  \"Result\": \"結果\",\n" +
                    "  \"Start Time\": \"開始時間\",\n" +
                    "  \"Select Cause\": \"原因を選択\",\n" +
                    "  \"Component Wear\": \"部品磨耗\",\n" +
                    "  \"Electrical Control Failure\": \"電気制御不良\",\n" +
                    "  \"Operator Error\": \"オペレーターミス\",\n" +
                    "  \"Initial Installation\": \"初期取付\",\n" +
                    "  \"Setting Change\": \"設定変更\",\n" +
                    "  \"Misalignment\": \"芯ずれ\",\n" +
                    "  \"Unknown Cause\": \"原因不明\",\n" +
                    "  \"Other Cause\": \"その他の原因\",\n" +
                    "  \"End Time\": \"終了時間\",\n" +
                    "  \"Current Status\": \"現在の状況\",\n" +
                    "  \"Cause Category\": \"原因分類\",\n" +
                    "  \"Root Cause\": \"根本原因\",\n" +
                    "  \"Impact (Product/Equipment)\": \"影響 (製品/設備)\",\n" +
                    "  \"Action Taken\": \"対処内容\",\n" +
                    "  \"Status\": \"ステータス\",\n" +
                    "  \"Choose File\": \"ファイル選択\",\n" +
                    "  \"Upload\": \"アップロード\",\n" +
                    "  \"Attachment\": \"添付ファイル\",\n" +
                    "  \"No attachment file\": \"添付ファイルなし\",\n" +
                    "  \"Create WMS Request\": \"出庫要求を作成\",\n" +
                    "  \"Materials\": \"資材\",\n" +
                    "  \"No materials available\": \"資材がありません\",\n" +
                    "  \"Close\": \"閉じる\",\n" +
                    "  \"Save\": \"保存\",\n" +
                    "  \"1.Information\": \"1. 情報\",\n" +
                    "  \"2.Materials\": \"2. 資材\",\n" +
                    "  \"Creating warehouse release request...\": \"出庫要求を作成中...\",\n" +
                    "  \"Error creating warehouse release request\": \"出庫要求の作成エラー\",\n" +
                    "  \"No response from server\": \"サーバーから応答がありません\",\n" +
                    // ── Keys cũ ─────────────────────────────────────────────────
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
                    "  \"Add Work Order\": \"新しいWork Orderを作成\",\n" +
                    "  \"Enter Work Order Data\": \"作業データ入力\",\n" +
                    "  \"Language\": \"言語設定\",\n" +
                    "  \"No attachment file to download\": \"ダウンロードする添付ファイルがありません\",\n" +
                    "  \"Select attachment document\": \"添付書類を選択してください\",\n" +
                    "  \"Please select a file before uploading\": \"アップロードする前にファイルを選択してください\",\n" +
                    "  \"Work Order code not found for upload\": \"アップロードするワークオーダーコードが見つかりません\",\n" +
                    "  \"Uploading document...\": \"書類をアップロード中...\",\n" +
                    "  \"Document uploaded successfully\": \"書類のアップロードが成功しました\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"アップロードは成功しましたがファイル名が取得できません\",\n" +
                    "  \"Upload failed\": \"アップロードに失敗しました\",\n" +
                    "  \"User login information not found\": \"ユーザーログイン情報が見つかりません\",\n" +
                    "  \"No materials available to create warehouse request\": \"出庫要求を作成する資材がありません\",\n" +
                    "  \"Error parsing material list\": \"資材リストの解析エラー\",\n" +
                    "  \"No valid materials for warehouse release\": \"出庫に有効な資材がありません\",\n" +
                    "  \"Warehouse request for Work Order\": \"ワークオーダーの出庫要求\",\n" +
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
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"はい\",\n" +
                    "  \"NO\": \"いいえ\",\n" +
                    "  \"CANCEL\": \"キャンセル\",\n" +
                    "  \"BACK\": \"戻る\",\n" +
                    "  \"STOP\": \"停止\",\n" +
                    "  \"SAVE\": \"保存\",\n" +
                    "  \"DELETE\": \"削除\",\n" +
                    "  \"Agree\": \"同意する\",\n" +
                    "  \"Deny\": \"拒否する\",\n" +
                    "  \"Select\": \"選択\",\n" +
                    "  \"Login failed\": \"ログインに失敗しました\",\n" +
                    "  \"System error while saving\": \"保存中にシステムエラーが発生しました\",\n" +
                    "  \"Countermeasure\": \"対策\",\n" +
                    "  \"Alarm setting\": \"アラーム設定\",\n" +
                    "  \"W/O Code\": \"W/Oコード\",\n" +
                    "  \"Request Date\": \"依頼日\",\n" +
                    "  \"Work Order Content\": \"W/O内容\",\n" +
                    "  \"Week\": \"週\",\n" +
                    "  \"Month\": \"月\",\n" +
                    "  \"Delete Work Order\": \"Work Order削除\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"WorkOrder %s を削除してもよろしいですか？\",\n" +
                    "  \"Deleting Work Order...\": \"Work Orderを削除中...\",\n" +
                    "  \"Delete successful\": \"削除成功\",\n" +
                    "  \"Delete failed: %s\": \"削除失敗: %s\",\n" +
                    "  \"Delete error: %s\": \"削除エラー: %s\",\n" +
                    "  \"Load completed\": \"データの読み込みが完了しました\",\n" +
                    "  \"Please enter Machine and Requester\": \"設備と依頼者を入力してください\",\n" +
                    "  \"Processing data...\": \"データ処理中...\",\n" +
                    "  \"Add Work Order successful\": \"Work Orderの追加に成功しました\",\n" +
                    "  \"Updating...\": \"更新中...\",\n" +
                    "  \"Update successful\": \"更新成功\",\n" +
                    "  \"Update failed: %s\": \"更新失敗: %s\",\n" +
                    "  \"Update error: %s\": \"更新エラー: %s\",\n" +
                    "  \"Request reason is required\": \"依頼理由は必須です\",\n" +
                    "  \"Update Work Order\": \"Work Orderを更新\",\n" +
                    "  \"Edit Work Order\": \"Work Orderを編集\",\n" +
                    "  \"Select an item\": \"項目を選択\",\n" +
                    "  \"Enter process\": \"工程を入力\",\n" +
                    "  \"Select type\": \"種別を選択\",\n" +
                    "  \"Enter request reason\": \"依頼理由を入力\",\n" +
                    "  \"Enter work order content\": \"W/O内容を入力\",\n" +
                    "  \"MA Status\": \"MA報告状態\",\n" +
                    "  \"Completed\": \"完了\",\n" +
                    "  \"Incomplete\": \"未完了\",\n" +
                    "  \"Overdue\": \"期限切れ\",\n" +
                    "  \"Machine Breakdown\": \"設備故障\",\n" +
                    "  \"Maintenance and repair\": \"保全・修理\",\n" +
                    "  \"Maintenance\": \"メンテナンス\",\n" +
                    "  \"Minimum\": \"最小\",\n" +
                    "  \"Maximum\": \"最大\",\n" +
                    "  \"Has\": \"あり\",\n" +
                    "  \"child items\": \"子項目\",\n" +
                    "  \"Visual inspection\": \"外観確認\",\n" +
                    "  \"Note\": \"メモ\",\n" +
                    "  \"Time arises\": \"発生時間\",\n" +
                    "  \"Nhập dữ liệu\": \"作業データ入力\",\n" +
                    "  \"Đóng\": \"閉じる\",\n" +
                    "  \"Lưu\": \"保存\",\n" +
                    "  \"Không có vật tư đi kèm\": \"添付資材なし\",\n" +
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
                    "  \"Lịch sử\": \"履歴\",\n" +
                    "  \"Action\": \"操作\"\n" +
                    "}\n";

    // ============================================================
    // CHINESE
    // ============================================================
    private static final String chinese =
            "{\n" +
                    // ── Keys mới bổ sung ────────────────────────────────────────
                    "  \"Occurrence Time\": \"发生时间\",\n" +
                    "  \"Modification Time\": \"修改时间\",\n" +
                    "  \"Editor\": \"修改人\",\n" +
                    "  \"Result\": \"结果\",\n" +
                    "  \"Start Time\": \"开始时间\",\n" +
                    "  \"End Time\": \"结束时间\",\n" +
                    "  \"Current Status\": \"当前状态\",\n" +
                    "  \"Cause Category\": \"原因分类\",\n" +
                    "  \"Select Cause\": \"选择原因\",\n" +
                    "  \"Component Wear\": \"零件磨损\",\n" +
                    "  \"Electrical Control Failure\": \"电控故障\",\n" +
                    "  \"Operator Error\": \"操作失误\",\n" +
                    "  \"Initial Installation\": \"初次安装\",\n" +
                    "  \"Setting Change\": \"参数变更\",\n" +
                    "  \"Misalignment\": \"对位偏差\",\n" +
                    "  \"Unknown Cause\": \"原因不明\",\n" +
                    "  \"Other Cause\": \"其他原因\",\n" +
                    "  \"Root Cause\": \"根本原因\",\n" +
                    "  \"Impact (Product/Equipment)\": \"影响 (产品/设备)\",\n" +
                    "  \"Action Taken\": \"处理内容\",\n" +
                    "  \"Status\": \"状态\",\n" +
                    "  \"Choose File\": \"选择文件\",\n" +
                    "  \"Upload\": \"上传\",\n" +
                    "  \"Attachment\": \"附件\",\n" +
                    "  \"No attachment file\": \"暂无附件\",\n" +
                    "  \"Create WMS Request\": \"创建出库申请\",\n" +
                    "  \"Materials\": \"材料\",\n" +
                    "  \"No materials available\": \"暂无材料\",\n" +
                    "  \"Close\": \"关闭\",\n" +
                    "  \"Save\": \"保存\",\n" +
                    "  \"1.Information\": \"1. 信息\",\n" +
                    "  \"2.Materials\": \"2. 材料\",\n" +
                    "  \"Creating warehouse release request...\": \"正在创建出库申请...\",\n" +
                    "  \"Error creating warehouse release request\": \"创建出库申请错误\",\n" +
                    "  \"No response from server\": \"服务器未响应\",\n" +
                    // ── Keys cũ ─────────────────────────────────────────────────
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
                    "  \"Add Work Order\": \"新增工单\",\n" +
                    "  \"Enter Work Order Data\": \"录入工单与保养项目结果\",\n" +
                    "  \"Language\": \"语言选择\",\n" +
                    "  \"No attachment file to download\": \"没有可下载的附件\",\n" +
                    "  \"Select attachment document\": \"选择附加文件\",\n" +
                    "  \"Please select a file before uploading\": \"请在上传前选择文件\",\n" +
                    "  \"Work Order code not found for upload\": \"找不到用于上传工单代码\",\n" +
                    "  \"Uploading document...\": \"正在上传文件...\",\n" +
                    "  \"Document uploaded successfully\": \"文件上传成功\",\n" +
                    "  \"Uploaded successfully but failed to get file name\": \"上传成功但未能获取文件名\",\n" +
                    "  \"Upload failed\": \"上传失败\",\n" +
                    "  \"User login information not found\": \"找不到用户登录信息处理\",\n" +
                    "  \"No materials available to create warehouse request\": \"没有物料可用于创建出库申请\",\n" +
                    "  \"Error parsing material list\": \"物料列表解析错误\",\n" +
                    "  \"No valid materials for warehouse release\": \"没有有效的出库物料\",\n" +
                    "  \"Warehouse request for Work Order\": \"工单出库申请\",\n" +
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
                    "  \"OK\": \"确定\",\n" +
                    "  \"YES\": \"是\",\n" +
                    "  \"NO\": \"否\",\n" +
                    "  \"CANCEL\": \"取消\",\n" +
                    "  \"BACK\": \"返回\",\n" +
                    "  \"STOP\": \"停止\",\n" +
                    "  \"SAVE\": \"保存\",\n" +
                    "  \"Agree\": \"同意\",\n" +
                    "  \"Deny\": \"拒绝\",\n" +
                    "  \"Select\": \"选择\",\n" +
                    "  \"Login failed\": \"登录失败\",\n" +
                    "  \"System error while saving\": \"保存时系统错误\",\n" +
                    "  \"Countermeasure\": \"对策\",\n" +
                    "  \"Alarm setting\": \"警报设置\",\n" +
                    "  \"W/O Code\": \"W/O编码\",\n" +
                    "  \"Request Date\": \"申请日期\",\n" +
                    "  \"Work Order Content\": \"工单内容\",\n" +
                    "  \"Week\": \"周\",\n" +
                    "  \"Month\": \"月\",\n" +
                    "  \"Delete Work Order\": \"删除Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"您确认删除WorkOrder %s 吗？\",\n" +
                    "  \"Deleting Work Order...\": \"正在删除Work Order...\",\n" +
                    "  \"Delete successful\": \"删除成功\",\n" +
                    "  \"Delete failed: %s\": \"删除失败: %s\",\n" +
                    "  \"Delete error: %s\": \"删除错误: %s\",\n" +
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
                    "  \"Select an item\": \"请选择一项\",\n" +
                    "  \"Enter process\": \"输入工序\",\n" +
                    "  \"Select type\": \"选择类型\",\n" +
                    "  \"Enter request reason\": \"输入申请原因\",\n" +
                    "  \"Enter work order content\": \"输入工单内容\",\n" +
                    "  \"MA Status\": \"MA报告状态\",\n" +
                    "  \"Incomplete\": \"未完成\",\n" +
                    "  \"Completed\": \"完了\",\n" +
                    "  \"Canceled\": \"已取消\",\n" +
                    "  \"Machine Breakdown\": \"设备故障\",\n" +
                    "  \"Maintenance and repair\": \"保养与维修\",\n" +
                    "  \"Maintenance\": \"定期保守\",\n" +
                    "  \"Overdue\": \"已逾期\",\n" +
                    "  \"Minimum\": \"最小值\",\n" +
                    "  \"Maximum\": \"最大值\",\n" +
                    "  \"Has\": \"有\",\n" +
                    "  \"child items\": \"子项目\",\n" +
                    "  \"Visual inspection\": \"外观检查\",\n" +
                    "  \"Note\": \"备注\",\n" +
                    "  \"Time arises\": \"发生时间\",\n" +
                    "  \"Nhập dữ liệu\": \"录入数据\",\n" +
                    "  \"Đóng\": \"关闭\",\n" +
                    "  \"Lưu\": \"保存\",\n" +
                    "  \"Không có vật tư đi kèm\": \"暂无材料\",\n" +
                    "  \"Hạng mục\": \"项目\",\n" +
                    "  \"Phụ trách\": \"负责人\",\n" +
                    "  \"Thực hiện\": \"执行\",\n" +
                    "  \"Dự kiến\": \"预计\",\n" +
                    "  \"Xong\": \"完成\",\n" +
                    "  \"Tạm hoãn\": \"暂缓\",\n" +
                    "  \"Tiến hành kiểm tra\": \"输入结果\",\n" +
                    "  \"Chưa làm\": \"未执行\",\n" +
                    "  \"Đã làm\": \"已执行\",\n" +
                    "  \"Quá hạn\": \"已逾期\",\n" +
                    "  \"Lịch sử\": \"历史记录\",\n" +
                    "  \"Language changed successfully\": \"语言修改成功\",\n" +
                    "  \"Action\": \"操作\"\n" +
                    "}\n";

    // ============================================================
    // setLang / applyLangRecursive (giữ nguyên logic gốc)
    // ============================================================
    public static void setLang(View rootView) {
        init(rootView.getContext());
        applyLangRecursive(rootView);
    }

    private static void applyLangRecursive(View rootView) {
        if (rootView instanceof TextView) {
            TextView textView = (TextView) rootView;
            String currentText = textView.getText() != null ? textView.getText().toString() : "";
            String sourceText = resolveSourceText(textView, R.id.tag_i18n_text_key, currentText);
            if (!sourceText.isEmpty()) textView.setText(i18n(sourceText));

            CharSequence hint = textView.getHint();
            String currentHint = hint != null ? hint.toString() : "";
            String sourceHint = resolveSourceText(textView, R.id.tag_i18n_hint_key, currentHint);
            if (!sourceHint.isEmpty()) textView.setHint(i18n(sourceHint));
        } else if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyLangRecursive(viewGroup.getChildAt(i));
            }
        }
    }

    private static String resolveSourceText(View view, int keyTagResId, String currentValue) {
        if (currentValue == null) currentValue = "";
        Object tagObj = view.getTag(keyTagResId);
        String taggedSource = tagObj instanceof String ? (String) tagObj : "";

        if (taggedSource.isEmpty()) {
            if (!currentValue.isEmpty()) view.setTag(keyTagResId, currentValue);
            return currentValue;
        }

        String expectedRendered = i18n(taggedSource);
        if (!currentValue.isEmpty()
                && !currentValue.equals(expectedRendered)
                && !currentValue.equals(taggedSource)) {
            view.setTag(keyTagResId, currentValue);
            return currentValue;
        }
        return taggedSource;
    }
}