package com.mkac.meikomms.ui.workorder;

import static android.text.method.TextKeyListener.clear;
import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;
import static java.util.Collections.addAll;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

        applyI18nFieldTexts();

        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

//        if (btnAdd != null) btnAdd.setOnClickListener(v -> performAddWorkOrder());
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
       // setupDropdown(autoLoaiHinh, Collections.singletonList("BM"));
        autoLoaiHinh.setText("BM");
        applyReadOnlyFieldStyle(edtWoCode);
        applyReadOnlyFieldStyle(autoLoaiHinh);
    }

    private void applyI18nFieldTexts() {
        if (tvWorkOrderTitle != null) {
            tvWorkOrderTitle.setText(i18n(isEditMode ? "Edit Work Order" : "Add Work Order"));
        }
        if (tvLabelWoCode != null) tvLabelWoCode.setText(i18n("W/O Code"));
        if (tvLabelRequestDate != null) tvLabelRequestDate.setText(i18n("Request Date"));
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

        if (editText.getId() == R.id.edt_request_date) {
            dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
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
        //String loaiHinh = autoLoaiHinh.getText().toString().trim(); //loại hình
        String loaiHinh = "BM";
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

        //TODO: validate
        if (machineRaw.isEmpty() || requesterRaw.isEmpty() || woCode.isEmpty()) {
            Toast.makeText(this, i18n("Please enter Machine and Requester"), Toast.LENGTH_SHORT).show();
            return;

        }
        if(!validateRequiredFields()){
            return;
        }

        isSubmitting = true;
        btnAdd.setEnabled(false);

        int statusVal = statusFromText(maStatusStr);

       //TODO: Tách lấy ID từ chuỗi "ID - Name"
        final String machineId = machineRaw.contains(" - ") ? machineRaw.split(" - ")[0] : machineRaw;
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
                woData.put("ASSIGNEE", escapeSql(requesterUsername));
                woData.put("MATERIAL_JSON", escapeSql("[]"));
                woData.put("PHYSICAL_GROUP_NAME", escapeSql(process));
                woData.put("PASSED_DATE", escapeSql(passedDateVal));
                woData.put("STATUS", statusVal);
                woData.put("STATUS_1", 0);
                woData.put("IS_LOCKED", currentLockStatus);
                woData.put("NOTE", escapeSql(reason));
             //   woData.put("CREATE_BY", escapeSql(createByRealId));
                woData.put("CREATE_BY", escapeSql(loginUserName));

                woData.put("DELETED", 0);
                ColorConsole.i(TAG, "Add WO data: " + woData.toString());

                HttpClient.APIReturn resWo = HttpClient.addMtWorkOrder(this, serverUrl, woData);
                ColorConsole.d(TAG, "Add WO response: " + resWo.toString());

                if(resWo == null || resWo.code != 200){
                    String errorMsg = (resWo != null) ? resWo.message : "Không kết nối được server";
                    throw new Exception("Lỗi thêm WorkOrder: " + errorMsg);
                }
                ColorConsole.i(TAG, "Add WO success, start mail and task processes...");

                //TODO: Cập nhật trạng thái máy
               // HttpClient.APIReturn resStatus = HttpClient.updateMachineStatus(this, serverUrl, schemaCore, schemaMms, schemaData, machineId);
//                HttpClient.APIReturn resStatus = HttpClient.updateMachineStatus(this, serverUrl, schemaCore, machineId);
//                if(resStatus == null || resStatus.code != 200){
//                    ColorConsole.e("API Error", "Lỗi cập nhật trạng thái máy: " + (resStatus != null ? resStatus.message : "Không kết nối được server"));
//                }

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
                        finalDivisionName
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
                JSONObject taskData = prepareTaskData(machineId, woCode, reason, maintainerIdForTask);
                HttpClient.APIReturn resTask = HttpClient.addMtTask(this, serverUrl, taskData);

                if(resTask == null || resTask.code != 200){
                    throw new Exception("Lỗi thêm Task: " + (resTask != null ? resTask.message : "Không kết nối được server"));
                }

                //TODO: Thêm WorkOrder thành công, cập nhật trạng thái thành công, tạo task tương ứng thành công
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    isSubmitting = false;
                    btnAdd.setEnabled(true);
                    Toast.makeText(this, i18n("Add Work Order successful"), Toast.LENGTH_SHORT).show();
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

        isSubmitting = true;
        btnAdd.setEnabled(false);

        String woCode = editingWoCode;
        String requestDateUi = edtRequestDate.getText().toString().trim();
        String reason = edtReason.getText().toString().trim();

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(i18n("Updating..."));
        dialog.setCancelable(false);
        dialog.show();

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

                String currentLockStatus = getCurrentLockStatus();

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
                            divisionName
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

    private JSONObject prepareTaskData(String machineId, String woCode, String reason, String maintainerId) throws Exception {
        JSONObject taskData = new JSONObject();
        String dateSuffix = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String taskId = "TASK_" + dateSuffix + "_" + (new Random().nextInt(9000) + 1000);
        long unixTime = System.currentTimeMillis() / 1000;

        String dsaTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        taskData.put("Schema_MMS", schemaMms);
        taskData.put("taskId", escapeSql(taskId));
        taskData.put("taskType", "4");
        taskData.put("machineId", escapeSql(machineId));
        taskData.put("status", "0");
        taskData.put("dsa", "TO_TIMESTAMP('" + dsaTime + "', 'YYYY-MM-DD HH24:MI:SS')");
        taskData.put("taskDateUnix", String.valueOf(unixTime));
        taskData.put("maintainerId", escapeSql(maintainerId));
        taskData.put("requirementTask", escapeSql(reason));
        taskData.put("issueId", escapeSql(woCode));

        return taskData;
    }

    private int getWoTypeInt(String loaiHinh){
        if("BM".equalsIgnoreCase(loaiHinh)) return 3;
        return 3;
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
            Date now = new Date();

            long diffInMillis = now.getTime() - selectedDate.getTime();
            long diffInDays = diffInMillis / (24 * 60 * 60 * 1000);

            if(diffInDays < 0) diffInDays = 0;

            edtPassedDate.setText(String.valueOf(diffInDays));

        } catch (Exception e) {
            edtPassedDate.setText("0");
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
            String divisionName
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
            String divisionName
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
            String divisionName
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
                    divisionName
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
                            divisionName
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

            // PASSED DATE
            edtPassedDate.setText(safeGet(data, "Passed_Date"));

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
                Date d = input.parse(s);
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
