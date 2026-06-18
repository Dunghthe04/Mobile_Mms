package com.mkac.meikomms.ui.workorder;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;

import com.mkac.meikomms.R;
import com.mkac.meikomms.common.Barcode;
import com.mkac.meikomms.common.ConfigManager;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.common.LanguageAPIUtils;
import com.mkac.meikomms.common.PreferenceHandler;

import org.json.JSONObject;

import static com.mkac.meikomms.common.LanguageAPIUtils.i18n;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WorkOrderDataActivity extends AppCompatActivity {
    private static final String EXTRA_LANGUAGE_CODE = "LANGUAGE_CODE";
    private static final String EXTRA_MACHINE_ID = "MACHINE_ID";
    private static final int REQUEST_SCAN_MACHINE = 1201;

    private static final String TAG_WORK_ORDER = "tab_work_order";
    private static final String TAG_MAINTENANCE = "tab_maintenance";

    public static class FeTeam {
        public String teamId;
        public String teamName;
        public FeTeam(String teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
        }
    }

    private final List<FeTeam> dynamicMaintGroups = new ArrayList<>();
    private int selectedWoStatusIndex = -2;
    private int selectedMaintStatusIndex = -2;
    private int selectedGroupIndex = -2;

    private int getDefaultGroupSelectionIndex() {
        return 0; // Default to "All" initially, fetched dynamically
    }

    public int getSelectedGroupIndex() {
        return selectedGroupIndex;
    }

    public List<FeTeam> getDynamicMaintGroups() {
        return dynamicMaintGroups;
    }

    private void fetchFeTeams() {
        PreferenceHandler handler = new PreferenceHandler(this);
        JSONObject userObj = handler.getJsonObject("user");
        String userName = handler.getString("Userlogin").trim();
        if (userName.isEmpty() && userObj != null) {
            userName = userObj.optString("username", "").trim();
        }
        final String finalUserName = userName;

        initConfiguration();
        new Thread(() -> {
            try {
                // 1. Fetch all teams first
                JSONObject allCondition = new JSONObject();
                allCondition.put("USER_ID", "'admin'");
                allCondition.put("Schema_Core", schemaCore);
                allCondition.put("Schema_Mms", schemaMms);

                HttpClient.APIReturn resAll = HttpClient.callDynamics(this, serverUrl, "mes_mms", "MMS_GET_FE_TEAM", allCondition);
                List<FeTeam> loadedTeams = new ArrayList<>();
                loadedTeams.add(new FeTeam("", i18n("All")));

                if (resAll != null && resAll.code == 200 && resAll.data != null) {
                    for (JSONObject teamObj : resAll.data) {
                        String teamId = teamObj.optString("Team_Id", teamObj.optString("TEAM_ID", "")).trim();
                        String teamName = teamObj.optString("Team_Name", teamObj.optString("TEAM_NAME", "")).trim();
                        if (!teamId.isEmpty() && !teamName.isEmpty()) {
                            boolean exists = false;
                            for (FeTeam t : loadedTeams) {
                                if (t.teamId.equalsIgnoreCase(teamId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                loadedTeams.add(new FeTeam(teamId, teamName));
                            }
                        }
                    }
                }

                // If loading failed or empty, fallback to static list to avoid breaking UI
                if (loadedTeams.size() <= 1) {
                    loadedTeams.add(new FeTeam("00011", "FE1"));
                    loadedTeams.add(new FeTeam("00014", "FE2"));
                    loadedTeams.add(new FeTeam("00030", "FE3"));
                    loadedTeams.add(new FeTeam("00031", "FE4"));
                }

                // 2. Fetch the user's specific team to select the default one
                String userTeamId = "";
                String userTeamName = "";
                if (!finalUserName.isEmpty()) {
                    JSONObject userCondition = new JSONObject();
                    userCondition.put("USER_ID", "'" + finalUserName + "'");
                    userCondition.put("Schema_Core", schemaCore);
                    userCondition.put("Schema_Mms", schemaMms);

                    HttpClient.APIReturn resUser = HttpClient.callDynamics(this, serverUrl, "mes_mms", "MMS_GET_FE_TEAM", userCondition);
                    if (resUser != null && resUser.code == 200 && resUser.data != null && !resUser.data.isEmpty()) {
                        JSONObject userTeamObj = resUser.data.get(0);
                        userTeamId = userTeamObj.optString("Team_Id", userTeamObj.optString("TEAM_ID", "")).trim();
                        userTeamName = userTeamObj.optString("Team_Name", userTeamObj.optString("TEAM_NAME", "")).trim();
                    }
                }

                // Find index of user's team
                int matchedIndex = 0; // Default to "All" (index 0)
                if (!userTeamId.isEmpty() || !userTeamName.isEmpty()) {
                    for (int i = 0; i < loadedTeams.size(); i++) {
                        FeTeam t = loadedTeams.get(i);
                        if ((!userTeamId.isEmpty() && t.teamId.equalsIgnoreCase(userTeamId)) ||
                            (!userTeamName.isEmpty() && t.teamName.equalsIgnoreCase(userTeamName))) {
                            matchedIndex = i;
                            break;
                        }
                    }
                }

                final List<FeTeam> finalTeams = loadedTeams;
                final int finalIndex = matchedIndex;
                final String finalTeamId = finalTeams.get(matchedIndex).teamId;

                runOnUiThread(() -> {
                    dynamicMaintGroups.clear();
                    dynamicMaintGroups.addAll(finalTeams);
                    selectedGroupIndex = finalIndex;

                    if (currentSelectedTab == 1) {
                        populateMaintSpinners();
                    }
                    if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                        maintenanceTabFragment.filterByGroup(finalTeamId);
                    }
                });

            } catch (Exception e) {
                Log.e("WorkOrderDataActivity", "Error loading dynamic FE teams", e);
                runOnUiThread(() -> {
                    if (dynamicMaintGroups.isEmpty()) {
                        dynamicMaintGroups.add(new FeTeam("", i18n("All")));
                        dynamicMaintGroups.add(new FeTeam("00011", "FE1"));
                        dynamicMaintGroups.add(new FeTeam("00014", "FE2"));
                        dynamicMaintGroups.add(new FeTeam("00030", "FE3"));
                        dynamicMaintGroups.add(new FeTeam("00031", "FE4"));
                    }
                    if (selectedGroupIndex == -2) {
                        selectedGroupIndex = 0;
                    }
                    if (currentSelectedTab == 1) {
                        populateMaintSpinners();
                    }
                });
            }
        }).start();
    }

    private TextView btnTabWorkOrder;
    private TextView btnTabMaintenance;
    private AutoCompleteTextView autoSearchMachine;
    private TextView tvTitle;
    private View lineTabWorkOrder;
    private View lineTabMaintenance;
    private View btnScanMachine;

    private Spinner spinnerWoStatus;
    private Spinner spinnerMaintStatus;
    private Spinner spinnerMaintGroup;

    private View btnFilterWoDate;
    private TextView tvFilterWoDateLabel;
    private View btnFilterMaintDate;
    private TextView tvFilterMaintDateLabel;
    private String selectedWoDate = "";
    private String selectedMaintDate = "";

    private WorkOrderDataListFragment workOrderListFragment;
    private MaintenanceTabFragment maintenanceTabFragment;
    private String currentMachineId = "";
    private ActivityResultLauncher<Intent> scanMachineLauncher;
    private String workOrderMachineId = "";
    private String maintenanceMachineId = "";
    private int currentSelectedTab = 0;// 0: Work Order, 1: Maintenance

    public static void start(Context context) {
        context.startActivity(new Intent(context, WorkOrderDataActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyIncomingLanguageFromIntent();
        setContentView(R.layout.activity_work_order_data);

        LanguageAPIUtils.init(this);

        tvTitle = findViewById(R.id.tv_title);

        btnTabWorkOrder = findViewById(R.id.btn_tab_work_order);
        btnTabMaintenance = findViewById(R.id.btn_tab_maintenance);
        autoSearchMachine = findViewById(R.id.auto_search_machine);
        btnScanMachine = findViewById(R.id.btn_scan_machine);
        lineTabWorkOrder = findViewById(R.id.line_tab_work_order);
        lineTabMaintenance = findViewById(R.id.line_tab_maintenance);
        spinnerWoStatus = findViewById(R.id.spinner_filter_wo_status);
        spinnerMaintStatus = findViewById(R.id.spinner_filter_maint_status);
        spinnerMaintGroup = findViewById(R.id.spinner_filter_maint_group);

        btnFilterWoDate = findViewById(R.id.btn_filter_wo_date);
        tvFilterWoDateLabel = findViewById(R.id.tv_filter_wo_date_label);
        btnFilterMaintDate = findViewById(R.id.btn_filter_maint_date);
        tvFilterMaintDateLabel = findViewById(R.id.tv_filter_maint_date_label);

        // Khởi tạo biến lưu trữ mã máy đơn lập tách biệt
        String initialMachineId = safeText(getIntent() != null ? getIntent().getStringExtra(EXTRA_MACHINE_ID) : null);
        workOrderMachineId = initialMachineId;
        maintenanceMachineId = initialMachineId;

        // Đảm bảo thiết lập bộ lắng nghe lọc ngày thành công
        setupDateFilterListeners();

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        loadMachineListAndSetupAutocomplete();
        setupSearchListeners();

        scanMachineLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }

                    String machineId = safeText(result.getData().getStringExtra("barcode"));
                    if (machineId.isEmpty()) {
                        Toast.makeText(this, i18n("Unable to read machine code"), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateMachineScanUi(machineId);
                    filterListsByMachine(machineId);
                }
        );

        if (btnScanMachine != null) {
            btnScanMachine.setOnClickListener(v -> openMachineScanner());
        }

        if (btnTabWorkOrder != null) btnTabWorkOrder.setOnClickListener(v -> selectTab(0));
        if (btnTabMaintenance != null) btnTabMaintenance.setOnClickListener(v -> selectTab(1));
        findViewById(R.id.tab_work_order_container).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.tab_maintenance_container).setOnClickListener(v -> selectTab(1));

        if (savedInstanceState == null) {
            if (!workOrderMachineId.isEmpty()) {
                workOrderListFragment = WorkOrderDataListFragment.newInstance(workOrderMachineId);
            } else {
                workOrderListFragment = new WorkOrderDataListFragment();
            }

            maintenanceTabFragment = new MaintenanceTabFragment();
            if (!maintenanceMachineId.isEmpty()) {
                maintenanceTabFragment.reloadDataForMachineId(maintenanceMachineId);
            }

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, workOrderListFragment, TAG_WORK_ORDER)
                    .add(R.id.fragment_container, maintenanceTabFragment, TAG_MAINTENANCE)
                    .hide(maintenanceTabFragment)
                    .commitNow();
        } else {
            workOrderListFragment = (WorkOrderDataListFragment) getSupportFragmentManager().findFragmentByTag(TAG_WORK_ORDER);
            maintenanceTabFragment = (MaintenanceTabFragment) getSupportFragmentManager().findFragmentByTag(TAG_MAINTENANCE);
        }

        // Khởi tạo việc tải danh sách nhóm FE động từ cơ sở dữ liệu
        fetchFeTeams();

        // Populate spinners after fragments are attached and added
        applyLanguage();

        selectTab(0);
    }

    private void setupDateFilterListeners() {
        if (btnFilterWoDate != null) {
            btnFilterWoDate.setOnClickListener(v -> showDatePickerDialog(true));
        }
        if (btnFilterMaintDate != null) {
            btnFilterMaintDate.setOnClickListener(v -> showDatePickerDialog(false));
        }
    }

    private void showDatePickerDialog(boolean isWorkOrder) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            String displayDate = String.format(Locale.getDefault(), "%04d/%02d/%02d", year, month + 1, dayOfMonth);

            if (isWorkOrder) {
                selectedWoDate = dateStr;
                if (tvFilterWoDateLabel != null) {
                    tvFilterWoDateLabel.setText(i18n("Ngày tạo") + ": " + displayDate);
                }
                if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                    workOrderListFragment.filterByDate(selectedWoDate);
                }
            } else {
                selectedMaintDate = dateStr;
                if (tvFilterMaintDateLabel != null) {
                    tvFilterMaintDateLabel.setText(i18n("Dự kiến") + ": " + displayDate);
                }
                if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                    maintenanceTabFragment.filterByDate(selectedMaintDate);
                }
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.setButton(DialogInterface.BUTTON_NEUTRAL, i18n("Tất cả"), (dialog, which) -> {
            if (isWorkOrder) {
                selectedWoDate = "";
                if (tvFilterWoDateLabel != null) tvFilterWoDateLabel.setText(i18n("Ngày tạo: Tất cả"));
                if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                    workOrderListFragment.filterByDate("");
                }
            } else {
                selectedMaintDate = "";
                if (tvFilterMaintDateLabel != null) tvFilterMaintDateLabel.setText(i18n("Dự kiến: Tất cả"));
                if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                    maintenanceTabFragment.filterByDate("");
                }
            }
        });

        datePickerDialog.show();
    }

    private void applyIncomingLanguageFromIntent() {
        String passedLanguageCode = getIntent() != null ? getIntent().getStringExtra(EXTRA_LANGUAGE_CODE) : null;
        if (passedLanguageCode == null || passedLanguageCode.trim().isEmpty()) {
            return;
        }

        String normalizedCode = passedLanguageCode.trim();
        int languagePosition = 2;
        if ("ja".equalsIgnoreCase(normalizedCode)) languagePosition = 0;
        else if ("en".equalsIgnoreCase(normalizedCode)) languagePosition = 1;
        else if ("ch".equalsIgnoreCase(normalizedCode)) languagePosition = 3;

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        prefs.edit().putInt("languageSettingPosition", languagePosition).apply();
        LanguageAPIUtils.setLanguageCode(normalizedCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageAPIUtils.init(this);
        applyLanguage();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WorkOrderEntryDialogHelper.onActivityResult(requestCode, resultCode, data);
    }

    private void selectTab(int index) {
        currentSelectedTab = index; // Cập nhật mốc định vị cho hệ thống
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();

        View filtersWorkOrder = findViewById(R.id.layout_filters_work_order);
        View filtersMaintenance = findViewById(R.id.layout_filters_maintenance);

        spinnerWoStatus = findViewById(R.id.spinner_filter_wo_status);
        spinnerMaintStatus = findViewById(R.id.spinner_filter_maint_status);
        spinnerMaintGroup = findViewById(R.id.spinner_filter_maint_group);

        if (index == 0) {
            if (workOrderListFragment != null) tx.show(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.hide(maintenanceTabFragment);
            updateTabUi(true);

            updateMachineScanUi(workOrderMachineId);

            // hiển thị bộ lọc
            if (filtersWorkOrder != null) filtersWorkOrder.setVisibility(View.VISIBLE);
            if (filtersMaintenance != null) filtersMaintenance.setVisibility(View.GONE);

            populateWoSpinners();
        } else {
            if (workOrderListFragment != null) tx.hide(workOrderListFragment);
            if (maintenanceTabFragment != null) tx.show(maintenanceTabFragment);
            updateTabUi(false);

            updateMachineScanUi(maintenanceMachineId);

            // hiển thị bộ lọc
            if (filtersWorkOrder != null) filtersWorkOrder.setVisibility(View.GONE);
            if (filtersMaintenance != null) filtersMaintenance.setVisibility(View.VISIBLE);

            populateMaintSpinners();
        }
        tx.commitNowAllowingStateLoss();
    }

    private void updateTabUi(boolean workOrderSelected) {
        int activeColor = Color.parseColor("#00A680");
        int inactiveColor = Color.parseColor("#5C5C5C");
        int activeLine = activeColor;
        int inactiveLine = Color.parseColor("#F4F2F2");

        if (btnTabWorkOrder != null) {
            btnTabWorkOrder.setTextColor(workOrderSelected ? activeColor : inactiveColor);
            btnTabWorkOrder.setTypeface(null, workOrderSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (btnTabMaintenance != null) {
            btnTabMaintenance.setTextColor(workOrderSelected ? inactiveColor : activeColor);
            btnTabMaintenance.setTypeface(null, workOrderSelected ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        }
        if (lineTabWorkOrder != null) lineTabWorkOrder.setBackgroundColor(workOrderSelected ? activeLine : inactiveLine);
        if (lineTabMaintenance != null) lineTabMaintenance.setBackgroundColor(workOrderSelected ? inactiveLine : activeLine);
    }

    private void applyLanguage() {
        if (btnTabWorkOrder != null) btnTabWorkOrder.setText(i18n("Work Order"));
        if (btnTabMaintenance != null) btnTabMaintenance.setText(i18n("Maintenance"));
        String maintenanceText = i18n("Maintenance");
        Log.e("DEBUG_LANG", "Maintenance = " + maintenanceText);
        btnTabMaintenance.setText(maintenanceText);

        if (tvTitle != null) tvTitle.setText(i18n("Enter Work Order Data"));

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            LanguageAPIUtils.setLang(rootView);
        }

        if (currentSelectedTab == 0) {
            populateWoSpinners();
        } else {
            populateMaintSpinners();
        }

        String activeMachineId = (currentSelectedTab == 0) ? workOrderMachineId : maintenanceMachineId;
        updateMachineScanUi(activeMachineId);
    }

    private void populateWoSpinners() {
        int currentWoSelection = selectedWoStatusIndex == -2 ? 0 : selectedWoStatusIndex;
        if (spinnerWoStatus != null) {
            String[] woStatuses = WorkOrderDataListFragment.getLocalizedStatusLabels();
            ArrayAdapter<String> woAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, woStatuses);
            woAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            spinnerWoStatus.setOnItemSelectedListener(null);
            spinnerWoStatus.setAdapter(woAdapter);
            if (currentWoSelection >= 0 && currentWoSelection < woStatuses.length) {
                spinnerWoStatus.setSelection(currentWoSelection);
            }
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (spinnerWoStatus != null) {
                    spinnerWoStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedWoStatusIndex = position;
                            if (workOrderListFragment != null && workOrderListFragment.isAdded()) {
                                workOrderListFragment.filterByStatus(position);
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            });
        }
    }

    private void populateMaintSpinners() {
        int currentMaintSelection = selectedMaintStatusIndex == -2 ? 0 : selectedMaintStatusIndex;
        int currentGroupSelection = selectedGroupIndex == -2 ? getDefaultGroupSelectionIndex() : selectedGroupIndex;

        if (spinnerMaintStatus != null) {
            String[] maintStatuses = MaintenanceTabFragment.getLocalizedStatusLabels();
            ArrayAdapter<String> maintAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, maintStatuses);
            maintAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            spinnerMaintStatus.setOnItemSelectedListener(null);
            spinnerMaintStatus.setAdapter(maintAdapter);
            if (currentMaintSelection >= 0 && currentMaintSelection < maintStatuses.length) {
                spinnerMaintStatus.setSelection(currentMaintSelection);
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (spinnerMaintStatus != null) {
                    spinnerMaintStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedMaintStatusIndex = position;
                            if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                                maintenanceTabFragment.filterByStatus(position);
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            });
        }

        if (spinnerMaintGroup != null) {
            if (selectedGroupIndex == -2) {
                selectedGroupIndex = currentGroupSelection;
            }
            String[] groupLabels;
            if (dynamicMaintGroups.isEmpty()) {
                groupLabels = new String[]{ i18n("All") };
            } else {
                groupLabels = new String[dynamicMaintGroups.size()];
                for (int i = 0; i < dynamicMaintGroups.size(); i++) {
                    groupLabels[i] = dynamicMaintGroups.get(i).teamName;
                }
            }
            ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, groupLabels);
            groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            spinnerMaintGroup.setOnItemSelectedListener(null);
            spinnerMaintGroup.setAdapter(groupAdapter);
            if (selectedGroupIndex >= 0 && selectedGroupIndex < groupLabels.length) {
                spinnerMaintGroup.setSelection(selectedGroupIndex);
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (spinnerMaintGroup != null) {
                    spinnerMaintGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedGroupIndex = position;
                            if (maintenanceTabFragment != null && maintenanceTabFragment.isAdded()) {
                                if (dynamicMaintGroups.size() > position) {
                                    String selectedGroupId = dynamicMaintGroups.get(position).teamId;
                                    maintenanceTabFragment.filterByGroup(selectedGroupId);
                                }
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            });
        }
    }


    private void refreshCurrentTab() {
        if (workOrderListFragment != null && workOrderListFragment.isVisible()) {
            workOrderListFragment.reloadData();
            return;
        }

        if (maintenanceTabFragment != null && maintenanceTabFragment.isVisible()) {
            maintenanceTabFragment.reloadData();
        }

        String activeMachineId = (currentSelectedTab == 0) ? workOrderMachineId : maintenanceMachineId;
        updateMachineScanUi(activeMachineId);
    }

    private void openMachineScanner() {
        if (scanMachineLauncher != null) {
            scanMachineLauncher.launch(new Intent(this, Barcode.class));
        }
    }

    private String serverUrl = "";
    private String schemaCore = "";
    private String schemaMms = "";
    private String schemaData = "";
    private boolean isProgrammaticChange = false;

    private void updateMachineScanUi(String machineId) {
        if (autoSearchMachine != null) {
            isProgrammaticChange = true;
            autoSearchMachine.setText(machineId);
            if (machineId != null) {
                autoSearchMachine.setSelection(machineId.length());
            }
            isProgrammaticChange = false;
        }
    }

    private void filterListsByMachine(String query) {
        String cleanMachine = query.trim();
        if (currentSelectedTab == 0) {
            workOrderMachineId = cleanMachine;
            if (workOrderListFragment != null) {
                workOrderListFragment.reloadDataForMachineId(workOrderMachineId);
            }
        } else {
            maintenanceMachineId = cleanMachine;
            if (maintenanceTabFragment != null) {
                maintenanceTabFragment.reloadDataForMachineId(maintenanceMachineId);
            }
        }
    }

    private void setupSearchListeners() {
        if (autoSearchMachine == null) return;

        autoSearchMachine.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            filterListsByMachine(selected);
        });

        autoSearchMachine.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                
                String query = autoSearchMachine.getText().toString().trim();
                filterListsByMachine(query);
                
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(autoSearchMachine.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        autoSearchMachine.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isProgrammaticChange) return;
                if (s.toString().trim().isEmpty()) {
                    filterListsByMachine("");
                }
            }
        });
    }

    private void initConfiguration() {
        PreferenceHandler handler = new PreferenceHandler(this);
        ConfigManager configManager = new ConfigManager(this);

        serverUrl = pickFirst(handler.getString("server_url"), configManager.getProperty("server_url"));
        schemaCore = pickFirst(handler.getString("schema_core"), configManager.getProperty("schema_core"), "MES_CORE_MKHC");
        schemaMms = pickFirst(handler.getString("schema_mms"), configManager.getProperty("schema_mms"), "MES_MMS_MKHC");
        schemaData = pickFirst(handler.getString("schema_data"), configManager.getProperty("schema_data"), "MES_MMS_MKHC");
    }

    private String pickFirst(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private void loadMachineListAndSetupAutocomplete() {
        initConfiguration();

        new Thread(() -> {
            try {
                HttpClient.APIReturn resMachine = HttpClient.getMachineIdList(this, serverUrl, schemaCore, schemaMms, schemaData);
                if (resMachine != null && resMachine.code == 200 && resMachine.data != null) {
                    List<String> list = new ArrayList<>();
                    for (JSONObject m : resMachine.data) {
                        String mid = m.optString("Machine_Id", m.optString("machine_id"));
                        String mname = m.optString("Machine_Name", m.optString("machine_name"));
                        if (!mid.isEmpty()) {
                            list.add(mid + " - " + mname);
                        }
                    }
                    
                    runOnUiThread(() -> {
                        setupDropdownNew(autoSearchMachine, list);
                    });
                }
            } catch (Exception e) {
                Log.e("WorkOrderDataActivity", "Error loading machine list", e);
            }
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

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}