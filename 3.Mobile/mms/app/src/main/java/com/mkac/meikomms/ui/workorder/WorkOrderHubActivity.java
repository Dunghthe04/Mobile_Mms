package com.mkac.meikomms.ui.workorder;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkOrderHubActivity extends AppCompatActivity {
    private static final String EXTRA_LANGUAGE_CODE = "LANGUAGE_CODE";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String serverUrl = "";
    private String localAppVersion = "2.4.7";

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderHubActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_order_hub);

        LanguageAPIUtils.init(this);
        loadAppConfigurations();
        displayUserInfo();
        applyLanguage();

        LinearLayout card_create_work_order = findViewById(R.id.btn_create_work_order);
        LinearLayout btn_enter_work_order_data = findViewById(R.id.btn_enter_work_order_data);
        ImageView img_user_profile = findViewById(R.id.img_user_profile);
        ImageView img_language_switch = findViewById(R.id.img_language_switch);

        card_create_work_order.setOnClickListener(v -> {
            Intent intent = new Intent(WorkOrderHubActivity.this, ListWorkOrderActivity.class);
            // Luôn truyền kèm mã ngôn ngữ mới nhất sang các Activity kế tiếp
            intent.putExtra(EXTRA_LANGUAGE_CODE, LanguageAPIUtils.getLanguageCode());
            startActivity(intent);
        });

        btn_enter_work_order_data.setOnClickListener(v -> {
            Intent intent = new Intent(WorkOrderHubActivity.this, WorkOrderDataActivity.class);
            // Luôn truyền kèm mã ngôn ngữ mới nhất sang các Activity kế tiếp
            intent.putExtra(EXTRA_LANGUAGE_CODE, LanguageAPIUtils.getLanguageCode());
            startActivity(intent);
        });

        if (img_user_profile != null) {
            img_user_profile.setOnClickListener(v -> showLogoutConfirmationDialog());
        }

        if (img_language_switch != null) {
            img_language_switch.setOnClickListener(v -> showLanguageSelectionDialog());
        }

        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        applyLanguage();
        displayUserInfo();
        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
    }

    private void applyLanguage() {
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvHubSubtitle = findViewById(R.id.tv_hub_subtitle);
        TextView tvHubDescription = findViewById(R.id.tv_hub_description);
        TextView tvCreateWorkOrderTitle = findViewById(R.id.tv_create_work_order_title);
        TextView tvCreateWorkOrderDesc = findViewById(R.id.tv_create_work_order_desc);
        TextView tvEnterWorkOrderTitle = findViewById(R.id.tv_enter_work_order_title);
        TextView tvEnterWorkOrderDesc = findViewById(R.id.tv_enter_work_order_desc);

        if (tvTitle != null) tvTitle.setText(i18n("W/O Management"));
        if (tvHubSubtitle != null) tvHubSubtitle.setText(i18n("Select function"));
        if (tvHubDescription != null) tvHubDescription.setText(i18n("Create a work order or enter work order and maintenance data"));
        if (tvCreateWorkOrderTitle != null) tvCreateWorkOrderTitle.setText(i18n("Add Work Order"));
        if (tvCreateWorkOrderDesc != null) tvCreateWorkOrderDesc.setText(i18n("Create a new work order"));
        if (tvEnterWorkOrderTitle != null) tvEnterWorkOrderTitle.setText(i18n("Enter Work Order Data"));
        if (tvEnterWorkOrderDesc != null) tvEnterWorkOrderDesc.setText(i18n("Manage work orders and perform maintenance"));
    }

    private void showLanguageSelectionDialog() {
        final String[] displayNames = new String[]{"日本", "English", "Tiếng Việt", "Chinese"};
        final String[] codes = new String[]{"ja", "en", "vi", "ch"};

        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        int currentPosition = prefs.getInt("languageSettingPosition", 2);

        new AlertDialog.Builder(this)
                .setTitle(i18n("Language"))
                .setSingleChoiceItems(displayNames, currentPosition, (dialog, which) -> {
                    // 1. Lưu cấu hình vị trí vào cấu trúc SharedPreferences toàn cục
                    prefs.edit().putInt("languageSettingPosition", which).apply();

                    // 2. Cập nhật mã code tĩnh và kích hoạt nạp lại từ điển (Clear map + nạp built-in/API)
                    LanguageAPIUtils.setLanguageCode(codes[which]);
                    LanguageAPIUtils.reloadLanguage(this);

                    // 3. Ép cập nhật lại toàn bộ chuỗi text trên giao diện Hub hiện tại
                    applyLanguage();
                    displayUserInfo();
                    LanguageAPIUtils.setLang(findViewById(android.R.id.content));

                    dialog.dismiss();

                    Toast.makeText(this, i18n("Language changed successfully"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(i18n("Close"), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void displayUserInfo() {
        TextView tvUsername = findViewById(R.id.tv_username_display);
        if (tvUsername == null) return;

        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");

        String accountName = "";
        if (userObj != null) {
            if (userObj.has("User_Name")) {
                accountName = userObj.optString("User_Name", "").trim();
            } else if (userObj.has("username")) {
                accountName = userObj.optString("username", "").trim();
            } else if (userObj.has("Account")) {
                accountName = userObj.optString("Account", "").trim();
            }

            if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
                accountName = userObj.optString("Full_Name", "").trim();
            }
        }

        if (accountName.isEmpty() || "null".equalsIgnoreCase(accountName)) {
            accountName = "MMS_Account";
        }

        tvUsername.setText(accountName);
    }

    private void loadAppConfigurations() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = handler.getString("server_url");
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = configManager.getProperty("server_url");
        }

        String configVer = configManager.getProperty("version");
        if (configVer != null && !configVer.trim().isEmpty()) {
            localAppVersion = configVer.trim();
        }
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(i18n("Logout"))
                .setMessage(i18n("Do you want to log out?"))
                .setCancelable(true)
                .setPositiveButton(i18n("Logout"), (dialog, which) -> executeLogoutAction())
                .setNegativeButton(i18n("Cancel"), (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void executeLogoutAction() {
        try {
            PreferenceHandler handler = new PreferenceHandler(this);
            handler.remove("user");
            handler.remove("api_key");

            Toast.makeText(this, i18n("Logout") + " " + i18n("Success"), Toast.LENGTH_SHORT).show();

            Class<?> loginActivityClass = Class.forName("com.mkac.meikomms.ui.login.LoginActivity");
            Intent intent = new Intent(this, loginActivityClass);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (ClassNotFoundException e) {
            Log.e("LOGOUT_ERROR", "LoginActivity class not found", e);
            Toast.makeText(this, i18n("Navigation to Login screen failed!"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("LOGOUT_ERROR", "Exception during logout", e);
        }
    }
}