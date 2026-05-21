package com.mkac.meikomms.ui.setting;

import static android.content.Context.MODE_PRIVATE;
import static android.media.MediaExtractor.MetricsConstants.MIME_TYPE;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.mkac.meikomms.BuildConfig;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ColorConsole;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.FileDownloadInFragment;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.data.AppDatabase;
import com.mkac.meikomms.ui.FragmentProfile;
import com.mkac.meikomms.ui.checksheet.ChecksheetFragment;
import com.mkac.meikomms.ui.custom.Language;
import com.mkac.meikomms.ui.login.LoginActivity;

import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class SettingFragment extends Fragment {
    private Context mContext; // Store the context reference


    private FileDownloadInFragment downloader;
    private String DOWNLOAD_URL = "";
    private String FILE_NAME = "";
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";
    private static String uniqueID = null;
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context; // Store the context reference
    }

    private String server_short_url = "";
    private String server_url = "";
    private String version = "";
    private String system_name = "";
    private String screen_id = "";

    private String Variable_Name = "";
    private String Link_dowload = "";
    private ImageView update_icon;
    private RelativeLayout btn_update_app;
    private TextView btn_endwork_lable;
    private TextView btn_logout;
    private TextView serverLabel;
    private TextView servervalue;

    // Permission launchers
    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startDownload();
                } else {
                    Toast.makeText(requireContext(), "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> unknownSourcesLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        requireContext().getPackageManager().canRequestPackageInstalls()) {
                    startDownload();
                } else {
                    Toast.makeText(requireContext(), "Unknown sources permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceHandler handler = new PreferenceHandler(getContext());
        ConfigManager configManager = new ConfigManager(getContext());

        server_url = configManager.getProperty("server_url");
        version = configManager.getProperty("version");
        system_name = configManager.getProperty("system_name");
        screen_id = configManager.getProperty("screen_id");
        server_short_url = configManager.getProperty("server_short_url");
        if (!handler.getString("server_url").isEmpty()) {
            server_url = handler.getString("server_url");
        }

        if (!handler.getString("server_short_url").isEmpty()) {
            server_short_url = handler.getString("server_short_url");
        }


    }



    public static final String TAG = "SettingFragment";

    private AppDatabase appDatabase;
    private ArrayAdapter<String> alarmSettingAdapter;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rooter =  inflater.inflate(R.layout.fragment_setting, container, false);
//        ColorConsole.d("onCreateView: restarttt!!!!!");
        servervalue = rooter.findViewById(R.id.servervalue);
        serverLabel = rooter.findViewById(R.id.serverLabel);
        btn_update_app = rooter.findViewById(R.id.btn_update_app);
        btn_endwork_lable = rooter.findViewById(R.id.btn_endwork_lable);
        update_icon = rooter.findViewById(R.id.update_icon);
        LanguageAPIUtils.init(requireContext());
        appDatabase = AppDatabase.getInstance(requireContext());
        SharedPreferences appSettingsSharedPreferences = requireActivity().getSharedPreferences("AppSettings", MODE_PRIVATE);
        SharedPreferences.Editor appSettingsEditor = appSettingsSharedPreferences.edit();
        int languageSettingPosition = appSettingsSharedPreferences.getInt("languageSettingPosition", 0);
        Spinner deviceinformation = rooter.findViewById(R.id.deviceinformation);
        btn_logout = rooter.findViewById(R.id.btn_logout);
        TextView alarmSettingLabel = rooter.findViewById(R.id.alarmSettingLabel);
        TextView languageSettingLabel = rooter.findViewById(R.id.languageSettingLabel);
        TextView versionSettingLabel = rooter.findViewById(R.id.versionSettingLabel);
        TextView serverSettingLabel = rooter.findViewById(R.id.serverSettingLabel);
        TextView header_home_title = getActivity().findViewById(R.id.lable_screen_header);
        TextView menu_home_lable = getActivity().findViewById(R.id.menu_home_lable);
        TextView menu_setting_lable = getActivity().findViewById(R.id.menu_setting_lable);

//        //TODO: mode workorder
//        String selectedMode = "WORK_ORDER";
//        //TODO: người dùng chọn chế độ (workorder/ checksheet)
//        appSettingsEditor.putString("UI_MODE", selectedMode);
//        appSettingsEditor.apply();

        //TODO: GIẢ SỬ KHI NGƯỜI DÙNG CLICK VÀO MENU "WORK ORDER"
        FragmentProfile woProfile = new FragmentProfile();
        woProfile.screenTitle = "Work Order Management";
        woProfile.schemaData = "MES_MMS_MKHC";
        woProfile.schemaCore = "MEIKO_CORE";

        //TODO: Chỉ định các cột muốn hiện lên bảng (Theo đúng key JSON từ API)
        woProfile.columnKeys = Arrays.asList("WO_CODE", "REQUEST_DATE", "MACHINE_ID", "REQUEST_USER", "STATUS");
        woProfile.columnLabels = Arrays.asList("Mã W/O", "Ngày yêu cầu", "Mã máy", "Người yêu cầu", "Trạng thái");

        //TODO: Định nghĩa màu sắc trạng thái Work Order
        Map<String, FragmentProfile.StatusInfo> woStatus = new HashMap<>();
        woStatus.put("0", new FragmentProfile.StatusInfo("Chờ xử lý", "#FF99FF")); // Hồng
        woStatus.put("1", new FragmentProfile.StatusInfo("Đang sửa", "#98FE69")); // Xanh lá
        woStatus.put("2", new FragmentProfile.StatusInfo("Hoàn tất", "#66CCFD")); // Xanh dương
        woProfile.statusMapping = woStatus;

        //TODO: Mở Fragment
        ChecksheetFragment fragment = ChecksheetFragment.newInstance(woProfile);








        appDatabase.languageDao().getCurrentLanguage().observe(requireActivity(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
//                ColorConsole.d("appDatabase.languageDao().getCurrentLanguage() onChanged: ");
                alarmSettingLabel.setText(i18n("Device information"));
                languageSettingLabel.setText(i18n("Language"));
                versionSettingLabel.setText(i18n("Version"));
                serverSettingLabel.setText(i18n("Release date"));
                header_home_title.setText(i18n("MAINTENANCE MANAGEMENT SYSTEM"));
                btn_logout.setText(i18n("Logout"));
                menu_home_lable.setText( i18n("Home"));
                serverLabel.setText(i18n("Server"));
                menu_setting_lable.setText(i18n("System settings"));
                btn_endwork_lable.setText(i18n("Update"));

                List<String> infoList = getDeviceInfoFilteredList();

//                List<String> settingAlarmOption = new ArrayList<>();
//
//                settingAlarmOption.add(i18n("Operating system version"));
//                settingAlarmOption.add(i18n("API level"));
//                settingAlarmOption.add(i18n("Only notification"));

                alarmSettingAdapter =  new ArrayAdapter<>(mContext,
                        android.R.layout.simple_spinner_item, infoList);

                alarmSettingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                deviceinformation.setAdapter(alarmSettingAdapter);

                deviceinformation.setSelection(appSettingsSharedPreferences.getInt("alarmSettingPosition", 0));
                ColorConsole.d("alarmSettingPosition: "+appSettingsSharedPreferences.getInt("alarmSettingPosition", 0));
                deviceinformation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        //  String selectedOption = (String) parent.getItemAtPosition(position);
                        appSettingsEditor.putInt("alarmSettingPosition", 0);
                        appSettingsEditor.apply();
                    }


                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Do something when nothing is selected
                    }
                });


            }
        });

        Spinner languageSetting = rooter.findViewById(R.id.languageSetting);
        ArrayAdapter<CharSequence> languageAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.language_options, android.R.layout.simple_spinner_item);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSetting.setAdapter(languageAdapter);
        languageSetting.setSelection(languageSettingPosition);
        languageSetting.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //    String[] languageOptions = getResources().getStringArray(R.array.language_options);
                String selectedLanguageCode = ""; // Store the corresponding language code
                // Map the selected language to its language code
                if (position == 0)
                {
                    selectedLanguageCode = "ja";
                } else if (position == 1) {
                    selectedLanguageCode = "en";
                } else if (position == 2) {
                    selectedLanguageCode = "vi";
                }else if (position == 3) {
                    selectedLanguageCode = "ch";
                }

                // Apply the language change immediately
                appSettingsEditor.putInt("languageSettingPosition", position);
                appSettingsEditor.apply();
                String finalSelectedLanguageCode = selectedLanguageCode;
                LanguageAPIUtils.setLanguageCode(selectedLanguageCode);
                Executor executor = Executors.newSingleThreadExecutor();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
//                        ColorConsole.d("Selected language code: " + finalSelectedLanguageCode);
                        appDatabase.languageDao().insertLanguage(new Language(1, finalSelectedLanguageCode));
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do something when nothing is selected
            }
        });

        TextView versionSetting = rooter.findViewById(R.id.versionSetting);
        TextView serverSetting = rooter.findViewById(R.id.serverSetting);
        versionSetting.setText(version);

        String buildTime = BuildConfig.BUILD_TIME;
        serverSetting.setText(buildTime);

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
                        int new_ver = Integer.parseInt(newversion.replace(".",""));
                        if (current_ver < new_ver)
                        {
                            update_icon.setImageResource(R.drawable.update_button_new);
                        }
                    }
                }
                catch (Exception e)
                {


                }

            }

        });
        autoUpdate.start();


        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LogoutDialog(Gravity.CENTER, "LOGOUT", "Do you want to log out?" );
            }
        });


        btn_update_app.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                // checkPermissionsAndStartDownload();

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
                                    downloader = new FileDownloadInFragment(requireContext(), DOWNLOAD_URL, FILE_NAME,filesize, MIME_TYPE);

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

                                    requireActivity().runOnUiThread(() -> showUpdateDialog(newversion,FILE_NAME));
                                } else {
                                    requireActivity().runOnUiThread(() ->
                                            new AlertDialog.Builder(requireContext())
                                                    .setTitle(i18n("Update"))
                                                    .setMessage(i18n("The application has been updated to the latest version"))
                                                    .setNegativeButton(i18n("Close"), null)
                                                    .setCancelable(false)
                                                    .show()

                                    );
                                }
                            }
                        }
                        catch (Exception e)
                        {


                        }

                    }

                });
                autoUpdate.start();

            }
        });

        servervalue.setText(server_short_url);

        return  rooter;
    }

    private List<String> getDeviceInfoFilteredList() {
        List<String> infoList = new ArrayList<>();

        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float density = metrics.density;
            int widthDp = (int) (metrics.widthPixels / density);
            int heightDp = (int) (metrics.heightPixels / density);
            int smallestWidth = Math.min(widthDp, heightDp);

            String osVersionRaw = System.getProperty("os.version");
            String androidVersion = "-";
            if (osVersionRaw != null && osVersionRaw.toLowerCase().contains("android")) {
                for (String part : osVersionRaw.split("-")) {
                    if (part.toLowerCase().contains("android")) {
                        androidVersion = part;
                        break;
                    }
                }
            }
            if(androidVersion.equals("-"))
            {
                androidVersion = android.os.Build.VERSION.RELEASE;
            }

            infoList.add(i18n("Operating system version")+" : " + androidVersion.replace("android",""));
            infoList.add(i18n("API level")+" : " + Build.VERSION.SDK_INT);
            infoList.add(i18n("Model")+" : " + Build.MODEL);
            infoList.add(i18n("Manufacturer")+" : " + Build.MANUFACTURER);
            infoList.add(i18n("Dots Per Inch")+" : " + smallestWidth);

        } catch (Exception e) {
            infoList.add("Lỗi khi lấy thông tin thiết bị: " + e.getMessage());
        }

        return infoList;
    }
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
    private void showUpdateDialog(String latestVersion,String file_name)
    {
        new AlertDialog.Builder(requireContext())
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
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isApkFile() &&
                !requireContext().getPackageManager().canRequestPackageInstalls()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()));
            unknownSourcesLauncher.launch(intent);
        }
        else
        {
            startDownload();
        }
    }

    private void startDownload() {
        if (isAdded()) {
            downloader.startDownload();
        } else {
            Toast.makeText(requireContext(), "Fragment not attached", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isApkFile() {
        return MIME_TYPE.equals("application/vnd.android.package-archive") || FILE_NAME.endsWith(".apk");
    }


    private void LogoutDialog(int gravity, String tieude, String noidung)
    {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_dialog_yesno);

        TextView btncancel = (TextView) dialog.findViewById(R.id.btnclose);
        TextView btnOK = (TextView) dialog.findViewById(R.id.btnlogout);
        TextView txtnoidung = (TextView) dialog.findViewById(R.id.txtnoidung);
        TextView tvTitle = (TextView) dialog.findViewById(R.id.tvTitle);
        ImageView btn_close = (ImageView) dialog.findViewById(R.id.btn_close);
        txtnoidung.setText(i18n(noidung));
        tvTitle.setText(i18n(tieude));
        Window window = dialog.getWindow();
        btnOK.setText(i18n("YES"));
        btncancel.setText(i18n("NO"));
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

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {

                PreferenceHandler handler = new PreferenceHandler(getContext());
                handler.setBoolean("isLoggedIn",false);
                dialog.dismiss();
                getActivity().finish();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                activityResultLaunch.launch(intent);


            }
        });

        dialog.show();
    }

    ActivityResultLauncher<Intent> activityResultLaunch = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result)
                {

                }
            });

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

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

}