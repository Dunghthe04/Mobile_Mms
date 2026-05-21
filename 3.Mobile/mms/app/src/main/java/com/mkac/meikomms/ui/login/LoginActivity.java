package com.mkac.meikomms.ui.login;

import static android.media.MediaExtractor.MetricsConstants.MIME_TYPE;
import static com.mkac.meikomms.common.LanguageAPIUtils.getLanguageCode;
import static com.mkac.meikomms.common.LanguageAPIUtils.getLanguageName;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;
import static com.mkac.meikomms.common.LanguageAPIUtils.setLang;
import static com.mkac.meikomms.common.LanguageAPIUtils.setLanguageCode;
import static com.mkac.meikomms.common.Utils.autoHideKeyboard;
import static com.mkac.meikomms.common.Utils.dismissLoading;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.mkac.meikomms.MainActivity;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ColorConsole;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.FileDownloadInFragment;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.data.AppDatabase;
import com.mkac.meikomms.databinding.ActivityLoginBinding;
import com.mkac.meikomms.ui.custom.LoadingDialog;
import com.mkac.meikomms.common.HttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.Objects;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_CODE = 1;
    boolean isLoginInProgress = false;
    private AppDatabase appDatabase;
    private ActivityLoginBinding binding;
    TextView usernameLabel;
    TextView passwordLabel;
    TextView companyTag;
    private ImageView connect_server_status,btn_show_password;
    private String version = "";
    private String login_url = "";
    private String server_url = "";
    LoadingDialog loadingDialog;
    private String system_name = "";
    private String screen_id = "";
    private String DOWNLOAD_URL = "";
    private String FILE_NAME = "";
    private boolean show_pass = false;
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";
    private static String uniqueID = null;
    private FileDownloadInFragment downloader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getSupportActionBar().hide();
       // LanguageUtils.init(this);
        LanguageAPIUtils.init(this);
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);
        String deviceModel = Build.MODEL;
        getDeviceSuperInfo();
        if(configManager.getProperty("vertical_lock").equals("true"))
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        version = configManager.getProperty("version");
        login_url = configManager.getProperty("login_url");
        server_url = configManager.getProperty("server_url");
        system_name = configManager.getProperty("system_name");
        screen_id = configManager.getProperty("screen_id");


        if (!handler.getString("login_url").isEmpty()) {
            login_url = handler.getString("login_url");
        }
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }

        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        uiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
        usernameLabel = findViewById(R.id.usernameLabel);
        passwordLabel = findViewById(R.id.passwordLabel);
        companyTag = findViewById(R.id.companyTag);

        appDatabase = AppDatabase.getInstance(this);

        initScreen();

        if (handler.getBoolean("isLoggedIn"))
        {
            proceedLogin();
        }else
        {
            Thread autoUpdate = new Thread(() ->
            {
                HttpClient.APIReturn rs = HttpClient.get_version_app(system_name, screen_id,server_url);
                if (rs.code == 200)
                {
                    try
                    {
                        if (rs.data != null)
                        {
                            int current_ver = Integer.parseInt(version.replace(".",""));
                            String newversion =  rs.data.get(0).getString("Note");
                            String data_value = rs.data.get(0).getString("Variable_Name");

                            JSONObject jsonObject = new JSONObject(data_value);

                            // Extract individual values
                            DOWNLOAD_URL = jsonObject.getString("BaseUrl");
                            String filesize = jsonObject.getString("Filesize");
                            String content = jsonObject.getString("Content");

                            int new_ver = Integer.parseInt(newversion.replace(".",""));
                            if (current_ver < new_ver)
                            {
                                FILE_NAME = String.valueOf(TimeUtils.getCurrentUnixTimestamp())+".apk";
                                downloader = new FileDownloadInFragment(this, DOWNLOAD_URL, FILE_NAME,filesize, MIME_TYPE);

                                downloader.setDownloadListener(new FileDownloadInFragment.DownloadListener() {
                                    @Override
                                    public void onDownloadSuccess(String filePath) {
                                        // Toast.makeText(requireContext(), "Downloaded to: " + filePath, Toast.LENGTH_LONG).show();
                                    }
                                    @Override
                                    public void onDownloadFailed(String error) {
                                        // Toast.makeText(requireContext(), "Download failed: " + error, Toast.LENGTH_LONG).show();
                                    }
                                    @Override
                                    public void onProgressUpdate(int progress) {
                                        Log.d("DownloadFragment", "Progress: " + progress + "%");
                                    }
                                    @Override
                                    public void onDownloadSuccess(String filePath, Uri fileUri) {

                                    }
                                });

                                runOnUiThread(() -> showUpdateDialog(newversion,FILE_NAME));
                            }
                        }
                    }
                    catch (Exception e)
                    {}
                }
            });
            autoUpdate.start();
        }
        Log.d("DeviceModel", deviceModel);
    }

    private void showUpdateDialog(String latestVersion,String file_name)
    {
        new AlertDialog.Builder(this)
                .setTitle(i18n("Update"))
                .setMessage(i18n("The new version") +" "+ latestVersion +" "+ i18n("is now available"))
                .setPositiveButton(i18n("Update"), (dialog, which) ->  checkPermissionsAndStartDownload(file_name))
                .setNegativeButton(i18n("Close"), null)
                .setCancelable(false)
                .show();

    }
    private void checkPermissionsAndStartDownload(String file_name)
    {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isApkFile() &&
                !this.getPackageManager().canRequestPackageInstalls()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + this.getPackageName()));
            unknownSourcesLauncher.launch(intent);
        }
        else
        {
            startDownload();
        }
    }

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startDownload();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> unknownSourcesLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        this.getPackageManager().canRequestPackageInstalls()) {
                    startDownload();
                } else {
                    Toast.makeText(this, "Unknown sources permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private void startDownload()
    {

      downloader.startDownload();

    }

    private boolean isApkFile() {
        return MIME_TYPE.equals("application/vnd.android.package-archive") || FILE_NAME.endsWith(".apk");
    }
    private void getDeviceSuperInfo()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);


        DisplayMetrics metricss = this.getResources().getDisplayMetrics();
        float densityy = metricss.density;
        int widthDp = (int)(metricss.widthPixels / densityy);
        int heightDp = (int)(metricss.heightPixels / densityy);
        int smallestWidth = Math.min(widthDp, heightDp);


        float densityDpi = metrics.densityDpi;     // Mật độ DPI
        float density = metrics.density;           // scale factor (e.g. 3.0 for xxhdpi)

        try {

            String s = "Debug-infos:";
            s += "\n OS Version: "      + System.getProperty("os.version")      + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
            s += "\n OS API Level: "    + android.os.Build.VERSION.SDK_INT;
            s += "\n Device: "          + android.os.Build.DEVICE;
            s += "\n Model (and Product): " + android.os.Build.MODEL            + " ("+ android.os.Build.PRODUCT + ")";
            s += "\n RELEASE: "         + android.os.Build.VERSION.RELEASE;
            s += "\n BRAND: "           + android.os.Build.BRAND;
            s += "\n DISPLAY: "         + android.os.Build.DISPLAY;
            s += "\n CPU_ABI: "         + android.os.Build.CPU_ABI;
            s += "\n HARDWARE: "        + android.os.Build.HARDWARE;
            s += "\n Build ID: "        + android.os.Build.ID;
            s += "\n MANUFACTURER: "    + android.os.Build.MANUFACTURER;
            s += "\n UUID: "            + getDeviceUUID(this);
            s += "\n USER: "            + android.os.Build.USER;
            s += "\n PACKNAME: "        + getApplicationContext().getPackageName();
            s += "\n DPI: "        + smallestWidth;

            ColorConsole.d( " | Device Info > ", s);

        } catch (Exception e) {

        }

    }//end getDeviceSuperInfo

    public synchronized static String getDeviceUUID(Context context)
    {
        if (uniqueID == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(PREF_UNIQUE_ID, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);
            if (uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PREF_UNIQUE_ID, uniqueID);
                editor.apply();
            }
        }
        return uniqueID;
    }

    public static boolean isPingSuccessful(String urlString, int timeoutMillis)
    {
        try {
            URL url = new URL(urlString);
            String host = url.getHost(); // Lấy IP hoặc domain
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort(); // nếu không có thì dùng port mặc định

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    protected void initScreen()
    {
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        loadingDialog = new LoadingDialog(this);
        View rootView = binding.getRoot();
        LanguageAPIUtils.init(this);
        setContentView(rootView);
        setLang(rootView);
        autoHideKeyboard(rootView, this);
        langChangeHandler(this);
        Button btnLogin = binding.loginButton;
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get username and password for validation
                EditText edtName = binding.usernameInput;
                String userName = edtName.getText().toString();
                EditText edtPsw = binding.passwordInput;
                String password = edtPsw.getText().toString();
                if (!userName.isEmpty() && !password.isEmpty())
                {
                    login(userName.trim(), password.trim());
                }
                else
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run() {
                            OpenDialogError(Gravity.CENTER, i18n("LOGIN FAIL"), i18n("Fill in the login information"));
                        }
                    });

                }
            }
        });

        TextView VersionText = findViewById(R.id.VersionText);
        ImageView companylogo = findViewById(R.id.companyLogo);

        btn_show_password = findViewById(R.id.btn_show_password);

        if(version.isEmpty() || version == null)
        {
            VersionText.setText(String.format("%s(%s)", getVersionNameInMajorMinorPatchFormat(this), 2));
        }else
        {
            VersionText.setText(version);
        }

        btn_show_password.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!show_pass) {
                    show_pass = true;
                    // Hiển thị mật khẩu
                    binding.passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btn_show_password.setImageResource(R.drawable.eye);
                } else {
                    show_pass = false;
                    // Ẩn mật khẩu
                    binding.passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btn_show_password.setImageResource(R.drawable.eye_slash);
                }

                // Đặt con trỏ về cuối chuỗi sau khi thay đổi input type
                binding.passwordInput.setSelection(binding.passwordInput.getText().length());

            }
        });

        appDatabase.languageDao().getCurrentLanguage().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                binding.usernameLabel.setText(i18n("User name"));
                binding.passwordLabel.setText(i18n("Password"));
                binding.loginButton.setText(i18n("LOGIN"));
                binding.companyTag.setText(i18n("Product of Meiko Automation"));

            }
        });
        AppDatabase appDatabase = AppDatabase.getInstance(this);
        companylogo.setOnLongClickListener(v -> {

            if (binding.usernameInput.getText().toString().equals("MKAC"))
            {
                try {
                    OpenDialogConfigIP(Gravity.CENTER, "SERVER INFORMATION", "", this);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        });

    }

    public static String getVersionNameInMajorMinorPatchFormat(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionName;

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    // Hàm khi Login sẽ vào ListWorkOrder
    private void proceedLogin() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, com.mkac.meikomms.ui.workorder.WorkOrderHubActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 300);
    }

    private void langChangeHandler(Context context) {
        LanguageAPIUtils.init(context);
        RelativeLayout languageLayout = findViewById(R.id.languageLayout);
        SharedPreferences appSettingsSharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor appSettingsEditor = appSettingsSharedPreferences.edit();

        TextView languageText = findViewById(R.id.languageText);
        languageText.setText(getLanguageName());
        languageLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String langCode = getLanguageCode();

                if (langCode.equals("ja"))
                {
                    setLanguageCode("en");
                    languageText.setText("English");
                    appSettingsEditor.putInt("languageSettingPosition", 1);
                }

                if (langCode.equals("en"))
                {
                    setLanguageCode("vi");
                    languageText.setText("Tiếng Việt");
                    appSettingsEditor.putInt("languageSettingPosition", 2);
                }
                if (langCode.equals("vi"))
                {
                    setLanguageCode("ch");
                    languageText.setText("Chinese");
                    appSettingsEditor.putInt("languageSettingPosition", 3);
                }
                if (langCode.equals("ch"))
                {
                    setLanguageCode("ja");
                    languageText.setText("日本");
                    appSettingsEditor.putInt("languageSettingPosition", 0);
                }

                appSettingsEditor.apply();
                setLang(findViewById(android.R.id.content));

                binding.usernameLabel.setText(i18n("User name"));
                binding.passwordLabel.setText(i18n("Password"));
                binding.companyTag.setText(i18n("Product of Meiko Automation"));
                binding.loginButton.setText(i18n("LOGIN"));

            }
        });


        languageLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                LanguageAPIUtils.reloadLanguage(context);
                Toast.makeText(context, "Cập nhật ngôn ngữ thành công ", Toast.LENGTH_SHORT).show();
                return false;
            }
        });


    }

    private void login(String userName, String password) {
        if (isLoginInProgress) return;

        binding.loginButton.setEnabled(false);
        isLoginInProgress = true;

        if (userName.isEmpty() || password.isEmpty()) {
            OpenDialogError(Gravity.CENTER, i18n("ERROR"), i18n("Fill in the login information"));
            binding.loginButton.setEnabled(true);
            isLoginInProgress = false;
            return;
        }

        loadingDialog.show();

        new Thread(() -> {
            try {
                HttpClient.APIReturn rs = HttpClient.login(getApplicationContext(), login_url, userName, password);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dismissLoading(this, binding.getRoot(), loadingDialog, true);
                    binding.loginButton.setEnabled(true);
                    isLoginInProgress = false;

                    if (rs.code == 200 && rs.data != null && !rs.data.isEmpty()) {
                        try {
                            PreferenceHandler handler = new PreferenceHandler(this);
                            handler.clear();

                            JSONObject jsonObject = rs.data.get(0);
                            String api_key = jsonObject.optString("token");
                            JSONObject userDetail = jsonObject.optJSONObject("user");

                            if (userDetail == null || userDetail.length() == 0) {
                                OpenDialogError(Gravity.CENTER, "LỖI", "User data không hợp lệ");
                                return;
                            }

                            if (api_key == null || api_key.isEmpty()) {
                                OpenDialogError(Gravity.CENTER, "LỖI", "Token không hợp lệ");
                                return;
                            }

                            userDetail.put("token", api_key);

                            handler.setBoolean("isLoggedIn", false);

                            handler.setJsonObject("user", userDetail);
                            handler.setString("api_key", api_key);

                            Log.d("LOGIN_SUCCESS", "Full User saved: " + userDetail.toString());

                            proceedLogin();

                        } catch (Exception e) {
                            Log.e("LOGIN_DATA_ERROR", "Lỗi xử lý JSON: " + e.getMessage());
                            OpenDialogError(Gravity.CENTER, "LỖI DỮ LIỆU", "Lỗi cấu trúc dữ liệu người dùng.");
                        }
                    } else {
                        // Sai tài khoản hoặc mật khẩu
                        Log.d("LOGIN_FAILED", rs.message != null ? rs.message : "Unknown error");
                        OpenDialogError(Gravity.CENTER, i18n("LOGIN FAIL"), i18n("Please check your username or password"));
                    }
                });
            } catch (Exception e) {
                Log.e("LOGIN_THREAD_ERROR", "Network or Server error: " + e.getMessage());
                runOnUiThread(() -> {
                    binding.loginButton.setEnabled(true);
                    isLoginInProgress = false;
                    dismissLoading(this, binding.getRoot(), loadingDialog, true);
                    OpenDialogError(Gravity.CENTER, "LỖI KẾT NỐI", "Không thể kết nối tới máy chủ.");
                });
            }
        }).start();
    }


    private void OpenDialogError(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_error);

        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        txtnoidung.setText(noidung);
        tvTitle.setText(tieude);
        Window window = dialog.getWindow();
        btncancel.setText(i18n("Close"));
        if(window == null){return;}
        else
        {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }

        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void OpenDialogConfigIP(int gravity, String tieude, String noidung, Context context) throws MalformedURLException {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_config_server);

        Button btncancel = (Button) dialog.findViewById(R.id.btncancel);
        Button btnOK = (Button) dialog.findViewById(R.id.btnOK);
        Button btnreset = (Button) dialog.findViewById(R.id.btnreset);
        TextView txttieude = (TextView) dialog.findViewById(R.id.txttieude);
        EditText txt_ip = (EditText) dialog.findViewById(R.id.txt_ip);


        txttieude.setText(i18n(tieude));
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        } else {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = gravity;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(false);
        }
        URL url = new URL(server_url);
        String host = url.getHost(); // Lấy IP hoặc domain
        txt_ip.setText(host);


        btncancel.setText(i18n("CLOSE"));
        btnOK.setText(i18n("SAVE"));
        btnreset.setText(i18n("RESET"));
        btncancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                try {
                    if (isValidIPAddress(txt_ip.getText().toString()))
                    {
                        PreferenceHandler handlersave = new PreferenceHandler(context);
                        handlersave.setString("server_url", "http://" + txt_ip.getText().toString() + ":9103");
                        handlersave.setString("server_short_url", "http://" + txt_ip.getText().toString());
                        handlersave.setString("login_url", "http://" + txt_ip.getText().toString() + ":3500");
                        handlersave.setString("api_creat_export_material", "http://" + txt_ip.getText().toString() + ":9999");
                        server_url = handlersave.getString("server_url");
                        login_url = handlersave.getString("login_url");
                        handlersave.setBoolean("isLoggedIn", false);
                        relaunchApp();
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        });


        btnreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PreferenceHandler handlersave = new PreferenceHandler(context);
                handlersave.setString("server_url", "");
                handlersave.setString("login_url", "");
                handlersave.setString("api_creat_export_material", "");
                handlersave.setString("server_short_url", "");

                ConfigManager configManager = new ConfigManager(context);
                txt_ip.setText(configManager.getProperty("server_url"));

            }
        });


        dialog.show();
    }

    private void relaunchApp() {
        // Get the package name of the app
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clears all activities and tasks
            startActivity(intent);
        }
    }

    public static boolean isValidIPAddress(String ip) {
        // Regular expression to match the IP format
        String ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        // Check if the IP matches the regular expression
        if (!ip.matches(ipRegex)) {
            return false;
        }

        // Split the IP by dots
        String[] octets = ip.split("\\.");

        // Ensure each octet is within the range 0-255
        for (String octet : octets) {
            int value = Integer.parseInt(octet);
            if (value < 0 || value > 255) {
                return false;
            }
        }

        return true;
    }


    public static long getNetworkTime()
    {
        // Get the current time in milliseconds from Internet
        try {
            String ntpServer = "time.google.com";
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(ntpServer);
            byte[] buffer = new byte[48];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, 123);

            buffer[0] = 0b00100011; // NTP mode 3 (client)
            socket.send(packet);

            socket.receive(packet);
            socket.close();

            long secondsSince1900 = ((buffer[43] & 0xFFL) | ((buffer[42] & 0xFFL) << 8) |
                    ((buffer[41] & 0xFFL) << 16) | ((buffer[40] & 0xFFL) << 24));

            long epochTime = secondsSince1900 - 2208988800L; // Convert to Unix time
            return epochTime * 1000; // Convert to milliseconds
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}