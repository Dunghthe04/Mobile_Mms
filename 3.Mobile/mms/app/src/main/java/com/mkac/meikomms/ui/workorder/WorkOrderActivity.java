package com.mkac.meikomms.ui.workorder;

import static android.text.method.TextKeyListener.clear;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;
import static java.util.Collections.addAll;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import android.view.Menu;

import androidx.appcompat.app.AppCompatActivity;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.ColorConsole;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.JsonConverter;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;
import com.mkac.meikomms.common.ProgressRequestBody;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.*;

public class WorkOrderActivity extends AppCompatActivity {
    private static final String TAG = "WorkOrderActivity";
    private static final List<String> MA_STATUS_KEYS = Arrays.asList(
            "Machine Breakdown",
            "Preparing operation",
            "Stop due to shortage",
            "Stop by production plan",
            "Maintenance and repair"
    );
        private static final String[][] MA_STATUS_ALIASES = new String[][]{
            {"Machine Breakdown", "Machine broken", "Máy hỏng", "設備故障", "设备故障"},
            {"Preparing operation", "Chuẩn bị thao tác", "作業準備", "准备作业"},
            {"Stop due to shortage", "Dừng thiếu tồn", "不足による停止", "缺料停机"},
            {"Stop by production plan", "Dừng theo kế hoạch sản xuất", "生産計画による停止", "按生产计划停机"},
            {"Maintenance and repair", "Bảo dưỡng, sửa chữa", "保全・修理", "保养与维修"}
        };
        
    private EditText edtWoCode, edtRequestDate, edtProcess, edtPassedDate, edtReason, edtDeadline;
    private AutoCompleteTextView autoMachine, autoLoaiHinh, autoRequester, autoMaStatus;
    private ImageView btnClose;
    private View btnAdd;
    private TextView tvWorkOrderTitle, tvLabelWoCode, tvLabelRequestDate, tvLabelMachine, tvLabelProcess,
            tvLabelType, tvLabelRequester, tvLabelPassedDate, tvLabelDeadline, tvLabelRequestReason, tvLabelMaStatus, tvLabelMesLock;
    private RadioGroup radioGroupMesLock;
    private RadioButton radioMesLockYes, radioMesLockNo;
    private View layoutMesLockContent;
    private String serverUrl, schemaMms, schemaCore, schemaData, schemaWms;
    private List<JSONObject> machineDataList = new ArrayList<>();
    private Map<String, String> userIdMap = new HashMap<>();
    private String selectedProcessId = "";
    private String createByRealId = "";
    private boolean isSubmitting = false;
    private String loginUserName = "";
    private boolean isEditMode = false;
    private String editingWoCode = "";
    private JSONObject editingData = null;
    private int currentFeStatus = 0;

    // Bộ phận người đăng nhập + các container ẩn/hiện theo bộ phận (giống web)
    private boolean isFeUser = false;
    private LinearLayout layoutRequestDate, layoutMaStatus, layoutMesLock;

    // ===== Đính kèm ảnh / file =====
    private static final int REQUEST_PICK_FILE = 9999;
    // File người dùng mới chọn nhưng chưa tải lên server
    private final List<Uri> selectedFileUris = new ArrayList<>();
    // File đã tải lên server (tên file đầy đủ, lấy từ FILE_WO / kết quả upload)
    private final List<String> uploadedFileNames = new ArrayList<>();
    private LinearLayout layoutAttachmentEmpty;
    private LinearLayout layoutAttachmentList;

    // Màn Sửa WO: 2 khối tài liệu MA/FE (giống web)
    private View layoutAttachmentSingle;
    private TextView tvLabelAttachment;
    private LinearLayout layoutDocMaBlock, layoutDocMaList, layoutDocFeBlock, layoutDocFeList;
    private final List<String> docMaFiles = new ArrayList<>();
    private final List<String> docFeFiles = new ArrayList<>();

    // Phase 3/4 (FE): tab Vật tư + Assignee + hành vi Loại WO = Other
    private android.widget.ViewFlipper viewFlipper;
    private View layoutWoTabs, btnTabInfo, btnTabMaterial, layoutMachine, layoutAssignee, btnAddMaterial;
    private AutoCompleteTextView autoAssignee;
    private LinearLayout layoutMaterialRows;
    private final List<String> materialLabels = new ArrayList<>();          // "Id - Name"
    private final Map<String, String> materialLabelToId = new HashMap<>();  // label -> Item_Id
    private final Map<String, Double> materialLabelToStock = new HashMap<>(); // label -> tồn kho
    private final List<MaterialRow> materialRows = new ArrayList<>();

    private static class MaterialRow {
        AutoCompleteTextView material;
        EditText stock;
        EditText qty;
        View row;
    }

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_work_order);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        String passedLanguageCode = getIntent().getStringExtra("LANGUAGE_CODE");
        if (passedLanguageCode != null && !passedLanguageCode.trim().isEmpty()) {
            String normalizedCode = passedLanguageCode.trim();
            int languagePosition = 2;
            if ("ja".equalsIgnoreCase(normalizedCode)) languagePosition = 0;
            else if ("en".equalsIgnoreCase(normalizedCode)) languagePosition = 1;
            else if ("ch".equalsIgnoreCase(normalizedCode)) languagePosition = 3;

            SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
            prefs.edit().putInt("languageSettingPosition", languagePosition).commit();
            LanguageAPIUtils.setLanguageCode(normalizedCode);
        }

        initConfiguration();
        // Nhận diện FE: prefs trực tiếp HOẶC cờ do hub lưu (chỉ dùng làm tín hiệu FE bổ sung,
        // không hạ FE->MA để tránh hồi quy khi API hub đoán sai).
        String savedDivision = new PreferenceHandler(this).getString("wo_user_division");
        isFeUser = isCurrentUserFe() || "FE".equalsIgnoreCase(savedDivision);
        initViews();
        applyI18nFieldTexts();

        String dataStr = getIntent().getStringExtra("DATA");
        if (dataStr != null) {
            try {
                JSONObject data = new JSONObject(dataStr);
                editingData = data;
                isEditMode = true;



                // Re-apply i18n after edit mode is known so title/button texts use Update variants.
                applyI18nFieldTexts();

                bindDataToUI(data);
                setupEditModeUI();

                // FE: hiển thị đúng Loại WO đã lưu.
                if (isFeUser && autoLoaiHinh != null) {
                    int wt = editingData.optInt("WO_TYPE", editingData.optInt("Wo_Type", 3));
                    autoLoaiHinh.setText(woTypeNumberToText(wt), false);
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    bindDataToUI(data);
                }, 300);

                ((TextView) btnAdd).setText(i18n("Save"));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!isEditMode) {
            loadInitialData();
        }

        // Áp quy tắc ẩn/hiện field theo bộ phận + Add/Edit — gọi 1 lần duy nhất sau khi biết isEditMode
        // (để removeView ẩn field FE thu gọn hẳn, không để lại ô trống trong GridLayout).
        applyDivisionFormRules();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply localized labels and dynamic field values after language changes.
        applyI18nFieldTexts();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Language can be switched while staying on this screen; refresh dynamic texts on focus return.
        if (hasFocus) {
            applyI18nFieldTexts();
        }
    }

    private void initConfiguration() {
        ConfigManager configManager = new ConfigManager(this);
        String rawUrl = configManager.getProperty("server_dynamic_url");

        if (rawUrl != null && rawUrl.contains("/api/dynamics")) {
            serverUrl = rawUrl.substring(0, rawUrl.indexOf("/api/dynamics"));
        } else {
            serverUrl = rawUrl;
        }

        schemaMms = configManager.getProperty("schema_mms");
        schemaCore = configManager.getProperty("schema_core");
        schemaData = configManager.getProperty("schema_data");
        schemaWms = configManager.getProperty("schema_wms");

        if (schemaWms == null) schemaWms = "MES_WMS_MKHC";
    }

    private void initViews() {
        edtWoCode = findViewById(R.id.edt_wo_code);
        edtRequestDate = findViewById(R.id.edt_request_date);
        edtProcess = findViewById(R.id.edt_process);
        edtPassedDate = findViewById(R.id.edt_passed_date);
        edtDeadline = findViewById(R.id.edt_deadline);
        edtReason = findViewById(R.id.edt_reason);
        autoMachine = findViewById(R.id.auto_machine);
        autoLoaiHinh = findViewById(R.id.auto_loai_hinh);
        autoRequester = findViewById(R.id.auto_requester);
        autoMaStatus = findViewById(R.id.auto_ma_status);
        btnClose = findViewById(R.id.btn_close_activity);
        btnAdd = findViewById(R.id.btn_add);
        tvWorkOrderTitle = findViewById(R.id.tv_work_order_title);
        tvLabelWoCode = findViewById(R.id.tv_label_wo_code);

        tvLabelRequestDate = findViewById(R.id.tv_label_request_date);
        tvLabelMachine = findViewById(R.id.tv_label_machine);
        tvLabelProcess = findViewById(R.id.tv_label_process);
        tvLabelType = findViewById(R.id.tv_label_type);
        tvLabelRequester = findViewById(R.id.tv_label_requester);
        tvLabelPassedDate = findViewById(R.id.tv_label_passed_date);
//        tvLabelDeadline = findViewById(R.id.tv_label_deadline);
        tvLabelRequestReason = findViewById(R.id.tv_label_request_reason);
        tvLabelMaStatus = findViewById(R.id.tv_label_ma_status);
        tvLabelMesLock = findViewById(R.id.tv_label_mes_lock);
        radioGroupMesLock = findViewById(R.id.radio_group_mes_lock);
        radioMesLockYes = findViewById(R.id.radio_mes_lock_yes);
        radioMesLockNo = findViewById(R.id.radio_mes_lock_no);

        layoutAttachmentEmpty = findViewById(R.id.layout_attachment_empty);
        layoutAttachmentList = findViewById(R.id.layout_attachment_list);
        layoutRequestDate = findViewById(R.id.layout_request_date);
        layoutMaStatus = findViewById(R.id.layout_ma_status);
        layoutMesLock = findViewById(R.id.layout_mes_lock);
        layoutAttachmentSingle = findViewById(R.id.layout_attachment_single);
        tvLabelAttachment = findViewById(R.id.tv_label_attachment);
        layoutDocMaBlock = findViewById(R.id.layout_doc_ma_block);
        layoutDocMaList = findViewById(R.id.layout_doc_ma_list);
        layoutDocFeBlock = findViewById(R.id.layout_doc_fe_block);
        layoutDocFeList = findViewById(R.id.layout_doc_fe_list);
        viewFlipper = findViewById(R.id.view_flipper);
        layoutWoTabs = findViewById(R.id.layout_wo_tabs);
        btnTabInfo = findViewById(R.id.btn_tab_info);
        btnTabMaterial = findViewById(R.id.btn_tab_material);
        layoutMachine = findViewById(R.id.layout_machine);
        layoutAssignee = findViewById(R.id.layout_assignee);
        autoAssignee = findViewById(R.id.auto_assignee);
        layoutMaterialRows = findViewById(R.id.layout_material_rows);
        btnAddMaterial = findViewById(R.id.btn_add_material);
        View btnChooseFile = findViewById(R.id.btn_choose_file);
        View btnUploadFile = findViewById(R.id.btn_upload_file);
        if (btnChooseFile != null) btnChooseFile.setOnClickListener(v -> openFilePicker());
        if (btnUploadFile != null) btnUploadFile.setOnClickListener(v -> uploadSelectedFiles());
        renderAttachmentList();

        applyI18nFieldTexts();

        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        btnAdd.setOnClickListener(v -> {
            if(isEditMode){
                performUpdateWorkOrder();
            }else{
                performAddWorkOrder();
            }
        });

        String currentDateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        edtRequestDate.setText(currentDateTime);
        updatePassedDays(currentDateTime);

        edtRequestDate.setOnClickListener(v -> showDateTimePicker(edtRequestDate));
        if (edtDeadline != null) {
            edtDeadline.setOnClickListener(v -> showDateTimePicker(edtDeadline));
        }
        edtPassedDate.setOnClickListener(v -> showDateTimePicker(edtPassedDate));

        autoMachine.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = (String) parent.getItemAtPosition(position);
            for (JSONObject machine : machineDataList) {
                String mId = safeGet(machine, "Machine_Id").trim();
                String mName = safeGet(machine, "Machine_Name").trim();
                if ((mId + " - " + mName).equals(selectedItem)) {
                    //String processStep = machine.optString("MAIN_PROCESS_STEP", machine.optString("Main_Process_Step"));
                    String processStep = safeGet(machine, "MAIN_PROCESS_STEP");
                    if (processStep.isEmpty()) {
                        processStep = safeGet(machine, "Main_Process_Step");
                    }
                    edtProcess.setText(processStep);
                    selectedProcessId = safeGet(machine, "Main_Process_Step_Code");
                    break;
                }
            }
        });

        setupDropdown(autoMaStatus, getLocalizedMaStatusOptions());
        applyReadOnlyFieldStyle(edtWoCode);

        // Loại WO: MA cố định BM; FE được chọn BM/PM/CM/Other (giống web).
        if (isFeUser) {
            setupDropdown(autoLoaiHinh, Arrays.asList("BM", "PM", "CM", "Other"));
            autoLoaiHinh.setText("BM", false);
            applyEditableFieldStyle(autoLoaiHinh);
            setupFeExtras();
        } else {
            autoLoaiHinh.setText("BM");
            applyReadOnlyFieldStyle(autoLoaiHinh);
        }
        // Lưu ý: applyDivisionFormRules() được gọi 1 lần ở cuối onCreate (sau khi biết Add/Edit),
        // không gọi ở đây để tránh gỡ nhầm view khi màn Sửa.
    }

    /**
     * Ẩn/hiện & enable field theo bộ phận (MA/FE) và chế độ Add/Edit — bám theo web getWoPopupHTML:
     *  - Thời gian phát sinh: MA luôn hiện; FE chỉ hiện khi Edit (và bị disable).
     *  - Trạng thái MA báo + "Có lock thiết bị": hiện nếu MA hoặc (FE và Edit); FE thì disable.
     */
    private void applyDivisionFormRules() {
        // Giống web: 3 field này hiện nếu MA, hoặc (FE và đang Sửa). => FE + Thêm: ẩn hẳn.
        boolean showRequestDate = !isFeUser || isEditMode;
        boolean showMaBlock = !isFeUser || isEditMode;

        setBlockVisible(layoutRequestDate, showRequestDate);
        // FE (kể cả Edit) không được sửa thời gian phát sinh.
        if (isFeUser && edtRequestDate != null) {
            applyReadOnlyFieldStyle(edtRequestDate);
            edtRequestDate.setOnClickListener(null);
        }

        setBlockVisible(layoutMaStatus, showMaBlock);
        setBlockVisible(layoutMesLock, showMaBlock);
        // Trạng thái MA báo + MES lock chỉ MA mới thao tác được.
        if (isFeUser) {
            if (autoMaStatus != null) applyReadOnlyFieldStyle(autoMaStatus);
            if (radioMesLockYes != null) { radioMesLockYes.setEnabled(false); radioMesLockYes.setClickable(false); }
            if (radioMesLockNo != null) { radioMesLockNo.setEnabled(false); radioMesLockNo.setClickable(false); }
        }

        applyAttachmentMode();
    }

    /**
     * Ẩn/hiện 1 block trong GridLayout. GridLayout không thu gọn view GONE nên set GONE
     * cho cả container LẪN các view con để đảm bảo không hiển thị (dù có thể còn ô trống).
     */
    private void setBlockVisible(LinearLayout container, boolean visible) {
        if (container == null) return;
        if (visible) {
            container.setVisibility(View.VISIBLE);
        } else {
            // GridLayout không thu gọn view GONE (để lại ô trống) -> gỡ hẳn khỏi layout.
            // applyDivisionFormRules chỉ chạy 1 lần (sau khi biết Add/Edit) nên không cần thêm lại.
            if (container.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) container.getParent()).removeView(container);
            }
        }
    }

    // =========================================================================
    // FE: tab Vật tư + Assignee + hành vi Loại WO = Other (giống web)
    // =========================================================================
    private void setupFeExtras() {
        if (layoutWoTabs != null) layoutWoTabs.setVisibility(View.VISIBLE);
        if (btnTabInfo != null) btnTabInfo.setOnClickListener(v -> selectWoTab(true));
        if (btnTabMaterial != null) btnTabMaterial.setOnClickListener(v -> selectWoTab(false));
        if (btnAddMaterial != null) btnAddMaterial.setOnClickListener(v -> addMaterialRow(null, "0"));

        // Đổi UI khi Loại WO thay đổi (Other <-> BM/PM/CM).
        if (autoLoaiHinh != null) {
            autoLoaiHinh.setOnItemClickListener((p, vv, pos, id) -> applyWoTypeUi());
            autoLoaiHinh.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) { applyWoTypeUi(); }
            });
        }

        loadMaterialList();
        addMaterialRow(null, "0"); // dòng vật tư đầu tiên
        applyWoTypeUi();
    }

    private void selectWoTab(boolean info) {
        if (viewFlipper != null) viewFlipper.setDisplayedChild(info ? 0 : 1);
        if (btnTabInfo instanceof TextView) {
            btnTabInfo.setBackgroundResource(info ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
            ((TextView) btnTabInfo).setTextColor(info ? Color.WHITE : Color.BLACK);
        }
        if (btnTabMaterial instanceof TextView) {
            btnTabMaterial.setBackgroundResource(!info ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
            ((TextView) btnTabMaterial).setTextColor(!info ? Color.WHITE : Color.BLACK);
        }
    }

    /** Loại WO = Other: ẩn Máy, hiện Assignee, ẩn tab Vật tư (chuyển về tab Info). */
    private void applyWoTypeUi() {
        boolean isOther = "Other".equalsIgnoreCase(
                autoLoaiHinh != null ? autoLoaiHinh.getText().toString().trim() : "");
        if (layoutMachine != null) layoutMachine.setVisibility(isOther ? View.GONE : View.VISIBLE);
        if (layoutAssignee != null) layoutAssignee.setVisibility(isOther ? View.VISIBLE : View.GONE);
        if (btnTabMaterial != null) btnTabMaterial.setVisibility(isOther ? View.GONE : View.VISIBLE);
        if (isOther) selectWoTab(true);
    }

    private void loadMaterialList() {
        final String serverDynamic = getServerDynamicUrl();
        new Thread(() -> {
            try {
                // Giống hệt web getMaterialDataList(): Condition chỉ có Schema_WMS + where.
                JSONObject cond = new JSONObject();
                cond.put("Schema_WMS", schemaWms);
                cond.put("where", "1=1");
                HttpClient.APIReturn rs = HttpClient.callDynamics(
                        this, serverDynamic, "mes_mms", "MMS_GET_LIST_MATERIALS_225", cond);
                final List<String> labels = new ArrayList<>();
                final Map<String, String> map = new HashMap<>();
                final Map<String, Double> stockMap = new HashMap<>();
                if (rs != null && rs.code == 200 && rs.data != null) {
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (JSONObject m : rs.data) {
                        if (m == null) continue;
                        String id = m.optString("Item_Id").trim();
                        String name = m.optString("Item_Name").trim();
                        int deleted = m.optInt("Deleted", 0);
                        if (id.isEmpty() || seen.contains(id) || deleted != 0) continue;
                        seen.add(id);
                        String label = id + " - " + name;
                        labels.add(label);
                        map.put(label, id);
                        stockMap.put(label, m.optDouble("Item_Total_Quantity", 0));
                    }
                }
                runOnUiThread(() -> {
                    materialLabels.clear(); materialLabels.addAll(labels);
                    materialLabelToId.clear(); materialLabelToId.putAll(map);
                    materialLabelToStock.clear(); materialLabelToStock.putAll(stockMap);
                    for (MaterialRow r : materialRows) {
                        if (r.material != null) setupMaterialDropdown(r.material);
                    }
                });
            } catch (Exception e) {
                ColorConsole.e(TAG, "loadMaterialList error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gắn dropdown cho ô vật tư. Không dùng setupDropdownNew vì hàm đó set dropDownWidth theo
     * getWidth() (bằng 0 lúc section Vật tư đang GONE trong ViewFlipper) -> dropdown rộng 0px.
     */
    private void setupMaterialDropdown(AutoCompleteTextView view) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(materialLabels));
        view.setAdapter(adapter);
        view.setThreshold(0);
        view.setDropDownWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.6));
        view.setOnClickListener(v -> view.showDropDown());
        view.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) view.showDropDown();
            return false;
        });
        view.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) view.showDropDown(); });
    }

    private void addMaterialRow(String preLabel, String preQty) {
        if (layoutMaterialRows == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowLp);

        // Ô vật tư: RelativeLayout (nền + mũi tên) bọc AutoCompleteTextView -> giống dropdown các ô khác.
        RelativeLayout matWrap = new RelativeLayout(this);
        LinearLayout.LayoutParams matLp = new LinearLayout.LayoutParams(0, dp(44), 2.4f);
        matLp.setMarginEnd(dp(6));
        matWrap.setLayoutParams(matLp);
        matWrap.setBackgroundResource(R.drawable.bg_input_field);

        AutoCompleteTextView mat = new AutoCompleteTextView(this);
        RelativeLayout.LayoutParams matInnerLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        mat.setLayoutParams(matInnerLp);
        mat.setBackgroundColor(Color.TRANSPARENT);
        mat.setPadding(dp(10), 0, dp(28), 0);
        mat.setHint(i18n("Select an item"));
        mat.setTextSize(13);
        mat.setSingleLine(true);
        // Luôn gắn adapter + listener mở dropdown (kể cả khi list vật tư đang tải, sẽ refresh sau).
        setupMaterialDropdown(mat);
        if (preLabel != null && !preLabel.isEmpty()) mat.setText(preLabel, false);

        ImageView matArrow = new ImageView(this);
        RelativeLayout.LayoutParams arrowLp = new RelativeLayout.LayoutParams(dp(18), dp(18));
        arrowLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        arrowLp.addRule(RelativeLayout.CENTER_VERTICAL);
        arrowLp.setMarginEnd(dp(8));
        matArrow.setLayoutParams(arrowLp);
        matArrow.setImageResource(android.R.drawable.arrow_down_float);
        matArrow.setOnClickListener(v -> mat.showDropDown());
        matWrap.addView(mat);
        matWrap.addView(matArrow);

        // Tồn kho (chỉ đọc) — tự điền theo vật tư đã chọn.
        EditText stock = new EditText(this);
        LinearLayout.LayoutParams stockLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        stockLp.setMarginEnd(dp(6));
        stock.setLayoutParams(stockLp);
        stock.setBackgroundResource(R.drawable.bg_input_field_disabled);
        stock.setPadding(dp(10), 0, dp(10), 0);
        stock.setHint(i18n("Stock"));
        stock.setTextSize(13);
        stock.setEnabled(false);
        stock.setFocusable(false);
        stock.setText("0");

        EditText qty = new EditText(this);
        LinearLayout.LayoutParams qtyLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        qtyLp.setMarginEnd(dp(6));
        qty.setLayoutParams(qtyLp);
        qty.setBackgroundResource(R.drawable.bg_input_field);
        qty.setPadding(dp(10), 0, dp(10), 0);
        qty.setHint("0");
        qty.setTextSize(13);
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (preQty != null) qty.setText(preQty);

        // Khi chọn vật tư -> điền tồn kho tương ứng.
        mat.setOnItemClickListener((parent, vv, pos, id) -> {
            String lbl = mat.getText().toString().trim();
            Double s = materialLabelToStock.get(lbl);
            stock.setText(String.valueOf(s == null ? 0 : s.longValue()));
        });

        ImageView btnRemove = new ImageView(this);
        LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(dp(64), dp(28));
        btnRemove.setLayoutParams(rmLp);
        btnRemove.setImageResource(R.drawable.close_24);
        btnRemove.setScaleType(ImageView.ScaleType.CENTER);
        btnRemove.setPadding(dp(3), dp(3), dp(3), dp(3));

        final MaterialRow holder = new MaterialRow();
        holder.material = mat; holder.stock = stock; holder.qty = qty; holder.row = row;
        btnRemove.setOnClickListener(v -> {
            layoutMaterialRows.removeView(row);
            materialRows.remove(holder);
            if (materialRows.isEmpty()) addMaterialRow(null, "0"); // luôn còn ít nhất 1 dòng
        });

        row.addView(matWrap); row.addView(stock); row.addView(qty); row.addView(btnRemove);
        layoutMaterialRows.addView(row);
        materialRows.add(holder);
    }

    /** Thu thập vật tư -> JSON [{idx, materialId, quantity}] (giống web getMaterialsFromUI). */
    private String collectMaterialsJson() {
        org.json.JSONArray arr = new org.json.JSONArray();
        int idx = 0;
        for (MaterialRow r : materialRows) {
            String label = r.material.getText().toString().trim();
            String qtyStr = r.qty.getText().toString().trim();
            double q;
            try { q = qtyStr.isEmpty() ? 0 : Double.parseDouble(qtyStr); } catch (Exception e) { q = 0; }
            String id = materialLabelToId.get(label);
            if (id == null || id.isEmpty()) {
                id = label.contains(" - ") ? label.split(" - ")[0].trim() : label;
            }
            if ((id == null || id.isEmpty()) && q <= 0) continue; // bỏ dòng trống
            try {
                JSONObject o = new JSONObject();
                o.put("idx", idx++);
                o.put("materialId", id == null ? "" : id);
                o.put("quantity", q);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** Kiểm tra vật tư giống web: chọn mã + SL>0 + SL<=tồn kho. Trả về true nếu hợp lệ. */
    private boolean validateMaterials() {
        for (MaterialRow r : materialRows) {
            String label = r.material.getText().toString().trim();
            String qtyStr = r.qty.getText().toString().trim();
            double q;
            try { q = qtyStr.isEmpty() ? 0 : Double.parseDouble(qtyStr); } catch (Exception e) { q = 0; }
            boolean hasId = !label.isEmpty();

            // Dòng trống hoàn toàn -> bỏ qua (giống getMaterialsFromUI).
            if (!hasId && q <= 0) continue;

            // Nhập số lượng nhưng chưa chọn vật tư.
            if (!hasId) {
                Toast.makeText(this, i18n("Please select a material after entering quantity"), Toast.LENGTH_LONG).show();
                return false;
            }

            String id = materialLabelToId.get(label);
            if (id == null || id.isEmpty()) id = label.contains(" - ") ? label.split(" - ")[0].trim() : label;

            // Đã chọn vật tư nhưng số lượng <= 0.
            if (q <= 0) {
                Toast.makeText(this, i18n("Material") + " [" + id + "] " + i18n("must have quantity > 0"),
                        Toast.LENGTH_LONG).show();
                return false;
            }

            // Số lượng vượt tồn kho.
            Double mapStock = materialLabelToStock.get(label);
            double stock = (mapStock != null) ? mapStock : 0;
            if (q > stock) {
                Toast.makeText(this,
                        i18n("Requested quantity") + " (" + fmtNum(q) + ") "
                                + i18n("exceeds stock") + " (" + fmtNum(stock) + ")",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    /** Bỏ .0 thừa khi hiển thị số lượng/tồn kho. */
    private String fmtNum(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /**
     * Chế độ hiển thị đính kèm giống web:
     *  - Thêm WO: 1 khối duy nhất.
     *  - Sửa WO: 2 khối riêng "Tài liệu MA" (đọc FILE_WO_MA) + "Tài liệu FE" (đọc FILE_WO_FE).
     */
    private void applyAttachmentMode() {
        boolean dual = isEditMode;
        if (layoutAttachmentSingle != null) layoutAttachmentSingle.setVisibility(dual ? View.GONE : View.VISIBLE);
        if (tvLabelAttachment != null) tvLabelAttachment.setVisibility(dual ? View.GONE : View.VISIBLE);
        if (layoutDocMaBlock != null) layoutDocMaBlock.setVisibility(dual ? View.VISIBLE : View.GONE);
        if (layoutDocFeBlock != null) layoutDocFeBlock.setVisibility(dual ? View.VISIBLE : View.GONE);
        if (dual) loadEditModeAttachments();
    }

    /** Tải file 2 kho MA/FE cho màn Sửa WO rồi vẽ lại 2 khối. */
    private void loadEditModeAttachments() {
        final String woCode = edtWoCode != null ? edtWoCode.getText().toString().trim() : "";
        if (woCode.isEmpty() || "...".equals(woCode)) return;
        final String serverDynamicUrl = getServerDynamicUrl();
        new Thread(() -> {
            List<String> maList = extractFileValues(HttpClient.getWorkOrderFiles(this, serverDynamicUrl, woCode, "MA"));
            List<String> feList = extractFileValues(HttpClient.getWorkOrderFiles(this, serverDynamicUrl, woCode, "FE"));
            runOnUiThread(() -> {
                docMaFiles.clear();
                docMaFiles.addAll(maList);
                docFeFiles.clear();
                docFeFiles.addAll(feList);
                renderDocBlocks();
            });
        }).start();
    }

    private List<String> extractFileValues(HttpClient.APIReturn result) {
        List<String> files = new ArrayList<>();
        if (result == null || result.code != 200 || result.data == null) return files;
        for (JSONObject item : result.data) {
            if (item == null) continue;
            String v = item.optString("value");
            if (!v.isEmpty()) files.add(v);
        }
        return files;
    }

    /**
     * Vẽ 2 khối tài liệu. Theo web: khối MA xóa được (readonly=false);
     * khối FE chỉ xóa được khi người dùng là FE. File mới chọn (chưa upload) hiển thị ở khối
     * đúng bộ phận đang đăng nhập.
     */
    private void renderDocBlocks() {
        final String woCode = edtWoCode != null ? edtWoCode.getText().toString().trim() : "";
        renderDocBlock(layoutDocMaList, docMaFiles, "MA", true, !isFeUser, woCode);
        renderDocBlock(layoutDocFeList, docFeFiles, "FE", isFeUser, isFeUser, woCode);
    }

    /**
     * @param canDelete cho phép xóa file đã upload ở kho này
     * @param showPending khối này có hiển thị file mới chọn (chưa upload) của phiên hiện tại không
     */
    private void renderDocBlock(LinearLayout container, List<String> files, String scope,
                                boolean canDelete, boolean showPending, String woCode) {
        if (container == null) return;
        container.removeAllViews();

        boolean hasAny = !files.isEmpty() || (showPending && !selectedFileUris.isEmpty());
        if (!hasAny) {
            TextView empty = new TextView(this);
            empty.setText(i18n("No attachment file"));
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13);
            empty.setTypeface(empty.getTypeface(), android.graphics.Typeface.ITALIC);
            container.addView(empty);
            return;
        }

        for (final String fileName : new ArrayList<>(files)) {
            container.addView(buildDocRow(displayFileName(woCode, fileName), fileName, scope, canDelete));
        }
        if (showPending) {
            for (final Uri uri : new ArrayList<>(selectedFileUris)) {
                container.addView(buildPendingRow(getFileName(uri), uri));
            }
        }
    }

    /** 1 dòng file đã upload trong 1 kho (MA/FE): tên (tải về) + nút tải + nút xóa (nếu được phép). */
    private View buildDocRow(String displayName, final String fullName, final String scope, boolean canDelete) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconLp.setMarginEnd(dp(8));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_paperclip);
        row.addView(icon);

        TextView tvName = new TextView(this);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setText(displayName);
        tvName.setTextColor(Color.parseColor("#333333"));
        tvName.setTextSize(13);
        tvName.setPaintFlags(tvName.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tvName.setOnClickListener(v -> downloadUploadedFile(fullName));
        row.addView(tvName);

        ImageView btnDownload = new ImageView(this);
        LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        dlLp.setMarginStart(dp(8));
        btnDownload.setLayoutParams(dlLp);
        btnDownload.setPadding(dp(3), dp(3), dp(3), dp(3));
        btnDownload.setImageResource(R.drawable.ic_download_24);
        btnDownload.setOnClickListener(v -> downloadUploadedFile(fullName));
        row.addView(btnDownload);

        if (canDelete) {
            ImageView btnRemove = new ImageView(this);
            LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(dp(22), dp(22));
            rmLp.setMarginStart(dp(8));
            btnRemove.setLayoutParams(rmLp);
            btnRemove.setPadding(dp(2), dp(2), dp(2), dp(2));
            btnRemove.setImageResource(R.drawable.close_24);
            btnRemove.setOnClickListener(v -> confirmDeleteDocFile(fullName, scope));
            row.addView(btnRemove);
        }
        return row;
    }

    /** 1 dòng file mới chọn chưa upload (chỉ gỡ ở local). */
    private View buildPendingRow(String displayName, final Uri localUri) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconLp.setMarginEnd(dp(8));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_paperclip);
        row.addView(icon);

        TextView tvName = new TextView(this);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setText(displayName + "  (" + i18n("Not uploaded yet") + ")");
        tvName.setTextColor(Color.parseColor("#64748B"));
        tvName.setTextSize(13);
        row.addView(tvName);

        ImageView btnRemove = new ImageView(this);
        LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        rmLp.setMarginStart(dp(8));
        btnRemove.setLayoutParams(rmLp);
        btnRemove.setPadding(dp(2), dp(2), dp(2), dp(2));
        btnRemove.setImageResource(R.drawable.close_24);
        btnRemove.setOnClickListener(v -> {
            selectedFileUris.remove(localUri);
            renderAttachments();
        });
        row.addView(btnRemove);
        return row;
    }

    private void confirmDeleteDocFile(final String fullName, final String scope) {
        new AlertDialog.Builder(this)
                .setTitle(i18n("Remove attachment"))
                .setMessage(i18n("Are you sure you want to remove this file?") + "\n" + fullName)
                .setNegativeButton(i18n("Cancel"), null)
                .setPositiveButton(i18n("Remove"), (d, w) -> deleteDocFile(fullName, scope))
                .show();
    }

    private void deleteDocFile(final String fullName, final String scope) {
        final String woCode = edtWoCode != null ? edtWoCode.getText().toString().trim() : "";
        if (woCode.isEmpty() || "...".equals(woCode)) return;
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(i18n("Removing attachment..."));
        progress.setCancelable(false);
        progress.show();
        final String serverDynamicUrl = getServerDynamicUrl();
        new Thread(() -> {
            HttpClient.APIReturn result = HttpClient.deleteWorkOrderFile(this, serverDynamicUrl, woCode, fullName, scope);
            runOnUiThread(() -> {
                progress.dismiss();
                if (result != null && result.code == 200) {
                    loadEditModeAttachments();
                    Toast.makeText(this, i18n("Attachment removed"), Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = (result != null) ? result.message : i18n("No response from server");
                    Toast.makeText(this, i18n("Remove failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** Dispatcher: màn Sửa vẽ 2 khối; màn Thêm vẽ 1 khối. */
    private void renderAttachments() {
        if (isEditMode) {
            renderDocBlocks();
        } else {
            renderAttachmentList();
        }
    }

    private void applyI18nFieldTexts() {
        if (tvWorkOrderTitle != null) {
            tvWorkOrderTitle.setText(i18n(isEditMode ? "Edit Work Order" : "Add Work Order"));
        }
        if (tvLabelWoCode != null) tvLabelWoCode.setText(i18n("W/O Code"));
        if (tvLabelRequestDate != null) tvLabelRequestDate.setText(i18n("Time arises"));
        if (tvLabelMachine != null) tvLabelMachine.setText(i18n("Machine"));
        if (tvLabelProcess != null) tvLabelProcess.setText(i18n("Process"));
        if (tvLabelType != null) tvLabelType.setText(i18n("Type"));
        if (tvLabelRequester != null) tvLabelRequester.setText(i18n("Requester"));
        if (tvLabelPassedDate != null) tvLabelPassedDate.setText(i18n("Passed Date"));
        if (tvLabelDeadline != null) tvLabelDeadline.setText(i18n("Deadline"));
        if (tvLabelRequestReason != null) tvLabelRequestReason.setText(i18n("Work Order Content"));
        if (tvLabelMaStatus != null) tvLabelMaStatus.setText(i18n("MA Status"));
        if (tvLabelMesLock != null) tvLabelMesLock.setText(i18n("Is there a device lock feature on the MES system?"));
        if (radioMesLockYes != null) radioMesLockYes.setText(i18n("Yes"));
        if (radioMesLockNo != null) radioMesLockNo.setText(i18n("No"));

        if (edtWoCode != null) edtWoCode.setHint(i18n("W/O Code"));
        if (edtProcess != null) edtProcess.setHint(i18n("Enter process"));
        if (edtPassedDate != null) edtPassedDate.setHint(i18n("Enter passed date"));
        if (edtReason != null) edtReason.setHint(i18n("Enter work order content"));
        if (autoMachine != null) autoMachine.setHint(i18n("Select an item"));
        if (autoLoaiHinh != null) autoLoaiHinh.setHint(i18n("Select type"));
        if (autoRequester != null) autoRequester.setHint(i18n("Requester"));
        if (autoMaStatus != null) autoMaStatus.setHint(i18n("Select an item"));

        if (autoMaStatus != null) {
            setupDropdown(autoMaStatus, getLocalizedMaStatusOptions());
            String currentStatusText = autoMaStatus.getText() != null
                    ? autoMaStatus.getText().toString().trim()
                    : "";
//            int currentStatusCode = statusFromText(currentStatusText);
            int currentStatusCode = 0;
            if (!currentStatusText.isEmpty()) {
                currentStatusCode = statusFromText(currentStatusText);
            }
            if (currentStatusCode == 0 && editingData != null) {
                currentStatusCode = resolveStatusCodeFromData(editingData);
            }
            if (currentStatusCode == 0) {
                currentStatusCode = statusFromText(safeGet(editingData, "MA_STATUS"));
            }
            if (currentStatusCode > 0) {
                autoMaStatus.setText(convertStatus(currentStatusCode), false);
            }
        }

        if (btnAdd instanceof TextView) {
            ((TextView) btnAdd).setText(i18n(isEditMode ? "Save" : "Add"));
        }

        TextView tvLabelAttachment = findViewById(R.id.tv_label_attachment);
        if (tvLabelAttachment != null) tvLabelAttachment.setText(i18n("Attachment"));
        TextView tvChooseFile = findViewById(R.id.tv_choose_file);
        if (tvChooseFile != null) tvChooseFile.setText(i18n("Choose File"));
        TextView tvUploadFile = findViewById(R.id.tv_upload_file);
        if (tvUploadFile != null) tvUploadFile.setText(i18n("Upload"));
        TextView tvAttachmentStatus = findViewById(R.id.tv_attachment_status);
        if (tvAttachmentStatus != null) tvAttachmentStatus.setText(i18n("No attachment file"));
        TextView tvLabelDocMa = findViewById(R.id.tv_label_doc_ma);
        if (tvLabelDocMa != null) tvLabelDocMa.setText(i18n("Tài liệu MA"));
        TextView tvLabelDocFe = findViewById(R.id.tv_label_doc_fe);
        if (tvLabelDocFe != null) tvLabelDocFe.setText(i18n("Tài liệu FE"));

        // Các view chỉ dành cho FE (tab Vật tư, Assignee) — dịch đa ngôn ngữ.
        TextView tvTabInfo = findViewById(R.id.btn_tab_info);
        if (tvTabInfo != null) tvTabInfo.setText("1. " + i18n("Information"));
        TextView tvTabMaterial = findViewById(R.id.btn_tab_material);
        if (tvTabMaterial != null) tvTabMaterial.setText("2. " + i18n("Materials"));
        TextView tvBtnAddMaterial = findViewById(R.id.btn_add_material);
        if (tvBtnAddMaterial != null) tvBtnAddMaterial.setText("+ " + i18n("Add material"));
        TextView tvMaterialHeaderName = findViewById(R.id.tv_material_header_name);
        if (tvMaterialHeaderName != null) tvMaterialHeaderName.setText(i18n("Materials"));
        TextView tvMaterialHeaderStock = findViewById(R.id.tv_material_header_stock);
        if (tvMaterialHeaderStock != null) tvMaterialHeaderStock.setText(i18n("Stock"));
        TextView tvMaterialHeaderQty = findViewById(R.id.tv_material_header_qty);
        if (tvMaterialHeaderQty != null) tvMaterialHeaderQty.setText(i18n("Quantity"));
        TextView tvMaterialHeaderFunc = findViewById(R.id.tv_material_header_func);
        if (tvMaterialHeaderFunc != null) tvMaterialHeaderFunc.setText(i18n("Function"));
        TextView tvLabelAssignee = findViewById(R.id.tv_label_assignee);
        if (tvLabelAssignee != null) tvLabelAssignee.setText(i18n("Assignee"));
    }

    private void loadInitialData() {
        SimpleDateFormat sdfCode = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String dateForCode = sdfCode.format(new Date());

        new Thread(() -> {
            try {
                HttpClient.APIReturn resWo = HttpClient.getLastWoCodeToday(this, serverUrl, schemaMms, dateForCode);
                HttpClient.APIReturn resMachine = HttpClient.getMachineIdList(this, serverUrl, schemaCore, schemaMms, schemaData);
                HttpClient.APIReturn resUser = HttpClient.getUserList(this, serverUrl, schemaCore);

                runOnUiThread(() -> {
                    // WO Code
                    if(resWo != null && resWo.code == 200 && resWo.data != null && !resWo.data.isEmpty()){
                        String lastCode = resWo.data.get(0).optString("Wo_Code", resWo.data.get(0).optString("WO_CODE"));
                        edtWoCode.setText(generateNextWoCode(lastCode));
                    } else {
                        edtWoCode.setText("WO_" + dateForCode + "_001");
                    }

                    // Machine
                    if (resMachine != null && resMachine.code == 200 && resMachine.data != null) {
                        machineDataList = resMachine.data;
                        ArrayList<String> list = new ArrayList<>();
                        for (JSONObject m : machineDataList) {
                            list.add(m.optString("Machine_Id") + " - " + m.optString("Machine_Name"));
                        }
                        setupDropdownNew(autoMachine, list);
                    }

                    // User & Mapping Id_User
                    if (resUser != null && resUser.code == 200 && resUser.data != null) {
                        ArrayList<String> list = new ArrayList<>();
                        userIdMap.clear();
                        for (JSONObject u : resUser.data) {
                            String userName = u.optString("Id");
                            String fullName = u.optString("Full_Name");
                            String idUser = u.optString("Id_User");

                            userIdMap.put(userName, idUser);
                            list.add(userName + " - " + fullName);
                        }
                        setupDropdownNew(autoRequester, list);
                        // Assignee (FE, Loại WO = Other) dùng chung danh sách user.
                        if (autoAssignee != null) setupDropdownNew(autoAssignee, list);
                    }
                    try {
                        PreferenceHandler pref = new PreferenceHandler(this);
                        JSONObject userData = pref.getJsonObject("user");

                        ColorConsole.d(TAG, "DỮ LIỆU USER ĐÃ LƯU: " + (userData != null ? userData.toString() : "NULL"));

                        if (userData != null) {
                            loginUserName = userData.optString("userId", "");
                           // String loginUserName = userData.optString("userId", "");
                            String loginFullName = userData.optString("fullName", "");


                            if (userIdMap.containsKey(loginUserName)) {
                                createByRealId = userIdMap.get(loginUserName);
                                ColorConsole.d(TAG, "MAPPED ID: " + loginUserName + " -> " + createByRealId);
                            } else {
                                createByRealId = loginUserName;
                            }

                            if (!loginUserName.isEmpty()) {
                                autoRequester.setText(loginUserName + " - " + loginFullName, false);
                            }
                        }
                    } catch (Exception e) { Log.e(TAG, "Error default user", e); }

                    Toast.makeText(this, i18n("Load completed"), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) { Log.e(TAG, "Error", e); }
        }).start();
    }

    private void setupDropdownNew(AutoCompleteTextView view, List<String> list) {
        if (view == null || list == null || list.isEmpty()) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(list)) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        if (constraint == null || constraint.length() == 0) {
                            results.values = list;
                            results.count = list.size();
                        } else {
                            List<String> suggestions = new ArrayList<>();
                            String filterPattern = constraint.toString().toLowerCase().trim();
                            for (String item : list) {
                                if (item.toLowerCase().contains(filterPattern)) {
                                    suggestions.add(item);
                                }
                            }
                            results.values = suggestions;
                            results.count = suggestions.size();
                        }
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results != null && results.count > 0) {
                            addAll((List<String>) results.values);
                        }
                        notifyDataSetChanged();
                    }

                    @Override
                    public CharSequence convertResultToString(Object resultValue) {
                        return resultValue.toString();
                    }
                };
            }
        };

        view.setAdapter(adapter);
        view.setThreshold(0);

        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (view.getText().toString().isEmpty()) {
                    adapter.getFilter().filter(null);
                }
                view.showDropDown();
            }
            return false;
        });

        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                view.postDelayed(view::showDropDown, 200);
            }
        });

        view.post(() -> view.setDropDownWidth(view.getWidth()));
    }

    private void setupDropdown(AutoCompleteTextView view, List<String> list) {
        if (view == null || list == null) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, list);
        view.setAdapter(adapter);
        view.setOnClickListener(v -> {
            view.setText(view.getText(), false);
            adapter.getFilter().filter(null);
            view.postDelayed(view::showDropDown, 150);
        });
    }

    private void showDateTimePicker(EditText editText) {
        Calendar cal = Calendar.getInstance();
        try {
            String currentText = editText.getText().toString();
            if (!currentText.isEmpty()) {
                Date d = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(currentText);
                if (d != null) cal.setTime(d);
            }
        } catch (Exception e) { e.printStackTrace(); }

        DatePickerDialog dialog = new DatePickerDialog(this, (v, y, m, d) -> {
            cal.set(y, m, d);
            new TimePickerDialog(this, (v2, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                String formattedDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(cal.getTime());
                editText.setText(formattedDate);
                if (editText.getId() == R.id.edt_request_date) {
                    updatePassedDays(formattedDate);
                }
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        //TODO: chọn từ hiện tại tới tương lai
//        if (editText.getId() == R.id.edt_request_date) {
//            dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
//        }

        //TODO: chọn từ quá khứ tới hiện tại
        if (editText.getId() == R.id.edt_request_date) {
            try {
                long maxDate;
                if (isEditMode && editingData != null) {

                    String createDateRaw = safeGet(editingData, "CREATE_DATE");

                    if (createDateRaw.isEmpty()) {
                        createDateRaw = safeGet(editingData, "Create_Date");
                    }

                    String createDateUi = formatDateForUI(createDateRaw);
                    Date createDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(createDateUi);

                    if (createDate != null) {
                        maxDate = createDate.getTime();
                    } else {
                        maxDate = System.currentTimeMillis();
                    }
                } else {
                    maxDate = System.currentTimeMillis();
                }
                dialog.getDatePicker().setMaxDate(maxDate);
            } catch (Exception e) {
                dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            }
        }

        dialog.show();
    }

    private String generateNextWoCode(String last) {
        if (last == null || last.isEmpty() || !last.contains("_")) {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            return "WO_" + today + "_001";
        }
        try {
            String[] p = last.split("_");
            if (p.length >= 3) {
                int num = Integer.parseInt(p[2]) + 1;
                return String.format(Locale.getDefault(), "%s_%s_%03d", p[0], p[1], num);
            } else {
                return last + "_1";
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi parse mã W/O: " + e.getMessage());
            return last;
        }
    }

    private String escapeSql(String value){
        return "'" + value + "'";
    }

    private String formatToTimestamp(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Date date = inputFormat.parse(dateStr);

            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            String formatted = outputFormat.format(date);

            return "TO_TIMESTAMP('" + formatted + "', 'YYYY-MM-DD HH24:MI:SS')";
        } catch (Exception e) {
            return "NULL";
        }
    }

    private void performAddWorkOrder(){
        if (isSubmitting) return;

        ColorConsole.i("Bắt đầu thêm Work Order");

        //TODO: Lấy dữ liệu

        String woCode = edtWoCode.getText().toString().trim(); //mã WO
        String requestDate = edtRequestDate.getText().toString().trim(); //ngày yêu cầu
        String machineRaw = autoMachine.getText().toString().trim(); //mã máy
        // Loại WO: MA cố định BM; FE lấy theo lựa chọn (BM/PM/CM/Other).
        String loaiHinhSel = isFeUser ? autoLoaiHinh.getText().toString().trim() : "BM";
        final String loaiHinh = loaiHinhSel.isEmpty() ? "BM" : loaiHinhSel;
        String requesterRaw = autoRequester.getText().toString().trim(); //tên người yêu cầu

        String userIdToSave = "";
        if (!requesterRaw.isEmpty() && requesterRaw.contains(" - ")) {
            userIdToSave = requesterRaw.split(" - ")[0].trim();
        } else {
            userIdToSave = requesterRaw;
        }
        ColorConsole.d(TAG, "ID người yêu cầu để lưu: " + userIdToSave);

        String reason = edtReason.getText().toString().trim(); //lý do yêu cầu
        String process = edtProcess.getText().toString().trim(); //trạng thái máy
        String passedDateVal = edtPassedDate.getText().toString().trim(); //ngày trải qua
        String maStatusStr = autoMaStatus.getText().toString().trim(); //trạng thái MA báo
        String currentLockStatus = getCurrentLockStatus();

        // FE + Loại WO = Other: không cần Máy, thay bằng Assignee.
        final boolean isOther = isFeUser && "Other".equalsIgnoreCase(loaiHinh);
        String assigneeRaw = (isFeUser && autoAssignee != null) ? autoAssignee.getText().toString().trim() : "";
        final String assigneeId = assigneeRaw.contains(" - ") ? assigneeRaw.split(" - ")[0].trim() : assigneeRaw;

        //TODO: validate
        if (requesterRaw.isEmpty() || woCode.isEmpty()) {
            Toast.makeText(this, i18n("Please enter Machine and Requester"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isOther && machineRaw.isEmpty()) {
            Toast.makeText(this, i18n("Please enter Machine and Requester"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (isOther && assigneeId.isEmpty()) {
            Toast.makeText(this, i18n("Assignee") + " " + i18n("is required"), Toast.LENGTH_SHORT).show();
            return;
        }
        // Giống web: Thời gian phát sinh chỉ bắt buộc với MA.
        if (!isFeUser && requestDate.isEmpty()) {
            Toast.makeText(this, i18n("Time arises is required"), Toast.LENGTH_SHORT).show();
            return;
        }
        // FE (không phải Other): đã nhập số lượng thì phải chọn vật tư.
        if (isFeUser && !isOther && !validateMaterials()) {
            return;
        }
        if(!validateRequiredFields()){
            return;
        }

        isSubmitting = true;
        btnAdd.setEnabled(false);

        // Status: nếu thời gian phát sinh < hôm nay (so theo ngày) và chưa chọn MA status
        // thì đặt Quá hạn (5); ngược lại dùng giá trị MA status. (giống web)
        int maReport = statusFromText(maStatusStr);
        final boolean isOverdue = isRequestDateBeforeToday(requestDate);
        int statusVal = (isOverdue && maReport == 0) ? 5 : maReport;

       //TODO: Tách lấy ID từ chuỗi "ID - Name"
        final String machineId = isOther ? "OTHER"
                : (machineRaw.contains(" - ") ? machineRaw.split(" - ")[0] : machineRaw);
        final String requesterUsername = requesterRaw.contains(" - ") ? requesterRaw.split(" - ")[0].trim() : requesterRaw;
        ColorConsole.d(TAG, "ID người yêu cầu: " + requesterUsername);

        //TODO: Lấy thông tin người tạo
        PreferenceHandler preferenceHandler = new PreferenceHandler(this);
        //JSONObject userData = preferenceHandler.getJsonObject("user");
        JSONObject userData = null;
        try {
            userData = preferenceHandler.getJsonObject("user");
        } catch (Exception e) {
            ColorConsole.e(TAG, "Lỗi khi lấy user object từ SharedPreferences: " + e.getMessage());
        }
        ColorConsole.d(TAG, "DỮ LIỆU USER ĐÃ LƯU: " + (userData != null ? userData.toString() : "NULL"));

        String tempEmail = "";
        String tempFullName = "";
        String tempDivisionName = "";

        if(userData != null){
            tempEmail = safeGet(userData, "email");
            tempFullName = safeGet(userData, "fullName");
            tempDivisionName = safeGet(userData, "divisionName");
            ColorConsole.d(TAG, "DỮ LIỆU ĐÃ LẤY: Email=" + tempEmail + " | Division=" + tempDivisionName);
        }

        final String currentLoginUserId = createByRealId;
        final String finalUserEmail = tempEmail;
        final String finalFullName = tempFullName;
        final String finalDivisionName = tempDivisionName;
        final String finalRequestUser = buildRequestUserLabel(userData);
        ColorConsole.d(TAG, "ID người tạo (CREATE_BY) sẽ gửi lên DB: " + currentLoginUserId);

        //TODO: Thêm loading data
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(i18n("Processing data..."));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try{
                // Chặn tạo WO thứ 2 khi máy đang có WO chưa hoàn thành
                // (giống web checkActiveWorkOrderForMachine). Bỏ qua với Loại WO = Other (không gắn máy).
                String activeWhere = "mt.MACHINE_ID = '" + machineId + "' AND mt.DELETED = 0 "
                        + "AND NVL(mt.STATUS,0) <> 6 AND NVL(tp.Status_1, 0) NOT IN (1, 6)";
                HttpClient.APIReturn resActive = isOther ? null : HttpClient.getAllWorkOrder(
                        this, serverUrl, schemaMms, schemaCore, activeWhere, 0, 1);
                if (resActive != null && resActive.code == 200
                        && resActive.data != null && !resActive.data.isEmpty()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        isSubmitting = false;
                        btnAdd.setEnabled(true);
                        Toast.makeText(this,
                                i18n("This machine already has an unfinished Work Order. Cannot create another."),
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                JSONObject woData = new JSONObject();
                woData.put("Schema_MMS", schemaMms);
                woData.put("WO_CODE", escapeSql(woCode));
                woData.put("WO_TYPE", getWoTypeInt(loaiHinh));
                woData.put("MACHINE_ID", escapeSql(machineId));
               // woData.put("REQUEST_USER", escapeSql(createByRealId));
               // woData.put("REQUEST_USER", escapeSql(requesterRaw));
                woData.put("REQUEST_USER", escapeSql(requesterUsername));
                woData.put("REQUEST_REASON", escapeSql(reason));
//                woData.put("REQUEST_DATE", formatToTimestamp(requestDate));
                woData.put("REQUEST_DATE", convertToServerFormat(requestDate));
                woData.put("DEADLINE", "");
                // Other -> assignee được chọn; ngược lại giữ người yêu cầu.
                woData.put("ASSIGNEE", escapeSql(isOther ? assigneeId : requesterUsername));
                // FE (không phải Other) gửi danh sách vật tư thật; còn lại rỗng.
                woData.put("MATERIAL_JSON", escapeSql((isFeUser && !isOther) ? collectMaterialsJson() : "[]"));
                woData.put("PHYSICAL_GROUP_NAME", escapeSql(process));
                woData.put("PASSED_DATE", escapeSql(passedDateVal));
                woData.put("STATUS", statusVal);
                woData.put("STATUS_1", 0);
                woData.put("IS_LOCKED", currentLockStatus);
                woData.put("NOTE", escapeSql(reason));
             //   woData.put("CREATE_BY", escapeSql(createByRealId));
                woData.put("CREATE_BY", escapeSql(loginUserName));

                woData.put("DELETED", 0);
                // Bắt buộc gửi 2 field này (giống web) để template INSERT không bị thiếu cột
                // -> tránh dấu phẩy thừa gây ORA-01747. Màn Thêm mặc định: có khai báo daily (1), lý do NULL.
                woData.put("IS_DAILY_REPORT", 1);
                woData.put("DAILY_REPORT_REASON", "NULL");
                ColorConsole.i(TAG, "Add WO data: " + woData.toString());

                HttpClient.APIReturn resWo = HttpClient.addMtWorkOrder(this, serverUrl, woData);
                ColorConsole.d(TAG, "Add WO response: " + resWo.toString());

                if(resWo == null || resWo.code != 200){
                    String errorMsg = (resWo != null) ? resWo.message : "Không kết nối được server";
                    throw new Exception("Lỗi thêm WorkOrder: " + errorMsg);
                }
                ColorConsole.i(TAG, "Add WO success, start mail and task processes...");

                //TODO: Cập nhật trạng thái máy

                if (getWoTypeInt(loaiHinh) == 3) {

                    int machineStatus = 1;

                    if ("MA".equalsIgnoreCase(finalDivisionName)) {

                        if ("2".equals(currentLockStatus)) {
                            machineStatus = 2; // khóa -> đỏ nhấp nháy
                        } else {
                            machineStatus = 1; // không khóa -> đỏ thường
                        }

                    } else {
                        machineStatus = 1;
                    }

                    HttpClient.APIReturn resStatus =
                            HttpClient.updateMachineStatus(
                                    this,
                                    serverUrl,
                                    schemaCore,
                                    machineId,
                                    machineStatus
                            );

                    if(resStatus == null || resStatus.code != 200){
                        ColorConsole.e(
                                "API Error",
                                "Lỗi cập nhật trạng thái máy: "
                                        + (resStatus != null ? resStatus.message : "Không kết nối được server")
                        );
                    }
                }

                int isLockVal = 0;
                try {
                    isLockVal = Integer.parseInt(currentLockStatus);
                } catch (NumberFormatException ignored) {}

                sendWoMailNotificationIfNeeded(
                        "ADD",
                        getWoTypeInt(loaiHinh),
                        woCode,
                        machineId,
                        machineRaw,
                        process,
                        requestDate,
                        reason,
                        finalRequestUser,
                        finalUserEmail,
                        finalDivisionName,
                        isLockVal
                );

                //TODO: Thêm task tương ứng
                String maintainerIdForTask = "";

                try {
                    HttpClient.APIReturn resPicTask = HttpClient.getPersonInCharge(
                            this, serverUrl, machineId, schemaCore, schemaMms, schemaData
                    );

                    if (resPicTask != null && resPicTask.code == 200 && resPicTask.data != null && !resPicTask.data.isEmpty()) {
                        JSONObject picData = resPicTask.data.get(0);

                        maintainerIdForTask = safeGet(picData, "Person_In_Charge");

                        if (maintainerIdForTask.isEmpty()) {
                            maintainerIdForTask = safeGet(picData, "Person_In_Charge");
                        }

                        ColorConsole.d(TAG, "[TASK] MaintainerId: " + maintainerIdForTask);
                    }
                } catch (Exception e) {
                    ColorConsole.e(TAG, "Lỗi lấy maintainer cho TASK: " + e.getMessage());
                }

                if (maintainerIdForTask.isEmpty()) {
                    maintainerIdForTask = createByRealId;
                }
                // Other: người phụ trách task = Assignee đã chọn (không lấy PIC theo máy).
                if (isOther && !assigneeId.isEmpty()) {
                    maintainerIdForTask = assigneeId;
                }
                // Task cũng quá hạn nếu thời gian phát sinh < hôm nay (giống web).
                int taskStatus = isOverdue ? 5 : 0;
                // Task_Type theo Loại WO (giống web): PM=2, CM=3, BM=4, Other=5.
                int taskType = getWoTypeInt(loaiHinh) + 1;
                JSONObject taskData = prepareTaskData(machineId, woCode, reason, maintainerIdForTask, taskStatus, taskType);
                HttpClient.APIReturn resTask = HttpClient.addMtTask(this, serverUrl, taskData);

                if(resTask == null || resTask.code != 200){
                    throw new Exception("Lỗi thêm Task: " + (resTask != null ? resTask.message : "Không kết nối được server"));
                }

                //TODO: Upload ảnh đính kèm đã chọn (taskId = WO_CODE)
                final boolean uploadOk = uploadSelectedFilesSync(woCode);

                //TODO: Thêm WorkOrder thành công, cập nhật trạng thái thành công, tạo task tương ứng thành công
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    isSubmitting = false;
                    btnAdd.setEnabled(true);
                    Toast.makeText(this, i18n("Add Work Order successful"), Toast.LENGTH_SHORT).show();
                    if (!uploadOk) {
                        Toast.makeText(this, i18n("Upload failed"), Toast.LENGTH_LONG).show();
                    }
                    setResult(RESULT_OK);
                    finish();
                });
            }catch (Exception e){
                ColorConsole.e(TAG, "Lỗi gửi mail: " + e.getMessage());

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    isSubmitting = false;
                    btnAdd.setEnabled(true);
                    Toast.makeText(this, i18n("Error") + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void performUpdateWorkOrder(){
        if (isSubmitting) return;

        String woCode = editingWoCode;
        String requestDateUi = edtRequestDate.getText().toString().trim();
        String reason = edtReason.getText().toString().trim();

        //TODO: validate (giống web renderEditWorkOrder)
        // 1) Thời gian phát sinh bắt buộc
        if (requestDateUi.isEmpty()) {
            Toast.makeText(this, i18n("Time arises is required"), Toast.LENGTH_SHORT).show();
            return;
        }
        // 2) Thời gian phát sinh không được lớn hơn ngày tạo work order
        if (editingData != null) {
            String createRaw = safeGet(editingData, "CREATE_DATE");
            if (createRaw.isEmpty()) createRaw = safeGet(editingData, "Create_Date");
            if (!createRaw.isEmpty()) {
                Date reqDate = parseDisplayDate(requestDateUi);
                Date createDate = parseDisplayDate(formatDateForUI(createRaw));
                if (reqDate != null && createDate != null && reqDate.getTime() > createDate.getTime()) {
                    Toast.makeText(this,
                            i18n("Thời gian phát sinh không được lớn hơn ngày tạo work order"),
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        // 3) Nội dung work order bắt buộc
        if (reason.isEmpty()) {
            edtReason.setError(i18n("Request reason is required"));
            edtReason.requestFocus();
            return;
        }

        isSubmitting = true;
        btnAdd.setEnabled(false);

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(i18n("Updating..."));
        dialog.setCancelable(false);
        dialog.show();

        final String currentLockStatusVal = getCurrentLockStatus();

        new Thread(() -> {
            try{
                String requestDateApi = formatDateForApi(requestDateUi);

                String machineId = "";
                String requester = "";
                int woType = 3;
                int status = 0;
                String materialInfo = "[]";
                String createDateApi = requestDateApi;

                if (editingData != null) {
                    machineId = safeGet(editingData, "MACHINE_ID");
                    if (machineId.isEmpty()) machineId = safeGet(editingData, "Machine_Id");

                    requester = safeGet(editingData, "REQUEST_USER");
                    if (requester.isEmpty()) requester = safeGet(editingData, "Request_User");

                    if (editingData.has("WO_TYPE")) woType = editingData.optInt("WO_TYPE", 3);
                    else if (editingData.has("Wo_Type")) woType = editingData.optInt("Wo_Type", 3);

                    if (editingData.has("STATUS")) status = editingData.optInt("STATUS", 0);
                    else if (editingData.has("Status")) status = editingData.optInt("Status", 0);

                    String material = safeGet(editingData, "MATERIAL_INFO");
                    if (material.isEmpty()) material = safeGet(editingData, "MATERIAL_JSON");
                    if (!material.isEmpty()) materialInfo = material;

                    String createRaw = safeGet(editingData, "CREATE_DATE");
                    if (createRaw.isEmpty()) createRaw = safeGet(editingData, "Create_Date");
                    if (!createRaw.isEmpty()) createDateApi = formatDateForApi(createRaw);
                }

                if (machineId.isEmpty()) {
                    String machineRaw = autoMachine.getText().toString().trim();
                    machineId = machineRaw.contains(" - ") ? machineRaw.split(" - ")[0].trim() : machineRaw;
                }

                if (requester.isEmpty()) {
                    String requesterRaw = autoRequester.getText().toString().trim();
                    requester = requesterRaw.contains(" - ") ? requesterRaw.split(" - ")[0].trim() : requesterRaw;
                }

                if (status == 0) {
                    String maStatusStr = autoMaStatus.getText().toString().trim();
                    status = statusFromText(maStatusStr);
                }

                String currentLockStatus = currentLockStatusVal;

                JSONObject condition = new JSONObject();
                condition.put("Schema_MMS", schemaMms);
                condition.put("WO_CODE", woCode);
                condition.put("WO_TYPE", woType);
                condition.put("MACHINE_ID", machineId);
                condition.put("REQUEST_USER", requester);
                condition.put("ASSIGNEE", requester);
                condition.put("REQUEST_REASON", reason);
                condition.put("REQUEST_DATE", requestDateApi);
                condition.put("CREATE_DATE", createDateApi);
                condition.put("DEADLINE", "");
                condition.put("MATERIAL_INFO", materialInfo);
                condition.put("STATUS", status);
                condition.put("IS_LOCKED", currentLockStatus);
                condition.put("NOTE", reason);

                ColorConsole.d(TAG, "Update Condition: " + condition.toString());

                HttpClient.APIReturn res = HttpClient.updateMtWorkOrder(this, serverUrl, condition);

                if (res != null && res.code == 200) {
                    JSONObject userData = getCurrentUserProfile();
                    String creatorEmail = safeGet(userData, "email");
                    String divisionName = safeGet(userData, "divisionName");
                    if (woType == 3) {

                        int machineStatus = currentFeStatus;

                        if ("MA".equalsIgnoreCase(divisionName)) {

                            if ("2".equals(currentLockStatus)) {

                                machineStatus = 2; // khóa -> đỏ nhấp nháy

                            } else {

                                machineStatus = 1; // không khóa -> đỏ thường
                            }
                        }

                        HttpClient.APIReturn resStatus =
                                HttpClient.updateMachineStatus(
                                        this,
                                        serverUrl,
                                        schemaCore,
                                        machineId,
                                        machineStatus
                                );

                        if(resStatus == null || resStatus.code != 200){

                            ColorConsole.e(
                                    "API Error",
                                    "Lỗi cập nhật trạng thái máy: "
                                            + (resStatus != null ? resStatus.message : "Không kết nối được server")
                            );
                        }
                    }
                    String requestUser = buildRequestUserLabel(userData);
                    String machineLabel = machineId;
                    if (editingData != null) {
                        String machineName = safeGet(editingData, "MACHINE_NAME");
                        if (machineName.isEmpty()) machineName = safeGet(editingData, "Machine_Name");
                        if (!machineName.isEmpty()) {
                            machineLabel = machineId + " - " + machineName;
                        }
                    }

                    String processStep = safeGet(editingData, "PHYSICAL_GROUP_NAME");
                    if (processStep.isEmpty()) {
                        processStep = edtProcess.getText().toString().trim();
                    }

                    int isLockVal = 0;
                    try {
                        isLockVal = Integer.parseInt(currentLockStatus);
                    } catch (NumberFormatException ignored) {}

                    sendWoMailNotificationIfNeeded(
                            "EDIT",
                            woType,
                            woCode,
                            machineId,
                            machineLabel,
                            processStep,
                            requestDateUi,
                            reason,
                            requestUser,
                            creatorEmail,
                            divisionName,
                            isLockVal
                    );
                }

                runOnUiThread(() -> {
                    dialog.dismiss();
                    isSubmitting = false;
                    btnAdd.setEnabled(true);

                    if(res != null && res.code == 200){
                        Toast.makeText(this, i18n("Update successful"), Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }else{
                        String msg = (res != null) ? res.message : i18n("No response from server");
                        Toast.makeText(this, String.format(Locale.getDefault(), i18n("Update failed: %s"), msg), Toast.LENGTH_LONG).show();
                    }
                });

            }catch (Exception e){
                runOnUiThread(() -> {
                    dialog.dismiss();
                    isSubmitting = false;
                    btnAdd.setEnabled(true);
                    Toast.makeText(this, String.format(Locale.getDefault(), i18n("Update error: %s"), e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private JSONObject prepareTaskData(String machineId, String woCode, String reason, String maintainerId, int taskStatus) throws Exception {
        return prepareTaskData(machineId, woCode, reason, maintainerId, taskStatus, 4);
    }

    private JSONObject prepareTaskData(String machineId, String woCode, String reason, String maintainerId, int taskStatus, int taskType) throws Exception {
        JSONObject taskData = new JSONObject();
        String dateSuffix = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String taskId = "TASK_" + dateSuffix + "_" + (new Random().nextInt(9000) + 1000);
        long unixTime = System.currentTimeMillis() / 1000;

        String dsaTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        taskData.put("Schema_MMS", schemaMms);
        taskData.put("taskId", escapeSql(taskId));
        taskData.put("taskType", String.valueOf(taskType));
        taskData.put("machineId", escapeSql(machineId));
        taskData.put("status", String.valueOf(taskStatus));
        taskData.put("dsa", "TO_TIMESTAMP('" + dsaTime + "', 'YYYY-MM-DD HH24:MI:SS')");
        taskData.put("taskDateUnix", String.valueOf(unixTime));
        taskData.put("maintainerId", escapeSql(maintainerId));
        taskData.put("requirementTask", escapeSql(reason));
        taskData.put("issueId", escapeSql(woCode));

        return taskData;
    }

    private int getWoTypeInt(String loaiHinh){
        // Map giống web: PM=1, CM=2, BM=3, Other=4.
        if (loaiHinh == null) return 3;
        String t = loaiHinh.trim();
        if ("PM".equalsIgnoreCase(t)) return 1;
        if ("CM".equalsIgnoreCase(t)) return 2;
        if ("BM".equalsIgnoreCase(t)) return 3;
        if ("Other".equalsIgnoreCase(t)) return 4;
        return 3;
    }

    /** Số WO_TYPE -> text (giống web woTypeNumberToText). */
    private String woTypeNumberToText(int n){
        switch (n) {
            case 1: return "PM";
            case 2: return "CM";
            case 3: return "BM";
            case 4: return "Other";
            default: return "BM";
        }
    }

    private String formatToDateOnly(String dateTimeStr) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = input.parse(dateTimeStr);
            return output.format(date);
        } catch (Exception e) {
            return dateTimeStr;
        }
    }

    private String safeGet(JSONObject json, String key) {
        if (json == null) return "";
        try{
            if (json.has(key)) {
                String val = json.optString(key, "");
                return (val == null || val.equalsIgnoreCase("null")) ? "" : val;
            }
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (k.equalsIgnoreCase(key)) {
                    String val = json.optString(k, "");
                    return (val == null || val.equalsIgnoreCase("null")) ? "" : val;
                }
            }
        }catch (Exception e){
            return "";
        }
        return "";
    }

    //TODO: func tính ngày trải qua
    private void updatePassedDays(String selectedDateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Date selectedDate = sdf.parse(selectedDateStr);

            // Tính theo NGÀY LỊCH (bỏ giờ) để khớp với số hiển thị ở danh sách.
            Calendar c1 = Calendar.getInstance();
            c1.setTime(selectedDate);
            c1.set(Calendar.HOUR_OF_DAY, 0); c1.set(Calendar.MINUTE, 0);
            c1.set(Calendar.SECOND, 0); c1.set(Calendar.MILLISECOND, 0);

            Calendar c2 = Calendar.getInstance();
            c2.set(Calendar.HOUR_OF_DAY, 0); c2.set(Calendar.MINUTE, 0);
            c2.set(Calendar.SECOND, 0); c2.set(Calendar.MILLISECOND, 0);

            long diffInDays = Math.round((c2.getTimeInMillis() - c1.getTimeInMillis()) / (24.0 * 60 * 60 * 1000));
            if (diffInDays < 0) diffInDays = 0;

            edtPassedDate.setText(String.valueOf(diffInDays));

        } catch (Exception e) {
            edtPassedDate.setText("0");
        }
    }

    /**
     * So sánh theo NGÀY (bỏ giờ): trả về true nếu ngày phát sinh trước ngày hôm nay.
     * Dùng để đặt trạng thái Quá hạn (5) khi tạo WO/Task, giống web.
     */
    /** Parse chuỗi hiển thị "dd/MM/yyyy HH:mm" thành Date, trả null nếu lỗi. */
    private Date parseDisplayDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRequestDateBeforeToday(String uiDate) {
        try {
            Date d = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(uiDate);
            if (d == null) return false;
            Calendar req = Calendar.getInstance();
            req.setTime(d);
            req.set(Calendar.HOUR_OF_DAY, 0);
            req.set(Calendar.MINUTE, 0);
            req.set(Calendar.SECOND, 0);
            req.set(Calendar.MILLISECOND, 0);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            return req.getTimeInMillis() < today.getTimeInMillis();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateRequiredFields() {
        if (edtReason.getText().toString().trim().isEmpty()) {
            edtReason.setError(i18n("Request reason is required"));
            edtReason.requestFocus();
            return false;
        }
        return true;
    }

    //TODO: func convert time
    private String convertToServerFormat(String dateStr){
        try{
            SimpleDateFormat input = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date d = input.parse(dateStr);

            return "TO_TIMESTAMP('" + output.format(d) + "', 'YYYY-MM-DD HH24:MI:SS')";
        }catch (Exception e){
            return "NULL";
        }
    }

    private JSONObject getCurrentUserProfile() {
        try {
            PreferenceHandler preferenceHandler = new PreferenceHandler(this);
            return preferenceHandler.getJsonObject("user");
        } catch (Exception e) {
            ColorConsole.e(TAG, "Lỗi khi lấy user hiện tại: " + e.getMessage());
            return null;
        }
    }

    private String buildRequestUserLabel(JSONObject userData) {
        String userId = safeGet(userData, "userId").trim();
        String fullName = safeGet(userData, "fullName").trim();

        if (userId.isEmpty()) return fullName;
        if (fullName.isEmpty()) return userId;
        return userId + "-" + fullName;
    }

    private JSONObject buildWoMailPayload(
            String action,
            String woCode,
            int woType,
            String machineId,
            String machineLabel,
            String processStep,
            String requestDate,
            String requestReason,
            String requestUser,
            String creatorEmail,
            String divisionName,
            int isLock
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("Action", action);
        payload.put("WoCode", woCode);
        payload.put("WoType", woType == 3 ? "BM" : String.valueOf(woType));
        payload.put("MachineId", machineId);
        payload.put("MachineLabel", machineLabel);
        payload.put("ProcessStep", processStep);
        payload.put("RequestDate", requestDate);
        payload.put("RequestReason", requestReason);
        payload.put("RequestUser", requestUser);
        payload.put("CreatorEmail", creatorEmail);
        payload.put("DivisionName", divisionName);
        payload.put("IsLock", isLock);
        payload.put("isLock", isLock);

        // Compatibility keys for services that parse camelCase field names.
        payload.put("action", action);
        payload.put("woCode", woCode);
        payload.put("woType", woType == 3 ? "BM" : String.valueOf(woType));
        payload.put("machineId", machineId);
        payload.put("machineLabel", machineLabel);
        payload.put("processStep", processStep);
        payload.put("requestDate", requestDate);
        payload.put("requestReason", requestReason);
        payload.put("requestUser", requestUser);
        payload.put("creatorEmail", creatorEmail);
        payload.put("divisionName", divisionName);
        return payload;
    }

    private JSONObject buildWoMailPayloadCamelCaseOnly(
            String action,
            String woCode,
            int woType,
            String machineId,
            String machineLabel,
            String processStep,
            String requestDate,
            String requestReason,
            String requestUser,
            String creatorEmail,
            String divisionName,
            int isLock
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("action", action);
        payload.put("woCode", woCode);
        payload.put("woType", woType == 3 ? "BM" : String.valueOf(woType));
        payload.put("machineId", machineId);
        payload.put("machineLabel", machineLabel);
        payload.put("processStep", processStep);
        payload.put("requestDate", requestDate);
        payload.put("requestReason", requestReason);
        payload.put("requestUser", requestUser);
        payload.put("creatorEmail", creatorEmail);
        payload.put("divisionName", divisionName);
        payload.put("isLock", isLock);
        return payload;
    }

    private void sendWoMailNotificationIfNeeded(
            String action,
            int woType,
            String woCode,
            String machineId,
            String machineLabel,
            String processStep,
            String requestDate,
            String requestReason,
            String requestUser,
            String creatorEmail,
            String divisionName,
            int isLock
    ) {
        if (woType != 3) {
            ColorConsole.w(TAG, "Skip WO mail: unsupported woType=" + woType + " for WO=" + woCode);
            return;
        }

        if (divisionName == null || !"MA".equalsIgnoreCase(divisionName.trim())) {
            ColorConsole.w(TAG, "Skip WO mail: division is not MA, division=" + divisionName + " for WO=" + woCode);
            return;
        }

        try {
            String requestDateApi = formatDateForApi(requestDate);
            JSONObject payload = buildWoMailPayload(
                    action,
                    woCode,
                    woType,
                    machineId,
                    machineLabel,
                    processStep,
                    requestDateApi,
                    requestReason,
                    requestUser,
                    creatorEmail,
                    divisionName,
                    isLock
            );

            ColorConsole.d(TAG, "WO mail payload: " + payload.toString());
            HttpClient.APIReturn mailRes = HttpClient.sendWoMailNotification(this, serverUrl, payload);
            if (mailRes != null) {
                ColorConsole.d(TAG, "WO mail response: " + mailRes.code + " | " + mailRes.message);
                if (mailRes.code != 200) {
                    JSONObject fallbackPayload = buildWoMailPayloadCamelCaseOnly(
                            action,
                            woCode,
                            woType,
                            machineId,
                            machineLabel,
                            processStep,
                            requestDateApi,
                            requestReason,
                            requestUser,
                            creatorEmail,
                            divisionName,
                            isLock
                    );
                    ColorConsole.w(TAG, "WO mail retry with camelCase payload: " + fallbackPayload.toString());
                    HttpClient.APIReturn retryRes = HttpClient.sendWoMailNotification(this, serverUrl, fallbackPayload);
                    if (retryRes != null) {
                        ColorConsole.d(TAG, "WO mail retry response: " + retryRes.code + " | " + retryRes.message);
                    } else {
                        ColorConsole.w(TAG, "WO mail retry response is null");
                    }
                }
            } else {
                ColorConsole.w(TAG, "WO mail response is null");
            }
        } catch (Exception e) {
            ColorConsole.e(TAG, "Lỗi gửi mail WorkOrder: " + e.getMessage());
        }
    }

    //TODO: func bind data ui
    private void bindDataToUI(JSONObject data){
        try{
            editingWoCode = safeGet(data, "WO_CODE");

            // FE STATUS
            currentFeStatus = data.optInt("Fe_Status", 0);
            if (currentFeStatus == 0) {
                currentFeStatus = data.optInt("FE_STATUS", 0);
            }

            edtWoCode.setText(editingWoCode);
            edtRequestDate.setText(formatDateForUI(safeGet(data, "REQUEST_DATE")));
            edtReason.setText(safeGet(data, "REQUEST_REASON"));

            // MACHINE
            String machineId = safeGet(data, "MACHINE_ID");
            String machineName = safeGet(data, "MACHINE_NAME");
            autoMachine.setText(machineId + " - " + machineName, false);

            // PROCESS
            edtProcess.setText(safeGet(data, "PHYSICAL_GROUP_NAME"));

            // DEADLINE
            String deadline = safeGet(data, "DEADLINE");
            if (edtDeadline != null) {
                edtDeadline.setText(formatDateForUI(deadline));
            }

            // PASSED DATE: tính số ngày đã trải qua từ Thời gian phát sinh tới hiện tại
            // (giống màn Thêm). Nếu không parse được thì fallback lấy giá trị đã lưu.
            String reqDateForPassed = edtRequestDate.getText().toString().trim();
            if (!reqDateForPassed.isEmpty()) {
                updatePassedDays(reqDateForPassed);
            } else {
                edtPassedDate.setText(safeGet(data, "Passed_Date"));
            }

            // REQUESTER
            String requester = safeGet(data, "REQUEST_USER");
            autoRequester.setText(requester, false);

            // STATUS (support both numeric and text payloads, and both key styles)
            int status = resolveStatusCodeFromData(data);

            autoMaStatus.setText(convertStatus(status), false);

            // STATUS LOCKED
            String isLocked = safeGet(data, "IS_LOCKED");
            if (isLocked.isEmpty()) {
                isLocked = safeGet(data, "Is_Locked");
            }
            if ("2".equals(isLocked)) {

                radioMesLockYes.setChecked(true);

            } else if ("1".equals(isLocked)) {

                radioMesLockNo.setChecked(true);

            } else {

                radioGroupMesLock.clearCheck();
            }

            // FILE ĐÍNH KÈM ĐÃ TẢI LÊN
            // Hiển thị nhanh từ FILE_WO trong dữ liệu list (nếu có), sau đó
            // đồng bộ lại từ server (get-task) để lấy nguồn chuẩn giống web.
            String existingFile = safeGet(data, "File_Wo");
            if (existingFile.isEmpty()) existingFile = safeGet(data, "FILE_WO");
            if (existingFile.isEmpty()) existingFile = safeGet(data, "file_wo");
            ColorConsole.d(TAG, "[ATTACHMENT] FILE_WO trong list = '" + existingFile + "'");
            uploadedFileNames.clear();
            uploadedFileNames.addAll(parseFileWo(existingFile));
            renderAttachmentList();

            loadUploadedFilesFromServer();

        }catch (Exception e){
            Log.e(TAG, "Bind error: " + e.getMessage());
        }
    }

    //TODO: func format date
    private String formatDateForUI(String raw){
        if (raw == null) return "";

        String s = raw.trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return "";

        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        output.setTimeZone(java.util.TimeZone.getDefault());

        boolean isUtc = s.endsWith("Z") || s.contains("+00") || s.contains("-00");

        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm"
        };

        for (String p : patterns) {
            try {
                SimpleDateFormat input = new SimpleDateFormat(p, Locale.getDefault());
                input.setLenient(false);
                if (isUtc && !p.contains("X")) {
                    input.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                }
                String cleanS = s;
                if (!p.contains("X") && cleanS.endsWith("Z")) {
                    cleanS = cleanS.substring(0, cleanS.length() - 1);
                }
                Date d = input.parse(cleanS);
                if (d != null) return output.format(d);
            } catch (Exception ignored) {
            }
        }

        try {
            String normalized = s.replace('T', ' ');
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.contains(".")) {
                normalized = normalized.substring(0, normalized.indexOf('.'));
            }
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            input.setLenient(false);
            if (isUtc) {
                input.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            }
            Date d = input.parse(normalized);
            if (d != null) return output.format(d);
        } catch (Exception ignored) {
        }

        return raw;
    }

    private String formatDateForApi(String raw) {
        if (raw == null) return "";

        String s = raw.trim();
        if (s.isEmpty()) return "";

        String[] patterns = new String[]{
                "dd/MM/yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX"
        };

        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.getDefault());
                Date d = in.parse(s.replace("Z", ""));
                if (d != null) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(d);
                }
            } catch (Exception ignored) {
            }
        }

        return s;
    }

    //TODO: func format date to api
    private String formatDateToApi(String dateStr){
        try{
            SimpleDateFormat input = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date d = input.parse(dateStr);
            return output.format(d);
        }catch (Exception e){
            return null;
        }
    }

    //TODO: func: cho phép các trường được chỉnh sửa
    private void setupEditModeUI() {
        applyReadOnlyFieldStyle(edtWoCode);
        applyReadOnlyFieldStyle(autoMachine);
        applyReadOnlyFieldStyle(autoRequester);
        applyReadOnlyFieldStyle(autoMaStatus);
        applyReadOnlyFieldStyle(edtProcess);
        applyReadOnlyFieldStyle(edtPassedDate);
        applyReadOnlyFieldStyle(edtDeadline);
        applyReadOnlyFieldStyle(autoLoaiHinh);

        applyEditableFieldStyle(edtRequestDate);
        applyEditableFieldStyle(edtReason);

        // disable toàn bộ group
        if (radioGroupMesLock != null) {
            radioGroupMesLock.setEnabled(false);
            radioGroupMesLock.setClickable(false);
            radioGroupMesLock.setFocusable(false);
            radioGroupMesLock.setAlpha(0.7f);

            radioGroupMesLock.setBackgroundResource(
                    R.drawable.bg_input_field_disabled
            );
        }
        // disable từng radio
        radioMesLockYes.setEnabled(false);
        radioMesLockYes.setClickable(false);

        radioMesLockNo.setEnabled(false);
        radioMesLockNo.setClickable(false);

    }

    // =========================================================================
    // ĐÍNH KÈM ẢNH / FILE
    // =========================================================================

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(
                Intent.createChooser(intent, i18n("Select one or more photos")), REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK && data != null) {
            // Chọn nhiều file cùng lúc
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    selectedFileUris.add(data.getClipData().getItemAt(i).getUri());
                }
            }
            // Chỉ chọn 1 file
            else if (data.getData() != null) {
                selectedFileUris.add(data.getData());
            }
            renderAttachments();
        }
    }

    private String getServerDynamicUrl() {
        ConfigManager config = new ConfigManager(this);
        String url = config.getProperty("server_dynamic_url");
        if (url == null || url.isEmpty()) {
            url = "http://192.86.0.225:9101/api/dynamics";
        }
        return url;
    }

    /**
     * Bộ phận của người dùng đang đăng nhập (đọc từ prefs "user".divisionName).
     * Dùng để định tuyến kho tài liệu MA/FE giống web ở màn Chỉnh sửa WO.
     */
    private boolean isCurrentUserFe() {
        try {
            PreferenceHandler ph = new PreferenceHandler(this);
            JSONObject u = ph.getJsonObject("user");
            if (u == null) return false;

            // Division_Id "006" = FE (giống web: Division_Id == "006" ? "FE" : "MA").
            String divId = safeGet(u, "Division_Id");
            if (divId.isEmpty()) divId = safeGet(u, "divisionId");
            if (divId.isEmpty()) divId = safeGet(u, "DIVISION_ID");
            if ("006".equals(divId.trim())) return true;

            // Chuẩn hóa mã bộ phận từ nhiều key khả dĩ (giống ListWorkOrderActivity).
            String code = safeGet(u, "departmentCode");
            if (code.isEmpty()) code = safeGet(u, "Department_Code");
            if (code.isEmpty()) code = safeGet(u, "divisionCode");
            if (code.isEmpty()) code = safeGet(u, "divisionName");
            if (code.isEmpty()) code = safeGet(u, "Division_Name");
            return "FE".equals(normalizeDeptCode(code));
        } catch (Exception e) {
            ColorConsole.e(TAG, "isCurrentUserFe error: " + e.getMessage());
        }
        return false;
    }

    /** Chuẩn hóa mã bộ phận về "FE"/"MA" (chấp nhận "FE", "FE-xxx", chuỗi chứa "FE"). */
    private String normalizeDeptCode(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if ("FE".equalsIgnoreCase(value) || "MA".equalsIgnoreCase(value)) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }
        String[] parts = value.split("-");
        if (parts.length > 0) {
            String first = parts[0].trim();
            if ("FE".equalsIgnoreCase(first) || "MA".equalsIgnoreCase(first)) {
                return first.toUpperCase(java.util.Locale.ROOT);
            }
        }
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("FE")) return "FE";
        if (upper.contains("MA")) return "MA";
        return upper;
    }

    /** Bộ phận dùng cho kho tài liệu: FE nếu user là FE, ngược lại MA. */
    private String attachmentDivision() {
        return isCurrentUserFe() ? "FE" : "MA";
    }

    /**
     * Tải danh sách ảnh đã đính kèm từ server theo WO_CODE (giống web: gọi get-task).
     * Backend đọc cột FILE_WO trong MT_WORK_ORDER.
     */
    private void loadUploadedFilesFromServer() {
        final String woCode = edtWoCode.getText().toString().trim();
        if (woCode.isEmpty() || "...".equals(woCode)) return;

        final String serverDynamicUrl = getServerDynamicUrl();
        new Thread(() -> {
            // Edit WO: FE đọc kho FE (FILE_WO_FE), MA đọc kho MA (FILE_WO_MA) — giống web.
            HttpClient.APIReturn result = HttpClient.getWorkOrderFiles(this, serverDynamicUrl, woCode, attachmentDivision());
            if (result == null || result.code != 200 || result.data == null) return;

            final List<String> serverFiles = new ArrayList<>();
            for (JSONObject item : result.data) {
                if (item == null) continue;
                String fileVal = item.optString("value");
                if (!fileVal.isEmpty()) serverFiles.add(fileVal);
            }
            ColorConsole.d(TAG, "[ATTACHMENT] get-task trả về " + serverFiles.size() + " file cho " + woCode);

            runOnUiThread(() -> {
                uploadedFileNames.clear();
                uploadedFileNames.addAll(serverFiles);
                renderAttachmentList();
            });
        }).start();
    }

    /** Tải file đã đính kèm trên server về máy. */
    private void downloadUploadedFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            Toast.makeText(this, i18n("No attachment file to download"), Toast.LENGTH_SHORT).show();
            return;
        }

        String finalUrl = getServerDynamicUrl();
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
        String downloadUrl = finalUrl + ":9101/api/v1/mms_file-img/" + fileName;
        downloadFile(downloadUrl, fileName);
    }

    private void downloadFile(String url, String fileName) {
        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(url);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(i18n("Download file") + ": " + fileName);
            request.setDescription(i18n("Downloading attachment..."));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            PreferenceHandler handler = new PreferenceHandler(this);
            String apiToken = handler.getString("api_key");
            if (apiToken != null && !apiToken.isEmpty()) {
                request.addRequestHeader("Authorization", "Bearer " + apiToken);
            }

            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (fileExtension != null) {
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                if (mimeType != null) request.setMimeType(mimeType);
            }

            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toast.makeText(this, i18n("Starting file download..."), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, i18n("Cannot initialize Download Manager"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, i18n("File download error") + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, i18n("Cannot open link") + ": " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Tải các file đã chọn lên server. taskId dùng cho upload chính là WO_CODE.
     * Dùng được cho cả màn hình Add (WO_CODE đã sinh sẵn) và Edit.
     */
    private void uploadSelectedFiles() {
        if (selectedFileUris.isEmpty()) {
            Toast.makeText(this, i18n("Please select a file before uploading"), Toast.LENGTH_SHORT).show();
            return;
        }
        // Ở màn Add: Work Order chưa tồn tại trên server nên không thể upload sớm
        // (sẽ thành file mồ côi). File sẽ được tải lên tự động sau khi bấm Add.
        if (!isEditMode) {
            Toast.makeText(this, i18n("The photo will be uploaded automatically after you press Add"), Toast.LENGTH_LONG).show();
            return;
        }
        final String woCode = edtWoCode.getText().toString().trim();
        if (woCode.isEmpty() || "...".equals(woCode)) {
            Toast.makeText(this, i18n("Work Order code not found for upload"), Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog uploadProgress = new ProgressDialog(this);
        uploadProgress.setMessage(i18n("Uploading document..."));
        uploadProgress.setCancelable(false);
        uploadProgress.show();

        final String serverDynamicUrl = getServerDynamicUrl();
        final List<Uri> filesToUpload = new ArrayList<>(selectedFileUris);
        // Edit WO: FE upload vào kho FE (division=FE, bucket=edit -> FILE_WO_FE); MA vào kho MA.
        final String uploadDivision = attachmentDivision();
        final String uploadBucket = isCurrentUserFe() ? "edit" : "";
        new Thread(() -> {
            HttpClient.APIReturn result = HttpClient.uploadWorkOrderFile(
                    this, serverDynamicUrl, woCode, filesToUpload, uploadDivision, uploadBucket);

            runOnUiThread(() -> {
                uploadProgress.dismiss();
                if (result != null && result.code == 200) {
                    // upload-file-task trả về toàn bộ FILE_WO; đọc lại đúng kho theo bộ phận
                    // (FE -> get-task-fe, MA -> get-task-ma) để danh sách hiển thị chuẩn.
                    selectedFileUris.clear();
                    // Màn Sửa dùng 2 khối MA/FE -> tải lại cả 2 kho; nếu không thì danh sách đơn.
                    if (isEditMode) {
                        loadEditModeAttachments();
                    } else {
                        loadUploadedFilesFromServer();
                    }
                    Toast.makeText(this, i18n("Photos uploaded successfully"), Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = (result != null) ? result.message : i18n("No response from server");
                    Toast.makeText(this, i18n("Upload failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /**
     * Tải file đã chọn lên đồng bộ (gọi từ luồng nền của performAddWorkOrder, sau khi WO đã tạo).
     * Trả về true nếu không có file để up hoặc up thành công.
     */
    private boolean uploadSelectedFilesSync(String woCode) {
        if (selectedFileUris.isEmpty()) return true;
        if (woCode == null || woCode.trim().isEmpty()) return false;
        try {
            // Thêm WO: theo tài khoản đăng nhập — FE up vào kho FE (division=FE, bucket=edit -> FILE_WO_FE),
            // MA up vào kho MA (FILE_WO_MA). Không fix cứng MA nữa.
            String division = attachmentDivision();
            String bucket = isCurrentUserFe() ? "edit" : "";
            HttpClient.APIReturn result = HttpClient.uploadWorkOrderFile(
                    this, getServerDynamicUrl(), woCode.trim(), new ArrayList<>(selectedFileUris), division, bucket);
            return result != null && result.code == 200;
        } catch (Exception e) {
            ColorConsole.e(TAG, "Lỗi upload file cho WO mới: " + e.getMessage());
            return false;
        }
    }

    /**
     * Bỏ tiền tố phân loại của web ({WO_CODE}_MA_ / {WO_CODE}_FE_) để hiển thị tên gọn,
     * giống hàm woReportClassifyAttach bên web. Tên đầy đủ vẫn được dùng khi tải/xóa.
     */
    private String displayFileName(String woCode, String fileName) {
        if (fileName == null) return "";
        String rest = fileName;
        if (woCode != null && !woCode.isEmpty()) {
            String prefix = woCode + "_";
            if (rest.startsWith(prefix)) rest = rest.substring(prefix.length());
        }
        if (rest.startsWith("MA_")) rest = rest.substring(3);
        else if (rest.startsWith("FE_")) rest = rest.substring(3);
        return rest;
    }

    /** Tách chuỗi FILE_WO (phân tách bằng dấu phẩy) thành danh sách tên file. */
    private List<String> parseFileWo(String fileWo) {
        List<String> files = new ArrayList<>();
        if (fileWo == null) return files;
        for (String part : fileWo.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                files.add(name);
            }
        }
        return files;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Vẽ lại khung tài liệu đính kèm: mỗi file là 1 dòng kèm nút (X) để gỡ.
     * - File đã tải lên server (uploadedFileNames): nút X gọi API xóa trên server.
     * - File mới chọn nhưng chưa tải lên (selectedFileUris): nút X chỉ gỡ ở local.
     */
    private void renderAttachmentList() {
        if (layoutAttachmentList == null || layoutAttachmentEmpty == null) return;

        layoutAttachmentList.removeAllViews();

        boolean hasUploaded = !uploadedFileNames.isEmpty();
        boolean hasSelected = !selectedFileUris.isEmpty();

        if (!hasUploaded && !hasSelected) {
            layoutAttachmentEmpty.setVisibility(View.VISIBLE);
            layoutAttachmentList.setVisibility(View.GONE);
            return;
        }

        layoutAttachmentEmpty.setVisibility(View.GONE);
        layoutAttachmentList.setVisibility(View.VISIBLE);

        final String woCode = edtWoCode != null ? edtWoCode.getText().toString().trim() : "";
        for (final String fileName : new ArrayList<>(uploadedFileNames)) {
            // Hiển thị tên gọn (bỏ tiền tố {WO_CODE}_MA_) nhưng vẫn dùng tên đầy đủ để tải/xóa.
            layoutAttachmentList.addView(buildAttachmentRow(displayFileName(woCode, fileName), true, null, fileName));
        }
        for (final Uri uri : new ArrayList<>(selectedFileUris)) {
            String displayName = getFileName(uri);
            layoutAttachmentList.addView(buildAttachmentRow(displayName, false, uri, null));
        }
    }

    private View buildAttachmentRow(String displayName, final boolean isUploaded,
                                    final Uri localUri, final String serverFileName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconLp.setMarginEnd(dp(8));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_paperclip);
        row.addView(icon);

        TextView tvName = new TextView(this);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLp);
        tvName.setText(displayName);
        tvName.setTextColor(Color.parseColor("#333333"));
        tvName.setTextSize(13);
        tvName.setPaintFlags(tvName.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (isUploaded) {
            tvName.setOnClickListener(v -> downloadUploadedFile(serverFileName));
        }
        row.addView(tvName);

        // Nút tải về (chỉ cho file đã tải lên server)
        if (isUploaded) {
            ImageView btnDownload = new ImageView(this);
            LinearLayout.LayoutParams downloadLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            downloadLp.setMarginStart(dp(8));
            btnDownload.setLayoutParams(downloadLp);
            btnDownload.setPadding(dp(3), dp(3), dp(3), dp(3));
            btnDownload.setImageResource(R.drawable.ic_download_24);
            btnDownload.setClickable(true);
            btnDownload.setFocusable(true);
            btnDownload.setOnClickListener(v -> downloadUploadedFile(serverFileName));
            row.addView(btnDownload);
        }

        ImageView btnRemove = new ImageView(this);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        removeLp.setMarginStart(dp(8));
        btnRemove.setLayoutParams(removeLp);
        btnRemove.setPadding(dp(2), dp(2), dp(2), dp(2));
        btnRemove.setImageResource(R.drawable.close_24);
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);
        btnRemove.setOnClickListener(v -> {
            if (isUploaded) {
                confirmAndDeleteUploadedFile(serverFileName);
            } else {
                selectedFileUris.remove(localUri);
                renderAttachmentList();
            }
        });
        row.addView(btnRemove);

        return row;
    }

    private void confirmAndDeleteUploadedFile(final String serverFileName) {
        new AlertDialog.Builder(this)
                .setTitle(i18n("Remove attachment"))
                .setMessage(i18n("Are you sure you want to remove this file?") + "\n" + serverFileName)
                .setNegativeButton(i18n("Cancel"), null)
                .setPositiveButton(i18n("Remove"), (d, w) -> deleteUploadedFile(serverFileName))
                .show();
    }

    private void deleteUploadedFile(final String serverFileName) {
        final String woCode = edtWoCode.getText().toString().trim();
        if (woCode.isEmpty() || "...".equals(woCode)) {
            Toast.makeText(this, i18n("Work Order code not found for upload"), Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(i18n("Removing attachment..."));
        progress.setCancelable(false);
        progress.show();

        final String serverDynamicUrl = getServerDynamicUrl();
        new Thread(() -> {
            // Edit WO: FE xóa trong kho FE (scope=FE), MA xóa trong kho MA (scope=MA).
            HttpClient.APIReturn result = HttpClient.deleteWorkOrderFile(
                    this, serverDynamicUrl, woCode, serverFileName, attachmentDivision());

            runOnUiThread(() -> {
                progress.dismiss();
                if (result != null && result.code == 200) {
                    uploadedFileNames.remove(serverFileName);
                    renderAttachmentList();
                    Toast.makeText(this, i18n("Attachment removed"), Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = (result != null) ? result.message : i18n("No response from server");
                    Toast.makeText(this, i18n("Remove failed") + ": " + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void applyReadOnlyFieldStyle(View v) {
        if (v == null) return;
        v.setEnabled(false);
        v.setFocusable(false);
        v.setClickable(false);
        v.setAlpha(1f);

        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setBackgroundResource(R.drawable.bg_input_field_disabled);
            tv.setTextColor(Color.parseColor("#606266"));
            tv.setHintTextColor(Color.parseColor("#A8ABB2"));
        }
    }

    private void applyEditableFieldStyle(View v) {
        if (v == null) return;
        v.setEnabled(true);
        v.setFocusable(true);
        v.setClickable(true);
        v.setAlpha(1f);

        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setBackgroundResource(R.drawable.bg_input_field);
            tv.setTextColor(Color.parseColor("#303133"));
            tv.setHintTextColor(Color.parseColor("#909399"));
        }
    }

    //TODO: func get local status MA
    private List<String> getLocalizedMaStatusOptions() {
        List<String> localized = new ArrayList<>();
        for (String key : MA_STATUS_KEYS) {
            localized.add(i18n(key));
        }
        return localized;
    }

    private int statusFromText(String value) {
        if (value == null) return 0;
        String normalizedInput = normalizeStatusText(value);
        if (normalizedInput.isEmpty()) return 0;
        for (int i = 0; i < MA_STATUS_KEYS.size(); i++) {
            String key = MA_STATUS_KEYS.get(i);
            String normalizedKey = normalizeStatusText(key);
            String normalizedLocalized = normalizeStatusText(i18n(key));
            if (normalizedInput.equals(normalizedKey) || normalizedInput.equals(normalizedLocalized)) {
                return i + 1;
            }

            if (i < MA_STATUS_ALIASES.length) {
                for (String alias : MA_STATUS_ALIASES[i]) {
                    if (alias != null && normalizedInput.equals(normalizeStatusText(alias))) {
                        return i + 1;
                    }
                }
            }
        }
        return 0;
    }

    private int resolveStatusCodeFromData(JSONObject data) {
        if (data == null) return 0;

        int status = data.optInt("Status", 0);
        if (status == 0) status = data.optInt("STATUS", 0);
        if (status == 0) status = data.optInt("STATUS_1", 0);

        if (status == 0) {
            String statusText = safeGet(data, "Status");
            if (statusText.isEmpty()) statusText = safeGet(data, "STATUS");
            if (statusText.isEmpty()) statusText = safeGet(data, "STATUS_1");
            if (statusText.isEmpty()) statusText = safeGet(data, "MA_STATUS");
            if (statusText.isEmpty()) statusText = safeGet(data, "Ma_Status");
            if (statusText.isEmpty()) statusText = safeGet(data, "STATUS_TEXT");
            if (statusText.isEmpty()) statusText = safeGet(data, "Status_Text");

            String numericCandidate = statusText == null ? "" : statusText.trim();
            if (!numericCandidate.isEmpty()) {
                try {
                    status = Integer.parseInt(numericCandidate);
                } catch (Exception ignored) {
                }
            }

            if (status <= 0) {
                status = statusFromText(statusText);
            }
        }

        return status;
    }

    private String normalizeStatusText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
        return normalized;
    }

    private String getCurrentLockStatus() {
        if (radioMesLockYes != null && radioMesLockYes.isChecked()) {
            return "2"; //khóa
        }
        if (radioMesLockNo != null && radioMesLockNo.isChecked()) {
            return "1"; //không khóa
        }
        return "0"; //mặc định
    }

    //TODO: func convert status ma báo
    private String convertStatus(int status){
        if (status >= 1 && status <= MA_STATUS_KEYS.size()) {
            return i18n(MA_STATUS_KEYS.get(status - 1));
        }
        return "";
    }

}
