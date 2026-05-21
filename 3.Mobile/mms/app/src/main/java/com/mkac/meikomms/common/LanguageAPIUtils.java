package com.mkac.meikomms.common;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"MAINTENANCE MANAGEMENT SYSTEM\",\n" +
                    "  \"Maintenance management system\": \"Maintenance management system\",\n" +
                    "  \"User name\": \"User name\",\n" +
                    "  \"Reset\": \"Đặt lại\",\n" +
                    "  \"Password\": \"Password\",\n" +
                    "  \"LOGOUT\": \"LOGOUT\",\n" +
                    "  \"Login_Singin\": \"LOGIN\",\n" +
                    "  \"CLOSE\": \"CLOSE\",\n" +
                    "  \"EXIT\": \"EXIT\",\n" +
                    "  \"OK\": \"OK\",\n" +
                    "  \"YES\": \"YES\",\n" +
                    "  \"NO\": \"NO\",\n" +
                    "  \"CANCEL\":\"CANCEL\",\n" +
                    "  \"CONTINUE\":\"CONTINUE\",\n" +
                    "  \"BACK\": \"BACK\",\n" +
                    "  \"STOP\":\"STOP\",\n" +
                    "  \"SAVE\":\"SAVE\",\n" +
                    "  \"DELETE\": \"DELETE\",\n" +
                    "  \"Agree\": \"Agree\",\n" +
                    "  \"Deny\": \"Deny\",\n" +
                    "  \"Select\": \"Select\",\n" +
                    "  \"Fill in the login information\": \"Fill in the login information\",\n" +
                    "  \"Login failed\": \"Login failed\",\n" +
                    "  \"Please check your username or password\": \"Please check your username or password\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Maintenance and Repair Schedule\",\n" +
                    "  \"Category Name\": \"Category Name\",\n" +
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
                    "  \"W/O Management\": \"W/O Management\",\n" +
                    "  \"W/O code\": \"W/O code\",\n" +
                    "  \"Request date\": \"Request date\",\n" +
                    "  \"Machine\": \"Machine\",\n" +
                    "  \"Process\": \"Process\",\n" +
                    "  \"Work type\": \"Work type\",\n" +
                    "  \"Creator\": \"Creator\",\n" +
                    "  \"Requester\": \"Requester\",\n" +
                    "  \"Elapsed days\": \"Elapsed days\",\n" +
                    "  \"Deadline\": \"Deadline\",\n" +
                    "  \"Reset\": \"リセット\",\n" +
                    "  \"Request reason\": \"Request reason\",\n" +
                    "  \"Work Order Content\": \"Work Order Content\",\n" +
                    "  \"WO status\": \"WO status\",\n" +
                    "  \"MA status\": \"MA status\",\n" +
                    "  \"Add W/O\": \"Add W/O\",\n" +
                    "  \"Day\": \"Day\",\n" +
                    "  \"Week\": \"Week\",\n" +
                    "  \"Month\": \"Month\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"You do not have permission to edit this Work Order or it is already Done\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"You do not have permission to delete this Work Order or it is already Done\",\n" +
                    "  \"Delete Work Order\": \"Delete Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"Do you confirm deleting WorkOrder %s?\",\n" +
                    "  \"Deleting Work Order...\": \"Deleting Work Order...\",\n" +
                    "  \"Delete successful\": \"Delete successful\",\n" +
                    "  \"Delete failed: %s\": \"Delete failed: %s\",\n" +
                    "  \"No response from server\": \"No response from server\",\n" +
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
                    "  \"Add Work Order\": \"Add Work Order\",\n" +
                    "  \"Update Work Order\": \"Update Work Order\",\n" +
                    "  \"Edit Work Order\": \"Edit Work Order\",\n" +
                    "  \"Save\": \"Save\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"Is there a device lock feature on the MES system?\",\n" +
                    "  \"W/O Code\": \"W/O Code\",\n" +
                    "  \"Request Date\": \"Request Date\",\n" +
                    "  \"Select an item\": \"Select an item\",\n" +
                    "  \"Enter process\": \"Enter process\",\n" +
                    "  \"Type\": \"Type\",\n" +
                    "  \"Select type\": \"Select type\",\n" +
                    "  \"Requester\": \"Requester\",\n" +
                    "  \"Passed Date\": \"Passed Date\",\n" +
                    "  \"Enter passed date\": \"Enter passed date\",\n" +
                    "  \"Request Reason\": \"Request Reason\",\n" +
                    "  \"Enter request reason\": \"Enter request reason\",\n" +
                    "  \"Enter work order content\": \"Enter work order content\",\n" +
                    "  \"MA Status\": \"MA Status\",\n" +
                    "  \"Completed\": \"Completed\",\n" +
                    "  \"Incomplete\": \"Incomplete\",\n" +
                    "  \"Machine Breakdown\": \"Machine Breakdown\",\n" +
                    "  \"Machine broken\": \"Machine broken\",\n" +
                    "  \"Preparing operation\": \"Preparing operation\",\n" +
                    "  \"Stop due to shortage\": \"Stop due to shortage\",\n" +
                    "  \"Stop by production plan\": \"Stop by production plan\",\n" +
                    "  \"Maintenance and repair\": \"Maintenance and repair\",\n" +
                    "  \"Add\": \"Add\",\n" +
                    "  \"Action\": \"Action\"" +

                    "}\n";


    private static final String vietnamese =
            "{\n" +
                    "  \"Model\": \"Mã thiết bị\",\n" +
                    "  \"Manufacturer\": \"Hãng sản xuất\",\n" +
                    "  \"Dots Per Inch\": \"Mật độ điểm ảnh (DPI)\",\n" +
                    "  \"Device information\": \"Thông tin thiết bị\",\n" +
                    "  \"Operating system version\": \"Phiên bản OS\",\n" +
                    "  \"API level\": \"Cấp độ API\",\n" +
                    "  \"Error creating warehouse release request\": \"Lỗi khi tạo yêu cầu xuất kho\",\n" +
                    "  \"Error while getting list of materials\": \"Lỗi khi lấy danh sách vật tư\",\n" +
                    "  \"Maintenance management system\": \"Hệ thống quản lý bảo trì\",\n" +
                    "  \"All\": \"Tất cả\",\n" +
                    "  \"User name\": \"Tên đăng nhập\",\n" +
                    "  \"Password\": \"Mật khẩu\",\n" +
                    "  \"Login_Singin\": \"ĐĂNG NHẬP\",\n" +
                    "  \"CLOSE\": \"ĐÓNG\",\n" +
                    "  \"Close\": \"Đóng\",\n" +
                    "  \"OK\": \"ĐỒNG Ý\",\n" +
                    "  \"YES\": \"CÓ\",\n" +
                    "  \"Yes\": \"Có\",\n" +
                    "  \"NO\": \"KHÔNG\",\n" +
                    "  \"No\": \"Không\",\n" +
                    "  \"CANCEL\": \"THOÁT\",\n" +
                    "  \"BACK\": \"TRỞ LẠI\",\n" +
                    "  \"Back\": \"Quay lại\",\n" +
                    "  \"STOP\": \"DỪNG\",\n" +
                    "  \"ERROR\": \"LỖI\",\n" +
                    "  \"Reset\": \"重置\",\n" +
                    "  \"SAVE\": \"LƯU\",\n" +
                    "  \"Save\": \"Lưu\",\n" +
                    "  \"Agree\": \"Đồng ý\",\n" +
                    "  \"Deny\": \"Từ chối\",\n" +
                    "  \"Select\": \"Chọn\",\n" +
                    "  \"Logout\": \"Đăng xuất\",\n" +
                    "  \"Confirm\": \"Xác nhận\",\n" +
                    "  \"Home\": \"Trang chủ\",\n" +
                    "  \"Website\": \"Trang web\",\n" +
                    "  \"Success\": \"Thành công\",\n" +
                    "  \"Login failed\": \"Đăng nhập không thành công\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"Lịch bảo trì - sửa chữa\",\n" +
                    "  \"Report new error\": \"Yêu cầu công việc\",\n" +
                    "  \"To-Do List\": \"Công việc cần làm\",\n" +
                    "  \"Completed To Do List\": \"Đã hoàn thành\",\n" +
                    "  \"In progress\": \"Đang thực hiện\",\n" +
                    "  \"Pending\": \"Chưa thực hiện\",\n" +
                    "  \"Overdue\": \"Quá hạn\",\n" +
                    "  \"Machine code\": \"Mã máy\",\n" +
                    "  \"Machine name\": \"Tên máy\",\n" +
                    "  \"Category Name\": \"Tên hạng mục\",\n" +
                    "  \"Materials\": \"Vật tư\",\n" +
                    "  \"Progress\": \"Tiến độ\",\n" +
                    "  \"Date\": \"Ngày\",\n" +
                    "  \"Complete task\": \"Hoàn thành công việc\",\n" +
                    "  \"Execute task\": \"Thực hiện công việc\",\n" +
                    "  \"Search\": \"Tìm kiếm\",\n" +
                    "  \"From\": \"Từ\",\n" +
                    "  \"To\": \"Đến\",\n" +
                    "  \"Error type\": \"Loại lỗi\",\n" +
                    "  \"Error content\": \"Nội dung lỗi\",\n" +
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
                    "  \"Quantity\": \"Số lượng\",\n" +
                    "  \"Unit\": \"Đơn vị\",\n" +
                    "  \"Pause task\": \"Tạm dừng công việc\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"Bạn muốn hoàn thành các hạng mục bảo trì ?\",\n" +
                    "  \"Do you want to save the incident information ?\": \"Bạn muốn lưu thông tin sự cố ?\",\n" +
                    "  \"Complete\": \"Hoàn thành\",\n" +
                    "  \"Person in charge\": \"Người phụ trách\",\n" +
                    "  \"Could not save data\": \"Không thể lưu dữ liệu\",\n" +
                    "  \"Maintenance\": \"Bảo trì\",\n" +
                    "  \"Repair\": \"Sửa chữa\",\n" +
                    "  \"Type\": \"Loại\",\n" +
                    "  \"Approve\": \"Phê duyệt\",\n" +
                    "  \"Approved OK\": \"Đã phê duyệt OK\",\n" +
                    "  \"Approved NG\": \"Đã phê duyệt NG\",\n" +
                    "  \"Done\": \"Đã thực hiện\",\n" +
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
                    "  \"Error code\": \"Mã báo lỗi\",\n" +
                    "  \"Status\": \"Trạng thái\",\n" +
                    "  \"Language\": \"Ngôn ngữ\",\n" +
                    "  \"Version\": \"Phiên bản\",\n" +
                    "  \"Server\": \"Máy chủ\",\n" +
                    "  \"Release date\": \"Ngày phát hành\",\n" +
                    "  \"Only notification\": \"Chỉ thông báo\",\n" +
                    "  \"Notification and vibrate\": \"Thông báo và rung\",\n" +
                    "  \"Do you want to log out?\": \"Bạn muốn đăng xuất ?\",\n" +
                    "  \"Product of Meiko Automation\": \"Sản phẩm của Meiko Automation\",\n" +
                    "  \"Please select maintenance category\": \"Vui lòng chọn hạng mục bảo trì\",\n" +
                    "  \"Blank material list\": \"Danh sách vật tư trống\",\n" +
                    "  \"No completion date selected\": \"Chưa chọn ngày yêu cầu hoàn thành\",\n" +
                    "  \"Enter the note requesting the release of stock\": \"Nhập ghi chú yêu cầu xuất kho\",\n" +
                    "  \"Please select material\": \"Vui lòng chọn vật tư\",\n" +
                    "  \"Invalid quantity\": \"Số lượng không hợp lệ\",\n" +
                    "  \"Please select warehouse\": \"Vui lòng kho\",\n" +
                    "  \"Creating a warehouse release request failed\": \"Tạo yêu cầu xuất kho không thành công\",\n" +
                    "  \"Save materials list failed\": \"Lưu danh sách vật tư không thành công\",\n" +
                    "  \"Update\": \"Cập nhật\",\n" +
                    "  \"The new version\": \"Phiên bản mới\",\n" +
                    "  \"is now available\": \"đã sẵn sàng\",\n" +
                    "  \"Downloading\": \"Đang tải\",\n" +
                    "  \"Please wait...\": \"Xin vui lòng đợi...\",\n" +
                    "  \"Current situation\": \"Hiện trạng\",\n" +
                    "  \"Root cause\": \"Nguyên nhân\",\n" +
                    "  \"Action taken\": \"Xử lý\",\n" +
                    "  \"Countermeasure\": \"Đối sách\",\n" +
                    "  \"Fill in the reason information\": \"Điền thông tin nguyên nhân\",\n" +
                    "  \"Fill in the action taken information\": \"Điền thông tin xử lý\",\n" +
                    "  \"Fill in countermeasure information\": \"Điền thông tin đối sách\",\n" +
                    "  \"Alarm setting\": \"Cài đặt cảnh báo\",\n" +
                    "  \"W/O Management\": \"Quản lý W/O\",\n" +
                    "  \"W/O code\": \"Mã W/O\",\n" +
                    "  \"Request date\": \"Ngày yêu cầu\",\n" +
                    "  \"Machine\": \"Máy\",\n" +
                    "  \"Process\": \"Công đoạn\",\n" +
                    "  \"Work type\": \"Loại hình\",\n" +
                    "  \"Creator\": \"Người tạo\",\n" +
                    "  \"Requester\": \"Người yêu cầu\",\n" +
                    "  \"Elapsed days\": \"Ngày trải qua\",\n" +
                    "  \"Deadline\": \"Thời hạn\",\n" +
                    "  \"Request reason\": \"Lý do yêu cầu\",\n" +
                    "  \"Work Order Content\": \"Nội dung Work Order\",\n" +
                    "  \"WO status\": \"Trạng thái\",\n" +
                    "  \"MA status\": \"Trạng thái MA báo\",\n" +
                    "  \"Add W/O\": \"Thêm W/O\",\n" +
                    "  \"Day\": \"Ngày\",\n" +
                    "  \"Week\": \"Tuần\",\n" +
                    "  \"Month\": \"Tháng\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"Bạn không có quyền sửa Work Order này hoặc Work Order đã Đã thực hiện\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"Bạn không có quyền xóa Work Order này hoặc Work Order đã Đã thực hiện\",\n" +
                    "  \"Delete Work Order\": \"Xóa Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"Bạn có xác nhận xóa WorkOrder %s?\",\n" +
                    "  \"Deleting Work Order...\": \"Đang xóa Work Order...\",\n" +
                    "  \"Delete successful\": \"Xóa thành công\",\n" +
                    "  \"Delete failed: %s\": \"Xóa thất bại: %s\",\n" +
                    "  \"No response from server\": \"Không có phản hồi từ server\",\n" +
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
                    "  \"Add Work Order\": \"Thêm Work Order\",\n" +
                    "  \"Update Work Order\": \"Cập nhật Work Order\",\n" +
                    "  \"Edit Work Order\": \"Sửa Work Order\",\n" +
                    "  \"Save\": \"Lưu\",\n" +
                    "  \"W/O Code\": \"Mã W/O\",\n" +
                    "  \"Request Date\": \"Ngày yêu cầu\",\n" +
                    "  \"Select an item\": \"Chọn một mục\",\n" +
                    "  \"Enter process\": \"Nhập process\",\n" +
                    "  \"Type\": \"Loại hình\",\n" +
                    "  \"Select type\": \"Chọn Loại\",\n" +
                    "  \"Requester\": \"Người yêu cầu\",\n" +
                    "  \"Passed Date\": \"Ngày trải qua\",\n" +
                    "  \"Enter passed date\": \"Nhập ngày trải qua\",\n" +
                    "  \"Request Reason\": \"Lý do yêu cầu\",\n" +
                    "  \"Enter request reason\": \"Nhập lý do yêu cầu\",\n" +
                    "  \"Enter work order content\": \"Nhập nội dung Work Order\",\n" +
                    "  \"MA Status\": \"Trạng thái MA báo\",\n" +
                    "  \"Completed\": \"Hoàn thành\",\n" +
                    "  \"Incomplete\": \"Chưa hoàn thành\",\n" +
                    "  \"Machine Breakdown\": \"Máy hỏng\",\n" +
                    "  \"Machine broken\": \"Máy hỏng\",\n" +
                    "  \"Preparing operation\": \"Chuẩn bị thao tác\",\n" +
                    "  \"Stop due to shortage\": \"Dừng thiếu tồn\",\n" +
                    "  \"Stop by production plan\": \"Dừng theo kế hoạch sản xuất\",\n" +
                    "  \"Maintenance and repair\": \"Bảo dưỡng, sửa chữa\",\n" +
                    "  \"Add\": \"Thêm\",\n" +
                    "  \"Maintenance steps\": \"Các bước thực hiện\",\n" +
                    "  \"Click here\": \"Nhấn vào đây\",\n" +
                    "  \"Material code\": \"Mã vật tư\",\n" +
                    "  \"Retry\": \"Thử lại\",\n" +
                    "  \"Start maintenance\": \"Bắt đầu thực hiện\",\n" +
                    "  \"Create a warehouse request\": \"Tạo yêu cầu xuất kho\",\n" +
                    "  \"Enter complete information\": \"Nhập đầy đủ thông tin\",\n" +
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"HỆ THỐNG QUẢN LÝ BẢO TRÌ\",\n" +
                    "  \"LOGIN FAIL\": \"ĐĂNG NHẬP KHÔNG THÀNH CÔNG\",\n" +
                    "  \"Please check your username or password\": \"Vui lòng kiểm tra tên người dùng hoặc mật khẩu\",\n" +
                    "  \"Condition photo\": \"Ảnh hiện trạng\",\n" +
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
                    "  \"Fill in the current situation information\": \"Điền thông tin hiện trạng\",\n" +
                    "  \"LOGOUT\": \"ĐĂNG XUẤT\",\n" +
                    "  \"EXIT\": \"THOÁT\",\n" +
                    "  \"CONTINUE\": \"TIẾP TỤC\",\n" +
                    "  \"DELETE\": \"XÓA\",\n" +
                    "  \"MATERIALS LIST\": \"DANH SÁCH VẬT TƯ\",\n" +
                    "  \"Cancel\": \"Hủy\",\n" +
                    "  \"System settings\": \"Cài đặt hệ thống\",\n" +
                    "  \"Fill in the login information\": \"Nhập đầy đủ thông tin đăng nhập\",\n" +
                    "  \"Action\": \"Thao tác\"\n" +
                    "}\n";

    private static final String japanese =
            "{\n" +
                    "  \"All\": \"すべて\",\n" +
                    "  \"MAINTENANCE MANAGEMENT SYSTEM\": \"メンテナンス管理システム\",\n" +
                    "  \"Maintenance management system\": \"メンテナンス管理システム\",\n" +
                    "  \"User name\": \"ユーザー名\",\n" +
                    "  \"Password\": \"パスワード\",\n" +
                    "  \"LOGOUT\": \"ログアウト\",\n" +
                    "  \"Login_Singin\": \"ログイン\",\n" +
                    "  \"CLOSE\": \"閉じる\",\n" +
                    "  \"Close\": \"閉じる\",\n" +
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
                    "  \"System settings\": \"システム設定\",\n" +
                    "  \"Success\": \"成功\",\n" +
                    "  \"Fill in the login information\": \"ログイン情報を入力してください\",\n" +
                    "  \"Login failed\": \"ログインに失敗しました\",\n" +
                    "  \"Please check your username or password\": \"ユーザー名またはパスワードを確認してください\",\n" +
                    "  \"Maintenance and Repair Schedule\": \"メンテナンス項目リスト\",\n" +
                    "  \"Report new error\": \"新しいエラーを報告\",\n" +
                    "  \"To-Do List\": \"やることリスト\",\n" +
                    "  \"Completed To Do List\": \"完了リスト\",\n" +
                    "  \"In progress\": \"進行中\",\n" +
                    "  \"Pending\": \"未着手\",\n" +
                    "  \"Overdue\": \"期限切れ\",\n" +
                    "  \"Machine code\": \"機械コード\",\n" +
                    "  \"Machine name\": \"機械名\",\n" +
                    "  \"Category Name\": \"カテゴリー名\",\n" +
                    "  \"Materials\": \"資材\",\n" +
                    "  \"Progress\": \"進捗\",\n" +
                    "  \"Date\": \"日付\",\n" +
                    "  \"Complete task\": \"作業完了\",\n" +
                    "  \"Execute task\": \"作業実行\",\n" +
                    "  \"Search\": \"検索\",\n" +
                    "  \"From\": \"開始日\",\n" +
                    "  \"To\": \"終了日\",\n" +
                    "  \"Error type\": \"エラータイプ\",\n" +
                    "  \"Error content\": \"エラー内容\",\n" +
                    "  \"Condition photo\": \"現状写真\",\n" +
                    "  \"Maintenance photo\": \"メンテナンス写真\",\n" +
                    "  \"Materials list\": \"資材リスト\",\n" +
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
                    "  \"Quantity\": \"数量\",\n" +
                    "  \"Unit\": \"単位\",\n" +
                    "  \"Retry\": \"再試行\",\n" +
                    "  \"Account not authorized to delete\": \"削除の権限がありません\",\n" +
                    "  \"Start maintenance\": \"メンテナンス開始\",\n" +
                    "  \"Pause task\": \"作業一時停止\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"メンテナンス項目を完了しますか？\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"作業の進捗はどのくらいですか（％）？\",\n" +
                    "  \"Do you want to save the incident information ?\": \"インシデント情報を保存しますか？\",\n" +
                    "  \"Complete\": \"完了\",\n" +
                    "  \"Person in charge\": \"担当者\",\n" +
                    "  \"Create a warehouse request\": \"倉庫出庫依頼を作成\",\n" +
                    "  \"Do you want to create this warehouse request ?\": \"この出庫依頼を作成しますか？\",\n" +
                    "  \"Enter complete information\": \"すべての情報を入力してください\",\n" +
                    "  \"Could not save data\": \"データを保存できませんでした\",\n" +
                    "  \"Maintenance\": \"メンテナンス\",\n" +
                    "  \"Repair\": \"修理\",\n" +
                    "  \"Type\": \"タイプ\",\n" +
                    "  \"Approve\": \"承認\",\n" +
                    "  \"Approved OK\": \"承認済み（OK）\",\n" +
                    "  \"Approved NG\": \"承認済み（NG）\",\n" +
                    "  \"Done\": \"実施済み\",\n" +
                    "  \"Machine history\": \"機械履歴\",\n" +
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
                    "  \"Status\": \"ステータス\",\n" +
                    "  \"Language\": \"言語\",\n" +
                    "  \"Version\": \"バージョン\",\n" +
                    "  \"Server\": \"サーバー\",\n" +
                    "  \"Release date\": \"発売日\",\n" +
                    "  \"Only notification\": \"通知のみ\",\n" +
                    "  \"Notification and vibrate\": \"通知とバイブ\",\n" +
                    "  \"Notification, vibrate and ring\": \"通知、バイブ、着信音\",\n" +
                    "  \"Do you want to log out?\": \"ログアウトしますか？\",\n" +
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
                    "  \"Save materials list failed\": \"材料リストの保存に失敗しました\",\n" +
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



                    "  \"Alarm setting\": \"アラーム設定\",\n"+
                    "  \"W/O Management\": \"W/O管理\",\n"+
                    "  \"W/O code\": \"W/Oコード\",\n"+
                    "  \"Request date\": \"依頼日\",\n"+
                    "  \"Machine\": \"設備\",\n"+
                    "  \"Process\": \"工程\",\n"+
                    "  \"Work type\": \"作業種別\",\n"+
                    "  \"Creator\": \"作成者\",\n"+
                    "  \"Requester\": \"依頼者\",\n"+
                    "  \"Elapsed days\": \"経過日数\",\n"+
                    "  \"Deadline\": \"期限\",\n"+
                    "  \"Request reason\": \"依頼理由\",\n"+
                    "  \"Work Order Content\": \"W/O内容\",\n"+
                    "  \"WO status\": \"WO状態\",\n"+
                    "  \"MA status\": \"MA状態\",\n"+
                    "  \"Add W/O\": \"W/O追加\",\n"+
                    "  \"Day\": \"日\",\n"+
                    "  \"Week\": \"週\",\n"+
                    "  \"Month\": \"月\",\n"+
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"このWork Orderを編集する権限がないか、すでに完了しています\",\n"+
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"このWork Orderを削除する権限がないか、すでに完了しています\",\n"+
                    "  \"Delete Work Order\": \"Work Order削除\",\n"+
                    "  \"Do you confirm deleting WorkOrder %s?\": \"WorkOrder %s を削除してもよろしいですか？\",\n"+
                    "  \"Deleting Work Order...\": \"Work Orderを削除中...\",\n"+
                    "  \"Delete successful\": \"削除成功\",\n"+
                    "  \"Delete failed: %s\": \"削除失敗: %s\",\n"+
                    "  \"No response from server\": \"サーバーから応答がありません\",\n"+
                    "  \"Delete error: %s\": \"削除エラー: %s\",\n"+
                    "  \"Storage permission denied\": \"ストレージ権限が拒否されました\",\n"+
                    "  \"Unknown sources permission denied\": \"提供元不明アプリの権限が拒否されました\",\n"+
                    "  \"Load completed\": \"データの読み込みが完了しました\",\n"+
                    "  \"Please enter Machine and Requester\": \"設備と依頼者を入力してください\",\n"+
                    "  \"Processing data...\": \"データ処理中...\",\n"+
                    "  \"Add Work Order successful\": \"Work Orderの追加に成功しました\",\n"+
                    "  \"Updating...\": \"更新中...\",\n"+
                    "  \"Update successful\": \"更新成功\",\n"+
                    "  \"Update failed: %s\": \"更新失敗: %s\",\n"+
                    "  \"Update error: %s\": \"更新エラー: %s\",\n"+
                    "  \"Request reason is required\": \"依頼理由は必須です\",\n"+
                    "  \"Add Work Order\": \"Work Orderを追加\",\n"+
                    "  \"Update Work Order\": \"Work Orderを更新\",\n"+
                    "  \"Edit Work Order\": \"Work Orderを編集\",\n"+
                    "  \"Save\": \"保存\",\n"+
                    "  \"Is there a device lock feature on the MES system?\": \"MESシステムに設備のロック機能はありますか？\",\n" +
                    "  \"W/O Code\": \"W/Oコード\",\n"+
                    "  \"Request Date\": \"依頼日\",\n"+
                    "  \"Select an item\": \"項目を選択\",\n"+
                    "  \"Enter process\": \"工程を入力\",\n"+
                    "  \"Type\": \"種別\",\n"+
                    "  \"Select type\": \"種別を選択\",\n"+
                    "  \"Requester\": \"依頼者\",\n"+
                    "  \"Passed Date\": \"経過日\",\n"+
                    "  \"Enter passed date\": \"経過日を入力\",\n"+
                    "  \"Request Reason\": \"依頼理由\",\n"+
                    "  \"Enter request reason\": \"依頼理由を入力\",\n"+
                    "  \"Enter work order content\": \"W/O内容を入力\",\n"+
                    "  \"MA Status\": \"MA報告状態\",\n"+
                    "  \"Completed\": \"完了\",\n"+
                    "  \"Incomplete\": \"未完了\",\n"+
                    "  \"Machine Breakdown\": \"設備故障\",\n"+
                    "  \"Machine broken\": \"設備故障\",\n"+
                    "  \"Preparing operation\": \"作業準備\",\n"+
                    "  \"Stop due to shortage\": \"不足による停止\",\n"+
                    "  \"Stop by production plan\": \"生産計画による停止\",\n"+
                    "  \"Maintenance and repair\": \"保全・修理\",\n"+
                    "  \"Add\": \"追加\",\n"+
                    "  \"Action\": \"操作\"\n"+
                    "}\n";


    private static final String chinese =
            "{\n" +
                    "  \"All\": \"全部\",\n" +
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
                    "  \"Save\": \"保存\",\n" +
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
                    "  \"Pending\": \"待处理\",\n" +
                    "  \"Overdue\": \"逾期\",\n" +
                    "  \"Machine code\": \"机器代码\",\n" +
                    "  \"Machine name\": \"机器名称\",\n" +
                    "  \"Category Name\": \"类别名称\",\n" +
                    "  \"Materials\": \"材料\",\n" +
                    "  \"Progress\": \"进度\",\n" +
                    "  \"Date\": \"日期\",\n" +
                    "  \"Complete task\": \"完成任务\",\n" +
                    "  \"Execute task\": \"执行任务\",\n" +
                    "  \"Search\": \"搜索\",\n" +
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
                    "  \"Quantity\": \"数量\",\n" +
                    "  \"Unit\": \"单位\",\n" +
                    "  \"Retry\": \"重试\",\n" +
                    "  \"Account not authorized to delete\": \"账户无权删除\",\n" +
                    "  \"Start maintenance\": \"开始维护\",\n" +
                    "  \"Pause task\": \"暂停任务\",\n" +
                    "  \"Do you want to complete the maintenance items ?\": \"您想完成这些维护项目吗？\",\n" +
                    "  \"How much (%) of the work have you completed ?\": \"您完成了多少（%）的工作？\",\n" +
                    "  \"Do you want to save the incident information ?\": \"您想保存事件信息吗？\",\n" +
                    "  \"Complete\": \"完成\",\n" +
                    "  \"Person in charge\": \"负责人\",\n" +
                    "  \"Create a warehouse request\": \"创建仓库请求\",\n" +
                    "  \"Do you want to create this warehouse request ?\": \"您想创建此仓库请求吗？\",\n" +
                    "  \"Enter complete information\": \"请输入完整信息\",\n" +
                    "  \"Could not save data\": \"无法保存数据\",\n" +
                    "  \"Maintenance\": \"维护\",\n" +
                    "  \"Repair\": \"修理\",\n" +
                    "  \"Type\": \"类型\",\n" +
                    "  \"Approve\": \"批准\",\n" +
                    "  \"Approved OK\": \"批准通过\",\n" +
                    "  \"Approved NG\": \"批准未通过\",\n" +
                    "  \"Done\": \"已完成\",\n" +
                    "  \"Machine history\": \"机器历史记录\",\n" +
                    "  \"Job type\": \"工作类型\",\n" +
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
                    "  \"Error code\": \"错误代码\",\n" +
                    "  \"Status\": \"状态\",\n" +
                    "  \"Language\": \"语言\",\n" +
                    "  \"Version\": \"版本\",\n" +
                    "  \"Server\": \"服务器\",\n" +
                    "  \"Release date\": \"发布日期\",\n" +
                    "  \"Only notification\": \"仅通知\",\n" +
                    "  \"Notification and vibrate\": \"通知和振动\",\n" +
                    "  \"Notification, vibrate and ring\": \"通知、振动和铃声\",\n" +
                    "  \"Do you want to log out?\": \"您想登出吗？\",\n" +
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
                    "  \"Update\": \"更新\",\n" +
                    "  \"The application has been updated to the latest version\": \"应用程序已更新到最新版本\",\n" +
                    "  \"The new version\": \"新版本\",\n" +
                    "  \"is now available\": \"现已可用\",\n" +
                    "  \"Downloading\": \"下载中\",\n" +
                    "  \"Please wait...\": \"请稍候...\",\n" +


                    "  \"Current situation\": \"当前状况\",\n" +
                    "  \"Root cause\": \"根本原因\",\n" +
                    "  \"Action taken\": \"处理措施\",\n" +
                    "  \"Countermeasure\": \"对策\",\n" +

                    "  \"Fill in the current situation information\": \"填写当前状况信息\",\n" +
                    "  \"Fill in the reason information\": \"填写原因信息\",\n" +
                    "  \"Fill in the action taken information\": \"填写处理措施信息\",\n" +
                    "  \"Fill in countermeasure information\": \"填写对策信息\",\n" +


                    "  \"Alarm setting\": \"警报设置\",\n" +
                    "  \"W/O Management\": \"W/O管理\",\n" +
                    "  \"W/O code\": \"W/O编码\",\n" +
                    "  \"Request date\": \"请求日期\",\n" +
                    "  \"Machine\": \"机器\",\n" +
                    "  \"Process\": \"工序\",\n" +
                    "  \"Work type\": \"工单类型\",\n" +
                    "  \"Creator\": \"创建人\",\n" +
                    "  \"Requester\": \"申请人\",\n" +
                    "  \"Elapsed days\": \"经过天数\",\n" +
                    "  \"Deadline\": \"截止时间\",\n" +
                    "  \"Request reason\": \"申请原因\",\n" +
                    "  \"Work Order Content\": \"工单内容\",\n" +
                    "  \"WO status\": \"工单状态\",\n" +
                    "  \"MA status\": \"MA报修状态\",\n" +
                    "  \"Add W/O\": \"新增W/O\",\n" +
                    "  \"Day\": \"日\",\n" +
                    "  \"Week\": \"周\",\n" +
                    "  \"Month\": \"月\",\n" +
                    "  \"Is there a device lock feature on the MES system?\": \"MES系统是否具有设备锁定功能？\",\n" +
                    "  \"You do not have permission to edit this Work Order or it is already Done\": \"您无权编辑此Work Order，或该工单已完成\",\n" +
                    "  \"You do not have permission to delete this Work Order or it is already Done\": \"您无权删除此Work Order，或该工单已完成\",\n" +
                    "  \"Delete Work Order\": \"删除Work Order\",\n" +
                    "  \"Do you confirm deleting WorkOrder %s?\": \"您确认删除WorkOrder %s 吗？\",\n" +
                    "  \"Deleting Work Order...\": \"正在删除Work Order...\",\n" +
                    "  \"Delete successful\": \"删除成功\",\n" +
                    "  \"Delete failed: %s\": \"删除失败: %s\",\n" +
                    "  \"No response from server\": \"服务器无响应\",\n" +
                    "  \"Delete error: %s\": \"删除错误: %s\",\n" +
                    "  \"Storage permission denied\": \"存储权限被拒绝\",\n" +
                    "  \"Unknown sources permission denied\": \"未知来源安装权限被拒绝\",\n" +
                    "  \"Load completed\": \"数据加载完成\",\n" +
                    "  \"Please enter Machine and Requester\": \"请输入完整的机器和申请人信息\",\n" +
                    "  \"Processing data...\": \"正在处理数据...\",\n" +
                    "  \"Add Work Order successful\": \"新增Work Order成功！\",\n" +
                    "  \"Updating...\": \"正在更新...\",\n" +
                    "  \"Update successful\": \"更新成功\",\n" +
                    "  \"Update failed: %s\": \"更新失败: %s\",\n" +
                    "  \"Update error: %s\": \"更新错误: %s\",\n" +
                    "  \"Request reason is required\": \"必须输入申请原因\",\n" +
                    "  \"Add Work Order\": \"新增Work Order\",\n" +
                    "  \"Update Work Order\": \"更新Work Order\",\n" +
                    "  \"Edit Work Order\": \"编辑Work Order\",\n" +
                    "  \"Save\": \"保存\",\n" +
                    "  \"W/O Code\": \"W/O编码\",\n" +
                    "  \"Request Date\": \"申请日期\",\n" +
                    "  \"Select an item\": \"请选择一项\",\n" +
                    "  \"Enter process\": \"输入工序\",\n" +
                    "  \"Type\": \"类型\",\n" +
                    "  \"Select type\": \"选择类型\",\n" +
                    "  \"Requester\": \"申请人\",\n" +
                    "  \"Passed Date\": \"经过天数\",\n" +
                    "  \"Enter passed date\": \"输入经过天数\",\n" +
                    "  \"Request Reason\": \"申请原因\",\n" +
                    "  \"Enter request reason\": \"输入申请原因\",\n" +
                    "  \"Enter work order content\": \"输入工单内容\",\n" +
                    "  \"MA Status\": \"MA报告状态\",\n" +
                    "  \"Completed\": \"已完成\",\n" +
                    "  \"Incomplete\": \"未完成\",\n" +
                    "  \"Machine Breakdown\": \"设备故障\",\n" +
                    "  \"Machine broken\": \"设备故障\",\n" +
                    "  \"Preparing operation\": \"准备作业\",\n" +
                    "  \"Stop due to shortage\": \"缺料停机\",\n" +
                    "  \"Stop by production plan\": \"按生产计划停机\",\n" +
                    "  \"Maintenance and repair\": \"保养与维修\",\n" +
                    "  \"Add\": \"新增\",\n" +
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
            String text = textView.getText() != null ? textView.getText().toString() : "";
            if (!text.isEmpty()) {
                textView.setText(i18n(text));
            }

            CharSequence hint = textView.getHint();
            if (hint != null && hint.length() > 0) {
                textView.setHint(i18n(hint.toString()));
            }
        } else if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                applyLangRecursive(child);
            }
        }
    }
}
