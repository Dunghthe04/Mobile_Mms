package com.mkac.meikomms.ui.workorder;

import static android.media.MediaExtractor.MetricsConstants.MIME_TYPE;
import static androidx.core.content.ContentProviderCompat.requireContext;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ColorConsole;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.FileDownloadInFragment;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.TimeUtils;
import com.mkac.meikomms.ui.login.LoginActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class ListWorkOrderActivity extends AppCompatActivity {
    private static final String TAG = "ListWorkOrderActivity";
    private static final int REQUEST_CODE_ADD_WO = 1001;
    private String serverDynamic, schemaData, schemaCore;
    private String currentDSACondition = "1=1";
    private final String dateColumn = "REQUEST_DATE";
    private HorizontalScrollView hsvTableContainer;

    private TextView tvStartDate, tvEndDate, tvCountdown;
    private TextView btnFilterDay, btnFilterWeek, btnFilterMonth;
    private TextView btnResetFilter;
    private CheckBox cbChuaThucHien, cbQuaHan, cbDaThucHien;
    private TextView tvCountChua, tvCountQuaHan, tvCountDaThucHien;
    private TextView tvProgressChua, tvProgressQuaHan, tvProgressDaThucHien;
    private TextView tvUsernameDisplay;
    private RecyclerView rvWorkOrder;
    private LinearLayout headerFixedContainer;
    private LinearLayout headerScrollContainer;
    private LinearLayout headerActionContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private WorkOrderAdapter adapter;
    private CountDownTimer countDownTimer;
    private List<JSONObject> originalDataList = new ArrayList<>();
    private final Set<String> selectedStatuses = new HashSet<>();
    private boolean isDestroyed = false;
    private final int[] COLUMN_WIDTHS = {
            50, 120, 100, 180, 100, 80, 120, 120, 100, 200, 120, 120
    };
    private static final int FIXED_TABLE_WIDTH_DP = 170;
    private static final int ACTION_TABLE_WIDTH_DP = 104;
    private static final int TABLE_CELL_MIN_HEIGHT_DP = 44;
        private final String[] HEADERS = {
            "No.", "W/O code", "Request date", "Machine", "Process", "Work type", "Creator", "Requester", "Elapsed days", "Work Order Content", "WO status", "MA status"
        };
    private ImageView update_icon;
    private RelativeLayout btn_update_app;
    private FileDownloadInFragment downloader;
    private String DOWNLOAD_URL = "";
    private String FILE_NAME = "";
    private TextView serverLabel;
    private TextView servervalue;
    private String server_short_url = "";
    private String server_url = "";
    private String version = "";
    private String system_name = "";
    private String screen_id = "";
    private String currentUserId = "";
    private String currentDivisionName = "";
    private String currentDepartmentCode = "";
    private int currentQuickFilterDays = -1;
    private int currentTableScrollX = 0;
    private boolean isTableScrollSyncing = false;
    private boolean isCardViewMode = true;
    private ImageView btnToggleView;
    private View tableHeaderContainer;
    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startDownload();
                } else {
                    Toast.makeText(this, i18n("Storage permission denied"), Toast.LENGTH_SHORT).show();
                }
            });

    public static void start(Context context) {
        Intent intent = new Intent(context, ListWorkOrderActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list_work_order);

        ImageView imgUserProfile = findViewById(R.id.img_user_profile);
        if (imgUserProfile != null) {
            imgUserProfile.setOnClickListener(v -> showLogoutConfirmation());
        }

        initConfiguration();
        loadCurrentUserInfo();
        initViews();
        setupEventListeners();

        originalDataList.clear();
        selectedStatuses.clear();

        initSelectedStatuses();
        startReloadTimer();

        LanguageAPIUtils.setLang(findViewById(android.R.id.content));
        applyI18nTexts();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rooter = inflater.inflate(R.layout.activity_list_work_order, container, false);
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
                                    downloader = new FileDownloadInFragment(getBaseContext(), DOWNLOAD_URL, FILE_NAME,filesize, MIME_TYPE);
                                    downloader.setDownloadListener(new FileDownloadInFragment.DownloadListener() {
                                        @Override
                                        public void onDownloadSuccess(String filePath) {
                                            // Toast.makeText(this, "Downloaded to: " + filePath, Toast.LENGTH_LONG).show();
                                        }
                                        @Override
                                        public void onDownloadFailed(String error) {
                                            // Toast.makeText(this, "Download failed: " + error, Toast.LENGTH_LONG).show();
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
                                } else {
                                    runOnUiThread(() ->
                                            new AlertDialog.Builder(ListWorkOrderActivity.this)
                                                    //new AlertDialog.Builder(getBaseContext())
                                                    .setTitle(i18n("Update"))
                                                    .setMessage(i18n("The application has been updated to the latest version"))
                                                    .setNegativeButton(i18n("Close"), null)
                                                    .setCancelable(false)
                                                    .show()
                                    );
                                }
                            }
                        }
                        catch (Exception e) {}
                    }
                });
                autoUpdate.start();
            }
        });

        servervalue.setText(server_short_url);
        return  rooter;

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_work_order, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.btn_change_language) {
            showLanguagePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLanguagePicker() {
        final String[] languageCodes = {"ja", "en", "vi", "ch"};
        final String[] languageNames = {"日本", "English", "Tiếng Việt", "Chinese"};

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        int currentPosition = prefs.getInt("languageSettingPosition", 2);

        new AlertDialog.Builder(this)
                .setTitle(i18n("Language"))
                .setSingleChoiceItems(languageNames, currentPosition, (dialog, which) -> {
                    String selectedCode = languageCodes[which];

                    prefs.edit().putInt("languageSettingPosition", which).commit();
                    LanguageAPIUtils.setLanguageCode(selectedCode);

                    LanguageAPIUtils.setLang(findViewById(android.R.id.content));
                    applyI18nTexts();
                    if (adapter != null) adapter.notifyDataSetChanged();

                    dialog.dismiss();
                })
                .setNegativeButton(i18n("Cancel"), null)
                .show();
    }
    private void initConfiguration() {
        try {
            ConfigManager configManager = new ConfigManager(this);
            serverDynamic = configManager.getProperty("server_dynamic_url");
            if (serverDynamic == null || serverDynamic.isEmpty()) {
                serverDynamic = "http://192.86.0.225:9101/api/dynamics";
            }

            schemaData = configManager.getProperty("schema_mms");
            if (schemaData == null || schemaData.isEmpty()) {
                schemaData = "MES_MMS_MKHC";
            }

            schemaCore = configManager.getProperty("schema_core");
            if (schemaCore == null || schemaCore.isEmpty()) {
                schemaCore = "MES_CORE_MKHC";
            }
        } catch (Exception e) {
            serverDynamic = "http://192.86.0.225:9101/api/dynamics";
            schemaData = "MES_MMS_MKHC";
            schemaCore = "MES_CORE_MKHC";
        }
    }

    private void initViews() {
        tvStartDate = findViewById(R.id.tv_start_date);
        tvEndDate = findViewById(R.id.tv_end_date);
        tvCountdown = findViewById(R.id.tv_countdown);
        btnFilterDay = findViewById(R.id.btn_filter_day);
        btnFilterWeek = findViewById(R.id.btn_filter_week);
        btnFilterMonth = findViewById(R.id.btn_filter_month);
        btnResetFilter = findViewById(R.id.btn_reset_filter);

        cbChuaThucHien = findViewById(R.id.cb_chua_thuc_hien);
        cbQuaHan = findViewById(R.id.cb_qua_han);
        cbDaThucHien = findViewById(R.id.cb_da_thuc_hien);

        tvCountChua = findViewById(R.id.tv_count_chua_thuc_hien);
        tvCountQuaHan = findViewById(R.id.tv_count_qua_han);
        tvCountDaThucHien = findViewById(R.id.tv_count_da_thuc_hien);

        tvProgressChua = findViewById(R.id.tv_progress_chua_thuc_hien);
        tvProgressQuaHan = findViewById(R.id.tv_progress_qua_han);
        tvProgressDaThucHien = findViewById(R.id.tv_progress_da_thuc_hien);

        tvUsernameDisplay = findViewById(R.id.tv_username_display);
        displayUserInfo();

        rvWorkOrder = findViewById(R.id.rv_work_order);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        hsvTableContainer = findViewById(R.id.hsv_table_container);
        headerFixedContainer = findViewById(R.id.header_fixed_container);
        headerScrollContainer = findViewById(R.id.header_scroll_container);
        headerActionContainer = findViewById(R.id.header_action_container);
        tableHeaderContainer = findViewById(R.id.table_header_container);
        btnToggleView = findViewById(R.id.btn_toggle_view);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#2F5597"));
            swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> rvWorkOrder != null && rvWorkOrder.canScrollVertically(-1));
            swipeRefreshLayout.setOnRefreshListener(() -> loadData(currentDSACondition));
        }

        if (hsvTableContainer != null) {
            hsvTableContainer.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hsvTableContainer.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (isTableScrollSyncing) return;
                if (scrollX == currentTableScrollX) return;
                currentTableScrollX = scrollX;
                syncTableScroll(scrollX, null);
            });
        }

        if (rvWorkOrder != null) {
            rvWorkOrder.setLayoutManager(new LinearLayoutManager(this));
            adapter = new WorkOrderAdapter();
            rvWorkOrder.setAdapter(adapter);
        }

        renderTableHeaders();
        updateViewModeUi();
    }

    private void setupEventListeners(){
        if (btnFilterDay != null) btnFilterDay.setOnClickListener(v -> performFilter(0));
        if (btnFilterWeek != null) btnFilterWeek.setOnClickListener(v -> performFilter(8));
        if (btnFilterMonth != null) btnFilterMonth.setOnClickListener(v -> performFilter(31));
        if (btnResetFilter != null) btnResetFilter.setOnClickListener(v -> resetFilters());

        if (tvStartDate != null) tvStartDate.setOnClickListener(v -> showDatePicker(true));
        if (tvEndDate != null) tvEndDate.setOnClickListener(v -> showDatePicker(false));

        View.OnClickListener statusToggle = v -> {
            updateSelectedStatuses();
            filterAndPopulateList();
        };

        if (findViewById(R.id.ln_chua_thuc_hien) != null) findViewById(R.id.ln_chua_thuc_hien).setOnClickListener(v -> { if(cbChuaThucHien != null) cbChuaThucHien.toggle(); statusToggle.onClick(v); });
        if (findViewById(R.id.ln_qua_han) != null) findViewById(R.id.ln_qua_han).setOnClickListener(v -> { if(cbQuaHan != null) cbQuaHan.toggle(); statusToggle.onClick(v); });
        if (findViewById(R.id.ln_da_thuc_hien) != null) findViewById(R.id.ln_da_thuc_hien).setOnClickListener(v -> { if(cbDaThucHien != null) cbDaThucHien.toggle(); statusToggle.onClick(v); });

        if (findViewById(R.id.btn_add_wo) != null) {
            findViewById(R.id.btn_add_wo).setOnClickListener(v -> {
                Intent intent = new Intent(ListWorkOrderActivity.this, WorkOrderActivity.class);
                intent.putExtra("LANGUAGE_CODE", LanguageAPIUtils.getLanguageCode());
                startActivityForResult(intent, REQUEST_CODE_ADD_WO);
            });
        }

//        if (btnToggleView != null) {
//            btnToggleView.setOnClickListener(v -> {
//                isCardViewMode = !isCardViewMode;
//                updateViewModeUi();
//                if (adapter != null) adapter.notifyDataSetChanged();
//            });
//        }

    }

    private void applyI18nTexts() {
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView btnAddWo = findViewById(R.id.btn_add_wo);
        TextView btnFilterDay = findViewById(R.id.btn_filter_day);
        TextView btnFilterWeek = findViewById(R.id.btn_filter_week);
        TextView btnFilterMonth = findViewById(R.id.btn_filter_month);

        if (tvTitle != null) tvTitle.setText(i18n("W/O Management"));
        if (btnAddWo != null) btnAddWo.setText("+ " + i18n("Add W/O"));
        if (btnFilterDay != null) btnFilterDay.setText(i18n("Day"));
        if (btnFilterWeek != null) btnFilterWeek.setText(i18n("Week"));
        if (btnFilterMonth != null) btnFilterMonth.setText(i18n("Month"));
        if (btnResetFilter != null) btnResetFilter.setText(i18n("Reset"));

        updateQuickFilterUi();

        if (tvStartDate != null && !tvStartDate.getText().toString().contains("/")) {
            tvStartDate.setText(i18n("From"));
        }
        if (tvEndDate != null && !tvEndDate.getText().toString().contains("/")) {
            tvEndDate.setText(i18n("To"));
        }

        renderTableHeaders();
        updateViewModeUi();
        updateSummary(originalDataList);
    }

    private void updateViewModeUi() {
        if (btnToggleView != null) {
            btnToggleView.setImageResource(isCardViewMode
                    ? android.R.drawable.ic_menu_sort_by_size
                    : android.R.drawable.ic_menu_agenda);
            btnToggleView.setContentDescription(i18n(isCardViewMode ? "Switch to table view" : "Switch to card view"));
        }

        int headerVisibility = isCardViewMode ? View.GONE : View.VISIBLE;
        if (tableHeaderContainer != null) tableHeaderContainer.setVisibility(headerVisibility);
    }

    private void renderTableHeaders() {
        if (headerFixedContainer != null) headerFixedContainer.removeAllViews();
        if (headerScrollContainer != null) headerScrollContainer.removeAllViews();
        if (headerActionContainer != null) headerActionContainer.removeAllViews();

        if (headerFixedContainer != null) {
            addHeaderCell(headerFixedContainer, "No.", COLUMN_WIDTHS[0]);
            addHeaderCell(headerFixedContainer, "W/O code", COLUMN_WIDTHS[1]);
        }

        if (headerScrollContainer != null) {
            for (int i = 2; i < HEADERS.length; i++) {
                addHeaderCell(headerScrollContainer, HEADERS[i], COLUMN_WIDTHS[i]);
            }
        }

        if (headerActionContainer != null) {
            addHeaderCell(headerActionContainer, i18n("Action"), 96);
        }

        if (hsvTableContainer != null) {
            hsvTableContainer.post(() -> hsvTableContainer.scrollTo(currentTableScrollX, 0));
        }
    }

    private void addHeaderCell(LinearLayout parent, String title, int widthDp) {
        if (parent == null) return;
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(widthDp), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(1, 1, 1, 1);
        tv.setLayoutParams(lp);
        tv.setText(i18n(title));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
        tv.setMinHeight(dpToPx(TABLE_CELL_MIN_HEIGHT_DP));
        tv.setIncludeFontPadding(false);
        tv.setBackgroundResource(R.drawable.bg_table_header_sticky);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(tv);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_WO && resultCode == RESULT_OK) {
            loadData(currentDSACondition);
        }
    }

    private void initSelectedStatuses() {
        if (cbChuaThucHien != null) cbChuaThucHien.setChecked(true);
        if (cbQuaHan != null) cbQuaHan.setChecked(true);
        if (cbDaThucHien != null) cbDaThucHien.setChecked(true);
        updateSelectedStatuses();
    }

    private void updateSelectedStatuses() {
        selectedStatuses.clear();
        if (cbChuaThucHien != null && cbChuaThucHien.isChecked()) selectedStatuses.add("CHUA_THUC_HIEN");
        if (cbQuaHan != null && cbQuaHan.isChecked()) selectedStatuses.add("QUA_HAN");
        if (cbDaThucHien != null && cbDaThucHien.isChecked()) selectedStatuses.add("DA_THUC_HIEN");
    }


    private void loadData(String dsaCondition) {
        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");

        if (userObj == null || userObj.length() == 0) {
            logout();
            return;
        }

        String token = userObj.optString("token", "");

        if (token == null || token.isEmpty()) {
            logout();
            return;
        }

        HttpClient.initToken(token);

        if (isDestroyed) return;

        new Thread(() -> {
            HttpClient.APIReturn apiReturn = HttpClient.getAllWorkOrder(
                    getApplicationContext(),
                    serverDynamic,
                    schemaData,
                    schemaCore,
                    dsaCondition,
                    0,
                    100
            );
            if (isDestroyed || isFinishing()) return;
            runOnUiThread(() -> {
                if(isDestroyed || isFinishing()) return;
                if (apiReturn != null && apiReturn.code == 200 && apiReturn.data != null) {
                    originalDataList.clear();
                    originalDataList.addAll(apiReturn.data);

                    updateSummary(originalDataList);
                    filterAndPopulateList();

                    if (!originalDataList.isEmpty()) {
                        rvWorkOrder.scrollToPosition(0);
                    }
                } else {
                    adapter.setItems(new ArrayList<>());
                }

                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
    }

    private String getStatusType(JSONObject data) {
        int status1 = -1;
        try {
            if (data.has("STATUS_1")) {
                status1 = Integer.parseInt(data.optString("STATUS_1", "-1"));
            } else if (data.has("Status_1")) {
                status1 = Integer.parseInt(data.optString("Status_1", "-1"));
            }
        } catch (Exception e) {
            status1 = -1;
        }
        switch (status1) {
            case 1:
                return "DA_THUC_HIEN";
            case 5:
                return "QUA_HAN";
            case 0:
            default:
                return "CHUA_THUC_HIEN";
        }
    }

    private boolean isOverdue(String deadlineStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date deadline = sdf.parse(deadlineStr.replace("Z", ""));
            return deadline != null && deadline.before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private void filterAndPopulateList() {
        if (adapter == null) return;
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject obj : originalDataList) {
            if (selectedStatuses.contains(getStatusType(obj))) filtered.add(obj);
        }
        adapter.setItems(filtered);
    }

    private void updateSummary(List<JSONObject> list) {
        int cChua = 0, cQua = 0, cXong = 0;
        for (JSONObject obj : list) {
            if (obj == null) continue;
            String type = getStatusType(obj);
            if (type.equals("CHUA_THUC_HIEN")) cChua++;
            else if (type.equals("QUA_HAN")) cQua++;
            else if (type.equals("DA_THUC_HIEN")) cXong++;
        }

        if (tvCountChua != null) tvCountChua.setText(i18n("Incomplete") + ": " + cChua);
        if (tvCountQuaHan != null) tvCountQuaHan.setText(i18n("Overdue") + ": " + cQua);
        if (tvCountDaThucHien != null) tvCountDaThucHien.setText(i18n("Completed") + ": " + cXong);

        int total = list.size();
        updateProgressPart(tvProgressChua, cChua, total);
        updateProgressPart(tvProgressQuaHan, cQua, total);
        updateProgressPart(tvProgressDaThucHien, cXong, total);
    }

    private void updateProgressPart(TextView tv, int count, int total) {
        if (tv == null) return;
        if (total == 0) { tv.setVisibility(View.GONE); return; }
        float percent = (count * 100f) / total;

        ViewGroup.LayoutParams params = tv.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
            lp.weight = percent > 0 ? percent : 0.001f;
            tv.setLayoutParams(lp);
        }

        tv.setText(percent > 0 ? String.format(Locale.getDefault(), "%.0f%%", percent) : "");
        tv.setVisibility(percent > 0 ? View.VISIBLE : View.GONE);
    }

    private void performFilter(int daysBack) {
        // Toggle behavior: tapping the active quick filter resets to the initial default state.
        if (currentQuickFilterDays == daysBack) {
            resetFilters();
            return;
        }

        currentQuickFilterDays = daysBack;
        updateQuickFilterUi();

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdfUI = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat sdfDB = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        String endUI = sdfUI.format(cal.getTime());
        String endDB = sdfDB.format(cal.getTime());

        if (tvEndDate != null) tvEndDate.setText(endUI);

        if (daysBack == 0) {
            if (tvStartDate != null) tvStartDate.setText(endUI);
            currentDSACondition = "TRUNC(" + dateColumn + ") = TO_DATE('" + endDB + "', 'YYYY-MM-DD')";
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1));
            String startUI = sdfUI.format(cal.getTime());
            String startDB = sdfDB.format(cal.getTime());
            if (tvStartDate != null) tvStartDate.setText(startUI);
            currentDSACondition = "TRUNC(" + dateColumn + ") BETWEEN TO_DATE('" + startDB + "', 'YYYY-MM-DD') AND TO_DATE('" + endDB + "', 'YYYY-MM-DD')";
        }
        loadData(currentDSACondition);
    }

    private void startReloadTimer() {
        if (isDestroyed) return;
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(15000, 1000) {
            public void onTick(long ms) { if(tvCountdown != null) tvCountdown.setText(String.format(Locale.getDefault(), "00:%02d", ms/1000)); }
            public void onFinish() {
                if (!isDestroyed) {
                    loadData(currentDSACondition);
                    startReloadTimer();
                }
            }
        }.start();
    }

    private void showDatePicker(boolean isStart) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            String date = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y);
            if (isStart) { if(tvStartDate != null) tvStartDate.setText(date); } else { if(tvEndDate != null) tvEndDate.setText(date); }
            performManualFilter();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void performManualFilter() {
        currentQuickFilterDays = -1;
        updateQuickFilterUi();

        if (tvStartDate == null || tvEndDate == null) return;
        String startStr = tvStartDate.getText().toString();
        String endStr = tvEndDate.getText().toString();
        if (startStr.contains("/") && endStr.contains("/")) {
            try {
                SimpleDateFormat sdfUI = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                SimpleDateFormat sdfDB = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String sDB = sdfDB.format(sdfUI.parse(startStr));
                String eDB = sdfDB.format(sdfUI.parse(endStr));
                currentDSACondition = "TRUNC(" + dateColumn + ") BETWEEN TO_DATE('" + sDB + "', 'YYYY-MM-DD') AND TO_DATE('" + eDB + "', 'YYYY-MM-DD')";
                loadData(currentDSACondition);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private class WorkOrderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_TABLE = 0;
        private static final int VIEW_TYPE_CARD = 1;
        public final List<JSONObject> items = new ArrayList<>();
        public int selectedPosition = -1;

        void setItems(List<JSONObject> newItems) {
            items.clear();
            items.addAll(newItems);
            selectedPosition = -1;
            notifyDataSetChanged();
        }

        private void selectRow(int newPosition) {
            if (newPosition == RecyclerView.NO_POSITION || newPosition < 0 || newPosition >= items.size()) return;
            int oldPosition = selectedPosition;

            if (oldPosition == newPosition) {
                selectedPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(oldPosition);
                return;
            }

            selectedPosition = newPosition;

            if (oldPosition != RecyclerView.NO_POSITION && oldPosition >= 0 && oldPosition < items.size()) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(newPosition);
        }

        private void ensureRowSelected(int newPosition) {
            if (newPosition == RecyclerView.NO_POSITION || newPosition < 0 || newPosition >= items.size()) return;
            if (selectedPosition == newPosition) return;
            int oldPosition = selectedPosition;
            selectedPosition = newPosition;
            if (oldPosition != RecyclerView.NO_POSITION && oldPosition >= 0 && oldPosition < items.size()) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(newPosition);
        }

        @Override
        public int getItemViewType(int position) {
            return isCardViewMode ? VIEW_TYPE_CARD : VIEW_TYPE_TABLE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_CARD) {
                View cardView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_work_order_card, parent, false);
                return new CardVH(cardView);
            }

            View tableView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_work_order, parent, false);
            return new RowVH(tableView);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            try {
                JSONObject data = items.get(position);
                if (data == null) data = new JSONObject();

                RowDisplayData rowData = buildRowDisplayData(data, position);

                if (holder instanceof CardVH) {
                    bindCardView((CardVH) holder, data, rowData, position == selectedPosition);
                } else if (holder instanceof RowVH) {
                    bindTableView((RowVH) holder, data, rowData, position == selectedPosition);
                }
            } catch (Exception e) {
                ColorConsole.e("ADAPTER_CRASH", "Loi bind view: " + e.getMessage());
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        private RowDisplayData buildRowDisplayData(JSONObject data, int position) {
            RowDisplayData dto = new RowDisplayData();
            dto.number = String.valueOf(position + 1);
            dto.typeCode = getStatusType(data);

            dto.woCode = safeGet(data, "WO_CODE");
            if (dto.woCode.isEmpty()) dto.woCode = safeGet(data, "Wo_Code");

            String rawRequestDate = safeGet(data, "Request_Date");
            dto.requestDate = formatDate(rawRequestDate);
            dto.requestDateOnly = formatDatePart(rawRequestDate);
            dto.requestTimeOnly = formatTimePart(rawRequestDate);

            String machineId = safeGet(data, "Machine_Id");
            String machineName = safeGet(data, "Machine_Name");
            dto.machine = machineId + (machineName.isEmpty() ? "" : " - " + machineName);

            dto.process = safeGet(data, "Physical_Group_Name");
            if (dto.process.isEmpty()) dto.process = safeGet(data, "PHYSICAL_GROUP_NAME");

            dto.workType = formatWoType(data.optInt("Wo_Type", 0));

            String createUser = safeGet(data, "Create_By");
            String createFullName = safeGet(data, "Create_Full_Name");
            if (!createUser.isEmpty() && !createFullName.isEmpty()) dto.creator = createUser + " - " + createFullName;
            else if (!createUser.isEmpty()) dto.creator = createUser;
            else dto.creator = createFullName;

            String reqUser = safeGet(data, "Request_User");
            String reqFullName = safeGet(data, "Full_Name");
            if (!reqUser.isEmpty() && !reqFullName.isEmpty()) dto.requester = reqUser + " - " + reqFullName;
            else if (!reqUser.isEmpty()) dto.requester = reqUser;
            else dto.requester = reqFullName;

            dto.elapsedDays = getPassedDate(rawRequestDate);
            dto.deadline = formatDate(safeGet(data, "Deadline"));

            dto.content = safeGet(data, "Request_Reason");
            if (dto.content.isEmpty()) dto.content = safeGet(data, "Note");

            dto.woStatus = formatWoStatusUI(dto.typeCode);
            dto.maStatus = resolveMaStatusDisplay(data);
            return dto;
        }

        private void bindTableView(RowVH holder, JSONObject data, RowDisplayData dto, boolean isSelected) {
            boolean canEditRow = canEdit(data);
            boolean canDeleteRow = canDelete(data);

            if (holder.rowScrollView != null) {
                holder.rowScrollView.post(() -> {
                    int targetScrollX = clampScrollX(holder.rowScrollView, currentTableScrollX);
                    if (holder.rowScrollView.getScrollX() != targetScrollX) {
                        holder.rowScrollView.scrollTo(targetScrollX, 0);
                    }
                });
                holder.rowScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (isTableScrollSyncing) return;
                    if (scrollX == currentTableScrollX) return;
                    currentTableScrollX = scrollX;
                    syncTableScroll(scrollX, holder);
                });
            }

            for (int i = 0; i < holder.fixedCells.length; i++) {
                holder.fixedCells[i].setBackgroundResource(
                        isSelected ? R.drawable.bg_table_fixed_cell_selected : R.drawable.bg_table_fixed_cell
                );
                holder.fixedCells[i].setTextColor(Color.BLACK);
                holder.fixedCells[i].setTypeface(null, android.graphics.Typeface.BOLD);
                holder.fixedCells[i].setMinHeight(dpToPx(TABLE_CELL_MIN_HEIGHT_DP));
                holder.fixedCells[i].setIncludeFontPadding(false);
                holder.fixedCells[i].setOnClickListener(v -> selectRow(holder.getAdapterPosition()));
            }

            holder.fixedCells[0].setText(dto.number);
            holder.fixedCells[1].setText(dto.woCode);

            String[] values = {
                    dto.requestDate,
                    dto.machine,
                    dto.process,
                    dto.workType,
                    dto.creator,
                    dto.requester,
                    dto.elapsedDays,
                    dto.content,
                    dto.woStatus,
                    dto.maStatus
            };

            for (int i = 0; i < holder.cells.length; i++) {
                holder.cells[i].setBackgroundResource(
                        isSelected ? R.drawable.bg_cell_selected : R.drawable.bg_cell
                );
                holder.cells[i].setTextColor(Color.BLACK);
                holder.cells[i].setMinHeight(dpToPx(TABLE_CELL_MIN_HEIGHT_DP));
                holder.cells[i].setIncludeFontPadding(false);
                holder.cells[i].setOnClickListener(v -> selectRow(holder.getAdapterPosition()));

                if (i == 8) {
                    if ("CHUA_THUC_HIEN".equals(dto.typeCode)) {
                        holder.cells[i].setTextColor(Color.parseColor("#A9A9A9"));
                    } else if ("QUA_HAN".equals(dto.typeCode)) {
                        holder.cells[i].setTextColor(Color.parseColor("#FF9800"));
                    } else if ("DA_THUC_HIEN".equals(dto.typeCode)) {
                        holder.cells[i].setTextColor(Color.parseColor("#2196F3"));
                    }
                    holder.cells[i].setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    holder.cells[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                }

                if (i < values.length) {
                    holder.cells[i].setText(values[i]);
                }
            }

            applyActions(holder.btnEdit, holder.btnDelete, canEditRow, canDeleteRow, holder.getAdapterPosition());

            holder.itemView.setOnClickListener(v -> selectRow(holder.getAdapterPosition()));
            holder.layoutForeground.setOnClickListener(v -> selectRow(holder.getAdapterPosition()));

            if (isSelected) {
                holder.layoutForeground.setBackgroundResource(R.drawable.bg_row_selected);
                holder.actionContainer.setBackgroundResource(R.drawable.bg_table_action_container_selected);
            } else {
                holder.layoutForeground.setBackgroundResource(R.drawable.bg_row);
                holder.actionContainer.setBackgroundResource(R.drawable.bg_table_action_container);
            }
            holder.layoutForeground.setTranslationX(0);
        }

        private void bindCardView(CardVH holder, JSONObject data, RowDisplayData dto, boolean isSelected) {
            boolean canEditRow = canEdit(data);
            boolean canDeleteRow = canDelete(data);

            holder.tvNo.setText(dto.number);
            holder.tvWoCode.setText(dto.woCode);
            holder.tvMachineInline.setText(dto.machine);
            holder.tvWorkTypeInline.setText(dto.workType);
            holder.tvRequestDate.setText(dto.requestDateOnly);
            holder.tvRequestTime.setText(dto.requestTimeOnly);
            holder.tvWoStatus.setText(dto.woStatus);
            holder.tvMachine.setText(i18n("Machine") + ": " + dto.machine);
            holder.tvProcess.setText(i18n("Process") + ": " + dto.process);
            holder.tvWorkType.setVisibility(View.GONE);
            holder.tvCreator.setText(i18n("Creator") + ": " + dto.creator);
            holder.tvRequester.setText(i18n("Requester") + ": " + dto.requester);
            holder.tvElapsedDays.setText(i18n("Elapsed days") + ": " + dto.elapsedDays);
            holder.tvDeadline.setText(i18n("Deadline") + ": " + dto.deadline);
            holder.tvContent.setText(i18n("Work Order Content") + ": " + dto.content);
            holder.tvMaStatus.setText(i18n("MA status") + ": " + dto.maStatus);

            int statusColor = Color.parseColor("#9CA3AF");
            if ("QUA_HAN".equals(dto.typeCode)) {
                statusColor = Color.parseColor("#F59E0B");
            } else if ("DA_THUC_HIEN".equals(dto.typeCode)) {
                statusColor = Color.parseColor("#2563EB");
            }
            holder.tvWoStatus.setBackgroundTintList(ColorStateList.valueOf(statusColor));
            holder.tvWoStatus.setTextColor(Color.WHITE);

            holder.tvMaStatus.setBackgroundColor(Color.TRANSPARENT);
            holder.tvMaStatus.setTextColor(Color.parseColor("#9A3412"));

            holder.cardRoot.setStrokeColor(isSelected ? Color.parseColor("#2F5597") : Color.parseColor("#D4D9E2"));
            holder.itemView.setOnClickListener(v -> selectRow(holder.getAdapterPosition()));
            holder.cardRoot.setOnClickListener(v -> selectRow(holder.getAdapterPosition()));

            applyActions(holder.btnEdit, holder.btnDelete, canEditRow, canDeleteRow, holder.getAdapterPosition());
        }

        private void applyActions(ImageView btnEdit, ImageView btnDelete, boolean canEditRow, boolean canDeleteRow, int adapterPosition) {
            btnEdit.setEnabled(canEditRow);
            btnEdit.setClickable(canEditRow);
            btnEdit.setAlpha(canEditRow ? 1f : 0.35f);

            btnDelete.setEnabled(canDeleteRow);
            btnDelete.setClickable(canDeleteRow);
            btnDelete.setAlpha(canDeleteRow ? 1f : 0.35f);

            btnEdit.setOnClickListener(v -> handleEdit(adapterPosition, v));
            btnDelete.setOnClickListener(v -> handleDelete(adapterPosition, v));
        }

        private void handleEdit(int adapterPosition, View clickedView) {
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition < 0 || adapterPosition >= items.size()) return;

            JSONObject item = items.get(adapterPosition);
            ensureRowSelected(adapterPosition);
            if (!canEdit(item)) {
                Toast.makeText(ListWorkOrderActivity.this,
                        i18n("You do not have permission to edit this Work Order or it is already Done"),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            checkEditPermissionAsync(item, allowed -> runOnUiThread(() -> {
                if (!allowed) {
                    Toast.makeText(ListWorkOrderActivity.this,
                            i18n("You do not have permission to edit this Work Order or it is already Done"),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(clickedView.getContext(), WorkOrderActivity.class);
                intent.putExtra("DATA", item.toString());
                intent.putExtra("LANGUAGE_CODE", LanguageAPIUtils.getLanguageCode());

                ((AppCompatActivity) clickedView.getContext())
                        .startActivityForResult(intent, REQUEST_CODE_ADD_WO);
            }));
        }

        private void handleDelete(int adapterPosition, View clickedView) {
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition < 0 || adapterPosition >= items.size()) return;

            JSONObject item = items.get(adapterPosition);
            ensureRowSelected(adapterPosition);
            if (!canDelete(item)) {
                Toast.makeText(ListWorkOrderActivity.this,
                        i18n("You do not have permission to delete this Work Order or it is already Done"),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String woCodeRaw = safeGet(item, "WO_CODE");
            String woCodeAlt = safeGet(item, "Wo_Code");
            final String woCode = !woCodeRaw.isEmpty() ? woCodeRaw : (!woCodeAlt.isEmpty() ? woCodeAlt : "N/A");

            checkDeletePermissionAsync(item, allowed -> runOnUiThread(() -> {
                if (!allowed) {
                    Toast.makeText(ListWorkOrderActivity.this,
                            i18n("You do not have permission to delete this Work Order or it is already Done"),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                String confirmMsg = String.format(
                        Locale.getDefault(),
                        i18n("Do you confirm deleting WorkOrder %s?"),
                        woCode
                );

                new AlertDialog.Builder(clickedView.getContext())
                        .setTitle(i18n("Delete Work Order"))
                        .setMessage(confirmMsg)
                        .setPositiveButton(i18n("Agree"), (dialog, which) -> deleteWorkOrder(item))
                        .setNegativeButton(i18n("No"), null)
                        .show();
            }));
        }

        class RowVH extends RecyclerView.ViewHolder {
            LinearLayout layoutForeground;
            LinearLayout actionContainer;
            HorizontalScrollView rowScrollView;
            TextView[] fixedCells;
            LinearLayout rowContent;
            TextView[] cells;
            ImageView btnEdit, btnDelete;
            RowVH(View v) {
                super(v);
                layoutForeground = v.findViewById(R.id.layout_foreground);

                rowScrollView = v.findViewById(R.id.row_scroll_view);
                if (rowScrollView != null) {
                    rowScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                }
                fixedCells = new TextView[2];
                rowContent = v.findViewById(R.id.row_content_container);
                actionContainer = v.findViewById(R.id.action_container);

                btnEdit = v.findViewById(R.id.btn_edit);
                btnDelete = v.findViewById(R.id.btn_delete);

                for (int i = 0; i < fixedCells.length; i++) {
                    TextView tv = new TextView(v.getContext());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(COLUMN_WIDTHS[i]), ViewGroup.LayoutParams.MATCH_PARENT);
                    lp.setMargins(1, 1, 1, 1);
                    tv.setLayoutParams(lp);
                    tv.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
                    tv.setMinHeight(dpToPx(TABLE_CELL_MIN_HEIGHT_DP));
                    tv.setIncludeFontPadding(false);
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextSize(11);
                    tv.setTextColor(Color.BLACK);
                    tv.setBackgroundResource(R.drawable.bg_table_sticky_cell);
                    rowContent.getParent();
                    ((LinearLayout) v.findViewById(R.id.fixed_columns_container)).addView(tv);
                    fixedCells[i] = tv;
                }

                cells = new TextView[COLUMN_WIDTHS.length - 2];

                for (int i = 2; i < COLUMN_WIDTHS.length; i++) {
                    TextView tv = new TextView(v.getContext());

                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(dpToPx(COLUMN_WIDTHS[i]),
                                    ViewGroup.LayoutParams.MATCH_PARENT);

                    lp.setMargins(1,1,1,1);
                    tv.setLayoutParams(lp);
                    tv.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
                    tv.setMinHeight(dpToPx(TABLE_CELL_MIN_HEIGHT_DP));
                    tv.setIncludeFontPadding(false);
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextSize(11);
                    tv.setTextColor(Color.BLACK);

                    rowContent.addView(tv);
                    cells[i - 2] = tv;
                }
            }
        }

        class CardVH extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            TextView tvNo;
            TextView tvWoCode;
            TextView tvMachineInline;
            TextView tvWorkTypeInline;
            TextView tvRequestDate;
            TextView tvRequestTime;
            TextView tvWoStatus;
            TextView tvMachine;
            TextView tvProcess;
            TextView tvWorkType;
            TextView tvCreator;
            TextView tvRequester;
            TextView tvElapsedDays;
            TextView tvDeadline;
            TextView tvContent;
            TextView tvMaStatus;
            ImageView btnEdit;
            ImageView btnDelete;

            CardVH(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.card_root);
                tvNo = v.findViewById(R.id.tv_card_no);
                tvWoCode = v.findViewById(R.id.tv_card_wo_code);
                tvMachineInline = v.findViewById(R.id.tv_card_machine_inline);
                tvWorkTypeInline = v.findViewById(R.id.tv_card_work_type_inline);
                tvRequestDate = v.findViewById(R.id.tv_card_request_date);
                tvRequestTime = v.findViewById(R.id.tv_card_request_time);
                tvWoStatus = v.findViewById(R.id.tv_card_wo_status);
                tvMachine = v.findViewById(R.id.tv_card_machine);
                tvProcess = v.findViewById(R.id.tv_card_process);
                tvWorkType = v.findViewById(R.id.tv_card_work_type);
                tvCreator = v.findViewById(R.id.tv_card_creator);
                tvRequester = v.findViewById(R.id.tv_card_requester);
                tvElapsedDays = v.findViewById(R.id.tv_card_elapsed_days);
                tvDeadline = v.findViewById(R.id.tv_card_deadline);
                tvContent = v.findViewById(R.id.tv_card_content);
                tvMaStatus = v.findViewById(R.id.tv_card_ma_status);
                btnEdit = v.findViewById(R.id.btn_card_edit);
                btnDelete = v.findViewById(R.id.btn_card_delete);
            }
        }

        class RowDisplayData {
            String number = "";
            String woCode = "";
            String requestDate = "";
            String requestDateOnly = "";
            String requestTimeOnly = "";
            String machine = "";
            String process = "";
            String workType = "";
            String creator = "";
            String requester = "";
            String elapsedDays = "";
            String deadline = "";
            String content = "";
            String woStatus = "";
            String maStatus = "";
            String typeCode = "CHUA_THUC_HIEN";
        }
    }

    private void syncTableScroll(int scrollX, @Nullable WorkOrderAdapter.RowVH sourceHolder) {
        if (isTableScrollSyncing) return;
        isTableScrollSyncing = true;
        try {
            if (hsvTableContainer != null) {
                int headerScrollX = clampScrollX(hsvTableContainer, scrollX);
                if (hsvTableContainer.getScrollX() != headerScrollX) {
                    hsvTableContainer.scrollTo(headerScrollX, 0);
                }
                currentTableScrollX = headerScrollX;
            }
            if (rvWorkOrder != null) {
                for (int i = 0; i < rvWorkOrder.getChildCount(); i++) {
                    View child = rvWorkOrder.getChildAt(i);
                    RecyclerView.ViewHolder holder = rvWorkOrder.getChildViewHolder(child);
                    if (holder instanceof WorkOrderAdapter.RowVH) {
                        WorkOrderAdapter.RowVH rowVH = (WorkOrderAdapter.RowVH) holder;
                        if (rowVH != sourceHolder && rowVH.rowScrollView != null) {
                            int rowScrollX = clampScrollX(rowVH.rowScrollView, currentTableScrollX);
                            if (rowVH.rowScrollView.getScrollX() != rowScrollX) {
                                rowVH.rowScrollView.scrollTo(rowScrollX, 0);
                            }
                        }
                    }
                }
            }
        } finally {
            isTableScrollSyncing = false;
        }
    }

    private int clampScrollX(@Nullable HorizontalScrollView scrollView, int desiredScrollX) {
        if (scrollView == null) return Math.max(0, desiredScrollX);
        View content = scrollView.getChildAt(0);
        if (content == null) return Math.max(0, desiredScrollX);
        int maxScroll = Math.max(0, content.getWidth() - scrollView.getWidth());
        return Math.max(0, Math.min(desiredScrollX, maxScroll));
    }

    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    private void resetFilters() {
        currentQuickFilterDays = -1;
        updateQuickFilterUi();

        initSelectedStatuses();

        if (tvStartDate != null) tvStartDate.setText(i18n("From"));
        if (tvEndDate != null) tvEndDate.setText(i18n("To"));

        currentDSACondition = "1=1";
        loadData(currentDSACondition);
    }

    private void updateQuickFilterUi() {
        applyFilterChipStyle(btnFilterDay, currentQuickFilterDays == 0);
        applyFilterChipStyle(btnFilterWeek, currentQuickFilterDays == 8);
        applyFilterChipStyle(btnFilterMonth, currentQuickFilterDays == 31);
    }

    private void applyFilterChipStyle(TextView tv, boolean selected) {
        if (tv == null) return;
        tv.setBackgroundResource(selected ? R.drawable.bg_filter_segment_selected : R.drawable.bg_filter_segment_unselected);
        tv.setTextColor(selected ? Color.WHITE : Color.parseColor("#374151"));
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return "";
        try {
            Date date = parseRequestDate(raw);
            if (date == null) return raw;
            SimpleDateFormat uiFormat = new SimpleDateFormat("dd/MM/yyyy\nHH:mm", Locale.getDefault());
            uiFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
            return uiFormat.format(date);
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatDatePart(String raw) {
        Date date = parseRequestDate(raw);
        if (date == null) return "";
        SimpleDateFormat uiFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        uiFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        return uiFormat.format(date);
    }

    private String formatTimePart(String raw) {
        Date date = parseRequestDate(raw);
        if (date == null) return "";
        SimpleDateFormat uiFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        uiFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        return uiFormat.format(date);
    }

    private Date parseRequestDate(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return null;
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault());
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return inputFormat.parse(raw);
        } catch (Exception ignored) {
            try {
                String clean = raw.replace("T", " ").replace("Z", "");
                if (clean.contains(".")) clean = clean.substring(0, clean.indexOf("."));
                SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                dbFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                return dbFormat.parse(clean);
            } catch (Exception ignored2) {
                try {
                    SimpleDateFormat legacyFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    legacyFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return legacyFormat.parse(raw.replace("Z", ""));
                } catch (Exception ignored3) {
                    return null;
                }
            }
        }
    }

    private String formatWoType(int type) {
        switch (type) {
            case 3: return "BM";
            case 1: return "CM";
            case 2: return "PM";
            default: return "BM";
        }
    }

    private String formatStatus(int status1){
        switch (status1){
            case 1: return i18n("Machine Breakdown");
            case 2: return i18n("Preparing operation");
            case 3: return i18n("Stop due to shortage");
            case 4: return i18n("Stop by production plan");
            case 5: return i18n("Maintenance and repair");
            default: return "";
        }
    }

    private String resolveMaStatusDisplay(JSONObject data) {
        int statusCode = resolveMaStatusCode(data);
        if (statusCode > 0) {
            return formatStatus(statusCode);
        }

        String rawText = safeGet(data, "MA_STATUS");
        if (rawText.isEmpty()) rawText = safeGet(data, "Ma_Status");
        if (rawText.isEmpty()) rawText = safeGet(data, "STATUS_TEXT");
        if (rawText.isEmpty()) rawText = safeGet(data, "Status_Text");

        int mappedCode = mapMaStatusTextToCode(rawText);
        return mappedCode > 0 ? formatStatus(mappedCode) : rawText;
    }

    private int resolveMaStatusCode(JSONObject data) {
        int status = data.optInt("STATUS", 0);
        if (status == 0) status = data.optInt("Status", 0);
        if (status == 0) status = data.optInt("STATUS_1", 0);

        if (status == 0) {
            String statusText = safeGet(data, "STATUS");
            if (statusText.isEmpty()) statusText = safeGet(data, "Status");
            if (statusText.isEmpty()) statusText = safeGet(data, "STATUS_1");
            if (!statusText.isEmpty()) {
                try {
                    status = Integer.parseInt(statusText.trim());
                } catch (Exception ignored) {
                    status = mapMaStatusTextToCode(statusText);
                }
            }
        }

        return status;
    }

    private int mapMaStatusTextToCode(String value) {
        String normalizedInput = normalizeStatusText(value);
        if (normalizedInput.isEmpty()) return 0;

        String[] canonical = new String[]{
            "Machine Breakdown",
                "Preparing operation",
                "Stop due to shortage",
                "Stop by production plan",
                "Maintenance and repair"
        };

        String[][] aliases = new String[][]{
            {"Machine Breakdown", "Machine broken", "Máy hỏng", "設備故障", "设备故障"},
                {"Preparing operation", "Chuẩn bị thao tác", "作業準備", "准备作业"},
                {"Stop due to shortage", "Dừng thiếu tồn", "不足による停止", "缺料停机"},
                {"Stop by production plan", "Dừng theo kế hoạch sản xuất", "生産計画による停止", "按生产计划停机"},
                {"Maintenance and repair", "Bảo dưỡng, sửa chữa", "保全・修理", "保养与维修"}
        };

        for (int i = 0; i < canonical.length; i++) {
            if (normalizedInput.equals(normalizeStatusText(canonical[i]))
                    || normalizedInput.equals(normalizeStatusText(i18n(canonical[i])))) {
                return i + 1;
            }

            for (String alias : aliases[i]) {
                if (normalizedInput.equals(normalizeStatusText(alias))) {
                    return i + 1;
                }
            }
        }

        return 0;
    }

    private String normalizeStatusText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
    }

    private String formatWoStatusUI(String typeCode){
        switch (typeCode){
            case "DA_THUC_HIEN": return i18n("Completed");
            case "QUA_HAN": return i18n("Overdue");
            case "CHUA_THUC_HIEN": return i18n("Incomplete");
            default: return "";
        }
    }

    private String safeGet(JSONObject json, String key) {
        if (json == null || key == null) return "";
        try {
            if (json.has(key)) {
                String value = json.optString(key, "");
                return "null".equalsIgnoreCase(value) ? "" : value;
            }
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.equalsIgnoreCase(key)) {
                    String val = json.optString(k, "");
                    return "null".equalsIgnoreCase(val) ? "" : val;
                }
            }
        } catch (Exception e) { Log.e(TAG, "safeGet error: " + e.getMessage()); }
        return "";
    }

    private void loadCurrentUserInfo() {
        try {
            PreferenceHandler handler = new PreferenceHandler(this);
            JSONObject userObj = handler.getJsonObject("user");
            if (userObj == null) return;

            currentUserId = userObj.optString("userId", "").trim();
            currentDivisionName = userObj.optString("divisionName", "").trim();
            currentDepartmentCode = userObj.optString("departmentCode", "").trim();
            if (currentDepartmentCode.isEmpty()) currentDepartmentCode = userObj.optString("Department_Code", "").trim();
            if (currentDepartmentCode.isEmpty()) currentDepartmentCode = userObj.optString("divisionCode", "").trim();
            if (currentDepartmentCode.isEmpty()) currentDepartmentCode = currentDivisionName;
        } catch (Exception e) {
            Log.e(TAG, "loadCurrentUserInfo error", e);
        }
    }

    private interface PermissionCallback {
        void onResult(boolean allowed);
    }

    private void checkEditPermissionAsync(JSONObject item, PermissionCallback callback) {
        new Thread(() -> callback.onResult(canEdit(item))).start();
    }

    private void checkDeletePermissionAsync(JSONObject item, PermissionCallback callback) {
        new Thread(() -> callback.onResult(canDelete(item))).start();
    }

    private boolean canEdit(JSONObject item) {
        if (item == null) return false;
        if (isDoneStatus(item)) return false;

        if (isCurrentUserFE()) return true;

        String creatorId = getCreatorUserId(item);
        if (isCurrentUserMA()) return isSameUser(creatorId, currentUserId);

        return false;
    }

    private boolean canDelete(JSONObject item) {
        if (item == null) return false;
        if (isDoneStatus(item)) return false;

        if (isCurrentUserFE()) return true;

        String creatorId = getCreatorUserId(item);
        if (isCurrentUserMA()) return isSameUser(creatorId, currentUserId);

        return false;
    }

    private boolean isCurrentUserFE() {
        return "FE".equalsIgnoreCase(normalizeDepartmentCode(currentDepartmentCode));
    }

    private boolean isCurrentUserMA() {
        return "MA".equalsIgnoreCase(normalizeDepartmentCode(currentDepartmentCode));
    }

    private String normalizeDepartmentCode(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if ("FE".equalsIgnoreCase(value) || "MA".equalsIgnoreCase(value)) return value.toUpperCase(Locale.ROOT);

        String[] parts = value.split("-");
        if (parts.length > 0) {
            String first = parts[0].trim();
            if ("FE".equalsIgnoreCase(first) || "MA".equalsIgnoreCase(first)) {
                return first.toUpperCase(Locale.ROOT);
            }
        }

        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("FE")) return "FE";
        if (upper.contains("MA")) return "MA";
        return upper;
    }

    private boolean isDoneStatus(JSONObject item) {
        return "DA_THUC_HIEN".equals(getStatusType(item));
    }

    private String getCreatorUserId(JSONObject item) {
        String creator = safeGet(item, "CREATE_BY");
        if (creator.isEmpty()) creator = safeGet(item, "Create_By");
        if (creator.isEmpty()) creator = safeGet(item, "REQUEST_USER");
        if (creator.isEmpty()) creator = safeGet(item, "Request_User");
        return creator.trim();
    }

    private String getCreatorDivision(JSONObject item, String creatorId) {
        String division = safeGet(item, "CREATE_DIVISION_NAME");
        if (division.isEmpty()) division = safeGet(item, "Create_Division_Name");
        if (division.isEmpty()) division = safeGet(item, "DIVISION_NAME");
        if (division.isEmpty()) division = safeGet(item, "Division_Name");
        if (!division.isEmpty()) return division.trim();

        if (creatorId.isEmpty()) return "";

        try {
            HttpClient.APIReturn res = HttpClient.getUserInfo(this, serverDynamic, creatorId, schemaCore);
            if (res != null && res.code == 200 && res.data != null && !res.data.isEmpty()) {
                JSONObject user = res.data.get(0);
                division = safeGet(user, "Division_Name");
                if (division.isEmpty()) division = safeGet(user, "divisionName");
                if (division.isEmpty()) division = safeGet(user, "Department_Code");
                return division.trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "getCreatorDivision error", e);
        }
        return "";
    }

    private boolean isSameUser(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private void deleteWorkOrder(JSONObject item) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(i18n("Deleting Work Order..."));
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            try {
                String woCode = safeGet(item, "WO_CODE");
                if (woCode.isEmpty()) woCode = safeGet(item, "Wo_Code");

                String requestUser = safeGet(item, "REQUEST_USER");
                if (requestUser.isEmpty()) requestUser = safeGet(item, "Request_User");
                if (requestUser.isEmpty()) requestUser = currentUserId;

                JSONObject condition = new JSONObject();
                condition.put("Schema_MMS", schemaData);
                condition.put("Schema_Core", schemaCore);
                condition.put("WO_CODE", woCode);
                condition.put("ISSUE_ID", woCode);
                condition.put("REQUEST_USER", requestUser);

                ColorConsole.d(TAG, "Delete Condition: " + condition.toString());
                HttpClient.APIReturn res = HttpClient.deleteMtWorkOrder(this, serverDynamic, condition);

                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (res != null && res.code == 200) {
                        Toast.makeText(this, i18n("Delete successful"), Toast.LENGTH_SHORT).show();
                        loadData(currentDSACondition);
                    } else {
                        String msg = (res != null) ? res.message : i18n("No response from server");
                        Toast.makeText(this, String.format(Locale.getDefault(), i18n("Delete failed: %s"), msg), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, String.format(Locale.getDefault(), i18n("Delete error: %s"), e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showLogoutConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(i18n("Logout"))
                .setMessage(i18n("Do you want to log out?"))
                .setPositiveButton(i18n("Logout"), (dialog, which) -> logout())
                .setNegativeButton(i18n("No"), null).show();
    }

    private void logout(){

        PreferenceHandler handler = new PreferenceHandler(this);
        handler.clear();

        HttpClient.clearToken();

        if(countDownTimer != null) countDownTimer.cancel();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String getPassedDate(String requestDateStr){
        if(requestDateStr == null || requestDateStr.isEmpty() || requestDateStr.equals("null")) return "0";
        try{
            String clean = requestDateStr.replace("T", " ").replace("Z", "");
            if (clean.contains(".")) clean = clean.substring(0, clean.indexOf("."));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
//            Date requestDate = sdf.parse(requestDateStr.replace("Z", ""));
            Date requestDate = sdf.parse(requestDateStr);

            long diffInMillis = new Date().getTime() - requestDate.getTime();
            return String.valueOf(Math.max(0, diffInMillis / (24 * 60 * 60 * 1000)));
        }catch (Exception e){ return "0"; }
    }

    private void displayUserInfo() {
        try {
            PreferenceHandler handler = new PreferenceHandler(this);
            JSONObject userObj = handler.getJsonObject("user");
            if (userObj == null || userObj.length() == 0) return;
            String fullName = userObj.optString("fullName", "User");
            String userId = userObj.optString("userId", "");
            if (tvUsernameDisplay != null) tvUsernameDisplay.setText(fullName + " (" + userId + ")");
            if ("admin".equalsIgnoreCase(userId)) tvUsernameDisplay.setTextColor(Color.RED);
        } catch (Exception e) { Log.e(TAG, "Error displaying user info", e); }
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


    private final ActivityResultLauncher<Intent> unknownSourcesLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        this.getPackageManager().canRequestPackageInstalls()) {
                    startDownload();
                } else {
                    Toast.makeText(this, i18n("Unknown sources permission denied"), Toast.LENGTH_SHORT).show();
                }
            });

    private void startDownload() {

        downloader.startDownload();

    }

    private boolean isApkFile() {
        return MIME_TYPE.equals("application/vnd.android.package-archive") || FILE_NAME.endsWith(".apk");
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadData(currentDSACondition);
        startReloadTimer();
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}
